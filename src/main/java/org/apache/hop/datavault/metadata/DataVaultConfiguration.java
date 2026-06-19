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

package org.apache.hop.datavault.metadata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Configuration for Data Vault 2.0 physical implementation and update strategy.
 *
 * <p>Embedded inline in {@link DataVaultModel} so each model carries its own hashing, naming and
 * target database settings.
 */
@Getter
@Setter
@GuiPlugin
public class DataVaultConfiguration {

  public static final String GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID =
      "DATAVAULT_CONFIGURATION_GENERAL_TAB";

  public static final String GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID =
      "DATAVAULT_CONFIGURATION_UNKNOWN_TAB";

  public static final String GUI_PLUGIN_ELEMENT_INVALID_TAB_ID =
      "DATAVAULT_CONFIGURATION_INVALID_TAB";

  public static final String GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID =
      "DATAVAULT_CONFIGURATION_COLUMNS_TAB";

  public static final String GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID =
      "DATAVAULT_CONFIGURATION_TARGET_LOAD_TAB";

  public static final String GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID =
      "DATAVAULT_CONFIGURATION_GENERATED_PIPELINES_TAB";

  /** Default commit size used by {@link org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta}. */
  public static final String DEFAULT_TARGET_TABLE_BATCH_SIZE = "1000";

  public static final String DEFAULT_HUB_PIPELINE_NAME_PREFIX = "hub-";
  public static final String DEFAULT_LINK_PIPELINE_NAME_PREFIX = "link-";
  public static final String DEFAULT_SATELLITE_PIPELINE_NAME_PREFIX = "sat-";

  /**
   * The target database (DatabaseMeta) in which the Data Vault will be implemented. This makes the
   * configuration aware of the destination RDBMS for quoting, DDL, etc.
   */
  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DataVaultConfiguration.TargetDatabase.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty(key = "targetDatabase")
  private String targetDatabase;

  // --- Hashing Strategy ---
  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashAlgorithm.Label",
      toolTip = "i18n::DataVaultConfiguration.HashAlgorithm.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private HashAlgorithm hashAlgorithm = HashAlgorithm.MD5;

  @GuiWidgetElement(
      order = "0110",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashKeyDataType.Label",
      toolTip = "i18n::DataVaultConfiguration.HashKeyDataType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private HashKeyDataType hashKeyDataType = HashKeyDataType.BINARY;

  // --- Unknown / ghost record handling (common DV pattern) ---
  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateUnknownRecord.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateUnknownRecord.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private boolean generateUnknownRecord = true;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownBusinessKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownBusinessKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownBusinessKeyValue;

  @GuiWidgetElement(
      order = "0220",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownHashKeyValue;

  @GuiWidgetElement(
      order = "0230",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownLinkHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownLinkHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownLinkHashKeyValue;

  @GuiWidgetElement(
      order = "0240",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownRecordSource.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownRecordSource.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownRecordSource;

  // --- Invalid / error record handling ---
  @GuiWidgetElement(
      order = "0250",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateInvalidRecord.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateInvalidRecord.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private boolean generateInvalidRecord = true;

  @GuiWidgetElement(
      order = "0260",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidBusinessKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidBusinessKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidBusinessKeyValue;

  @GuiWidgetElement(
      order = "0270",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidHashKeyValue;

  @GuiWidgetElement(
      order = "0280",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidLinkHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidLinkHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidLinkHashKeyValue;

  @GuiWidgetElement(
      order = "0290",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidRecordSource.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidRecordSource.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidRecordSource;

  @GuiWidgetElement(
      order = "0120",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashContentCasing.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentCasing.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private HashContentCasing hashContentCasing = HashContentCasing.UPPER;

  @GuiWidgetElement(
      order = "0130",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BusinessKeyDelimiter.Label",
      toolTip = "i18n::DataVaultConfiguration.BusinessKeyDelimiter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String businessKeyDelimiter = "||";

  @GuiWidgetElement(
      order = "0135",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.HashContentPrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentPrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashContentPrefix;

  @GuiWidgetElement(
      order = "0140",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.NullPlaceholder.Label",
      toolTip = "i18n::DataVaultConfiguration.NullPlaceholder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String nullPlaceholder = "^^";

  @GuiWidgetElement(
      order = "0145",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.HashContentSuffix.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentSuffix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashContentSuffix;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.TrimBusinessKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.TrimBusinessKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private boolean trimBusinessKeys = true;

  @GuiWidgetElement(
      order = "0330",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LoadDateField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String loadDateField = "LOAD_DATE";

  @GuiWidgetElement(
      order = "0340",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LoadEndDateField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadEndDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String loadEndDateField = "LOAD_END_DATE";

  @GuiWidgetElement(
      order = "0350",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.RecordSourceField.Label",
      toolTip = "i18n::DataVaultConfiguration.RecordSourceField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String recordSourceField = "RECORD_SOURCE";

  @GuiWidgetElement(
      order = "0355",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.RecordSourceFieldLength.Label",
      toolTip = "i18n::DataVaultConfiguration.RecordSourceFieldLength.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String recordSourceFieldLength = "100";

  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.UseLoadEndDate.Label",
      toolTip = "i18n::DataVaultConfiguration.UseLoadEndDate.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private boolean useLoadEndDate = false;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.TargetTableBatchSize.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetTableBatchSize.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String targetTableBatchSize = DEFAULT_TARGET_TABLE_BATCH_SIZE;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::DataVaultConfiguration.GeneratedPipelineFolder.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratedPipelineFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String generatedPipelineFolder;

  @GuiWidgetElement(
      order = "0610",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.HubPipelineNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.HubPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String hubPipelineNamePrefix = DEFAULT_HUB_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0620",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.LinkPipelineNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.LinkPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String linkPipelineNamePrefix = DEFAULT_LINK_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0630",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.SatellitePipelineNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.SatellitePipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String satellitePipelineNamePrefix = DEFAULT_SATELLITE_PIPELINE_NAME_PREFIX;

  public DataVaultConfiguration() {
    this.unknownHashKeyValue = "00000000000000000000000000000000";
    this.unknownLinkHashKeyValue = "00000000000000000000000000000000";
    this.unknownRecordSource = "UNKNOWN";
    this.invalidHashKeyValue = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    this.invalidLinkHashKeyValue = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    this.invalidRecordSource = "INVALID";
  }

  /**
   * Resolves the batch size (commit size) for generated Table Output transforms writing to target DV
   * tables.
   */
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

  public String buildHubPipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(
        variables, hubPipelineNamePrefix, DEFAULT_HUB_PIPELINE_NAME_PREFIX, targetTableName, sourceName);
  }

  public String buildLinkPipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(
        variables,
        linkPipelineNamePrefix,
        DEFAULT_LINK_PIPELINE_NAME_PREFIX,
        targetTableName,
        sourceName);
  }

  public String buildSatellitePipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(
        variables,
        satellitePipelineNamePrefix,
        DEFAULT_SATELLITE_PIPELINE_NAME_PREFIX,
        targetTableName,
        sourceName);
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
    return prefix + targetTableName + "-" + sourceName;
  }
}