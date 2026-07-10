/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hop.quality.profile;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;

/** SQL pushdown profile collection for database tables. */
public final class DatabaseProfileCollector {

  private static final LoggingObject LOGGING_OBJECT =
      new LoggingObject("hop-quality-DatabaseProfileCollector");

  private DatabaseProfileCollector() {}

  public static DataProfileSnapshot collect(
      String subjectKey,
      String databaseMetaName,
      String schemaName,
      String tableName,
      Collection<DataQualityRule> rules,
      QualityLifecycle lifecycle,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(databaseMetaName) || Utils.isEmpty(tableName)) {
      throw new HopException("Database connection and table name are required for SQL profiling");
    }
    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(databaseMetaName);
    } catch (Exception e) {
      throw new HopException("Error loading database connection '" + databaseMetaName + "'", e);
    }
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + databaseMetaName + "' was not found");
    }

    String quotedTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, Const.NVL(schemaName, ""), tableName);
    DataProfileSnapshot snapshot = new DataProfileSnapshot();
    snapshot.setSubjectKey(subjectKey);
    snapshot.setLifecycle(lifecycle != null ? lifecycle : QualityLifecycle.AD_HOC);
    snapshot.setEvaluationMode(QualityEvaluationMode.SQL_PUSHDOWN);
    snapshot.setRowCountExact(true);

    Set<String> fieldNames = collectFieldNames(rules);

    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      snapshot.setRowCount(queryLong(db, "SELECT COUNT(*) FROM " + quotedTable));

      for (String fieldName : fieldNames) {
        String quotedField = databaseMeta.quoteField(fieldName);
        FieldProfile field = snapshot.field(fieldName);

        long nullCount =
            queryLong(
                db, "SELECT COUNT(*) FROM " + quotedTable + " WHERE " + quotedField + " IS NULL");
        field.addNullCount(nullCount);

        try {
          long emptyCount =
              queryLong(
                  db,
                  "SELECT COUNT(*) FROM "
                      + quotedTable
                      + " WHERE "
                      + quotedField
                      + " IS NOT NULL AND "
                      + quotedField
                      + " = ''");
          field.addEmptyStringCount(emptyCount);
        } catch (Exception ignored) {
          // best-effort
        }

        try {
          Object[] minMax =
              queryRow(
                  db,
                  "SELECT MIN("
                      + quotedField
                      + "), MAX("
                      + quotedField
                      + ") FROM "
                      + quotedTable
                      + " WHERE "
                      + quotedField
                      + " IS NOT NULL");
          if (minMax != null) {
            field.setMinMaxFromSql(asComparable(minMax[0]), asComparable(minMax[1]));
          }
        } catch (Exception ignored) {
          // best-effort
        }

        try {
          ResultSet rs =
              db.openQuery(
                  "SELECT "
                      + quotedField
                      + ", COUNT(*) FROM "
                      + quotedTable
                      + " WHERE "
                      + quotedField
                      + " IS NOT NULL GROUP BY "
                      + quotedField);
          if (rs != null) {
            try {
              while (rs.next()) {
                Object raw = rs.getObject(1);
                String display = raw != null ? String.valueOf(raw) : null;
                long count = rs.getLong(2);
                field.observeValueCount(
                    raw, display, count, RowProfileCollector.DEFAULT_MAX_DISTINCT);
              }
            } finally {
              db.closeQuery(rs);
            }
          }
        } catch (Exception e) {
          // Without value distribution, range/null rules still work; allowed-values may be weak.
          long nonNull = Math.max(0, snapshot.getRowCount() - nullCount);
          if (field.getNonNullCount() < nonNull) {
            field.observeValueCount(
                "?", "?", nonNull - field.getNonNullCount(), 1);
          }
        }
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Error profiling database table " + quotedTable, e);
    }
    return snapshot;
  }

  private static Comparable<?> asComparable(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Comparable<?> comparable) {
      return comparable;
    }
    return value.toString();
  }

  private static Set<String> collectFieldNames(Collection<DataQualityRule> rules) {
    Set<String> fields = new LinkedHashSet<>();
    if (rules == null) {
      return fields;
    }
    for (DataQualityRule rule : rules) {
      if (rule == null || Utils.isEmpty(rule.getFieldName())) {
        continue;
      }
      if (rule.getType() == DataQualityRuleType.MIN_ROW_COUNT
          || rule.getType() == DataQualityRuleType.MAX_ROW_COUNT) {
        continue;
      }
      fields.add(rule.getFieldName().trim());
    }
    return fields;
  }

  private static long queryLong(Database db, String sql) throws Exception {
    ResultSet rs = db.openQuery(sql);
    try {
      if (rs != null && rs.next()) {
        return rs.getLong(1);
      }
      return 0L;
    } finally {
      if (rs != null) {
        db.closeQuery(rs);
      }
    }
  }

  private static Object[] queryRow(Database db, String sql) throws Exception {
    ResultSet rs = db.openQuery(sql);
    try {
      if (rs != null && rs.next()) {
        int cols = rs.getMetaData().getColumnCount();
        Object[] row = new Object[cols];
        for (int i = 0; i < cols; i++) {
          row[i] = rs.getObject(i + 1);
        }
        return row;
      }
      return null;
    } finally {
      if (rs != null) {
        db.closeQuery(rs);
      }
    }
  }
}
