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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Helpers for generating Data Vault target table DDL. */
public final class DvDdlSupport {

  private DvDdlSupport() {}

  public static boolean isSingleStore(DatabaseMeta databaseMeta) {
    return databaseMeta != null
        && "SINGLESTORE".equalsIgnoreCase(databaseMeta.getPluginId());
  }

  public static boolean isShardKeyDdlEnabled(
      DataVaultConfiguration config, DatabaseMeta databaseMeta) {
    return config != null
        && config.isSingleStoreShardKeyOnHashKey()
        && isSingleStore(databaseMeta);
  }

  /**
   * Returns CREATE TABLE DDL with an optional SingleStore {@code SHARD KEY} clause, or the existing
   * Hop DDL when the table already exists (ALTER) or no DDL is required.
   */
  public static String getCreateTableDdl(
      Database db,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      String primaryKeyColumn,
      boolean semicolon)
      throws HopDatabaseException {
    if (db == null || Utils.isEmpty(tableName) || fields == null || fields.isEmpty()) {
      return "";
    }

    DatabaseMeta databaseMeta = db.getDatabaseMeta();
    IRowMeta layout = fields.clone();
    databaseMeta.quoteReservedWords(layout);

    String existingDdl = db.getDDL(tableName, layout);
    if (Utils.isEmpty(existingDdl)) {
      return "";
    }
    if (!isCreateTableDdl(existingDdl)) {
      return existingDdl;
    }

    return buildCreateTableStatement(
        databaseMeta, db, tableName, layout, shardKeyColumns, primaryKeyColumn, semicolon);
  }

  static boolean isCreateTableDdl(String ddl) {
    if (Utils.isEmpty(ddl)) {
      return false;
    }
    return ddl.trim().regionMatches(true, 0, "CREATE", 0, 6);
  }

  /**
   * Extracts the physical table name from a CREATE TABLE statement. Returns {@code null} when the
   * statement cannot be parsed.
   */
  public static String extractCreateTableName(String ddl) {
    if (!isCreateTableDdl(ddl)) {
      return null;
    }
    String remainder =
        ddl.trim()
            .replaceFirst(
                "(?is)^CREATE\\s+(OR\\s+REPLACE\\s+)?TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?", "");
    remainder = remainder.trim();
    if (remainder.isEmpty()) {
      return null;
    }
    if (remainder.charAt(0) == '"') {
      int endQuote = remainder.indexOf('"', 1);
      if (endQuote > 1) {
        return remainder.substring(1, endQuote);
      }
    }
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < remainder.length(); i++) {
      char c = remainder.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
        name.append(c);
      } else {
        break;
      }
    }
    String parsed = name.toString();
    if (parsed.isEmpty()) {
      return null;
    }
    int dot = parsed.lastIndexOf('.');
    return dot >= 0 ? parsed.substring(dot + 1) : parsed;
  }

  /**
   * Removes duplicate CREATE TABLE statements that target the same physical table. Keeps the first
   * statement for each table name.
   */
  public static List<String> deduplicateCreateTableDdl(List<String> ddlStatements) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return ddlStatements;
    }
    List<String> result = new ArrayList<>(ddlStatements.size());
    Set<String> seenCreateTables = new HashSet<>();
    for (String ddl : ddlStatements) {
      if (isCreateTableDdl(ddl)) {
        String tableName = extractCreateTableName(ddl);
        if (!Utils.isEmpty(tableName)) {
          String key = tableName.toLowerCase(Locale.ROOT);
          if (seenCreateTables.contains(key)) {
            continue;
          }
          seenCreateTables.add(key);
        }
      }
      result.add(ddl);
    }
    return result;
  }

  /**
   * Returns {@code true} when a CREATE TABLE statement should not be executed because the table
   * was already created in the current batch or already exists in the target database.
   */
  public static boolean shouldSkipCreateTable(
      Database db,
      IVariables variables,
      DatabaseMeta databaseMeta,
      String ddl,
      Set<String> createdInBatch)
      throws HopDatabaseException {
    if (!isCreateTableDdl(ddl)) {
      return false;
    }
    String tableName = extractCreateTableName(ddl);
    if (Utils.isEmpty(tableName)) {
      return false;
    }
    String key = tableName.toLowerCase(Locale.ROOT);
    if (createdInBatch != null && createdInBatch.contains(key)) {
      return true;
    }
    if (db == null) {
      return false;
    }
    String schema =
        databaseMeta != null && variables != null
            ? variables.resolve(databaseMeta.getPreferredSchemaName())
            : databaseMeta != null ? databaseMeta.getPreferredSchemaName() : null;
    if (db.checkTableExists(schema, tableName)) {
      return true;
    }
    return createdInBatch != null && createdInBatch.contains(key);
  }

  /**
   * Builds a CREATE TABLE statement using the same field-definition primitives as Hop's {@link
   * Database#getCreateTableStatement(String, IRowMeta, String, boolean, String, boolean)}.
   */
  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      String primaryKeyColumn,
      boolean semicolon) {
    List<String> primaryKeyFieldNames =
        Utils.isEmpty(primaryKeyColumn) ? List.of() : List.of(primaryKeyColumn);
    return buildCreateTableStatement(
        databaseMeta, variables, tableName, fields, shardKeyColumns, primaryKeyFieldNames, semicolon);
  }

  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      boolean semicolon) {
    if (databaseMeta == null || Utils.isEmpty(tableName) || fields == null || fields.isEmpty()) {
      return "";
    }

    IDatabase database = databaseMeta.getIDatabase();
    StringBuilder ddl = new StringBuilder();
    ddl.append(database.getCreateTableStatement());
    ddl.append(tableName);
    ddl.append(Const.CR).append("(").append(Const.CR);

    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        ddl.append(",").append(Const.CR);
      }
      IValueMeta valueMeta = fields.getValueMeta(i);
      // Use a table-level PRIMARY KEY clause so JDBC discovery finds the constraint and
      // PostgreSQL does not emit BIGSERIAL for the first key column.
      ddl.append(databaseMeta.getFieldDefinition(valueMeta, null, null, false));
    }

    appendPrimaryKeyClause(ddl, databaseMeta, primaryKeyFieldNames);

    if (shardKeyColumns != null && shardKeyColumns.length > 0) {
      ddl.append(",").append(Const.CR);
      ddl.append("SHARD KEY (");
      for (int i = 0; i < shardKeyColumns.length; i++) {
        if (i > 0) {
          ddl.append(", ");
        }
        ddl.append(databaseMeta.quoteField(shardKeyColumns[i]));
      }
      ddl.append(")");
    }

    ddl.append(")").append(Const.CR);
    ddl.append(database.getDataTablespaceDDL(variables, databaseMeta));

    if (semicolon) {
      ddl.append(";");
    }
    return ddl.toString();
  }

  static void appendPrimaryKeyClause(
      StringBuilder ddl, DatabaseMeta databaseMeta, List<String> primaryKeyFieldNames) {
    if (primaryKeyFieldNames == null || primaryKeyFieldNames.isEmpty()) {
      return;
    }
    ddl.append(",").append(Const.CR);
    ddl.append("PRIMARY KEY (");
    for (int i = 0; i < primaryKeyFieldNames.size(); i++) {
      if (i > 0) {
        ddl.append(", ");
      }
      ddl.append(databaseMeta.quoteField(primaryKeyFieldNames.get(i)));
    }
    ddl.append(")");
  }
}