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

package org.apache.hop.datavault.metadata;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * A Data Vault record source: DV semantics (indicator, group) plus an embedded polymorphic {@link
 * IDvSource} for how rows are read (database table today; file formats later).
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

  public static final String GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID = "DATAVAULT_SOURCE_GENERAL_TAB";

  @HopMetadataProperty private String name;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultSource.SourceIndicatorField.Label",
      toolTip = "i18n::DataVaultSource.SourceIndicatorField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String sourceIndicatorField;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultSource.SourceIndicator.Label",
      toolTip = "i18n::DataVaultSource.SourceIndicator.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String sourceIndicator;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultSource.Group.Label",
      toolTip = "i18n::DataVaultSource.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String group;

  @HopMetadataProperty(key = "source")
  private IDvSource source = new DvDatabaseSource();

  public DataVaultSource() {
    super();
  }

  public DataVaultSource(String name) {
    this.name = name;
  }

  public IDvSource getDvSourceOrDefault() {
    if (source == null) {
      source = new DvDatabaseSource();
    }
    return source;
  }

  public DvSourceType getSourceType() {
    DvSourceType type = getDvSourceOrDefault().getSourceType();
    return type != null ? type : DvSourceType.DATABASE;
  }

  public boolean matchesRecordSourceGroup(String groupFilter, IVariables variables) {
    if (Utils.isEmpty(groupFilter)) {
      return true;
    }
    String resolvedFilter = variables != null ? variables.resolve(groupFilter) : groupFilter;
    if (Utils.isEmpty(resolvedFilter)) {
      return true;
    }
    String resolvedGroup = group;
    if (variables != null) {
      resolvedGroup = variables.resolve(resolvedGroup);
    }
    return resolvedFilter.equals(resolvedGroup);
  }

  public String getResolvedSourceIndicator(IVariables variables) {
    String staticValue = sourceIndicator;
    if (variables != null) {
      staticValue = variables.resolve(staticValue);
    }
    if (!StringUtils.isEmpty(staticValue)) {
      return staticValue;
    }
    return null;
  }

  public List<SourceField> getFields() throws HopException {
    List<SourceField> fields = getDvSourceOrDefault().getFields();
    if (fields == null || fields.isEmpty()) {
      throw new HopException(
          "Please define source fields for Data Vault Source '"
              + getName()
              + "' (import from table or edit the fields list).");
    }
    return fields;
  }

  public List<SourceField> getFields(IHopMetadataProvider metadataProvider) throws HopException {
    return getFields();
  }

  public IDvSource getDvSource(IHopMetadataProvider metadataProvider) throws HopException {
    return getDvSourceOrDefault();
  }
}