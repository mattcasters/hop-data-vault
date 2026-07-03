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

package org.apache.hop.datavault.hopgui.file.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.executionmap.ArtifactSnapshotSupport;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactSnapshot;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecutionMapNavigationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void detectsSyntheticPaths() {
    assertTrue(ExecutionMapNavigationSupport.isSyntheticPath("generated://model/p1"));
    assertTrue(ExecutionMapNavigationSupport.isSyntheticPath("synthetic://orchestrator"));
    assertTrue(ExecutionMapNavigationSupport.isSyntheticPath("dataset://Vault/d_customer"));
    assertFalse(ExecutionMapNavigationSupport.isSyntheticPath("/tmp/workflow.hwf"));
  }

  @Test
  void resolvesVariablesInNodePath() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/project");
    assertEquals(
        "/project/models/retail.hdv",
        ExecutionMapNavigationSupport.resolvePath(
            variables, "${PROJECT_HOME}/models/retail.hdv"));
  }

  @Test
  void findsSnapshotAndBuildsTooltip() throws Exception {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapArtifactSnapshot snapshot = new ExecutionMapArtifactSnapshot();
    snapshot.setId("snap-1");
    snapshot.setArtifactType(ExecutionMapArtifactType.PIPELINE);
    snapshot.setSourcePath("generated://model/p1");
    snapshot.setXmlGzipBase64(
        ArtifactSnapshotSupport.encodeXml("<pipeline><name>p1</name></pipeline>"));
    document.getSnapshotsOrEmpty().add(snapshot);

    ExecutionMapNode node = new ExecutionMapNode();
    node.setName("p1");
    node.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    node.setPath("generated://model/p1");
    node.setSnapshotId("snap-1");

    assertNotNull(ExecutionMapNavigationSupport.findSnapshot(node, document));
    assertTrue(ExecutionMapNavigationSupport.canOpenFromSnapshot(node, document));
    assertFalse(ExecutionMapNavigationSupport.canOpenArtifactFile(node));
    assertTrue(ExecutionMapNavigationSupport.canNavigate(node, document));

    String tooltip = ExecutionMapNavigationSupport.buildTooltip(node, document);
    assertNotNull(tooltip);
    assertTrue(tooltip.contains("p1"));
    assertTrue(tooltip.contains("GENERATED_PIPELINE"));
    assertFalse(tooltip.isBlank());
  }
}