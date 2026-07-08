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

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Builds ORDER BY clauses with optional SQL Server COLLATE for hub business-key alignment. */
public final class DvSqlOrderBySupport {

  private DvSqlOrderBySupport() {}

  public static void appendOrderBy(
      StringBuilder sql,
      List<BusinessKey> businessKeys,
      List<String> quotedFieldExpressions,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables) {
    if (quotedFieldExpressions == null || quotedFieldExpressions.isEmpty()) {
      return;
    }
    sql.append(" ORDER BY ");
    List<String> orderExpressions = new ArrayList<>(quotedFieldExpressions.size());
    for (int i = 0; i < quotedFieldExpressions.size(); i++) {
      String quotedField = quotedFieldExpressions.get(i);
      BusinessKey businessKey =
          businessKeys != null && i < businessKeys.size() ? businessKeys.get(i) : null;
      orderExpressions.add(
          orderExpression(quotedField, businessKey, databaseMeta, config, variables));
    }
    sql.append(String.join(", ", orderExpressions));
  }

  static String orderExpression(
      String quotedField,
      BusinessKey businessKey,
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables) {
    if (Utils.isEmpty(quotedField)) {
      return quotedField;
    }
    if (!isSqlServer(databaseMeta) || !isStringBusinessKey(businessKey, variables)) {
      return quotedField;
    }
    String collation = resolveCollation(businessKey, config, variables);
    if (Utils.isEmpty(collation)) {
      return quotedField;
    }
    return quotedField + " COLLATE " + collation;
  }

  public static String resolveCollation(
      BusinessKey businessKey, DataVaultConfiguration config, IVariables variables) {
    if (businessKey != null && !Utils.isEmpty(businessKey.getOrderByCollation())) {
      String perKey = businessKey.getOrderByCollation();
      return variables != null ? variables.resolve(perKey) : perKey;
    }
    if (config != null) {
      return config.resolveHubOrderByCollation(variables);
    }
    return null;
  }

  static boolean isSqlServer(DatabaseMeta databaseMeta) {
    if (databaseMeta == null) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    return DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId);
  }

  static boolean isStringBusinessKey(BusinessKey businessKey, IVariables variables) {
    if (businessKey == null) {
      return false;
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
}