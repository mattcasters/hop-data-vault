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

package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.businessvault.BvScd2PipelineSupport.Scd2BuildContext;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.analyticquery.AnalyticQueryMeta;
import org.apache.hop.pipeline.transforms.analyticquery.QueryField;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.ifnull.IfNullMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2PipelineSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveFunctionalTimestampPrefersExplicitField() {
    BvScd2Table table = new BvScd2Table();
    table.setFunctionalTimestampField("effective_date");
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    String resolved =
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            table, bvConfig, dvConfig, new Variables());

    assertEquals("effective_date", resolved);
  }

  @Test
  void resolveFunctionalTimestampUsesModelDefaultBeforeLoadDateFallback() {
    BvScd2Table table = new BvScd2Table();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    bvConfig.setFunctionalTimestampField("effective_date");
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    String resolved =
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            table, bvConfig, dvConfig, new Variables());

    assertEquals("effective_date", resolved);
  }

  @Test
  void resolveFunctionalTimestampFallsBackToDvLoadDate() {
    BvScd2Table table = new BvScd2Table();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    String resolved =
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            table, bvConfig, dvConfig, new Variables());

    assertEquals("x_load_ts", resolved);
  }

  @Test
  void resolveValidityFieldsUseModelDefaultsWhenTableOverridesEmpty() {
    BvScd2Table table = new BvScd2Table();
    BusinessVaultConfiguration bvConfig = new BusinessVaultConfiguration();
    bvConfig.setValidFromField("from_ts");
    bvConfig.setValidToField("to_ts");
    Variables variables = new Variables();

    assertEquals("from_ts", BvScd2PipelineSupport.resolveValidFromField(table, bvConfig, variables));
    assertEquals("to_ts", BvScd2PipelineSupport.resolveValidToField(table, bvConfig, variables));

    table.setValidFromField("custom_from");
    table.setValidToField("custom_to");
    assertEquals(
        "custom_from", BvScd2PipelineSupport.resolveValidFromField(table, bvConfig, variables));
    assertEquals("custom_to", BvScd2PipelineSupport.resolveValidToField(table, bvConfig, variables));
  }

  @Test
  void buildSatelliteTableInputSqlOrdersByGrainAndFunctionalTimestamp() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            new BusinessVaultModel(),
            dvModel,
            new BusinessVaultConfiguration(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(ctx);

    assertTrue(sql.startsWith("SELECT "));
    assertTrue(sql.contains("customer_hk"));
    assertTrue(sql.contains("x_record_source"));
    assertTrue(sql.contains("x_load_ts"));
    assertTrue(sql.contains("FROM "));
    assertTrue(sql.contains("sat_customer"));
    assertTrue(sql.contains("ORDER BY "));
    assertTrue(sql.indexOf("ORDER BY") < sql.lastIndexOf("x_load_ts"));
  }

  @Test
  void generatedPipelineContainsExpectedTransformChain() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setName("bv_customer_scd2");
    scd2Table.setTableName("bv_customer_scd2");
    scd2Table.setFunctionalTimestampField("x_load_ts");
    scd2Table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    Scd2BuildContext ctx =
        new Scd2BuildContext(
            scd2Table,
            satellite,
            bvModel,
            dvModel,
            bvModel.getConfigurationOrDefault(),
            dvModel.getConfigurationOrDefault(),
            null,
            new Variables(),
            databaseMeta,
            "Vault",
            databaseMeta,
            "Vault",
            "sat_customer",
            "bv_customer_scd2",
            "bv-scd2-bv_customer_scd2-sat_customer",
            "customer_hk",
            null,
            BvScd2PipelineSupport.resolveAttributeFieldNames(satellite),
            "x_load_ts",
            "valid_from",
            "valid_to",
            "x_record_source",
            BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
            BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
            true);

    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();

    assertEquals(5, transforms.size());
    assertTrue(transforms.get(0).getTransform() instanceof TableInputMeta);
    assertTrue(transforms.get(1).getTransform() instanceof AnalyticQueryMeta);

    AnalyticQueryMeta analyticQueryMeta = (AnalyticQueryMeta) transforms.get(1).getTransform();
    assertEquals(1, analyticQueryMeta.getGroupFields().size());
    assertEquals("customer_hk", analyticQueryMeta.getGroupFields().get(0).getFieldName());
    assertEquals(2, analyticQueryMeta.getQueryFields().size());
    assertEquals(QueryField.AggregateType.LAG, analyticQueryMeta.getQueryFields().get(0).getAggregateType());
    assertEquals("valid_from", analyticQueryMeta.getQueryFields().get(0).getAggregateField());
    assertEquals("x_load_ts", analyticQueryMeta.getQueryFields().get(0).getSubjectField());
    assertEquals(1, analyticQueryMeta.getQueryFields().get(0).getValueField());
    assertEquals(QueryField.AggregateType.LEAD, analyticQueryMeta.getQueryFields().get(1).getAggregateType());
    assertEquals("valid_to", analyticQueryMeta.getQueryFields().get(1).getAggregateField());
    assertEquals("x_load_ts", analyticQueryMeta.getQueryFields().get(1).getSubjectField());

    assertTrue(transforms.get(2).getTransform() instanceof IfNullMeta);
    IfNullMeta ifNullMeta = (IfNullMeta) transforms.get(2).getTransform();
    assertEquals(2, ifNullMeta.getFields().size());

    assertTrue(transforms.get(3).getTransform() instanceof GroupByMeta);
    GroupByMeta groupByMeta = (GroupByMeta) transforms.get(3).getTransform();
    assertEquals(4, groupByMeta.getAggregations().size());
    assertEquals("MIN", groupByMeta.getAggregations().get(0).getTypeLabel());
    assertEquals("MAX", groupByMeta.getAggregations().get(1).getTypeLabel());
    assertEquals("CONCAT_COMMA", groupByMeta.getAggregations().get(2).getTypeLabel());
    assertEquals("MAX", groupByMeta.getAggregations().get(3).getTypeLabel());
    assertEquals("valid_from", groupByMeta.getAggregations().get(0).getSubject());
    assertEquals("valid_to", groupByMeta.getAggregations().get(1).getSubject());
    assertEquals("x_record_source", groupByMeta.getAggregations().get(2).getField());
    assertEquals("x_load_ts", groupByMeta.getAggregations().get(3).getField());

    assertTrue(transforms.get(4).getTransform() instanceof TableOutputMeta);
    TableOutputMeta tableOutputMeta = (TableOutputMeta) transforms.get(4).getTransform();
    assertTrue(tableOutputMeta.isTruncateTable());
    assertEquals("bv_customer_scd2", tableOutputMeta.getTableName());
  }

  @Test
  void targetTableLayoutIncludesHashKeyAttributesAndValidityColumns() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setIncludeHashKey(true);

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            scd2Table, new BusinessVaultConfiguration(), dvModel, satellite, new Variables());

    assertEquals("customer_hk", layout.getValueMeta(0).getName());
    assertEquals("name", layout.getValueMeta(1).getName());
    assertEquals("x_record_source", layout.getValueMeta(layout.size() - 4).getName());
    assertEquals("x_load_ts", layout.getValueMeta(layout.size() - 3).getName());
    assertEquals("valid_from", layout.getValueMeta(layout.size() - 2).getName());
    assertEquals("valid_to", layout.getValueMeta(layout.size() - 1).getName());
    assertEquals(IValueMeta.TYPE_TIMESTAMP, layout.getValueMeta(layout.size() - 1).getType());
  }

  @Test
  void targetTableLayoutCanOmitHashKey() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer");

    BvScd2Table scd2Table = new BvScd2Table();
    scd2Table.setIncludeHashKey(false);

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            scd2Table, new BusinessVaultConfiguration(), dvModel, satellite, new Variables());

    assertFalse(layout.getValueMetaList().stream().anyMatch(vm -> "customer_hk".equals(vm.getName())));
    assertEquals("name", layout.getValueMeta(0).getName());
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    Path dvPath = Path.of("project/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document =
        XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}