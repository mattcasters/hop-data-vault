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

package org.apache.hop.datavault.metadata.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DataVaultSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.bus.HopGuiEvents;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.metadata.MetadataPerspective;

/**
 * Shared helpers for importing {@link DvDatabaseSource} metadata from relational database tables.
 */
public final class DvDatabaseSourceImportSupport {

  private DvDatabaseSourceImportSupport() {}

  /**
   * Removes wrapping single, double, or back-quote characters from a table name returned by JDBC
   * metadata (e.g. {@code "order"} → {@code order}).
   */
  public static String stripTableNameQuotes(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return tableName;
    }
    String trimmed = tableName.trim();
    if (trimmed.length() < 2) {
      return trimmed;
    }
    char first = trimmed.charAt(0);
    char last = trimmed.charAt(trimmed.length() - 1);
    if ((first == '"' && last == '"')
        || (first == '\'' && last == '\'')
        || (first == '`' && last == '`')) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  public static String buildDefaultMetadataName(
      String prefix, String connectionName, String tableName) {
    String effectivePrefix = Const.NVL(prefix, "");
    if (Utils.isEmpty(effectivePrefix)) {
      return connectionName + "-" + tableName;
    }
    return effectivePrefix + tableName;
  }

  public static String uniqueMetadataName(IHopMetadataSerializer<?> serializer, String baseName)
      throws HopException {
    String candidate = Const.NVL(baseName, "");
    if (Utils.isEmpty(candidate)) {
      throw new HopException("Metadata name cannot be empty");
    }
    if (!serializer.exists(candidate)) {
      return candidate;
    }
    int suffix = 2;
    while (serializer.exists(candidate + "_" + suffix)) {
      suffix++;
    }
    return candidate + "_" + suffix;
  }

  public static List<SourceField> importFieldsFromTable(
      Database db, IVariables variables, String schemaName, String tableName)
      throws HopDatabaseException, HopException {
    String resolvedSchema = variables != null ? variables.resolve(schemaName) : schemaName;
    String resolvedTable = variables != null ? variables.resolve(tableName) : tableName;

    IRowMeta rowMeta = db.getTableFieldsMeta(resolvedSchema, resolvedTable);
    if (rowMeta == null || rowMeta.isEmpty()) {
      return List.of();
    }

    Set<String> primaryKeys =
        toLowerCaseSet(getPrimaryKeyColumnNames(db, resolvedSchema, resolvedTable));

    List<SourceField> fields = new ArrayList<>();
    for (IValueMeta vm : rowMeta.getValueMetaList()) {
      SourceField sf = new SourceField(vm.getName());
      sf.setDescription("");
      sf.setSourceDataType(vm.getTypeDesc());
      sf.setLength(vm.getLength() > 0 ? String.valueOf(vm.getLength()) : "");
      sf.setPrecision(vm.getPrecision() >= 0 ? String.valueOf(vm.getPrecision()) : "");
      sf.setHopType(vm.getType());
      sf.setPrimaryKey(primaryKeys.contains(vm.getName().toLowerCase(Locale.ROOT)));
      fields.add(sf);
    }
    return fields;
  }

  public static String[] getPrimaryKeyColumnNames(
      Database db, String schemaName, String tableName) throws HopDatabaseException {
    List<String> names = new ArrayList<>();
    ResultSet allKeys = null;
    try {
      String schema = Utils.isEmpty(schemaName) ? null : schemaName;
      allKeys = db.getDatabaseMetaData().getPrimaryKeys(null, schema, tableName);
      while (allKeys.next()) {
        String columnName = allKeys.getString("COLUMN_NAME");
        if (!Utils.isEmpty(columnName) && !names.contains(columnName)) {
          names.add(columnName);
        }
      }
    } catch (SQLException e) {
      throw new HopDatabaseException(
          "Error getting primary key columns for table [" + tableName + "]", e);
    } finally {
      if (allKeys != null) {
        try {
          allKeys.close();
        } catch (SQLException e) {
          // best effort
        }
      }
    }
    return names.toArray(new String[0]);
  }

  public static DvDatabaseSource createDatabaseSource(
      String metadataName,
      String connectionName,
      String schemaName,
      String tableName,
      List<SourceField> fields) {
    DvDatabaseSource source = new DvDatabaseSource(metadataName);
    source.setDatabaseName(connectionName);
    source.setSchemaName(schemaName);
    source.setTableName(tableName);
    source.setFields(fields);
    return source;
  }

  public static DataVaultSource createDataVaultSource(String metadataName, String databaseSourceName) {
    DataVaultSource source = new DataVaultSource(metadataName);
    source.setSourceType(DataVaultSourceType.DATABASE);
    source.setSourceTableName(databaseSourceName);
    return source;
  }

  public static <T extends IHopMetadata> void saveNewMetadata(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      ILogChannel log,
      T metadata)
      throws HopException {
    @SuppressWarnings("unchecked")
    IHopMetadataSerializer<T> serializer =
        (IHopMetadataSerializer<T>) metadataProvider.getSerializer(metadata.getClass());
    serializer.save(metadata);
    ExtensionPointHandler.callExtensionPoint(
        log != null ? log : LogChannel.GENERAL,
        variables,
        HopExtensionPoint.HopGuiMetadataObjectCreated.id,
        metadata);
  }

  public static void refreshMetadataPerspective(HopGui hopGui) throws HopException {
    MetadataPerspective.getInstance().refresh();
    hopGui.getEventsHandler().fire(HopGuiEvents.MetadataCreated.name());
  }

  private static Set<String> toLowerCaseSet(String[] values) {
    Set<String> result = new HashSet<>();
    if (values != null) {
      for (String value : values) {
        if (!Utils.isEmpty(value)) {
          result.add(value.toLowerCase(Locale.ROOT));
        }
      }
    }
    return result;
  }
}