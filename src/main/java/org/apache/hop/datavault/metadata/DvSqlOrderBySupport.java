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
 */

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Builds ORDER BY clauses with automatic SQL Server COLLATE when live source/target metadata shows
 * a sort risk. The same bridge collation is applied on both merge legs for a given key.
 */
public final class DvSqlOrderBySupport {

  private DvSqlOrderBySupport() {}

  /**
   * A single ORDER BY term: quoted SQL expression plus logical names used for collation lookup.
   *
   * @param quotedExpression SQL fragment (already quoted)
   * @param sourceColumnName source table column name for live meta lookup (may be null)
   * @param targetColumnName target table column name for live meta lookup (may be null)
   * @param stringTyped whether this term is a string sort key
   */
  public record OrderByField(
      String quotedExpression,
      String sourceColumnName,
      String targetColumnName,
      boolean stringTyped) {}

  public static void appendOrderBy(
      StringBuilder sql,
      List<BusinessKey> businessKeys,
      List<String> quotedFieldExpressions,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables) {
    appendOrderBy(
        sql, businessKeys, quotedFieldExpressions, databaseMeta, config, variables, null);
  }

  public static void appendOrderBy(
      StringBuilder sql,
      List<BusinessKey> businessKeys,
      List<String> quotedFieldExpressions,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables,
      DvSqlOrderByCollationSupport.Session session) {
    if (quotedFieldExpressions == null || quotedFieldExpressions.isEmpty()) {
      return;
    }
    List<OrderByField> fields = new ArrayList<>(quotedFieldExpressions.size());
    for (int i = 0; i < quotedFieldExpressions.size(); i++) {
      String quotedField = quotedFieldExpressions.get(i);
      BusinessKey businessKey =
          businessKeys != null && i < businessKeys.size() ? businessKeys.get(i) : null;
      boolean stringTyped = isStringBusinessKey(businessKey, variables);
      String sourceName = null;
      String targetName = null;
      if (businessKey != null) {
        targetName = resolveName(businessKey.getName(), variables);
        sourceName =
            !Utils.isEmpty(businessKey.getSourceFieldName())
                ? resolveName(businessKey.getSourceFieldName(), variables)
                : targetName;
      }
      fields.add(new OrderByField(quotedField, sourceName, targetName, stringTyped));
    }
    appendOrderByFields(sql, fields, databaseMeta, config, variables, session);
  }

  /** Appends {@code ORDER BY} for arbitrary field lists (hubs, satellites, STS). */
  public static void appendOrderByFields(
      StringBuilder sql,
      List<OrderByField> fields,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables,
      DvSqlOrderByCollationSupport.Session session) {
    if (fields == null || fields.isEmpty()) {
      return;
    }
    sql.append(" ORDER BY ");
    List<String> orderExpressions = new ArrayList<>(fields.size());
    for (OrderByField field : fields) {
      orderExpressions.add(orderExpression(field, databaseMeta, session));
    }
    sql.append(String.join(", ", orderExpressions));
  }

  static String orderExpression(
      String quotedField,
      BusinessKey businessKey,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables) {
    return orderExpression(quotedField, businessKey, databaseMeta, config, variables, null);
  }

  static String orderExpression(
      String quotedField,
      BusinessKey businessKey,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables,
      DvSqlOrderByCollationSupport.Session session) {
    boolean stringTyped = isStringBusinessKey(businessKey, variables);
    String sourceName = null;
    String targetName = null;
    if (businessKey != null) {
      targetName = resolveName(businessKey.getName(), variables);
      sourceName =
          !Utils.isEmpty(businessKey.getSourceFieldName())
              ? resolveName(businessKey.getSourceFieldName(), variables)
              : targetName;
    }
    return orderExpression(
        new OrderByField(quotedField, sourceName, targetName, stringTyped),
        databaseMeta,
        session);
  }

  static String orderExpression(
      OrderByField field,
      DatabaseMeta databaseMeta,
      DvSqlOrderByCollationSupport.Session session) {
    if (field == null || Utils.isEmpty(field.quotedExpression())) {
      return field != null ? field.quotedExpression() : null;
    }
    if (!isSqlServer(databaseMeta) || !field.stringTyped()) {
      return field.quotedExpression();
    }
    String collation = resolveCollation(field, session);
    if (Utils.isEmpty(collation)) {
      return field.quotedExpression();
    }
    return field.quotedExpression() + " COLLATE " + collation;
  }

  /**
   * Auto-bridge when live meta shows a sort risk. Same bridge should be used on source and target
   * ORDER BY for a given key.
   */
  public static String resolveCollation(
      OrderByField field, DvSqlOrderByCollationSupport.Session session) {
    DvSqlOrderByCollationSupport.ColumnSqlMeta sourceMeta = null;
    DvSqlOrderByCollationSupport.ColumnSqlMeta targetMeta = null;
    String sourceDefault = null;
    String targetDefault = null;
    if (session != null) {
      sourceMeta = session.sourceColumn(field != null ? field.sourceColumnName() : null);
      targetMeta = session.targetColumn(field != null ? field.targetColumnName() : null);
      if (sourceMeta == null && field != null && !Utils.isEmpty(field.targetColumnName())) {
        sourceMeta = session.sourceColumn(field.targetColumnName());
      }
      if (targetMeta == null && field != null && !Utils.isEmpty(field.sourceColumnName())) {
        targetMeta = session.targetColumn(field.sourceColumnName());
      }
      sourceDefault = session.sourceDbDefaultCollation();
      targetDefault = session.targetDbDefaultCollation();
    }
    return DvSqlOrderByCollationSupport.resolveBridgeCollation(
        sourceMeta, targetMeta, sourceDefault, targetDefault);
  }

  public static boolean isSqlServer(DatabaseMeta databaseMeta) {
    if (databaseMeta == null) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    return DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId);
  }

  static boolean isStringBusinessKey(BusinessKey businessKey, IVariables variables) {
    if (businessKey == null) {
      return true;
    }
    String dataType = Const.NVL(businessKey.getDataType(), "").trim();
    if (Utils.isEmpty(dataType)) {
      return true;
    }
    if (variables != null) {
      dataType = variables.resolve(dataType);
    }
    try {
      int typeId = ValueMetaFactory.getIdForValueMeta(dataType);
      return typeId == IValueMeta.TYPE_STRING;
    } catch (Exception e) {
      return true;
    }
  }

  private static String resolveName(String name, IVariables variables) {
    if (name == null) {
      return null;
    }
    return variables != null ? variables.resolve(name) : name;
  }
}
