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

package org.apache.hop.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit the properties of a DvHub, including business keys list using TableView. */
public class DvHubDialog {
  private static final Class<?> PKG = DvHubDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvHub input;
  private Shell shell;

  private CTabFolder wTabFolder;

  // Widgets
  private Text wName;
  private Text wTableName;
  private Text wDescription;

  private Text wHashKeyFieldName;
  private Text wRecordSourceFieldName;
  private TableView wBusinessKeys;
  private TableView wSources;

  private boolean ok;

  private int margin;
  private int middle;

  public DvHubDialog(Shell parent, HopGui hopGui, DvHub hub) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = hub;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvHubDialog.Title", input.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();
    middle = PropsUi.getInstance().getMiddlePct();

    // Buttons at the bottom (using standard positioning)
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wGetKeys = new Button(shell, SWT.PUSH);
    wGetKeys.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.Button"));
    wGetKeys.addListener(SWT.Selection, e -> getKeys());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wGetKeys, wCancel}, margin, null);

    // Name
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvHubDialog.Name.Label"));
    PropsUi.setLook(wlName);
    FormData fdlName = new FormData();
    fdlName.left = new FormAttachment(0, 0);
    fdlName.top = new FormAttachment(0, margin);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(middle, 0);
    fdName.top = new FormAttachment(0, margin);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    // Description
    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvHubDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wlDescription, 0, SWT.CENTER);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wDescription, margin))
            .right()
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());

    addOptionsTab();
    addSourcesTab();
    addKeysTab();

    wTabFolder.setSelection(0);

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void addOptionsTab() {
    CTabItem wOptionsTab = new CTabItem(wTabFolder, SWT.NONE);
    wOptionsTab.setFont(GuiResource.getInstance().getFontDefault());
    wOptionsTab.setText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Options.Label"));
    wOptionsTab.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Options.ToolTip"));
    Composite wOptionsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wOptionsComp);
    wOptionsComp.setLayout(new FormLayout());

    // Table name (physical)
    Label wlTableName = new Label(wOptionsComp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvHubDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(wDescription, margin);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wlTableName, 0, SWT.CENTER);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);

    // Hash key field name (per-hub, replaces global suffix from DataVaultConfiguration)
    Label wlHashKeyFieldName = new Label(wOptionsComp, SWT.RIGHT);
    wlHashKeyFieldName.setText(
        BaseMessages.getString(PKG, "DvHubDialog.HashKeyFieldName.Label"));
    PropsUi.setLook(wlHashKeyFieldName);
    FormData fdlHashKeyFieldName = new FormData();
    fdlHashKeyFieldName.left = new FormAttachment(0, 0);
    fdlHashKeyFieldName.top = new FormAttachment(wTableName, margin);
    fdlHashKeyFieldName.right = new FormAttachment(middle, -margin);
    wlHashKeyFieldName.setLayoutData(fdlHashKeyFieldName);

    wHashKeyFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHashKeyFieldName);
    FormData fdHashKeyFieldName = new FormData();
    fdHashKeyFieldName.left = new FormAttachment(middle, 0);
    fdHashKeyFieldName.top = new FormAttachment(wlHashKeyFieldName, 0, SWT.CENTER);
    fdHashKeyFieldName.right = new FormAttachment(100, 0);
    wHashKeyFieldName.setLayoutData(fdHashKeyFieldName);

    // Record source field name (per-hub override of the one in DataVaultConfiguration)
    Label wlRecordSourceFieldName = new Label(wOptionsComp, SWT.RIGHT);
    wlRecordSourceFieldName.setText(
        BaseMessages.getString(PKG, "DvHubDialog.RecordSourceFieldName.Label"));
    PropsUi.setLook(wlRecordSourceFieldName);
    FormData fdlRecordSourceFieldName = new FormData();
    fdlRecordSourceFieldName.left = new FormAttachment(0, 0);
    fdlRecordSourceFieldName.top = new FormAttachment(wHashKeyFieldName, margin);
    fdlRecordSourceFieldName.right = new FormAttachment(middle, -margin);
    wlRecordSourceFieldName.setLayoutData(fdlRecordSourceFieldName);

    wRecordSourceFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRecordSourceFieldName);
    FormData fdRecordSourceFieldName = new FormData();
    fdRecordSourceFieldName.left = new FormAttachment(middle, 0);
    fdRecordSourceFieldName.top = new FormAttachment(wlRecordSourceFieldName, 0, SWT.CENTER);
    fdRecordSourceFieldName.right = new FormAttachment(100, 0);
    wRecordSourceFieldName.setLayoutData(fdRecordSourceFieldName);

    wOptionsComp.layout();
    wOptionsTab.setControl(wOptionsComp);
  }

  private void addKeysTab() {
    CTabItem wKeysTab = new CTabItem(wTabFolder, SWT.NONE);
    wKeysTab.setFont(GuiResource.getInstance().getFontDefault());
    wKeysTab.setText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Keys.Label"));
    wKeysTab.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Keys.ToolTip"));
    Composite wKeysComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wKeysComp);
    wKeysComp.setLayout(new FormLayout());

    // Business keys - use TableView
    Label wlBusinessKeys = new Label(wKeysComp, SWT.LEFT);
    wlBusinessKeys.setText(BaseMessages.getString(PKG, "DvHubDialog.BusinessKeys.Label"));
    PropsUi.setLook(wlBusinessKeys);
    FormData fdlBusinessKeys = new FormData();
    fdlBusinessKeys.left = new FormAttachment(0, 0);
    fdlBusinessKeys.top = new FormAttachment(0, 0);
    wlBusinessKeys.setLayoutData(fdlBusinessKeys);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.SourceFieldName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.SourceSystem.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrRows = input.getBusinessKeys() != null ? input.getBusinessKeys().size() : 1;
    wBusinessKeys =
        new TableView(
            variables,
            wKeysComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            null,
            PropsUi.getInstance());

    FormData fdBusinessKeys = new FormData();
    fdBusinessKeys.left = new FormAttachment(0, 0);
    fdBusinessKeys.top = new FormAttachment(wlBusinessKeys, margin);
    fdBusinessKeys.right = new FormAttachment(100, 0);
    fdBusinessKeys.bottom = new FormAttachment(100, 0);
    wBusinessKeys.setLayoutData(fdBusinessKeys);

    wKeysComp.layout();
    wKeysTab.setControl(wKeysComp);
  }

  private void addSourcesTab() {
    CTabItem wSourcesTab = new CTabItem(wTabFolder, SWT.NONE);
    wSourcesTab.setFont(GuiResource.getInstance().getFontDefault());
    wSourcesTab.setText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Sources.Label"));
    wSourcesTab.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Sources.ToolTip"));
    Composite wSourcesComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wSourcesComp);
    wSourcesComp.setLayout(new FormLayout());

    // Multi-record sources section for Hubs (supports multiple sources feeding one Hub table)
    Label wlSources = new Label(wSourcesComp, SWT.LEFT);
    wlSources.setText(BaseMessages.getString(PKG, "DvHubDialog.RecordSources.Label"));
    PropsUi.setLook(wlSources);
    FormData fdlSources = new FormData();
    fdlSources.left = new FormAttachment(0, 0);
    fdlSources.top = new FormAttachment(0, 0);
    wlSources.setLayoutData(fdlSources);

    List<String> sources = new ArrayList<>();
    try {
      sources = hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).listObjectNames();
    } catch (Exception e) {
      sources = new ArrayList<>();
    }
    Collections.sort(sources);
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              "Data Vault Source", ColumnInfo.COLUMN_TYPE_CCOMBO, sources.toArray(new String[0])),
        };
    wSources =
        new TableView(
            hopGui.getVariables(),
            wSourcesComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            null,
            PropsUi.getInstance());
    FormData fdSources = new FormData();
    fdSources.left = new FormAttachment(0, 0);
    fdSources.top = new FormAttachment(wlSources, margin);
    fdSources.right = new FormAttachment(100, 0);
    fdSources.bottom = new FormAttachment(100, 0);
    wSources.setLayoutData(fdSources);

    wSourcesComp.layout();
    wSourcesTab.setControl(wSourcesComp);
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wHashKeyFieldName.setText(Const.NVL(input.getHashKeyFieldName(), ""));
    wRecordSourceFieldName.setText(Const.NVL(input.getRecordSourceFieldName(), ""));

    // The business keys
    //
    for (int i = 0; i < input.getBusinessKeys().size(); i++) {
      BusinessKey bk = input.getBusinessKeys().get(i);
      TableItem item = wBusinessKeys.table.getItem(i);
      item.setText(1, Const.NVL(bk.getName(), ""));
      item.setText(2, Const.NVL(bk.getDescription(), ""));
      item.setText(3, Const.NVL(bk.getDataType(), ""));
      item.setText(4, Const.NVL(bk.getLength(), ""));
      item.setText(5, Const.NVL(bk.getSourceFieldName(), ""));
      item.setText(6, Const.NVL(bk.getRecordSourceName(), ""));
    }

    // The record sources
    //
    for (String recordSource : input.getRecordSources()) {
      TableItem item = new TableItem(wSources.table, SWT.NONE);
      item.setText(1, Const.NVL(recordSource, ""));
    }
    wSources.optimizeTableView();
  }

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    input.setHashKeyFieldName(wHashKeyFieldName.getText());
    input.setRecordSourceFieldName(wRecordSourceFieldName.getText());

    // Business keys from table
    List<BusinessKey> keys = new ArrayList<>();
    for (TableItem item : wBusinessKeys.getNonEmptyItems()) {
      BusinessKey bk = new BusinessKey();
      bk.setName(item.getText(1));
      bk.setDescription(item.getText(2));
      bk.setDataType(item.getText(3));
      bk.setLength(item.getText(4));
      bk.setSourceFieldName(item.getText(5));
      bk.setRecordSourceName(item.getText(6));
      keys.add(bk);
    }
    input.setBusinessKeys(keys);

    // The data vault sources
    //
    input.getRecordSources().clear();
    List<TableItem> sourcesItems = wSources.getNonEmptyItems();
    for (TableItem item : sourcesItems) {
      String sourceName = item.getText(1);
      input.getRecordSources().add(sourceName);
    }

    input.setChanged();
    ok = true;
    dispose();
  }

  private void cancel() {
    // restore backup? simple, no for now
    ok = false;
    dispose();
  }

  private void getKeys() {
    // Select the first source and pick source field(s) to add as business keys.
    //
    List<TableItem> sourceItems = wSources.getNonEmptyItems();
    if (sourceItems.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoSource.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoSource.Title"));
      mb.open();
      return;
    }

    String sourceName = sourceItems.getFirst().getText(1);

    DataVaultSource source = null;
    List<SourceField> sourceFields = null;

    try {
      source = hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(sourceName);
      if (source != null) {
        sourceFields = source.getFields(hopGui.getMetadataProvider());
      }
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.Error.Title"),
          BaseMessages.getString(
              PKG, "DvHubDialog.GetKeys.ErrorLoadingSource.Message", sourceName),
          e);
      return;
    }

    if (source == null || sourceFields == null || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoFields.Message", sourceName));
      mb.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoFields.Title"));
      mb.open();
      return;
    }

    Set<String> preselectedSourceFields = new HashSet<>();
    for (TableItem item : wBusinessKeys.getNonEmptyItems()) {
      String sourceFieldName = item.getText(5);
      if (!Utils.isEmpty(sourceFieldName)) {
        preselectedSourceFields.add(sourceFieldName);
      } else {
        String businessKeyName = item.getText(1);
        if (!Utils.isEmpty(businessKeyName)) {
          preselectedSourceFields.add(businessKeyName);
        }
      }
    }

    String[] choices = new String[sourceFields.size()];
    List<Integer> selectedIndexes = new ArrayList<>();
    for (int i = 0; i < sourceFields.size(); i++) {
      SourceField sf = sourceFields.get(i);
      choices[i] = sf.getName();
      if (preselectedSourceFields.contains(sf.getName())) {
        selectedIndexes.add(i);
      }
    }

    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            choices,
            BaseMessages.getString(PKG, "DvHubDialog.GetKeys.Title"),
            BaseMessages.getString(PKG, "DvHubDialog.GetKeys.Message"));
    dialog.setMulti(true);
    dialog.setSelectedNrs(selectedIndexes);
    String result = dialog.open();
    if (result != null) {
      int[] indices = dialog.getSelectionIndeces();
      for (int idx : indices) {
        SourceField sf = sourceFields.get(idx);
        TableItem item = new TableItem(wBusinessKeys.table, SWT.NONE);
        item.setText(1, Const.NVL(sf.getName(), ""));
        item.setText(2, Const.NVL(sf.getDescription(), ""));
        item.setText(3, Const.NVL(sf.getSourceDataType(), ""));
        item.setText(4, Const.NVL(sf.getLength(), ""));
        item.setText(5, Const.NVL(sf.getName(), "")); // sourceFieldName
        item.setText(6, Const.NVL(sourceName, "")); // sourceSystem
      }
      wBusinessKeys.removeEmptyRows();
      wBusinessKeys.setRowNums();
      wBusinessKeys.optWidth(true);
    }
  }

  private void dispose() {
    if (!shell.isDisposed()) {
      shell.dispose();
    }
  }
}
