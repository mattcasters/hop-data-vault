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
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataWrapper;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * A named Data Vault 2.0 model.
 *
 * <p>This aggregates references to Hubs, Links and Satellites (via {@link IDvTable})
 * that belong together in one enterprise data warehouse (or a subject area / release of it).
 *
 * <p>Each table in the unified {@link #getTables()} list carries its own type
 * ({@link IDvTable#getTableType()}) and full definition. The model provides the "big picture"
 * and a single place to attach a default DataVaultConfiguration.
 *
 * <p>Implements IChanged (like PipelineMeta) for dirty state tracking in the visual modeler.
 */
@GuiPlugin
public class DataVaultModel extends HopMetadataBase implements IHopMetadata, IChanged, IHasName {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_MODEL_DIALOG";

  @HopMetadataProperty private String name;

  /** The filename (runtime, like in AbstractMeta/PipelineMeta). Not always serialized. */
  @HopMetadataProperty private String filename;

  /**
   * Whether the name should be kept in sync with the filename (derived via extractNameFromFilename).
   * Default true, like AbstractMetaInfo.
   */
  @HopMetadataProperty(key = "name_sync_with_filename")
  private boolean nameSynchronizedWithFilename = true;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultModel.Description.Label",
      toolTip = "i18n::DataVaultModel.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  /**
   * Default configuration for hashing and physical strategy used by objects in this model
   * (can be overridden at the individual table level).
   */
  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = DataVaultConfiguration.class,
      label = "i18n::DataVaultModel.Configuration.Label",
      toolTip = "i18n::DataVaultModel.Configuration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "configuration")
  private String configurationName;

  /**
   * All tables (Hubs, Links and Satellites) that belong to this Data Vault model.
   * Each table knows its own type via {@link IDvTable#getTableType()}.
   * Stored using storeWithName so that the model holds references to the individual
   * table metadata objects (rather than inlining them).
   */
  // GuiElementType.LIST is not available; lists are handled by serialization and the metadata perspective.
  @HopMetadataProperty(key="table", groupKey = "tables")
  private List<IDvTable> tables = new ArrayList<>();

  protected final ChangedFlag changedFlag = new ChangedFlag();

  public DataVaultModel() {
    super();
  }

  public DataVaultModel(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    // Derive from filename basename (without extension) if synchronized, exactly like PipelineMeta.
    // Uses AbstractMeta.extractNameFromFilename() .
    return AbstractMeta.extractNameFromFilename(
        nameSynchronizedWithFilename, name, filename, ".hdv");
  }

  @Override
  public void setName(String name) {
    if (!java.util.Objects.equals(this.name, name)) {
      setChanged();
    }
    this.name = name;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    if (!java.util.Objects.equals(this.filename, filename)) {
      setChanged();
    }
    this.filename = filename;
  }

  public boolean isNameSynchronizedWithFilename() {
    return nameSynchronizedWithFilename;
  }

  public void setNameSynchronizedWithFilename(boolean nameSynchronizedWithFilename) {
    if (this.nameSynchronizedWithFilename != nameSynchronizedWithFilename) {
      setChanged();
    }
    this.nameSynchronizedWithFilename = nameSynchronizedWithFilename;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    if (!java.util.Objects.equals(this.description, description)) {
      setChanged();
    }
    this.description = description;
  }

  public String getConfigurationName() {
    return configurationName;
  }

  public void setConfigurationName(String configurationName) {
    if (!java.util.Objects.equals(this.configurationName, configurationName)) {
      setChanged();
    }
    this.configurationName = configurationName;
  }

  public List<IDvTable> getTables() {
    return tables;
  }

  public void setTables(List<IDvTable> tables) {
    if (!java.util.Objects.equals(this.tables, tables)) {
      setChanged();
    }
    this.tables = tables;
  }

  // -------------------------------------------------------------------------------------
  // IChanged implementation (like PipelineMeta / AbstractMeta)
  // -------------------------------------------------------------------------------------

  @Override
  public boolean hasChanged() {
    if (changedFlag.hasChanged()) {
      return true;
    }
    if (tables != null) {
      for (IDvTable table : tables) {
        if (table.hasChanged()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean ch) {
    changedFlag.setChanged(ch);
  }

  @Override
  public void clearChanged() {
    changedFlag.clearChanged();
    if (tables != null) {
      for (IDvTable table : tables) {
        table.clearChanged();
      }
    }
  }
}
