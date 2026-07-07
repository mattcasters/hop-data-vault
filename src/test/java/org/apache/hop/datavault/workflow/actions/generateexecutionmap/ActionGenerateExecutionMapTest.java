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

package org.apache.hop.datavault.workflow.actions.generateexecutionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.executionmap.CrawlOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActionGenerateExecutionMapTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesConfiguredRootArtifactPath() throws HopException {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    assertEquals(
        "/workspace/retail-example/workflows/run-retail-update.hwf",
        ActionGenerateExecutionMap.resolveRootArtifactPath(
            "${PROJECT_HOME}/workflows/run-retail-update.hwf", null, variables));
  }

  @Test
  void buildsCrawlOptionsMatchingDialogDefaults() {
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();

    CrawlOptions options = action.toCrawlOptions();

    assertTrue(options.isIncludeGeneratedPipelines());
    assertTrue(options.isIncludeDatasetNodes());
    assertTrue(options.isCaptureSnapshots());
    assertTrue(options.isFollowNestedWorkflows());
    assertTrue(options.isFollowNestedPipelines());
    assertEquals(false, options.isIncludeWorkflowActions());
    assertEquals(false, options.isIncludePipelineTransforms());
  }

  @Test
  void failsWhenRootArtifactMissingAndNoParentWorkflow() {
    assertThrows(
        HopException.class,
        () -> ActionGenerateExecutionMap.resolveRootArtifactPath(null, null, new Variables()));
  }
}