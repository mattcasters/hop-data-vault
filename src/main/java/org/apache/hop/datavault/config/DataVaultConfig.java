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
  public static final int DEFAULT_MODEL_GRAPH_SPLINE_SEGMENTS = 20;

  private boolean drawingHashKeysInModel;
  private int maxUndoOperations = DEFAULT_MAX_UNDO_OPERATIONS;
  private int modelGraphSplineSegments = DEFAULT_MODEL_GRAPH_SPLINE_SEGMENTS;
  private ElkLayout elkLayout = ElkLayout.createDefault();
  private boolean suppressLocalCatalogOffer;

  /** @deprecated Migrated to {@link org.apache.hop.datavault.ai.HopAiConfig}; kept for JSON migration. */
  @Deprecated private boolean aiEnabled;

  /** @deprecated Migrated to HopAiConfig. */
  @Deprecated private String aiProviderPreset = "GROK";

  /** @deprecated Migrated to HopAiConfig. */
  @Deprecated private String aiApiKey;

  /** @deprecated Migrated to HopAiConfig. */
  @Deprecated private String aiBaseUrl;

  /** @deprecated Migrated to HopAiConfig. */
  @Deprecated private String aiModelName;

  /** @deprecated Migrated to HopAiConfig. */
  @Deprecated private String aiTemperature = "0.3";

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
    setModelGraphSplineSegments(config.modelGraphSplineSegments);
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

  public int getModelGraphSplineSegments() {
    return modelGraphSplineSegments > 0
        ? modelGraphSplineSegments
        : DEFAULT_MODEL_GRAPH_SPLINE_SEGMENTS;
  }

  public void setModelGraphSplineSegments(int modelGraphSplineSegments) {
    this.modelGraphSplineSegments =
        modelGraphSplineSegments > 0
            ? modelGraphSplineSegments
            : DEFAULT_MODEL_GRAPH_SPLINE_SEGMENTS;
  }
}