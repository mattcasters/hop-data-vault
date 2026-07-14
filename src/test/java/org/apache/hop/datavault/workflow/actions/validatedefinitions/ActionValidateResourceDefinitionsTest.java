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

package org.apache.hop.datavault.workflow.actions.validatedefinitions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.resourcedefinition.SchemaCompareMode;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationFailureSeverity;
import org.apache.hop.datavault.resourcedefinition.SchemaValidationReportFileWriter;
import org.junit.jupiter.api.Test;

class ActionValidateResourceDefinitionsTest {

  @Test
  void defaultsAndComboOptions() {
    ActionValidateResourceDefinitions action = new ActionValidateResourceDefinitions();
    assertEquals(SchemaCompareMode.LIVE_SOURCE.name(), action.getCompareMode());
    assertEquals(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name(), action.getFailureSeverity());
    assertEquals(
        SchemaValidationReportFileWriter.ReportFormat.MARKDOWN.name(), action.getReportFormat());
    assertTrue(action.isIncludeImpact());
    assertTrue(action.isEvaluation());

    assertArrayEquals(
        new String[] {
          SchemaCompareMode.LIVE_SOURCE.name(),
          SchemaCompareMode.WORKING_VS_VERSION.name(),
          SchemaCompareMode.VERSION_VS_VERSION.name()
        },
        action.getCompareModeOptions());
    assertArrayEquals(
        new String[] {
          SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.name(),
          SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.name(),
          SchemaValidationFailureSeverity.WARN_ONLY.name()
        },
        action.getFailureSeverityOptions());
  }

  @Test
  void clone_copiesNewFields() {
    ActionValidateResourceDefinitions original = new ActionValidateResourceDefinitions();
    original.setResourceDefinitionGroup("retail-sources");
    original.setTargetCatalogVersion("${TARGET_VERSION}");
    original.setCompareMode(SchemaCompareMode.WORKING_VS_VERSION.name());
    original.setBaselineCatalogVersion("v1.0.0");
    original.setReportOutputPath("${PROJECT_HOME}/reports");
    original.setReportFileBaseName("schema-gate");
    original.setReportFormat(SchemaValidationReportFileWriter.ReportFormat.BOTH.name());
    original.setFailureSeverity(SchemaValidationFailureSeverity.WARN_ONLY.name());
    original.setFailOnWarnings(true);
    original.setIncludeImpact(false);

    ActionValidateResourceDefinitions copy =
        (ActionValidateResourceDefinitions) original.clone();
    assertEquals("retail-sources", copy.getResourceDefinitionGroup());
    assertEquals("${TARGET_VERSION}", copy.getTargetCatalogVersion());
    assertEquals(SchemaCompareMode.WORKING_VS_VERSION.name(), copy.getCompareMode());
    assertEquals("v1.0.0", copy.getBaselineCatalogVersion());
    assertEquals("${PROJECT_HOME}/reports", copy.getReportOutputPath());
    assertEquals("schema-gate", copy.getReportFileBaseName());
    assertEquals(SchemaValidationReportFileWriter.ReportFormat.BOTH.name(), copy.getReportFormat());
    assertEquals(SchemaValidationFailureSeverity.WARN_ONLY.name(), copy.getFailureSeverity());
    assertTrue(copy.isFailOnWarnings());
    assertEquals(false, copy.isIncludeImpact());
  }
}
