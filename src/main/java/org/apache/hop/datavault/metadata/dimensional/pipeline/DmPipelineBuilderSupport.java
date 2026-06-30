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

package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadSupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;


/** Shared helpers for generated dimensional load pipelines. */
public final class DmPipelineBuilderSupport {

  public static final int SPACING_WIDTH = 200;
  public static final Point LOCATION_START = new Point(100, 100);
  public static final String PREVIOUS_VERSION_FIELD = "dm_prev_version";

  private DmPipelineBuilderSupport() {}

  public static final class BuildContext {
    public final DmTableBase table;
    public final DimensionalModel model;
    public final DimensionalConfiguration config;
    public final DmSourceConfiguration source;
    public final IVariables variables;
    public final DatabaseMeta targetDatabaseMeta;
    public final String targetDbName;
    public final String sourceDbName;
    public final String targetTableName;
    public final String pipelineName;

    private BuildContext(
        DmTableBase table,
        DimensionalModel model,
        DimensionalConfiguration config,
        DmSourceConfiguration source,
        IVariables variables,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String sourceDbName,
        String targetTableName,
        String pipelineName) {
      this.table = table;
      this.model = model;
      this.config = config;
      this.source = source;
      this.variables = variables;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.sourceDbName = sourceDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
    }

    public static BuildContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DimensionalModel model,
        DmTableBase table)
        throws HopException {
      if (metadataProvider == null || model == null || table == null) {
        return null;
      }

      DimensionalConfiguration config = model.getConfigurationOrDefault();
      DmSourceConfiguration source = table.getSourceOrDefault();
      String sourceSql = source.resolveSourceSql(variables);
      if (Utils.isEmpty(sourceSql)) {
        throw new HopException(
            "Dimensional table " + table.getName() + " requires source SQL for pipeline generation");
      }

      DatabaseMeta targetDatabaseMeta =
          DmTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
      if (targetDatabaseMeta == null) {
        throw new HopException(
            "Dimensional model target database is not configured for table " + table.getName());
      }

      String targetDbName = config.getTargetDatabase();
      String sourceDbName = source.resolveSourceConnection(config, variables);
      if (Utils.isEmpty(sourceDbName)) {
        sourceDbName = targetDbName;
      }

      String targetTableName =
          !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
      String pipelineName = resolvePipelineName(config, variables, table, targetTableName);

      return new BuildContext(
          table,
          model,
          config,
          source,
          variables,
          targetDatabaseMeta,
          targetDbName,
          sourceDbName,
          targetTableName,
          pipelineName);
    }
  }

  public static TransformMeta addSourceTableInput(BuildContext ctx, PipelineMeta pipelineMeta) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, ctx.source.resolveSourceSql(ctx.variables));

    TransformMeta tm = new TransformMeta("TableInput", "source_" + ctx.targetTableName, tableInputMeta);
    tm.setLocation(LOCATION_START.x, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  public static TransformMeta addTableOutput(
      BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      boolean truncate)
      throws HopException {
    return addTargetLoad(ctx, pipelineMeta, targetLayout, predecessor, truncate);
  }

  public static TransformMeta addTargetLoad(
      BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      boolean truncate)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            ctx.config,
            ctx.variables,
            ctx.targetDatabaseMeta,
            ctx.targetDbName,
            ctx.targetTableName,
            ctx.pipelineName,
            ctx.model.getName(),
            predecessor.getLocation().x + SPACING_WIDTH,
            predecessor.getLocation().y);

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            targetCtx,
            pipelineMeta,
            targetLayout,
            predecessor,
            Collections.emptySet(),
            truncate);
    return result.transformMeta;
  }

  public static TransformMeta addDummyTransform(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String name,
      int x,
      int y) {
    if (predecessor == null) {
      return null;
    }
    TransformMeta tm = new TransformMeta("Dummy", name, new DummyMeta());
    tm.setLocation(x, y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static String resolvePipelineName(
      DimensionalConfiguration config,
      IVariables variables,
      DmTableBase table,
      String targetTableName) {
    return switch (table.getTableType()) {
      case DIMENSION -> config.buildDimensionPipelineName(variables, targetTableName);
      case JUNK_DIMENSION -> config.buildJunkDimensionPipelineName(variables, targetTableName);
      case BRIDGE -> config.buildBridgePipelineName(variables, targetTableName);
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          config.buildFactPipelineName(variables, targetTableName);
    };
  }

  public static List<String> naturalKeyFieldNamesFromList(
      List<org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField> naturalKeys,
      IVariables variables) {
    List<String> names = new ArrayList<>();
    if (naturalKeys == null) {
      return names;
    }
    for (var key : naturalKeys) {
      if (key == null || Utils.isEmpty(key.getFieldName())) {
        continue;
      }
      String field = key.getFieldName();
      if (variables != null) {
        field = variables.resolve(field);
      }
      names.add(field);
    }
    return names;
  }

  public static List<String> naturalKeyFieldNames(
      org.apache.hop.datavault.metadata.dimensional.DmDimension dimension, IVariables variables) {
    List<String> names = new ArrayList<>();
    for (var key : dimension.getNaturalKeysOrEmpty()) {
      if (key == null || Utils.isEmpty(key.getFieldName())) {
        continue;
      }
      String field = key.getFieldName();
      if (variables != null) {
        field = variables.resolve(field);
      }
      names.add(field);
    }
    return names;
  }

  public static List<String> type1AttributeFieldNames(
      org.apache.hop.datavault.metadata.dimensional.DmDimension dimension, IVariables variables) {
    List<String> names = new ArrayList<>();
    for (var attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      if (attribute.getScdUpdatePolicy() == org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy.TYPE2) {
        continue;
      }
      String field = attribute.getFieldName();
      if (variables != null) {
        field = variables.resolve(field);
      }
      names.add(field);
    }
    return names;
  }

  public static List<String> scd2AttributeFieldNames(
      org.apache.hop.datavault.metadata.dimensional.DmDimension dimension, IVariables variables) {
    List<String> names = new ArrayList<>();
    for (var attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      String field = attribute.getFieldName();
      if (variables != null) {
        field = variables.resolve(field);
      }
      names.add(field);
    }
    return names;
  }
}