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

import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Definition of a single field (column) in the expected row layout coming from a Data Vault
 * Source.
 *
 * <p>This describes the source schema as seen by the Data Vault loading processes. It is
 * independent of the target DV physical model (hubs/sats will derive their columns from these
 * source fields plus generated hash keys, etc).
 *
 * <p>Used for all source types (Database, CSV, Parquet, ...).
 */
@GuiPlugin
public class SourceField {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_SOURCE_FIELD_DIALOG";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::SourceField.Name.Label",
      toolTip = "i18n::SourceField.Name.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String name;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::SourceField.Description.Label",
      toolTip = "i18n::SourceField.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::SourceField.SourceDataType.Label",
      toolTip = "i18n::SourceField.SourceDataType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceDataType;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::SourceField.Length.Label",
      toolTip = "i18n::SourceField.Length.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String length;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::SourceField.Precision.Label",
      toolTip = "i18n::SourceField.Precision.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String precision;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::SourceField.PrimaryKey.Label",
      toolTip = "i18n::SourceField.PrimaryKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean primaryKey;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::SourceField.HopType.Label",
      toolTip = "i18n::SourceField.HopType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(intCodeConverter = ValueMetaBase.ValueTypeCodeConverter.class)
  private int hopType;

  public SourceField() {}

  public SourceField(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSourceDataType() {
    return sourceDataType;
  }

  public void setSourceDataType(String sourceDataType) {
    this.sourceDataType = sourceDataType;
  }

  public String getLength() {
    return length;
  }

  public void setLength(String length) {
    this.length = length;
  }

  public String getPrecision() {
    return precision;
  }

  public void setPrecision(String precision) {
    this.precision = precision;
  }

  public boolean isPrimaryKey() {
    return primaryKey;
  }

  public void setPrimaryKey(boolean primaryKey) {
    this.primaryKey = primaryKey;
  }

  public int getHopType() {
    return hopType;
  }

  public void setHopType(int hopType) {
    this.hopType = hopType;
  }
}
