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

package org.apache.hop.datavault.executionmap;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.datavault.metadata.file.DvCsvSource;
import org.apache.hop.datavault.metadata.file.IDvFileBasedSource;
import org.apache.hop.pipeline.PipelineMeta;

/** Adds dataset leaf nodes from DV/BV/DM model table and source metadata. */
public final class ModelDatasetResolver {

  private ModelDatasetResolver() {}

  public static void resolveDataVaultModel(
      ExecutionMapContext context, String modelNodeId, DataVaultModel model) {
    if (context == null
        || model == null
        || Utils.isEmpty(modelNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String targetNamespace = model.getConfigurationOrDefault().getTargetDatabase();
    for (IDvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      addTargetDataset(context, modelNodeId, targetNamespace, table.getTableName(), table.getName());
      addDvTableSources(context, modelNodeId, model, table);
    }
  }

  public static void resolveBusinessVaultModel(
      ExecutionMapContext context, String modelNodeId, BusinessVaultModel model) {
    if (context == null
        || model == null
        || Utils.isEmpty(modelNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String targetNamespace = model.getConfigurationOrDefault().getTargetDatabase();
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      addTargetDataset(context, modelNodeId, targetNamespace, table.getTableName(), table.getName());
    }
  }

  public static void resolveDimensionalModel(
      ExecutionMapContext context, String modelNodeId, DimensionalModel model) {
    if (context == null
        || model == null
        || Utils.isEmpty(modelNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String targetNamespace = model.getConfigurationOrDefault().getTargetDatabase();
    for (IDmTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      addTargetDataset(context, modelNodeId, targetNamespace, table.getTableName(), table.getName());
    }
  }

  public static void linkGeneratedPipeline(
      ExecutionMapContext context,
      String modelNodeId,
      String generatedPipelineNodeId,
      PipelineMeta pipelineMeta) {
    if (context == null
        || Utils.isEmpty(generatedPipelineNodeId)
        || pipelineMeta == null
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    PipelineDatasetResolver.resolvePipeline(context, generatedPipelineNodeId, pipelineMeta);
  }

  private static void addTargetDataset(
      ExecutionMapContext context,
      String modelNodeId,
      String namespace,
      String tableName,
      String label) {
    if (Utils.isEmpty(tableName)) {
      return;
    }
    String datasetNodeId =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.TARGET_DATASET,
            namespace,
            tableName,
            "DATABASE",
            modelNodeId);
    if (!Utils.isEmpty(datasetNodeId)) {
      context.addEdge(ExecutionMapEdgeType.CONTAINS, modelNodeId, datasetNodeId, label);
    }
  }

  private static void addDvTableSources(
      ExecutionMapContext context, String modelNodeId, DataVaultModel model, IDvTable table) {
    List<DataVaultSource> sources = new ArrayList<>();
    try {
      if (table instanceof DvHub hub) {
        if (hub.getRecordSources() != null) {
          for (String sourceRef : hub.getRecordSources()) {
            if (Utils.isEmpty(sourceRef)) {
              continue;
            }
            DataVaultSource source =
                org.apache.hop.datavault.catalog.DvSourceCatalogService.resolveSource(
                    sourceRef, model, context.getVariables(), context.getMetadataProvider());
            if (source != null) {
              sources.add(source);
            }
          }
        }
      } else if (table instanceof DvSatellite satellite) {
        DataVaultSource source =
            satellite.resolveRecordSource(
                context.getVariables(), context.getMetadataProvider(), model);
        if (source != null) {
          sources.add(source);
        }
      } else if (table instanceof DvLink link && link.getLinkHubSources() != null) {
        for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
          if (linkSource == null) {
            continue;
          }
          DataVaultSource source =
              linkSource.resolveSource(
                  context.getVariables(), context.getMetadataProvider(), model);
          if (source != null) {
            sources.add(source);
          }
        }
      }
    } catch (HopException e) {
      context.addWarning(
          "Failed to resolve DV sources for table '"
              + table.getName()
              + "': "
              + e.getMessage());
    }
    for (DataVaultSource source : sources) {
      addDataVaultSourceDataset(context, modelNodeId, source, table.getName());
    }
  }

  private static void addDataVaultSourceDataset(
      ExecutionMapContext context, String modelNodeId, DataVaultSource source, String tableLabel) {
    if (source == null) {
      return;
    }
    IDvSource dvSource = source.getDvSourceOrDefault();
    String namespace;
    String datasetName;
    String kind;
    if (dvSource instanceof DvDatabaseSource databaseSource) {
      namespace = databaseSource.getDatabaseName();
      datasetName = qualifiedDatabaseTable(databaseSource.getSchemaName(), databaseSource.getTableName());
      kind = "DATABASE";
    } else if (dvSource instanceof DvCsvSource csvSource) {
      namespace = source.getName();
      datasetName = qualifiedFileDataset(csvSource);
      kind = "CSV";
    } else if (dvSource instanceof IDvFileBasedSource fileSource) {
      namespace = source.getName();
      datasetName =
          !Utils.isEmpty(fileSource.getSingleFilename())
              ? fileSource.getSingleFilename()
              : qualifiedFileDataset(fileSource.getFolder(), fileSource.getIncludeFileMask());
      kind = dvSource.getSourceType() != null ? dvSource.getSourceType().name() : "FILE";
    } else {
      namespace = source.getName();
      datasetName = source.getName();
      kind = dvSource.getSourceType() != null ? dvSource.getSourceType().name() : "SOURCE";
    }
    String datasetNodeId =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.SOURCE_DATASET,
            namespace,
            datasetName,
            kind,
            modelNodeId);
    if (!Utils.isEmpty(datasetNodeId)) {
      context.addEdge(ExecutionMapEdgeType.CONTAINS, modelNodeId, datasetNodeId, tableLabel);
    }
  }

  private static String qualifiedDatabaseTable(String schemaName, String tableName) {
    if (Utils.isEmpty(schemaName)) {
      return tableName;
    }
    if (Utils.isEmpty(tableName)) {
      return schemaName;
    }
    return schemaName + "." + tableName;
  }

  private static String qualifiedFileDataset(DvCsvSource csvSource) {
    if (!Utils.isEmpty(csvSource.getSingleFilename())) {
      return csvSource.getSingleFilename();
    }
    return qualifiedFileDataset(csvSource.getFolder(), csvSource.getIncludeFileMask());
  }

  private static String qualifiedFileDataset(String folder, String fileMask) {
    if (!Utils.isEmpty(folder) && !Utils.isEmpty(fileMask)) {
      return folder + "/" + fileMask;
    }
    if (!Utils.isEmpty(fileMask)) {
      return fileMask;
    }
    return folder;
  }
}