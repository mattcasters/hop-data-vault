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

package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvTargetLoadSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void tableOutputModeAddsWriteTransform() throws Exception {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setTargetLoadMode(DvTargetLoadMode.TABLE_OUTPUT.getCode());
    config.setTargetTableParallelCopies("2");

    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("target-db");

    Variables variables = new Variables();
    DvTargetLoadSupport.TargetLoadContext ctx =
        new DvTargetLoadSupport.TargetLoadContext(
            config,
            variables,
            databaseMeta,
            "target-db",
            "hub_customer",
            "hub-customer-src",
            "vault1",
            400,
            200);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("hub-customer-src");
    TransformMeta predecessor =
        new TransformMeta("Constant", "add_LOAD_DATE", new ConstantMeta());

    IRowMeta layout = new RowMeta();
    layout.addValueMeta(new ValueMetaString("CUSTOMER_HK"));
    layout.addValueMeta(new ValueMetaString("flag"));

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            ctx, pipelineMeta, layout, predecessor, Set.of("flag"));

    assertEquals(DvTargetLoadMode.TABLE_OUTPUT, result.mode);
    assertNotNull(result.transformMeta);
    assertEquals("2", result.transformMeta.getCopiesString());
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(tm -> "write_to_hub_customer".equals(tm.getName())));

    TableOutputMeta tableOutputMeta =
        (TableOutputMeta) result.transformMeta.getTransform();
    assertEquals("hub_customer", tableOutputMeta.getTableName());
    assertEquals(1, tableOutputMeta.getFields().size());
    assertEquals("CUSTOMER_HK", tableOutputMeta.getFields().get(0).getFieldStream());
  }

  @Test
  void stagingFileModeAddsTextFileOutputWithParallelCopies() throws Exception {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setTargetLoadMode(DvTargetLoadMode.STAGING_FILE.getCode());
    config.setTargetTableParallelCopies("4");
    config.setBulkLoadStagingFolder("/tmp/dv2/bulk/");

    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("target-db");

    Variables variables = new Variables();
    DvTargetLoadSupport.TargetLoadContext ctx =
        new DvTargetLoadSupport.TargetLoadContext(
            config,
            variables,
            databaseMeta,
            "target-db",
            "hub_customer",
            "hub-customer-src",
            "vault1",
            400,
            200);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("hub-customer-src");
    TransformMeta predecessor =
        new TransformMeta("Constant", "add_LOAD_DATE", new ConstantMeta());
    predecessor.setLocation(new Point(100, 100));

    IRowMeta layout = new RowMeta();
    layout.addValueMeta(new ValueMetaString("CUSTOMER_HK"));

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            ctx, pipelineMeta, layout, predecessor, Set.of("flag"));

    assertEquals(DvTargetLoadMode.STAGING_FILE, result.mode);
    assertNotNull(result.transformMeta);
    assertEquals("4", result.transformMeta.getCopiesString());
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(tm -> "stage_to_hub_customer".equals(tm.getName())));
    assertTrue(
        result.stagingFilePattern.contains("hub-customer-src")
            && result.stagingFilePattern.contains(DvTargetLoadSupport.STAGING_FILE_COPY_VARIABLE));
  }

  @Test
  void nativeBulkModeAddsMysqlBulkLoaderWhenPluginInstalled() throws Exception {
    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID)) {
      return;
    }

    DatabaseMeta databaseMeta =
        new DatabaseMeta("mysql-test", "MySQL", "Native", "", "localhost", "test", "root", "");
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setTargetLoadMode(DvTargetLoadMode.NATIVE_BULK.getCode());
    config.setBulkLoadDelimiter(",");
    config.setBulkLoadEnclosure("\"");
    config.setBulkLoadEncoding("UTF-8");

    Variables variables = new Variables();
    DvTargetLoadSupport.TargetLoadContext ctx =
        new DvTargetLoadSupport.TargetLoadContext(
            config,
            variables,
            databaseMeta,
            "target-db",
            "hub_customer",
            "hub-customer-src",
            "vault1",
            400,
            200);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("hub-customer-src");
    TransformMeta predecessor =
        new TransformMeta("Constant", "add_LOAD_DATE", new ConstantMeta());
    predecessor.setLocation(new Point(100, 100));

    IRowMeta layout = new RowMeta();
    layout.addValueMeta(new ValueMetaString("CUSTOMER_HK"));

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            ctx, pipelineMeta, layout, predecessor, Set.of("flag"));

    assertEquals(DvTargetLoadMode.NATIVE_BULK, result.mode);
    assertNotNull(result.transformMeta);
    assertEquals("1", result.transformMeta.getCopiesString());
    assertEquals(
        DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID, result.transformMeta.getPluginId());
    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(tm -> "bulk_load_to_hub_customer".equals(tm.getName())));
  }
}