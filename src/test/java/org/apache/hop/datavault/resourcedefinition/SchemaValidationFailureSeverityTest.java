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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.Test;

class SchemaValidationFailureSeverityTest {

  @Test
  void parse_defaultsAndValues() {
    assertEquals(
        SchemaValidationFailureSeverity.FAIL_ON_BLOCKING,
        SchemaValidationFailureSeverity.parse(null));
    assertEquals(
        SchemaValidationFailureSeverity.FAIL_ON_WARNINGS,
        SchemaValidationFailureSeverity.parse("fail_on_warnings"));
    assertEquals(
        SchemaValidationFailureSeverity.WARN_ONLY,
        SchemaValidationFailureSeverity.parse("WARN_ONLY"));
  }

  @Test
  void shouldFail_respectsPolicy() {
    ValidationReport clean = new ValidationReport("g");
    ValidationReport warning = reportWith(IssueSeverity.WARNING);
    ValidationReport blocking = reportWith(IssueSeverity.BLOCKING);

    assertFalse(SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.shouldFail(clean));
    assertFalse(SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.shouldFail(warning));
    assertTrue(SchemaValidationFailureSeverity.FAIL_ON_BLOCKING.shouldFail(blocking));

    assertTrue(SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.shouldFail(warning));
    assertTrue(SchemaValidationFailureSeverity.FAIL_ON_WARNINGS.shouldFail(blocking));

    assertFalse(SchemaValidationFailureSeverity.WARN_ONLY.shouldFail(blocking));
    assertFalse(SchemaValidationFailureSeverity.WARN_ONLY.shouldFail(warning));
  }

  private static ValidationReport reportWith(IssueSeverity severity) {
    ValidationReport report = new ValidationReport("g");
    report.addRecordValidation(
        new RecordDefinitionValidation(
            new RecordDefinitionKey("ns", "n"),
            "c",
            "CSV",
            false,
            new RecordDefinitionSchemaDiffSupport.SchemaDiff(List.of()),
            List.of(),
            List.of(
                new ValidationIssue(
                    "1",
                    IssueKind.FIELD_TYPE_CHANGED,
                    severity,
                    "f",
                    "msg",
                    List.of(),
                    "hub_x"))));
    return report;
  }
}
