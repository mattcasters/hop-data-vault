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

package org.apache.hop.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.junit.jupiter.api.Test;

class MetadataMetricsResolverTest {

  @Test
  void aggregatesPipelineTotalsFromStampedTransformRoles() {
    List<TransformRunMetrics> transforms =
        List.of(
            TransformRunMetrics.builder()
                .transformName("source_f_orders")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ)
                .rowsRead(1000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("lookup_d_customer")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP)
                .rowsRead(5_000_000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("write_to_f_orders")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET)
                .rowsWritten(1000L)
                .build());

    MetadataMetricsResolver.AggregatedPipelineTotals totals =
        MetadataMetricsResolver.aggregateRoleTotals(transforms);

    assertEquals(1000L, totals.sourceRowsRead());
    assertEquals(0L, totals.targetRowsRead());
    assertEquals(1000L, totals.targetRowsInserted());
  }

  @Test
  void nameHeuristicExtractorStillHandlesBulkAndStagingWrites() {
    DvUpdateMetricsParser.ParsedPipeline identity =
        new DvUpdateMetricsParser.ParsedPipeline("hub", "customer", "crm");
    assertEquals("hub", identity.tableType());
    assertEquals("customer", identity.tableName());
    assertEquals(
        "bulk_load_to_customer", DvUpdateMetricsConstants.BULK_WRITE_TRANSFORM_PREFIX + "customer");
    assertEquals(
        "stage_to_customer", DvUpdateMetricsConstants.STAGING_WRITE_TRANSFORM_PREFIX + "customer");
  }
}