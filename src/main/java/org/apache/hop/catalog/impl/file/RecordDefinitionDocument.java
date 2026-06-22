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

package org.apache.hop.catalog.impl.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.util.RowMetaCatalogSupport;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;

/** JSON-serializable document stored by {@link FileDataCatalog}. */
@Getter
@Setter
@NoArgsConstructor
class RecordDefinitionDocument {

  private String namespace;
  private String name;
  private String type;
  private String description;
  private String rowMetaXml;
  private RecordOrigin origin;
  private PhysicalTableRef physicalTable;
  private List<String> tags = new ArrayList<>();
  private List<String> glossaryTerms = new ArrayList<>();
  private Map<String, CatalogCustomProperty> customProperties = new HashMap<>();
  private DvSourceRecord dvSource;

  static RecordDefinitionDocument from(RecordDefinition definition) throws HopException {
    definition.validate();
    RecordDefinitionDocument doc = new RecordDefinitionDocument();
    doc.namespace = definition.getKey().getNamespace();
    doc.name = definition.getKey().getName();
    doc.type =
        definition.getType() != null ? definition.getType().name() : RecordDefinitionType.UNKNOWN.name();
    doc.description = definition.getDescription();
    doc.rowMetaXml = RowMetaCatalogSupport.toXml(definition.getFields());
    doc.origin = definition.getOrigin();
    doc.physicalTable = definition.getPhysicalTable();
    if (definition.getTags() != null) {
      doc.tags = new ArrayList<>(definition.getTags());
    }
    if (definition.getGlossaryTerms() != null) {
      doc.glossaryTerms = new ArrayList<>(definition.getGlossaryTerms());
    }
    if (definition.getCustomProperties() != null) {
      doc.customProperties = new HashMap<>(definition.getCustomProperties());
    }
    doc.dvSource = definition.getDvSource();
    return doc;
  }

  RecordDefinition toRecordDefinition() throws HopException {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, name));
    definition.setType(parseType(type));
    definition.setDescription(description);
    definition.setFields(RowMetaCatalogSupport.fromXml(rowMetaXml));
    definition.setOrigin(origin);
    definition.setPhysicalTable(physicalTable);
    definition.setTags(tags != null ? new ArrayList<>(tags) : new ArrayList<>());
    definition.setGlossaryTerms(
        glossaryTerms != null ? new ArrayList<>(glossaryTerms) : new ArrayList<>());
    definition.setCustomProperties(
        customProperties != null ? new HashMap<>(customProperties) : new HashMap<>());
    definition.setDvSource(dvSource);
    return definition;
  }

  private static RecordDefinitionType parseType(String raw) {
    if (raw == null || raw.isBlank()) {
      return RecordDefinitionType.UNKNOWN;
    }
    try {
      return RecordDefinitionType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return RecordDefinitionType.UNKNOWN;
    }
  }
}