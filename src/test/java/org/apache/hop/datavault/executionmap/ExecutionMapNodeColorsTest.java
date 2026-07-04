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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapNodeColorsTest {

  @Test
  void dataVaultModelNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DATA_VAULT_MODEL));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DV_UPDATE));
  }

  @Test
  void dimensionalModelNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DIMENSIONAL_MODEL));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DM_UPDATE));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DM_PUBLISH));
  }

  @Test
  void datasetNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.TARGET_DATASET));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.SOURCE_DATASET));
  }

  @Test
  void workflowNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.WORKFLOW));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.ROOT_WORKFLOW));
  }

  @Test
  void pipelineNodesUseEnumFillOnly() {
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.PIPELINE));
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.ROOT_PIPELINE));
  }
}