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

package org.apache.hop.datavault.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.config.plugin.ConfigPlugin;
import org.apache.hop.core.config.plugin.IConfigOptions;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import picocli.CommandLine;

@ConfigPlugin(
    id = "DataVaultConfigOptionPlugin",
    description = "Configuration options for the data vault 2.0 plugin")
@GuiPlugin(
    description = "i18n::DataVaultConfig.Tab.Name" // label in options dialog
    )
@Getter
@Setter
public class DataVaultConfigOptionPlugin
    implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  protected static final Class<?> PKG = DataVaultConfigOptionPlugin.class;

  private static final String WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL = "10000-draw-hash-keys-in-model";

  @GuiWidgetElement(
      id = WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.DrawHashKeysInModel.Message")
  @CommandLine.Option(
          names = {"--dv-draw-hash-keys"},
          description = "Enable or disable the projects plugin")
  private Boolean drawingHashKeysInModel;

  /**
   * Gets instance
   *
   * @return value of instance
   */
  public static DataVaultConfigOptionPlugin getInstance() {
    DataVaultConfigOptionPlugin instance = new DataVaultConfigOptionPlugin();

    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    instance.drawingHashKeysInModel = config.isDrawingHashKeysInModel();
    return instance;
  }

  @Override
  public boolean handleOption(
      ILogChannel log, IHasHopMetadataProvider hasHopMetadataProvider, IVariables variables)
      throws HopException {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    try {
      boolean changed = false;
      if (drawingHashKeysInModel != null) {
        config.setDrawingHashKeysInModel(drawingHashKeysInModel);
        if (drawingHashKeysInModel) {
          log.logBasic("Enabled drawing hash keys in model");
        } else {
          log.logBasic("Disabled drawing hash keys in model");
        }
        changed = true;
      }

      // Save to file if anything changed
      //
      if (changed) {
        DataVaultConfigSingleton.saveConfig();
      }
      return changed;
    } catch (Exception e) {
      throw new HopException("Error handling data vault plugin configuration options", e);
    }
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {
    // Do nothing
  }

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {
    // Do nothing
  }

  @Override
  public void widgetModified(
      GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
    persistContents(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      switch (widgetId) {
        case WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL:
          drawingHashKeysInModel = ((Button) control).getSelection();
          DataVaultConfigSingleton.getConfig().setDrawingHashKeysInModel(drawingHashKeysInModel);
          break;
        default:
          break;
      }
    }
    // Save the project...
    //
    try {
      DataVaultConfigSingleton.saveConfig();
    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(),
          BaseMessages.getString(PKG, "DataVaultConfigOptionPlugin.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(PKG, "DataVaultConfigOptionPlugin.SavingOption.ErrorDialog.Message"),
          e);
    }
  }
}
