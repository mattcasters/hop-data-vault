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

package org.apache.hop.datavault.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BvTableBase;
import org.apache.hop.datavault.metadata.businessvault.BvDerivativeRef;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.datavault.executionmap.ExecutionMapLayoutOptions;
import org.apache.hop.datavault.executionmap.ExecutionMapMetrics;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.graph.ElkEdge;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

/**
 * Applies {@link ElkLayout} to an abstract graph of {@link ElkLayoutNode} nodes and {@link
 * ElkLayoutEdge} edges. Factory methods build graphs from Hop pipeline, workflow and Data Vault
 * model metadata.
 */
public final class ElkGraphLayout {

  private static final double VAULT_NODE_HEIGHT = 64;

  private final String graphName;
  private final List<ElkLayoutNode> nodes;
  private final List<ElkLayoutEdge> edges;

  private ElkGraphLayout(String graphName, List<ElkLayoutNode> nodes, List<ElkLayoutEdge> edges) {
    this.graphName = graphName;
    this.nodes = nodes;
    this.edges = edges;
  }

  /** Applies nested ELK layout to an execution map document. */
  public static void layoutExecutionMap(
      ExecutionMapDocument document, ExecutionMapLayoutOptions options, ElkLayout layout)
      throws HopException {
    if (document == null || document.getNodesOrEmpty().isEmpty() || layout == null || !layout.isEnabled()) {
      return;
    }
    ExecutionMapLayoutOptions layoutOptions =
        options != null ? options : ExecutionMapLayoutOptions.DEFAULT;

    Map<String, ExecutionMapNode> nodesById = new HashMap<>();
    List<ElkLayoutNode> layoutNodes = new ArrayList<>();
    Map<String, List<String>> childrenByParent = new HashMap<>();
    List<String> rootNodeIds = new ArrayList<>();

    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getId()) || Utils.isEmpty(node.getName())) {
        continue;
      }
      nodesById.put(node.getId(), node);
      layoutNodes.add(
          new ElkLayoutNode(
              node.getId(),
              node.getName(),
              ExecutionMapMetrics.NODE_WIDTH,
              ExecutionMapMetrics.NODE_HEIGHT,
              node));
    }

    for (ExecutionMapNode node : nodesById.values()) {
      String parentId = node.getParentNodeId();
      if (Utils.isEmpty(parentId) || !nodesById.containsKey(parentId)) {
        rootNodeIds.add(node.getId());
      } else {
        childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(node.getId());
      }
    }

    List<ElkLayoutEdge> layoutEdges = collectExecutionMapLayoutEdges(document, layoutOptions);

    try {
      ElkNode root = ElkGraphUtil.createGraph();
      layout.applyTo(root);

      Map<String, ElkNode> elkNodes = new HashMap<>();
      for (String rootNodeId : rootNodeIds) {
        buildExecutionMapElkNode(root, rootNodeId, nodesById, childrenByParent, elkNodes);
      }

      for (ElkLayoutEdge edge : layoutEdges) {
        ExecutionMapNode fromNode = nodesById.get(edge.getFromId());
        ExecutionMapNode toNode = nodesById.get(edge.getToId());
        if (isNestedLayoutEdge(fromNode, toNode)) {
          continue;
        }
        ElkNode from = elkNodes.get(edge.getFromId());
        ElkNode to = elkNodes.get(edge.getToId());
        if (from != null && to != null) {
          ElkEdge elkEdge = ElkGraphUtil.createSimpleEdge(from, to);
          ElkGraphUtil.updateContainment(elkEdge);
        }
      }

      new RecursiveGraphLayoutEngine().layout(root, new BasicProgressMonitor());
      applyNestedExecutionMapPositions(root, layoutNodes, layout, 0, 0);
    } catch (Exception e) {
      String name = document.getRootArtifactPath();
      throw new HopException(
          "Error applying ELK layout to execution map " + (name != null ? name : ""), e);
    }
  }

  private static boolean isNestedLayoutEdge(ExecutionMapNode from, ExecutionMapNode to) {
    if (from == null || to == null || Utils.isEmpty(from.getId())) {
      return false;
    }
    return from.getId().equals(to.getParentNodeId());
  }

  private static List<ElkLayoutEdge> collectExecutionMapLayoutEdges(
      ExecutionMapDocument document, ExecutionMapLayoutOptions options) {
    List<ElkLayoutEdge> edges = new ArrayList<>();
    Set<String> layoutEdgeKeys = new HashSet<>();
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null
          || Utils.isEmpty(edge.getFromNodeId())
          || Utils.isEmpty(edge.getToNodeId())) {
        continue;
      }
      if (!options.usesEdgeForLayout(edge.getEdgeType())) {
        continue;
      }
      String key = edge.getEdgeType().name() + ":" + edge.getFromNodeId() + "->" + edge.getToNodeId();
      if (layoutEdgeKeys.add(key)) {
        edges.add(new ElkLayoutEdge(edge.getFromNodeId(), edge.getToNodeId()));
      }
    }
    return edges;
  }

  private static void buildExecutionMapElkNode(
      ElkNode parent,
      String nodeId,
      Map<String, ExecutionMapNode> nodesById,
      Map<String, List<String>> childrenByParent,
      Map<String, ElkNode> elkNodes) {
    ExecutionMapNode node = nodesById.get(nodeId);
    if (node == null) {
      return;
    }
    ElkNode elkNode = ElkGraphUtil.createNode(parent);
    elkNode.setIdentifier(nodeId);
    elkNode.setDimensions(ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);
    ElkGraphUtil.createLabel(node.getName(), elkNode);

    List<String> children = childrenByParent.get(nodeId);
    if (children != null && !children.isEmpty()) {
      int padding = ExecutionMapMetrics.CONTAINER_PADDING;
      elkNode.setProperty(CoreOptions.PADDING, new ElkPadding(padding, padding, padding, padding));
      for (String childId : children) {
        buildExecutionMapElkNode(elkNode, childId, nodesById, childrenByParent, elkNodes);
      }
    }

    elkNodes.put(nodeId, elkNode);
  }

  private static void applyNestedExecutionMapPositions(
      ElkNode elkNode,
      List<ElkLayoutNode> layoutNodes,
      ElkLayout layout,
      double offsetX,
      double offsetY) {
    Map<String, ElkLayoutNode> layoutNodeById = new HashMap<>();
    for (ElkLayoutNode layoutNode : layoutNodes) {
      layoutNodeById.put(layoutNode.getId(), layoutNode);
    }
    applyNestedExecutionMapPositions(elkNode, layoutNodeById, layout, offsetX, offsetY);
  }

  private static void applyNestedExecutionMapPositions(
      ElkNode elkNode,
      Map<String, ElkLayoutNode> layoutNodeById,
      ElkLayout layout,
      double offsetX,
      double offsetY) {
    for (ElkNode child : elkNode.getChildren()) {
      String id = child.getIdentifier();
      ElkLayoutNode layoutNode = layoutNodeById.get(id);
      double childOffsetX = offsetX + child.getX();
      double childOffsetY = offsetY + child.getY();
      if (layoutNode != null && layoutNode.getTarget() != null) {
        int x = layout.snap((int) Math.round(childOffsetX) + layout.getOriginX());
        int y = layout.snap((int) Math.round(childOffsetY) + layout.getOriginY());
        layoutNode.getTarget().setLocation(x, y);
      }
      applyNestedExecutionMapPositions(child, layoutNodeById, layout, childOffsetX, childOffsetY);
    }
  }

  public static ElkGraphLayout fromPipeline(PipelineMeta pipelineMeta) {
    List<ElkLayoutNode> nodes = new ArrayList<>();
    List<ElkLayoutEdge> edges = new ArrayList<>();
    String name = pipelineMeta != null ? pipelineMeta.getName() : null;

    if (pipelineMeta != null) {
      ElkLayout defaults = ElkLayout.createDefault();
      for (TransformMeta transform : pipelineMeta.getTransforms()) {
        if (transform == null) {
          continue;
        }
        String transformName = transform.getName();
        if (Utils.isEmpty(transformName)) {
          continue;
        }
        nodes.add(
            new ElkLayoutNode(
                transformName,
                transformName,
                defaults.estimateNodeWidth(transformName),
                defaults.getNodeHeight(),
                transform));
      }

      for (PipelineHopMeta hop : pipelineMeta.getPipelineHops()) {
        if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
          continue;
        }
        String fromName = hop.getFromTransform().getName();
        String toName = hop.getToTransform().getName();
        if (!Utils.isEmpty(fromName) && !Utils.isEmpty(toName)) {
          edges.add(new ElkLayoutEdge(fromName, toName));
        }
      }
    }

    return new ElkGraphLayout(name, nodes, edges);
  }

  public static ElkGraphLayout fromWorkflow(WorkflowMeta workflowMeta) {
    List<ElkLayoutNode> nodes = new ArrayList<>();
    List<ElkLayoutEdge> edges = new ArrayList<>();
    String name = workflowMeta != null ? workflowMeta.getName() : null;

    if (workflowMeta != null) {
      ElkLayout defaults = ElkLayout.createDefault();
      for (ActionMeta action : workflowMeta.getActions()) {
        if (action == null) {
          continue;
        }
        String actionName = action.getName();
        if (Utils.isEmpty(actionName)) {
          continue;
        }
        nodes.add(
            new ElkLayoutNode(
                actionName,
                actionName,
                defaults.estimateNodeWidth(actionName),
                defaults.getNodeHeight(),
                action));
      }

      for (WorkflowHopMeta hop : workflowMeta.getWorkflowHops()) {
        if (hop == null || hop.getFromAction() == null || hop.getToAction() == null) {
          continue;
        }
        String fromName = hop.getFromAction().getName();
        String toName = hop.getToAction().getName();
        if (!Utils.isEmpty(fromName) && !Utils.isEmpty(toName)) {
          edges.add(new ElkLayoutEdge(fromName, toName));
        }
      }
    }

    return new ElkGraphLayout(name, nodes, edges);
  }

  public static ElkGraphLayout fromDataVaultModel(DataVaultModel model) {
    List<ElkLayoutNode> nodes = new ArrayList<>();
    List<ElkLayoutEdge> edges = new ArrayList<>();
    String name = model != null ? model.getName() : null;

    if (model != null && model.getTables() != null) {
      ElkLayout defaults = ElkLayout.createDefault();
      Map<String, IDvTable> tableByName = new HashMap<>();

      for (IDvTable table : model.getTables()) {
        if (table == null) {
          continue;
        }
        String tableName = table.getName();
        if (Utils.isEmpty(tableName)) {
          continue;
        }
        tableByName.put(tableName, table);
        nodes.add(
            new ElkLayoutNode(
                tableName,
                tableName,
                defaults.estimateNodeWidth(tableName),
                VAULT_NODE_HEIGHT,
                table));
      }

      for (IDvTable table : model.getTables()) {
        if (table == null) {
          continue;
        }
        if (table.getTableType() == DvTableType.LINK && table instanceof DvLink link) {
          for (String hubName : link.getHubNames()) {
            if (!Utils.isEmpty(hubName) && tableByName.containsKey(hubName)) {
              edges.add(new ElkLayoutEdge(hubName, link.getName()));
            }
          }
        }
        if (table.getTableType() == DvTableType.SATELLITE && table instanceof DvSatellite satellite) {
          String parentName = satellite.getHubName();
          if (Utils.isEmpty(parentName)) {
            parentName = satellite.getLinkName();
          }
          if (!Utils.isEmpty(parentName) && tableByName.containsKey(parentName)) {
            edges.add(new ElkLayoutEdge(parentName, satellite.getName()));
          }
        }
      }
    }

    return new ElkGraphLayout(name, nodes, edges);
  }

  public static ElkGraphLayout fromBusinessVaultModel(
      BusinessVaultModel businessVaultModel, DataVaultModel dataVaultModel) {
    List<ElkLayoutNode> nodes = new ArrayList<>();
    List<ElkLayoutEdge> edges = new ArrayList<>();
    String name = businessVaultModel != null ? businessVaultModel.getName() : null;

    if (businessVaultModel != null && businessVaultModel.getTables() != null) {
      ElkLayout defaults = ElkLayout.createDefault();
      Map<String, IDvTable> dvTableByName = new HashMap<>();
      if (dataVaultModel != null && dataVaultModel.getTables() != null) {
        for (IDvTable dvTable : dataVaultModel.getTables()) {
          if (dvTable != null && !Utils.isEmpty(dvTable.getName())) {
            dvTableByName.put(dvTable.getName(), dvTable);
          }
        }
      }

      for (IBvTable bvTable : businessVaultModel.getTables()) {
        if (bvTable == null || Utils.isEmpty(bvTable.getName())) {
          continue;
        }
        nodes.add(
            new ElkLayoutNode(
                bvTable.getName(),
                bvTable.getName(),
                defaults.estimateNodeWidth(bvTable.getName()),
                VAULT_NODE_HEIGHT,
                bvTable));

        for (BvDerivativeRef derivative : bvTable.getDerivatives()) {
          if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
            continue;
          }
          if (dvTableByName.containsKey(derivative.getDvTableName())) {
            edges.add(new ElkLayoutEdge(bvTable.getName(), derivative.getDvTableName()));
          }
        }
      }
    }

    return new ElkGraphLayout(name, nodes, edges);
  }

  public static ElkGraphLayout fromDimensionalModel(DimensionalModel dimensionalModel) {
    List<ElkLayoutNode> nodes = new ArrayList<>();
    List<ElkLayoutEdge> edges = new ArrayList<>();
    String name = dimensionalModel != null ? dimensionalModel.getName() : null;

    if (dimensionalModel != null && dimensionalModel.getTables() != null) {
      ElkLayout defaults = ElkLayout.createDefault();
      Map<String, IDmTable> tableByName = new HashMap<>();

      for (IDmTable table : dimensionalModel.getTables()) {
        if (table == null || Utils.isEmpty(table.getName())) {
          continue;
        }
        tableByName.put(table.getName(), table);
        nodes.add(
            new ElkLayoutNode(
                table.getName(),
                table.getName(),
                defaults.estimateNodeWidth(table.getName()),
                VAULT_NODE_HEIGHT,
                table));
      }

      for (IDmTable table : dimensionalModel.getTables()) {
        if (table == null) {
          continue;
        }
        if (table instanceof DmFact fact) {
          for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
            if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
              continue;
            }
            if (tableByName.containsKey(role.getDimensionTableName())) {
              edges.add(new ElkLayoutEdge(fact.getName(), role.getDimensionTableName()));
            }
          }
        }
        if (table instanceof DmDimension dimension) {
          for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
            if (outrigger == null || Utils.isEmpty(outrigger.getDimensionTableName())) {
              continue;
            }
            if (tableByName.containsKey(outrigger.getDimensionTableName())) {
              edges.add(new ElkLayoutEdge(dimension.getName(), outrigger.getDimensionTableName()));
            }
          }
        }
      }
    }

    return new ElkGraphLayout(name, nodes, edges);
  }

  public void layout(ElkLayout layout) throws HopException {
    if (layout == null || !layout.isEnabled() || nodes.isEmpty()) {
      return;
    }

    try {
      ElkNode root = ElkGraphUtil.createGraph();
      layout.applyTo(root);

      Map<String, ElkNode> elkNodes = new HashMap<>();
      for (ElkLayoutNode node : nodes) {
        ElkNode elkNode = ElkGraphUtil.createNode(root);
        elkNode.setIdentifier(node.getId());
        elkNode.setDimensions(resolveNodeWidth(node, layout), resolveNodeHeight(node, layout));
        ElkGraphUtil.createLabel(node.getLabel(), elkNode);
        elkNodes.put(node.getId(), elkNode);
      }

      if (layout.getAlgorithm() != ElkLayoutAlgorithm.RECT_PACKING) {
        for (ElkLayoutEdge edge : edges) {
          ElkNode from = elkNodes.get(edge.getFromId());
          ElkNode to = elkNodes.get(edge.getToId());
          if (from != null && to != null) {
            ElkGraphUtil.createSimpleEdge(from, to);
          }
        }
      }

      new RecursiveGraphLayoutEngine().layout(root, new BasicProgressMonitor());

      int originX = layout.getOriginX();
      int originY = layout.getOriginY();
      for (ElkLayoutNode node : nodes) {
        ElkNode elkNode = elkNodes.get(node.getId());
        if (elkNode == null || node.getTarget() == null) {
          continue;
        }
        int x = layout.snap((int) Math.round(elkNode.getX()) + originX);
        int y = layout.snap((int) Math.round(elkNode.getY()) + originY);
        node.getTarget().setLocation(x, y);
      }
    } catch (Exception e) {
      throw new HopException(
          "Error applying ELK layout to graph " + (graphName != null ? graphName : ""), e);
    }
  }

  private static double resolveNodeWidth(ElkLayoutNode node, ElkLayout layout) {
    IGuiPosition target = node.getTarget();
    if (target instanceof DvTableBase dvTable && dvTable.getDrawnBoxWidth() > 0) {
      return dvTable.getDrawnBoxWidth();
    }
    if (target instanceof BvTableBase bvTable && bvTable.getDrawnBoxWidth() > 0) {
      return bvTable.getDrawnBoxWidth();
    }
    if (target instanceof DmTableBase dmTable && dmTable.getDrawnBoxWidth() > 0) {
      return dmTable.getDrawnBoxWidth();
    }
    return layout.estimateNodeWidth(node.getLabel());
  }

  private static double resolveNodeHeight(ElkLayoutNode node, ElkLayout layout) {
    IGuiPosition target = node.getTarget();
    if (target instanceof DvTableBase dvTable && dvTable.getDrawnBoxHeight() > 0) {
      return dvTable.getDrawnBoxHeight();
    }
    if (target instanceof BvTableBase bvTable && bvTable.getDrawnBoxHeight() > 0) {
      return bvTable.getDrawnBoxHeight();
    }
    if (target instanceof DmTableBase dmTable && dmTable.getDrawnBoxHeight() > 0) {
      return dmTable.getDrawnBoxHeight();
    }
    if (layout.getAlgorithm() == ElkLayoutAlgorithm.RECT_PACKING
        && (target instanceof DvTableBase
            || target instanceof BvTableBase
            || target instanceof DmTableBase)) {
      return VAULT_NODE_HEIGHT;
    }
    return layout.getNodeHeight();
  }
}