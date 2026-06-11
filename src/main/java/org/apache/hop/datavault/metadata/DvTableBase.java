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

import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/**
 * Common abstract base class for Data Vault 2.0 table definitions (Hub, Link, Satellite).
 *
 * <p>Provides shared properties that every DV table has:
 *
 * <ul>
 *   <li>physical table name
 *   <li>description
 *   <li>record source
 * </ul>
 *
 * <p>Concrete classes (DvHub, DvLink, DvSatellite) extend this and add their specific attributes
 * (business keys, participating hubs, satellite columns, etc.).
 *
 * <p>This base also implements {@link IDvTable} so that generic code can work with any DV table
 * type uniformly.
 *
 * <p>Because IDvTable now extends IGuiPosition, IBaseMeta and IHasName, this base provides the
 * necessary fields and method implementations (including location support via IGuiPosition) so that
 * Hubs, Links and Satellites can be used as positionable elements in a visual Data Vault modeler.
 */
@Getter
@Setter
public abstract class DvTableBase extends HopMetadataBase implements IHopMetadata, IDvTable {

  private static final Class<?> PKG = DvTableBase.class;

  // Common DV table properties. @HopMetadataProperty is here for correct JSON serialization
  // and metadata provider behavior. The @GuiWidgetElement annotations are deliberately
  // omitted from the base so that the fields participate cleanly in the per-type dialogs
  // defined on DvHub / DvLink / DvSatellite (which use their own GUI_PLUGIN_ELEMENT_PARENT_ID).
  // The properties will receive reasonable default widgets. For pixel-perfect per-type
  // layout a custom MetadataEditor can be added later.
  @HopMetadataProperty(key = "tableName")
  protected String tableName;

  @HopMetadataProperty protected String description;

  /**
   * The type of this Data Vault table. Persisted for easy identification when serializing models.
   */
  @HopMetadataProperty protected DvTableType tableType;

  protected final ChangedFlag changedFlag = new ChangedFlag();

  /**
   * Location (x,y) of this table element when displayed in a visual Data Vault modeler / graph
   * canvas. Stored inline so that model layouts are persisted with the metadata.
   */
  @HopMetadataProperty(inline = true)
  private Point location;

  /**
   * GUI selection state. This is transient (not persisted) and only meaningful while interacting
   * with a visual editor.
   */
  private boolean selected;

  /**
   * Transient pixel size of the drawn table box (card) for this table. Used for hit-testing,
   * connection line attachment points, and fit-to-screen calculations. Computed in the painter
   * using textExtent() based on the table name.
   */
  private int drawnBoxWidth = 140;

  private int drawnBoxHeight = 70;

  protected DvTableBase() {
    super();
    this.location = new Point(0, 0);
  }

  protected DvTableBase(String name) {
    super(name);
    this.tableName = name;
    this.location = new Point(0, 0);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DvTableBase that)) return false;
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(tableName, that.tableName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), tableName);
  }

  // --- IDvTable + common accessors ---

  @Override
  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    if (!java.util.Objects.equals(this.tableName, tableName)) {
      setChanged();
    }
    this.tableName = tableName;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    if (!java.util.Objects.equals(this.description, description)) {
      setChanged();
    }
    this.description = description;
  }

  @Override
  public void setName(String name) {
    if (!java.util.Objects.equals(getName(), name)) {
      setChanged();
    }
    super.setName(name);
  }

  @Override
  public DvTableType getTableType() {
    return tableType;
  }

  public void setTableType(DvTableType tableType) {
    if (!java.util.Objects.equals(this.tableType, tableType)) {
      setChanged();
    }
    this.tableType = tableType;
  }

  /**
   * Convenience: if no explicit table name has been set, fall back to the metadata name. Useful for
   * generators that want a sensible default.
   */
  public String getEffectiveTableName() {
    if (tableName != null && !tableName.isBlank()) {
      return tableName;
    }
    return getName();
  }

  // -------------------------------------------------------------------------------------
  // IGuiPosition, IBaseMeta implementations (for visual modeling / canvas)
  // (covers location + selection via IGuiPosition; IBaseMeta just requires getLocation())
  // -------------------------------------------------------------------------------------

  @Override
  public Point getLocation() {
    if (location == null) {
      location = new Point(0, 0);
    }
    return location;
  }

  @Override
  public void setLocation(Point p) {
    if (p != null && !p.equals(this.location)) {
      setChanged();
    }
    this.location = p;
  }

  @Override
  public void setLocation(int x, int y) {
    int nx = Math.max(0, x);
    int ny = Math.max(0, y);
    Point newLoc = new Point(nx, ny);
    if (!newLoc.equals(this.location)) {
      setChanged();
      this.location = newLoc;
    }
  }

  @Override
  public boolean isSelected() {
    return selected;
  }

  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  // -------------------------------------------------------------------------------------
  // IChanged implementation (for change detection in visual modeler and serialization)
  // -------------------------------------------------------------------------------------

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
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
  }

  @Override
  public void check(List<ICheckResult> remarks) {
    if (Utils.isEmpty(getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvTableBase.CheckResult.NoName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvTableBase.CheckResult.HasName", getName()),
              this));
    }

    if (Utils.isEmpty(getTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvTableBase.CheckResult.NoTableName"),
              this));
    }
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    // default: no layout in base
    return null;
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate)
      throws HopException {
    // default: no pipeline in base
    return java.util.Collections.emptyList();
  }
}
