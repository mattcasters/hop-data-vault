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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.HashAlgorithm;
import org.apache.hop.datavault.metadata.HashKeyDataType;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.datavault.transform.sortedschemamerge.SortedSchemaMergeMeta;
import org.apache.hop.datavault.transform.sortedschemamerge.SortedSchemaMergeMetaFactory;
import org.apache.hop.datavault.transform.sortedschemamerge.SortedSchemaMergeSortKey;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.analyticquery.AnalyticQueryMeta;
import org.apache.hop.pipeline.transforms.analyticquery.GroupField;
import org.apache.hop.pipeline.transforms.analyticquery.QueryField;
import org.apache.hop.pipeline.transforms.groupby.Aggregation;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupingField;
import org.apache.hop.pipeline.transforms.ifnull.Field;
import org.apache.hop.pipeline.transforms.ifnull.IfNullMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.repeatfields.Repeat;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta.RepeatType;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
/**
 * Generates SCD2 build pipelines from DV satellite history using Analytic Query (LAG/LEAD validity
 * bounds), If Null sentinels, and Group By collapse for duplicate timestamps.
 */
public final class BvScd2PipelineSupport {

  private static final Class<?> PKG = BvScd2PipelineSupport.class;

  private static final Point LOCATION_START = new Point(160, 160);
  private static final int SPACING_WIDTH = 160;
  private static final int LEG_SPACING_HEIGHT = 96;
  static final String SOURCE_INDICATOR_FIELD = "_bv_source";
  private static final String REPEAT_FIELD_PREFIX = "_r_";

  private BvScd2PipelineSupport() {}

  /** Validates that DV and BV target database connections are configured and resolvable. */
  public static void validateTargetDatabases(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table) {
    if (remarks == null || scd2Table == null) {
      return;
    }
    BusinessVaultConfiguration bvConfig =
        bvModel != null ? bvModel.getConfigurationOrDefault() : new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig =
        dvModel != null ? dvModel.getConfigurationOrDefault() : new DataVaultConfiguration();

    if (Utils.isEmpty(dvConfig.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvScd2PipelineSupport.CheckResult.MissingDvTargetDatabase", scd2Table.getName()),
              scd2Table));
    } else if (metadataProvider != null) {
      try {
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
      } catch (HopException e) {
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), scd2Table));
      }
    }

    if (Utils.isEmpty(bvConfig.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvScd2PipelineSupport.CheckResult.MissingBvTargetDatabase", scd2Table.getName()),
              scd2Table));
    } else if (metadataProvider != null) {
      try {
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
      } catch (HopException e) {
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), scd2Table));
      }
    }
  }

  public static PipelineMeta generatePipeline(Scd2BuildContext ctx) throws HopException {
    if (ctx.isMultiSatellite()) {
      return generateMultiSatellitePipeline(ctx);
    }
    return generateSingleSatellitePipeline(ctx);
  }

  private static PipelineMeta generateSingleSatellitePipeline(Scd2BuildContext ctx)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta,
        ctx.bvModel,
        "scd2",
        ctx.scd2Table.getName(),
        ctx.bvTargetTableName);

    TransformMeta tableInput = addSatelliteTableInput(ctx, pipelineMeta);
    if (tableInput != null) {
      GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, ctx.sourceDbName);
    }
    TransformMeta analyticQuery = addAnalyticQuery(ctx, pipelineMeta, tableInput);
    TransformMeta ifNull = addIfNull(ctx, pipelineMeta, analyticQuery);
    TransformMeta groupBy = addGroupBy(ctx, pipelineMeta, ifNull);
    TransformMeta writeTransform = addTableOutput(ctx, pipelineMeta, groupBy);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName, ctx.targetDbName);
    }

    BvGeneratedPipelineSupport.applyScd2Layout(pipelineMeta);
    return pipelineMeta;
  }

  private static PipelineMeta generateMultiSatellitePipeline(Scd2BuildContext ctx)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta,
        ctx.bvModel,
        "scd2",
        ctx.scd2Table.getName(),
        ctx.bvTargetTableName);

    List<TransformMeta> legOutputs = new ArrayList<>();
    for (int legIndex = 0; legIndex < ctx.legs.size(); legIndex++) {
      SatelliteLeg leg = ctx.legs.get(legIndex);
      Point legLocation =
          new Point(LOCATION_START.x, LOCATION_START.y + legIndex * LEG_SPACING_HEIGHT);
      TransformMeta tableInput = addLegTableInput(ctx, leg, pipelineMeta, legLocation);
      if (tableInput != null) {
        GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, ctx.sourceDbName);
      }
      TransformMeta sourceConstant =
          addLegSourceIndicatorConstant(ctx, leg, pipelineMeta, tableInput, legLocation);
      legOutputs.add(addLegSelectValues(ctx, leg, pipelineMeta, sourceConstant, legLocation));
    }

    TransformMeta sortedMerge = addSortedSchemaMerge(ctx, pipelineMeta, legOutputs);
    TransformMeta repeatFields = addRepeatFields(ctx, pipelineMeta, sortedMerge);
    TransformMeta postRepeatSelect = addPostRepeatSelectValues(ctx, pipelineMeta, repeatFields);
    TransformMeta analyticQuery = addAnalyticQuery(ctx, pipelineMeta, postRepeatSelect);
    TransformMeta ifNull = addIfNull(ctx, pipelineMeta, analyticQuery);
    TransformMeta groupBy = addGroupBy(ctx, pipelineMeta, ifNull);
    TransformMeta writeTransform = addTableOutput(ctx, pipelineMeta, groupBy);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName, ctx.targetDbName);
    }

    BvGeneratedPipelineSupport.applyScd2Layout(pipelineMeta);
    return pipelineMeta;
  }

  public static Scd2BuildContext createContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table)
      throws HopException {
    if (metadataProvider == null || bvModel == null || dvModel == null || scd2Table == null) {
      return null;
    }

    List<DvSatellite> satellites = resolveSourceSatellites(scd2Table, dvModel);
    if (satellites.size() >= 2) {
      return createMultiSatelliteContext(
          metadataProvider, variables, bvModel, dvModel, scd2Table, satellites);
    }
    return createSingleSatelliteContext(
        metadataProvider, variables, bvModel, dvModel, scd2Table, satellites.get(0));
  }

  private static Scd2BuildContext createSingleSatelliteContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      DvSatellite satellite)
      throws HopException {
    SharedScd2Resources resources = resolveSharedResources(metadataProvider, bvModel, dvModel, scd2Table, variables);

    String satelliteTableName =
        !Utils.isEmpty(satellite.getTableName()) ? satellite.getTableName() : satellite.getName();
    String pipelineName =
        resources.bvConfig.buildScd2PipelineName(variables, resources.bvTargetTableName, satellite.getName());

    String hashKeyFieldName = resolveHashKeyFieldName(satellite, dvModel, variables);
    String drivingKeyFieldName =
        satellite.hasDrivingKey() ? variables.resolve(satellite.getDrivingKey()) : null;
    List<String> attributeFieldNames = resolveAttributeFieldNames(satellite);

    SatelliteLeg leg =
        new SatelliteLeg(
            satellite,
            satelliteTableName,
            resolveSourceIndicatorValue(scd2Table, satellite, null, variables),
            resources.functionalTimestampField,
            List.of());

    return new Scd2BuildContext(
        scd2Table,
        List.of(leg),
        false,
        List.of(),
        bvModel,
        dvModel,
        resources.bvConfig,
        resources.dvConfig,
        metadataProvider,
        variables,
        resources.sourceDatabaseMeta,
        resources.sourceDbName,
        resources.targetDatabaseMeta,
        resources.targetDbName,
        satelliteTableName,
        resources.bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        drivingKeyFieldName,
        attributeFieldNames,
        resources.functionalTimestampField,
        resources.validFromField,
        resources.validToField,
        resources.recordSourceField,
        resources.openStartSentinel,
        resources.openEndSentinel,
        scd2Table.isIncludeHashKey());
  }

  private static Scd2BuildContext createMultiSatelliteContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites)
      throws HopException {
    if (!hasFieldMappings(scd2Table)) {
      throw new HopException(
          "SCD2 table "
              + scd2Table.getName()
              + " references multiple satellites and requires explicit field mappings");
    }

    SharedScd2Resources resources = resolveSharedResources(metadataProvider, bvModel, dvModel, scd2Table, variables);
    DvSatellite anchorSatellite = satellites.get(0);
    String pipelineName =
        resources.bvConfig.buildScd2PipelineName(variables, resources.bvTargetTableName, scd2Table.getName());
    String hashKeyFieldName = resolveHashKeyFieldName(anchorSatellite, dvModel, variables);
    String drivingKeyFieldName = resolveSharedDrivingKeyFieldName(satellites, variables);
    List<String> mappedAttributeFieldNames = resolveMappedTargetFieldNames(scd2Table, variables);

    List<SatelliteLeg> legs = new ArrayList<>();
    for (DvSatellite satellite : satellites) {
      BvScd2SatelliteConfig satelliteConfig =
          BvScd2FieldMappingValidationSupport.findSatelliteConfig(
              scd2Table, satellite.getName(), variables);
      String satelliteTableName =
          !Utils.isEmpty(satellite.getTableName()) ? satellite.getTableName() : satellite.getName();
      legs.add(
          new SatelliteLeg(
              satellite,
              satelliteTableName,
              resolveSourceIndicatorValue(scd2Table, satellite, satelliteConfig, variables),
              resolveFunctionalTimestampFieldForSatellite(
                  scd2Table, satelliteConfig, resources.bvConfig, resources.dvConfig, variables),
              resolveFieldMappingsForSatellite(scd2Table, satellite.getName(), variables)));
    }

    return new Scd2BuildContext(
        scd2Table,
        legs,
        true,
        mappedAttributeFieldNames,
        bvModel,
        dvModel,
        resources.bvConfig,
        resources.dvConfig,
        metadataProvider,
        variables,
        resources.sourceDatabaseMeta,
        resources.sourceDbName,
        resources.targetDatabaseMeta,
        resources.targetDbName,
        anchorSatellite.getName(),
        resources.bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        drivingKeyFieldName,
        mappedAttributeFieldNames,
        resources.functionalTimestampField,
        resources.validFromField,
        resources.validToField,
        resources.recordSourceField,
        resources.openStartSentinel,
        resources.openEndSentinel,
        scd2Table.isIncludeHashKey());
  }

  private static SharedScd2Resources resolveSharedResources(
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      IVariables variables)
      throws HopException {
    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();

    String sourceDbName = dvConfig.getTargetDatabase();
    if (Utils.isEmpty(sourceDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvScd2PipelineSupport.Error.MissingDvTargetDatabase",
              scd2Table.getName()));
    }
    if (metadataProvider == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvScd2PipelineSupport.Error.MissingMetadataProvider",
              scd2Table.getName()));
    }
    DatabaseMeta sourceDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);

    String targetDbName = bvConfig.getTargetDatabase();
    if (Utils.isEmpty(targetDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvScd2PipelineSupport.Error.MissingBvTargetDatabase",
              scd2Table.getName()));
    }
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);

    String bvTargetTableName =
        !Utils.isEmpty(scd2Table.getTableName()) ? scd2Table.getTableName() : scd2Table.getName();
    String functionalTimestampField =
        resolveFunctionalTimestampField(scd2Table, bvConfig, dvConfig, variables);
    String validFromField = resolveValidFromField(scd2Table, bvConfig, variables);
    String validToField = resolveValidToField(scd2Table, bvConfig, variables);
    String recordSourceField = resolveRecordSourceField(dvConfig, variables);

    String openStartSentinel = bvConfig.getOpenStartSentinel();
    if (Utils.isEmpty(openStartSentinel)) {
      openStartSentinel = BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL;
    }
    openStartSentinel = variables.resolve(openStartSentinel);

    String openEndSentinel = bvConfig.getOpenEndSentinel();
    if (Utils.isEmpty(openEndSentinel)) {
      openEndSentinel = BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL;
    }
    openEndSentinel = variables.resolve(openEndSentinel);

    return new SharedScd2Resources(
        bvConfig,
        dvConfig,
        sourceDatabaseMeta,
        sourceDbName,
        targetDatabaseMeta,
        targetDbName,
        bvTargetTableName,
        functionalTimestampField,
        validFromField,
        validToField,
        recordSourceField,
        openStartSentinel,
        openEndSentinel);
  }

  static List<String> resolveMappedTargetFieldNames(BvScd2Table scd2Table, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return List.of();
    }
    Set<String> names = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (!Utils.isEmpty(targetFieldName)) {
        names.add(targetFieldName);
      }
    }
    return new ArrayList<>(names);
  }

  private static List<BvScd2FieldMapping> resolveFieldMappingsForSatellite(
      BvScd2Table scd2Table, String satelliteName, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return List.of();
    }
    List<BvScd2FieldMapping> mappings = new ArrayList<>();
    String resolvedSatelliteName = variables.resolve(satelliteName);
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping != null
          && resolvedSatelliteName.equals(variables.resolve(mapping.getSatelliteName()))) {
        mappings.add(mapping);
      }
    }
    return mappings;
  }

  private static String resolveSharedDrivingKeyFieldName(
      List<DvSatellite> satellites, IVariables variables) {
    for (DvSatellite satellite : satellites) {
      if (satellite != null && satellite.hasDrivingKey()) {
        return variables.resolve(satellite.getDrivingKey());
      }
    }
    return null;
  }

  public static IRowMeta buildTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    if (hasFieldMappings(scd2Table)) {
      return buildMappedTargetTableLayout(scd2Table, bvConfig, dvModel, variables);
    }
    return buildLegacyTargetTableLayout(
        scd2Table, bvConfig, dvModel, resolveSourceSatellite(scd2Table, dvModel), variables);
  }

  public static IRowMeta buildTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      DvSatellite satellite,
      IVariables variables)
      throws HopException {
    if (hasFieldMappings(scd2Table)) {
      return buildTargetTableLayout(scd2Table, bvConfig, dvModel, variables);
    }
    return buildLegacyTargetTableLayout(scd2Table, bvConfig, dvModel, satellite, variables);
  }

  private static IRowMeta buildLegacyTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      DvSatellite satellite,
      IVariables variables)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    appendGrainFields(rowMeta, scd2Table, dvModel, List.of(satellite), variables);

    for (String attrName : resolveAttributeFieldNames(satellite)) {
      if (satellite.hasDrivingKey()
          && attrName.equals(variables.resolve(satellite.getDrivingKey()))) {
        continue;
      }
      IValueMeta attrMeta = findAttributeValueMeta(satellite, attrName);
      if (attrMeta != null) {
        rowMeta.addValueMeta(attrMeta);
      }
    }

    appendControlFields(rowMeta, scd2Table, bvConfig, dvModel, variables);
    return rowMeta;
  }

  private static IRowMeta buildMappedTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(scd2Table, dvModel);
    if (satellites.isEmpty()) {
      throw new HopException(
          "SCD2 table " + scd2Table.getName() + " must reference a Data Vault satellite derivative");
    }

    RowMeta rowMeta = new RowMeta();
    appendGrainFields(rowMeta, scd2Table, dvModel, satellites, variables);

    Set<String> addedTargets = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String satelliteName = variables.resolve(mapping.getSatelliteName());
      String sourceFieldName = variables.resolve(mapping.getSourceFieldName());
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (Utils.isEmpty(satelliteName)
          || Utils.isEmpty(sourceFieldName)
          || Utils.isEmpty(targetFieldName)
          || !addedTargets.add(targetFieldName)) {
        continue;
      }

      DvSatellite satellite = findSatelliteByName(satellites, satelliteName);
      if (satellite == null) {
        continue;
      }
      IValueMeta sourceMeta = findAttributeValueMeta(satellite, sourceFieldName);
      if (sourceMeta == null) {
        continue;
      }
      rowMeta.addValueMeta(cloneValueMetaWithName(sourceMeta, targetFieldName));
    }

    appendControlFields(rowMeta, scd2Table, bvConfig, dvModel, variables);
    return rowMeta;
  }

  private static void appendGrainFields(
      RowMeta rowMeta,
      BvScd2Table scd2Table,
      DataVaultModel dvModel,
      List<DvSatellite> satellites,
      IVariables variables)
      throws HopException {
    DvSatellite anchorSatellite = satellites.get(0);

    if (scd2Table.isIncludeHashKey()) {
      IValueMeta hashMeta =
          resolveHashKeyValueMeta(
              resolveHashKeyFieldName(anchorSatellite, dvModel, variables), dvModel);
      rowMeta.addValueMeta(hashMeta);
    }

    for (DvSatellite satellite : satellites) {
      if (!satellite.hasDrivingKey()) {
        continue;
      }
      String drivingKeyName = variables.resolve(satellite.getDrivingKey());
      if (Utils.isEmpty(drivingKeyName) || rowMeta.indexOfValue(drivingKeyName) >= 0) {
        continue;
      }
      IValueMeta drivingKeyMeta = findAttributeValueMeta(satellite, drivingKeyName);
      if (drivingKeyMeta != null) {
        rowMeta.addValueMeta(drivingKeyMeta);
      }
    }
  }

  private static void appendControlFields(
      RowMeta rowMeta,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables) {
    rowMeta.addValueMeta(buildRecordSourceValueMeta(dvModel.getConfigurationOrDefault(), variables));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(
            resolveFunctionalTimestampField(
                scd2Table, bvConfig, dvModel.getConfigurationOrDefault(), variables)));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(resolveValidFromField(scd2Table, bvConfig, variables)));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(resolveValidToField(scd2Table, bvConfig, variables)));
  }

  static boolean hasFieldMappings(BvScd2Table scd2Table) {
    return scd2Table != null
        && scd2Table.getFieldMappings() != null
        && !scd2Table.getFieldMappings().isEmpty();
  }

  public static String resolveFunctionalTimestampFieldForSatellite(
      BvScd2Table scd2Table,
      BvScd2SatelliteConfig satelliteConfig,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (satelliteConfig != null
        && !Utils.isEmpty(satelliteConfig.getFunctionalTimestampField())) {
      return variables.resolve(satelliteConfig.getFunctionalTimestampField());
    }
    return resolveFunctionalTimestampField(scd2Table, bvConfig, dvConfig, variables);
  }

  public static String resolveSourceIndicatorValue(
      BvScd2Table scd2Table,
      DvSatellite satellite,
      BvScd2SatelliteConfig satelliteConfig,
      IVariables variables) {
    if (satelliteConfig != null && !Utils.isEmpty(satelliteConfig.getSourceIndicatorValue())) {
      return variables.resolve(satelliteConfig.getSourceIndicatorValue());
    }
    return satellite != null ? variables.resolve(satellite.getName()) : null;
  }

  public static List<DvSatellite> resolveSourceSatellites(BvScd2Table table, DataVaultModel dvModel)
      throws HopException {
    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(table, dvModel);
    if (satellites.isEmpty()) {
      throw new HopException(
          "SCD2 table " + table.getName() + " must reference a Data Vault satellite derivative");
    }
    return satellites;
  }

  public static String resolveRecordSourceField(
      DataVaultConfiguration dvConfig, IVariables variables) {
    String rsFieldName = "RECORD_SOURCE";
    if (dvConfig != null && !Utils.isEmpty(dvConfig.getRecordSourceField())) {
      rsFieldName = dvConfig.getRecordSourceField();
    }
    rsFieldName = variables.resolve(rsFieldName);
    if (Utils.isEmpty(rsFieldName)) {
      rsFieldName = "RECORD_SOURCE";
    }
    return rsFieldName;
  }

  private static IValueMeta buildRecordSourceValueMeta(
      DataVaultConfiguration dvConfig, IVariables variables) {
    String rsFieldName = resolveRecordSourceField(dvConfig, variables);
    String lengthString =
        (dvConfig != null && !Utils.isEmpty(dvConfig.getRecordSourceFieldLength()))
            ? dvConfig.getRecordSourceFieldLength()
            : "100";
    int rsLength = Const.toInt(variables.resolve(lengthString), 100);
    IValueMeta rsMeta = new ValueMetaString(rsFieldName);
    rsMeta.setLength(rsLength);
    return rsMeta;
  }

  public static String buildSatelliteTableInputSql(Scd2BuildContext ctx) {
    if (ctx.isMultiSatellite()) {
      throw new IllegalStateException("Use buildLegTableInputSql for multi-satellite contexts");
    }
    return buildLegTableInputSql(ctx, ctx.legs.get(0));
  }

  static String buildLegTableInputSql(Scd2BuildContext ctx, SatelliteLeg leg) {
    List<String> selectFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    if (ctx.isMultiSatellite()) {
      for (BvScd2FieldMapping mapping : leg.fieldMappings) {
        if (mapping != null && !Utils.isEmpty(mapping.getSourceFieldName())) {
          selectFields.add(
              ctx.sourceDatabaseMeta.quoteField(
                  ctx.variables.resolve(mapping.getSourceFieldName())));
        }
      }
    } else {
      for (String attr : ctx.attributeFieldNames) {
        if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
          continue;
        }
        selectFields.add(ctx.sourceDatabaseMeta.quoteField(attr));
      }
    }
    selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.recordSourceField));
    selectFields.add(
        ctx.sourceDatabaseMeta.quoteField(
            ctx.isMultiSatellite()
                ? leg.sourceFunctionalTimestampField
                : ctx.functionalTimestampField));

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.sourceDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, leg.satelliteTableName));
    sql.append(" ORDER BY ");
    if (ctx.includeHashKey) {
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    } else if (ctx.hasDrivingKey()) {
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    } else if (!selectFields.isEmpty()) {
      sql.append(selectFields.get(0));
    }
    if (ctx.hasDrivingKey() && ctx.includeHashKey) {
      sql.append(", ");
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    sql.append(", ");
    sql.append(
        ctx.sourceDatabaseMeta.quoteField(
            ctx.isMultiSatellite()
                ? leg.sourceFunctionalTimestampField
                : ctx.functionalTimestampField));

    return sql.toString();
  }

  public static String resolveFunctionalTimestampField(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (scd2Table != null && !Utils.isEmpty(scd2Table.getFunctionalTimestampField())) {
      return variables.resolve(scd2Table.getFunctionalTimestampField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getFunctionalTimestampField())) {
      return variables.resolve(bvConfig.getFunctionalTimestampField());
    }
    if (dvConfig != null && !Utils.isEmpty(dvConfig.getLoadDateField())) {
      return variables.resolve(dvConfig.getLoadDateField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getLoadDateFieldFallback())) {
      return variables.resolve(bvConfig.getLoadDateFieldFallback());
    }
    return "LOAD_DATE";
  }

  public static String resolveValidFromField(
      BvScd2Table scd2Table, BusinessVaultConfiguration bvConfig, IVariables variables) {
    if (scd2Table != null && !Utils.isEmpty(scd2Table.getValidFromField())) {
      return variables.resolve(scd2Table.getValidFromField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getValidFromField())) {
      return variables.resolve(bvConfig.getValidFromField());
    }
    return variables.resolve(BusinessVaultConfiguration.DEFAULT_VALID_FROM_FIELD);
  }

  public static String resolveValidToField(
      BvScd2Table scd2Table, BusinessVaultConfiguration bvConfig, IVariables variables) {
    if (scd2Table != null && !Utils.isEmpty(scd2Table.getValidToField())) {
      return variables.resolve(scd2Table.getValidToField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getValidToField())) {
      return variables.resolve(bvConfig.getValidToField());
    }
    return variables.resolve(BusinessVaultConfiguration.DEFAULT_VALID_TO_FIELD);
  }

  static DvSatellite resolveSourceSatellite(BvScd2Table table, DataVaultModel dvModel)
      throws HopException {
    for (BvDerivativeRef ref : table.getDerivatives()) {
      if (ref == null
          || ref.getDvTableType() != DvTableType.SATELLITE
          || Utils.isEmpty(ref.getDvTableName())) {
        continue;
      }
      IDvTable dvTable = dvModel.findTable(ref.getDvTableName());
      if (dvTable instanceof DvSatellite satellite) {
        return satellite;
      }
    }
    throw new HopException(
        "SCD2 table " + table.getName() + " must reference a Data Vault satellite derivative");
  }

  static String resolveHashKeyFieldName(
      DvSatellite satellite, DataVaultModel model, IVariables variables) {
    if (!Utils.isEmpty(satellite.getHubName())) {
      DvHub hub = model.findHub(satellite.getHubName());
      if (hub != null) {
        String hashKey = variables.resolve(hub.getHashKeyFieldName());
        if (!Utils.isEmpty(hashKey)) {
          return hashKey;
        }
        if (!Utils.isEmpty(hub.getBusinessKeys())) {
          return hub.getBusinessKeys().get(0).getName() + "_hk";
        }
      }
    } else if (!Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = model.findLink(satellite.getLinkName());
      if (link != null) {
        String linkHash = variables.resolve(link.getLinkHashKeyFieldName());
        if (!Utils.isEmpty(linkHash)) {
          return linkHash;
        }
        return link.getName() + "_LK";
      }
    }
    return "hashkey";
  }

  static List<String> resolveAttributeFieldNames(DvSatellite satellite) {
    List<String> names = new ArrayList<>();
    if (satellite.getAttributes() == null) {
      return names;
    }
    for (SatelliteAttribute attr : satellite.getAttributes()) {
      if (attr != null && !Utils.isEmpty(attr.getName())) {
        names.add(attr.getName());
      }
    }
    return names;
  }

  static IValueMeta resolveHashKeyValueMeta(String hashKeyName, DataVaultModel dvModel) {
    DataVaultConfiguration config = dvModel.getConfigurationOrDefault();
    HashKeyDataType hdt = config.resolveHashKeyDataType();
    HashAlgorithm algo = config.resolveHashAlgorithm();
    if (algo == null) {
      algo = HashAlgorithm.MD5;
    }
    int digestBytes = algo.getDigestLength();

    if (hdt == HashKeyDataType.BINARY) {
      IValueMeta hashMeta = new ValueMetaBinary(hashKeyName);
      hashMeta.setLength(digestBytes);
      return hashMeta;
    }
    if (hdt == HashKeyDataType.HEX) {
      IValueMeta hashMeta = new ValueMetaString(hashKeyName);
      hashMeta.setLength(digestBytes * 2);
      return hashMeta;
    }
    int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
    IValueMeta hashMeta = new ValueMetaString(hashKeyName);
    hashMeta.setLength(stringMax);
    return hashMeta;
  }

  private static DvSatellite findSatelliteByName(List<DvSatellite> satellites, String name) {
    for (DvSatellite satellite : satellites) {
      if (satellite != null && name.equals(satellite.getName())) {
        return satellite;
      }
    }
    return null;
  }

  private static IValueMeta cloneValueMetaWithName(IValueMeta sourceMeta, String targetName)
      throws HopException {
    try {
      IValueMeta targetMeta =
          ValueMetaFactory.createValueMeta(targetName, sourceMeta.getType());
      targetMeta.setLength(sourceMeta.getLength());
      targetMeta.setPrecision(sourceMeta.getPrecision());
      targetMeta.setConversionMask(sourceMeta.getConversionMask());
      return targetMeta;
    } catch (org.apache.hop.core.exception.HopPluginException e) {
      throw new HopException("Error creating value meta for mapped field " + targetName, e);
    }
  }

  private static IValueMeta findAttributeValueMeta(DvSatellite satellite, String name)
      throws HopException {
    if (satellite.getAttributes() == null) {
      return null;
    }
    for (SatelliteAttribute attr : satellite.getAttributes()) {
      if (attr != null && name.equals(attr.getName())) {
        String dt = attr.getDataType();
        int typeId = IValueMeta.TYPE_STRING;
        if (!Utils.isEmpty(dt)) {
          typeId = ValueMetaFactory.getIdForValueMeta(dt);
          if (typeId <= 0) {
            typeId = IValueMeta.TYPE_STRING;
          }
        }
        try {
          IValueMeta vm = ValueMetaFactory.createValueMeta(name, typeId);
          vm.setLength(Const.toInt(attr.getLength(), -1));
          vm.setPrecision(Const.toInt(attr.getPrecision(), -1));
          return vm;
        } catch (org.apache.hop.core.exception.HopPluginException e) {
          throw new HopException("Error creating value meta for attribute " + name, e);
        }
      }
    }
    return null;
  }

  private static TransformMeta addSatelliteTableInput(Scd2BuildContext ctx, PipelineMeta pipelineMeta) {
    return addLegTableInput(ctx, ctx.legs.get(0), pipelineMeta, LOCATION_START);
  }

  private static TransformMeta addLegTableInput(
      Scd2BuildContext ctx, SatelliteLeg leg, PipelineMeta pipelineMeta, Point location) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildLegTableInputSql(ctx, leg));

    TransformMeta tm =
        new TransformMeta("TableInput", "read_" + leg.satelliteTableName, tableInputMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addLegSourceIndicatorConstant(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point location) {
    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField indicatorField =
        new ConstantField(SOURCE_INDICATOR_FIELD, "String", leg.sourceIndicatorValue);
    constantMeta.getFields().add(indicatorField);

    TransformMeta tm =
        new TransformMeta("Constant", "source_" + leg.satellite.getName(), constantMeta);
    tm.setLocation(location.x + SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addLegSelectValues(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point location)
      throws HopException {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    if (ctx.includeHashKey) {
      selectFields.add(selectField(ctx.hashKeyFieldName, null));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(selectField(ctx.drivingKeyFieldName, null));
    }
    for (BvScd2FieldMapping mapping : leg.fieldMappings) {
      if (mapping == null) {
        continue;
      }
      String sourceFieldName = ctx.variables.resolve(mapping.getSourceFieldName());
      String targetFieldName = ctx.variables.resolve(mapping.getTargetFieldName());
      selectFields.add(selectField(sourceFieldName, targetFieldName));
    }
    selectFields.add(selectField(ctx.recordSourceField, null));
    if (!leg.sourceFunctionalTimestampField.equals(ctx.functionalTimestampField)) {
      selectFields.add(
          selectField(leg.sourceFunctionalTimestampField, ctx.functionalTimestampField));
    } else {
      selectFields.add(selectField(ctx.functionalTimestampField, null));
    }
    selectFields.add(selectField(SOURCE_INDICATOR_FIELD, null));

    TransformMeta tm =
        new TransformMeta("SelectValues", "select_" + leg.satellite.getName(), selectMeta);
    tm.setLocation(location.x + 2 * SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static SelectField selectField(String name, String rename) {
    SelectField selectField = new SelectField();
    selectField.setName(name);
    if (!Utils.isEmpty(rename) && !rename.equals(name)) {
      selectField.setRename(rename);
    }
    return selectField;
  }

  private static TransformMeta addSortedSchemaMerge(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, List<TransformMeta> legOutputs) {
    List<String> inputTransformNames = new ArrayList<>();
    for (TransformMeta legOutput : legOutputs) {
      inputTransformNames.add(legOutput.getName());
    }

    List<SortedSchemaMergeSortKey> sortKeys = new ArrayList<>();
    sortKeys.add(new SortedSchemaMergeSortKey(ctx.hashKeyFieldName, true));
    if (ctx.hasDrivingKey()) {
      sortKeys.add(new SortedSchemaMergeSortKey(ctx.drivingKeyFieldName, true));
    }
    sortKeys.add(new SortedSchemaMergeSortKey(ctx.functionalTimestampField, true));

    SortedSchemaMergeMeta sortedSchemaMergeMeta =
        SortedSchemaMergeMetaFactory.create(inputTransformNames, sortKeys);

    TransformMeta tm =
        new TransformMeta("SortedSchemaMerge", "merge_sorted", sortedSchemaMergeMeta);
    tm.setLocation(LOCATION_START.x + 3 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    for (TransformMeta legOutput : legOutputs) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(legOutput, tm));
    }
    return tm;
  }

  private static TransformMeta addRepeatFields(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    RepeatFieldsMeta repeatFieldsMeta = new RepeatFieldsMeta();
    repeatFieldsMeta.getGroupFields().add(ctx.hashKeyFieldName);
    if (ctx.hasDrivingKey()) {
      repeatFieldsMeta.getGroupFields().add(ctx.drivingKeyFieldName);
    }

    for (BvScd2FieldMapping mapping : ctx.scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String targetFieldName = ctx.variables.resolve(mapping.getTargetFieldName());
      String satelliteName = ctx.variables.resolve(mapping.getSatelliteName());
      if (Utils.isEmpty(targetFieldName) || Utils.isEmpty(satelliteName)) {
        continue;
      }
      SatelliteLeg leg = findLeg(ctx, satelliteName);
      if (leg == null) {
        continue;
      }
      Repeat repeat = new Repeat();
      repeat.setType(RepeatType.CurrentWhenIndicated);
      repeat.setSourceField(targetFieldName);
      repeat.setTargetField(repeatTargetFieldName(targetFieldName));
      repeat.setIndicatorFieldName(SOURCE_INDICATOR_FIELD);
      repeat.setIndicatorValue(leg.sourceIndicatorValue);
      repeatFieldsMeta.getRepeats().add(repeat);
    }

    TransformMeta tm = new TransformMeta("RepeatFields", "repeat_sparse", repeatFieldsMeta);
    tm.setLocation(LOCATION_START.x + 4 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addPostRepeatSelectValues(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    if (ctx.includeHashKey) {
      selectFields.add(selectField(ctx.hashKeyFieldName, null));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(selectField(ctx.drivingKeyFieldName, null));
    }
    selectFields.add(selectField(ctx.functionalTimestampField, null));
    selectFields.add(selectField(SOURCE_INDICATOR_FIELD, ctx.recordSourceField));
    for (String attr : ctx.collapseAttributeFieldNames()) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      selectFields.add(selectField(repeatTargetFieldName(attr), attr));
    }

    TransformMeta tm = new TransformMeta("SelectValues", "select_repeated", selectMeta);
    tm.setLocation(LOCATION_START.x + 5 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static String repeatTargetFieldName(String fieldName) {
    return REPEAT_FIELD_PREFIX + fieldName;
  }

  private static SatelliteLeg findLeg(Scd2BuildContext ctx, String satelliteName) {
    for (SatelliteLeg leg : ctx.legs) {
      if (leg.satellite != null && satelliteName.equals(leg.satellite.getName())) {
        return leg;
      }
    }
    return null;
  }

  private static TransformMeta addAnalyticQuery(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    AnalyticQueryMeta analyticQueryMeta = new AnalyticQueryMeta();
    List<GroupField> partitionFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      partitionFields.add(new GroupField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      partitionFields.add(new GroupField(ctx.drivingKeyFieldName));
    }
    analyticQueryMeta.setGroupFields(partitionFields);

    List<QueryField> queryFields = new ArrayList<>();
    queryFields.add(
        new QueryField(
            ctx.validFromField,
            ctx.functionalTimestampField,
            QueryField.AggregateType.LAG,
            1));
    queryFields.add(
        new QueryField(
            ctx.validToField,
            ctx.functionalTimestampField,
            QueryField.AggregateType.LEAD,
            1));
    analyticQueryMeta.setQueryFields(queryFields);

    String analyticTransformName =
        ctx.isMultiSatellite()
            ? "analytic_" + ctx.bvTargetTableName
            : "analytic_" + ctx.satelliteTableName;
    int analyticX =
        ctx.isMultiSatellite() ? LOCATION_START.x + 6 * SPACING_WIDTH : LOCATION_START.x + SPACING_WIDTH;
    TransformMeta tm =
        new TransformMeta("AnalyticQuery", analyticTransformName, analyticQueryMeta);
    tm.setLocation(analyticX, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addGroupBy(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    GroupByMeta groupByMeta = new GroupByMeta();
    List<GroupingField> groupingFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      groupingFields.add(new GroupingField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      groupingFields.add(new GroupingField(ctx.drivingKeyFieldName));
    }
    for (String attr : ctx.collapseAttributeFieldNames()) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      groupingFields.add(new GroupingField(attr));
    }
    groupByMeta.setGroupingFields(groupingFields);

    List<Aggregation> aggregations = new ArrayList<>();

    Aggregation minAgg = new Aggregation();
    minAgg.setSubject(ctx.validFromField);
    minAgg.setField(ctx.validFromField);
    minAgg.setTypeLabel("MIN");
    aggregations.add(minAgg);

    Aggregation maxAgg = new Aggregation();
    maxAgg.setSubject(ctx.validToField);
    maxAgg.setField(ctx.validToField);
    maxAgg.setTypeLabel("MAX");
    aggregations.add(maxAgg);

    Aggregation rsAgg = new Aggregation();
    rsAgg.setSubject(ctx.recordSourceField);
    rsAgg.setField(ctx.recordSourceField);
    rsAgg.setTypeLabel("CONCAT_COMMA");
    aggregations.add(rsAgg);

    Aggregation tsAgg = new Aggregation();
    tsAgg.setSubject(ctx.functionalTimestampField);
    tsAgg.setField(ctx.functionalTimestampField);
    tsAgg.setTypeLabel("MAX");
    aggregations.add(tsAgg);

    groupByMeta.setAggregations(aggregations);

    String collapseTransformName =
        ctx.isMultiSatellite()
            ? "collapse_" + ctx.bvTargetTableName
            : "collapse_" + ctx.satelliteTableName;
    int collapseX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 8 * SPACING_WIDTH
            : LOCATION_START.x + 3 * SPACING_WIDTH;
    TransformMeta tm = new TransformMeta("GroupBy", collapseTransformName, groupByMeta);
    tm.setLocation(collapseX, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addIfNull(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    IfNullMeta ifNullMeta = new IfNullMeta();
    ifNullMeta.setSelectFields(true);

    Field validFromField = new Field();
    validFromField.setName(ctx.validFromField);
    validFromField.setValue(ctx.openStartSentinel);
    validFromField.setMask("yyyy-MM-dd HH:mm:ss");
    ifNullMeta.getFields().add(validFromField);

    Field validToField = new Field();
    validToField.setName(ctx.validToField);
    validToField.setValue(ctx.openEndSentinel);
    validToField.setMask("yyyy-MM-dd HH:mm:ss");
    ifNullMeta.getFields().add(validToField);

    int ifNullX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 7 * SPACING_WIDTH
            : LOCATION_START.x + 2 * SPACING_WIDTH;
    TransformMeta tm = new TransformMeta("IfNull", "sentinels", ifNullMeta);
    tm.setLocation(ifNullX, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addTableOutput(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    IRowMeta targetLayout =
        buildTargetTableLayout(ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    int tableOutputX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 9 * SPACING_WIDTH
            : LOCATION_START.x + 4 * SPACING_WIDTH;

    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            ctx.bvConfig,
            ctx.variables,
            ctx.targetDatabaseMeta,
            ctx.targetDbName,
            ctx.bvTargetTableName,
            ctx.pipelineName,
            ctx.bvModel.getName(),
            tableOutputX,
            LOCATION_START.y);

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            targetCtx,
            pipelineMeta,
            targetLayout,
            predecessor,
            java.util.Collections.emptySet(),
            true);
    return result.transformMeta;
  }

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table)
      throws HopException {
    try {
      DbCache.clearAll();
      Scd2BuildContext ctx = createContext(metadataProvider, variables, bvModel, dvModel, scd2Table);
      if (ctx == null) {
        return List.of();
      }
      return List.of(generatePipeline(ctx));
    } catch (Exception e) {
      throw new HopException(
          "Error generating SCD2 build pipeline for Business Vault table " + scd2Table.getName(),
          e);
    }
  }

  /** Resolved inputs for one satellite branch in a generated SCD2 build pipeline. */
  public static final class SatelliteLeg {
    final DvSatellite satellite;
    final String satelliteTableName;
    final String sourceIndicatorValue;
    final String sourceFunctionalTimestampField;
    final List<BvScd2FieldMapping> fieldMappings;

    SatelliteLeg(
        DvSatellite satellite,
        String satelliteTableName,
        String sourceIndicatorValue,
        String sourceFunctionalTimestampField,
        List<BvScd2FieldMapping> fieldMappings) {
      this.satellite = satellite;
      this.satelliteTableName = satelliteTableName;
      this.sourceIndicatorValue = sourceIndicatorValue;
      this.sourceFunctionalTimestampField = sourceFunctionalTimestampField;
      this.fieldMappings =
          fieldMappings == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(fieldMappings));
    }
  }

  private static final class SharedScd2Resources {
    final BusinessVaultConfiguration bvConfig;
    final DataVaultConfiguration dvConfig;
    final DatabaseMeta sourceDatabaseMeta;
    final String sourceDbName;
    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String bvTargetTableName;
    final String functionalTimestampField;
    final String validFromField;
    final String validToField;
    final String recordSourceField;
    final String openStartSentinel;
    final String openEndSentinel;

    SharedScd2Resources(
        BusinessVaultConfiguration bvConfig,
        DataVaultConfiguration dvConfig,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String bvTargetTableName,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel) {
      this.bvConfig = bvConfig;
      this.dvConfig = dvConfig;
      this.sourceDatabaseMeta = sourceDatabaseMeta;
      this.sourceDbName = sourceDbName;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.bvTargetTableName = bvTargetTableName;
      this.functionalTimestampField = functionalTimestampField;
      this.validFromField = validFromField;
      this.validToField = validToField;
      this.recordSourceField = recordSourceField;
      this.openStartSentinel = openStartSentinel;
      this.openEndSentinel = openEndSentinel;
    }
  }

  /** Resolved inputs for a generated SCD2 build pipeline. */
  public static final class Scd2BuildContext {
    final BvScd2Table scd2Table;
    final List<SatelliteLeg> legs;
    final boolean multiSatellite;
    final List<String> mappedAttributeFieldNames;
    final BusinessVaultModel bvModel;
    final DataVaultModel dvModel;
    final BusinessVaultConfiguration bvConfig;
    final DataVaultConfiguration dvConfig;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;
    final DatabaseMeta sourceDatabaseMeta;
    final String sourceDbName;
    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String satelliteTableName;
    final String bvTargetTableName;
    final String pipelineName;
    final String hashKeyFieldName;
    final String drivingKeyFieldName;
    final List<String> attributeFieldNames;
    final String functionalTimestampField;
    final String validFromField;
    final String validToField;
    final String recordSourceField;
    final String openStartSentinel;
    final String openEndSentinel;
    final boolean includeHashKey;

    Scd2BuildContext(
        BvScd2Table scd2Table,
        List<SatelliteLeg> legs,
        boolean multiSatellite,
        List<String> mappedAttributeFieldNames,
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
        String satelliteTableName,
        String bvTargetTableName,
        String pipelineName,
        String hashKeyFieldName,
        String drivingKeyFieldName,
        List<String> attributeFieldNames,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel,
        boolean includeHashKey) {
      this.scd2Table = scd2Table;
      this.legs = Collections.unmodifiableList(new ArrayList<>(legs));
      this.multiSatellite = multiSatellite;
      this.mappedAttributeFieldNames =
          mappedAttributeFieldNames == null
              ? List.of()
              : Collections.unmodifiableList(new ArrayList<>(mappedAttributeFieldNames));
      this.bvModel = bvModel;
      this.dvModel = dvModel;
      this.bvConfig = bvConfig;
      this.dvConfig = dvConfig;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.sourceDatabaseMeta = sourceDatabaseMeta;
      this.sourceDbName = sourceDbName;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.satelliteTableName = satelliteTableName;
      this.bvTargetTableName = bvTargetTableName;
      this.pipelineName = pipelineName;
      this.hashKeyFieldName = hashKeyFieldName;
      this.drivingKeyFieldName = drivingKeyFieldName;
      this.attributeFieldNames = attributeFieldNames;
      this.functionalTimestampField = functionalTimestampField;
      this.validFromField = validFromField;
      this.validToField = validToField;
      this.recordSourceField = recordSourceField;
      this.openStartSentinel = openStartSentinel;
      this.openEndSentinel = openEndSentinel;
      this.includeHashKey = includeHashKey;
    }

    /** Legacy test constructor for single-satellite contexts. */
    public Scd2BuildContext(
        BvScd2Table scd2Table,
        DvSatellite satellite,
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
        String satelliteTableName,
        String bvTargetTableName,
        String pipelineName,
        String hashKeyFieldName,
        String drivingKeyFieldName,
        List<String> attributeFieldNames,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel,
        boolean includeHashKey) {
      this(
          scd2Table,
          List.of(
              new SatelliteLeg(
                  satellite,
                  satelliteTableName,
                  satellite.getName(),
                  functionalTimestampField,
                  List.of())),
          false,
          List.of(),
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
          satelliteTableName,
          bvTargetTableName,
          pipelineName,
          hashKeyFieldName,
          drivingKeyFieldName,
          attributeFieldNames,
          functionalTimestampField,
          validFromField,
          validToField,
          recordSourceField,
          openStartSentinel,
          openEndSentinel,
          includeHashKey);
    }

    public DvSatellite getSatellite() {
      return legs.get(0).satellite;
    }

    boolean isMultiSatellite() {
      return multiSatellite;
    }

    List<String> collapseAttributeFieldNames() {
      return multiSatellite ? mappedAttributeFieldNames : attributeFieldNames;
    }

    boolean hasDrivingKey() {
      return !Utils.isEmpty(drivingKeyFieldName);
    }
  }
}