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
 */

package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmDateRolePlayingPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void factPipelineResolvesBothDateAliasesToSamePhysicalDimension() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    DmFact fact = (DmFact) model.findTable("fact_orders");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);
    assertEquals(4, pipelineMeta.getTransforms().size());

    TransformMeta orderLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "lookup_OrderDate".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta orderLookupMeta = (DimensionLookupMeta) orderLookup.getTransform();
    assertEquals("d_date", orderLookupMeta.getTableName());
    assertEquals("order_date_key", orderLookupMeta.getFields().getReturns().getKeyRename());

    TransformMeta shipmentLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "lookup_ShipmentDate".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta shipmentLookupMeta = (DimensionLookupMeta) shipmentLookup.getTransform();
    assertEquals("d_date", shipmentLookupMeta.getTableName());
    assertEquals("shipment_date_key", shipmentLookupMeta.getFields().getReturns().getKeyRename());
    assertFalse(shipmentLookupMeta.isUpdate());
  }

  @Test
  void executionOrderSkipsDimensionAliases() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    List<IDmTable> ordered =
        DmUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables());

    assertEquals(2, ordered.size());
    assertEquals("dim_date", ordered.get(0).getName());
    assertEquals("fact_orders", ordered.get(1).getName());
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return metadataProvider;
  }

  private static DimensionalModel loadDateRolePlayingModel() throws Exception {
    Path fixture = Path.of("project/tests/basic/date-role-playing.hdm").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    return model;
  }
}