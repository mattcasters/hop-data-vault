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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmPipelineBuilderSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmScd2DimensionPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void type2CustomerDimensionBuildsVersionIncrementAndCloseUpdate() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DmDimension dimension = new DmDimension();
    dimension.setName("d_customer");
    dimension.setTableName("d_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField("customer_hk");
    dimension.setSurrogateKeySourceField("customer_hk");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension
        .getAttributes()
        .add(new DmDimensionAttribute("cust_segment", DmScdUpdatePolicy.TYPE2));
    dimension
        .getSourceOrDefault()
        .setSourceSql(
            "SELECT hc.customer_id, sc.* FROM hub_customer hc JOIN customer_360_bv sc ON hc.customer_hk = sc.customer_hk");
    model.getTables().add(dimension);

    PipelineMeta pipeline =
        dimension
            .generateUpdatePipelines(testMetadataProvider(), new Variables(), model, new Date())
            .get(0);

    assertEquals("dm-dim-d_customer", pipeline.getName());
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof CalculatorMeta));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("close_d_customer")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("select_merge_compare")));
    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getName().equals("select_merge_reference")));

    ConstantMeta effectiveDates =
        (ConstantMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals("add_effective_dates"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(
        effectiveDates.getFields().stream()
            .anyMatch(
                field ->
                    "version".equals(field.getFieldName())
                        && "Integer".equals(field.getFieldType())
                        && "0".equals(field.getValue())
                        && field.getFieldLength() == 9));

    List<String> expectedMergeFields =
        List.of(
            "customer_hk",
            "cust_segment",
            "customer_id",
            "version",
            "date_from",
            "date_to");
    assertEquals(
        expectedMergeFields, selectOutputFieldNames(pipeline, "select_merge_compare"));
    assertEquals(
        expectedMergeFields, selectOutputFieldNames(pipeline, "select_merge_reference"));
    assertEquals(6, selectOutputFieldNames(pipeline, "select_merge_compare").size());

    TableInputMeta targetInput =
        (TableInputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals("target_d_customer"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(targetInput.getSql().contains("version"));

    TableOutputMeta tableOutput =
        (TableOutputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof TableOutputMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    Set<String> outputFields =
        tableOutput.getFields().stream()
            .map(field -> field.getFieldDatabase())
            .collect(Collectors.toSet());
    assertTrue(outputFields.contains("version"));
    assertTrue(outputFields.contains("is_current"));
    assertFalse(outputFields.contains("flag"));
    assertFalse(outputFields.contains(DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD));
    assertFalse(outputFields.contains(DmPipelineBuilderSupport.PREVIOUS_VERSION_NUM_FIELD));

    TransformMeta closeTransform =
        pipeline.getTransforms().stream()
            .filter(t -> t.getName().equals("close_d_customer"))
            .findFirst()
            .orElseThrow();
    UpdateMeta updateMeta = (UpdateMeta) closeTransform.getTransform();
    assertNotNull(updateMeta.getCommitSizeVar());
    assertFalse(Utils.isEmpty(updateMeta.getCommitSizeVar()));
    assertEquals("Vault", updateMeta.getConnection());
    assertEquals("", updateMeta.getLookupField().getSchemaName());
    assertEquals("d_customer", updateMeta.getLookupField().getTableName());
    assertTrue(
        updateMeta.getLookupField().getLookupKeys().stream()
            .anyMatch(
                key ->
                    DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD.equals(key.getKeyStream())
                        && "date_from".equals(key.getKeyLookup())));
    assertEquals(1, updateMeta.getLookupField().getUpdateFields().size());
    assertEquals("date_to", updateMeta.getLookupField().getUpdateFields().get(0).getUpdateLookup());
    assertEquals("date_from", updateMeta.getLookupField().getUpdateFields().get(0).getUpdateStream());
  }

  private static List<String> selectOutputFieldNames(PipelineMeta pipeline, String transformName) {
    SelectValuesMeta selectMeta =
        (SelectValuesMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().equals(transformName))
                .findFirst()
                .orElseThrow()
                .getTransform();
    return selectMeta.getSelectOption().getSelectFields().stream()
        .map(
            field ->
                Utils.isEmpty(field.getRename()) ? field.getName() : field.getRename())
        .toList();
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return metadataProvider;
  }
}