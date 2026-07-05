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

package org.apache.hop.catalog.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogRecordListFilter;
import org.junit.jupiter.api.Test;

class RecordDefinitionQueryOperationsFilterTest {

  @Test
  void loadMetricsFilterMatchesOperationsMetricsTables() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/operations", "load_run"));
    definition.getTags().addAll(List.of("operations", "load-metrics"));

    assertTrue(DataCatalogRecordListFilter.LOAD_METRICS.toQuery().matches(definition));
    assertTrue(DataCatalogRecordListFilter.OPERATIONS.toQuery().matches(definition));
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(definition));
  }

  @Test
  void loadMetricsFilterSkipsModelSourceDefinitions() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "CRM-customer"));
    definition.getTags().add("DV SOURCE");

    assertFalse(DataCatalogRecordListFilter.LOAD_METRICS.toQuery().matches(definition));
    assertFalse(DataCatalogRecordListFilter.OPERATIONS.toQuery().matches(definition));
    assertTrue(DataCatalogRecordListFilter.ALL.toQuery().matches(definition));
  }
}