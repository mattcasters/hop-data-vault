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

package org.apache.hop.datavault.catalog;

import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;

import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes Data Vault model tables and referenced sources to a configured data catalog. */
public final class DvCatalogPublisher {

  private DvCatalogPublisher() {}

  public static void publish(
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Data catalog connection name is required for publishing");
    }
    if (model == null) {
      throw new HopException("Data Vault model is required for publishing");
    }

    String projectKey = resolveProjectKey(variables);
    String modelBasename = resolveModelBasename(model);
    String tableNamespace = "hop/" + projectKey + "/models/" + modelBasename;
    String sourceNamespace = "hop/" + projectKey + "/sources";
    Date updatedAt = new Date();

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    for (IDvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      try {
        RecordDefinition definition =
            toTableRecordDefinition(
                table, model, tableNamespace, variables, metadataProvider, updatedAt, workflowName);
        registry.upsert(catalogConnectionName, definition, variables, metadataProvider);
      } catch (Exception ignored) {
        // Per-table publish failures are non-fatal; caller may log aggregate outcome.
      }
    }

    for (DataVaultSource source :
        collectReferencedSources(model, variables, metadataProvider)) {
      try {
        RecordDefinition definition =
            DvSourceCatalogMapper.toRecordDefinition(
                source, sourceNamespace, model, variables, metadataProvider, updatedAt, workflowName);
        registry.upsert(catalogConnectionName, definition, variables, metadataProvider);
      } catch (Exception ignored) {
        // Per-source publish failures are non-fatal; caller may log aggregate outcome.
      }
    }
  }

  static RecordDefinition toTableRecordDefinition(
      IDvTable table,
      DataVaultModel model,
      String namespace,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName)
      throws HopException {
    IRowMeta layout = table.getTargetTableLayout(metadataProvider, variables, model);
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, table.getName()));
    definition.setType(mapTableType(table.getTableType()));
    definition.setDescription(table.getDescription());
    definition.setFields(layout);
    definition.setOrigin(buildTableOrigin(table, model, variables, updatedAt, workflowName));
    definition.setPhysicalTable(buildPhysicalTableRef(model, table));
    definition.getTags().add("DV " + table.getTableType().name());
    if (!Utils.isEmpty(model.getName())) {
      definition.getTags().add(model.getName());
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config != null && !Utils.isEmpty(config.getRecordSourceField())) {
      definition
          .getCustomProperties()
          .put("recordSourceField", CatalogCustomProperty.string(config.getRecordSourceField()));
    }
    return definition;
  }

  private static RecordDefinitionType mapTableType(DvTableType tableType) {
    if (tableType == null) {
      return RecordDefinitionType.UNKNOWN;
    }
    return switch (tableType) {
      case HUB -> RecordDefinitionType.DV_HUB;
      case LINK -> RecordDefinitionType.DV_LINK;
      case SATELLITE -> RecordDefinitionType.DV_SATELLITE;
    };
  }

  private static RecordOrigin buildTableOrigin(
      IDvTable table,
      DataVaultModel model,
      IVariables variables,
      Date updatedAt,
      String workflowName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType("DATA_VAULT_MODEL");
    origin.setModelName(model.getName());
    origin.setModelFilename(model.getFilename());
    origin.setModelElementName(table.getName());
    origin.setHopProject(resolveProjectKey(variables));
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    return origin;
  }

  private static PhysicalTableRef buildPhysicalTableRef(DataVaultModel model, IDvTable table) {
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config == null || Utils.isEmpty(config.getTargetDatabase())) {
      return null;
    }
    PhysicalTableRef ref = new PhysicalTableRef();
    ref.setDatabaseMetaName(config.getTargetDatabase());
    ref.setTableName(table.getTableName());
    return ref;
  }

  static Set<DataVaultSource> collectReferencedSources(
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    Set<DataVaultSource> sources = new LinkedHashSet<>();
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvHub hub) {
        if (hub.getRecordSources() != null) {
          for (String sourceName : hub.getRecordSources()) {
            addSourceByName(sources, sourceName, metadataProvider);
          }
        }
      } else if (table instanceof DvSatellite satellite) {
        if (satellite.getRecordSource() != null) {
          sources.add(satellite.getRecordSource());
        }
      } else if (table instanceof DvLink link) {
        if (link.getLinkHubSources() != null) {
          for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
            if (linkSource.getSource() != null) {
              sources.add(linkSource.getSource());
            }
          }
        }
        if (link.getLinkSatelliteSources() != null) {
          for (DvLink.DvLinkSatelliteSource linkSource : link.getLinkSatelliteSources()) {
            if (linkSource.getSource() != null) {
              sources.add(linkSource.getSource());
            }
          }
        }
      }
    }
    return sources;
  }

  private static void addSourceByName(
      Set<DataVaultSource> sources, String sourceName, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceName)) {
      return;
    }
    DataVaultSource source = metadataProvider.getSerializer(DataVaultSource.class).load(sourceName);
    if (source != null) {
      sources.add(source);
    }
  }

  static String resolveProjectKey(IVariables variables) {
    if (variables != null) {
      String projectHome = variables.resolve("${PROJECT_HOME}");
      if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
        Path path = Path.of(projectHome).getFileName();
        if (path != null) {
          return sanitizeKeySegment(path.toString());
        }
      }
    }
    return "default";
  }

  static String resolveModelBasename(DataVaultModel model) {
    String filename = model.getFilename();
    if (!Utils.isEmpty(filename)) {
      String base = Path.of(filename).getFileName().toString();
      int dot = base.lastIndexOf('.');
      if (dot > 0) {
        return sanitizeKeySegment(base.substring(0, dot));
      }
      return sanitizeKeySegment(base);
    }
    return sanitizeKeySegment(model.getName());
  }

  private static String sanitizeKeySegment(String value) {
    if (Utils.isEmpty(value)) {
      return "unknown";
    }
    return value.replace('\\', '_').replace('/', '_').replace(' ', '_');
  }
}