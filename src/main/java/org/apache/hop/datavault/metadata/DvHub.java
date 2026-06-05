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
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;

/**
 * Data Vault 2.0 Hub metadata definition.
 *
 * <p>A Hub represents a core business concept (e.g. Customer, Product, Order).
 * It contains the business key(s) and the surrogate hash key derived from them.
 */
@GuiPlugin
public class DvHub extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_HUB_DIALOG";

  /**
   * The business keys that uniquely identify the hub instance.
   * Composite keys are supported by having multiple entries (order matters for hashing).
   */
  // List of business keys. The @GuiWidgetElement annotation is omitted because
  // GuiElementType does not (currently) include a LIST type. Nested POJO lists are
  // supported via @HopMetadataProperty and will be handled by the metadata editor / serialization.
  @HopMetadataProperty
  private List<BusinessKey> businessKeys = new ArrayList<>();

  public DvHub() {
    super();
    this.tableType = DvTableType.HUB;
  }

  public DvHub(String name) {
    super(name);
    this.tableType = DvTableType.HUB;
  }

  public List<BusinessKey> getBusinessKeys() {
    return businessKeys;
  }

  public void setBusinessKeys(List<BusinessKey> businessKeys) {
    if (!java.util.Objects.equals(this.businessKeys, businessKeys)) {
      setChanged();
    }
    this.businessKeys = businessKeys;
  }
}
