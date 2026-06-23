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
      ddl.append(
          databaseMeta.getFieldDefinition(valueMeta, primaryKeyColumn, null, false));
    }

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
}