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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.config.DataVaultConfig;
import org.junit.jupiter.api.Test;

class HopAiConfigSingletonTest {

  @Test
  void migrateFromLegacyDataVaultConfigCopiesAiSettings() {
    HopAiConfig target = new HopAiConfig();
    target.setAiTemperature(null);
    target.setAiProviderPreset(null);

    DataVaultConfig legacy = new DataVaultConfig();
    legacy.setAiEnabled(true);
    legacy.setAiApiKey("legacy-key");
    legacy.setAiBaseUrl("https://legacy.example/v1");
    legacy.setAiModelName("legacy-model");
    legacy.setAiTemperature("0.7");
    legacy.setAiProviderPreset("OPENAI");

    HopAiConfigSingleton.migrateFromLegacyDataVaultConfig(target, legacy);

    assertTrue(target.isAiEnabled());
    assertEquals("legacy-key", target.getAiApiKey());
    assertEquals("https://legacy.example/v1", target.getAiBaseUrl());
    assertEquals("legacy-model", target.getAiModelName());
    assertEquals("0.7", target.getAiTemperature());
    assertEquals("OPENAI", target.getAiProviderPreset());
  }

  @Test
  void migrateDoesNotOverwriteExistingHopAiValues() {
    HopAiConfig target = new HopAiConfig();
    target.setAiApiKey("existing-key");
    target.setAiProviderPreset("ANTHROPIC");

    DataVaultConfig legacy = new DataVaultConfig();
    legacy.setAiApiKey("legacy-key");
    legacy.setAiProviderPreset("OPENAI");

    HopAiConfigSingleton.migrateFromLegacyDataVaultConfig(target, legacy);

    assertEquals("existing-key", target.getAiApiKey());
    assertEquals("ANTHROPIC", target.getAiProviderPreset());
  }
}