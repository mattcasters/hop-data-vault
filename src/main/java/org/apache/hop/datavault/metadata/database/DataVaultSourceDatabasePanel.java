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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.hop.core.Const;
import org.apache.hop.core.IRunnableWithProgress;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.DatabaseExplorerDialog;
import org.apache.hop.ui.core.database.dialog.PreviewTableSettingsDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.PreviewRowsDialog;
import org.apache.hop.ui.core.dialog.ProgressMonitorDialog;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Database connection, table and field layout UI for an embedded {@link DvDatabaseSource}. */
public class DataVaultSourceDatabasePanel {

  private static final Class<?> PKG = DataVaultSourceDatabasePanel.class;

  private final Shell shell;
  private final HopGui hopGui;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final Runnable onChanged;
  private final Supplier<String> sourceNameSupplier;
  private final Consumer<String> sourceNameConsumer;
  private final DataVaultSource dataVaultSource;

  private Text wDescription;
  private MetaSelectionLine<DatabaseMeta> wDatabaseName;
  private Text wSchemaName;
  private Text wTableName;
  private TableView wFields;

  public DataVaultSourceDatabasePanel(
      Shell shell,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultSource dataVaultSource,
      Runnable onChanged,
      Supplier<String> sourceNameSupplier,
      Consumer<String> sourceNameConsumer) {
    this.shell = shell;
    this.hopGui = hopGui;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.dataVaultSource = dataVaultSource;
    this.onChanged = onChanged;
    this.sourceNameSupplier = sourceNameSupplier;
    this.sourceNameConsumer = sourceNameConsumer;
  }

  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int margin = PropsUi.getMargin();
    int middle = props.getMiddlePct();

    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Description.Label"));
    FormData fdlDescription = new FormData();
    fdlDescription.top = new FormAttachment(0, margin * 2);
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
    Control lastControl = wDescription;

    wDatabaseName =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
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
        };

    DvDatabaseSource dbSource = getDatabaseSource();
    int nrRows = dbSource.getFields() != null ? dbSource.getFields().size() : 1;
    wFields =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            e -> notifyChanged(),
            PropsUi.getInstance());
    PropsUi.setLook(wFields);
    FormData fdFields = new FormData();
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.left = new FormAttachment(0, 0);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(100, -margin);
    wFields.setLayoutData(fdFields);

    wDescription.addListener(SWT.Modify, e -> notifyChanged());
    wSchemaName.addListener(SWT.Modify, e -> notifyChanged());
    wTableName.addListener(SWT.Modify, e -> notifyChanged());
    wFields.addListener(SWT.Modify, e -> notifyChanged());
    if (wDatabaseName.getComboWidget() != null) {
      wDatabaseName.getComboWidget().addListener(SWT.Modify, e -> notifyChanged());
      wDatabaseName.getComboWidget().addListener(SWT.Selection, e -> notifyChanged());
    }
  }

  public Button[] createImportButtons(Composite parent) {
    Button wPreview = new Button(parent, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Preview.Button"));
    wPreview.addListener(SWT.Selection, e -> previewSource());

    Button wImport = new Button(parent, SWT.PUSH);
    wImport.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Import.Button"));
    wImport.addListener(SWT.Selection, e -> importFromTable());

    Button wImportTables = new Button(parent, SWT.PUSH);
    wImportTables.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Button"));
    wImportTables.addListener(SWT.Selection, e -> importTables());

    return new Button[] {wPreview, wImport, wImportTables};
  }

  public void refreshOnDialogActivate() {
    try {
      if (wDatabaseName != null) {
        wDatabaseName.fillItems();
      }
    } catch (Exception e) {
      // best effort
    }
  }

  public void setWidgetsContent() {
    DvDatabaseSource meta = getDatabaseSource();
    wDescription.setText(Const.NVL(meta.getDescription(), ""));

    try {
      wDatabaseName.fillItems();
      wDatabaseName.setText(Const.NVL(meta.getDatabaseName(), ""));
    } catch (HopException e) {
      wDatabaseName.setText(Const.NVL(meta.getDatabaseName(), ""));
    }

    wSchemaName.setText(Const.NVL(meta.getSchemaName(), ""));
    wTableName.setText(Const.NVL(meta.getTableName(), ""));

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
      }
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);
  }

  public void getWidgetsContent() {
    DvDatabaseSource meta = getDatabaseSource();
    meta.setDescription(wDescription.getText());
    meta.setDatabaseName(wDatabaseName.getText());
    meta.setSchemaName(wSchemaName.getText());
    meta.setTableName(wTableName.getText());

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
      fields.add(f);
    }
    meta.setFields(fields);
    dataVaultSource.setSource(meta);
  }

  private DvDatabaseSource getDatabaseSource() {
    if (dataVaultSource.getDvSourceOrDefault() instanceof DvDatabaseSource dbSource) {
      return dbSource;
    }
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dataVaultSource.setSource(dbSource);
    return dbSource;
  }

  private void notifyChanged() {
    if (onChanged != null) {
      onChanged.run();
    }
  }

  private void previewSource() {
    getWidgetsContent();

    DvDatabaseSource dbSource = getDatabaseSource();
    if (Utils.isEmpty(dbSource.getDatabaseName())) {
      showError(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogMessage"));
      return;
    }
    if (Utils.isEmpty(dbSource.getTableName())) {
      showError(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTable.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTable.DialogMessage"));
      return;
    }

    PreviewTableSettingsDialog settingsDialog =
        new PreviewTableSettingsDialog(shell, 100, variables, false);
    PreviewTableSettingsDialog.Settings settings = settingsDialog.open();
    if (settings == null) {
      return;
    }

    AtomicReference<List<RowMetaAndData>> previewRows = new AtomicReference<>();
    IRunnableWithProgress operation =
        monitor -> {
          monitor.beginTask(
              BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Preview.Progress.Task"), -1);
          try {
            previewRows.set(
                dataVaultSource
                    .getDvSourceOrDefault()
                    .previewRecords(
                        variables,
                        metadataProvider,
                        settings.rowLimit,
                        settings.queryTimeoutSeconds));
            monitor.done();
          } catch (HopException e) {
            throw new InvocationTargetException(e);
          }
        };

    try {
      ProgressMonitorDialog pmd = new ProgressMonitorDialog(shell);
      pmd.run(true, operation);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getTargetException() != null ? e.getTargetException() : e;
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Preview.Error.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Preview.Error.DialogMessage"),
          cause);
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    List<RowMetaAndData> rows = previewRows.get();
    if (rows == null || rows.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Preview.NoRows.DialogTitle"));
      mb.setMessage(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.Preview.NoRows.DialogMessage"));
      mb.open();
      return;
    }

    IRowMeta rowMeta = rows.get(0).getRowMeta();
    List<Object[]> buffer = new ArrayList<>();
    for (RowMetaAndData row : rows) {
      buffer.add(row.getData());
    }

    String previewTitle = buildPreviewTitle(dbSource);
    PreviewRowsDialog previewDialog =
        new PreviewRowsDialog(shell, variables, SWT.NONE, previewTitle, rowMeta, buffer);
    previewDialog.open();
  }

  private String buildPreviewTitle(DvDatabaseSource dbSource) {
    StringBuilder title = new StringBuilder(Const.NVL(dbSource.getDatabaseName(), ""));
    if (!Utils.isEmpty(dbSource.getSchemaName())) {
      title.append('.').append(dbSource.getSchemaName());
    }
    if (!Utils.isEmpty(dbSource.getTableName())) {
      title.append('.').append(dbSource.getTableName());
    }
    return title.toString();
  }

  private void importFromTable() {
    String connectionName = wDatabaseName.getText();
    if (Utils.isEmpty(connectionName)) {
      showError(
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
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName));
      return;
    }

    List<DatabaseMeta> databases = new ArrayList<>();
    try {
      databases = metadataProvider.getSerializer(DatabaseMeta.class).loadAll();
    } catch (Exception e) {
      // best effort
    }

    DatabaseExplorerDialog std =
        new DatabaseExplorerDialog(shell, SWT.NONE, variables, databaseMeta, databases);
    std.setSelectedSchemaAndTable(wSchemaName.getText(), wTableName.getText());

    if (std.open()) {
      String schemaName = Const.NVL(std.getSchemaName(), "");
      String tableName =
          DvDatabaseSourceImportSupport.stripTableNameQuotes(Const.NVL(std.getTableName(), ""));

      ILoggingObject loggingObject =
          new SimpleLoggingObject("DataVaultSourceEditor", LoggingObjectType.GENERAL, null);
      try (Database db = new Database(loggingObject, variables, databaseMeta)) {
        db.connect();
        List<SourceField> fields =
            DvDatabaseSourceImportSupport.importFieldsFromTable(
                db, variables, schemaName, tableName);

        DvDatabaseSource dbSource = getDatabaseSource();
        dbSource.setDatabaseName(connectionName);
        dbSource.setSchemaName(schemaName);
        dbSource.setTableName(tableName);
        dbSource.setFields(fields);
        dataVaultSource.setSource(dbSource);

        if (Utils.isEmpty(sourceNameSupplier.get())) {
          sourceNameConsumer.accept(
              DvDatabaseSourceImportSupport.buildSuggestedSourceName(
                  connectionName, schemaName, tableName));
        }

        setWidgetsContent();
        notifyChanged();
      } catch (Exception e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingFields.DialogTitle"),
            BaseMessages.getString(
                PKG, "DvDatabaseSourceEditor.ErrorImportingFields.DialogMessage"),
            e);
      }
    }
  }

  private void importTables() {
    DvDatabaseSourceImportSupport.importDatabaseTables(shell, hopGui, variables, metadataProvider);
  }

  private void showError(String title, String message) {
    MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
    mb.setMessage(message);
    mb.setText(title);
    mb.open();
  }
}