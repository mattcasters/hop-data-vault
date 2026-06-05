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

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Configuration metadata for Data Vault 2.0 physical implementation and update strategy.
 *
 * <p>This is kept separate from the logical model (Hubs/Links/Satellites) so that the same
 * model can be realized with different physical strategies or on different platforms.
 *
 * <p>Common options covered:
 * <ul>
 *   <li>Target database (DatabaseMeta) for physical implementation</li>
 *   <li>Hashing algorithm (MD5, SHA1, SHA256...)</li>
 *   <li>Hash key storage: binary (recommended) or string (hex)</li>
 *   <li>Trimming of business keys</li>
 *   <li>Case normalization before hashing</li>
 *   <li>Delimiter for composite business keys and link key construction</li>
 *   <li>Null/unknown value placeholder for consistent hashing</li>
 *   <li>Standard column naming conventions (suffixes, field names)</li>
 *   <li>Unknown record generation</li>
 * </ul>
 */
@GuiPlugin
@HopMetadata(
    key = "data-vault-configuration",
    name = "i18n::DataVaultConfiguration.name",
    description = "i18n::DataVaultConfiguration.description",
    image = "datavault_configuration.svg",
    documentationUrl = "/metadata-types/data-vault-configuration.html")
public class DataVaultConfiguration extends HopMetadataBase implements IHopMetadata, IHasName {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_CONFIGURATION_DIALOG";

  @HopMetadataProperty private String name;

  /**
   * The target database (DatabaseMeta) in which the Data Vault will be implemented.
   * This makes the configuration aware of the destination RDBMS for quoting, DDL, etc.
   */
  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DataVaultConfiguration.TargetDatabase.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "targetDatabase")
  private String targetDatabase;

  // --- Hashing Strategy ---
  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashAlgorithm.Label",
      toolTip = "i18n::DataVaultConfiguration.HashAlgorithm.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private HashAlgorithm hashAlgorithm = HashAlgorithm.MD5;

  @GuiWidgetElement(
      order = "0110",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashKeyDataType.Label",
      toolTip = "i18n::DataVaultConfiguration.HashKeyDataType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private HashKeyDataType hashKeyDataType = HashKeyDataType.BINARY;

  @GuiWidgetElement(
      order = "0120",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashContentCasing.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentCasing.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private HashContentCasing hashContentCasing = HashContentCasing.UPPER;

  @GuiWidgetElement(
      order = "0130",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BusinessKeyDelimiter.Label",
      toolTip = "i18n::DataVaultConfiguration.BusinessKeyDelimiter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String businessKeyDelimiter = "||";

  @GuiWidgetElement(
      order = "0140",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.NullPlaceholder.Label",
      toolTip = "i18n::DataVaultConfiguration.NullPlaceholder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String nullPlaceholder = "^^";

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.TrimBusinessKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.TrimBusinessKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean trimBusinessKeys = true;

  // --- Unknown / Ghost record handling (common DV pattern) ---
  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateUnknownRecord.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateUnknownRecord.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean generateUnknownRecord = true;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.UnknownBusinessKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownBusinessKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String unknownBusinessKeyValue = "N/A";

  // --- Naming conventions (update / physical strategy) ---
  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.HubHashKeySuffix.Label",
      toolTip = "i18n::DataVaultConfiguration.HubHashKeySuffix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String hubHashKeySuffix = "_HK";

  @GuiWidgetElement(
      order = "0310",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LinkHashKeySuffix.Label",
      toolTip = "i18n::DataVaultConfiguration.LinkHashKeySuffix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String linkHashKeySuffix = "_HK";

  @GuiWidgetElement(
      order = "0320",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.SatelliteHashDiffSuffix.Label",
      toolTip = "i18n::DataVaultConfiguration.SatelliteHashDiffSuffix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String satelliteHashDiffSuffix = "_HASHDIFF";

  @GuiWidgetElement(
      order = "0330",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LoadDateField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String loadDateField = "LOAD_DATE";

  @GuiWidgetElement(
      order = "0340",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LoadEndDateField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadEndDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String loadEndDateField = "LOAD_END_DATE";

  @GuiWidgetElement(
      order = "0350",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.RecordSourceField.Label",
      toolTip = "i18n::DataVaultConfiguration.RecordSourceField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceField = "RECORD_SOURCE";

  @GuiWidgetElement(
      order = "0360",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.SourceField.Label",
      toolTip = "i18n::DataVaultConfiguration.SourceField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceField = "SOURCE";

  // --- Satellite loading pattern ---
  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.UseHashDiff.Label",
      toolTip = "i18n::DataVaultConfiguration.UseHashDiff.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean useHashDiff = true;

  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.UseLoadEndDate.Label",
      toolTip = "i18n::DataVaultConfiguration.UseLoadEndDate.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean useLoadEndDate = false;

  public DataVaultConfiguration() {
    super();
  }

  public DataVaultConfiguration(String name) {
    this.name = name;
  }

  // --- Getters & Setters ---

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  public String getTargetDatabase() {
    return targetDatabase;
  }

  public void setTargetDatabase(String targetDatabase) {
    this.targetDatabase = targetDatabase;
  }

  public HashAlgorithm getHashAlgorithm() {
    return hashAlgorithm;
  }

  public void setHashAlgorithm(HashAlgorithm hashAlgorithm) {
    this.hashAlgorithm = hashAlgorithm;
  }

  public HashKeyDataType getHashKeyDataType() {
    return hashKeyDataType;
  }

  public void setHashKeyDataType(HashKeyDataType hashKeyDataType) {
    this.hashKeyDataType = hashKeyDataType;
  }

  public HashContentCasing getHashContentCasing() {
    return hashContentCasing;
  }

  public void setHashContentCasing(HashContentCasing hashContentCasing) {
    this.hashContentCasing = hashContentCasing;
  }

  public String getBusinessKeyDelimiter() {
    return businessKeyDelimiter;
  }

  public void setBusinessKeyDelimiter(String businessKeyDelimiter) {
    this.businessKeyDelimiter = businessKeyDelimiter;
  }

  public String getNullPlaceholder() {
    return nullPlaceholder;
  }

  public void setNullPlaceholder(String nullPlaceholder) {
    this.nullPlaceholder = nullPlaceholder;
  }

  public boolean isTrimBusinessKeys() {
    return trimBusinessKeys;
  }

  public void setTrimBusinessKeys(boolean trimBusinessKeys) {
    this.trimBusinessKeys = trimBusinessKeys;
  }

  public boolean isGenerateUnknownRecord() {
    return generateUnknownRecord;
  }

  public void setGenerateUnknownRecord(boolean generateUnknownRecord) {
    this.generateUnknownRecord = generateUnknownRecord;
  }

  public String getUnknownBusinessKeyValue() {
    return unknownBusinessKeyValue;
  }

  public void setUnknownBusinessKeyValue(String unknownBusinessKeyValue) {
    this.unknownBusinessKeyValue = unknownBusinessKeyValue;
  }

  public String getHubHashKeySuffix() {
    return hubHashKeySuffix;
  }

  public void setHubHashKeySuffix(String hubHashKeySuffix) {
    this.hubHashKeySuffix = hubHashKeySuffix;
  }

  public String getLinkHashKeySuffix() {
    return linkHashKeySuffix;
  }

  public void setLinkHashKeySuffix(String linkHashKeySuffix) {
    this.linkHashKeySuffix = linkHashKeySuffix;
  }

  public String getSatelliteHashDiffSuffix() {
    return satelliteHashDiffSuffix;
  }

  public void setSatelliteHashDiffSuffix(String satelliteHashDiffSuffix) {
    this.satelliteHashDiffSuffix = satelliteHashDiffSuffix;
  }

  public String getLoadDateField() {
    return loadDateField;
  }

  public void setLoadDateField(String loadDateField) {
    this.loadDateField = loadDateField;
  }

  public String getLoadEndDateField() {
    return loadEndDateField;
  }

  public void setLoadEndDateField(String loadEndDateField) {
    this.loadEndDateField = loadEndDateField;
  }

  public String getRecordSourceField() {
    return recordSourceField;
  }

  public void setRecordSourceField(String recordSourceField) {
    this.recordSourceField = recordSourceField;
  }

  public String getSourceField() {
    return sourceField;
  }

  public void setSourceField(String sourceField) {
    this.sourceField = sourceField;
  }

  public boolean isUseHashDiff() {
    return useHashDiff;
  }

  public void setUseHashDiff(boolean useHashDiff) {
    this.useHashDiff = useHashDiff;
  }

  public boolean isUseLoadEndDate() {
    return useLoadEndDate;
  }

  public void setUseLoadEndDate(boolean useLoadEndDate) {
    this.useLoadEndDate = useLoadEndDate;
  }
}
