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
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipelineDatasetResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesTableInputAndOutputTransforms() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(
            document, new Variables(), null, CrawlOptions.builder().build());

    ExecutionMapNode pipelineNode = new ExecutionMapNode();
    pipelineNode.setNodeType(ExecutionMapNodeType.PIPELINE);
    pipelineNode.setName("load");
    context.addNode(pipelineNode);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("load");

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection("CRM");
    tableInputMeta.setSql("SELECT * FROM public.customer");
    TransformMeta inputTransform = new TransformMeta("source customer", tableInputMeta);
    inputTransform.setLocation(100, 100);
    pipelineMeta.addTransform(inputTransform);

    TableOutputMeta tableOutputMeta = new TableOutputMeta();
    tableOutputMeta.setConnection("Vault");
    tableOutputMeta.setTableName("h_customer");
    TransformMeta outputTransform = new TransformMeta("target h_customer", tableOutputMeta);
    outputTransform.setLocation(300, 100);
    pipelineMeta.addTransform(outputTransform);

    PipelineDatasetResolver.resolvePipeline(context, pipelineNode.getId(), pipelineMeta);

    long sourceDatasets =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET)
            .count();
    long targetDatasets =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
            .count();
    assertEquals(1, sourceDatasets);
    assertEquals(1, targetDatasets);

    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.READS_FROM
                        && pipelineNode.getId().equals(edge.getToNodeId())));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.WRITES_TO
                        && pipelineNode.getId().equals(edge.getFromNodeId())));

    ExecutionMapNode sourceDataset =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET)
            .findFirst()
            .orElseThrow();
    assertEquals("dataset://CRM/customer", sourceDataset.getPath());
    assertEquals("DATABASE", sourceDataset.getProperty("datasetKind"));
  }
}