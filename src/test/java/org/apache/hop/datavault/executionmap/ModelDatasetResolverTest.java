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

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelDatasetResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveDimensionalModelAddsTargetDatasetNodes() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(
            document, new Variables(), null, CrawlOptions.builder().build());

    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setNodeType(ExecutionMapNodeType.DIMENSIONAL_MODEL);
    modelNode.setName("retail-dm");
    context.addNode(modelNode);

    DimensionalModel model = new DimensionalModel();
    model.setName("retail-dm");
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setTableName("d_customer");
    model.getTables().add(dimension);

    ModelDatasetResolver.resolveDimensionalModel(context, modelNode.getId(), model);

    ExecutionMapNode dataset =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
            .findFirst()
            .orElseThrow();
    assertEquals("dataset://Vault/d_customer", dataset.getPath());
    assertEquals("Vault.d_customer", dataset.getName());
    assertEquals("DATABASE", dataset.getProperty("datasetKind"));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS
                        && modelNode.getId().equals(edge.getFromNodeId())
                        && dataset.getId().equals(edge.getToNodeId())));
  }

  @Test
  void skipsDatasetNodesWhenOptionDisabled() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(
            document,
            new Variables(),
            null,
            CrawlOptions.builder().includeDatasetNodes(false).build());

    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setNodeType(ExecutionMapNodeType.DIMENSIONAL_MODEL);
    context.addNode(modelNode);

    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setTableName("d_customer");
    model.getTables().add(dimension);

    ModelDatasetResolver.resolveDimensionalModel(context, modelNode.getId(), model);

    assertTrue(
        document.getNodesOrEmpty().stream()
            .noneMatch(node -> node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET));
  }
}