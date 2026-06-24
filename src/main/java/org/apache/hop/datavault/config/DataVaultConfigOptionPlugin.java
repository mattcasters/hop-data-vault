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

import java.util.Arrays;
import java.util.List;
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
import org.apache.hop.datavault.ai.DvAiProviderPreset;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
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
  private static final String WIDGET_ID_MAX_UNDO_OPERATIONS = "10010-max-undo-operations";
  private static final String WIDGET_ID_AI_ENABLED = "10200-ai-enabled";
  private static final String WIDGET_ID_AI_PROVIDER_PRESET = "10210-ai-provider-preset";
  private static final String WIDGET_ID_AI_API_KEY = "10220-ai-api-key";
  private static final String WIDGET_ID_AI_BASE_URL = "10230-ai-base-url";
  private static final String WIDGET_ID_AI_MODEL_NAME = "10240-ai-model-name";
  private static final String WIDGET_ID_AI_TEMPERATURE = "10250-ai-temperature";

  @GuiWidgetElement(
      id = WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.DrawHashKeysInModel.Message")
  @CommandLine.Option(
      names = {"--dv-draw-hash-keys"},
      description = "Enable or disable drawing hash keys in Data Vault models")
  private Boolean drawingHashKeysInModel;

  @GuiWidgetElement(
      id = WIDGET_ID_MAX_UNDO_OPERATIONS,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.MaxUndoOperations.Message")
  @CommandLine.Option(
      names = {"--dv-max-undo-operations"},
      description = "Maximum number of undo/redo snapshots kept in memory for Data Vault models")
  private String maxUndoOperations;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_ENABLED,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.AiEnabled.Message")
  @CommandLine.Option(
      names = {"--dv-ai-enabled"},
      description = "Enable AI advisory in the Data Vault modeler")
  private Boolean aiEnabled;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_PROVIDER_PRESET,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfigOptionPlugin.AiProviderPreset.Message",
      comboValuesMethod = "getAiProviderPresetOptions")
  @CommandLine.Option(
      names = {"--dv-ai-provider"},
      description = "AI provider preset for Data Vault advisory")
  private String aiProviderPreset;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_API_KEY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      password = true,
      label = "i18n::DataVaultConfigOptionPlugin.AiApiKey.Message")
  @CommandLine.Option(
      names = {"--dv-ai-api-key"},
      description = "API key for the Data Vault AI provider")
  private String aiApiKey;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_BASE_URL,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfigOptionPlugin.AiBaseUrl.Message")
  @CommandLine.Option(
      names = {"--dv-ai-base-url"},
      description = "Optional override of the AI provider base URL")
  private String aiBaseUrl;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_MODEL_NAME,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfigOptionPlugin.AiModelName.Message")
  @CommandLine.Option(
      names = {"--dv-ai-model-name"},
      description = "Optional override of the AI model name")
  private String aiModelName;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_TEMPERATURE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfigOptionPlugin.AiTemperature.Message")
  @CommandLine.Option(
      names = {"--dv-ai-temperature"},
      description = "Sampling temperature for Data Vault AI advisory")
  private String aiTemperature;

  public static DataVaultConfigOptionPlugin getInstance() {
    DataVaultConfigOptionPlugin instance = new DataVaultConfigOptionPlugin();

    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    instance.drawingHashKeysInModel = config.isDrawingHashKeysInModel();
    instance.maxUndoOperations = Integer.toString(config.getMaxUndoOperations());
    instance.aiEnabled = config.isAiEnabled();
    instance.aiProviderPreset = DvAiProviderPreset.resolve(config.getAiProviderPreset()).getLabel();
    instance.aiApiKey = config.getAiApiKey();
    instance.aiBaseUrl = config.getAiBaseUrl();
    instance.aiModelName = config.getAiModelName();
    instance.aiTemperature = config.getAiTemperature();
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
      if (maxUndoOperations != null) {
        config.setMaxUndoOperations(parseMaxUndoOperations(maxUndoOperations));
        log.logBasic(
            "Set maximum Data Vault undo/redo operations to " + config.getMaxUndoOperations());
        changed = true;
      }
      if (aiEnabled != null) {
        config.setAiEnabled(aiEnabled);
        log.logBasic(
            aiEnabled ? "Enabled Data Vault AI advisory" : "Disabled Data Vault AI advisory");
        changed = true;
      }
      if (aiProviderPreset != null) {
        config.setAiProviderPreset(DvAiProviderPreset.fromLabel(aiProviderPreset).name());
        changed = true;
      }
      if (aiApiKey != null) {
        config.setAiApiKey(aiApiKey);
        changed = true;
      }
      if (aiBaseUrl != null) {
        config.setAiBaseUrl(aiBaseUrl);
        changed = true;
      }
      if (aiModelName != null) {
        config.setAiModelName(aiModelName);
        changed = true;
      }
      if (aiTemperature != null) {
        config.setAiTemperature(aiTemperature);
        changed = true;
      }

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
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      switch (widgetId) {
        case WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL:
          drawingHashKeysInModel = ((Button) control).getSelection();
          config.setDrawingHashKeysInModel(drawingHashKeysInModel);
          break;
        case WIDGET_ID_MAX_UNDO_OPERATIONS:
          maxUndoOperations = getTextValue(control);
          config.setMaxUndoOperations(parseMaxUndoOperations(maxUndoOperations));
          break;
        case WIDGET_ID_AI_ENABLED:
          aiEnabled = ((Button) control).getSelection();
          config.setAiEnabled(aiEnabled);
          break;
        case WIDGET_ID_AI_PROVIDER_PRESET:
          aiProviderPreset = getComboText(control);
          config.setAiProviderPreset(DvAiProviderPreset.fromLabel(aiProviderPreset).name());
          break;
        case WIDGET_ID_AI_API_KEY:
          aiApiKey = getTextValue(control);
          config.setAiApiKey(aiApiKey);
          break;
        case WIDGET_ID_AI_BASE_URL:
          aiBaseUrl = getTextValue(control);
          config.setAiBaseUrl(aiBaseUrl);
          break;
        case WIDGET_ID_AI_MODEL_NAME:
          aiModelName = getTextValue(control);
          config.setAiModelName(aiModelName);
          break;
        case WIDGET_ID_AI_TEMPERATURE:
          aiTemperature = getTextValue(control);
          config.setAiTemperature(aiTemperature);
          break;
        default:
          break;
      }
    }
    try {
      DataVaultConfigSingleton.saveConfig();
    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(),
          BaseMessages.getString(
              PKG, "DataVaultConfigOptionPlugin.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(
              PKG, "DataVaultConfigOptionPlugin.SavingOption.ErrorDialog.Message"),
          e);
    }
  }

  private static String getTextValue(Control control) {
    if (control instanceof TextVar textVar) {
      return textVar.getText();
    }
    if (control instanceof Text text) {
      return text.getText();
    }
    throw new IllegalArgumentException(
        "Unsupported text control type: " + control.getClass().getName());
  }

  private static String getComboText(Control control) {
    if (control instanceof ComboVar comboVar) {
      return comboVar.getText();
    }
    if (control instanceof Combo combo) {
      return combo.getText();
    }
    throw new IllegalArgumentException(
        "Unsupported combo control type: " + control.getClass().getName());
  }

  /** Combo items for the AI provider preset widget. */
  public List<String> getAiProviderPresetOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(DvAiProviderPreset.labels());
  }

  private static int parseMaxUndoOperations(String value) {
    if (value == null || value.isBlank()) {
      return DataVaultConfig.DEFAULT_MAX_UNDO_OPERATIONS;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(1, parsed);
    } catch (NumberFormatException e) {
      return DataVaultConfig.DEFAULT_MAX_UNDO_OPERATIONS;
    }
  }
}