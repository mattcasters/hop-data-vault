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

package org.apache.hop.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/** Warehouse configuration for a dimensional model. */
@GuiPlugin
@Getter
@Setter
public class DimensionalConfiguration extends HopMetadataBase implements IHopMetadata {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DIMENSIONAL_MODEL_DIALOG";
  public static final String GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID = "DIMENSIONAL_CONFIGURATION_COLUMNS_TAB";

  public static final String DEFAULT_DIM_KEY_FIELD = "dim_key";
  public static final String DEFAULT_VERSION_FIELD = "version";
  public static final String DEFAULT_DATE_FROM_FIELD = "date_from";
  public static final String DEFAULT_DATE_TO_FIELD = "date_to";
  public static final String DEFAULT_LOAD_DATE_FIELD = "load_dt";
  public static final String DEFAULT_CURRENT_FLAG_FIELD = "is_current";
  public static final String DEFAULT_OPEN_START_SENTINEL = "1900-01-01 00:00:00";
  public static final String DEFAULT_OPEN_END_SENTINEL = "9999-12-31 23:59:59";
  public static final String DEFAULT_TARGET_TABLE_BATCH_SIZE = "1000";
  public static final String DEFAULT_TARGET_TABLE_PARALLEL_COPIES = "1";
  public static final String DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX = "dm-dim-";
  public static final String DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX = "dm-junk-";
  public static final String DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX = "dm-bridge-";
  public static final String DEFAULT_FACT_PIPELINE_NAME_PREFIX = "dm-fact-";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DimensionalConfiguration.TargetDatabase.Label",
      toolTip = "i18n::DimensionalConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetDatabase;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DimensionalConfiguration.SourceDatabase.Label",
      toolTip = "i18n::DimensionalConfiguration.SourceDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceDatabase;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FOLDER,
      label = "i18n::DimensionalConfiguration.StandardColumns.Label",
      toolTip = "i18n::DimensionalConfiguration.StandardColumns.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String standardColumnsFolder;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DimKeyField.Label",
      toolTip = "i18n::DimensionalConfiguration.DimKeyField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dimKeyField = DEFAULT_DIM_KEY_FIELD;

  @GuiWidgetElement(
      order = "0220",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.VersionField.Label",
      toolTip = "i18n::DimensionalConfiguration.VersionField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String versionField = DEFAULT_VERSION_FIELD;

  @GuiWidgetElement(
      order = "0230",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DateFromField.Label",
      toolTip = "i18n::DimensionalConfiguration.DateFromField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dateFromField = DEFAULT_DATE_FROM_FIELD;

  @GuiWidgetElement(
      order = "0240",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DateToField.Label",
      toolTip = "i18n::DimensionalConfiguration.DateToField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dateToField = DEFAULT_DATE_TO_FIELD;

  @GuiWidgetElement(
      order = "0250",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.LoadDateField.Label",
      toolTip = "i18n::DimensionalConfiguration.LoadDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String loadDateField = DEFAULT_LOAD_DATE_FIELD;

  @GuiWidgetElement(
      order = "0260",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.CurrentFlagField.Label",
      toolTip = "i18n::DimensionalConfiguration.CurrentFlagField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String currentFlagField = DEFAULT_CURRENT_FLAG_FIELD;

  @HopMetadataProperty private String openStartSentinel = DEFAULT_OPEN_START_SENTINEL;

  @HopMetadataProperty private String openEndSentinel = DEFAULT_OPEN_END_SENTINEL;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::DimensionalConfiguration.GeneratedPipelineFolder.Label",
      toolTip = "i18n::DimensionalConfiguration.GeneratedPipelineFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String generatedPipelineFolder;

  @HopMetadataProperty private String dimensionPipelineNamePrefix = DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty
  private String junkDimensionPipelineNamePrefix = DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty private String bridgePipelineNamePrefix = DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty private String factPipelineNamePrefix = DEFAULT_FACT_PIPELINE_NAME_PREFIX;

  @HopMetadataProperty private String targetTableBatchSize = DEFAULT_TARGET_TABLE_BATCH_SIZE;

  @HopMetadataProperty private String targetTableParallelCopies = DEFAULT_TARGET_TABLE_PARALLEL_COPIES;

  public String resolveDimKeyField(IVariables variables) {
    return resolveField(dimKeyField, DEFAULT_DIM_KEY_FIELD, variables);
  }

  public String resolveVersionField(IVariables variables) {
    return resolveField(versionField, DEFAULT_VERSION_FIELD, variables);
  }

  public String resolveDateFromField(IVariables variables) {
    return resolveField(dateFromField, DEFAULT_DATE_FROM_FIELD, variables);
  }

  public String resolveDateToField(IVariables variables) {
    return resolveField(dateToField, DEFAULT_DATE_TO_FIELD, variables);
  }

  public String resolveLoadDateField(IVariables variables) {
    return resolveField(loadDateField, DEFAULT_LOAD_DATE_FIELD, variables);
  }

  public String resolveCurrentFlagField(IVariables variables) {
    return resolveField(currentFlagField, DEFAULT_CURRENT_FLAG_FIELD, variables);
  }

  public String resolveTargetTableCommitSize(IVariables variables) {
    return resolveField(targetTableBatchSize, DEFAULT_TARGET_TABLE_BATCH_SIZE, variables);
  }

  public String resolveTargetTableParallelCopies(IVariables variables) {
    return resolveField(targetTableParallelCopies, DEFAULT_TARGET_TABLE_PARALLEL_COPIES, variables);
  }

  public String buildDimensionPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, dimensionPipelineNamePrefix, DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX, targetTableName);
  }

  public String buildJunkDimensionPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables,
        junkDimensionPipelineNamePrefix,
        DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX,
        targetTableName);
  }

  public String buildBridgePipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, bridgePipelineNamePrefix, DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX, targetTableName);
  }

  public String buildFactPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, factPipelineNamePrefix, DEFAULT_FACT_PIPELINE_NAME_PREFIX, targetTableName);
  }

  private static String resolveField(String value, String defaultValue, IVariables variables) {
    String resolved = Utils.isEmpty(value) ? defaultValue : value;
    if (variables != null) {
      resolved = variables.resolve(resolved);
    }
    return resolved;
  }

  private static String buildPipelineName(
      IVariables variables, String configuredPrefix, String defaultPrefix, String targetTableName) {
    String prefix = Utils.isEmpty(configuredPrefix) ? defaultPrefix : configuredPrefix;
    if (variables != null) {
      prefix = variables.resolve(prefix);
    }
    StringBuilder name = new StringBuilder(prefix);
    if (!Utils.isEmpty(targetTableName)) {
      name.append(targetTableName);
    }
    return name.toString();
  }
}