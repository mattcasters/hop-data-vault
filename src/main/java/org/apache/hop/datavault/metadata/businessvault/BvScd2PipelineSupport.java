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
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
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
import org.apache.hop.datavault.metadata.HashAlgorithm;
import org.apache.hop.datavault.metadata.HashKeyDataType;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
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
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;

/**
 * Generates SCD2 build pipelines from DV satellite history using Analytic Query (LAG/LEAD validity
 * bounds), If Null sentinels, and Group By collapse for duplicate timestamps.
 */
public final class BvScd2PipelineSupport {

  private static final Point LOCATION_START = new Point(160, 160);
  private static final int SPACING_WIDTH = 160;

  private BvScd2PipelineSupport() {}

  public static PipelineMeta generatePipeline(Scd2BuildContext ctx) throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);

    TransformMeta tableInput = addSatelliteTableInput(ctx, pipelineMeta);
    TransformMeta analyticQuery = addAnalyticQuery(ctx, pipelineMeta, tableInput);
    TransformMeta ifNull = addIfNull(ctx, pipelineMeta, analyticQuery);
    TransformMeta groupBy = addGroupBy(ctx, pipelineMeta, ifNull);
    addTableOutput(ctx, pipelineMeta, groupBy);

    BvGeneratedPipelineSupport.applyLayout(pipelineMeta);
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

    DvSatellite satellite = resolveSourceSatellite(scd2Table, dvModel);
    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();

    DatabaseMeta sourceDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
    String sourceDbName = dvConfig.getTargetDatabase();
    if (sourceDatabaseMeta == null) {
      throw new HopException(
          "Data Vault target database is required to read satellite history for SCD2 table "
              + scd2Table.getName());
    }

    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
    String targetDbName = bvConfig.getTargetDatabase();
    if (targetDatabaseMeta == null) {
      throw new HopException(
          "Business Vault target database is required to load SCD2 table "
              + scd2Table.getName());
    }

    String satelliteTableName =
        !Utils.isEmpty(satellite.getTableName()) ? satellite.getTableName() : satellite.getName();
    String bvTargetTableName =
        !Utils.isEmpty(scd2Table.getTableName()) ? scd2Table.getTableName() : scd2Table.getName();
    String pipelineName =
        bvConfig.buildScd2PipelineName(variables, bvTargetTableName, satellite.getName());

    String hashKeyFieldName = resolveHashKeyFieldName(satellite, dvModel, variables);
    String drivingKeyFieldName =
        satellite.hasDrivingKey() ? variables.resolve(satellite.getDrivingKey()) : null;
    List<String> attributeFieldNames = resolveAttributeFieldNames(satellite);
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

    return new Scd2BuildContext(
        scd2Table,
        satellite,
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
        scd2Table.isIncludeHashKey());
  }

  public static IRowMeta buildTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      DvSatellite satellite,
      IVariables variables)
      throws HopException {
    RowMeta rowMeta = new RowMeta();

    if (scd2Table.isIncludeHashKey()) {
      IValueMeta hashMeta =
          resolveHashKeyValueMeta(resolveHashKeyFieldName(satellite, dvModel, variables), dvModel);
      rowMeta.addValueMeta(hashMeta);
    }

    if (satellite.hasDrivingKey()) {
      String drivingKeyName = variables.resolve(satellite.getDrivingKey());
      IValueMeta drivingKeyMeta = findAttributeValueMeta(satellite, drivingKeyName);
      if (drivingKeyMeta != null) {
        rowMeta.addValueMeta(drivingKeyMeta);
      }
    }

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

    rowMeta.addValueMeta(buildRecordSourceValueMeta(dvModel.getConfigurationOrDefault(), variables));

    rowMeta.addValueMeta(
        new ValueMetaTimestamp(
            resolveFunctionalTimestampField(
                scd2Table, bvConfig, dvModel.getConfigurationOrDefault(), variables)));

    rowMeta.addValueMeta(
        new ValueMetaTimestamp(resolveValidFromField(scd2Table, bvConfig, variables)));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(resolveValidToField(scd2Table, bvConfig, variables)));

    return rowMeta;
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
    List<String> selectFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    for (String attr : ctx.attributeFieldNames) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      selectFields.add(ctx.sourceDatabaseMeta.quoteField(attr));
    }
    selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.recordSourceField));
    selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.functionalTimestampField));

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.sourceDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.satelliteTableName));
    sql.append(" ORDER BY ");
    if (ctx.includeHashKey) {
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    } else if (ctx.hasDrivingKey()) {
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    } else {
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.attributeFieldNames.get(0)));
    }
    if (ctx.hasDrivingKey() && ctx.includeHashKey) {
      sql.append(", ");
      sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    sql.append(", ");
    sql.append(ctx.sourceDatabaseMeta.quoteField(ctx.functionalTimestampField));

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

  private static IValueMeta resolveHashKeyValueMeta(String hashKeyName, DataVaultModel dvModel) {
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
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName);
    tableInputMeta.setSql(buildSatelliteTableInputSql(ctx));

    TransformMeta tm =
        new TransformMeta("TableInput", "read_" + ctx.satelliteTableName, tableInputMeta);
    tm.setLocation(LOCATION_START);
    pipelineMeta.addTransform(tm);
    return tm;
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

    TransformMeta tm =
        new TransformMeta(
            "AnalyticQuery", "analytic_" + ctx.satelliteTableName, analyticQueryMeta);
    tm.setLocation(LOCATION_START.x + SPACING_WIDTH, LOCATION_START.y);
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
    for (String attr : ctx.attributeFieldNames) {
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

    TransformMeta tm =
        new TransformMeta("GroupBy", "collapse_" + ctx.satelliteTableName, groupByMeta);
    tm.setLocation(LOCATION_START.x + 3 * SPACING_WIDTH, LOCATION_START.y);
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

    TransformMeta tm = new TransformMeta("IfNull", "sentinels", ifNullMeta);
    tm.setLocation(LOCATION_START.x + 2 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addTableOutput(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    IRowMeta targetLayout =
        buildTargetTableLayout(
            ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.satellite, ctx.variables);

    TableOutputMeta tableOutputMeta = new TableOutputMeta();
    tableOutputMeta.setConnection(ctx.targetDbName);
    tableOutputMeta.setTableName(ctx.bvTargetTableName);
    tableOutputMeta.setSpecifyFields(true);
    tableOutputMeta.setTruncateTable(true);
    tableOutputMeta.setCommitSize(ctx.bvConfig.resolveTargetTableCommitSize(ctx.variables));

    for (IValueMeta vm : targetLayout.getValueMetaList()) {
      String name = vm.getName();
      tableOutputMeta.getFields().add(new TableOutputField(name, name));
    }

    TransformMeta tm =
        new TransformMeta("TableOutput", "write_" + ctx.bvTargetTableName, tableOutputMeta);
    tm.setCopiesString(ctx.bvConfig.resolveTargetTableParallelCopies(ctx.variables));
    tm.setLocation(LOCATION_START.x + 4 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
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

  /** Resolved inputs for a generated SCD2 build pipeline. */
  public static final class Scd2BuildContext {
    final BvScd2Table scd2Table;
    final DvSatellite satellite;
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
      this.scd2Table = scd2Table;
      this.satellite = satellite;
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

    boolean hasDrivingKey() {
      return !Utils.isEmpty(drivingKeyFieldName);
    }
  }
}