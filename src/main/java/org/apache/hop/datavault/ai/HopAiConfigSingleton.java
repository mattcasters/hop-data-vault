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

package org.apache.hop.datavault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.hop.core.config.HopConfig;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.config.DataVaultConfig;

public class HopAiConfigSingleton {

  private static HopAiConfigSingleton instance;
  private HopAiConfig hopAiConfig;

  private HopAiConfigSingleton() {
    Object configObject =
        HopConfig.getInstance().getConfigMap().get(HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY);
    if (configObject == null) {
      hopAiConfig = new HopAiConfig();
      migrateFromLegacyDataVaultConfig(hopAiConfig);
    } else {
      try {
        ObjectMapper mapper = HopJson.newMapper();
        hopAiConfig = mapper.readValue(new Gson().toJson(configObject), HopAiConfig.class);
      } catch (Exception e) {
        LogChannel.GENERAL.logError(
            "Error reading AI assistant configuration, check property '"
                + HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY
                + "' in the Hop config json file",
            e);
        hopAiConfig = new HopAiConfig();
        migrateFromLegacyDataVaultConfig(hopAiConfig);
      }
    }
    HopConfig.getInstance().getConfigMap().put(HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY, hopAiConfig);
  }

  static void migrateFromLegacyDataVaultConfig(HopAiConfig target) {
    if (target == null) {
      return;
    }
    Object legacyObject =
        HopConfig.getInstance().getConfigMap().get(DataVaultConfig.HOP_CONFIG_DATA_VAULT_CONFIG_KEY);
    if (legacyObject == null) {
      return;
    }
    try {
      ObjectMapper mapper = HopJson.newMapper();
      DataVaultConfig legacy =
          mapper.readValue(new Gson().toJson(legacyObject), DataVaultConfig.class);
      migrateFromLegacyDataVaultConfig(target, legacy);
    } catch (Exception e) {
      LogChannel.GENERAL.logError("Unable to migrate AI settings from legacy Data Vault config", e);
    }
  }

  static void migrateFromLegacyDataVaultConfig(HopAiConfig target, DataVaultConfig legacy) {
    if (target == null || legacy == null) {
      return;
    }
    if (!target.isAiEnabled() && legacy.isAiEnabled()) {
      target.setAiEnabled(true);
    }
    if (Utils.isEmpty(target.getAiApiKey()) && !Utils.isEmpty(legacy.getAiApiKey())) {
      target.setAiApiKey(legacy.getAiApiKey());
    }
    if (Utils.isEmpty(target.getAiBaseUrl()) && !Utils.isEmpty(legacy.getAiBaseUrl())) {
      target.setAiBaseUrl(legacy.getAiBaseUrl());
    }
    if (Utils.isEmpty(target.getAiModelName()) && !Utils.isEmpty(legacy.getAiModelName())) {
      target.setAiModelName(legacy.getAiModelName());
    }
    if (Utils.isEmpty(target.getAiTemperature()) && !Utils.isEmpty(legacy.getAiTemperature())) {
      target.setAiTemperature(legacy.getAiTemperature());
    }
    if (Utils.isEmpty(target.getAiProviderPreset()) || "GROK".equals(target.getAiProviderPreset())) {
      if (!Utils.isEmpty(legacy.getAiProviderPreset())) {
        target.setAiProviderPreset(legacy.getAiProviderPreset());
      }
    }
  }

  public static HopAiConfig getConfig() {
    if (instance == null) {
      instance = new HopAiConfigSingleton();
    }
    return instance.hopAiConfig;
  }

  public static void saveConfig() throws HopException {
    if (instance == null) {
      instance = new HopAiConfigSingleton();
    }
    HopConfig.getInstance().saveOption(HopAiConfig.HOP_CONFIG_HOP_AI_CONFIG_KEY, instance.hopAiConfig);
    HopConfig.getInstance().saveToFile();
  }
}