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
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DvHub;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to edit the properties of a DvHub, including business keys list using TableView.
 */
public class HopGuiHubDialog {
  private static final Class<?> PKG = HopGuiHubDialog.class;

  private Shell parent;
  private IVariables variables;
  private DvHub input;
  private Shell shell;

  // Widgets
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Text wRecordSource;
  private TableView wBusinessKeys;

  private boolean ok;

  public HopGuiHubDialog(Shell parent, IVariables variables, DvHub hub) {
    this.parent = parent;
    this.variables = variables;
    this.input = hub;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiHubDialog.Title", input.getName()));

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
    wlName.setText(BaseMessages.getString(PKG, "HopGuiHubDialog.Name.Label"));
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

    // Table name (physical)
    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiHubDialog.TableName.Label"));
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
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiHubDialog.Description.Label"));
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

    // Record source
    Label wlRecordSource = new Label(shell, SWT.RIGHT);
    wlRecordSource.setText(BaseMessages.getString(PKG, "HopGuiHubDialog.RecordSource.Label"));
    PropsUi.setLook(wlRecordSource);
    FormData fdlRecordSource = new FormData();
    fdlRecordSource.left = new FormAttachment(0, 0);
    fdlRecordSource.top = new FormAttachment(wDescription, margin);
    fdlRecordSource.right = new FormAttachment(middle, -margin);
    wlRecordSource.setLayoutData(fdlRecordSource);

    wRecordSource = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRecordSource);
    FormData fdRecordSource = new FormData();
    fdRecordSource.left = new FormAttachment(middle, 0);
    fdRecordSource.top = new FormAttachment(wDescription, margin);
    fdRecordSource.right = new FormAttachment(100, 0);
    wRecordSource.setLayoutData(fdRecordSource);
    wRecordSource.addModifyListener(e -> input.setChanged());

    // Business keys - use TableView
    Label wlBusinessKeys = new Label(shell, SWT.LEFT);
    wlBusinessKeys.setText(BaseMessages.getString(PKG, "HopGuiHubDialog.BusinessKeys.Label"));
    PropsUi.setLook(wlBusinessKeys);
    FormData fdlBusinessKeys = new FormData();
    fdlBusinessKeys.left = new FormAttachment(0, 0);
    fdlBusinessKeys.top = new FormAttachment(wRecordSource, margin);
    wlBusinessKeys.setLayoutData(fdlBusinessKeys);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiHubDialog.BusinessKey.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiHubDialog.BusinessKey.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiHubDialog.BusinessKey.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiHubDialog.BusinessKey.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrRows = input.getBusinessKeys() != null ? input.getBusinessKeys().size() : 1;
    wBusinessKeys =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            e -> input.setChanged(),
            PropsUi.getInstance());

    FormData fdBusinessKeys = new FormData();
    fdBusinessKeys.left = new FormAttachment(0, 0);
    fdBusinessKeys.top = new FormAttachment(wlBusinessKeys, margin);
    fdBusinessKeys.right = new FormAttachment(100, 0);
    fdBusinessKeys.bottom = new FormAttachment(wOk, -2 * margin);
    wBusinessKeys.setLayoutData(fdBusinessKeys);

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void getData() {
    if (input.getName() != null) wName.setText(input.getName());
    if (input.getTableName() != null) wTableName.setText(input.getTableName());
    if (input.getDescription() != null) wDescription.setText(input.getDescription());
    if (input.getRecordSource() != null) wRecordSource.setText(input.getRecordSource());

    if (input.getBusinessKeys() != null) {
      for (int i = 0; i < input.getBusinessKeys().size(); i++) {
        BusinessKey bk = input.getBusinessKeys().get(i);
        TableItem item = wBusinessKeys.table.getItem(i);
        item.setText(1, Const.NVL(bk.getName(), ""));
        item.setText(2, Const.NVL(bk.getDescription(), ""));
        item.setText(3, Const.NVL(bk.getDataType(), ""));
        item.setText(4, Const.NVL(bk.getLength(), ""));
      }
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setTableName(wTableName.getText());
    input.setDescription(wDescription.getText());
    input.setRecordSource(wRecordSource.getText());

    // Business keys from table
    List<BusinessKey> keys = new ArrayList<>();
    for (TableItem item : wBusinessKeys.getNonEmptyItems()) {
      BusinessKey bk = new BusinessKey();
      bk.setName(item.getText(1));
      bk.setDescription(item.getText(2));
      bk.setDataType(item.getText(3));
      bk.setLength(item.getText(4));
      keys.add(bk);
    }
    input.setBusinessKeys(keys);

    ok = true;
    dispose();
  }

  private void cancel() {
    // restore backup? simple, no for now
    ok = false;
    dispose();
  }

  private void dispose() {
    if (!shell.isDisposed()) {
      shell.dispose();
    }
  }
}
