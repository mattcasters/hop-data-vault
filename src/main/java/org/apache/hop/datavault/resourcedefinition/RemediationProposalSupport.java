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

package org.apache.hop.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;

/** Builds report-only remediation proposals for source schema drift. */
public final class RemediationProposalSupport {

  private static final Class<?> PKG = RemediationProposalSupport.class;

  private RemediationProposalSupport() {}

  public static List<ValidationIssue> buildIssues(
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff,
      List<SourceUsage> usages,
      String sourceUnavailableMessage) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (!Utils.isEmpty(sourceUnavailableMessage)) {
      issues.add(
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(IssueKind.SOURCE_UNAVAILABLE, null, null),
              IssueKind.SOURCE_UNAVAILABLE,
              IssueSeverity.BLOCKING,
              null,
              sourceUnavailableMessage,
              List.of(
                  new RemediationProposal(
                      ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
                      BaseMessages.getString(
                          PKG, "RemediationProposalSupport.BlockUpdateUntilResolved.Summary"),
                      sourceUnavailableMessage))));
      return issues;
    }

    if (diff == null || !diff.hasChanges()) {
      return issues;
    }

    for (RecordDefinitionSchemaDiffSupport.FieldChange change : diff.changes()) {
      if (change == null) {
        continue;
      }
      issues.add(buildIssueForChange(change, usages));
    }
    return issues;
  }

  private static ValidationIssue buildIssueForChange(
      RecordDefinitionSchemaDiffSupport.FieldChange change, List<SourceUsage> usages) {
    String fieldName = change.fieldName();
    boolean mapped = isFieldMapped(fieldName, usages);
    return switch (change.kind()) {
      case ADDED ->
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(IssueKind.FIELD_ADDED, change),
              IssueKind.FIELD_ADDED,
              IssueSeverity.WARNING,
              fieldName,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.Issue.FieldAdded", fieldName),
              proposalsForAddedField(fieldName, usages));
      case REMOVED -> {
        IssueKind kind = mapped ? IssueKind.MAPPING_BROKEN : IssueKind.FIELD_REMOVED;
        yield new ValidationIssue(
            ValidationIssueSupport.buildIssueId(kind, change),
            kind,
            mapped ? IssueSeverity.BLOCKING : IssueSeverity.WARNING,
            fieldName,
            mapped
                ? BaseMessages.getString(
                    PKG,
                    "RemediationProposalSupport.Issue.FieldRemovedMapped",
                    fieldName)
                : BaseMessages.getString(
                    PKG, "RemediationProposalSupport.Issue.FieldRemoved", fieldName),
            proposalsForRemovedField(fieldName, mapped, usages));
      }
      case CHANGED ->
          new ValidationIssue(
              ValidationIssueSupport.buildIssueId(IssueKind.FIELD_TYPE_CHANGED, change),
              IssueKind.FIELD_TYPE_CHANGED,
              mapped ? IssueSeverity.BLOCKING : IssueSeverity.WARNING,
              fieldName,
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.Issue.FieldChanged",
                  fieldName,
                  Utils.isEmpty(change.details()) ? "" : change.details()),
              proposalsForChangedField(fieldName, change.details(), mapped, usages));
    };
  }

  private static List<RemediationProposal> proposalsForAddedField(
      String fieldName, List<SourceUsage> usages) {
    List<RemediationProposal> proposals = new ArrayList<>();
    proposals.add(
        new RemediationProposal(
            ProposalType.REFRESH_CATALOG_CONTRACT,
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.RefreshCatalogContract.Summary"),
            BaseMessages.getString(
                PKG,
                "RemediationProposalSupport.RefreshCatalogContract.AddedDetails",
                fieldName)));

    Set<String> hubElements = hubElementsForUsages(usages);
    if (!hubElements.isEmpty()) {
      proposals.add(
          new RemediationProposal(
              ProposalType.ADD_NEW_SATELLITE,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.AddNewSatellite.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.AddNewSatellite.Details",
                  fieldName,
                  String.join(", ", hubElements))));
    }

    Set<String> satelliteElements = satelliteElementsForUsages(usages);
    if (!satelliteElements.isEmpty()) {
      proposals.add(
          new RemediationProposal(
              ProposalType.EXTEND_EXISTING_SATELLITE,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.ExtendSatellite.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.ExtendSatellite.Details",
                  fieldName,
                  String.join(", ", satelliteElements))));
    }
    return proposals;
  }

  private static List<RemediationProposal> proposalsForRemovedField(
      String fieldName, boolean mapped, List<SourceUsage> usages) {
    List<RemediationProposal> proposals = new ArrayList<>();
    proposals.add(
        new RemediationProposal(
            ProposalType.REFRESH_CATALOG_CONTRACT,
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.RefreshCatalogContract.Summary"),
            BaseMessages.getString(
                PKG,
                "RemediationProposalSupport.RefreshCatalogContract.RemovedDetails",
                fieldName)));
    if (mapped) {
      proposals.add(
          new RemediationProposal(
              ProposalType.REVIEW_MAPPINGS,
              BaseMessages.getString(PKG, "RemediationProposalSupport.ReviewMappings.Summary"),
              formatUsageDetails(fieldName, usages)));
      proposals.add(
          new RemediationProposal(
              ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.BlockUpdateUntilResolved.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.BlockUpdateUntilResolved.RemovedField",
                  fieldName)));
    }
    return proposals;
  }

  private static List<RemediationProposal> proposalsForChangedField(
      String fieldName, String details, boolean mapped, List<SourceUsage> usages) {
    List<RemediationProposal> proposals = new ArrayList<>();
    proposals.add(
        new RemediationProposal(
            ProposalType.REFRESH_CATALOG_CONTRACT,
            BaseMessages.getString(
                PKG, "RemediationProposalSupport.RefreshCatalogContract.Summary"),
            BaseMessages.getString(
                PKG,
                "RemediationProposalSupport.RefreshCatalogContract.ChangedDetails",
                fieldName,
                Utils.isEmpty(details) ? "" : details)));
    if (mapped) {
      proposals.add(
          new RemediationProposal(
              ProposalType.UPDATE_TARGET_COLUMN_LENGTH,
              BaseMessages.getString(
                  PKG, "RemediationProposalSupport.UpdateTargetColumn.Summary"),
              BaseMessages.getString(
                  PKG,
                  "RemediationProposalSupport.UpdateTargetColumn.Details",
                  fieldName,
                  Utils.isEmpty(details) ? "" : details,
                  formatUsageDetails(fieldName, usages))));
      proposals.add(
          new RemediationProposal(
              ProposalType.REVIEW_MAPPINGS,
              BaseMessages.getString(PKG, "RemediationProposalSupport.ReviewMappings.Summary"),
              formatUsageDetails(fieldName, usages)));
    }
    return proposals;
  }

  private static boolean isFieldMapped(String fieldName, List<SourceUsage> usages) {
    if (Utils.isEmpty(fieldName) || usages == null) {
      return false;
    }
    for (SourceUsage usage : usages) {
      if (usage.mappedFields().contains(fieldName)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> hubElementsForUsages(List<SourceUsage> usages) {
    return dataVaultElementsForUsages(usages);
  }

  private static Set<String> satelliteElementsForUsages(List<SourceUsage> usages) {
    return dataVaultElementsForUsages(usages);
  }

  private static Set<String> dataVaultElementsForUsages(List<SourceUsage> usages) {
    Set<String> elements = new LinkedHashSet<>();
    if (usages == null) {
      return elements;
    }
    for (SourceUsage usage : usages) {
      if (SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
          && !Utils.isEmpty(usage.modelElementName())) {
        elements.add(usage.modelElementName());
      }
    }
    return elements;
  }

  private static String formatUsageDetails(String fieldName, List<SourceUsage> usages) {
    if (usages == null || usages.isEmpty()) {
      return BaseMessages.getString(PKG, "RemediationProposalSupport.NoUsages");
    }
    StringBuilder builder = new StringBuilder();
    for (SourceUsage usage : usages) {
      if (!usage.mappedFields().contains(fieldName)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(
          BaseMessages.getString(
              PKG,
              "RemediationProposalSupport.UsageLine",
              usage.modelType(),
              usage.modelName(),
              usage.modelElementName()));
    }
    if (builder.length() == 0) {
      return BaseMessages.getString(PKG, "RemediationProposalSupport.NoMappedUsages");
    }
    return builder.toString();
  }
}