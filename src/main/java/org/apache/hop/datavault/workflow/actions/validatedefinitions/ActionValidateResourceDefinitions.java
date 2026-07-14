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

package org.apache.hop.datavault.workflow.actions.validatedefinitions;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.datavault.resourcedefinition.SchemaCompareMode;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationRequest;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationResult;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationService;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationFailureSeverity;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFormatter;
import org.apache.hop.datavault.resourcedefinition.ValidationReport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * CI/CD schema gate: validates source contracts for a resource definition group, optionally against
 * a tagged catalog version, writes Markdown/HTML reports, and fails by configured severity.
 */
@Action(
    id = "VALIDATE_RESOURCE_DEFINITIONS",
    name = "i18n::ActionValidateResourceDefinitions.Name",
    description = "i18n::ActionValidateResourceDefinitions.Description",
    image = "datavault-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionValidateResourceDefinitions.Keywords",
    documentationUrl = "/workflow/actions/validatedefinitions.html")
@GuiPlugin(description = "Validate Resource Definitions action")
@Getter
@Setter
public class ActionValidateResourceDefinitions extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionValidateResourceDefinitions.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "VALIDATE_RESOURCE_DEFINITIONS_ACTION";

  /** Extension-data key for downstream actions that want the report text. */
  public static final String RESULT_ATTR_REPORT = "schemaValidationReportText";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionValidateResourceDefinitions.Group.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.TargetCatalogVersion.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.TargetCatalogVersion.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetCatalogVersion;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getCompareModeOptions",
      label = "i18n::ActionValidateResourceDefinitions.CompareMode.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.CompareMode.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String compareMode = SchemaCompareMode.LIVE_SOURCE.name();

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.BaselineVersion.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.BaselineVersion.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String baselineCatalogVersion;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.ReportOutputPath.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ReportOutputPath.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportOutputPath;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionValidateResourceDefinitions.ReportFileBaseName.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ReportFileBaseName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportFileBaseName;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getReportFormatOptions",
      label = "i18n::ActionValidateResourceDefinitions.ReportFormat.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.ReportFormat.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportFormat = SchemaValidationReportFileWriter.ReportFormat.MARKDOWN.name();

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getFailureSeverityOptions",
      label = "i18n::ActionValidateResourceDefinitions.FailureSeverity.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.FailureSeverity.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String failureSeverity = SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name();

  /**
   * Legacy checkbox kept for backward compatibility with existing workflows. When true and failure
   * severity is unset/default, behaves as {@link SchemaValidationFailureSeverity#FAIL_ON_WARNINGS}.
   */
  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionValidateResourceDefinitions.FailOnWarnings.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.FailOnWarnings.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnWarnings;

  @GuiWidgetElement(
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionValidateResourceDefinitions.IncludeImpact.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.IncludeImpact.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeImpact = true;

  public ActionValidateResourceDefinitions() {
    super();
  }

  public ActionValidateResourceDefinitions(ActionValidateResourceDefinitions meta) {
    super(meta);
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.targetCatalogVersion = meta.targetCatalogVersion;
    this.compareMode = meta.compareMode;
    this.baselineCatalogVersion = meta.baselineCatalogVersion;
    this.reportOutputPath = meta.reportOutputPath;
    this.reportFileBaseName = meta.reportFileBaseName;
    this.reportFormat = meta.reportFormat;
    this.failureSeverity = meta.failureSeverity;
    this.failOnWarnings = meta.failOnWarnings;
    this.includeImpact = meta.includeImpact;
  }

  /**
   * Hop GUI comboValuesMethod contract: {@code (ILogChannel, IHopMetadataProvider) -> List/String[]}.
   */
  public List<String> getCompareModeOptions(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        SchemaCompareMode.LIVE_SOURCE.name(),
        SchemaCompareMode.WORKING_VS_VERSION.name(),
        SchemaCompareMode.VERSION_VS_VERSION.name());
  }

  public List<String> getReportFormatOptions(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        SchemaValidationReportFileWriter.ReportFormat.MARKDOWN.name(),
        SchemaValidationReportFileWriter.ReportFormat.HTML.name(),
        SchemaValidationReportFileWriter.ReportFormat.BOTH.name());
  }

  public List<String> getFailureSeverityOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name(),
        SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.name(),
        SchemaValidationFailureSeverity.WARN_ONLY.name());
  }

  @Override
  public String getDialogClassName() {
    return ActionValidateResourceDefinitionsDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    if (Utils.isEmpty(resourceDefinitionGroup)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Error.MissingGroup"));
    }

    String groupName = resolve(resourceDefinitionGroup);
    String versionTag = resolveOptional(targetCatalogVersion);
    String baselineTag = resolveOptional(baselineCatalogVersion);
    SchemaCompareMode mode = parseCompareMode(compareMode);

    // LIVE_SOURCE: expected contract = target version, or baseline if target is empty.
    // WORKING_VS_VERSION: expected = baseline (or target if baseline empty); actual = working tree.
    if (mode == SchemaCompareMode.LIVE_SOURCE && versionTag == null && baselineTag != null) {
      versionTag = baselineTag;
    }
    if (mode == SchemaCompareMode.WORKING_VS_VERSION && baselineTag == null && versionTag != null) {
      baselineTag = versionTag;
    }

    SchemaImpactSimulationRequest request =
        SchemaImpactSimulationRequest.builder()
            .resourceDefinitionGroup(groupName)
            .catalogVersionTag(versionTag)
            .baselineVersionTag(baselineTag)
            .compareMode(mode)
            .includeImpact(includeImpact)
            .detailedDataTypeChecking(true)
            .build();

    SchemaImpactSimulationResult simulation =
        SchemaImpactSimulationService.run(request, getVariables(), getMetadataProvider());
    ValidationReport report = simulation.validationReport();

    String formatted = SchemaValidationReportFormatter.formatLog(simulation);
    if (!Utils.isEmpty(formatted)) {
      logBasic(formatted);
    }

    stashReport(formatted);

    String outputPath = resolveOptional(reportOutputPath);
    if (!Utils.isEmpty(outputPath)) {
      SchemaValidationReportFileWriter.ReportFormat format =
          parseReportFormat(reportFormat);
      List<String> written =
          SchemaValidationReportFileWriter.write(
              outputPath,
              reportFileBaseName,
              simulation,
              format,
              getVariables());
      for (String path : written) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionValidateResourceDefinitions.Log.ReportWritten", path));
      }
    }

    SchemaValidationFailureSeverity severity = resolveFailureSeverity();
    boolean failed = severity.shouldFail(report);
    if (failed && severity == SchemaValidationFailureSeverity.FAIL_ON_WARNINGS) {
      logError(
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Error.WarningsPresent"));
    }

    result.setResult(!failed);
    result.setNrErrors(failed ? Math.max(1, report != null ? report.getIssueCount() : 1) : 0);
    if (failed) {
      result.setLogText(
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Error.ValidationFailed"));
    }
    return result;
  }

  private SchemaValidationFailureSeverity resolveFailureSeverity() {
    if (failOnWarnings
        && (Utils.isEmpty(failureSeverity)
            || SchemaValidationFailureSeverity.FAIL_ON_BLOCKING
                .name()
                .equalsIgnoreCase(failureSeverity.trim()))) {
      // Legacy workflows: only failOnWarnings=Y was set.
      return SchemaValidationFailureSeverity.FAIL_ON_WARNINGS;
    }
    return SchemaValidationFailureSeverity.parse(failureSeverity);
  }

  private void stashReport(String formatted) {
    if (Utils.isEmpty(formatted)) {
      return;
    }
    getExtensionDataMap().put(RESULT_ATTR_REPORT, formatted);
    if (getParentWorkflow() != null) {
      getParentWorkflow().getExtensionDataMap().put(RESULT_ATTR_REPORT, formatted);
    }
  }

  private String resolveOptional(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    String resolved = resolve(value);
    return Utils.isEmpty(resolved) ? null : resolved.trim();
  }

  private static SchemaCompareMode parseCompareMode(String raw) {
    if (Utils.isEmpty(raw)) {
      return SchemaCompareMode.LIVE_SOURCE;
    }
    try {
      return SchemaCompareMode.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return SchemaCompareMode.LIVE_SOURCE;
    }
  }

  private static SchemaValidationReportFileWriter.ReportFormat parseReportFormat(String raw) {
    if (Utils.isEmpty(raw)) {
      return SchemaValidationReportFileWriter.ReportFormat.MARKDOWN;
    }
    try {
      return SchemaValidationReportFileWriter.ReportFormat.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return SchemaValidationReportFileWriter.ReportFormat.MARKDOWN;
    }
  }

  @Override
  public IAction clone() {
    return new ActionValidateResourceDefinitions(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
