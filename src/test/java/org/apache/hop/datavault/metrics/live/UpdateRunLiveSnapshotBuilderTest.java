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

package org.apache.hop.datavault.metrics.live;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateRunLiveSnapshotBuilderTest {

  @Test
  void usesFallbackElementWhenNoPipelineMetrics() {
    UpdateRunLiveRunContext context =
        UpdateRunLiveRunContext.builder()
            .metricsRunId("run-1")
            .modelName("retail-f-orders")
            .workflowFilename("/tmp/update-retail.hwf")
            .workflowName("update-retail")
            .actionName("retail-f-orders.hdm")
            .startedAt(new Date())
            .build();

    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshotBuilder.build(
            context,
            new UpdateRunLiveStallDetector(),
            List.of(),
            false,
            0L,
            "f_orders");

    assertEquals("f_orders", snapshot.getCurrentElementName());
    assertEquals(UpdateRunLiveState.RUNNING, snapshot.getOverallState());
  }
}