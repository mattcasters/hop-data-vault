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

package org.apache.hop.datavault.metadata.database;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.bus.HopGuiEvents;
import org.apache.hop.ui.core.dialog.EditRowsDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.metadata.MetadataPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * Shared helpers for importing {@link DvDatabaseSource} metadata from relational database tables.
 */
public final class DvDatabaseSourceImportSupport {

  private static final Class<?> PKG = DataVaultSourceDatabasePanel.class;

  private DvDatabaseSourceImportSupport() {}

  /** Bulk-import database tables as Data Vault Source metadata objects. */
  public static void importDatabaseTables(
      Shell shell, HopGui hopGui, IVariables variables, IHopMetadataProvider metadataProvider) {
    ImportDatabaseTablesOptionsDialog optionsDialog =
        new ImportDatabaseTablesOptionsDialog(shell, variables, metadataProvider);
    ImportDatabaseTablesOptionsDialog.ImportDatabaseTablesOptions options = optionsDialog.open();
    if (options == null) {
      return;
    }

    String connectionName = Const.NVL(options.getDatabaseName(), "");
    if (Utils.isEmpty(connectionName)) {
      showError(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogMessage"));
      return;
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName),
          e);
      return;
    }

    if (databaseMeta == null) {
      showError(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName));
      return;
    }

    String schemaName = Const.NVL(options.getSchemaName(), "");
    String[] tableNames;
    ILoggingObject loggingObject =
        new SimpleLoggingObject("DvDatabaseSourceImport", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, variables, databaseMeta)) {
      db.connect();
      tableNames = db.getTablenames(schemaName, false);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorListingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorListingTables.DialogMessage"),
          e);
      return;
    }

    if (tableNames == null || tableNames.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTablesFound.DialogMessage"));
      mb.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTablesFound.DialogTitle"));
      mb.open();
      return;
    }

    IRowMeta selectionRowMeta = new RowMeta();
    try {
      selectionRowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Column.TableName"),
              IValueMeta.TYPE_STRING));
      selectionRowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(
                  PKG, "DvDatabaseSourceEditor.ImportTables.Column.DataVaultSourceName"),
              IValueMeta.TYPE_STRING));
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    List<Object[]> selectionRows = new ArrayList<>();
    IHopMetadataSerializer<DataVaultSource> dataVaultSourceSerializer;
    try {
      dataVaultSourceSerializer = metadataProvider.getSerializer(DataVaultSource.class);
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    String prefix = Const.NVL(options.getDataVaultSourcePrefix(), "");
    for (String rawTableName : tableNames) {
      String tableName = stripTableNameQuotes(rawTableName);
      String dataVaultSourceName =
          buildDefaultMetadataName(prefix, connectionName, schemaName, tableName);
      try {
        dataVaultSourceName = uniqueMetadataName(dataVaultSourceSerializer, dataVaultSourceName);
        selectionRows.add(new Object[] {tableName, dataVaultSourceName});
      } catch (HopException e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
            e);
        return;
      }
    }

    EditRowsDialog tableSelectionDialog =
        new EditRowsDialog(
            shell,
            SWT.NONE,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Title"),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Message"),
            selectionRowMeta,
            selectionRows);
    List<Object[]> selectedRows = tableSelectionDialog.open();
    if (selectedRows == null) {
      return;
    }

    int importedCount = 0;
    List<String> errors = new ArrayList<>();
    try (Database db = new Database(loggingObject, variables, databaseMeta)) {
      db.connect();

      for (Object[] row : selectedRows) {
        if (row == null || row.length < 2) {
          continue;
        }
        String tableName =
            stripTableNameQuotes(row[0] != null ? row[0].toString() : null);
        String dataVaultSourceName = row[1] != null ? row[1].toString() : null;
        if (Utils.isEmpty(tableName) || Utils.isEmpty(dataVaultSourceName)) {
          continue;
        }

        try {
          if (dataVaultSourceSerializer.exists(dataVaultSourceName)) {
            errors.add(
                BaseMessages.getString(
                    PKG,
                    "DvDatabaseSourceEditor.ImportTables.Exists.Message",
                    dataVaultSourceName,
                    tableName));
            continue;
          }

          List<SourceField> fields = importFieldsFromTable(db, variables, schemaName, tableName);
          DataVaultSource imported =
              createDataVaultSource(
                  dataVaultSourceName, connectionName, schemaName, tableName, fields);
          saveNewMetadata(metadataProvider, variables, hopGui.getLog(), imported);
          importedCount++;
        } catch (Exception e) {
          errors.add(
              BaseMessages.getString(
                  PKG,
                  "DvDatabaseSourceEditor.ImportTables.TableError.Message",
                  tableName,
                  e.getMessage()));
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    try {
      refreshMetadataPerspective(hopGui);
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorRefreshingTree.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorRefreshingTree.DialogMessage"),
          e);
    }

    if (!errors.isEmpty()) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.PartialFailure.Title"),
          BaseMessages.getString(
              PKG,
              "DvDatabaseSourceEditor.ImportTables.PartialFailure.Message",
              importedCount,
              String.join(Const.CR, errors)),
          null);
      return;
    }

    MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
    mb.setMessage(
        BaseMessages.getString(
            PKG, "DvDatabaseSourceEditor.ImportTables.Success.Message", importedCount));
    mb.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Success.Title"));
    mb.open();
  }

  private static void showError(Shell shell, String title, String message) {
    MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
    mb.setMessage(message);
    mb.setText(title);
    mb.open();
  }

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
    return buildDefaultMetadataName(prefix, connectionName, null, tableName);
  }

  public static String buildDefaultMetadataName(
      String prefix, String connectionName, String schemaName, String tableName) {
    String effectivePrefix = Const.NVL(prefix, "");
    if (Utils.isEmpty(effectivePrefix)) {
      return buildSuggestedSourceName(connectionName, schemaName, tableName);
    }
    return effectivePrefix + tableName;
  }

  /** Suggested Data Vault Source name: {@code connection[-schema]-table}. */
  public static String buildSuggestedSourceName(
      String connectionName, String schemaName, String tableName) {
    String connection = Const.NVL(connectionName, "");
    String schema = Const.NVL(schemaName, "").trim();
    String table = stripTableNameQuotes(Const.NVL(tableName, ""));
    if (Utils.isEmpty(schema)) {
      return connection + "-" + table;
    }
    return connection + "-" + schema + "-" + table;
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

    List<SourceField> fields = new ArrayList<>();
    for (IValueMeta vm : rowMeta.getValueMetaList()) {
      SourceField sf = new SourceField(vm.getName());
      sf.setDescription("");
      sf.setSourceDataType(vm.getTypeDesc());
      sf.setLength(vm.getLength() > 0 ? String.valueOf(vm.getLength()) : "");
      sf.setPrecision(vm.getPrecision() >= 0 ? String.valueOf(vm.getPrecision()) : "");
      sf.setHopType(vm.getType());
      fields.add(sf);
    }
    return fields;
  }

  public static DvDatabaseSource createDatabaseSource(
      String connectionName, String schemaName, String tableName, List<SourceField> fields) {
    DvDatabaseSource source = new DvDatabaseSource();
    source.setDatabaseName(connectionName);
    source.setSchemaName(schemaName);
    source.setTableName(tableName);
    source.setFields(fields);
    return source;
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName,
      String connectionName,
      String schemaName,
      String tableName,
      List<SourceField> fields) {
    DataVaultSource source = new DataVaultSource(metadataName);
    source.setSource(createDatabaseSource(connectionName, schemaName, tableName, fields));
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

}