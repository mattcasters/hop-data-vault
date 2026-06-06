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

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.checksum.CheckSumMeta;
import org.apache.hop.pipeline.transforms.checksum.CheckSumMeta.CheckSumType;
import org.apache.hop.pipeline.transforms.checksum.CheckSumMeta.ResultType;
import org.apache.hop.pipeline.transforms.checksum.Field;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;
import org.apache.hop.pipeline.transforms.sql.ExecSqlMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;

/**
 * Data Vault 2.0 Hub metadata definition.
 *
 * <p>A Hub represents a core business concept (e.g. Customer, Product, Order). It contains the
 * business key(s) and the surrogate hash key derived from them.
 */
@Getter
@GuiPlugin
public class DvHub extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  private static final Class<?> PKG = DvHub.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_HUB_DIALOG";

  private static final Point LOCATION_EXEC_SQL = new Point(160, 48);
  private static final Point LOCATION_SOURCE_TABLE_INPUT = new Point(160, 160);
  private static final Point LOCATION_TARGET_TABLE_INPUT = new Point(160, 320);
  private static final Point LOCATION_MERGE_ROWS = new Point(240, 240);
  private static final Point LOCATION_FILTER_ROWS = new Point(368, 240);
  private static final Point LOCATION_CHECK_SUM = new Point(496, 240);
  private static final Point LOCATION_ADD_CONSTANT = new Point(624, 240);
  private static final Point LOCATION_TABLE_OUTPUT = new Point(752, 240);

  /**
   * The business keys that uniquely identify the hub instance. Composite keys are supported by
   * having multiple entries (order matters for hashing).
   */
  // List of business keys. The @GuiWidgetElement annotation is omitted because
  // GuiElementType does not (currently) include a LIST type. Nested POJO lists are
  // supported via @HopMetadataProperty and will be handled by the metadata editor / serialization.
  @HopMetadataProperty private List<BusinessKey> businessKeys = new ArrayList<>();

  /**
   * The name of the hash key (surrogate key) column for this hub in the target Data Vault tables.
   * Replaces the previous global "hash key suffix" setting in the DataVaultConfiguration.
   */
  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::DvHub.HashKeyFieldName.Label",
      toolTip = "i18n::DvHub.HashKeyFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String hashKeyFieldName;

  /**
   * Optional per-hub name for the record source column (overrides the global one from
   * DataVaultConfiguration). Supports variables.
   */
  @GuiWidgetElement(
      order = "0510",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvHub.RecordSourceFieldName.Label",
      toolTip = "i18n::DvHub.RecordSourceFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceFieldName;

  public DvHub() {
    super();
    this.tableType = DvTableType.HUB;
  }

  public DvHub(String name) {
    super(name);
    this.tableType = DvTableType.HUB;
  }

  public void setBusinessKeys(List<BusinessKey> businessKeys) {
    if (!java.util.Objects.equals(this.businessKeys, businessKeys)) {
      setChanged();
    }
    this.businessKeys = businessKeys;
  }

  public void setHashKeyFieldName(String hashKeyFieldName) {
    if (!java.util.Objects.equals(this.hashKeyFieldName, hashKeyFieldName)) {
      setChanged();
    }
    this.hashKeyFieldName = hashKeyFieldName;
  }

  public void setRecordSourceFieldName(String recordSourceFieldName) {
    if (!java.util.Objects.equals(this.recordSourceFieldName, recordSourceFieldName)) {
      setChanged();
    }
    this.recordSourceFieldName = recordSourceFieldName;
  }

  @Override
  public void check(List<ICheckResult> remarks) {
    super.check(remarks);
    if (Utils.isEmpty(businessKeys)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvHub.CheckResult.NoBusinessKeys"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvHub.CheckResult.HasBusinessKeys", businessKeys.size()),
              this));
    }
  }

  @Override
  public PipelineMeta generateUpdatePipeline(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate)
      throws HopException {
    try {
      if (metadataProvider == null || model == null) {
        return null;
      }

      HubUpdateContext ctx = HubUpdateContext.create(metadataProvider, variables, model, this);
      if (ctx == null) {
        return null;
      }

      PipelineMeta pipelineMeta = new PipelineMeta();
      pipelineMeta.setName(ctx.pipelineName);

      // Add the optional DDL transform first (not wired into the main flow)
      addExecSqlIfNeeded(ctx, pipelineMeta);

      // Source TableInput (from record source)
      TransformMeta sourceTransform = addSourceTableInput(ctx, pipelineMeta);

      // Target TableInput (from target DB, for diff). Includes business key at start and
      // hash at end (per request) so that when combined with passthroughs the layouts align
      // as much as possible.
      TransformMeta targetTransform = addTargetTableInput(ctx, pipelineMeta);

      // MergeRows (diff) if we have a target side. The source table input is the compare leg
      // (direct hop, per request).
      TransformMeta mergeTransform =
          addMergeRowsIfNeeded(ctx, pipelineMeta, sourceTransform, targetTransform);

      // Filter Rows: keep only "new" rows coming out of the diff (for insert into target hub)
      TransformMeta filterTransform = addFilterNewRowsIfNeeded(pipelineMeta, mergeTransform);

      // CheckSum after the filter (only for new rows). The result type (STRING/HEX/BINARY) and
      // length in the final layout still reflect the HashKeyDataType choice from configuration.
      TransformMeta checkSumTransform = addCheckSum(ctx, pipelineMeta);

      // Add Constant transform for the static load date (provided to the method)
      TransformMeta constantTransform = addConstantForLoadDate(ctx, pipelineMeta, loadDate);

      // Add Table Output at the end to write new rows (all target fields except "flag")
      IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);
      TransformMeta tableOutputTransform = addTableOutput(ctx, pipelineMeta, targetLayout);

      // Wire the hops: direct from source table input to MergeRows (compare leg), target to
      // MergeRows (reference), then filter -> checksum (new rows) -> constant -> table output.
      addPipelineHops(
          pipelineMeta,
          sourceTransform,
          targetTransform,
          mergeTransform,
          filterTransform,
          checkSumTransform,
          constantTransform,
          tableOutputTransform);

      return pipelineMeta;
    } catch (Exception e) {
      throw new HopException("Error generating update pipeline for Hub target " + getName(), e);
    }
  }

  // --- Small methods, each adding one transform and referencing the shared context ---

  private void addExecSqlIfNeeded(HubUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      return;
    }
    IRowMeta targetFields =
        ctx.hub.getTargetTableLayout(ctx.metadataProvider, ctx.variables, ctx.model);
    if (targetFields == null) {
      return;
    }
    String ddl = "";
    ILoggingObject loggingObject =
        new SimpleLoggingObject("DvHub.generateUpdatePipeline", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, ctx.variables, ctx.targetDatabaseMeta)) {
      db.connect();
      ddl = db.getDDL(ctx.targetTableName, targetFields);
    } catch (Exception e) {
      throw new HopException("Error getting DDL for target table: " + ctx.targetTableName, e);
    }
    if (!Utils.isEmpty(ddl)) {
      ExecSqlMeta execSqlMeta = new ExecSqlMeta();
      execSqlMeta.setConnection(ctx.targetDbName);
      execSqlMeta.setSql(ddl);
      TransformMeta execSqlTransformMeta =
          new TransformMeta(
              "ExecSql", "Create/update target table " + ctx.targetTableName, execSqlMeta);
      execSqlTransformMeta.setLocation(LOCATION_EXEC_SQL);
      pipelineMeta.addTransform(execSqlTransformMeta);
    }
  }

  private TransformMeta addSourceTableInput(HubUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    TableInputMeta tableInputMeta = new TableInputMeta();

    if (ctx.sourceDatabaseMeta != null) {
      tableInputMeta.setConnection(ctx.sourceDbName);

      List<String> pkQuotedFields = new ArrayList<>();
      for (String fieldName : ctx.pkSourceFieldNames) {
        pkQuotedFields.add(ctx.sourceDatabaseMeta.quoteField(fieldName));
      }

      StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
      if (pkQuotedFields.isEmpty()) {
        sql.append("*");
      } else {
        sql.append(String.join(", ", pkQuotedFields));
      }

      // Add source indicator (record source) column.
      // - If static sourceIndicator on the DataVaultSource: use resolved constant value, aliased.
      // - Else if sourceIndicatorField: include the source column, aliased to the (per-hub or
      // config) record source field name.
      // The alias name is determined preferring DvHub.recordSourceFieldName (resolved), else from
      // config.
      String rsFieldName = ctx.hub.getRecordSourceFieldName();
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = ctx.recordSourceField;
      }
      rsFieldName = ctx.variables.resolve(rsFieldName);
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = "RECORD_SOURCE";
      }
      if (!Utils.isEmpty(ctx.sourceIndicator)) {
        String resolved = ctx.variables.resolve(ctx.sourceIndicator);
        // Build a safe SQL string literal + AS alias (identifier quoted for the dialect)
        String literal = "'" + resolved.replace("'", "''") + "'";
        sql.append(", ")
            .append(literal)
            .append(" AS ")
            .append(ctx.sourceDatabaseMeta.quoteField(rsFieldName));
      } else if (!Utils.isEmpty(ctx.sourceIndicatorField)) {
        String resolvedField = ctx.variables.resolve(ctx.sourceIndicatorField);
        String qField = ctx.sourceDatabaseMeta.quoteField(resolvedField);
        sql.append(", ")
            .append(qField)
            .append(" AS ")
            .append(ctx.sourceDatabaseMeta.quoteField(rsFieldName));
      }

      sql.append(" FROM ");
      sql.append(
          ctx.sourceDatabaseMeta.getQuotedSchemaTableCombination(
              ctx.variables, ctx.sourceSchema, ctx.sourceTable));

      if (!pkQuotedFields.isEmpty()) {
        sql.append(" ORDER BY ").append(String.join(", ", pkQuotedFields));
      }

      tableInputMeta.setSql(sql.toString());
    }

    TransformMeta tm = new TransformMeta("TableInput", ctx.sourceTransformName, tableInputMeta);
    tm.setLocation(LOCATION_SOURCE_TABLE_INPUT);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addTargetTableInput(HubUpdateContext ctx, PipelineMeta pipelineMeta) {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    // business key name from the hub definition (first one)
    String businessKeyName = "id";
    if (!Utils.isEmpty(ctx.hub.getBusinessKeys())) {
      businessKeyName = ctx.hub.getBusinessKeys().get(0).getName();
    }

    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    String quotedBK = ctx.targetDatabaseMeta.quoteField(ctx.variables.resolve(businessKeyName));
    sql.append(quotedBK);

    // Project the record source field on the target side as well (using NULL) so that
    // the input row meta to MergeRows is compatible when we passthrough the indicator
    // value from the source/compare leg.
    String rsFieldName = ctx.hub.getRecordSourceFieldName();
    if (Utils.isEmpty(rsFieldName)) {
      rsFieldName = ctx.recordSourceField;
    }
    rsFieldName = ctx.variables.resolve(rsFieldName);
    if (Utils.isEmpty(rsFieldName)) {
      rsFieldName = "RECORD_SOURCE";
    }
    sql.append(", NULL AS ").append(ctx.targetDatabaseMeta.quoteField(rsFieldName));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    sql.append(" ORDER BY ");
    sql.append(quotedBK);

    targetTableInputMeta.setSql(sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", ctx.targetTransformName, targetTableInputMeta);
    tm.setLocation(LOCATION_TARGET_TABLE_INPUT);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addMergeRowsIfNeeded(
      HubUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta sourceTransform,
      TransformMeta targetTransform) {
    if (targetTransform == null || sourceTransform == null) {
      return null;
    }

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(ctx.targetTransformName); // target as reference
    // Direct hop from source table input as the compare leg (per request).
    mergeRowsMeta.setCompareTransform(sourceTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    for (BusinessKey bk : ctx.hub.getBusinessKeys()) {
      keyFields.add(bk.getName());
    }
    mergeRowsMeta.setKeyFields(keyFields);

    List<PassThroughField> passThroughFields = new ArrayList<>();
    if (!Utils.isEmpty(ctx.hub.getBusinessKeys())) {
      String bkField = ctx.hub.getBusinessKeys().get(0).getName();
      passThroughFields.add(new PassThroughField(bkField, null, false));
    }
    // Pass through the record source indicator from the source (compare) side.
    // It was added (as constant or source column) and aliased in the source TableInput.
    // We use referenceField=false so the value is taken from the "new" / compare leg.
    // Note: hash is not passed through here (it is not present in the source TI); it is
    // added by the post-filter CheckSum on new rows only. The target query includes the
    // hash (at end) for layout compatibility where possible.
    String rsFieldName = ctx.hub.getRecordSourceFieldName();
    if (Utils.isEmpty(rsFieldName)) {
      rsFieldName = ctx.recordSourceField;
    }
    rsFieldName = ctx.variables.resolve(rsFieldName);
    if (!Utils.isEmpty(rsFieldName)) {
      passThroughFields.add(new PassThroughField(rsFieldName, null, false));
    }
    mergeRowsMeta.setPassThroughFields(passThroughFields);

    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_MERGE_ROWS);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addFilterNewRowsIfNeeded(
      PipelineMeta pipelineMeta, TransformMeta mergeTransform) throws HopException {
    if (mergeTransform == null) {
      return null;
    }

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      filterRowsMeta
          .getCompare()
          .setCondition(
              new Condition(
                  "flag",
                  Condition.Function.EQUAL,
                  null,
                  new ValueMetaAndData(new ValueMetaString("static"), "new")));
    } catch (HopValueException e) {
      throw new HopException("Error creating 'new rows' filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "filter_new", filterRowsMeta);
    tm.setLocation(LOCATION_FILTER_ROWS);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addCheckSum(HubUpdateContext ctx, PipelineMeta pipelineMeta) {
    CheckSumMeta checkSumMeta = new CheckSumMeta();
    checkSumMeta.setCheckSumType(ctx.checkSumType);

    List<Field> checkFields = new ArrayList<>();
    for (String fieldName : ctx.pkSourceFieldNames) {
      checkFields.add(new Field(fieldName));
    }
    checkSumMeta.setFields(checkFields);

    // Result field name = the hub's configured hash key field name (or fallback computed from first
    // BK)
    String resultFieldName = ctx.hub.getHashKeyFieldName();
    if (Utils.isEmpty(resultFieldName)) {
      String bkName = "hashkey";
      if (!Utils.isEmpty(ctx.hub.getBusinessKeys())) {
        bkName = ctx.hub.getBusinessKeys().get(0).getName();
      }
      resultFieldName = bkName + "_HK";
    }
    checkSumMeta.setResultFieldName(resultFieldName);

    HashKeyDataType hdt = (ctx.config != null) ? ctx.config.getHashKeyDataType() : HashKeyDataType.BINARY;
    if (hdt == HashKeyDataType.BINARY) {
      checkSumMeta.setResultType(ResultType.BINARY);
    } else if (hdt == HashKeyDataType.HEX) {
      checkSumMeta.setResultType(ResultType.HEXADECIMAL);
    } else {
      // STRING -> the decimal-dash string format (0-255 separated by "-")
      checkSumMeta.setResultType(ResultType.STRING);
    }

    TransformMeta tm = new TransformMeta("CheckSum", "calc_" + resultFieldName, checkSumMeta);
    tm.setLocation(LOCATION_CHECK_SUM);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, Date loadDate) throws HopException {
    String loadDateField = "LOAD_DATE";
    if (ctx.config != null && !Utils.isEmpty(ctx.config.getLoadDateField())) {
      loadDateField = ctx.config.getLoadDateField();
    }
    if (loadDate == null) {
      throw new HopException("Please provide a load date when updating a data vault.");
    }

    ValueMetaDate valueMeta = new ValueMetaDate("ld");
    valueMeta.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    String string = valueMeta.getString(loadDate);

    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField cf = new ConstantField(loadDateField, "Date", string);
    cf.setFieldFormat(valueMeta.getConversionMask());
    constantMeta.getFields().add(cf);

    TransformMeta tm = new TransformMeta("Constant", "add_" + loadDateField, constantMeta);
    tm.setLocation(LOCATION_ADD_CONSTANT);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addTableOutput(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, IRowMeta targetLayout) throws HopException {
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return null;
    }
    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      tableName = ctx.hub.getName();
    }

    try {
      TableOutputMeta tableOutputMeta = new TableOutputMeta();
      tableOutputMeta.setConnection(ctx.targetDbName);
      tableOutputMeta.setTableName(tableName);
      tableOutputMeta.setSpecifyFields(true);

      if (targetLayout != null) {
        for (IValueMeta vm : targetLayout.getValueMetaList()) {
          String name = vm.getName();
          if (!"flag".equalsIgnoreCase(name)) {
            tableOutputMeta.getFields().add(new TableOutputField(name, name));
          }
        }
      }

      TransformMeta tm = new TransformMeta("TableOutput", "write_to_" + tableName, tableOutputMeta);
      tm.setLocation(LOCATION_TABLE_OUTPUT);
      pipelineMeta.addTransform(tm);
      return tm;
    } catch (Exception e) {
      throw new HopException("Error creating Table Output transform", e);
    }
  }

  private void addPipelineHops(
      PipelineMeta pipelineMeta,
      TransformMeta sourceTransform,
      TransformMeta targetTransform,
      TransformMeta mergeTransform,
      TransformMeta filterTransform,
      TransformMeta checkSumTransform,
      TransformMeta constantTransform,
      TransformMeta tableOutputTransform) {
    if (mergeTransform != null) {
      // Direct hop from source table input to Merge Rows as the compare leg (per request).
      if (sourceTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(sourceTransform, mergeTransform));
      }
      if (targetTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(targetTransform, mergeTransform));
      }
      if (filterTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, filterTransform));
        if (checkSumTransform != null) {
          pipelineMeta.addPipelineHop(new PipelineHopMeta(filterTransform, checkSumTransform));
        }
      } else if (checkSumTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, checkSumTransform));
      }
      // After filter: checksum (on new rows) -> constant -> output
      if (checkSumTransform != null && constantTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(checkSumTransform, constantTransform));
      }
    } else if (checkSumTransform != null && constantTransform != null) {
      // No target (no merge): source -> checksum -> constant -> output
      if (sourceTransform != null && checkSumTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(sourceTransform, checkSumTransform));
      }
      if (checkSumTransform != null && constantTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(checkSumTransform, constantTransform));
      }
    }

    // Final hop to TableOutput (when present)
    if (constantTransform != null && tableOutputTransform != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(constantTransform, tableOutputTransform));
    }
  }

  /** Context object holding all loaded / derived objects for the hub update pipeline generation. */
  private static class HubUpdateContext {
    final DvHub hub;
    final DataVaultModel model;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;

    final DataVaultConfiguration config;
    final HashAlgorithm hashAlgorithm;
    final CheckSumType checkSumType;

    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String targetTableName;
    final String pipelineName;

    final String sourceTransformName;
    final String targetTransformName;
    final String hashKeyFieldName;
    final DatabaseMeta sourceDatabaseMeta;
    final String sourceDbName;
    final String sourceSchema;
    final String sourceTable;
    final List<String> pkSourceFieldNames;

    // Record source indicator support (from DataVaultSource + DataVaultConfiguration)
    final String sourceIndicator;
    final String sourceIndicatorField;
    final String recordSourceField;

    HubUpdateContext(
        DvHub hub,
        DataVaultModel model,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultConfiguration config,
        CheckSumType checkSumType,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String targetTableName,
        String pipelineName,
        String sourceTransformName,
        String targetTransformName,
        String hashKeyFieldName,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        String sourceSchema,
        String sourceTable,
        List<String> pkSourceFieldNames,
        String sourceIndicator,
        String sourceIndicatorField,
        String recordSourceField) {
      this.hub = hub;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
      this.hashAlgorithm = (config != null) ? config.getHashAlgorithm() : HashAlgorithm.MD5;
      this.checkSumType = checkSumType;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
      this.sourceTransformName = sourceTransformName;
      this.targetTransformName = targetTransformName;
      this.hashKeyFieldName = hashKeyFieldName != null ? hashKeyFieldName : "hashkey_HK";
      this.sourceDatabaseMeta = sourceDatabaseMeta;
      this.sourceDbName = sourceDbName;
      this.sourceSchema = sourceSchema;
      this.sourceTable = sourceTable;
      this.pkSourceFieldNames = pkSourceFieldNames != null ? pkSourceFieldNames : new ArrayList<>();
      this.sourceIndicator = sourceIndicator;
      this.sourceIndicatorField = sourceIndicatorField;
      this.recordSourceField = recordSourceField != null ? recordSourceField : "RECORD_SOURCE";
    }

    /**
     * Factory: load configuration, target/source database metas, source fields for PKs, compute
     * names, checksum type, etc. Validations (missing critical metadata) throw HopException.
     * Returns null only for top-level null inputs (graceful no-op as before).
     */
    static HubUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvHub hub)
        throws HopException {
      if (metadataProvider == null || model == null || hub == null) {
        return null;
      }

      // Load DataVaultConfiguration
      DataVaultConfiguration config = null;
      String configName = model.getConfigurationName();
      if (!Utils.isEmpty(configName)) {
        config = metadataProvider.getSerializer(DataVaultConfiguration.class).load(configName);
      }

      // checksum type from hash algo in config
      HashAlgorithm hashAlgorithm =
          (config != null) ? config.getHashAlgorithm() : HashAlgorithm.MD5;
      CheckSumType checkSumType = CheckSumType.MD5;
      if (hashAlgorithm != null) {
        switch (hashAlgorithm) {
          case SHA1:
            checkSumType = CheckSumType.SHA1;
            break;
          case SHA256:
            checkSumType = CheckSumType.SHA256;
            break;
          case SHA512:
            checkSumType = CheckSumType.SHA512;
            break;
          default:
        }
      }
      // Record source field name from configuration (used for the indicator column alias +
      // passthrough)
      String recordSourceField = "RECORD_SOURCE";
      if (config != null && !Utils.isEmpty(config.getRecordSourceField())) {
        recordSourceField = config.getRecordSourceField();
      }

      // Target DB
      DatabaseMeta targetDatabaseMeta = null;
      String targetDbName = (config != null) ? config.getTargetDatabase() : null;
      if (!Utils.isEmpty(targetDbName)) {
        targetDatabaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(targetDbName);
        if (targetDatabaseMeta == null) {
          throw new HopException(
              "Target database connection not found in metadata: " + targetDbName);
        }
      }

      String targetTableName =
          !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
      String pipelineName = "hub-" + targetTableName;

      String sourceTransformName = hub.getRecordSource();
      if (Utils.isEmpty(sourceTransformName)) {
        sourceTransformName = hub.getName();
      }

      String targetTransformName = "target_" + targetTableName;

      String hashKeyFieldName = hub.getHashKeyFieldName();
      if (Utils.isEmpty(hashKeyFieldName)) {
        if (!Utils.isEmpty(hub.getBusinessKeys())) {
          hashKeyFieldName = hub.getBusinessKeys().get(0).getName() + "_HK";
        } else {
          hashKeyFieldName = "hashkey_HK";
        }
      }

      // Source side loads (recordSource -> DataVaultSource -> DvDatabaseSource -> DatabaseMeta + PK
      // fields)
      DatabaseMeta sourceDatabaseMeta = null;
      String sourceDbName = null;
      String sourceSchema = null;
      String sourceTable = null;
      List<String> pkSourceFieldNames = new ArrayList<>();
      String sourceIndicator = null;
      String sourceIndicatorField = null;
      String recordSourceName = hub.getRecordSource();
      if (!Utils.isEmpty(recordSourceName)) {
        DataVaultSource dataVaultSource =
            metadataProvider.getSerializer(DataVaultSource.class).load(recordSourceName);
        if (dataVaultSource != null
            && dataVaultSource.getSourceType() == DataVaultSourceType.DATABASE
            && !Utils.isEmpty(dataVaultSource.getSourceTableName())) {
          DvDatabaseSource dbSource =
              metadataProvider
                  .getSerializer(DvDatabaseSource.class)
                  .load(dataVaultSource.getSourceTableName());
          if (dbSource != null) {
            sourceDbName = dbSource.getDatabaseName();
            sourceSchema = dbSource.getSchemaName();
            sourceTable = dbSource.getTableName();
            sourceDatabaseMeta =
                metadataProvider.getSerializer(DatabaseMeta.class).load(sourceDbName);
            if (sourceDatabaseMeta == null) {
              throw new HopException("Database connection not found in metadata: " + sourceDbName);
            }

            sourceIndicator = dataVaultSource.getSourceIndicator();
            sourceIndicatorField = dataVaultSource.getSourceIndicatorField();

            List<SourceField> sourceFields = dataVaultSource.getFields(metadataProvider);
            for (SourceField sf : sourceFields) {
              if (sf.isPrimaryKey()) {
                String fieldName = variables.resolve(sf.getName());
                pkSourceFieldNames.add(fieldName);
              }
            }
          }
        }
      }

      // Basic validation example inside context (more can be added; serious ones throw above)
      // e.g. we could collect warnings but for now the critical loads already validated via throws.

      return new HubUpdateContext(
          hub,
          model,
          metadataProvider,
          variables,
          config,
          checkSumType,
          targetDatabaseMeta,
          targetDbName,
          targetTableName,
          pipelineName,
          sourceTransformName,
          targetTransformName,
          hashKeyFieldName,
          sourceDatabaseMeta,
          sourceDbName,
          sourceSchema,
          sourceTable,
          pkSourceFieldNames,
          sourceIndicator,
          sourceIndicatorField,
          recordSourceField);
    }
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    if (metadataProvider == null || model == null) {
      return null;
    }

    IRowMeta rowMeta = new RowMeta();

    try {
      // Load the DataVaultConfiguration using the metadata provider
      DataVaultConfiguration config = null;
      String configName = model.getConfigurationName();
      if (!Utils.isEmpty(configName)) {
        config = metadataProvider.getSerializer(DataVaultConfiguration.class).load(configName);
      }
      if (config == null) {
        config = new DataVaultConfiguration(); // defaults
      }

      // 1. First column: the hub's hash key field name (or fallback from first business key + _HK)
      String hashKeyName = hashKeyFieldName;
      if (Utils.isEmpty(hashKeyName)) {
        String bkName = "hashkey";
        if (!Utils.isEmpty(businessKeys)) {
          bkName = businessKeys.get(0).getName();
        }
        hashKeyName = bkName + "_HK";
      }

      IValueMeta hashMeta;
      HashKeyDataType hdt = config.getHashKeyDataType();
      HashAlgorithm algo = config.getHashAlgorithm();
      if (algo == null) {
        algo = HashAlgorithm.MD5;
      }
      int digestBytes = algo.getDigestLength();

      if (hdt == HashKeyDataType.BINARY) {
        hashMeta = new ValueMetaBinary(hashKeyName);
        hashMeta.setLength(digestBytes);
      } else if (hdt == HashKeyDataType.HEX) {
        hashMeta = new ValueMetaString(hashKeyName);
        hashMeta.setLength(digestBytes * 2);
      } else {
        // STRING: the decimal-dash format produced by CheckSum ResultType.STRING
        // max length = N*3 + (N-1)  e.g. 63 for MD5
        int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
        hashMeta = new ValueMetaString(hashKeyName);
        hashMeta.setLength(stringMax);
      }
      rowMeta.addValueMeta(hashMeta);

      // Load source for business key types
      List<SourceField> sourceFields = null;
      String recordSourceName = getRecordSource();
      if (!Utils.isEmpty(recordSourceName)) {
        DataVaultSource dataVaultSource =
            metadataProvider.getSerializer(DataVaultSource.class).load(recordSourceName);
        if (dataVaultSource != null) {
          sourceFields = dataVaultSource.getFields(metadataProvider);
        }
      }

      // 2. Then business key source names (type from the source)
      for (BusinessKey bk : businessKeys) {
        String srcFieldName = bk.getSourceFieldName();
        if (Utils.isEmpty(srcFieldName)) {
          srcFieldName = bk.getName();
        }

        IValueMeta bkMeta = null;
        if (sourceFields != null) {
          for (SourceField sf : sourceFields) {
            if (srcFieldName.equals(sf.getName())) {
              int hopType = sf.getHopType();
              if (hopType > 0) {
                bkMeta = ValueMetaFactory.createValueMeta(srcFieldName, hopType);
                // Prefer length/precision declared on the Data Vault Source's SourceField
                // (the actual source column definition) over the BusinessKey metadata.
                String len = !Utils.isEmpty(sf.getLength()) ? sf.getLength() : bk.getLength();
                String prec = !Utils.isEmpty(sf.getPrecision()) ? sf.getPrecision() : null;
                bkMeta.setLength(Const.toInt(len, -1));
                bkMeta.setPrecision(Const.toInt(prec, -1));
                if (!Utils.isEmpty(bk.getDataType()) && bkMeta.getType() == IValueMeta.TYPE_NONE) {
                  throw new HopException(
                      "Please specify a data type for business key " + bk.getName());
                }
              }
              break;
            }
          }
        }
        if (bkMeta == null) {
          // fallback using bk dataType or string (no SourceField available to take length/precision from)
          String dt = bk.getDataType();
          int typeId = IValueMeta.TYPE_STRING;
          if (!Utils.isEmpty(dt)) {
            typeId = ValueMetaFactory.getIdForValueMeta(dt);
            if (typeId <= 0) typeId = IValueMeta.TYPE_STRING;
          }
          bkMeta = ValueMetaFactory.createValueMeta(srcFieldName, typeId);
          bkMeta.setLength(Const.toInt(bk.getLength(), 15));
          bkMeta.setPrecision(-1);
        }
        rowMeta.addValueMeta(bkMeta);
      }

      // Record source column (per-hub name if specified on DvHub, else from config; supports
      // variables)
      // Length from config's recordSourceFieldLength (resolved + Const.toInt default 100)
      String rsFieldName = getRecordSourceFieldName();
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = config.getRecordSourceField();
      }
      rsFieldName = variables.resolve(rsFieldName);
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = "RECORD_SOURCE";
      }
      String lengthString =
          !Utils.isEmpty(config.getRecordSourceFieldLength())
              ? config.getRecordSourceFieldLength()
              : "100";
      lengthString = variables.resolve(lengthString);
      int rsLength = Const.toInt(lengthString, 100);
      IValueMeta rsMeta = new ValueMetaString(rsFieldName);
      rsMeta.setLength(rsLength);
      rowMeta.addValueMeta(rsMeta);

      // 3. Finally the load date field from config, Hop Timestamp type
      String loadDateField = config.getLoadDateField();
      if (Utils.isEmpty(loadDateField)) {
        loadDateField = "LOAD_DATE";
      }
      IValueMeta loadMeta = new ValueMetaTimestamp(loadDateField);
      rowMeta.addValueMeta(loadMeta);

      return rowMeta;

    } catch (Exception e) {
      // Since interface doesn't declare throws, wrap
      throw new HopException("Error building target table layout for hub", e);
    }
  }
}
