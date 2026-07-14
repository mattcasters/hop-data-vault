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

package org.apache.hop.datavault.resourcedefinition;

import java.time.Instant;
import org.apache.hop.datavault.impact.ImpactGraph;

/** Outcome of a schema impact simulation run. */
public record SchemaImpactSimulationResult(
    ValidationReport validationReport,
    ImpactGraph impactGraph,
    String catalogVersionUsed,
    String baselineVersionUsed,
    SchemaCompareMode compareMode,
    Instant timestamp,
    SimulationStatus status) {

  public static SimulationStatus statusOf(ValidationReport report) {
    if (report == null) {
      return SimulationStatus.PASS;
    }
    if (report.hasBlockingIssues()) {
      return SimulationStatus.CRITICAL_BLOCKED;
    }
    if (report.getIssueCount() > 0) {
      return SimulationStatus.WARNING;
    }
    return SimulationStatus.PASS;
  }
}
