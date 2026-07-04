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

package org.apache.hop.datavault.hopgui.file.dimensional;

import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DmRangeBand;
import org.apache.hop.datavault.metadata.dimensional.DmRangeDimension;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a metadata-only {@link DmRangeDimension} (NumberRange band definitions). */
public class HopGuiDmRangeDimensionDialog {
  private static final Class<?> PKG = HopGuiDmRangeDimensionDialog.class;

  private final Shell parent;
  private final DmRangeDimension input;
  private final IVariables variables;

  private Shell shell;
  private Text wName;
  private Text wDescription;
  private Text wFallBackLabel;
  private TableView wBands;

  private boolean ok;
  private int margin;
  private int middle;

  public HopGuiDmRangeDimensionDialog(
      Shell parent, DmRangeDimension rangeDimension, IVariables variables) {
    this.parent = parent;
    this.input = rangeDimension;
    this.variables = variables;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "HopGuiDmRangeDimensionDialog.Title",
            Const.NVL(input.getName(), input.getTableType().name())));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();
    middle = PropsUi.getInstance().getMiddlePct();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    Label wlFallBackLabel = new Label(shell, SWT.RIGHT);
    wlFallBackLabel.setText(
        BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.FallBackLabel.Label"));
    PropsUi.setLook(wlFallBackLabel);
    wlFallBackLabel.setLayoutData(
        new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());
    wlFallBackLabel.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.FallBackLabel.ToolTip"));

    wFallBackLabel = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFallBackLabel);
    wFallBackLabel.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDescription, margin).right().result());

    Label wlBands = new Label(shell, SWT.NONE);
    wlBands.setText(BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Label"));
    PropsUi.setLook(wlBands);
    wlBands.setLayoutData(
        new FormDataBuilder().left().top(wFallBackLabel, margin).right(middle, -margin).result());

    ColumnInfo[] bandColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Column.LowerBound"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Column.UpperBound"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Column.Value"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    bandColumns[0].setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Column.LowerBound.ToolTip"));
    bandColumns[1].setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Column.UpperBound.ToolTip"));
    bandColumns[2].setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmRangeDimensionDialog.Bands.Column.Value.ToolTip"));

    int initialRows = Math.max(1, input.getBandsOrEmpty().size());
    wBands =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            bandColumns,
            initialRows,
            null,
            PropsUi.getInstance());
    wBands.setLayoutData(
        new FormDataBuilder().left().top(wlBands, margin).right().bottom(wOk, -2 * margin).result());

    getData();
    shell.layout(true, true);
    BaseTransformDialog.setSize(shell, 560, 520);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    wFallBackLabel.setText(Const.NVL(input.getFallBackLabel(), "unknown"));

    wBands.clearAll();
    for (DmRangeBand band : input.getBandsOrEmpty()) {
      if (band == null) {
        continue;
      }
      TableItem item = new TableItem(wBands.table, SWT.NONE);
      item.setText(1, Const.NVL(band.getLowerBound(), ""));
      item.setText(2, Const.NVL(band.getUpperBound(), ""));
      item.setText(3, Const.NVL(band.getLabel(), ""));
    }
    wBands.removeEmptyRows();
    wBands.setRowNums();
    wBands.optWidth(true);
  }

  private void ok() {
    if (Utils.isEmpty(wName.getText())) {
      return;
    }
    input.setName(wName.getText());
    input.setDescription(wDescription.getText());
    input.setTableName(wName.getText().toLowerCase().replace(' ', '_'));
    input.setFallBackLabel(wFallBackLabel.getText());

    input.getBands().clear();
    for (TableItem item : wBands.getNonEmptyItems()) {
      String lowerBound = item.getText(1);
      String upperBound = item.getText(2);
      String value = item.getText(3);
      if (Utils.isEmpty(value)) {
        continue;
      }
      input.getBands().add(new DmRangeBand(lowerBound, upperBound, value));
    }

    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    shell.dispose();
  }
}