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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Tracks the focused parent node for 2-level drill-down navigation. */
@Getter
@Setter
public class ExecutionMapFocusContext {

  /** Null means root focus (implicit root workflow/pipeline node). */
  private String focusNodeId;

  public ExecutionMapFocusContext() {}

  public ExecutionMapFocusContext(String focusNodeId) {
    this.focusNodeId = focusNodeId;
  }

  public ExecutionMapNode resolveFocusNode(ExecutionMapDocument document) {
    if (document == null) {
      return null;
    }
    if (!Utils.isEmpty(focusNodeId)) {
      ExecutionMapNode focused = document.findNodeById(focusNodeId);
      if (focused != null) {
        return focused;
      }
    }
    return findRootNode(document);
  }

  public List<ExecutionMapNode> getBreadcrumb(ExecutionMapDocument document) {
    List<ExecutionMapNode> breadcrumb = new ArrayList<>();
    ExecutionMapNode focus = resolveFocusNode(document);
    if (focus == null) {
      return breadcrumb;
    }
    List<ExecutionMapNode> ancestors = new ArrayList<>();
    ExecutionMapNode current = focus;
    while (current != null) {
      ancestors.add(0, current);
      String parentId = current.getParentNodeId();
      current =
          Utils.isEmpty(parentId) || document == null
              ? null
              : document.findNodeById(parentId);
    }
    breadcrumb.addAll(ancestors);
    return breadcrumb;
  }

  public boolean canDrillInto(ExecutionMapNode node, ExecutionMapDocument document) {
    if (node == null || document == null || Utils.isEmpty(node.getId())) {
      return false;
    }
    for (ExecutionMapNode candidate : document.getNodesOrEmpty()) {
      if (candidate != null && node.getId().equals(candidate.getParentNodeId())) {
        return true;
      }
    }
    return false;
  }

  public void drillInto(String nodeId) {
    if (!Utils.isEmpty(nodeId)) {
      focusNodeId = nodeId;
    }
  }

  public void navigateTo(String nodeId) {
    focusNodeId = Utils.isEmpty(nodeId) ? null : nodeId;
  }

  public void navigateToRoot() {
    focusNodeId = null;
  }

  public static ExecutionMapNode findRootNode(ExecutionMapDocument document) {
    if (document == null) {
      return null;
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || node.getNodeType() == null) {
        continue;
      }
      if (node.getNodeType() == ExecutionMapNodeType.ROOT_WORKFLOW
          || node.getNodeType() == ExecutionMapNodeType.ROOT_PIPELINE) {
        return node;
      }
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && Utils.isEmpty(node.getParentNodeId())) {
        return node;
      }
    }
    return document.getNodesOrEmpty().isEmpty() ? null : document.getNodesOrEmpty().get(0);
  }
}