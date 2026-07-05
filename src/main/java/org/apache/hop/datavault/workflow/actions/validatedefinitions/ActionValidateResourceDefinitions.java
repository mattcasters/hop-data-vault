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

package org.apache.hop.datavault.workflow.actions.validatedefinitions;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.resourcedefinition.SourceRecordValidationService;
import org.apache.hop.datavault.resourcedefinition.ValidationReport;
import org.apache.hop.datavault.resourcedefinition.ValidationReportFormatter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/** Validates source record definitions for a resource definition group before DV/BV/DM updates. */
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
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionValidateResourceDefinitions.FailOnWarnings.Label",
      toolTip = "i18n::ActionValidateResourceDefinitions.FailOnWarnings.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnWarnings;

  public ActionValidateResourceDefinitions() {
    super();
  }

  public ActionValidateResourceDefinitions(ActionValidateResourceDefinitions meta) {
    super(meta);
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.failOnWarnings = meta.failOnWarnings;
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

    ValidationReport report =
        SourceRecordValidationService.validateGroupByName(
            resourceDefinitionGroup, getVariables(), getMetadataProvider());

    String formatted = ValidationReportFormatter.format(report);
    if (!Utils.isEmpty(formatted)) {
      logBasic(formatted);
    }

    boolean failed = report.hasBlockingIssues();
    if (!failed && failOnWarnings && report.getIssueCount() > 0) {
      failed = true;
      logError(
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Error.WarningsPresent"));
    }

    result.setResult(!failed);
    result.setNrErrors(failed ? Math.max(1, report.getIssueCount()) : 0);
    if (failed) {
      result.setLogText(
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Error.ValidationFailed"));
    }
    return result;
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
