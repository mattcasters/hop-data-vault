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

package org.apache.hop.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Configuration embedded in a {@link BusinessVaultModel}. */
@Getter
@Setter
@GuiPlugin
public class BusinessVaultConfiguration {

  public static final String GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID = "BUSINESS_VAULT_CONFIGURATION_GENERAL_TAB";

  public static final String DEFAULT_SCD2_PIPELINE_NAME_PREFIX = "bv-scd2-";
  public static final String DEFAULT_PIT_PIPELINE_NAME_PREFIX = "bv-pit-";
  public static final String DEFAULT_BUSINESS_TABLE_PIPELINE_NAME_PREFIX = "bv-biz-";
  public static final String DEFAULT_OPEN_START_SENTINEL = "1900-01-01 00:00:00";
  public static final String DEFAULT_OPEN_END_SENTINEL = "9999-12-31 23:59:59";
  public static final String DEFAULT_VALID_FROM_FIELD = "valid_from";
  public static final String DEFAULT_VALID_TO_FIELD = "valid_to";
  public static final String DEFAULT_TARGET_TABLE_BATCH_SIZE = "1000";
  public static final String DEFAULT_TARGET_TABLE_PARALLEL_COPIES = "1";

  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::BusinessVaultConfiguration.TargetDatabase.Label",
      toolTip = "i18n::BusinessVaultConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty(key = "targetDatabase")
  private String targetDatabase;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FOLDER,
      label = "i18n::BusinessVaultConfiguration.GeneratedPipelineFolder.Label",
      toolTip = "i18n::BusinessVaultConfiguration.GeneratedPipelineFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty private String generatedPipelineFolder;

  @HopMetadataProperty private String scd2PipelineNamePrefix = DEFAULT_SCD2_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty private String pitPipelineNamePrefix = DEFAULT_PIT_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty private String businessTablePipelineNamePrefix =
      DEFAULT_BUSINESS_TABLE_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty private String openStartSentinel = DEFAULT_OPEN_START_SENTINEL;

  @HopMetadataProperty private String openEndSentinel = DEFAULT_OPEN_END_SENTINEL;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.FOLDER,
      label = "i18n::BusinessVaultConfiguration.Scd2Defaults.Label",
      toolTip = "i18n::BusinessVaultConfiguration.Scd2Defaults.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String scd2DefaultsFolder;

  @GuiWidgetElement(
      order = "0160",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.FunctionalTimestampField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.FunctionalTimestampField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String functionalTimestampField;

  @GuiWidgetElement(
      order = "0170",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.LoadDateFieldFallback.Label",
      toolTip = "i18n::BusinessVaultConfiguration.LoadDateFieldFallback.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String loadDateFieldFallback = "LOAD_DATE";

  @GuiWidgetElement(
      order = "0180",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.ValidFromField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.ValidFromField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String validFromField = DEFAULT_VALID_FROM_FIELD;

  @GuiWidgetElement(
      order = "0190",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.ValidToField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.ValidToField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String validToField = DEFAULT_VALID_TO_FIELD;

  @HopMetadataProperty private String targetTableBatchSize = DEFAULT_TARGET_TABLE_BATCH_SIZE;

  @HopMetadataProperty private String targetTableParallelCopies = DEFAULT_TARGET_TABLE_PARALLEL_COPIES;

  public String resolveTargetTableCommitSize(IVariables variables) {
    String size = targetTableBatchSize;
    if (Utils.isEmpty(size)) {
      return DEFAULT_TARGET_TABLE_BATCH_SIZE;
    }
    if (variables != null) {
      size = variables.resolve(size);
    }
    return size;
  }

  public String resolveTargetTableParallelCopies(IVariables variables) {
    String copies = targetTableParallelCopies;
    if (Utils.isEmpty(copies)) {
      return DEFAULT_TARGET_TABLE_PARALLEL_COPIES;
    }
    if (variables != null) {
      copies = variables.resolve(copies);
    }
    return copies;
  }

  public String buildScd2PipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(
        variables, scd2PipelineNamePrefix, DEFAULT_SCD2_PIPELINE_NAME_PREFIX, targetTableName, sourceName);
  }

  private static String buildPipelineName(
      IVariables variables,
      String configuredPrefix,
      String defaultPrefix,
      String targetTableName,
      String sourceName) {
    String prefix = Utils.isEmpty(configuredPrefix) ? defaultPrefix : configuredPrefix;
    if (variables != null) {
      prefix = variables.resolve(prefix);
    }
    StringBuilder name = new StringBuilder(prefix);
    if (!Utils.isEmpty(targetTableName)) {
      name.append(targetTableName);
    }
    if (!Utils.isEmpty(sourceName)) {
      name.append("-").append(sourceName);
    }
    return name.toString();
  }
}