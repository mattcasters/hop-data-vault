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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.database.dialog.DatabaseExplorerDialog;
import org.apache.hop.ui.core.dialog.EditRowsDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * The editor for Data Vault Database Source metadata.
 *
 * <p>Uses a {@link MetaSelectionLine} for selecting the RDBMS connection (by name) and a
 * {@link TableView} for editing the list of expected source fields (row layout).
 */
@GuiPlugin(description = "Editor for Data Vault Database Source metadata")
public class DvDatabaseSourceEditor extends MetadataEditor<DvDatabaseSource> {

  private static final Class<?> PKG = DvDatabaseSourceEditor.class;

  private Composite parent;

  private Text wName;
  private Text wDescription;
  private MetaSelectionLine<DatabaseMeta> wDatabaseName;
  private Text wSchemaName;
  private Text wTableName;
  private TableView wFields;

  public DvDatabaseSourceEditor(
      HopGui hopGui,
      MetadataManager<DvDatabaseSource> manager,
      DvDatabaseSource metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    this.parent = parent;

    PropsUi props = PropsUi.getInstance();
    int margin = PropsUi.getMargin();
    int middle = props.getMiddlePct();

    // Name...
    //
    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin * 2);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);
    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    Control lastControl = wName;

    // Description
    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Description.Label"));
    FormData fdlDescription = new FormData();
    fdlDescription.top = new FormAttachment(lastControl, margin);
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);
    wDescription = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.top = new FormAttachment(wlDescription, 0, SWT.CENTER);
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);
    lastControl = wDescription;

    // Database connection (using MetaSelectionLine<DatabaseMeta>)
    //
    wDatabaseName =
        new MetaSelectionLine<>(
            manager.getVariables(),
            manager.getMetadataProvider(),
            DatabaseMeta.class,
            parent,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.DatabaseName.Label"),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.DatabaseName.ToolTip"));
    FormData fdDatabaseName = new FormData();
    fdDatabaseName.top = new FormAttachment(lastControl, margin);
    fdDatabaseName.left = new FormAttachment(0, 0);
    fdDatabaseName.right = new FormAttachment(100, 0);
    wDatabaseName.setLayoutData(fdDatabaseName);
    lastControl = wDatabaseName;

    // Schema name
    Label wlSchemaName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlSchemaName);
    wlSchemaName.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.SchemaName.Label"));
    FormData fdlSchemaName = new FormData();
    fdlSchemaName.top = new FormAttachment(lastControl, margin);
    fdlSchemaName.left = new FormAttachment(0, 0);
    fdlSchemaName.right = new FormAttachment(middle, -margin);
    wlSchemaName.setLayoutData(fdlSchemaName);
    wSchemaName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchemaName);
    FormData fdSchemaName = new FormData();
    fdSchemaName.top = new FormAttachment(wlSchemaName, 0, SWT.CENTER);
    fdSchemaName.left = new FormAttachment(middle, 0);
    fdSchemaName.right = new FormAttachment(100, 0);
    wSchemaName.setLayoutData(fdSchemaName);
    lastControl = wSchemaName;

    // Table / view name
    Label wlTableName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlTableName);
    wlTableName.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.TableName.Label"));
    FormData fdlTableName = new FormData();
    fdlTableName.top = new FormAttachment(lastControl, margin);
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);
    wTableName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.top = new FormAttachment(wlTableName, 0, SWT.CENTER);
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);
    lastControl = wTableName;

    // Fields (row layout) - TableView
    //
    Label wlFields = new Label(parent, SWT.LEFT);
    PropsUi.setLook(wlFields);
    wlFields.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Fields.Label"));
    FormData fdlFieldsLabel = new FormData();
    fdlFieldsLabel.top = new FormAttachment(lastControl, margin * 2);
    fdlFieldsLabel.left = new FormAttachment(0, 0);
    fdlFieldsLabel.right = new FormAttachment(100, 0);
    wlFields.setLayoutData(fdlFieldsLabel);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.Name.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.Description.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.SourceDataType.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.HopType.Label"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaFactory.getValueMetaNames()),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.Length.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.Precision.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceField.PrimaryKey.Label"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"}),
        };

    int nrRows = getMetadata().getFields() != null ? getMetadata().getFields().size() : 1;
    wFields =
        new TableView(
            manager.getVariables(),
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            e -> setChanged(),
            PropsUi.getInstance());
    PropsUi.setLook(wFields);
    FormData fdFields = new FormData();
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.left = new FormAttachment(0, 0);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(100, -margin);
    wFields.setLayoutData(fdFields);

    // Set the content on the widgets...
    //
    setWidgetsContent();

    // Some widget set changed
    resetChanged();

    // Add changed listeners
    wName.addListener(SWT.Modify, e -> setChanged());
    wDescription.addListener(SWT.Modify, e -> setChanged());
    wSchemaName.addListener(SWT.Modify, e -> setChanged());
    wTableName.addListener(SWT.Modify, e -> setChanged());
    wFields.addListener(SWT.Modify, e -> setChanged());
    if (wDatabaseName != null && wDatabaseName.getComboWidget() != null) {
      wDatabaseName.getComboWidget().addListener(SWT.Modify, e -> setChanged());
      wDatabaseName.getComboWidget().addListener(SWT.Selection, e -> setChanged());
    }
  }

  @Override
  public void setWidgetsContent() {
    DvDatabaseSource meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));

    try {
      wDatabaseName.fillItems();
      wDatabaseName.setText(Const.NVL(meta.getDatabaseName(), ""));
    } catch (HopException e) {
      wDatabaseName.setText(Const.NVL(meta.getDatabaseName(), ""));
    }

    wSchemaName.setText(Const.NVL(meta.getSchemaName(), ""));
    wTableName.setText(Const.NVL(meta.getTableName(), ""));

    // Fields table
    wFields.clearAll(false);
    List<SourceField> fields = meta.getFields();
    if (fields != null) {
      for (SourceField f : fields) {
        TableItem item = new TableItem(wFields.table, SWT.NONE);
        item.setText(1, Const.NVL(f.getName(), ""));
        item.setText(2, Const.NVL(f.getDescription(), ""));
        item.setText(3, Const.NVL(f.getSourceDataType(), ""));
        int ht = f.getHopType();
        item.setText(4, ht <= 0 ? "" : ValueMetaFactory.getValueMetaName(ht));
        item.setText(5, Const.NVL(f.getLength(), ""));
        item.setText(6, Const.NVL(f.getPrecision(), ""));
        item.setText(7, f.isPrimaryKey() ? "Y" : "N");
      }
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);
  }

  @Override
  public void getWidgetsContent(DvDatabaseSource meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setDatabaseName(wDatabaseName.getText());
    meta.setSchemaName(wSchemaName.getText());
    meta.setTableName(wTableName.getText());

    // Fields from table
    List<SourceField> fields = new ArrayList<>();
    for (TableItem item : wFields.getNonEmptyItems()) {
      SourceField f = new SourceField();
      f.setName(item.getText(1));
      f.setDescription(item.getText(2));
      f.setSourceDataType(item.getText(3));
      String htStr = item.getText(4);
      f.setHopType(Utils.isEmpty(htStr) ? 0 : ValueMetaFactory.getIdForValueMeta(htStr));
      f.setLength(item.getText(5));
      f.setPrecision(item.getText(6));
      f.setPrimaryKey("Y".equalsIgnoreCase(item.getText(7)));
      fields.add(f);
    }
    meta.setFields(fields);
  }

  @Override
  public void refreshOnDialogActivate() {
    try {
      if (wDatabaseName != null) {
        wDatabaseName.fillItems();
      }
    } catch (Exception e) {
      // ignore, best effort refresh of selection items
    }
  }

  @Override
  public Button[] createButtonsForButtonBar(Composite parent) {
    Button wImport = new Button(parent, SWT.PUSH);
    wImport.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Import.Button"));
    wImport.addListener(SWT.Selection, e -> importFromTable());

    Button wImportTables = new Button(parent, SWT.PUSH);
    wImportTables.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Button"));
    wImportTables.addListener(SWT.Selection, e -> importTables());

    return new Button[] {wImport, wImportTables};
  }

  private void importFromTable() {
    String connectionName = wDatabaseName.getText();
    if (Utils.isEmpty(connectionName)) {
      MessageBox mb = new MessageBox(getShell(), SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogMessage"));
      mb.setText(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogTitle"));
      mb.open();
      return;
    }

    DatabaseMeta databaseMeta = null;
    try {
      databaseMeta =
          manager.getMetadataProvider().getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName),
          e);
      return;
    }

    if (databaseMeta == null) {
      MessageBox mb = new MessageBox(getShell(), SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName));
      mb.setText(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"));
      mb.open();
      return;
    }

    List<DatabaseMeta> databases = new ArrayList<>();
    try {
      databases =
          manager.getMetadataProvider().getSerializer(DatabaseMeta.class).loadAll();
    } catch (Exception e) {
      // best effort, proceed with empty list
    }

    DatabaseExplorerDialog std =
        new DatabaseExplorerDialog(
            getShell(), SWT.NONE, manager.getVariables(), databaseMeta, databases);
    std.setSelectedSchemaAndTable(wSchemaName.getText(), wTableName.getText());

    if (std.open()) {
      String schemaName = Const.NVL(std.getSchemaName(), "");
      String tableName = Const.NVL(std.getTableName(), "");

      // Fill in the database connection name, schema and table name in the editor
      wDatabaseName.setText(connectionName);
      wSchemaName.setText(schemaName);
      wTableName.setText(tableName);

      // Set the name of the metadata element to: database name - table name
      String elementName = connectionName + "-" + tableName;
      getMetadata().setName(elementName);
      wName.setText(elementName);
      setChanged();

      ILoggingObject loggingObject =
          new SimpleLoggingObject("DvDatabaseSourceEditor", LoggingObjectType.GENERAL, null);
      try (Database db = new Database(loggingObject, manager.getVariables(), databaseMeta)) {
        db.connect();

        List<SourceField> fields =
            DvDatabaseSourceImportSupport.importFieldsFromTable(
                db, manager.getVariables(), schemaName, tableName);
        if (!fields.isEmpty()) {
          getMetadata().setFields(fields);
          setWidgetsContent();
          setChanged();
        }
      } catch (Exception e) {
        new ErrorDialog(
            getShell(),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingFields.DialogTitle"),
            BaseMessages.getString(
                PKG, "DvDatabaseSourceEditor.ErrorImportingFields.DialogMessage"),
            e);
      }
    }
  }

  private void importTables() {
    ImportDatabaseTablesOptionsDialog optionsDialog =
        new ImportDatabaseTablesOptionsDialog(
            getShell(), manager.getVariables(), manager.getMetadataProvider());
    ImportDatabaseTablesOptionsDialog.ImportDatabaseTablesOptions options = optionsDialog.open();
    if (options == null) {
      return;
    }

    String connectionName = Const.NVL(options.getDatabaseName(), "");
    if (Utils.isEmpty(connectionName)) {
      MessageBox mb = new MessageBox(getShell(), SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogMessage"));
      mb.setText(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogTitle"));
      mb.open();
      return;
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta =
          manager.getMetadataProvider().getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName),
          e);
      return;
    }

    if (databaseMeta == null) {
      MessageBox mb = new MessageBox(getShell(), SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName));
      mb.setText(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"));
      mb.open();
      return;
    }

    String schemaName = Const.NVL(options.getSchemaName(), "");
    String[] tableNames;
    ILoggingObject loggingObject =
        new SimpleLoggingObject("DvDatabaseSourceEditor", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, manager.getVariables(), databaseMeta)) {
      db.connect();
      tableNames = db.getTablenames(schemaName, false);
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorListingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorListingTables.DialogMessage"),
          e);
      return;
    }

    if (tableNames == null || tableNames.length == 0) {
      MessageBox mb = new MessageBox(getShell(), SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTablesFound.DialogMessage"));
      mb.setText(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTablesFound.DialogTitle"));
      mb.open();
      return;
    }

    boolean createDataVaultSource = options.isCreateDataVaultSource();
    IRowMeta selectionRowMeta = new RowMeta();
    try {
      selectionRowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Column.TableName"),
              IValueMeta.TYPE_STRING));
      selectionRowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(
                  PKG, "DvDatabaseSourceEditor.ImportTables.Column.DatabaseSourceName"),
              IValueMeta.TYPE_STRING));
      if (createDataVaultSource) {
        selectionRowMeta.addValueMeta(
            ValueMetaFactory.createValueMeta(
                BaseMessages.getString(
                    PKG, "DvDatabaseSourceEditor.ImportTables.Column.DataVaultSourceName"),
                IValueMeta.TYPE_STRING));
      }
    } catch (HopException e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    List<Object[]> selectionRows = new ArrayList<>();
    IHopMetadataSerializer<DvDatabaseSource> databaseSourceSerializer;
    IHopMetadataSerializer<DataVaultSource> dataVaultSourceSerializer = null;
    try {
      databaseSourceSerializer = manager.getMetadataProvider().getSerializer(DvDatabaseSource.class);
      if (createDataVaultSource) {
        dataVaultSourceSerializer =
            manager.getMetadataProvider().getSerializer(DataVaultSource.class);
      }
    } catch (HopException e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    for (String rawTableName : tableNames) {
      String tableName = DvDatabaseSourceImportSupport.stripTableNameQuotes(rawTableName);
      String databaseSourceName =
          DvDatabaseSourceImportSupport.buildDefaultMetadataName(
              options.getDatabaseSourcePrefix(), connectionName, tableName);
      try {
        databaseSourceName =
            DvDatabaseSourceImportSupport.uniqueMetadataName(
                databaseSourceSerializer, databaseSourceName);
        Object[] row;
        if (createDataVaultSource) {
          String dataVaultSourceName =
              DvDatabaseSourceImportSupport.buildDefaultMetadataName(
                  options.getDataVaultSourcePrefix(), connectionName, tableName);
          dataVaultSourceName =
              DvDatabaseSourceImportSupport.uniqueMetadataName(
                  dataVaultSourceSerializer, dataVaultSourceName);
          row = new Object[] {tableName, databaseSourceName, dataVaultSourceName};
        } else {
          row = new Object[] {tableName, databaseSourceName};
        }
        selectionRows.add(row);
      } catch (HopException e) {
        new ErrorDialog(
            getShell(),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
            e);
        return;
      }
    }

    String selectionMessage =
        createDataVaultSource
            ? BaseMessages.getString(
                PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Message.WithDataVaultSource")
            : BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Message");

    EditRowsDialog tableSelectionDialog =
        new EditRowsDialog(
            getShell(),
            SWT.NONE,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Title"),
            selectionMessage,
            selectionRowMeta,
            selectionRows);
    List<Object[]> selectedRows = tableSelectionDialog.open();
    if (selectedRows == null) {
      return;
    }

    int importedCount = 0;
    List<String> errors = new ArrayList<>();
    try (Database db = new Database(loggingObject, manager.getVariables(), databaseMeta)) {
      db.connect();

      for (Object[] row : selectedRows) {
        if (row == null || row.length < 2) {
          continue;
        }
        String tableName =
            DvDatabaseSourceImportSupport.stripTableNameQuotes(
                row[0] != null ? row[0].toString() : null);
        String databaseSourceName = row[1] != null ? row[1].toString() : null;
        String dataVaultSourceName =
            createDataVaultSource && row.length > 2 && row[2] != null ? row[2].toString() : null;
        if (Utils.isEmpty(tableName) || Utils.isEmpty(databaseSourceName)) {
          continue;
        }

        try {
          if (databaseSourceSerializer.exists(databaseSourceName)) {
            errors.add(
                BaseMessages.getString(
                    PKG,
                    "DvDatabaseSourceEditor.ImportTables.Exists.Message",
                    databaseSourceName,
                    tableName));
            continue;
          }

          List<SourceField> fields =
              DvDatabaseSourceImportSupport.importFieldsFromTable(
                  db, manager.getVariables(), schemaName, tableName);
          DvDatabaseSource databaseSource =
              DvDatabaseSourceImportSupport.createDatabaseSource(
                  databaseSourceName, connectionName, schemaName, tableName, fields);
          DvDatabaseSourceImportSupport.saveNewMetadata(
              manager.getMetadataProvider(),
              manager.getVariables(),
              hopGui.getLog(),
              databaseSource);
          importedCount++;

          if (createDataVaultSource
              && dataVaultSourceSerializer != null
              && !Utils.isEmpty(dataVaultSourceName)) {
            if (dataVaultSourceSerializer.exists(dataVaultSourceName)) {
              errors.add(
                  BaseMessages.getString(
                      PKG,
                      "DvDatabaseSourceEditor.ImportTables.DataVaultSourceExists.Message",
                      dataVaultSourceName,
                      tableName));
            } else {
              DataVaultSource dataVaultSource =
                  DvDatabaseSourceImportSupport.createDataVaultSource(
                      dataVaultSourceName, databaseSourceName);
              DvDatabaseSourceImportSupport.saveNewMetadata(
                  manager.getMetadataProvider(),
                  manager.getVariables(),
                  hopGui.getLog(),
                  dataVaultSource);
            }
          }
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
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    try {
      DvDatabaseSourceImportSupport.refreshMetadataPerspective(hopGui);
    } catch (HopException e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorRefreshingTree.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorRefreshingTree.DialogMessage"),
          e);
    }

    if (!errors.isEmpty()) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.PartialFailure.Title"),
          BaseMessages.getString(
              PKG,
              "DvDatabaseSourceEditor.ImportTables.PartialFailure.Message",
              importedCount,
              String.join(Const.CR, errors)),
          null);
      return;
    }

    MessageBox mb = new MessageBox(getShell(), SWT.OK | SWT.ICON_INFORMATION);
    mb.setMessage(
        BaseMessages.getString(
            PKG, "DvDatabaseSourceEditor.ImportTables.Success.Message", importedCount));
    mb.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Success.Title"));
    mb.open();
  }
}
