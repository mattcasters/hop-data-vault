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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.executionmap.ExecutionMapLayoutOptions;
import org.apache.hop.datavault.executionmap.ExecutionMapMetrics;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;

/** Paints a read-only execution map graph. */
public class ExecutionMapPainter extends BasePainter {

  private static final int NODE_WIDTH = ExecutionMapMetrics.NODE_WIDTH;
  private static final int NODE_HEIGHT = ExecutionMapMetrics.NODE_HEIGHT;
  private static final int CONTAINER_BOUNDS_PADDING = 12;

  private final ExecutionMapDocument document;
  private final ExecutionMapLayoutOptions layoutOptions;
  private final Map<String, ExecutionMapNode> nodeById = new HashMap<>();
  private final Map<String, List<ExecutionMapNode>> childrenByParent = new HashMap<>();

  public ExecutionMapPainter(
      ExecutionMapDocument document, IGc gc, IVariables variables, int width, int height) {
    this(document, gc, variables, width, height, ExecutionMapLayoutOptions.DEFAULT);
  }

  public ExecutionMapPainter(
      ExecutionMapDocument document,
      IGc gc,
      IVariables variables,
      int width,
      int height,
      ExecutionMapLayoutOptions layoutOptions) {
    super(gc, variables, document, new Point(width, height));
    this.document = document;
    this.layoutOptions = layoutOptions != null ? layoutOptions : ExecutionMapLayoutOptions.DEFAULT;
  }

  public void drawExecutionMap() {
    if (document == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }
    nodeById.clear();
    childrenByParent.clear();
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && !Utils.isEmpty(node.getId())) {
        nodeById.put(node.getId(), node);
      }
    }
    for (ExecutionMapNode node : nodeById.values()) {
      if (Utils.isEmpty(node.getParentNodeId()) || !nodeById.containsKey(node.getParentNodeId())) {
        continue;
      }
      childrenByParent.computeIfAbsent(node.getParentNodeId(), ignored -> new ArrayList<>()).add(node);
    }

    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    // This does NOT translate with offset because it's been disabled in IGc.
    //
    gc.setTransform(0.0f, 0.0f, magnification);
    if (gridSize > 1) {
      drawGrid();
    }
    drawContainerBounds();
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS) {
        continue;
      }
      if (!layoutOptions.usesEdgeForLayout(edge.getEdgeType())) {
        drawEdge(edge, false);
      }
    }
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS) {
        continue;
      }
      if (layoutOptions.usesEdgeForLayout(edge.getEdgeType())) {
        drawEdge(edge, true);
      }
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      drawNode(node);
    }

    gc.setTransform(0.0f, 0.0f, 1.0f);
    drawNavigationView();
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (document == null) {
      return;
    }
    int minSize = 4;
    int w = Math.max(minSize, (int) Math.ceil(NODE_WIDTH * scaleX));
    int h = Math.max(minSize, (int) Math.ceil(NODE_HEIGHT * scaleY));
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || node.getLocation() == null) {
        continue;
      }
      int x = (int) (graphX + node.getLocation().x * scaleX);
      int y = (int) (graphY + node.getLocation().y * scaleY);
      gc.setBackground(resolveNodeColor(node));
      gc.setForeground(EColor.DARKGRAY);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
  }

  private Point nodeScreenLocation(ExecutionMapNode node) {
    Point loc = node.getLocation();
    return real2screen(loc.x, loc.y);
  }

  private void drawNode(ExecutionMapNode node) {
    if (node == null || node.getLocation() == null) {
      return;
    }
    Point screenLoc = nodeScreenLocation(node);
    int x = screenLoc.x;
    int y = screenLoc.y;
    EColor fill = resolveNodeColor(node);
    gc.setLineWidth(1);
    gc.setBackground(fill);
    gc.setForeground(EColor.CRYSTAL);
    gc.fillRoundRectangle(x, y, NODE_WIDTH, NODE_HEIGHT, 8, 8);
    gc.drawRoundRectangle(x, y, NODE_WIDTH, NODE_HEIGHT, 8, 8);
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.BLACK);
    String title = node.getName() != null ? node.getName() : node.getId();
    gc.drawText(title, x + 8, y + 8, true);
    String secondary = !Utils.isEmpty(node.getPluginId()) ? node.getPluginId() : null;
    if (secondary == null && node.getNodeType() != null) {
      secondary = node.getNodeType().name();
    }
    if (!Utils.isEmpty(node.getSnapshotId())) {
      secondary =
          secondary == null
              ? "snapshot"
              : secondary + " • snapshot";
    }
    if (!Utils.isEmpty(secondary)) {
      gc.drawText(secondary, x + 8, y + 24, true);
    }
    if (areaOwners != null) {
      areaOwners.add(
          new AreaOwner(AreaType.TRANSFORM_ICON, x, y, NODE_WIDTH, NODE_HEIGHT, offset, node, null));
    }
  }

  private void drawContainerBounds() {
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getId())) {
        continue;
      }
      List<ExecutionMapNode> children = childrenByParent.get(node.getId());
      if (children == null || children.isEmpty()) {
        continue;
      }
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      if (node.getLocation() != null) {
        Point screenLoc = nodeScreenLocation(node);
        minX = screenLoc.x;
        minY = screenLoc.y;
        maxX = screenLoc.x + NODE_WIDTH;
        maxY = screenLoc.y + NODE_HEIGHT;
      }
      for (ExecutionMapNode child : children) {
        if (child == null || child.getLocation() == null) {
          continue;
        }
        Point childLoc = nodeScreenLocation(child);
        minX = Math.min(minX, childLoc.x);
        minY = Math.min(minY, childLoc.y);
        maxX = Math.max(maxX, childLoc.x + NODE_WIDTH);
        maxY = Math.max(maxY, childLoc.y + NODE_HEIGHT);
      }
      if (minX == Integer.MAX_VALUE) {
        continue;
      }
      int pad = CONTAINER_BOUNDS_PADDING;
      int x = minX - pad;
      int y = minY - pad;
      int w = maxX - minX + pad * 2;
      int h = maxY - minY + pad * 2;
      gc.setLineWidth(1);
      gc.setLineStyle(ELineStyle.DOT);
      gc.setForeground(EColor.LIGHTGRAY);
      gc.setBackground(EColor.BACKGROUND);
      gc.drawRoundRectangle(x, y, w, h, 12, 12);
    }
  }

  private void drawEdge(ExecutionMapEdge edge, boolean flowEdge) {
    if (edge == null) {
      return;
    }
    ExecutionMapNode from = nodeById.get(edge.getFromNodeId());
    ExecutionMapNode to = nodeById.get(edge.getToNodeId());
    if (from == null || to == null || from.getLocation() == null || to.getLocation() == null) {
      return;
    }
    Point fromLoc = nodeScreenLocation(from);
    Point toLoc = nodeScreenLocation(to);
    int x1;
    int y1;
    int x2;
    int y2;
    if (flowEdge) {
      x1 = fromLoc.x + NODE_WIDTH;
      y1 = fromLoc.y + NODE_HEIGHT / 2;
      x2 = toLoc.x;
      y2 = toLoc.y + NODE_HEIGHT / 2;
    } else {
      int offset = resolveReferenceEdgeOffset(edge.getEdgeType());
      x1 = fromLoc.x + NODE_WIDTH / 2;
      y1 = fromLoc.y + NODE_HEIGHT + offset;
      x2 = toLoc.x + NODE_WIDTH / 2;
      y2 = toLoc.y - offset;
    }
    gc.setLineWidth(1);
    gc.setLineStyle(flowEdge ? ELineStyle.SOLID : ELineStyle.DASH);
    gc.setForeground(resolveEdgeColor(edge.getEdgeType(), flowEdge));
    ModelGraphConnectionGeometry.drawConnectionSpline(
        gc,
        ModelGraphConnectionGeometry.pointBounds(x1, y1),
        ModelGraphConnectionGeometry.pointBounds(x2, y2));
  }

  private static int resolveReferenceEdgeOffset(ExecutionMapEdgeType edgeType) {
    if (edgeType == null) {
      return 4;
    }
    return switch (edgeType) {
      case READS_FROM, WRITES_TO, PIPELINE_SOURCE -> 8;
      case REFERENCES, GENERATES, MODEL_LINK -> 4;
      default -> 4;
    };
  }

  private static EColor resolveEdgeColor(ExecutionMapEdgeType edgeType, boolean flowEdge) {
    if (flowEdge) {
      return EColor.CRYSTAL;
    }
    if (edgeType == ExecutionMapEdgeType.READS_FROM
        || edgeType == ExecutionMapEdgeType.WRITES_TO
        || edgeType == ExecutionMapEdgeType.PIPELINE_SOURCE) {
      return EColor.LIGHTBLUE;
    }
    return EColor.GRAY;
  }

  private static EColor resolveNodeColor(ExecutionMapNode node) {
    if (node.getNodeType() == null) {
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