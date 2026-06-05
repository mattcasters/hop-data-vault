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
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;

/**
 * Data Vault 2.0 Link metadata definition.
 *
 * <p>A Link represents a relationship (many-to-many or association) between Hubs.
 * The Link's hash key is typically computed from the hash keys of the participating Hubs
 * (plus any additional descriptors for the relationship).
 */
@GuiPlugin
@HopMetadata(
    key = "data-vault-link",
    name = "i18n::DvLink.name",
    description = "i18n::DvLink.description",
    image = "datavault_link.svg",
    documentationUrl = "/metadata-types/data-vault-link.html")
public class DvLink extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_LINK_DIALOG";

  /**
   * Names of the Hubs that participate in this link (order can matter for hashing in some implementations).
   * These are references by metadata name (storeWithName behavior when used in a model).
   */
  // Participating hubs (references by name). Gui list widget omitted (no LIST in GuiElementType).
  @HopMetadataProperty
  private List<String> hubNames = new ArrayList<>();

  /**
   * Optional driving key(s) - used when the same Hub appears more than once in a link
   * (e.g. "from location" vs "to location" in a route).
   */
  // Driving keys list - Gui annotation omitted for same reason.
  @HopMetadataProperty
  private List<String> drivingKeyNames = new ArrayList<>();

  /** Whether this link carries additional relationship attributes (i.e. has its own satellite). */
  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvLink.HasDescriptiveAttributes.Label",
      toolTip = "i18n::DvLink.HasDescriptiveAttributes.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean hasDescriptiveAttributes;

  public DvLink() {
    super();
    this.tableType = DvTableType.LINK;
  }

  public DvLink(String name) {
    super(name);
    this.tableType = DvTableType.LINK;
  }

  public List<String> getHubNames() {
    return hubNames;
  }

  public void setHubNames(List<String> hubNames) {
    if (!java.util.Objects.equals(this.hubNames, hubNames)) {
      setChanged();
    }
    this.hubNames = hubNames;
  }

  public List<String> getDrivingKeyNames() {
    return drivingKeyNames;
  }

  public void setDrivingKeyNames(List<String> drivingKeyNames) {
    if (!java.util.Objects.equals(this.drivingKeyNames, drivingKeyNames)) {
      setChanged();
    }
    this.drivingKeyNames = drivingKeyNames;
  }

  public boolean isHasDescriptiveAttributes() {
    return hasDescriptiveAttributes;
  }

  public void setHasDescriptiveAttributes(boolean hasDescriptiveAttributes) {
    if (this.hasDescriptiveAttributes != hasDescriptiveAttributes) {
      setChanged();
    }
    this.hasDescriptiveAttributes = hasDescriptiveAttributes;
  }
}
