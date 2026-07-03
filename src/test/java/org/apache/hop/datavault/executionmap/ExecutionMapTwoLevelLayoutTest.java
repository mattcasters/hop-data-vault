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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapTwoLevelLayoutTest {

  @Test
  void placesParentLeftOfChildren() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("root");
    List<ExecutionMapNode> visible = ExecutionMapViewFilter.getVisibleNodes(document, focus);
    var metrics = ExecutionMapTwoLevelLayout.defaultCardMetrics(visible);

    ExecutionMapTwoLevelLayout.layout(document, focus, metrics);

    ExecutionMapNode parent = document.findNodeById("root");
    ExecutionMapNode childA = document.findNodeById("child-a");
    ExecutionMapNode childB = document.findNodeById("child-b");

    assertTrue(parent.getLocation().x < childA.getLocation().x);
    assertTrue(parent.getLocation().x < childB.getLocation().x);
    assertTrue(childA.getLocation().y < childB.getLocation().y);
  }

  private static ExecutionMapDocument sampleDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document.getNodesOrEmpty().add(node("child-a", ExecutionMapNodeType.DV_UPDATE, "root"));
    document.getNodesOrEmpty().add(node("child-b", ExecutionMapNodeType.BV_UPDATE, "root"));
    return document;
  }

  private static ExecutionMapNode node(String id, ExecutionMapNodeType type, String parentId) {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setId(id);
    node.setName(id);
    node.setNodeType(type);
    node.setParentNodeId(parentId);
    return node;
  }
}