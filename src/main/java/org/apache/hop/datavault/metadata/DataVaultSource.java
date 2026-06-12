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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/**
 * A generic Data Vault Source metadata element.
 *
 * <p>Main properties include the type of source (e.g. Database) and the name of the source table.
 */
@GuiPlugin
@HopMetadata(
    key = "data-vault-source",
    name = "i18n::DataVaultSource.name",
    description = "i18n::DataVaultSource.description",
    image = "datavault_model.svg",
    documentationUrl = "/metadata-types/data-vault-source.html")
@Getter
@Setter
public class DataVaultSource extends HopMetadataBase implements IHopMetadata, IHasName {
  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_SOURCE_DIALOG";

  @HopMetadataProperty private String name;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultSource.SourceType.Label",
      toolTip = "i18n::DataVaultSource.SourceType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private DataVaultSourceType sourceType = DataVaultSourceType.DATABASE;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = DvDatabaseSource.class,
      label = "i18n::DataVaultSource.SourceTableName.Label",
      toolTip = "i18n::DataVaultSource.SourceTableName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceTableName;

  /**
   * Optional name of a column in the source table that holds the per-row source system indicator
   * (record source) value. Used only if no static {@link #sourceIndicator} is provided.
   */
  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultSource.SourceIndicatorField.Label",
      toolTip = "i18n::DataVaultSource.SourceIndicatorField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceIndicatorField;

  /**
   * Optional static value for the source system indicator (record source) for rows coming from this
   * source. If specified (non-empty after variable resolution), this static value is used in
   * preference to any sourceIndicatorField.
   */
  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultSource.SourceIndicator.Label",
      toolTip = "i18n::DataVaultSource.SourceIndicator.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceIndicator;

  public DataVaultSource() {
    super();
  }

  public DataVaultSource(String name) {
    this.name = name;
  }

  /**
   * Resolve the source indicator (record source value) according to the defined precedence:
   *
   * <p>If a static {@code sourceIndicator} is specified and non-empty (variables resolved if
   * provided), that static value is returned. Otherwise, the indicator value should come from the
   * column named by {@code sourceIndicatorField} in the referenced source table (if any), or none.
   *
   * @param variables used to resolve variables in a static sourceIndicator (may be null)
   * @return the static indicator value to emit, or {@code null} meaning "use the column named in
   *     sourceIndicatorField from the source table (or no indicator)"
   */
  public String getResolvedSourceIndicator(IVariables variables) {
    String staticValue = sourceIndicator;
    if (variables != null) {
      staticValue = variables.resolve(staticValue);
    }
    if (!StringUtils.isEmpty(staticValue)) {
      return staticValue;
    }
    // No static value: the indicator (if any) comes from the field named in sourceIndicatorField
    // in the source table rows.
    return null;
  }

  public List<SourceField> getFields(IHopMetadataProvider metadataProvider) throws HopException {
    try {
      if (StringUtils.isEmpty(getSourceTableName())) {
        throw new HopException("Please specify the Data Vault Source to use!");
      }

      switch (sourceType) {
        case DATABASE:
          return getDatabaseSourceFields(metadataProvider);
        default:
          throw new HopException(
              "Source type fields retrieval is not yet implemented for type " + sourceType);
      }
    } catch (Exception e) {
      throw new HopException(
          "Unable to get fields from underlying physical data vault source of type "
              + sourceType.name(),
          e);
    }
  }

  private List<SourceField> getDatabaseSourceFields(IHopMetadataProvider metadataProvider)
      throws HopException {
    DvDatabaseSource databaseSource = loadDatabaseSource(metadataProvider);
    return databaseSource.getFields();
  }

  private DvDatabaseSource loadDatabaseSource(IHopMetadataProvider metadataProvider)
      throws HopException {
    IHopMetadataSerializer<DvDatabaseSource> serializer =
        metadataProvider.getSerializer(DvDatabaseSource.class);
    DvDatabaseSource databaseSource = serializer.load(sourceTableName);
    return databaseSource;
  }

  public IDvSource getDvSource(IHopMetadataProvider metadataProvider) throws HopException {
    return switch (sourceType) {
      case DATABASE -> loadDatabaseSource(metadataProvider);
    };
  }
}
