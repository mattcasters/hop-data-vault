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

package org.apache.hop.datavault.hopgui.file.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BvDerivativeRef;
import org.apache.hop.datavault.metadata.businessvault.BvPitTable;
import org.apache.hop.datavault.metadata.businessvault.BvScd2Table;
import org.apache.hop.datavault.metadata.businessvault.BvTableType;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
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

/** Dialog to edit a Business Vault table on the canvas. */
public class HopGuiBvTableDialog {
  private static final Class<?> PKG = HopGuiBvTableDialog.class;

  private final Shell parent;
  private final IBvTable input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private Shell shell;

  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Text wFunctionalTimestamp;
  private Text wValidFromField;
  private Text wValidToField;
  private Text wSnapshotDateField;
  private Label wlFunctionalTimestamp;
  private Label wlValidFromField;
  private Label wlValidToField;
  private Label wlSnapshotDateField;
  private Label wlDerivatives;
  private TableView wDerivatives;
  private Button wAddDerivative;
  private Button wDeleteDerivative;

  private boolean ok;

  public HopGuiBvTableDialog(
      Shell parent,
      IBvTable table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    this.parent = parent;
    this.input = table;
    this.businessVaultModel = businessVaultModel;
    this.dataVaultModel = dataVaultModel;
    this.variables = variables;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvTableDialog.Title",
            Const.NVL(input.getName(), input.getTableType().name())));
    shell.setSize(560, 520);

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Name.Label"));
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

    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.TableName.Label"));
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

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Description.Label"));
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

    wlFunctionalTimestamp = new Label(shell, SWT.RIGHT);
    wlFunctionalTimestamp.setText(
        BaseMessages.getString(PKG, "HopGuiBvTableDialog.FunctionalTimestamp.Label"));
    PropsUi.setLook(wlFunctionalTimestamp);
    FormData fdlFunctionalTimestamp = new FormData();
    fdlFunctionalTimestamp.left = new FormAttachment(0, 0);
    fdlFunctionalTimestamp.top = new FormAttachment(wDescription, margin);
    fdlFunctionalTimestamp.right = new FormAttachment(middle, -margin);
    wlFunctionalTimestamp.setLayoutData(fdlFunctionalTimestamp);

    wFunctionalTimestamp = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFunctionalTimestamp);
    FormData fdFunctionalTimestamp = new FormData();
    fdFunctionalTimestamp.left = new FormAttachment(middle, 0);
    fdFunctionalTimestamp.top = new FormAttachment(wDescription, margin);
    fdFunctionalTimestamp.right = new FormAttachment(100, 0);
    wFunctionalTimestamp.setLayoutData(fdFunctionalTimestamp);

    wlValidFromField = new Label(shell, SWT.RIGHT);
    wlValidFromField.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.ValidFromField.Label"));
    PropsUi.setLook(wlValidFromField);
    FormData fdlValidFromField = new FormData();
    fdlValidFromField.left = new FormAttachment(0, 0);
    fdlValidFromField.top = new FormAttachment(wFunctionalTimestamp, margin);
    fdlValidFromField.right = new FormAttachment(middle, -margin);
    wlValidFromField.setLayoutData(fdlValidFromField);

    wValidFromField = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wValidFromField);
    FormData fdValidFromField = new FormData();
    fdValidFromField.left = new FormAttachment(middle, 0);
    fdValidFromField.top = new FormAttachment(wFunctionalTimestamp, margin);
    fdValidFromField.right = new FormAttachment(100, 0);
    wValidFromField.setLayoutData(fdValidFromField);

    wlValidToField = new Label(shell, SWT.RIGHT);
    wlValidToField.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.ValidToField.Label"));
    PropsUi.setLook(wlValidToField);
    FormData fdlValidToField = new FormData();
    fdlValidToField.left = new FormAttachment(0, 0);
    fdlValidToField.top = new FormAttachment(wValidFromField, margin);
    fdlValidToField.right = new FormAttachment(middle, -margin);
    wlValidToField.setLayoutData(fdlValidToField);

    wValidToField = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wValidToField);
    FormData fdValidToField = new FormData();
    fdValidToField.left = new FormAttachment(middle, 0);
    fdValidToField.top = new FormAttachment(wValidFromField, margin);
    fdValidToField.right = new FormAttachment(100, 0);
    wValidToField.setLayoutData(fdValidToField);

    wlSnapshotDateField = new Label(shell, SWT.RIGHT);
    wlSnapshotDateField.setText(
        BaseMessages.getString(PKG, "HopGuiBvTableDialog.SnapshotDateField.Label"));
    PropsUi.setLook(wlSnapshotDateField);
    FormData fdlSnapshotDateField = new FormData();
    fdlSnapshotDateField.left = new FormAttachment(0, 0);
    fdlSnapshotDateField.top = new FormAttachment(wFunctionalTimestamp, margin);
    fdlSnapshotDateField.right = new FormAttachment(middle, -margin);
    wlSnapshotDateField.setLayoutData(fdlSnapshotDateField);

    wSnapshotDateField = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSnapshotDateField);
    FormData fdSnapshotDateField = new FormData();
    fdSnapshotDateField.left = new FormAttachment(middle, 0);
    fdSnapshotDateField.top = new FormAttachment(wFunctionalTimestamp, margin);
    fdSnapshotDateField.right = new FormAttachment(100, 0);
    wSnapshotDateField.setLayoutData(fdSnapshotDateField);

    boolean scd2 = input.getTableType() == BvTableType.SCD2;
    boolean pit = input.getTableType() == BvTableType.PIT;
    wlFunctionalTimestamp.setVisible(scd2);
    wFunctionalTimestamp.setVisible(scd2);
    wlValidFromField.setVisible(scd2);
    wValidFromField.setVisible(scd2);
    wlValidToField.setVisible(scd2);
    wValidToField.setVisible(scd2);
    wlSnapshotDateField.setVisible(pit);
    wSnapshotDateField.setVisible(pit);

    if (scd2) {
      applyScd2FieldTooltips();
    }

    org.eclipse.swt.widgets.Control derivativeTop =
        pit ? wSnapshotDateField : (scd2 ? wValidToField : wDescription);

    wlDerivatives = new Label(shell, SWT.LEFT);
    wlDerivatives.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Label"));
    PropsUi.setLook(wlDerivatives);
    FormData fdlDerivatives = new FormData();
    fdlDerivatives.left = new FormAttachment(0, 0);
    fdlDerivatives.top = new FormAttachment(derivativeTop, 2 * margin);
    fdlDerivatives.right = new FormAttachment(100, 0);
    wlDerivatives.setLayoutData(fdlDerivatives);

    wAddDerivative = new Button(shell, SWT.PUSH);
    wAddDerivative.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Add"));
    PropsUi.setLook(wAddDerivative);
    FormData fdAddDerivative = new FormData();
    fdAddDerivative.left = new FormAttachment(0, 0);
    fdAddDerivative.top = new FormAttachment(wlDerivatives, margin);
    wAddDerivative.setLayoutData(fdAddDerivative);
    wAddDerivative.addListener(SWT.Selection, e -> addDerivativeRow());

    wDeleteDerivative = new Button(shell, SWT.PUSH);
    wDeleteDerivative.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Delete"));
    PropsUi.setLook(wDeleteDerivative);
    FormData fdDeleteDerivative = new FormData();
    fdDeleteDerivative.left = new FormAttachment(wAddDerivative, margin);
    fdDeleteDerivative.top = new FormAttachment(wlDerivatives, margin);
    wDeleteDerivative.setLayoutData(fdDeleteDerivative);
    wDeleteDerivative.addListener(SWT.Selection, e -> removeDerivativeRows());

    ColumnInfo[] derivativeCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Column.Name"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getEligibleDvTableNames(),
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Column.Type"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    derivativeCols[1].setReadOnly(true);

    wDerivatives =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            derivativeCols,
            1,
            null,
            PropsUi.getInstance());

    FormData fdDerivatives = new FormData();
    fdDerivatives.left = new FormAttachment(0, 0);
    fdDerivatives.top = new FormAttachment(wAddDerivative, margin);
    fdDerivatives.right = new FormAttachment(100, 0);
    fdDerivatives.bottom = new FormAttachment(100, -50);
    wDerivatives.setLayoutData(fdDerivatives);

    boolean dvAvailable = dataVaultModel != null && !dataVaultModel.getTables().isEmpty();
    wAddDerivative.setEnabled(dvAvailable);
    if (!dvAvailable) {
      wlDerivatives.setText(
          BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.MissingDvModel"));
    }

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    getData();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private String[] getEligibleDvTableNames() {
    List<String> names = new ArrayList<>();
    if (dataVaultModel == null) {
      return names.toArray(new String[0]);
    }
    for (IDvTable table : dataVaultModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || !BusinessVaultDerivativeSupport.isValidDerivativePair(
              input.getTableType(), table.getTableType())) {
        continue;
      }
      names.add(table.getName());
    }
    return names.toArray(new String[0]);
  }

  private void addDerivativeRow() {
    new TableItem(wDerivatives.table, SWT.NONE);
    wDerivatives.removeEmptyRows();
    wDerivatives.setRowNums();
    wDerivatives.optWidth(true);
  }

  private void removeDerivativeRows() {
    int idx = wDerivatives.getSelectionIndex();
    if (idx >= 0) {
      wDerivatives.table.remove(idx);
      wDerivatives.removeEmptyRows();
      wDerivatives.setRowNums();
      wDerivatives.optWidth(true);
    }
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getTableName())) {
      wTableName.setText(input.getTableName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    if (input instanceof BvScd2Table scd2) {
      if (!Utils.isEmpty(scd2.getFunctionalTimestampField())) {
        wFunctionalTimestamp.setText(scd2.getFunctionalTimestampField());
      }
      if (!Utils.isEmpty(scd2.getValidFromField())) {
        wValidFromField.setText(scd2.getValidFromField());
      }
      if (!Utils.isEmpty(scd2.getValidToField())) {
        wValidToField.setText(scd2.getValidToField());
      }
    }
    if (input instanceof BvPitTable pit && !Utils.isEmpty(pit.getSnapshotDateField())) {
      wSnapshotDateField.setText(pit.getSnapshotDateField());
    }

    wDerivatives.clearAll();
    for (BvDerivativeRef derivative : input.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      TableItem item = new TableItem(wDerivatives.table, SWT.NONE);
      item.setText(1, derivative.getDvTableName());
      if (derivative.getDvTableType() != null) {
        item.setText(2, derivative.getDvTableType().name());
      }
    }
    wDerivatives.removeEmptyRows();
    wDerivatives.setRowNums();
    wDerivatives.optWidth(true);
  }

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    if (input instanceof BvScd2Table scd2) {
      scd2.setFunctionalTimestampField(wFunctionalTimestamp.getText());
      scd2.setValidFromField(wValidFromField.getText());
      scd2.setValidToField(wValidToField.getText());
    }
    if (input instanceof BvPitTable pit) {
      pit.setSnapshotDateField(wSnapshotDateField.getText());
    }

    input.getDerivatives().clear();
    for (TableItem item : wDerivatives.getNonEmptyItems()) {
      String dvName = item.getText(1);
      if (Utils.isEmpty(dvName)) {
        continue;
      }
      DvTableType dvType = null;
      if (dataVaultModel != null) {
        IDvTable dvTable = dataVaultModel.findTable(dvName);
        if (dvTable != null) {
          dvType = dvTable.getTableType();
        }
      }
      if (dvType == null && !Utils.isEmpty(item.getText(2))) {
        try {
          dvType = DvTableType.valueOf(item.getText(2));
        } catch (IllegalArgumentException ignored) {
          // keep null
        }
      }
      if (dvType != null
          && BusinessVaultDerivativeSupport.isValidDerivativePair(input.getTableType(), dvType)
          && !BusinessVaultDerivativeSupport.hasDerivative(input, dvName)) {
        input.getDerivatives().add(new BvDerivativeRef(dvName, dvType));
      }
    }

    ok = true;
    shell.dispose();
  }

  private void cancel() {
    ok = false;
    shell.dispose();
  }

  private void applyScd2FieldTooltips() {
    BusinessVaultConfiguration config =
        businessVaultModel != null
            ? businessVaultModel.getConfigurationOrDefault()
            : new BusinessVaultConfiguration();
    wlFunctionalTimestamp.setToolTipText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvTableDialog.FunctionalTimestamp.Tooltip",
            Const.NVL(config.getFunctionalTimestampField(), ""),
            Const.NVL(config.getLoadDateFieldFallback(), "LOAD_DATE")));
    wlValidFromField.setToolTipText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvTableDialog.ValidFromField.Tooltip",
            Const.NVL(
                config.getValidFromField(),
                BusinessVaultConfiguration.DEFAULT_VALID_FROM_FIELD)));
    wlValidToField.setToolTipText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvTableDialog.ValidToField.Tooltip",
            Const.NVL(config.getValidToField(), BusinessVaultConfiguration.DEFAULT_VALID_TO_FIELD)));
  }
}