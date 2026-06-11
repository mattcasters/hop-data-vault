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
 * Unless otherwise indicated, files in this package are licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a copy of the License at
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
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.apache.hop.datavault.metadata.DrivingKeySource;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
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
 * Dialog to edit the source field mappings for one hub within one DvLinkSource. Allows specifying
 * which source columns map to the hub's business keys and which source columns supply any driving
 * keys.
 */
public class HubSourceKeyFieldDialog {
  private static final Class<?> PKG = HubSourceKeyFieldDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink.HubSourceKeyField input;
  private final List<String> availableHubNames;

  private Shell shell;

  private Text wHubName;
  private TableView wBusinessKeySources;
  private TableView wDrivingKeySources;

  private boolean ok;

  public HubSourceKeyFieldDialog(
      Shell parent, HopGui hopGui, DvLink.HubSourceKeyField field, List<String> hubs) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = field;
    this.availableHubNames = (hubs != null) ? new ArrayList<>(hubs) : new ArrayList<>();
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText("Edit Hub Source Key Fields");

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    // Buttons at the bottom
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    // Hub name (can be prefilled or chosen)
    Label wlHubName = new Label(shell, SWT.RIGHT);
    wlHubName.setText("Hub name");
    PropsUi.setLook(wlHubName);
    FormData fdlHubName = new FormData();
    fdlHubName.left = new FormAttachment(0, 0);
    fdlHubName.top = new FormAttachment(0, margin);
    fdlHubName.right = new FormAttachment(middle, -margin);
    wlHubName.setLayoutData(fdlHubName);

    wHubName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHubName);
    FormData fdHubName = new FormData();
    fdHubName.left = new FormAttachment(middle, 0);
    fdHubName.top = new FormAttachment(0, margin);
    fdHubName.right = new FormAttachment(100, 0);
    wHubName.setLayoutData(fdHubName);
    // If we have known hubs and none set yet, leave blank for user; editing context usually prefills

    // Business key sources section
    Label wlBks = new Label(shell, SWT.LEFT);
    wlBks.setText("Business key source fields (hub BK field -> source column)");
    PropsUi.setLook(wlBks);
    FormData fdlBks = new FormData();
    fdlBks.left = new FormAttachment(0, 0);
    fdlBks.top = new FormAttachment(wHubName, margin);
    wlBks.setLayoutData(fdlBks);

    ColumnInfo[] bkCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Hub business key field",
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              "Source field name",
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrBk = (input.getSourceBusinessKeyFields() != null)
        ? Math.max(1, input.getSourceBusinessKeyFields().size())
        : 2;
    wBusinessKeySources =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            bkCols,
            nrBk,
            e -> {},
            PropsUi.getInstance());

    FormData fdBks = new FormData();
    fdBks.left = new FormAttachment(0, 0);
    fdBks.top = new FormAttachment(wlBks, margin);
    fdBks.right = new FormAttachment(100, 0);
    fdBks.bottom = new FormAttachment(50, -margin);
    wBusinessKeySources.setLayoutData(fdBks);

    // Driving key sources section
    Label wlDks = new Label(shell, SWT.LEFT);
    wlDks.setText("Driving key source fields (driving key -> source column)");
    PropsUi.setLook(wlDks);
    FormData fdlDks = new FormData();
    fdlDks.left = new FormAttachment(0, 0);
    fdlDks.top = new FormAttachment(wBusinessKeySources, margin);
    wlDks.setLayoutData(fdlDks);

    ColumnInfo[] dkCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Driving key",
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              "Source field name",
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrDk = (input.getDrivingKeySources() != null)
        ? Math.max(1, input.getDrivingKeySources().size())
        : 2;
    wDrivingKeySources =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            dkCols,
            nrDk,
            e -> {},
            PropsUi.getInstance());

    FormData fdDks = new FormData();
    fdDks.left = new FormAttachment(0, 0);
    fdDks.top = new FormAttachment(wlDks, margin);
    fdDks.right = new FormAttachment(100, 0);
    fdDks.bottom = new FormAttachment(wOk, -2 * margin);
    wDrivingKeySources.setLayoutData(fdDks);

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void getData() {
    if (input.getHubName() != null) {
      wHubName.setText(input.getHubName());
    }

    // Business key sources
    wBusinessKeySources.clearAll();
    if (input.getSourceBusinessKeyFields() != null) {
      for (int i = 0; i < input.getSourceBusinessKeyFields().size(); i++) {
        BusinessKeySource bs = input.getSourceBusinessKeyFields().get(i);
        TableItem item = wBusinessKeySources.table.getItem(i);
        item.setText(1, Const.NVL(bs.getBusinessKeyField(), ""));
        item.setText(2, Const.NVL(bs.getSourceFieldName(), ""));
      }
    }
    wBusinessKeySources.removeEmptyRows();
    wBusinessKeySources.setRowNums();
    wBusinessKeySources.optWidth(true);

    // Driving key sources
    wDrivingKeySources.clearAll();
    if (input.getDrivingKeySources() != null) {
      for (int i = 0; i < input.getDrivingKeySources().size(); i++) {
        DrivingKeySource ds = input.getDrivingKeySources().get(i);
        TableItem item = wDrivingKeySources.table.getItem(i);
        item.setText(1, Const.NVL(ds.getDrivingKey(), ""));
        item.setText(2, Const.NVL(ds.getSourceField(), ""));
      }
    }
    wDrivingKeySources.removeEmptyRows();
    wDrivingKeySources.setRowNums();
    wDrivingKeySources.optWidth(true);
  }

  private void ok() {
    input.setHubName(wHubName.getText());

    // Read business key sources table
    List<BusinessKeySource> bks = new ArrayList<>();
    for (TableItem item : wBusinessKeySources.getNonEmptyItems()) {
      BusinessKeySource bs = new BusinessKeySource();
      bs.setBusinessKeyField(item.getText(1));
      bs.setSourceFieldName(item.getText(2));
      if (!Utils.isEmpty(bs.getBusinessKeyField()) || !Utils.isEmpty(bs.getSourceFieldName())) {
        bks.add(bs);
      }
    }
    input.setSourceBusinessKeyFields(bks);

    // Read driving key sources table
    List<DrivingKeySource> dks = new ArrayList<>();
    for (TableItem item : wDrivingKeySources.getNonEmptyItems()) {
      DrivingKeySource ds = new DrivingKeySource();
      ds.setDrivingKey(item.getText(1));
      ds.setSourceField(item.getText(2));
      if (!Utils.isEmpty(ds.getDrivingKey()) || !Utils.isEmpty(ds.getSourceField())) {
        dks.add(ds);
      }
    }
    input.setDrivingKeySources(dks);

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
