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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class RemediationProposalSupportTest {

  @Test
  void problemAProducesTargetColumnUpdateProposalForMappedFieldChange() {
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(
            List.of(
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED,
                    "last_name",
                    "length 50 -> 75")));

    List<SourceUsage> usages =
        List.of(
            SourceUsage.builder()
                .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
                .modelName("retail-360")
                .modelElementName("sat_customer_demo")
                .mappedField("last_name")
                .build());

    List<ValidationIssue> issues = RemediationProposalSupport.buildIssues(diff, usages, null);
    assertEquals(1, issues.size());
    ValidationIssue issue = issues.getFirst();
    assertEquals(IssueKind.FIELD_TYPE_CHANGED, issue.kind());
    assertEquals(IssueSeverity.BLOCKING, issue.severity());
    assertTrue(
        issue.proposals().stream()
            .anyMatch(proposal -> proposal.type() == ProposalType.UPDATE_TARGET_COLUMN_LENGTH));
    assertTrue(
        issue.proposals().stream()
            .anyMatch(proposal -> proposal.type() == ProposalType.REFRESH_CATALOG_CONTRACT));
  }

  @Test
  void problemBProducesSatelliteExtensionProposalsForAddedFields() {
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        new RecordDefinitionSchemaDiffSupport.SchemaDiff(
            List.of(
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, "loyalty_tier", null),
                new RecordDefinitionSchemaDiffSupport.FieldChange(
                    RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, "loyalty_points", null)));

    List<SourceUsage> usages =
        List.of(
            SourceUsage.builder()
                .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
                .modelName("retail-360")
                .modelElementName("sat_customer_demo")
                .build());

    List<ValidationIssue> issues = RemediationProposalSupport.buildIssues(diff, usages, null);
    assertEquals(2, issues.size());
    assertTrue(
        issues.stream()
            .flatMap(issue -> issue.proposals().stream())
            .anyMatch(proposal -> proposal.type() == ProposalType.ADD_NEW_SATELLITE));
    assertTrue(
        issues.stream()
            .flatMap(issue -> issue.proposals().stream())
            .anyMatch(proposal -> proposal.type() == ProposalType.EXTEND_EXISTING_SATELLITE));
  }
}