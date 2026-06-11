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
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to edit the properties of a DvLink using a TabFolder.
 * Name and description are placed at the top, buttons at the bottom.
 * Tabs: Options (hash key field, record source field, hasDescriptiveAttributes, participating hubs),
 * Driving keys (single column TableView for drivingKeyNames),
 * Sources (interface to edit DvLink.linkSources using DvLinkSourceDialog + HubSourceKeyFieldDialog).
 */
public class DvLinkDialog {
  private static final Class<?> PKG = DvLinkDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink input;
  private Shell shell;

  private CTabFolder wTabFolder;

  // Widgets (top level name/desc + per tab)
  private Text wName;
  private Text wDescription;

  // Options tab
  private Text wTableName;
  private Text wLinkHashKeyFieldName;
  private Text wRecordSourceFieldName;
  private Button wHasDescriptiveAttributes;
  private TableView wHubNames;

  // Driving keys tab
  private TableView wDrivingKeyNames;

  // Sources tab (for linkSources)
  private TableView wLinkSources;
  private List<DvLink.DvLinkSource> currentLinkSources = new ArrayList<>();

  private boolean ok;

  public DvLinkDialog(Shell parent, HopGui hopGui, DvLink link) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = link;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvLinkDialog.Title", input.getName()));

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
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    // Name at top (outside tabs)
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvLinkDialog.Name.Label"));
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
    wName.addModifyListener(e -> input.setChanged());

    // Description right under name
    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvLinkDialog.Description.Label"));
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
    fdDescription.top = new FormAttachment(wName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);
    wDescription.addModifyListener(e -> input.setChanged());

    // TabFolder between description and buttons
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
    addDrivingKeysTab();
    addSourcesTab();

    wTabFolder.setSelection(0);

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private int margin;
  private int middle;

  private void addOptionsTab() {
    CTabItem wOptionsTab = new CTabItem(wTabFolder, SWT.NONE);
    wOptionsTab.setFont(GuiResource.getInstance().getFontDefault());
    wOptionsTab.setText("Options");
    wOptionsTab.setToolTipText("General options for this link (hash key, record source field, descriptive attributes, participating hubs)");
    Composite wOptionsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wOptionsComp);
    wOptionsComp.setLayout(new FormLayout());

    // Table name (physical) inside options like hub dialog
    Label wlTableName = new Label(wOptionsComp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvLinkDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(0, 0);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(0, 0);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);
    wTableName.addModifyListener(e -> input.setChanged());

    // Link hash key field name
    Label wlLinkHashKey = new Label(wOptionsComp, SWT.RIGHT);
    wlLinkHashKey.setText(BaseMessages.getString(PKG, "DvLinkDialog.LinkHashKeyFieldName.Label"));
    PropsUi.setLook(wlLinkHashKey);
    FormData fdlLinkHashKey = new FormData();
    fdlLinkHashKey.left = new FormAttachment(0, 0);
    fdlLinkHashKey.top = new FormAttachment(wTableName, margin);
    fdlLinkHashKey.right = new FormAttachment(middle, -margin);
    wlLinkHashKey.setLayoutData(fdlLinkHashKey);

    wLinkHashKeyFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLinkHashKeyFieldName);
    FormData fdLinkHashKey = new FormData();
    fdLinkHashKey.left = new FormAttachment(middle, 0);
    fdLinkHashKey.top = new FormAttachment(wTableName, margin);
    fdLinkHashKey.right = new FormAttachment(100, 0);
    wLinkHashKeyFieldName.setLayoutData(fdLinkHashKey);
    wLinkHashKeyFieldName.addModifyListener(e -> input.setChanged());

    // Record source field name (the per-link override field name)
    Label wlRecordSourceField = new Label(wOptionsComp, SWT.RIGHT);
    wlRecordSourceField.setText("Record source field name");
    PropsUi.setLook(wlRecordSourceField);
    FormData fdlRecordSourceField = new FormData();
    fdlRecordSourceField.left = new FormAttachment(0, 0);
    fdlRecordSourceField.top = new FormAttachment(wLinkHashKeyFieldName, margin);
    fdlRecordSourceField.right = new FormAttachment(middle, -margin);
    wlRecordSourceField.setLayoutData(fdlRecordSourceField);

    wRecordSourceFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRecordSourceFieldName);
    FormData fdRecordSourceField = new FormData();
    fdRecordSourceField.left = new FormAttachment(middle, 0);
    fdRecordSourceField.top = new FormAttachment(wLinkHashKeyFieldName, margin);
    fdRecordSourceField.right = new FormAttachment(100, 0);
    wRecordSourceFieldName.setLayoutData(fdRecordSourceField);
    wRecordSourceFieldName.addModifyListener(e -> input.setChanged());

    // Has descriptive attributes checkbox
    Label wlHasDescriptive = new Label(wOptionsComp, SWT.RIGHT);
    wlHasDescriptive.setText(
        BaseMessages.getString(PKG, "DvLinkDialog.HasDescriptiveAttributes.Label"));
    PropsUi.setLook(wlHasDescriptive);
    FormData fdlHasDescriptive = new FormData();
    fdlHasDescriptive.left = new FormAttachment(0, 0);
    fdlHasDescriptive.top = new FormAttachment(wRecordSourceFieldName, margin);
    fdlHasDescriptive.right = new FormAttachment(middle, -margin);
    wlHasDescriptive.setLayoutData(fdlHasDescriptive);

    wHasDescriptiveAttributes = new Button(wOptionsComp, SWT.CHECK);
    PropsUi.setLook(wHasDescriptiveAttributes);
    FormData fdHasDescriptive = new FormData();
    fdHasDescriptive.left = new FormAttachment(middle, 0);
    fdHasDescriptive.top = new FormAttachment(wRecordSourceFieldName, margin);
    wHasDescriptiveAttributes.setLayoutData(fdHasDescriptive);
    wHasDescriptiveAttributes.addListener(SWT.Selection, e -> input.setChanged());

    // Participating hubs (single column table) inside options
    Label wlHubNames = new Label(wOptionsComp, SWT.LEFT);
    wlHubNames.setText(BaseMessages.getString(PKG, "DvLinkDialog.HubNames.Label"));
    PropsUi.setLook(wlHubNames);
    FormData fdlHubNames = new FormData();
    fdlHubNames.left = new FormAttachment(0, 0);
    fdlHubNames.top = new FormAttachment(wHasDescriptiveAttributes, margin);
    wlHubNames.setLayoutData(fdlHubNames);

    ColumnInfo[] hubColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.HubName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrHubRows = (input.getHubNames() != null && !input.getHubNames().isEmpty()) ? input.getHubNames().size() : 2;
    wHubNames =
        new TableView(
            variables,
            wOptionsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            hubColumns,
            nrHubRows,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdHubNames = new FormData();
    fdHubNames.left = new FormAttachment(0, 0);
    fdHubNames.top = new FormAttachment(wlHubNames, margin);
    fdHubNames.right = new FormAttachment(100, 0);
    fdHubNames.bottom = new FormAttachment(100, 0);
    wHubNames.setLayoutData(fdHubNames);

    wOptionsComp.layout();
    wOptionsTab.setControl(wOptionsComp);
  }

  private void addDrivingKeysTab() {
    CTabItem wKeysTab = new CTabItem(wTabFolder, SWT.NONE);
    wKeysTab.setFont(GuiResource.getInstance().getFontDefault());
    wKeysTab.setText("Driving keys");
    wKeysTab.setToolTipText("Driving keys (when the same hub participates multiple times under different roles)");
    Composite wKeysComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wKeysComp);
    wKeysComp.setLayout(new FormLayout());

    Label wlDrivingKeys = new Label(wKeysComp, SWT.LEFT);
    wlDrivingKeys.setText(BaseMessages.getString(PKG, "DvLinkDialog.DrivingKeyNames.Label"));
    PropsUi.setLook(wlDrivingKeys);
    FormData fdlDrivingKeys = new FormData();
    fdlDrivingKeys.left = new FormAttachment(0, 0);
    fdlDrivingKeys.top = new FormAttachment(0, 0);
    wlDrivingKeys.setLayoutData(fdlDrivingKeys);

    ColumnInfo[] drivingColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DrivingKeyName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrDrivingRows =
        (input.getDrivingKeyNames() != null && !input.getDrivingKeyNames().isEmpty())
            ? input.getDrivingKeyNames().size()
            : 2;
    wDrivingKeyNames =
        new TableView(
            variables,
            wKeysComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            drivingColumns,
            nrDrivingRows,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdDrivingKeys = new FormData();
    fdDrivingKeys.left = new FormAttachment(0, 0);
    fdDrivingKeys.top = new FormAttachment(wlDrivingKeys, margin);
    fdDrivingKeys.right = new FormAttachment(100, 0);
    fdDrivingKeys.bottom = new FormAttachment(100, 0);
    wDrivingKeyNames.setLayoutData(fdDrivingKeys);

    wKeysComp.layout();
    wKeysTab.setControl(wKeysComp);
  }

  private void addSourcesTab() {
    CTabItem wSourcesTab = new CTabItem(wTabFolder, SWT.NONE);
    wSourcesTab.setFont(GuiResource.getInstance().getFontDefault());
    wSourcesTab.setText("Sources");
    wSourcesTab.setToolTipText("Record sources for this link and their per-hub business key / driving key field mappings");
    Composite wSourcesComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wSourcesComp);
    wSourcesComp.setLayout(new FormLayout());

    Label wlSources = new Label(wSourcesComp, SWT.LEFT);
    wlSources.setText("Link sources (one entry per record source feeding this link)");
    PropsUi.setLook(wlSources);
    FormData fdlSources = new FormData();
    fdlSources.left = new FormAttachment(0, 0);
    fdlSources.top = new FormAttachment(0, 0);
    wlSources.setLayoutData(fdlSources);

    // Edit button to launch the detailed DvLinkSourceDialog for selected source
    Button wEditMappings = new Button(wSourcesComp, SWT.PUSH);
    wEditMappings.setText("Edit source mappings...");
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(0, 0);
    fdEdit.top = new FormAttachment(wlSources, margin);
    wEditMappings.setLayoutData(fdEdit);
    wEditMappings.addListener(SWT.Selection, e -> editSelectedLinkSource());

    // Simple table of source names (CCOMBO) - details managed via the sub dialog
    List<String> sourceNames = new ArrayList<>();
    try {
      sourceNames =
          hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).listObjectNames();
    } catch (Exception e) {
      sourceNames = new ArrayList<>();
    }
    Collections.sort(sourceNames);

    ColumnInfo[] srcCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Data Vault Source",
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              sourceNames.toArray(new String[0])),
        };

    wLinkSources =
        new TableView(
            variables,
            wSourcesComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            srcCols,
            2,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdSources = new FormData();
    fdSources.left = new FormAttachment(0, 0);
    fdSources.top = new FormAttachment(wEditMappings, margin);
    fdSources.right = new FormAttachment(100, 0);
    fdSources.bottom = new FormAttachment(100, 0);
    wLinkSources.setLayoutData(fdSources);

    wSourcesComp.layout();
    wSourcesTab.setControl(wSourcesComp);
  }

  private List<String> getHubNamesFromTable() {
    List<String> hubs = new ArrayList<>();
    if (wHubNames == null) {
      return hubs;
    }
    for (TableItem item : wHubNames.getNonEmptyItems()) {
      String h = item.getText(1);
      if (!Utils.isEmpty(h)) {
        hubs.add(h);
      }
    }
    return hubs;
  }

  private void editSelectedLinkSource() {
    List<TableItem> items = wLinkSources.getNonEmptyItems();
    if (items.isEmpty()) {
      return;
    }
    TableItem sel = null;
    if (wLinkSources.table.getSelectionCount() > 0) {
      sel = wLinkSources.table.getSelection()[0];
    }
    if (sel == null) {
      sel = items.get(0);
    }
    String sourceName = sel.getText(1);
    if (Utils.isEmpty(sourceName)) {
      return;
    }

    DvLink.DvLinkSource detail = null;
    for (DvLink.DvLinkSource ls : currentLinkSources) {
      if (ls.getSource() != null && sourceName.equals(ls.getSource().getName())) {
        detail = ls;
        break;
      }
    }
    if (detail == null) {
      detail = new DvLink.DvLinkSource();
      try {
        DataVaultSource src =
            hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(sourceName);
        detail.setSource(src);
      } catch (HopException ex) {
        DataVaultSource ph = new DataVaultSource();
        ph.setName(sourceName);
        detail.setSource(ph);
      }
      currentLinkSources.add(detail);
    }

    List<String> hubs = getHubNamesFromTable();
    DvLinkSourceDialog dlg = new DvLinkSourceDialog(shell, hopGui, detail, hubs);
    if (dlg.open()) {
      input.setChanged();
    }
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wLinkHashKeyFieldName.setText(Const.NVL(input.getLinkHashKeyFieldName(), ""));
    wRecordSourceFieldName.setText(Const.NVL(input.getRecordSourceFieldName(), ""));
    wHasDescriptiveAttributes.setSelection(input.isHasDescriptiveAttributes());

    // Hubs (single column now)
    wHubNames.clearAll();
    if (input.getHubNames() != null) {
      for (String hubName : input.getHubNames()) {
        TableItem item = new TableItem(wHubNames.table, SWT.NONE);
        item.setText(1, Const.NVL(hubName, ""));
      }
    }
    wHubNames.removeEmptyRows();
    wHubNames.setRowNums();
    wHubNames.optWidth(true);

    // Driving keys
    wDrivingKeyNames.clearAll();
    if (input.getDrivingKeyNames() != null) {
      for (String dk : input.getDrivingKeyNames()) {
        TableItem item = new TableItem(wDrivingKeyNames.table, SWT.NONE);
        item.setText(1, Const.NVL(dk, ""));
      }
    }
    wDrivingKeyNames.removeEmptyRows();
    wDrivingKeyNames.setRowNums();
    wDrivingKeyNames.optWidth(true);

    // Link sources (names in table; full objects kept in currentLinkSources for sub dialog editing)
    currentLinkSources.clear();
    wLinkSources.clearAll();
    if (input.getLinkSources() != null) {
      for (DvLink.DvLinkSource ls : input.getLinkSources()) {
        if (ls != null) {
          currentLinkSources.add(ls);
          if (ls.getSource() != null && !Utils.isEmpty(ls.getSource().getName())) {
            TableItem item = new TableItem(wLinkSources.table, SWT.NONE);
            item.setText(1, ls.getSource().getName());
          }
        }
      }
    }
    wLinkSources.removeEmptyRows();
    wLinkSources.setRowNums();
    wLinkSources.optWidth(true);
  }

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    input.setLinkHashKeyFieldName(wLinkHashKeyFieldName.getText());
    input.setRecordSourceFieldName(wRecordSourceFieldName.getText());
    input.setHasDescriptiveAttributes(wHasDescriptiveAttributes.getSelection());

    // Hubs from options tab table (single column)
    List<String> hubs = new ArrayList<>();
    for (TableItem item : wHubNames.getNonEmptyItems()) {
      String h = item.getText(1);
      if (!Utils.isEmpty(h)) {
        hubs.add(h);
      }
    }
    input.setHubNames(hubs);

    // Driving keys
    List<String> drives = new ArrayList<>();
    for (TableItem item : wDrivingKeyNames.getNonEmptyItems()) {
      String d = item.getText(1);
      if (!Utils.isEmpty(d)) {
        drives.add(d);
      }
    }
    input.setDrivingKeyNames(drives);

    // Link sources: rebuild from the sources table + the detailed objects edited via sub dialogs
    input.getLinkSources().clear();
    for (TableItem item : wLinkSources.getNonEmptyItems()) {
      String sname = item.getText(1);
      if (Utils.isEmpty(sname)) {
        continue;
      }
      DvLink.DvLinkSource match = null;
      for (DvLink.DvLinkSource cand : currentLinkSources) {
        if (cand.getSource() != null && sname.equals(cand.getSource().getName())) {
          match = cand;
          break;
        }
      }
      if (match == null) {
        match = new DvLink.DvLinkSource();
        try {
          DataVaultSource src =
              hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(sname);
          match.setSource(src);
        } catch (HopException ex) {
          DataVaultSource ph = new DataVaultSource();
          ph.setName(sname);
          match.setSource(ph);
        }
      }
      input.getLinkSources().add(match);
    }

    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (!shell.isDisposed()) {
      shell.dispose();
    }
  }
}
