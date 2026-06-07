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
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to edit the properties of a DvLink, including hub names and driving keys lists using
 * TableView.
 */
public class HopGuiLinkDialog {
  private static final Class<?> PKG = HopGuiLinkDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink input;
  private Shell shell;

  // Widgets
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private MetaSelectionLine<DataVaultSource> wRecordSource;
  private Text wLinkHashKeyFieldName;
  private TableView wHubNames;
  private TableView wDrivingKeyNames;
  private Button wHasDescriptiveAttributes;

  private boolean ok;

  public HopGuiLinkDialog(Shell parent, HopGui hopGui, DvLink link) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = link;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.Title", input.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    // Buttons at the bottom (using standard positioning)
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    // Name
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.Name.Label"));
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

    // Table name
    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(wName, margin);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wName, margin);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);
    wTableName.addModifyListener(e -> input.setChanged());

    // Description
    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wTableName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wTableName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);
    wDescription.addModifyListener(e -> input.setChanged());

    // Record source (reference to DataVaultSource metadata)
    wRecordSource =
        new MetaSelectionLine<>(
            variables,
            hopGui.getMetadataProvider(),
            DataVaultSource.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiLinkDialog.RecordSource.Label"),
            BaseMessages.getString(PKG, "HopGuiLinkDialog.RecordSource.ToolTip"));
    FormData fdRecordSource = new FormData();
    fdRecordSource.left = new FormAttachment(0, 0);
    fdRecordSource.top = new FormAttachment(wDescription, margin);
    fdRecordSource.right = new FormAttachment(100, 0);
    wRecordSource.setLayoutData(fdRecordSource);
    wRecordSource.addModifyListener(e -> input.setChanged());

    // Link hash key field name (optional override)
    Label wlLinkHashKey = new Label(shell, SWT.RIGHT);
    wlLinkHashKey.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.LinkHashKeyFieldName.Label"));
    PropsUi.setLook(wlLinkHashKey);
    FormData fdlLinkHashKey = new FormData();
    fdlLinkHashKey.left = new FormAttachment(0, 0);
    fdlLinkHashKey.top = new FormAttachment(wRecordSource, margin);
    fdlLinkHashKey.right = new FormAttachment(middle, -margin);
    wlLinkHashKey.setLayoutData(fdlLinkHashKey);

    wLinkHashKeyFieldName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLinkHashKeyFieldName);
    FormData fdLinkHashKey = new FormData();
    fdLinkHashKey.left = new FormAttachment(middle, 0);
    fdLinkHashKey.top = new FormAttachment(wRecordSource, margin);
    fdLinkHashKey.right = new FormAttachment(100, 0);
    wLinkHashKeyFieldName.setLayoutData(fdLinkHashKey);
    wLinkHashKeyFieldName.addModifyListener(e -> input.setChanged());

    // Has descriptive attributes
    Label wlHasDescriptive = new Label(shell, SWT.RIGHT);
    wlHasDescriptive.setText(
        BaseMessages.getString(PKG, "HopGuiLinkDialog.HasDescriptiveAttributes.Label"));
    PropsUi.setLook(wlHasDescriptive);
    FormData fdlHasDescriptive = new FormData();
    fdlHasDescriptive.left = new FormAttachment(0, 0);
    fdlHasDescriptive.top = new FormAttachment(wLinkHashKeyFieldName, margin);
    fdlHasDescriptive.right = new FormAttachment(middle, -margin);
    wlHasDescriptive.setLayoutData(fdlHasDescriptive);

    wHasDescriptiveAttributes = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wHasDescriptiveAttributes);
    FormData fdHasDescriptive = new FormData();
    fdHasDescriptive.left = new FormAttachment(middle, 0);
    fdHasDescriptive.top = new FormAttachment(wlHasDescriptive, 0, SWT.CENTER);
    wHasDescriptiveAttributes.setLayoutData(fdHasDescriptive);
    wHasDescriptiveAttributes.addListener(SWT.Selection, e -> input.setChanged());

    // Hub names table
    Label wlHubNames = new Label(shell, SWT.LEFT);
    wlHubNames.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.HubNames.Label"));
    PropsUi.setLook(wlHubNames);
    FormData fdlHubNames = new FormData();
    fdlHubNames.left = new FormAttachment(0, 0);
    fdlHubNames.right = new FormAttachment(50, 0);
    fdlHubNames.top = new FormAttachment(wHasDescriptiveAttributes, margin);
    wlHubNames.setLayoutData(fdlHubNames);

    ColumnInfo[] hubColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiLinkDialog.HubName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiLinkDialog.HubSourceKeyFields.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrHubRows = input.getHubNames() != null ? input.getHubNames().size() : 1;
    wHubNames =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            hubColumns,
            nrHubRows,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdHubNames = new FormData();
    fdHubNames.left = new FormAttachment(0, 0);
    fdHubNames.right = new FormAttachment(50, 0);
    fdHubNames.top = new FormAttachment(wlHubNames, margin);
    fdHubNames.bottom = new FormAttachment(wOk, -2*margin);
    wHubNames.setLayoutData(fdHubNames);

    // Driving key names table
    Label wlDrivingKeys = new Label(shell, SWT.LEFT);
    wlDrivingKeys.setText(BaseMessages.getString(PKG, "HopGuiLinkDialog.DrivingKeyNames.Label"));
    PropsUi.setLook(wlDrivingKeys);
    FormData fdlDrivingKeys = new FormData();
    fdlDrivingKeys.left = new FormAttachment(50, margin);
    fdlDrivingKeys.right = new FormAttachment(100, 0);
    fdlDrivingKeys.top = new FormAttachment(wHasDescriptiveAttributes, margin);
    wlDrivingKeys.setLayoutData(fdlDrivingKeys);

    ColumnInfo[] drivingColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiLinkDialog.DrivingKeyName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrDrivingRows = input.getDrivingKeyNames() != null ? input.getDrivingKeyNames().size() : 1;
    wDrivingKeyNames =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            drivingColumns,
            nrDrivingRows,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdDrivingKeys = new FormData();
    fdDrivingKeys.left = new FormAttachment(50, margin);
    fdDrivingKeys.top = new FormAttachment(wlDrivingKeys, margin);
    fdDrivingKeys.right = new FormAttachment(100, 0);
    fdDrivingKeys.bottom = new FormAttachment(wOk, -2 * margin);
    wDrivingKeyNames.setLayoutData(fdDrivingKeys);

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void getData() {
    if (input.getName() != null) wName.setText(input.getName());
    if (input.getTableName() != null) wTableName.setText(input.getTableName());
    if (input.getDescription() != null) wDescription.setText(input.getDescription());
    try {
      wRecordSource.fillItems();
      wRecordSource.setText(Const.NVL(input.getRecordSource(), ""));
    } catch (HopException e) {
      wRecordSource.setText(Const.NVL(input.getRecordSource(), ""));
    }
    if (input.getLinkHashKeyFieldName() != null) wLinkHashKeyFieldName.setText(input.getLinkHashKeyFieldName());

    wHasDescriptiveAttributes.setSelection(input.isHasDescriptiveAttributes());

    if (input.getHubNames() != null) {
      List<DvLink.HubSourceKeyField> keyFields = input.getHubSourceKeyFields();
      for (int i = 0; i < input.getHubNames().size(); i++) {
        String hubName = input.getHubNames().get(i);
        TableItem item = wHubNames.table.getItem(i);
        item.setText(1, Const.NVL(hubName, ""));
        String keysStr = "";
        if (keyFields != null) {
          for (DvLink.HubSourceKeyField field : keyFields) {
            if (hubName.equals(field.getHubName()) && field.getSourceBusinessKeyFields() != null) {
              keysStr = String.join(",", field.getSourceBusinessKeyFields());
              break;
            }
          }
        }
        item.setText(2, keysStr);
      }
    }
    if (input.getDrivingKeyNames() != null) {
      for (int i = 0; i < input.getDrivingKeyNames().size(); i++) {
        TableItem item = wDrivingKeyNames.table.getItem(i);
        item.setText(1, Const.NVL(input.getDrivingKeyNames().get(i), ""));
      }
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    input.setRecordSource(wRecordSource.getText());
    input.setLinkHashKeyFieldName(wLinkHashKeyFieldName.getText());
    input.setHasDescriptiveAttributes(wHasDescriptiveAttributes.getSelection());

    List<String> hubs = new ArrayList<>();
    List<DvLink.HubSourceKeyField> hubKeyFields = new ArrayList<>();
    for (TableItem item : wHubNames.getNonEmptyItems()) {
      String hubName = item.getText(1);
      hubs.add(hubName);
      String keysText = item.getText(2);
      List<String> keys = new ArrayList<>();
      if (!Utils.isEmpty(keysText)) {
        for (String k : keysText.split(",")) {
          String trimmed = k.trim();
          if (!trimmed.isEmpty()) keys.add(trimmed);
        }
      }
      DvLink.HubSourceKeyField field = new DvLink.HubSourceKeyField();
      field.setHubName(hubName);
      field.setSourceBusinessKeyFields(keys);
      hubKeyFields.add(field);
    }
    input.setHubNames(hubs);
    input.setHubSourceKeyFields(hubKeyFields);

    List<String> drives = new ArrayList<>();
    for (TableItem item : wDrivingKeyNames.getNonEmptyItems()) {
      drives.add(item.getText(1));
    }
    input.setDrivingKeyNames(drives);

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
