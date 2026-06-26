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
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BvDerivativeRef;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
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
        elkNode.setDimensions(node.getWidth(), node.getHeight());
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
}