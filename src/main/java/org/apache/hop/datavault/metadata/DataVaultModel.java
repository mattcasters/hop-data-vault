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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.IUndo;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.undo.ChangeAction;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * A named Data Vault 2.0 model.
 *
 * <p>This aggregates references to Hubs, Links and Satellites (via {@link IDvTable}) that belong
 * together in one enterprise data warehouse (or a subject area / release of it).
 *
 * <p>Each table in the unified {@link #getTables()} list carries its own type ({@link
 * IDvTable#getTableType()}) and full definition. The model provides the "big picture" and a single
 * place to attach a default DataVaultConfiguration.
 *
 * <p>Sources ({@link IDvSource}) describe the incoming systems (Database connections + expected row
 * layouts today). They are used by generators / transforms to know where data originates and what
 * columns to expect.
 *
 * <p>Implements IChanged (like PipelineMeta) for dirty state tracking in the visual modeler.
 */
@GuiPlugin
@Getter
@Setter
public class DataVaultModel extends HopMetadataBase
    implements IHopMetadata, IChanged, IHasName, IHasFilename, IUndo {

  private static final Class<?> PKG = DataVaultModel.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_MODEL_DIALOG";

  @HopMetadataProperty private String name;

  /** The filename (runtime, like in AbstractMeta/PipelineMeta). Not always serialized. */
  @HopMetadataProperty private String filename;

  /**
   * Whether the name should be kept in sync with the filename (derived via
   * extractNameFromFilename). Default true, like AbstractMetaInfo.
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
   * Default configuration for hashing and physical strategy used by objects in this model (can be
   * overridden at the individual table level).
   */
  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = DataVaultConfiguration.class,
      label = "i18n::DataVaultModel.Configuration.Label",
      toolTip = "i18n::DataVaultModel.Configuration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "configuration", storeWithName = true)
  private DataVaultConfiguration configuration;

  public String getConfigurationName() {
    if (configuration != null && !Utils.isEmpty(configuration.getName())) {
      return configuration.getName();
    }
    return null;
  }

  /**
   * All tables (Hubs, Links and Satellites) that belong to this Data Vault model. Each table knows
   * its own type via {@link IDvTable#getTableType()}. Stored using storeWithName so that the model
   * holds references to the individual table metadata objects (rather than inlining them).
   */
  // GuiElementType.LIST is not available; lists are handled by serialization and the metadata
  // perspective.
  @HopMetadataProperty(key = "table", groupKey = "tables")
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

  /**
   * Perform a series of validation checks on this Data Vault model and return the results.
   *
   * @return list of check results (errors, warnings, ok)
   */
  public List<ICheckResult> check(IHopMetadataProvider metadataProvider, IVariables variables) {
    List<ICheckResult> remarks = new ArrayList<>();

    List<DataVaultSource> sources = null;
    try {
      sources = metadataProvider.getSerializer(DataVaultSource.class).loadAll();
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DataVaultModel.CheckResult.ErrorLoadingMetadata"),
              null));
    }

    if (sources == null) {
      sources = new ArrayList<>();
    }

    // Top-level model checks
    if (configuration == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "DataVaultModel.CheckResult.NoConfiguration"),
              null));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DataVaultModel.CheckResult.HasConfiguration", configuration.getName()),
              null));
    }

    if (Utils.isEmpty(tables)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "DataVaultModel.CheckResult.NoTables"),
              null));
    }

    // Collect defined names for reference validation
    Set<String> definedSourceNames = new HashSet<>();
    if (sources != null) {
      for (DataVaultSource s : sources) {
        if (!Utils.isEmpty(s.getName())) {
          definedSourceNames.add(s.getName());
        }
      }
    }

    Set<String> definedHubNames = new HashSet<>();
    Set<String> definedLinkNames = new HashSet<>();
    if (tables != null) {
      for (IDvTable t : tables) {
        if (!Utils.isEmpty(t.getName())) {
          if (t.getTableType() == DvTableType.HUB) {
            definedHubNames.add(t.getName());
          } else if (t.getTableType() == DvTableType.LINK) {
            definedLinkNames.add(t.getName());
          }
        }
      }
    }

    // Per-table reference validation + delegate to table checks
    if (tables != null) {
      for (IDvTable table : tables) {
        table.check(remarks, metadataProvider, variables);

        // Cross-references for satellites and links
        if (table instanceof DvSatellite sat) {
          if (!Utils.isEmpty(sat.getHubName()) && !definedHubNames.contains(sat.getHubName())) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(
                        PKG, "DataVaultModel.CheckResult.MissingHub", sat.getHubName()),
                    table));
          }

          if (!Utils.isEmpty(sat.getLinkName()) && !definedLinkNames.contains(sat.getLinkName())) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(
                        PKG, "DataVaultModel.CheckResult.MissingLink", sat.getLinkName()),
                    table));
          }
        }

        if (table instanceof DvLink lnk) {
          for (String hn : lnk.getHubNames()) {
            if (!Utils.isEmpty(hn) && !definedHubNames.contains(hn)) {
              remarks.add(
                  new CheckResult(
                      ICheckResult.TYPE_RESULT_ERROR,
                      BaseMessages.getString(PKG, "DataVaultModel.CheckResult.MissingHub", hn),
                      table));
            }
          }
        }
      }
    }

    // Duplicate name checks
    Map<String, Integer> nameCount = new HashMap<>();
    if (tables != null) {
      for (IDvTable t : tables) {
        if (!Utils.isEmpty(t.getName())) {
          nameCount.put(t.getName(), nameCount.getOrDefault(t.getName(), 0) + 1);
        }
      }
    }
    for (Map.Entry<String, Integer> e : nameCount.entrySet()) {
      if (e.getValue() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DataVaultModel.CheckResult.DuplicateTableName", e.getKey()),
                null));
      }
    }

    nameCount.clear();
    for (DataVaultSource s : sources) {
      if (!Utils.isEmpty(s.getName())) {
        nameCount.put(s.getName(), nameCount.getOrDefault(s.getName(), 0) + 1);
      }
    }
    for (Map.Entry<String, Integer> e : nameCount.entrySet()) {
      if (e.getValue() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DataVaultModel.CheckResult.DuplicateSourceName", e.getKey()),
                null));
      }
    }

    return remarks;
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

  /**
   * Find the Hub with the given name in the model
   *
   * @param hubName The name of the hub to look for.
   * @return The Hub or null if not found.
   */
  public DvHub findHub(String hubName) {
    for (IDvTable table : tables) {
      if (table.getTableType() == DvTableType.HUB && table.getName().equalsIgnoreCase(hubName)) {
        return (DvHub) table;
      }
    }
    return null;
  }

  /**
   * Find the table with the given name in the model
   *
   * @param tableName The name of the table to look for.
   * @return The table or null if not found.
   */
  public IDvTable findTable(String tableName) {
    for (IDvTable table : tables) {
      if (table.getName().equalsIgnoreCase(tableName)) {
        return table;
      }
    }
    return null;
  }

  /**
   * Count the number of selected tables in the model.
   *
   * @return The number of selected tables
   */
  public int nrSelectedTables() {
    int nr = 0;
    for (IDvTable table : tables) {
      if (table.isSelected()) {
        nr++;
      }
    }
    return nr;
  }

  public Point getMaximum() {
    int maxx = 0;
    int maxy = 0;
    for (IDvTable table : tables) {
      Point loc = table.getLocation();
      if (loc.x > maxx) {
        maxx = loc.x;
      }
      if (loc.y > maxy) {
        maxy = loc.y;
      }
    }
    return new Point(maxx + 100, maxy + 100);
  }

  // TODO: implement undo

  @Override
  public void addUndo(
      Object[] from,
      Object[] to,
      int[] pos,
      Point[] prev,
      Point[] curr,
      int typeOfChange,
      boolean nextAlso) {}

  @Override
  public int getMaxUndo() {
    return 0;
  }

  @Override
  public void setMaxUndo(int mu) {}

  @Override
  public ChangeAction previousUndo() {
    return null;
  }

  @Override
  public ChangeAction viewThisUndo() {
    return null;
  }

  @Override
  public ChangeAction viewPreviousUndo() {
    return null;
  }

  @Override
  public ChangeAction nextUndo() {
    return null;
  }

  @Override
  public ChangeAction viewNextUndo() {
    return null;
  }
}
