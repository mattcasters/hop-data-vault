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
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to edit the properties of a DvSatellite, including attributes list using TableView.
 */
public class HopGuiSatelliteDialog {
  private static final Class<?> PKG = HopGuiSatelliteDialog.class;

  private Shell parent;
  private IVariables variables;
  private DvSatellite input;
  private HopGui hopGui;
  private Shell shell;

  // Widgets
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private MetaSelectionLine<DataVaultSource> wRecordSource;
  private Text wHubName;
  private Text wLinkName;
  private Button wMultiActive;
  private Text wDrivingKey;
  private TableView wAttributes;

  private boolean ok;

  public HopGuiSatelliteDialog(Shell parent, HopGui hopGui, DvSatellite satellite) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = satellite;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Title", input.getName()));

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
    Button wGetAttributes = new Button(shell, SWT.PUSH);
    wGetAttributes.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.GetAttributes.Button"));
    wGetAttributes.addListener(SWT.Selection, e -> getAttributes());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wGetAttributes, wCancel}, margin, null);

    // Name
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Name.Label"));
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
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.TableName.Label"));
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
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Description.Label"));
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

    // Record source (now a reference to DataVaultSource metadata)
    wRecordSource =
        new MetaSelectionLine<>(
            variables,
            hopGui.getMetadataProvider(),
            DataVaultSource.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiSatelliteDialog.RecordSource.Label"),
            BaseMessages.getString(PKG, "HopGuiSatelliteDialog.RecordSource.ToolTip"));
    FormData fdRecordSource = new FormData();
    fdRecordSource.left = new FormAttachment(0, 0);
    fdRecordSource.top = new FormAttachment(wDescription, margin);
    fdRecordSource.right = new FormAttachment(100, 0);
    wRecordSource.setLayoutData(fdRecordSource);
    wRecordSource.addModifyListener(e -> input.setChanged());

    // Hub name (ref)
    Label wlHubName = new Label(shell, SWT.RIGHT);
    wlHubName.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.HubName.Label"));
    PropsUi.setLook(wlHubName);
    FormData fdlHubName = new FormData();
    fdlHubName.left = new FormAttachment(0, 0);
    fdlHubName.top = new FormAttachment(wRecordSource, margin);
    fdlHubName.right = new FormAttachment(middle, -margin);
    wlHubName.setLayoutData(fdlHubName);

    wHubName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHubName);
    FormData fdHubName = new FormData();
    fdHubName.left = new FormAttachment(middle, 0);
    fdHubName.top = new FormAttachment(wRecordSource, margin);
    fdHubName.right = new FormAttachment(100, 0);
    wHubName.setLayoutData(fdHubName);
    wHubName.addModifyListener(e -> input.setChanged());

    // Link name (ref)
    Label wlLinkName = new Label(shell, SWT.RIGHT);
    wlLinkName.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.LinkName.Label"));
    PropsUi.setLook(wlLinkName);
    FormData fdlLinkName = new FormData();
    fdlLinkName.left = new FormAttachment(0, 0);
    fdlLinkName.top = new FormAttachment(wHubName, margin);
    fdlLinkName.right = new FormAttachment(middle, -margin);
    wlLinkName.setLayoutData(fdlLinkName);

    wLinkName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLinkName);
    FormData fdLinkName = new FormData();
    fdLinkName.left = new FormAttachment(middle, 0);
    fdLinkName.top = new FormAttachment(wHubName, margin);
    fdLinkName.right = new FormAttachment(100, 0);
    wLinkName.setLayoutData(fdLinkName);
    wLinkName.addModifyListener(e -> input.setChanged());

    // Multi active
    Label wlMultiActive = new Label(shell, SWT.RIGHT);
    wlMultiActive.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.MultiActive.Label"));
    PropsUi.setLook(wlMultiActive);
    FormData fdlMultiActive = new FormData();
    fdlMultiActive.left = new FormAttachment(0, 0);
    fdlMultiActive.top = new FormAttachment(wLinkName, margin);
    fdlMultiActive.right = new FormAttachment(middle, -margin);
    wlMultiActive.setLayoutData(fdlMultiActive);

    wMultiActive = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wMultiActive);
    FormData fdMultiActive = new FormData();
    fdMultiActive.left = new FormAttachment(middle, 0);
    fdMultiActive.top = new FormAttachment(wLinkName, margin);
    wMultiActive.setLayoutData(fdMultiActive);
    wMultiActive.addListener(SWT.Selection, e -> input.setChanged());

    // Driving key
    Label wlDrivingKey = new Label(shell, SWT.RIGHT);
    wlDrivingKey.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.DrivingKey.Label"));
    PropsUi.setLook(wlDrivingKey);
    FormData fdlDrivingKey = new FormData();
    fdlDrivingKey.left = new FormAttachment(0, 0);
    fdlDrivingKey.top = new FormAttachment(wMultiActive, margin);
    fdlDrivingKey.right = new FormAttachment(middle, -margin);
    wlDrivingKey.setLayoutData(fdlDrivingKey);

    wDrivingKey = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDrivingKey);
    FormData fdDrivingKey = new FormData();
    fdDrivingKey.left = new FormAttachment(middle, 0);
    fdDrivingKey.top = new FormAttachment(wMultiActive, margin);
    fdDrivingKey.right = new FormAttachment(100, 0);
    wDrivingKey.setLayoutData(fdDrivingKey);
    wDrivingKey.addModifyListener(e -> input.setChanged());

    // Attributes table
    Label wlAttributes = new Label(shell, SWT.LEFT);
    wlAttributes.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attributes.Label"));
    PropsUi.setLook(wlAttributes);
    FormData fdlAttributes = new FormData();
    fdlAttributes.left = new FormAttachment(0, 0);
    fdlAttributes.top = new FormAttachment(wDrivingKey, margin);
    wlAttributes.setLayoutData(fdlAttributes);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attribute.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attribute.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attribute.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attribute.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attribute.Precision.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSatelliteDialog.Attribute.IncludeInChangeDataCapture.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {
                BaseMessages.getString(PKG, "System.Combo.Yes"),
                BaseMessages.getString(PKG, "System.Combo.No")
              }),
        };

    int nrRows = input.getAttributes() != null ? input.getAttributes().size() : 1;
    wAttributes =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdAttributes = new FormData();
    fdAttributes.left = new FormAttachment(0, 0);
    fdAttributes.top = new FormAttachment(wlAttributes, margin);
    fdAttributes.right = new FormAttachment(100, 0);
    fdAttributes.bottom = new FormAttachment(wOk, -2 * margin);
    wAttributes.setLayoutData(fdAttributes);

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
    if (input.getHubName() != null) wHubName.setText(input.getHubName());
    if (input.getLinkName() != null) wLinkName.setText(input.getLinkName());
    wMultiActive.setSelection(input.isMultiActive());
    if (input.getDrivingKey() != null) wDrivingKey.setText(input.getDrivingKey());

    if (input.getAttributes() != null) {
      for (int i = 0; i < input.getAttributes().size(); i++) {
        SatelliteAttribute attr = input.getAttributes().get(i);
        TableItem item = wAttributes.table.getItem(i);
        item.setText(1, Const.NVL(attr.getName(), ""));
        item.setText(2, Const.NVL(attr.getDescription(), ""));
        item.setText(3, Const.NVL(attr.getDataType(), ""));
        item.setText(4, Const.NVL(attr.getLength(), ""));
        item.setText(5, Const.NVL(attr.getPrecision(), ""));
        item.setText(6, attr.isIncludeInChangeDataCapture() ? "Y" : "N");
      }
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    input.setRecordSource(wRecordSource.getText());
    input.setHubName(wHubName.getText());
    input.setLinkName(wLinkName.getText());
    input.setMultiActive(wMultiActive.getSelection());
    input.setDrivingKey(wDrivingKey.getText());

    List<SatelliteAttribute> attrs = new ArrayList<>();
    for (TableItem item : wAttributes.getNonEmptyItems()) {
      SatelliteAttribute attr = new SatelliteAttribute();
      attr.setName(item.getText(1));
      attr.setDescription(item.getText(2));
      attr.setDataType(item.getText(3));
      attr.setLength(item.getText(4));
      attr.setPrecision(item.getText(5));
      attr.setIncludeInChangeDataCapture("Y".equalsIgnoreCase(item.getText(6)));
      attrs.add(attr);
    }
    input.setAttributes(attrs);

    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void getAttributes() {
    String sourceName = wRecordSource.getText();
    if (Utils.isEmpty(sourceName)) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "HopGuiSatelliteDialog.GetAttributes.NoSource.Message"));
      mb.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.GetAttributes.NoSource.Title"));
      mb.open();
      return;
    }

    DataVaultSource source = null;
    List<SourceField> sourceFields = null;

    try {
      source =
          hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(sourceName);
      if (source != null) {
        sourceFields = source.getFields(hopGui.getMetadataProvider());
      }
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSatelliteDialog.GetAttributes.ErrorLoadingSource.Message", sourceName),
          e);
      return;
    }

    if (source == null
        || sourceFields == null
        || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "HopGuiSatelliteDialog.GetAttributes.NoFields.Message", sourceName));
      mb.setText(BaseMessages.getString(PKG, "HopGuiSatelliteDialog.GetAttributes.NoFields.Title"));
      mb.open();
      return;
    }

    String drivingKey = wDrivingKey.getText() != null ? wDrivingKey.getText().trim() : "";

    for (SourceField sf : sourceFields) {
      if (sf.isPrimaryKey()) {
        continue;
      }
      if (!Utils.isEmpty(drivingKey) && drivingKey.equals(sf.getName())) {
        continue;
      }

      TableItem item = new TableItem(wAttributes.table, SWT.NONE);
      item.setText(1, Const.NVL(sf.getName(), ""));
      item.setText(2, Const.NVL(sf.getDescription(), ""));
      item.setText(3, Const.NVL(sf.getSourceDataType(), ""));
      item.setText(4, Const.NVL(sf.getLength(), ""));
      item.setText(5, Const.NVL(sf.getPrecision(), ""));
      item.setText(6, "Y"); // includeInChangeDataCapture default true
    }

    wAttributes.removeEmptyRows();
    wAttributes.setRowNums();
    wAttributes.optWidth(true);
    input.setChanged();
  }

  private void dispose() {
    if (!shell.isDisposed()) {
      shell.dispose();
    }
  }
}
