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

package org.apache.hop.datavault.hopgui.resourcedefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.resourcedefinition.SourceRecordValidationService;
import org.apache.hop.datavault.resourcedefinition.ValidationReport;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
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

/** Interactive validation results: severity-sorted issues with drill-down and acknowledgement. */
public final class ResourceDefinitionValidationResultsDialog {

  private static final Class<?> PKG = ResourceDefinitionValidationResultsDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final ResourceDefinitionGroupMeta group;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private ValidationReport report;
  private Shell shell;
  private Label wlSummary;
  private TableView wIssues;
  private Button wShowAcknowledged;
  private List<ValidationIssueRow> rows = List.of();

  public ResourceDefinitionValidationResultsDialog(
      Shell parent, HopGui hopGui, ResourceDefinitionGroupMeta group, ValidationReport report) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.variables = hopGui.getVariables();
    this.metadataProvider = hopGui.getMetadataProvider();
    this.report = report;
  }

  public ResourceDefinitionValidationResultsDialog(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ValidationReport report) {
    this.parent = parent;
    this.hopGui = HopGui.getInstance();
    this.group = null;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.report = report;
  }

  public void open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle() | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.Shell.Title", report.getGroupName()));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    wlSummary = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlSummary);
    FormData fdlSummary = new FormData();
    fdlSummary.left = new FormAttachment(0, margin);
    fdlSummary.right = new FormAttachment(100, -margin);
    fdlSummary.top = new FormAttachment(0, margin);
    wlSummary.setLayoutData(fdlSummary);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Column.Severity"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Column.RecordDefinition"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Column.Field"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Column.Issue"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "ResourceDefinitionValidationResultsDialog.Column.Proposals"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wIssues =
        new TableView(
            variables,
            shell,
            SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER,
            columns,
            1,
            null,
            PropsUi.getInstance());
    FormData fdIssues = new FormData();
    fdIssues.left = new FormAttachment(0, margin);
    fdIssues.right = new FormAttachment(100, -margin);
    fdIssues.top = new FormAttachment(wlSummary, margin);
    fdIssues.bottom = new FormAttachment(100, -50);
    wIssues.setLayoutData(fdIssues);

    wShowAcknowledged = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wShowAcknowledged);
    wShowAcknowledged.setText(
        BaseMessages.getString(
            PKG, "ResourceDefinitionValidationResultsDialog.ShowAcknowledged.Label"));
    wShowAcknowledged.addListener(SWT.Selection, e -> refreshTable());

    Button wHandle = new Button(shell, SWT.PUSH);
    wHandle.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Handle.Label"));
    wHandle.addListener(SWT.Selection, e -> openSelectedIssue());

    Button wRevalidate = new Button(shell, SWT.PUSH);
    wRevalidate.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionValidationResultsDialog.Revalidate.Label"));
    wRevalidate.setEnabled(group != null);
    wRevalidate.addListener(SWT.Selection, e -> revalidate());

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wShowAcknowledged, wHandle, wRevalidate, wClose}, margin, wIssues);

    wIssues.getTable().addListener(SWT.MouseDoubleClick, e -> openSelectedIssue());

    refreshTable();
    shell.setMinimumSize(800, 520);
    shell.pack();
    shell.open();
  }

  private void refreshTable() {
    boolean showAcknowledged = wShowAcknowledged.getSelection();
    rows = buildRows(report, showAcknowledged);
    updateSummary(showAcknowledged);

    wIssues.clearAll(false);
    for (int i = 0; i < rows.size(); i++) {
      ValidationIssueRow row = rows.get(i);
      TableItem item =
          i == 0 ? wIssues.getTable().getItem(0) : new TableItem(wIssues.getTable(), SWT.NONE);
      item.setText(1, formatSeverity(row));
      item.setText(2, formatRecordDefinition(row.validation()));
      item.setText(3, Const.NVL(row.issue().fieldName(), ""));
      item.setText(4, row.issue().message());
      item.setText(
          5,
          Integer.toString(
              row.issue().proposals() != null ? row.issue().proposals().size() : 0));
      if (row.acknowledged()) {
        item.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
      }
    }
    wIssues.removeEmptyRows();
    wIssues.setRowNums();
    wIssues.optWidth(true);
  }

  private void updateSummary(boolean showAcknowledged) {
    int blocking = 0;
    int warnings = 0;
    for (ValidationIssueRow row : rows) {
      if (row.acknowledged()) {
        continue;
      }
      if (row.issue().severity() == IssueSeverity.BLOCKING) {
        blocking++;
      } else if (row.issue().severity() == IssueSeverity.WARNING) {
        warnings++;
      }
    }
    int hiddenAcknowledged = showAcknowledged ? 0 : report.getAcknowledgedIssueCount();
    wlSummary.setText(
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionValidationResultsDialog.Summary.Detailed",
            report.getTotalDefinitions(),
            rows.size(),
            blocking,
            warnings,
            hiddenAcknowledged,
            report.hasBlockingIssues()
                ? BaseMessages.getString(
                    PKG, "ResourceDefinitionValidationResultsDialog.HasBlocking")
                : BaseMessages.getString(
                    PKG, "ResourceDefinitionValidationResultsDialog.NoBlocking")));
  }

  private static List<ValidationIssueRow> buildRows(ValidationReport report, boolean showAcknowledged) {
    List<ValidationIssueRow> rows = new ArrayList<>();
    for (RecordDefinitionValidation validation : report.getRecordValidations()) {
      List<ValidationIssue> issues =
          showAcknowledged ? validation.allIssues() : validation.issues();
      if (issues == null || issues.isEmpty()) {
        continue;
      }
      for (ValidationIssue issue : issues) {
        if (issue == null) {
          continue;
        }
        boolean acknowledged =
            ValidationIssueRow.isAcknowledged(validation, issue, showAcknowledged);
        if (!showAcknowledged && acknowledged) {
          continue;
        }
        rows.add(new ValidationIssueRow(validation, issue, acknowledged));
      }
    }
    rows.sort(
        Comparator.comparingInt(ValidationIssueRow::severityRank)
            .thenComparing(row -> formatRecordDefinition(row.validation()))
            .thenComparing(row -> Const.NVL(row.issue().fieldName(), "")));
    return rows;
  }

  private static String formatRecordDefinition(RecordDefinitionValidation validation) {
    if (validation == null || validation.key() == null) {
      return "?";
    }
    return validation.key().getNamespace() + "/" + validation.key().getName();
  }

  private static String formatSeverity(ValidationIssueRow row) {
    if (row.acknowledged()) {
      return row.issue().severity() + " (ack)";
    }
    return row.issue().severity().name();
  }

  private void openSelectedIssue() {
    int index = wIssues.getTable().getSelectionIndex();
    if (index < 0 || index >= rows.size()) {
      return;
    }
    ValidationIssueRow row = rows.get(index);
    ResourceDefinitionIssueDialog dialog =
        new ResourceDefinitionIssueDialog(
            shell,
            hopGui,
            group,
            row.validation(),
            row.issue(),
            row.acknowledged(),
            this::revalidateQuietly);
    dialog.open();
  }

  private void revalidate() {
    if (group == null) {
      return;
    }
    try {
      report = SourceRecordValidationService.validateGroup(group, variables, metadataProvider);
      refreshTable();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Message"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private void revalidateQuietly() {
    if (group == null) {
      refreshTable();
      return;
    }
    try {
      report = SourceRecordValidationService.validateGroup(group, variables, metadataProvider);
      refreshTable();
    } catch (Exception ignored) {
      refreshTable();
    }
  }
}