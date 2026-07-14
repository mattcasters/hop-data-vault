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

package org.apache.hop.datavault.hopgui.resourcedefinition;

import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.resourcedefinition.SchemaCompareMode;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationRequest;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationResult;
import org.apache.hop.datavault.resourcedefinition.SchemaImpactSimulationService;
import org.apache.hop.datavault.resourcedefinition.ValidationReport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.widgets.Shell;

/** GUI entry points for resource definition group validation with impact enrichment. */
public final class ResourceDefinitionValidationGuiSupport {

  private static final Class<?> PKG = ResourceDefinitionValidationGuiSupport.class;

  private ResourceDefinitionValidationGuiSupport() {}

  public static ValidationReport validateAndShowResults(
      HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return null;
    }
    Shell shell = hopGui.getShell();
    try {
      SchemaImpactSimulationResult simulation =
          runLiveSimulation(group, hopGui.getVariables(), hopGui.getMetadataProvider());
      ValidationReport report = simulation.validationReport();
      if (shell != null && !shell.isDisposed()) {
        ResourceDefinitionValidationResultsDialog dialog =
            new ResourceDefinitionValidationResultsDialog(shell, hopGui, group, report, simulation);
        dialog.open();
      }
      return report;
    } catch (Exception e) {
      if (shell != null && !shell.isDisposed()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Title"),
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Message"),
            e instanceof HopException ? e : new HopException(e));
      }
      return null;
    }
  }

  public static ValidationReport validateAndShowResults(
      Shell shell,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String groupName) {
    try {
      SchemaImpactSimulationRequest request =
          SchemaImpactSimulationRequest.builder()
              .resourceDefinitionGroup(groupName)
              .compareMode(SchemaCompareMode.LIVE_SOURCE)
              .includeImpact(true)
              .build();
      SchemaImpactSimulationResult simulation =
          SchemaImpactSimulationService.run(request, variables, metadataProvider);
      ValidationReport report = simulation.validationReport();
      if (shell != null && !shell.isDisposed()) {
        ResourceDefinitionValidationResultsDialog dialog =
            new ResourceDefinitionValidationResultsDialog(
                shell, variables, metadataProvider, report, simulation);
        dialog.open();
      }
      return report;
    } catch (Exception e) {
      if (shell != null && !shell.isDisposed()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Title"),
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Message"),
            e instanceof HopException ? e : new HopException(e));
      }
      return null;
    }
  }

  public static SchemaImpactSimulationResult runLiveSimulation(
      ResourceDefinitionGroupMeta group, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    SchemaImpactSimulationRequest request =
        SchemaImpactSimulationRequest.builder()
            .resourceDefinitionGroup(group != null ? group.getName() : null)
            .compareMode(SchemaCompareMode.LIVE_SOURCE)
            .includeImpact(true)
            .detailedDataTypeChecking(group == null || group.isDetailedDataTypeChecking())
            .build();
    return SchemaImpactSimulationService.run(request, group, variables, metadataProvider);
  }
}
