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

package org.apache.hop.datavault.metadata.businessvault;

import java.util.List;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
/** Generates PIT build pipelines from DV hub/satellite history. */
public final class BvPitPipelineSupport {

  private static final Class<?> PKG = BvPitPipelineSupport.class;

  private static final Point LOCATION_TABLE_INPUT = new Point(160, 160);
  private static final Point LOCATION_TABLE_OUTPUT = new Point(400, 160);

  private BvPitPipelineSupport() {}

  public static PitBuildContext createContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvPitTable pitTable)
      throws HopException {
    if (metadataProvider == null || bvModel == null || dvModel == null || pitTable == null) {
      return null;
    }

    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();

    String sourceDbName = dvConfig.getTargetDatabase();
    if (Utils.isEmpty(sourceDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MissingDvTargetDatabase", pitTable.getName()));
    }
    DatabaseMeta sourceDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);

    String targetDbName = bvConfig.getTargetDatabase();
    if (Utils.isEmpty(targetDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MissingBvTargetDatabase", pitTable.getName()));
    }
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);

    DvHub hub = BvPitLayoutSupport.resolveHubDerivative(pitTable, dvModel);
    List<DvSatellite> satellites = BvPitLayoutSupport.resolveSatelliteDerivatives(pitTable, dvModel);
    if (hub == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BvPitPipelineSupport.Error.MissingHub", pitTable.getName()));
    }
    if (satellites.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MissingSatellite", pitTable.getName()));
    }
    if (satellites.size() > 1) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MultiSatelliteNotImplemented", pitTable.getName()));
    }

    DvSatellite satellite = satellites.get(0);
    BvPitSnapshotSchedule schedule = pitTable.getSnapshotScheduleOrDefault();
    if (schedule.getCadence() != BvPitCadence.DAILY) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvPitPipelineSupport.Error.NonDailyCadenceNotImplemented",
              pitTable.getName(),
              schedule.getCadence().name()));
    }

    String bvTargetTableName =
        !Utils.isEmpty(pitTable.getTableName()) ? pitTable.getTableName() : pitTable.getName();
    String pipelineName = bvConfig.buildPitPipelineName(variables, bvTargetTableName);
    String hashKeyFieldName = BvPitLayoutSupport.resolveHubHashKeyFieldName(hub, variables);
    String snapshotDateField = BvPitLayoutSupport.resolveSnapshotDateField(pitTable, variables);
    String loadDateField = BvPitSnapshotSpineSupport.resolveLoadDateField(dvConfig, variables);
    String satellitePointerColumnName =
        BvPitLayoutSupport.resolveSatellitePointerColumnName(satellite, schedule, variables);
    String hubTableName =
        !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
    String satelliteTableName = BvPitLayoutSupport.resolveSatellitePhysicalName(satellite);

    return new PitBuildContext(
        pitTable,
        hub,
        satellite,
        schedule,
        bvModel,
        dvModel,
        bvConfig,
        dvConfig,
        metadataProvider,
        variables,
        sourceDatabaseMeta,
        sourceDbName,
        targetDatabaseMeta,
        targetDbName,
        bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        snapshotDateField,
        loadDateField,
        satellitePointerColumnName,
        hubTableName,
        satelliteTableName);
  }

  public static String buildPitTableInputSql(PitBuildContext ctx) throws HopException {
    if (ctx == null) {
      throw new HopException("PIT build context is required");
    }

    BvPitSnapshotAnchor anchor =
        ctx.schedule().getSnapshotAnchor() != null
            ? ctx.schedule().getSnapshotAnchor()
            : BvPitSnapshotAnchor.END_OF_PERIOD;

    StringBuilder sql = new StringBuilder("WITH ");
    boolean needsSatelliteLoadCte = needsEarliestSatelliteLoadCte(ctx.schedule());
    boolean needsHubLoadCte = needsEarliestHubLoadCte(ctx.schedule());

    if (needsSatelliteLoadCte) {
      sql.append("earliest_satellite_load AS (");
      sql.append(
          BvPitSnapshotSpineSupport.buildEarliestParticipatingSatelliteLoadSql(
              ctx.sourceDatabaseMeta(),
              ctx.variables(),
              List.of(ctx.satellite()),
              ctx.loadDateField()));
      sql.append("), ");
    }
    if (needsHubLoadCte) {
      sql.append("earliest_hub_load AS (");
      sql.append(
          BvPitSnapshotSpineSupport.buildEarliestHubLoadSql(
              ctx.sourceDatabaseMeta(), ctx.variables(), ctx.hub(), ctx.loadDateField()));
      sql.append("), ");
    }

    sql.append("bounds AS (SELECT ");
    sql.append(buildStartDateExpression(ctx, needsSatelliteLoadCte, needsHubLoadCte));
    sql.append(" AS start_date, ");
    sql.append(buildEndDateExpression(ctx));
    sql.append(" AS end_date), ");

    sql.append(
        BvPitSnapshotSpineSupport.buildDynamicSnapshotSpineCte(
            ctx.sourceDatabaseMeta(), "snapshot_spine", "snapshot_date", "bounds", anchor));
    sql.append(", hub_keys AS (SELECT DISTINCT ");
    sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
    sql.append(" AS ");
    sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
    sql.append(" FROM ");
    sql.append(
        ctx.sourceDatabaseMeta()
            .getQuotedSchemaTableCombination(ctx.variables(), null, ctx.hubTableName()));
    sql.append(") ");

    if (BvPitSnapshotSpineSupport.resolveDialect(ctx.sourceDatabaseMeta())
        == BvPitSnapshotSpineSupport.PitSqlDialect.SINGLESTORE) {
      // SingleStore rejects correlated scalar subselects when the outer FROM is only CTEs
      // ("Scalar subselect where outer table is not a sharded table"). Use LEFT JOIN + GROUP BY.
      sql.append(buildSinglestorePitSelect(ctx));
    } else {
      sql.append("SELECT hk.");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
      sql.append(" AS ");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
      sql.append(", spine.");
      sql.append("snapshot_date");
      sql.append(" AS ");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.snapshotDateField()));
      sql.append(", ");
      sql.append(buildSatellitePointerSubquery(ctx));
      sql.append(" AS ");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.satellitePointerColumnName()));
      sql.append(" FROM hub_keys hk CROSS JOIN snapshot_spine spine WHERE ");
      sql.append(
          BvPitSnapshotSpineSupport.buildIncrementalSnapshotFilterSql(
              ctx.targetDatabaseMeta(),
              ctx.variables(),
              ctx.bvTargetTableName(),
              ctx.snapshotDateField(),
              "spine.snapshot_date"));
    }

    return sql.toString();
  }

  /**
   * SingleStore-safe final SELECT: join hub keys × spine to the satellite table and aggregate the
   * pointer load date instead of using a correlated scalar subselect.
   */
  private static String buildSinglestorePitSelect(PitBuildContext ctx) {
    String quotedHashKey = ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName());
    String quotedLoadDate = ctx.sourceDatabaseMeta().quoteField(ctx.loadDateField());
    String quotedSnapshotField = ctx.sourceDatabaseMeta().quoteField(ctx.snapshotDateField());
    String quotedPointer = ctx.sourceDatabaseMeta().quoteField(ctx.satellitePointerColumnName());
    String quotedSatelliteTable =
        ctx.sourceDatabaseMeta()
            .getQuotedSchemaTableCombination(ctx.variables(), null, ctx.satelliteTableName());
    String incremental =
        BvPitSnapshotSpineSupport.buildIncrementalSnapshotFilterSql(
            ctx.targetDatabaseMeta(),
            ctx.variables(),
            ctx.bvTargetTableName(),
            ctx.snapshotDateField(),
            "spine.snapshot_date");

    return "SELECT hk."
        + quotedHashKey
        + " AS "
        + quotedHashKey
        + ", spine.snapshot_date AS "
        + quotedSnapshotField
        + ", MAX(sat."
        + quotedLoadDate
        + ") AS "
        + quotedPointer
        + " FROM hub_keys hk "
        + "CROSS JOIN snapshot_spine spine "
        + "LEFT JOIN "
        + quotedSatelliteTable
        + " sat ON sat."
        + quotedHashKey
        + " = hk."
        + quotedHashKey
        + " AND sat."
        + quotedLoadDate
        + " <= spine.snapshot_date "
        + "WHERE "
        + incremental
        + " GROUP BY hk."
        + quotedHashKey
        + ", spine.snapshot_date";
  }

  public static PipelineMeta generatePipeline(PitBuildContext ctx) throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName());
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta,
        ctx.bvModel(),
        "pit",
        ctx.pitTable().getName(),
        ctx.bvTargetTableName());

    TransformMeta tableInput = addTableInput(ctx, pipelineMeta);
    if (tableInput != null) {
      GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, ctx.sourceDbName());
    }
    TransformMeta writeTransform = addTableOutput(ctx, pipelineMeta, tableInput);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform,
          "pit",
          ctx.pitTable().getName(),
          ctx.bvTargetTableName(),
          ctx.targetDbName());
    }

    BvGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvPitTable pitTable)
      throws HopException {
    try {
      DbCache.clearAll();
      PitBuildContext ctx = createContext(metadataProvider, variables, bvModel, dvModel, pitTable);
      if (ctx == null) {
        return List.of();
      }
      return List.of(generatePipeline(ctx));
    } catch (Exception e) {
      throw new HopException(
          "Error generating PIT build pipeline for Business Vault table " + pitTable.getName(), e);
    }
  }

  private static TransformMeta addTableInput(PitBuildContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildPitTableInputSql(ctx));

    TransformMeta tm =
        new TransformMeta("TableInput", "read_" + ctx.bvTargetTableName(), tableInputMeta);
    tm.setLocation(LOCATION_TABLE_INPUT);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addTableOutput(
      PitBuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    IRowMeta targetLayout =
        BvPitLayoutSupport.buildTargetTableLayout(ctx.pitTable(), ctx.dvModel(), ctx.variables());

    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            ctx.bvConfig(),
            ctx.variables(),
            ctx.targetDatabaseMeta(),
            ctx.targetDbName(),
            ctx.bvTargetTableName(),
            ctx.pipelineName(),
            ctx.bvModel().getName(),
            LOCATION_TABLE_OUTPUT.x,
            LOCATION_TABLE_OUTPUT.y);

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            targetCtx,
            pipelineMeta,
            targetLayout,
            predecessor,
            java.util.Collections.emptySet(),
            false);
    return result.transformMeta;
  }

  private static String buildSatellitePointerSubquery(PitBuildContext ctx) {
    String quotedHashKey = ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName());
    String quotedLoadDate = ctx.sourceDatabaseMeta().quoteField(ctx.loadDateField());
    String satelliteAlias = "sat";
    String quotedSatelliteTable =
        ctx.sourceDatabaseMeta()
            .getQuotedSchemaTableCombination(ctx.variables(), null, ctx.satelliteTableName());
    return "(SELECT MAX("
        + satelliteAlias
        + "."
        + quotedLoadDate
        + ") FROM "
        + quotedSatelliteTable
        + " "
        + satelliteAlias
        + " WHERE "
        + satelliteAlias
        + "."
        + quotedHashKey
        + " = hk."
        + quotedHashKey
        + " AND "
        + satelliteAlias
        + "."
        + quotedLoadDate
        + " <= spine.snapshot_date)";
  }

  private static boolean needsEarliestSatelliteLoadCte(BvPitSnapshotSchedule schedule) {
    BvPitRangeStart rangeStart =
        schedule.getRangeStart() != null
            ? schedule.getRangeStart()
            : BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
    return rangeStart == BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
  }

  private static boolean needsEarliestHubLoadCte(BvPitSnapshotSchedule schedule) {
    return schedule.getRangeStart() == BvPitRangeStart.EARLIEST_HUB_LOAD;
  }

  private static String buildStartDateExpression(
      PitBuildContext ctx, boolean needsSatelliteLoadCte, boolean needsHubLoadCte) {
    DatabaseMeta db = ctx.sourceDatabaseMeta();
    BvPitRangeStart rangeStart =
        ctx.schedule().getRangeStart() != null
            ? ctx.schedule().getRangeStart()
            : BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
    return switch (rangeStart) {
      case FIXED_DATE ->
          BvPitSnapshotSpineSupport.dateLiteral(
              db, resolveFixedDateLiteral(ctx.schedule().getRangeStartFixed(), ctx.variables()));
      case EARLIEST_PARTICIPATING_SATELLITE_LOAD ->
          needsSatelliteLoadCte
              ? "(SELECT "
                  + BvPitSnapshotSpineSupport.castToDateExpression(db, "earliest_load")
                  + " FROM earliest_satellite_load)"
              : BvPitSnapshotSpineSupport.currentDateExpression(db);
      case EARLIEST_HUB_LOAD ->
          needsHubLoadCte
              ? "(SELECT "
                  + BvPitSnapshotSpineSupport.castToDateExpression(db, "earliest_load")
                  + " FROM earliest_hub_load)"
              : BvPitSnapshotSpineSupport.currentDateExpression(db);
    };
  }

  private static String buildEndDateExpression(PitBuildContext ctx) {
    DatabaseMeta db = ctx.sourceDatabaseMeta();
    BvPitRangeEnd rangeEnd =
        ctx.schedule().getRangeEnd() != null
            ? ctx.schedule().getRangeEnd()
            : BvPitRangeEnd.NOW_MINUS_HORIZON;
    return switch (rangeEnd) {
      case NOW -> BvPitSnapshotSpineSupport.currentDateExpression(db);
      case NOW_MINUS_HORIZON ->
          BvPitSnapshotSpineSupport.currentDateMinusDaysExpression(
              db, ctx.schedule().getHorizonDays());
      case FIXED_DATE ->
          BvPitSnapshotSpineSupport.dateLiteral(
              db, resolveFixedDateLiteral(ctx.schedule().getRangeEndFixed(), ctx.variables()));
    };
  }

  private static String resolveFixedDateLiteral(String value, IVariables variables) {
    String resolved = variables != null ? variables.resolve(value) : value;
    if (Utils.isEmpty(resolved)) {
      return "1900-01-01";
    }
    String trimmed = resolved.trim();
    return trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed;
  }

  /** Resolved inputs for a generated PIT build pipeline. */
  public record PitBuildContext(
      BvPitTable pitTable,
      DvHub hub,
      DvSatellite satellite,
      BvPitSnapshotSchedule schedule,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DatabaseMeta sourceDatabaseMeta,
      String sourceDbName,
      DatabaseMeta targetDatabaseMeta,
      String targetDbName,
      String bvTargetTableName,
      String pipelineName,
      String hashKeyFieldName,
      String snapshotDateField,
      String loadDateField,
      String satellitePointerColumnName,
      String hubTableName,
      String satelliteTableName) {}
}