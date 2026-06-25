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

package org.apache.hop.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class RecordDefinitionSchemaDiffSupportTest {

  @Test
  void diff_reportsNoChangesWhenContractsMatch() {
    List<SourceField> stored = List.of(field("customer_id", "Integer", IValueMeta.TYPE_INTEGER));
    List<SourceField> discovered = List.of(field("customer_id", "Integer", IValueMeta.TYPE_INTEGER));

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(stored, discovered);

    assertTrue(diff.isInSync());
    assertFalse(diff.hasChanges());
  }

  @Test
  void diff_reportsAddedRemovedAndChangedFields() {
    List<SourceField> stored =
        List.of(
            field("customer_id", "Integer", IValueMeta.TYPE_INTEGER),
            field("legacy_code", "String", IValueMeta.TYPE_STRING));
    List<SourceField> discovered =
        List.of(
            field("customer_id", "String", IValueMeta.TYPE_STRING),
            field("email", "String", IValueMeta.TYPE_STRING));

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(stored, discovered);

    assertTrue(diff.hasChanges());
    assertEquals(3, diff.changes().size());
    assertEquals(
        RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED, diff.changes().get(0).kind());
    assertEquals("customer_id", diff.changes().get(0).fieldName());
    assertEquals(
        RecordDefinitionSchemaDiffSupport.ChangeKind.REMOVED, diff.changes().get(1).kind());
    assertEquals("legacy_code", diff.changes().get(1).fieldName());
    assertEquals(RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, diff.changes().get(2).kind());
    assertEquals("email", diff.changes().get(2).fieldName());
  }

  @Test
  void diffTypesOnly_ignoresLengthAndPrecisionDifferences() {
    SourceField stored = field("name", "String", IValueMeta.TYPE_STRING);
    stored.setLength("50");
    SourceField discovered = field("name", "String", IValueMeta.TYPE_STRING);
    discovered.setLength("");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diffTypesOnly(List.of(stored), List.of(discovered));

    assertTrue(diff.isInSync());
  }

  private static SourceField field(String name, String sourceDataType, int hopType) {
    SourceField field = new SourceField(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    return field;
  }
}