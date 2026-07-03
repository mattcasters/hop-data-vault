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

package org.apache.hop.datavault.executionmap;

import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Node fill colors for execution map painting. */
public final class ExecutionMapNodeColors {

  /** Dark amber fill for DM nodes; readable with light text in dark mode. */
  private static final int[] DM_FILL_RGB = {100, 62, 36};

  /** Dark sage fill for target dataset nodes (catalog records with physical tables). */
  private static final int[] TARGET_DATASET_FILL_RGB = {42, 72, 52};

  /** Dark slate fill for source dataset nodes. */
  private static final int[] SOURCE_DATASET_FILL_RGB = {42, 58, 78};

  private ExecutionMapNodeColors() {}

  public static int[] fillRgb(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return null;
    }
    return switch (nodeType) {
      case DM_UPDATE, DM_PUBLISH, DIMENSIONAL_MODEL -> DM_FILL_RGB;
      case TARGET_DATASET -> TARGET_DATASET_FILL_RGB;
      case SOURCE_DATASET -> SOURCE_DATASET_FILL_RGB;
      default -> null;
    };
  }

  public static void setFillBackground(IGc gc, ExecutionMapNode node) {
    if (gc == null) {
      return;
    }
    int[] rgb = node != null ? fillRgb(node.getNodeType()) : null;
    if (rgb != null) {
      gc.setBackground(rgb[0], rgb[1], rgb[2]);
      return;
    }
    gc.setBackground(resolveEnumFill(node));
  }

  public static EColor resolveEnumFill(ExecutionMapNode node) {
    if (node == null || node.getNodeType() == null) {
      return EColor.LIGHTGRAY;
    }
    return switch (node.getNodeType()) {
      case ROOT_WORKFLOW, WORKFLOW, ROOT_PIPELINE, PIPELINE -> EColor.BLUE;
      case DV_UPDATE, DATA_VAULT_MODEL -> EColor.GREEN;
      case BV_UPDATE, BUSINESS_VAULT_MODEL -> EColor.CRYSTAL;
      case DM_UPDATE, DM_PUBLISH, DIMENSIONAL_MODEL -> EColor.DARKGRAY;
      case PIPELINE_EXECUTOR, WORKFLOW_EXECUTOR, MAPPING, META_INJECT -> EColor.YELLOW;
      case GENERATED_PIPELINE, ORCHESTRATOR_PIPELINE, BULK_MASTER_WORKFLOW -> EColor.LIGHTGRAY;
      case SOURCE_DATASET -> EColor.LIGHTBLUE;
      case TARGET_DATASET -> EColor.GREEN;
      default -> EColor.WHITE;
    };
  }
}