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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapViewFilterTest {

  @Test
  void visibleNodesIncludeParentAndDirectChildrenOnly() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("root");

    Set<String> visibleIds =
        ExecutionMapViewFilter.getVisibleNodes(document, focus).stream()
            .map(ExecutionMapNode::getId)
            .collect(Collectors.toSet());

    assertEquals(Set.of("root", "child", "sibling"), visibleIds);
    assertTrue(!visibleIds.contains("grandchild"));
  }

  @Test
  void visibleEdgesExcludeContainsAndDistantEdges() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("root");

    var visible =
        ExecutionMapViewFilter.getVisibleEdges(document, focus).stream()
            .map(ExecutionMapEdge::getEdgeType)
            .collect(Collectors.toSet());

    assertTrue(visible.contains(ExecutionMapEdgeType.EXECUTES));
    assertTrue(!visible.contains(ExecutionMapEdgeType.CONTAINS));
    assertTrue(!visible.contains(ExecutionMapEdgeType.MODEL_LINK));
  }

  private static ExecutionMapDocument sampleDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document.getNodesOrEmpty().add(node("child", ExecutionMapNodeType.DV_UPDATE, "root"));
    document.getNodesOrEmpty().add(node("sibling", ExecutionMapNodeType.BV_UPDATE, "root"));
    document.getNodesOrEmpty().add(node("grandchild", ExecutionMapNodeType.PIPELINE, "child"));

    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "root", "child"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.EXECUTES, "root", "child"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.MODEL_LINK, "child", "grandchild"));
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

  private static ExecutionMapEdge edge(ExecutionMapEdgeType type, String from, String to) {
    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setEdgeType(type);
    edge.setFromNodeId(from);
    edge.setToNodeId(to);
    return edge;
  }
}