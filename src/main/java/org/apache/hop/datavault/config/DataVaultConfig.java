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
import org.apache.hop.datavault.layout.ElkLayout;

@Getter
@Setter
public class DataVaultConfig {

  public static final String HOP_CONFIG_DATA_VAULT_CONFIG_KEY = "dataVaultConfig";
  public static final int DEFAULT_MAX_UNDO_OPERATIONS = 200;

  private boolean drawingHashKeysInModel;
  private int maxUndoOperations = DEFAULT_MAX_UNDO_OPERATIONS;
  private ElkLayout elkLayout = ElkLayout.createDefault();
  private boolean suppressLocalCatalogOffer;

  /** Master switch for AI advisory in the Data Vault modeler. */
  private boolean aiEnabled;

  /** Provider preset: GROK, OPENAI, ANTHROPIC, OLLAMA, MISTRAL, HUGGING_FACE, CUSTOM. */
  private String aiProviderPreset = "GROK";

  /** API key for the selected LLM provider (stored in Hop config; never embedded in prompts). */
  private String aiApiKey;

  /** Optional override of the provider base URL (empty uses preset default). */
  private String aiBaseUrl;

  /** Optional override of the model name (empty uses preset default). */
  private String aiModelName;

  /** Sampling temperature for advisory requests (0.0–1.0 typical). */
  private String aiTemperature = "0.3";

  public DataVaultConfig() {
    drawingHashKeysInModel = true;
  }

  public DataVaultConfig(DataVaultConfig config) {
    this();
    drawingHashKeysInModel = config.drawingHashKeysInModel;
    suppressLocalCatalogOffer = config.suppressLocalCatalogOffer;
    aiEnabled = config.aiEnabled;
    aiProviderPreset = config.aiProviderPreset;
    aiApiKey = config.aiApiKey;
    aiBaseUrl = config.aiBaseUrl;
    aiModelName = config.aiModelName;
    aiTemperature = config.aiTemperature;
    setMaxUndoOperations(config.maxUndoOperations);
    setElkLayout(new ElkLayout(config.getElkLayout()));
  }

  public int getMaxUndoOperations() {
    return maxUndoOperations > 0 ? maxUndoOperations : DEFAULT_MAX_UNDO_OPERATIONS;
  }

  public void setMaxUndoOperations(int maxUndoOperations) {
    this.maxUndoOperations =
        maxUndoOperations > 0 ? maxUndoOperations : DEFAULT_MAX_UNDO_OPERATIONS;
  }

  public ElkLayout getElkLayout() {
    return elkLayout != null ? elkLayout : ElkLayout.createDefault();
  }

  public void setElkLayout(ElkLayout elkLayout) {
    this.elkLayout = elkLayout != null ? elkLayout : ElkLayout.createDefault();
  }
}