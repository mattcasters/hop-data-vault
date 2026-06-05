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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/**
 * A generic Data Vault Source metadata element.
 *
 * <p>Main properties include the type of source (e.g. Database) and the name of the source
 * table.
 */
@GuiPlugin
@HopMetadata(
    key = "data-vault-source",
    name = "i18n::DataVaultSource.name",
    description = "i18n::DataVaultSource.description",
    image = "datavault_model.svg",
    documentationUrl = "/metadata-types/data-vault-source.html")
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

  public DataVaultSource() {
    super();
  }

  public DataVaultSource(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  public DataVaultSourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(DataVaultSourceType sourceType) {
    this.sourceType = sourceType;
  }

  public String getSourceTableName() {
    return sourceTableName;
  }

  public void setSourceTableName(String sourceTableName) {
    this.sourceTableName = sourceTableName;
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
          throw new HopException("Source type fields retrieval is not yet implemented for type "+sourceType);
      }
    } catch(Exception e) {
      throw new HopException("Unable to get fields from underlying physical data vault source of type "+sourceType.name(), e);
    }
  }

  private List<SourceField> getDatabaseSourceFields(IHopMetadataProvider metadataProvider) throws HopException {
    IHopMetadataSerializer<DvDatabaseSource> serializer = metadataProvider.getSerializer(DvDatabaseSource.class);
    DvDatabaseSource databaseSource = serializer.load(sourceTableName);
    return databaseSource.getFields();
  }
}
