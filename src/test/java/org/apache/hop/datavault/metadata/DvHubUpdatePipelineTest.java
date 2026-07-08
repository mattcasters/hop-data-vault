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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvHubUpdatePipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void hubSourceSqlUsesOnlyBusinessKeysForCurrentRecordSource() throws Exception {
    DvHub hub = multiSourceCustomerHub();
    DataVaultSource prefsSource = customerPrefsSource();

    PipelineMeta pipelineMeta = new PipelineMeta();
    DvDatabaseHubSourcePipelineBuilder builder =
        new DvDatabaseHubSourcePipelineBuilder(
            new Variables(),
            testMetadataProvider(),
            new DataVaultModel(),
            pipelineMeta,
            prefsSource,
            prefsSource.getDvSourceOrDefault(),
            hub,
            new Point(0, 0));
    builder.build();

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();

    String sql = sourceMeta.getSql().replace('\n', ' ');
    assertTrue(sql.startsWith("SELECT DISTINCT customer_id,"));
    assertTrue(sql.contains("'crm_customer_prefs'"));
    assertTrue(sql.contains("customer_prefs"));
    assertTrue(sql.contains("ORDER BY customer_id"));
    assertFalse(sql.contains("customer_id, customer_id"));
  }

  @Test
  void getBusinessKeysForSourceFiltersByRecordSourceName() {
    DvHub hub = multiSourceCustomerHub();

    assertEquals(1, hub.getBusinessKeysForSource("crm_customer_prefs", new Variables()).size());
    assertEquals(
        "customer_id",
        hub.getBusinessKeysForSource("crm_customer_prefs", new Variables()).get(0).getName());
    assertEquals(5, hub.getBusinessKeys().size());
    assertEquals(1, hub.getDistinctBusinessKeys().size());
  }

  private static DvHub multiSourceCustomerHub() {
    DvHub hub = new DvHub("hub_customer");
    hub.setHashKeyFieldName("customer_hk");
    List<BusinessKey> keys = new ArrayList<>();
    for (String sourceName :
        List.of(
            "crm_customer",
            "crm_customer_prefs",
            "crm_customer_address",
            "crm_customer_phone",
            "crm_customer_email")) {
      keys.add(businessKey("customer_id", "customer_id", sourceName));
    }
    hub.setBusinessKeys(keys);
    return hub;
  }

  private static DataVaultSource customerPrefsSource() {
    DataVaultSource source = new DataVaultSource("crm_customer_prefs");
    source.setSourceIndicator("crm_customer_prefs");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("public");
    dbSource.setTableName("customer_prefs");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    SourceField field = new SourceField();
    field.setName("customer_id");
    field.setSourceDataType("Integer");
    field.setHopType(IValueMeta.TYPE_INTEGER);
    fields.add(field);
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static BusinessKey businessKey(String name, String sourceField, String recordSource) {
    BusinessKey key = new BusinessKey(name);
    key.setSourceFieldName(sourceField);
    key.setRecordSourceName(recordSource);
    key.setDataType("Integer");
    return key;
  }

  private static MemoryMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta crm = new DatabaseMeta();
    crm.setName("CRM");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);
    return metadataProvider;
  }
}