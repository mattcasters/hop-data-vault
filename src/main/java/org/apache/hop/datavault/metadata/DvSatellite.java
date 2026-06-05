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
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.hopgui.HopGui;

/**
 * Data Vault 2.0 Satellite metadata definition.
 *
 * <p>Satellites hold the time-variant descriptive attributes for either a Hub or a Link.
 * Changes are captured by inserting new rows (insert-only pattern in DV 2.0).
 */
@GuiPlugin
public class DvSatellite extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  private static final Class<?> PKG = DvSatellite.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_SATELLITE_DIALOG";

  /**
   * The Hub this satellite describes (if hub satellite).
   * Use either hubName or linkName, not both.
   */
  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.METADATA,
      metadata = DvHub.class,
      label = "i18n::DvSatellite.HubName.Label",
      toolTip = "i18n::DvSatellite.HubName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "hub")
  private String hubName;

  /**
   * The Link this satellite describes (if link satellite).
   */
  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.METADATA,
      metadata = DvLink.class,
      label = "i18n::DvSatellite.LinkName.Label",
      toolTip = "i18n::DvSatellite.LinkName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "link")
  private String linkName;

  // Satellite attributes (nested POJO list). @GuiWidgetElement LIST not available in GuiElementType.
  @HopMetadataProperty
  private List<SatelliteAttribute> attributes = new ArrayList<>();

  /** For multi-active satellites (e.g. multiple phone numbers per customer at same time). */
  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvSatellite.MultiActive.Label",
      toolTip = "i18n::DvSatellite.MultiActive.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean multiActive;

  /** Name of the driving key column in a multi-active satellite (if applicable). */
  @GuiWidgetElement(
      order = "0710",
      type = GuiElementType.TEXT,
      label = "i18n::DvSatellite.DrivingKey.Label",
      toolTip = "i18n::DvSatellite.DrivingKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String drivingKey;

  public DvSatellite() {
    super();
    this.tableType = DvTableType.SATELLITE;
  }

  public DvSatellite(String name) {
    super(name);
    this.tableType = DvTableType.SATELLITE;
  }

  public String getHubName() {
    return hubName;
  }

  public void setHubName(String hubName) {
    if (!java.util.Objects.equals(this.hubName, hubName)) {
      setChanged();
    }
    this.hubName = hubName;
  }

  public String getLinkName() {
    return linkName;
  }

  public void setLinkName(String linkName) {
    if (!java.util.Objects.equals(this.linkName, linkName)) {
      setChanged();
    }
    this.linkName = linkName;
  }

  public List<SatelliteAttribute> getAttributes() {
    return attributes;
  }

  public void setAttributes(List<SatelliteAttribute> attributes) {
    if (!java.util.Objects.equals(this.attributes, attributes)) {
      setChanged();
    }
    this.attributes = attributes;
  }

  public boolean isMultiActive() {
    return multiActive;
  }

  public void setMultiActive(boolean multiActive) {
    if (this.multiActive != multiActive) {
      setChanged();
    }
    this.multiActive = multiActive;
  }

  public String getDrivingKey() {
    return drivingKey;
  }

  public void setDrivingKey(String drivingKey) {
    if (!java.util.Objects.equals(this.drivingKey, drivingKey)) {
      setChanged();
    }
    this.drivingKey = drivingKey;
  }

  @Override
  public void check(List<ICheckResult> remarks) {
    super.check(remarks);
    if (Utils.isEmpty(hubName) && Utils.isEmpty(linkName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.NotLinked"),
              this));
    } else if (!Utils.isEmpty(hubName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.LinkedToHub", hubName),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.LinkedToLink", linkName),
              this));
    }
  }

  @Override
  public PipelineMeta generateUpdatePipeline(HopGui hopGui, DataVaultModel model) throws HopException {
    // TODO: implement for satellites (stub for now)
    return null;
  }

  @Override
  public IRowMeta getTargetTableLayout(HopGui hopGui, DataVaultModel model) {
    // TODO: implement for satellites (stub for now)
    return null;
  }
}
