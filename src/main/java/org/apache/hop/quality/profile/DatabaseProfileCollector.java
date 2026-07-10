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
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.engine.evaluators.RegexEvaluator;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;

/** SQL pushdown profile collection for database tables. */
public final class DatabaseProfileCollector {

  private static final LoggingObject LOGGING_OBJECT =
      new LoggingObject("hop-quality-DatabaseProfileCollector");

  /** Bounded sample size for REGEX fall-through (no unbounded GROUP BY for REGEX alone). */
  public static final int REGEX_SAMPLE_LIMIT = 500;

  /** Max accepted author regex pattern length for SQL pushdown. */
  public static final int REGEX_PATTERN_MAX_LENGTH = 512;

  private static final Set<DataQualityRuleType> DATASET_ONLY_TYPES =
      EnumSet.of(DataQualityRuleType.MIN_ROW_COUNT, DataQualityRuleType.MAX_ROW_COUNT);

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

    Map<String, FieldNeeds> needsByField = analyzeNeeds(rules);
    String pluginId = databaseMeta.getPluginId() != null ? databaseMeta.getPluginId() : "";

    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      snapshot.setRowCount(queryLong(db, "SELECT COUNT(*) FROM " + quotedTable));

      for (Map.Entry<String, FieldNeeds> entry : needsByField.entrySet()) {
        String fieldName = entry.getKey();
        FieldNeeds needs = entry.getValue();
        String quotedField = databaseMeta.quoteField(fieldName);
        FieldProfile field = snapshot.field(fieldName);

        if (needs.nullCount) {
          long nullCount =
              queryLong(
                  db, "SELECT COUNT(*) FROM " + quotedTable + " WHERE " + quotedField + " IS NULL");
          field.addNullCount(nullCount);
        }

        if (needs.emptyString) {
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
        }

        if (needs.minMaxValue) {
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
        }

        if (needs.exactDistinct) {
          try {
            long distinct =
                queryLong(
                    db,
                    "SELECT COUNT(DISTINCT "
                        + quotedField
                        + ") FROM "
                        + quotedTable
                        + " WHERE "
                        + quotedField
                        + " IS NOT NULL");
            field.setExactDistinctCount(distinct);
          } catch (Exception ignored) {
            // fall back to distribution sample if collected
          }
        }

        if (needs.stringLength) {
          try {
            String lengthExpr = charLengthExpression(pluginId, quotedField);
            Object[] minMax =
                queryRow(
                    db,
                    "SELECT MIN("
                        + lengthExpr
                        + "), MAX("
                        + lengthExpr
                        + ") FROM "
                        + quotedTable
                        + " WHERE "
                        + quotedField
                        + " IS NOT NULL");
            if (minMax != null) {
              field.setStringLengthFromSql(asInteger(minMax[0]), asInteger(minMax[1]));
            }
          } catch (Exception ignored) {
            // best-effort
          }
        }

        if (needs.valueDistribution) {
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
                  if (display != null) {
                    field.observeStringLength(display.length());
                  }
                }
              } finally {
                db.closeQuery(rs);
              }
            }
          } catch (Exception e) {
            long nullCount = field.getNullCount();
            long nonNull = Math.max(0, snapshot.getRowCount() - nullCount);
            if (field.getNonNullCount() < nonNull) {
              field.observeValueCount("?", "?", nonNull - field.getNonNullCount(), 1);
            }
          }
        }

        for (DataQualityRule regexRule : needs.regexRules) {
          collectRegex(db, pluginId, quotedTable, quotedField, field, regexRule);
        }
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Error profiling database table " + quotedTable, e);
    }
    return snapshot;
  }

  private static void collectRegex(
      Database db,
      String pluginId,
      String quotedTable,
      String quotedField,
      FieldProfile field,
      DataQualityRule rule) {
    String ruleKey = RegexEvaluator.ruleKey(rule);
    FieldProfile.RegexRuleProfile stats = field.regexProfile(ruleKey);
    String patternText = rule.parameter(DataQualityRule.PARAM_PATTERN);

    if (!isPushdownSafePattern(patternText)) {
      // Fall through to bounded sample
      collectRegexSample(db, quotedTable, quotedField, field, rule, stats);
      return;
    }

    if (supportsPostgresRegex(pluginId)) {
      try {
        String sqlLiteral = escapeSqlStringLiteral(patternText);
        boolean full = RegexEvaluator.isFullMatch(rule);
        boolean caseSensitive = rule.parameterBoolean(DataQualityRule.PARAM_CASE_SENSITIVE, true);
        String operator = caseSensitive ? "~" : "~*";
        String patternExpr;
        if (full) {
          // FULL: match entire value. Pattern is a SQL string literal constant.
          patternExpr = "'^(?:' || " + sqlLiteral + " || ')$'";
        } else {
          patternExpr = sqlLiteral;
        }
        String sql =
            "SELECT COUNT(*) FROM "
                + quotedTable
                + " WHERE "
                + quotedField
                + " IS NOT NULL AND NOT ("
                + quotedField
                + " "
                + operator
                + " "
                + patternExpr
                + ")";
        long mismatch = queryLong(db, sql);
        stats.setPath(RegexEvaluator.PATH_PUSHDOWN);
        stats.setMismatchCount(mismatch);
        return;
      } catch (Exception e) {
        // fall through to sample
      }
    }

    // Optional: if value distribution already present, client path in evaluator covers it
    if (!field.getValueCounts().isEmpty()) {
      evaluateRegexOnValueCounts(field, rule, stats);
      return;
    }

    collectRegexSample(db, quotedTable, quotedField, field, rule, stats);
  }

  private static void collectRegexSample(
      Database db,
      String quotedTable,
      String quotedField,
      FieldProfile field,
      DataQualityRule rule,
      FieldProfile.RegexRuleProfile stats) {
    String patternText = rule.parameter(DataQualityRule.PARAM_PATTERN);
    if (Utils.isEmpty(patternText)) {
      stats.setSkipped(true);
      stats.setPath("none");
      return;
    }
    Pattern pattern;
    try {
      pattern = RegexEvaluator.compile(rule, patternText);
    } catch (PatternSyntaxException e) {
      stats.setSkipped(true);
      stats.setPath("none");
      return;
    }
    boolean full = RegexEvaluator.isFullMatch(rule);
    long sampleSize = 0;
    long mismatch = 0;
    try {
      // Prefer dialect LIMIT; PostgreSQL/MySQL accept LIMIT N.
      String sql =
          "SELECT DISTINCT "
              + quotedField
              + " FROM "
              + quotedTable
              + " WHERE "
              + quotedField
              + " IS NOT NULL LIMIT "
              + REGEX_SAMPLE_LIMIT;
      ResultSet rs = db.openQuery(sql);
      if (rs != null) {
        try {
          while (rs.next()) {
            Object raw = rs.getObject(1);
            if (raw == null) {
              continue;
            }
            String display = String.valueOf(raw);
            sampleSize++;
            if (!RegexEvaluator.matches(pattern, display, full)) {
              mismatch++;
            }
            // Seed value distribution for other consumers (bounded).
            field.observeValueCount(
                raw, display, 1L, Math.max(RowProfileCollector.DEFAULT_MAX_DISTINCT, REGEX_SAMPLE_LIMIT));
          }
        } finally {
          db.closeQuery(rs);
        }
      }
      stats.setPath(RegexEvaluator.PATH_SAMPLE);
      stats.setSampleSize(sampleSize);
      stats.setSampleMismatchCount(mismatch);
      stats.setSampleLimit(REGEX_SAMPLE_LIMIT);
      stats.setCoverageIncomplete(mismatch == 0 && sampleSize >= REGEX_SAMPLE_LIMIT);
    } catch (Exception e) {
      stats.setSkipped(true);
      stats.setPath("none");
    }
  }

  private static void evaluateRegexOnValueCounts(
      FieldProfile field, DataQualityRule rule, FieldProfile.RegexRuleProfile stats) {
    String patternText = rule.parameter(DataQualityRule.PARAM_PATTERN);
    if (Utils.isEmpty(patternText)) {
      stats.setSkipped(true);
      stats.setPath("none");
      return;
    }
    Pattern pattern;
    try {
      pattern = RegexEvaluator.compile(rule, patternText);
    } catch (PatternSyntaxException e) {
      stats.setSkipped(true);
      stats.setPath("none");
      return;
    }
    boolean full = RegexEvaluator.isFullMatch(rule);
    long sampleSize = 0;
    long mismatchRows = 0;
    for (Map.Entry<String, Long> entry : field.getValueCounts().entrySet()) {
      sampleSize++;
      if (!RegexEvaluator.matches(pattern, entry.getKey(), full)) {
        mismatchRows += entry.getValue() != null ? entry.getValue() : 1L;
      }
    }
    stats.setPath(RegexEvaluator.PATH_SAMPLE);
    stats.setSampleSize(sampleSize);
    stats.setSampleMismatchCount(mismatchRows);
    stats.setSampleLimit(RowProfileCollector.DEFAULT_MAX_DISTINCT);
    stats.setCoverageIncomplete(mismatchRows == 0 && field.isDistinctTruncated());
  }

  static boolean isPushdownSafePattern(String pattern) {
    if (Utils.isEmpty(pattern)) {
      return false;
    }
    if (pattern.length() > REGEX_PATTERN_MAX_LENGTH) {
      return false;
    }
    if (pattern.indexOf('\u0000') >= 0) {
      return false;
    }
    return true;
  }

  /**
   * Escape a string as a SQL string literal (single-quoted, doubled quotes). Result includes the
   * surrounding single quotes.
   */
  public static String escapeSqlStringLiteral(String raw) {
    if (raw == null) {
      return "''";
    }
    return "'" + raw.replace("'", "''") + "'";
  }

  static boolean supportsPostgresRegex(String pluginId) {
    if (pluginId == null) {
      return false;
    }
    String id = pluginId.trim();
    return "POSTGRESQL".equalsIgnoreCase(id)
        || "GREENPLUM".equalsIgnoreCase(id)
        || "REDSHIFT".equalsIgnoreCase(id);
  }

  static String charLengthExpression(String pluginId, String quotedField) {
    if (pluginId != null) {
      String id = pluginId.trim();
      if ("MYSQL".equalsIgnoreCase(id) || "SINGLESTORE".equalsIgnoreCase(id)) {
        return "CHAR_LENGTH(" + quotedField + ")";
      }
    }
    // Postgres LENGTH on text is character length; default for others.
    return "LENGTH(" + quotedField + ")";
  }

  private static Map<String, FieldNeeds> analyzeNeeds(Collection<DataQualityRule> rules) {
    Map<String, FieldNeeds> map = new LinkedHashMap<>();
    if (rules == null) {
      return map;
    }
    for (DataQualityRule rule : rules) {
      if (rule == null || !rule.isEnabled() || Utils.isEmpty(rule.getFieldName())) {
        continue;
      }
      if (rule.getType() != null && DATASET_ONLY_TYPES.contains(rule.getType())) {
        continue;
      }
      String fieldName = rule.getFieldName().trim();
      FieldNeeds needs = map.computeIfAbsent(fieldName, f -> new FieldNeeds());
      // Always collect null counts for field-scoped rules (cheap; used by many types).
      needs.nullCount = true;
      DataQualityRuleType type = rule.getType();
      if (type == null) {
        continue;
      }
      switch (type) {
        case NOT_NULL, NULL_RATIO_MAX -> {
          // nullCount only
        }
        case NOT_EMPTY_STRING -> needs.emptyString = true;
        case RANGE -> needs.minMaxValue = true;
        case ALLOWED_VALUES -> needs.valueDistribution = true;
        case MIN_DISTINCT, MAX_DISTINCT -> needs.exactDistinct = true;
        case MIN_LENGTH, MAX_LENGTH -> needs.stringLength = true;
        case REGEX -> needs.regexRules.add(rule);
        default -> {
          // Unknown field-scoped types: collect basic nulls
        }
      }
    }
    return map;
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

  private static Integer asInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
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

  private static final class FieldNeeds {
    boolean nullCount;
    boolean emptyString;
    boolean minMaxValue;
    boolean valueDistribution;
    boolean exactDistinct;
    boolean stringLength;
    final List<DataQualityRule> regexRules = new ArrayList<>();
  }
}
