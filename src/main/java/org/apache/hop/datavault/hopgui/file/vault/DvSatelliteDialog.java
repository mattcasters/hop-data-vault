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

package org.apache.hop.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit the properties of a DvSatellite, including attributes list using TableView. */
public class DvSatelliteDialog {
  private static final Class<?> PKG = DvSatelliteDialog.class;

  private Shell parent;
  private IVariables variables;
  private DvSatellite input;
  private DataVaultModel model;
  private HopGui hopGui;
  private Shell shell;

  // Widgets
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private MetaSelectionLine<DataVaultSource> wRecordSource;
  private Text wHubName;
  private Text wLinkName;
  private Text wDrivingKey;
  private Combo wDrivingKeySourceField;
  private TableView wAttributes;

  private boolean ok;

  public DvSatelliteDialog(
      Shell parent, HopGui hopGui, DvSatellite satellite, DataVaultModel model) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = satellite;
    this.model = model;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Title", input.getName()));

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
    wGetAttributes.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.Button"));
    wGetAttributes.addListener(SWT.Selection, e -> getAttributes());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wGetAttributes, wCancel}, margin, null);

    // Name
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Name.Label"));
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

    // Table name
    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.TableName.Label"));
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

    // Description
    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Description.Label"));
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

    // Record source (now a reference to DataVaultSource metadata)
    wRecordSource =
        new MetaSelectionLine<>(
            variables,
            hopGui.getMetadataProvider(),
            DataVaultSource.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "DvSatelliteDialog.RecordSource.Label"),
            BaseMessages.getString(PKG, "DvSatelliteDialog.RecordSource.ToolTip"));
    FormData fdRecordSource = new FormData();
    fdRecordSource.left = new FormAttachment(0, 0);
    fdRecordSource.top = new FormAttachment(wDescription, margin);
    fdRecordSource.right = new FormAttachment(100, 0);
    wRecordSource.setLayoutData(fdRecordSource);

    // Hub name (ref)
    Label wlHubName = new Label(shell, SWT.RIGHT);
    wlHubName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.HubName.Label"));
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

    // Link name (ref)
    Label wlLinkName = new Label(shell, SWT.RIGHT);
    wlLinkName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.LinkName.Label"));
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

    // Driving key
    Label wlDrivingKey = new Label(shell, SWT.RIGHT);
    wlDrivingKey.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.DrivingKey.Label"));
    PropsUi.setLook(wlDrivingKey);
    FormData fdlDrivingKey = new FormData();
    fdlDrivingKey.left = new FormAttachment(0, 0);
    fdlDrivingKey.top = new FormAttachment(wLinkName, margin);
    fdlDrivingKey.right = new FormAttachment(middle, -margin);
    wlDrivingKey.setLayoutData(fdlDrivingKey);

    wDrivingKey = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDrivingKey);
    FormData fdDrivingKey = new FormData();
    fdDrivingKey.left = new FormAttachment(middle, 0);
    fdDrivingKey.top = new FormAttachment(wLinkName, margin);
    fdDrivingKey.right = new FormAttachment(100, 0);
    wDrivingKey.setLayoutData(fdDrivingKey);

    Label wlDrivingKeySourceField = new Label(shell, SWT.RIGHT);
    wlDrivingKeySourceField.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.DrivingKeySourceField.Label"));
    PropsUi.setLook(wlDrivingKeySourceField);
    FormData fdlDrivingKeySourceField = new FormData();
    fdlDrivingKeySourceField.left = new FormAttachment(0, 0);
    fdlDrivingKeySourceField.top = new FormAttachment(wDrivingKey, margin);
    fdlDrivingKeySourceField.right = new FormAttachment(middle, -margin);
    wlDrivingKeySourceField.setLayoutData(fdlDrivingKeySourceField);

    wDrivingKeySourceField = new Combo(shell, SWT.BORDER);
    PropsUi.setLook(wDrivingKeySourceField);
    FormData fdDrivingKeySourceField = new FormData();
    fdDrivingKeySourceField.left = new FormAttachment(middle, 0);
    fdDrivingKeySourceField.top = new FormAttachment(wDrivingKey, margin);
    fdDrivingKeySourceField.right = new FormAttachment(100, 0);
    wDrivingKeySourceField.setLayoutData(fdDrivingKeySourceField);

    // Attributes table
    Label wlAttributes = new Label(shell, SWT.LEFT);
    wlAttributes.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Attributes.Label"));
    PropsUi.setLook(wlAttributes);
    FormData fdlAttributes = new FormData();
    fdlAttributes.left = new FormAttachment(0, 0);
    fdlAttributes.top = new FormAttachment(wDrivingKeySourceField, margin);
    wlAttributes.setLayoutData(fdlAttributes);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Precision.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "DvSatelliteDialog.Attribute.IncludeInChangeDataCapture.Column"),
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
            null,
            PropsUi.getInstance());

    FormData fdAttributes = new FormData();
    fdAttributes.left = new FormAttachment(0, 0);
    fdAttributes.top = new FormAttachment(wlAttributes, margin);
    fdAttributes.right = new FormAttachment(100, 0);
    fdAttributes.bottom = new FormAttachment(wOk, -2 * margin);
    wAttributes.setLayoutData(fdAttributes);

    wRecordSource.addModifyListener(e -> refreshDrivingKeySourceFieldItems());

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    try {
      wRecordSource.fillItems();
      if (input.getRecordSource() != null) {
        wRecordSource.setText(Const.NVL(input.getRecordSource().getName(), ""));
      }
    } catch (HopException e) {
      wRecordSource.setText("");
    }
    wHubName.setText(Const.NVL(input.getHubName(), ""));
    wLinkName.setText(Const.NVL(input.getLinkName(), ""));
    wDrivingKey.setText(Const.NVL(input.getDrivingKey(), ""));
    refreshDrivingKeySourceFieldItems();
    wDrivingKeySourceField.setText(Const.NVL(input.getDrivingKeySourceField(), ""));

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

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    // For multi-source future, set first for now (satellites typically single source)
    String rsName = wRecordSource.getText();

    try {
      DataVaultSource recordSource =
          hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(rsName);
      input.setRecordSource(recordSource);
    } catch (HopException e) {
      new ErrorDialog(shell, "Error", "Error loading data vault source " + rsName, e);
      return;
    }

    // If you want full list support for satellites later, change to setRecordSources here.
    input.setHubName(wHubName.getText());
    input.setLinkName(wLinkName.getText());
    input.setDrivingKey(wDrivingKey.getText());
    input.setDrivingKeySourceField(wDrivingKeySourceField.getText());

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

    input.setChanged();
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void refreshDrivingKeySourceFieldItems() {
    if (wDrivingKeySourceField == null || wDrivingKeySourceField.isDisposed()) {
      return;
    }
    String current = wDrivingKeySourceField.getText();
    wDrivingKeySourceField.removeAll();
    for (String fieldName : loadSortedRecordSourceFieldNames(wRecordSource.getText())) {
      wDrivingKeySourceField.add(fieldName);
    }
    if (!Utils.isEmpty(current)) {
      wDrivingKeySourceField.setText(current);
    }
  }

  private List<String> loadSortedRecordSourceFieldNames(String sourceName) {
    if (Utils.isEmpty(sourceName)) {
      return List.of();
    }
    try {
      DataVaultSource source =
          hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(sourceName);
      if (source == null) {
        return List.of();
      }
      List<SourceField> sourceFields = source.getFields(hopGui.getMetadataProvider());
      if (sourceFields == null || sourceFields.isEmpty()) {
        return List.of();
      }
      List<String> names = new ArrayList<>();
      for (SourceField sourceField : sourceFields) {
        if (sourceField != null && !Utils.isEmpty(sourceField.getName())) {
          names.add(sourceField.getName());
        }
      }
      Collections.sort(names);
      return names;
    } catch (HopException e) {
      return List.of();
    }
  }

  private void getAttributes() {
    String sourceName = wRecordSource.getText();
    if (Utils.isEmpty(sourceName)) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.NoSource.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.NoSource.Title"));
      mb.open();
      return;
    }

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
              PKG, "DvSatelliteDialog.GetAttributes.ErrorLoadingSource.Message", sourceName),
          e);
      return;
    }

    if (source == null || sourceFields == null || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvSatelliteDialog.GetAttributes.NoFields.Message", sourceName));
      mb.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.NoFields.Title"));
      mb.open();
      return;
    }

    String drivingKey = wDrivingKey.getText() != null ? wDrivingKey.getText().trim() : "";
    String drivingKeySourceField =
        wDrivingKeySourceField.getText() != null ? wDrivingKeySourceField.getText().trim() : "";

    String hubName = wHubName.getText() != null ? wHubName.getText().trim() : "";
    DvHub linkedHub = null;
    if (!Utils.isEmpty(hubName) && model != null) {
      linkedHub = model.findHub(hubName);
    }
    Set<String> excluded =
        linkedHub != null
            ? DvSatellite.excludedHubSatelliteSourceFieldNames(
                linkedHub, variables, drivingKey, drivingKeySourceField)
            : Collections.emptySet();

    for (SourceField sf : sourceFields) {
      String name = variables.resolve(sf.getName());
      if (excluded.contains(name)) {
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

    wAttributes.optimizeTableView();
  }

  private void dispose() {
    if (!shell.isDisposed()) {
      shell.dispose();
    }
  }
}
