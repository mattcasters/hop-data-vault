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

import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.resourcedefinition.RemediationProposalApplySupport;
import org.apache.hop.datavault.resourcedefinition.RemediationProposalApplySupport.ApplyResult;
import org.apache.hop.datavault.resourcedefinition.RemediationProposalApplySupport.ProposalContext;
import java.util.List;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.ValidationAcknowledgementSupport;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
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
import org.eclipse.swt.widgets.Text;

/** Shows one validation issue, its usages, proposals, and acknowledgement actions. */
public final class ResourceDefinitionIssueDialog {

  private static final Class<?> PKG = ResourceDefinitionIssueDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final ResourceDefinitionGroupMeta group;
  private final RecordDefinitionValidation validation;
  private final ValidationIssue issue;
  private final boolean acknowledged;
  private final Runnable onChanged;

  private RecordDefinition definition;

  public ResourceDefinitionIssueDialog(
      Shell parent,
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      RecordDefinitionValidation validation,
      ValidationIssue issue,
      boolean acknowledged,
      Runnable onChanged) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.validation = validation;
    this.issue = issue;
    this.acknowledged = acknowledged;
    this.onChanged = onChanged;
  }

  public void open() {
    try {
      definition = loadDefinition();
    } catch (HopException e) {
      new ErrorDialog(
          parent,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.LoadDefinition"),
          e);
      return;
    }

    Shell shell = new Shell(parent, BaseDialog.getDefaultDialogStyle() | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "ResourceDefinitionIssueDialog.Shell.Title",
            validation.key() != null ? validation.key().getName() : "?"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Label wlHeader = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlHeader);
    wlHeader.setText(buildHeaderText());
    FormData fdlHeader = new FormData();
    fdlHeader.left = new FormAttachment(0, margin);
    fdlHeader.right = new FormAttachment(100, -margin);
    fdlHeader.top = new FormAttachment(0, margin);
    wlHeader.setLayoutData(fdlHeader);

    Label wlUsages = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlUsages);
    wlUsages.setText(BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Usages.Label"));
    FormData fdlUsages = new FormData();
    fdlUsages.left = new FormAttachment(0, margin);
    fdlUsages.top = new FormAttachment(wlHeader, margin);
    wlUsages.setLayoutData(fdlUsages);

    Text wUsages = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wUsages);
    wUsages.setText(buildUsagesText());
    FormData fdUsages = new FormData();
    fdUsages.left = new FormAttachment(0, margin);
    fdUsages.right = new FormAttachment(100, -margin);
    fdUsages.top = new FormAttachment(wlUsages, margin);
    fdUsages.height = 80;
    wUsages.setLayoutData(fdUsages);

    Label wlProposals = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlProposals);
    wlProposals.setText(BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Proposals.Label"));
    FormData fdlProposals = new FormData();
    fdlProposals.left = new FormAttachment(0, margin);
    fdlProposals.top = new FormAttachment(wUsages, margin);
    wlProposals.setLayoutData(fdlProposals);

    ColumnInfo[] proposalColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Proposals.Summary.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Proposals.Details.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    TableView wProposals =
        new TableView(
            hopGui.getVariables(),
            shell,
            SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER,
            proposalColumns,
            1,
            null,
            PropsUi.getInstance());
    populateProposals(wProposals);
    FormData fdProposals = new FormData();
    fdProposals.left = new FormAttachment(0, margin);
    fdProposals.right = new FormAttachment(100, -margin);
    fdProposals.top = new FormAttachment(wlProposals, margin);
    fdProposals.bottom = new FormAttachment(100, -90);
    wProposals.setLayoutData(fdProposals);

    Button wApplyProposal = new Button(shell, SWT.PUSH);
    wApplyProposal.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.ApplyProposal.Label"));
    wApplyProposal.addListener(
        SWT.Selection,
        e -> applySelectedProposal(shell, wProposals));

    Button wAcknowledge = new Button(shell, SWT.PUSH);
    wAcknowledge.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Acknowledge.Label"));
    wAcknowledge.setEnabled(!acknowledged);
    wAcknowledge.addListener(SWT.Selection, e -> acknowledgeIssue(shell));

    Button wRevoke = new Button(shell, SWT.PUSH);
    wRevoke.setText(BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Revoke.Label"));
    wRevoke.setEnabled(acknowledged);
    wRevoke.addListener(SWT.Selection, e -> revokeAcknowledgement(shell));

    Button wOpenRecord = new Button(shell, SWT.PUSH);
    wOpenRecord.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.OpenRecord.Label"));
    wOpenRecord.addListener(SWT.Selection, e -> openRecordDefinition());

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());

    BaseTransformDialog.positionBottomButtons(
        shell,
        new Button[] {wApplyProposal, wAcknowledge, wRevoke, wOpenRecord, wClose},
        margin,
        wProposals);

    shell.setMinimumSize(720, 520);
    shell.pack();
    shell.open();
  }

  private String buildHeaderText() {
    String keyLabel =
        validation.key() != null
            ? validation.key().getNamespace() + "/" + validation.key().getName()
            : "?";
    return BaseMessages.getString(
        PKG,
        "ResourceDefinitionIssueDialog.Header",
        issue.severity(),
        issue.kind(),
        Const.NVL(issue.fieldName(), ""),
        issue.message(),
        keyLabel,
        Const.NVL(validation.sourceType(), ""),
        acknowledged
            ? BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Acknowledged.Yes")
            : BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Acknowledged.No"));
  }

  private String buildUsagesText() {
    String fieldName = issue.fieldName();
    StringBuilder builder = new StringBuilder();
    for (SourceUsage usage : validation.usages()) {
      if (!Utils.isEmpty(fieldName) && !usage.mappedFields().contains(fieldName)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(
          BaseMessages.getString(
              PKG,
              "ResourceDefinitionIssueDialog.Usage.Line",
              usage.modelType(),
              usage.modelName(),
              usage.modelElementName()));
    }
    if (builder.length() == 0) {
      return BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.NoUsages");
    }
    return builder.toString();
  }

  private void populateProposals(TableView table) {
    table.clearAll(false);
    List<RemediationProposal> proposals =
        issue.proposals() != null ? issue.proposals() : List.of();
    for (int i = 0; i < proposals.size(); i++) {
      RemediationProposal proposal = proposals.get(i);
      if (proposal == null) {
        continue;
      }
      TableItem item =
          i == 0 ? table.getTable().getItem(0) : new TableItem(table.getTable(), SWT.NONE);
      item.setText(1, proposal.summary());
      item.setText(2, Const.NVL(proposal.details(), ""));
    }
    table.removeEmptyRows();
    table.setRowNums();
    table.optWidth(true);
  }

  private void applySelectedProposal(Shell shell, TableView proposalsTable) {
    int selectionIndex = proposalsTable.getTable().getSelectionIndex();
    if (selectionIndex < 0 || issue.proposals() == null || selectionIndex >= issue.proposals().size()) {
      return;
    }
    RemediationProposal proposal = issue.proposals().get(selectionIndex);
    if (proposal.type() == org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED) {
      return;
    }
    try {
      reloadDefinition();
      ApplyResult result =
          RemediationProposalApplySupport.apply(
              new ProposalContext(
                  hopGui,
                  group,
                  definition,
                  validation,
                  issue,
                  proposal,
                  hopGui.getVariables(),
                  hopGui.getMetadataProvider()));
      if (onChanged != null) {
        onChanged.run();
      }
      if (result.status() == RemediationProposalApplySupport.ApplyStatus.APPLIED) {
        shell.dispose();
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.ApplyProposal"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private void acknowledgeIssue(Shell shell) {
    AcknowledgeValidationIssueDialog dialog =
        new AcknowledgeValidationIssueDialog(shell, issue.message());
    if (!dialog.openConfirmed()) {
      return;
    }
    try {
      reloadDefinition();
      ValidationAcknowledgementSupport.acknowledge(
          validation.catalogConnection(),
          definition,
          issue.issueId(),
          dialog.getComment(),
          ValidationAcknowledgementSupport.resolveAcknowledgedBy(),
          hopGui.getVariables(),
          hopGui.getMetadataProvider());
      notifyChanged();
      shell.dispose();
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Acknowledge"),
          e);
    }
  }

  private void revokeAcknowledgement(Shell shell) {
    try {
      reloadDefinition();
      ValidationAcknowledgementSupport.revoke(
          validation.catalogConnection(),
          definition,
          issue.issueId(),
          hopGui.getVariables(),
          hopGui.getMetadataProvider());
      notifyChanged();
      shell.dispose();
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Revoke"),
          e);
    }
  }

  private void openRecordDefinition() {
    try {
      DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
      if (perspective != null && validation.key() != null) {
        perspective.selectRecordDefinition(validation.catalogConnection(), validation.key());
      }
    } catch (HopException e) {
      new ErrorDialog(
          parent,
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.Title"),
          BaseMessages.getString(PKG, "ResourceDefinitionIssueDialog.Error.OpenRecord"),
          e);
    }
  }

  private RecordDefinition loadDefinition() throws HopException {
    if (validation.key() == null || Utils.isEmpty(validation.catalogConnection())) {
      return null;
    }
    return RecordDefinitionRegistry.getInstance()
        .read(
            validation.catalogConnection(),
            validation.key(),
            hopGui.getVariables(),
            hopGui.getMetadataProvider());
  }

  private void reloadDefinition() throws HopException {
    definition = loadDefinition();
  }

  private void notifyChanged() {
    if (onChanged != null) {
      onChanged.run();
    }
  }
}