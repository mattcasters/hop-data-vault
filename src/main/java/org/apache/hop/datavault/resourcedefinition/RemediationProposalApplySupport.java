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
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogRefreshSupport;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.datavault.hopgui.resourcedefinition.ResourceDefinitionModelNavigationSupport;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiProposal;
import org.apache.hop.datavault.ai.DvAiProposalApplier;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.hopgui.HopGui;

/** Applies remediation proposals produced by source validation. */
public final class RemediationProposalApplySupport {

  private static final Class<?> PKG = RemediationProposalApplySupport.class;

  public enum ApplyStatus {
    APPLIED,
    NEEDS_MANUAL,
    NOT_APPLICABLE
  }

  public record ApplyResult(ApplyStatus status, String message) {}

  public record ProposalContext(
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      RecordDefinition definition,
      RecordDefinitionValidation validation,
      ValidationIssue issue,
      RemediationProposal proposal,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {}

  private RemediationProposalApplySupport() {}

  public static ApplyResult apply(ProposalContext context) {
    if (context == null || context.proposal() == null || context.proposal().type() == null) {
      return new ApplyResult(
          ApplyStatus.NOT_APPLICABLE,
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.MissingProposal"));
    }
    try {
      return switch (context.proposal().type()) {
        case REFRESH_CATALOG_CONTRACT -> applyRefreshCatalogContract(context);
        case UPDATE_TARGET_COLUMN_LENGTH -> applyUpdateTargetColumnLength(context);
        case ADD_NEW_SATELLITE -> applyAddNewSatellite(context);
        case EXTEND_EXISTING_SATELLITE -> applyExtendExistingSatellite(context);
        case REVIEW_MAPPINGS -> applyReviewMappings(context);
        case BLOCK_UPDATE_UNTIL_RESOLVED ->
            new ApplyResult(
                ApplyStatus.NEEDS_MANUAL,
                BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.BlockManual"));
      };
    } catch (HopException e) {
      return new ApplyResult(ApplyStatus.NEEDS_MANUAL, e.getMessage());
    } catch (Exception e) {
      return new ApplyResult(
          ApplyStatus.NEEDS_MANUAL,
          e.getMessage() != null
              ? e.getMessage()
              : BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.Failed"));
    }
  }

  private static ApplyResult applyRefreshCatalogContract(ProposalContext context)
      throws HopException {
    RecordDefinition definition = context.definition();
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDefinition"));
    }
    RecordDefinitionCatalogRefreshSupport.RefreshPreview preview =
        RecordDefinitionCatalogRefreshSupport.preview(
            definition, context.variables(), context.metadataProvider());
    RecordDefinitionCatalogRefreshSupport.applyDiscoveredFields(
        definition,
        preview.discoveredFields(),
        new Date(),
        preview.physicalSchemaId());
    ValidationIssueSupport.pruneStaleAcknowledgements(
        definition, preview.diff(), null);
    RecordDefinitionRegistry.getInstance()
        .update(
            context.validation().catalogConnection(),
            definition,
            context.variables(),
            context.metadataProvider());
    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.RefreshApplied"));
  }

  private static ApplyResult applyUpdateTargetColumnLength(ProposalContext context)
      throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    if (Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingField"));
    }
    SourceField discoveredField = resolveDiscoveredField(context, fieldName);
    if (discoveredField == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Error.DiscoveredFieldMissing", fieldName));
    }

    int updated = 0;
    for (SourceUsage usage : relevantUsages(context, fieldName)) {
      if (!SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())) {
        continue;
      }
      DataVaultModel model =
          ResourceDefinitionGroupResolver.loadDataVaultModel(
              usage.modelFilename(), context.variables(), context.metadataProvider());
      IDvTable table = model.findTable(usage.modelElementName());
      if (!(table instanceof DvSatellite satellite)) {
        continue;
      }
      SatelliteAttribute attribute = findAttribute(satellite, fieldName);
      if (attribute == null) {
        continue;
      }
      if (!Utils.isEmpty(discoveredField.getLength())) {
        attribute.setLength(discoveredField.getLength());
      }
      if (!Utils.isEmpty(discoveredField.getPrecision())) {
        attribute.setPrecision(discoveredField.getPrecision());
      }
      if (!Utils.isEmpty(discoveredField.getSourceDataType())) {
        attribute.setDataType(discoveredField.getSourceDataType());
      }
      model.setChanged(true);
      ResourceDefinitionModelPersistenceSupport.saveDataVaultModel(
          model, context.variables(), context.metadataProvider());
      updated++;
    }

    if (updated == 0) {
      return new ApplyResult(
          ApplyStatus.NEEDS_MANUAL,
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.UpdateTargetManual"));
    }
    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(
            PKG, "RemediationProposalApplySupport.Result.UpdateTargetApplied", updated));
  }

  private static ApplyResult applyAddNewSatellite(ProposalContext context) throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    SourceUsage usage = firstDataVaultUsage(context);
    if (usage == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDvUsage"));
    }

    DataVaultModel model =
        ResourceDefinitionGroupResolver.loadDataVaultModel(
            usage.modelFilename(), context.variables(), context.metadataProvider());
    String hubName = resolveHubForUsage(model, usage);
    if (Utils.isEmpty(hubName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingHub"));
    }

    String recordSource = resolveRecordSource(context);
    String satelliteName = suggestSatelliteName(hubName, fieldName);
    if (model.findTable(satelliteName) != null) {
      satelliteName = satelliteName + "_EXTRA";
    }

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_SATELLITE);
    proposal.setParameters(
        java.util.Map.of(
            "name",
            satelliteName,
            "hubName",
            hubName,
            "recordSource",
            recordSource,
            "attributeNames",
            Utils.isEmpty(fieldName) ? "" : fieldName));

    DvAiProposalApplier.apply(model, List.of(proposal), context.metadataProvider(), context.variables());
    ResourceDefinitionModelPersistenceSupport.saveDataVaultModel(
        model, context.variables(), context.metadataProvider());

    if (context.hopGui() != null) {
      ResourceDefinitionModelNavigationSupport.openDataVaultUsage(
          context.hopGui(), usage, satelliteName, context.variables());
    }

    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(
            PKG, "RemediationProposalApplySupport.Result.AddSatelliteApplied", satelliteName));
  }

  private static ApplyResult applyExtendExistingSatellite(ProposalContext context)
      throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    if (Utils.isEmpty(fieldName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingField"));
    }
    SourceUsage usage = firstDataVaultUsage(context);
    if (usage == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingDvUsage"));
    }

    DataVaultModel model =
        ResourceDefinitionGroupResolver.loadDataVaultModel(
            usage.modelFilename(), context.variables(), context.metadataProvider());
    IDvTable table = model.findTable(usage.modelElementName());
    if (!(table instanceof DvSatellite satellite)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RemediationProposalApplySupport.Error.NotSatellite",
              usage.modelElementName()));
    }
    if (findAttribute(satellite, fieldName) != null) {
      return new ApplyResult(
          ApplyStatus.APPLIED,
          BaseMessages.getString(
              PKG, "RemediationProposalApplySupport.Result.ExtendAlreadyPresent", fieldName));
    }

    SourceField discoveredField = resolveDiscoveredField(context, fieldName);
    SatelliteAttribute attribute = new SatelliteAttribute(fieldName);
    if (discoveredField != null) {
      if (!Utils.isEmpty(discoveredField.getSourceDataType())) {
        attribute.setDataType(discoveredField.getSourceDataType());
      }
      if (!Utils.isEmpty(discoveredField.getLength())) {
        attribute.setLength(discoveredField.getLength());
      }
      if (!Utils.isEmpty(discoveredField.getPrecision())) {
        attribute.setPrecision(discoveredField.getPrecision());
      }
    }
    satellite.getAttributes().add(attribute);
    model.setChanged(true);
    ResourceDefinitionModelPersistenceSupport.saveDataVaultModel(
        model, context.variables(), context.metadataProvider());

    if (context.hopGui() != null) {
      ResourceDefinitionModelNavigationSupport.openDataVaultUsage(
          context.hopGui(), usage, usage.modelElementName(), context.variables());
    }

    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(
            PKG,
            "RemediationProposalApplySupport.Result.ExtendApplied",
            fieldName,
            usage.modelElementName()));
  }

  private static ApplyResult applyReviewMappings(ProposalContext context) throws HopException {
    String fieldName = context.issue() != null ? context.issue().fieldName() : null;
    List<SourceUsage> usages = relevantUsages(context, fieldName);
    if (usages.isEmpty() && context.validation() != null) {
      usages = context.validation().usages();
    }
    if (usages.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Error.MissingUsages"));
    }
    if (context.hopGui() == null) {
      return new ApplyResult(
          ApplyStatus.NEEDS_MANUAL,
          BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.ReviewManual"));
    }

    Set<String> opened = new LinkedHashSet<>();
    for (SourceUsage usage : usages) {
      String key = usage.modelType() + ":" + usage.modelFilename() + ":" + usage.modelElementName();
      if (!opened.add(key)) {
        continue;
      }
      ResourceDefinitionModelNavigationSupport.openUsage(
          context.hopGui(), usage, context.variables());
    }
    return new ApplyResult(
        ApplyStatus.APPLIED,
        BaseMessages.getString(PKG, "RemediationProposalApplySupport.Result.ReviewOpened"));
  }

  private static SourceField resolveDiscoveredField(ProposalContext context, String fieldName)
      throws HopException {
    RecordDefinition definition = context.definition();
    if (definition == null) {
      return null;
    }
    RecordDefinitionCatalogRefreshSupport.RefreshPreview preview =
        RecordDefinitionCatalogRefreshSupport.preview(
            definition, context.variables(), context.metadataProvider());
    for (SourceField field : preview.discoveredFields()) {
      if (field != null && fieldName.equals(field.getName())) {
        return field;
      }
    }
    return null;
  }

  private static List<SourceUsage> relevantUsages(ProposalContext context, String fieldName) {
    if (context.validation() == null || context.validation().usages() == null) {
      return List.of();
    }
    if (Utils.isEmpty(fieldName)) {
      return context.validation().usages();
    }
    List<SourceUsage> usages = new ArrayList<>();
    for (SourceUsage usage : context.validation().usages()) {
      if (usage.mappedFields().contains(fieldName)) {
        usages.add(usage);
      }
    }
    return usages;
  }

  private static SourceUsage firstDataVaultUsage(ProposalContext context) {
    if (context.validation() == null) {
      return null;
    }
    for (SourceUsage usage : context.validation().usages()) {
      if (SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())) {
        return usage;
      }
    }
    return null;
  }

  private static String resolveHubForUsage(DataVaultModel model, SourceUsage usage) {
    if (usage == null || model == null) {
      return null;
    }
    IDvTable table = model.findTable(usage.modelElementName());
    if (table instanceof DvSatellite satellite && !Utils.isEmpty(satellite.getHubName())) {
      return satellite.getHubName();
    }
    if (table instanceof DvHub hub) {
      return hub.getName();
    }
    for (IDvTable candidate : model.getTables()) {
      if (candidate instanceof DvHub hub) {
        return hub.getName();
      }
    }
    return null;
  }

  private static String resolveRecordSource(ProposalContext context) {
    if (context.validation() != null
        && context.validation().key() != null
        && !Utils.isEmpty(context.validation().key().getName())) {
      return context.validation().key().getName();
    }
    RecordDefinition definition = context.definition();
    if (definition != null
        && definition.getDvSource() != null
        && !Utils.isEmpty(definition.getDvSource().getGroup())) {
      return definition.getDvSource().getGroup();
    }
    return "source";
  }

  private static String suggestSatelliteName(String hubName, String fieldName) {
    String hubToken = Utils.isEmpty(hubName) ? "SAT" : hubName;
    if (hubToken.startsWith("HUB_")) {
      hubToken = hubToken.substring(4);
    }
    String fieldToken = Utils.isEmpty(fieldName) ? "EXTRA" : fieldName.toUpperCase().replace(' ', '_');
    return "SAT_" + hubToken + "_" + fieldToken;
  }

  private static SatelliteAttribute findAttribute(DvSatellite satellite, String fieldName) {
    if (satellite == null || satellite.getAttributes() == null) {
      return null;
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute != null && fieldName.equals(attribute.getName())) {
        return attribute;
      }
    }
    return null;
  }
}