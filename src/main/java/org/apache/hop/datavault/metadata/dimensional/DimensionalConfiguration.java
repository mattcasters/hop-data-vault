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

package org.apache.hop.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/** Warehouse configuration for a dimensional model (scaffold). */
@GuiPlugin
@Getter
@Setter
public class DimensionalConfiguration extends HopMetadataBase implements IHopMetadata {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DIMENSIONAL_MODEL_DIALOG";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = org.apache.hop.core.database.DatabaseMeta.class,
      label = "i18n::DimensionalConfiguration.TargetDatabase.Label",
      toolTip = "i18n::DimensionalConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetDatabase;
}