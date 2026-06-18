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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
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
import org.apache.hop.metadata.api.IHopMetadataSerializer;
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
import org.apache.hop.pipeline.transforms.sql.ExecSqlMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.jspecify.annotations.NonNull;

/**
 * Data Vault 2.0 Hub metadata definition.
 *
 * <p>A Hub represents a core business concept (e.g. Customer, Product, Order). It contains the
 * business key(s) and the surrogate hash key derived from them.
 */
@GuiPlugin
@Getter
@Setter
public class DvHub extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {
  private static final Class<?> PKG = DvHub.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_HUB_DIALOG";

  private static final Point LOCATION_START_LINE_1 = new Point(160, 48);
  private static final Point LOCATION_START_LINE_2 = new Point(160, 160);
  private static final Point LOCATION_START_LINE_3 = new Point(160, 320);

  public static final int SPACING_WIDTH = 160;

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

  /**
   * List of referenced {@link DataVaultSource} metadata elements (stored by name) that can feed
   * this table. A Hub can have multiple sources.
   */
  @HopMetadataProperty(key = "recordSource", groupKey = "recordSources")
  protected List<String> recordSources = new ArrayList<>();

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
  public void check(List<ICheckResult> remarks, IHopMetadataProvider metadataProvider, IVariables variables) {
    super.check(remarks, metadataProvider, variables);
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
      for (BusinessKey bk : businessKeys) {
        if (Utils.isEmpty(bk.getName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvHub.CheckResult.BusinessKeyNoName"),
                  this));
        }

        // Validate that the source field (sourceFieldName or fallback to name) of this business
        // key exists in the DataVaultSource referenced by its recordSourceName.
        String sourceField = bk.getSourceFieldName();
        if (Utils.isEmpty(sourceField)) {
          sourceField = bk.getName();
        }
        if (!Utils.isEmpty(sourceField)
            && !Utils.isEmpty(bk.getRecordSourceName())
            && metadataProvider != null) {
          try {
            String resolvedRecordSource =
                variables != null
                    ? variables.resolve(bk.getRecordSourceName())
                    : bk.getRecordSourceName();
            DataVaultSource recordSource =
                metadataProvider
                    .getSerializer(DataVaultSource.class)
                    .load(resolvedRecordSource);
            if (recordSource != null) {
              List<SourceField> fields = recordSource.getFields(metadataProvider);
              boolean exists = false;
              String resolvedSourceField =
                  variables != null ? variables.resolve(sourceField) : sourceField;
              for (SourceField sf : fields) {
                String sfName = variables != null ? variables.resolve(sf.getName()) : sf.getName();
                if (resolvedSourceField != null && resolvedSourceField.equals(sfName)) {
                  exists = true;
                  break;
                }
              }
              if (!exists) {
                remarks.add(
                    new CheckResult(
                        ICheckResult.TYPE_RESULT_ERROR,
                        "Source field '"
                            + sourceField
                            + "' of business key '"
                            + bk.getName()
                            + "' does not exist in record source '"
                            + bk.getRecordSourceName()
                            + "'",
                        this));
              }
            }
          } catch (HopException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    "Error loading record source '"
                        + bk.getRecordSourceName()
                        + "' for business key '"
                        + bk.getName()
                        + "': "
                        + e.getMessage(),
                    this));
          }
        }
      }
    }

    if (Utils.isEmpty(hashKeyFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvHub.CheckResult.NoHashKeyFieldName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvHub.CheckResult.HasHashKeyFieldName", hashKeyFieldName),
              this));
    }

    if (Utils.isEmpty(recordSourceFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvHub.CheckResult.NoRecordSourceFieldName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvHub.CheckResult.HasRecordSourceFieldName", recordSourceFieldName),
              this));
    }
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    List<PipelineMeta> result = new ArrayList<>();
    try {
      if (metadataProvider == null || model == null) {
        return result;
      }

      List<DataVaultSource> sources =
          loadRecordSources(variables, metadataProvider, recordSourceGroup);
      if (sources.isEmpty()) {
        if (!Utils.isEmpty(recordSourceGroup)) {
          return result;
        }
        throw new HopException("Please specify one or more record sources in Hub " + getName());
      }

      for (DataVaultSource src : sources) {
        HubUpdateContext ctx =
            HubUpdateContext.create(metadataProvider, variables, model, this, src);
        if (ctx == null) continue;

        PipelineMeta pipelineMeta = new PipelineMeta();
        String baseName = ctx.pipelineName;
        if (sources.size() > 1 && src.getName() != null) {
          pipelineMeta.setName(baseName + "_" + src.getName());
        } else {
          pipelineMeta.setName(baseName);
        }

        // Source TableInput (from record source)
        TransformMeta sourceInputTransform = addSourceTableInput(ctx, src, pipelineMeta);

        // Target TableInput (from target DB, for diff). Includes business key at start and
        // hash at end (per request) so that when combined with passthroughs the layouts align
        // as much as possible.
        TransformMeta targetInputTransform = addTargetTableInput(ctx, pipelineMeta);

        // MergeRows (diff) if we have a target side. The source table input is the compare leg
        // (direct hop, per request).
        TransformMeta mergeTransform =
            addMergeRows(ctx, pipelineMeta, sourceInputTransform, targetInputTransform);

        // Filter Rows: keep only "new" rows coming out of the diff (for insert into target hub)
        TransformMeta filterTransform = addFilterNewRows(pipelineMeta, mergeTransform);

        // CheckSum after the filter (only for new rows). The result type (STRING/HEX/BINARY) and
        // length in the final layout still reflect the HashKeyDataType choice from configuration.
        TransformMeta checkSumTransform = addCheckSum(ctx, pipelineMeta, filterTransform);

        // Add Constant transform for the static load date (provided to the method)
        TransformMeta constantTransform =
            addConstantForLoadDate(ctx, pipelineMeta, loadDate, checkSumTransform);

        // Add Table Output at the end to write new rows (all target fields except "flag")
        IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);
        TransformMeta tableOutputTransform =
            addTableOutput(ctx, pipelineMeta, targetLayout, constantTransform);

        result.add(pipelineMeta);
      }
      return result;
    } catch (Exception e) {
      throw new HopException("Error generating update pipeline(s) for Hub target " + getName(), e);
    }
  }

  private List<DataVaultSource> loadRecordSources(
      IVariables variables, IHopMetadataProvider metadataProvider, String recordSourceGroup)
      throws HopException {
    List<DataVaultSource> sources = new ArrayList<>();

    IHopMetadataSerializer<DataVaultSource> serializer =
        metadataProvider.getSerializer(DataVaultSource.class);

    for (String recordSource : recordSources) {
      DataVaultSource source = serializer.load(variables.resolve(recordSource));
      if (source != null && source.matchesRecordSourceGroup(recordSourceGroup, variables)) {
        sources.add(source);
      }
    }
    return sources;
  }

  @Override
  public Map<DatabaseMeta, List<String>> generateUpdateDdl(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    Map<DatabaseMeta, List<String>> result = new HashMap<>();
    if (metadataProvider == null || model == null) {
      return result;
    }

    // Resolve the effective target configuration (same logic as context creation and getTargetTableLayout)
    DataVaultConfiguration config = null;
    String configName = model.getConfigurationName();
    if (!Utils.isEmpty(configName)) {
      config = metadataProvider.getSerializer(DataVaultConfiguration.class).load(configName);
    }
    if (config == null) {
      config = new DataVaultConfiguration();
    }

    DatabaseMeta targetDatabaseMeta = null;
    String targetDbName = (config != null) ? config.getTargetDatabase() : null;
    if (!Utils.isEmpty(targetDbName)) {
      targetDatabaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(targetDbName);
      if (targetDatabaseMeta == null) {
        throw new HopException(
            "Target database connection not found in metadata: " + targetDbName);
      }
    }
    if (targetDatabaseMeta == null) {
      return result;
    }

    String targetTableName =
        !Utils.isEmpty(getTableName()) ? getTableName() : getName();

    IRowMeta targetFields = getTargetTableLayout(metadataProvider, variables, model);
    if (targetFields == null) {
      return result;
    }

    String ddl = "";
    ILoggingObject loggingObject =
        new SimpleLoggingObject("DvHub.generateUpdateDdl", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
      db.connect();
      ddl = db.getDDL(targetTableName, targetFields);
    } catch (Exception e) {
      throw new HopException("Error getting DDL for target table: " + targetTableName, e);
    }
    if (!Utils.isEmpty(ddl)) {
      result.computeIfAbsent(targetDatabaseMeta, k -> new ArrayList<>()).add(ddl);
    }
    return result;
  }

  // --- Small methods, each adding one transform and referencing the shared context ---

  private TransformMeta addSourceTableInput(
      HubUpdateContext ctx, DataVaultSource dataVaultSource, PipelineMeta pipelineMeta)
      throws HopException {
    // Let's ask our source to provide the needed Hop transforms.
    //
    DvDatabaseHubSourcePipelineBuilder builder =
        new DvDatabaseHubSourcePipelineBuilder(
            ctx.variables,
            ctx.metadataProvider,
            ctx.model,
            pipelineMeta,
            dataVaultSource,
            ctx.dvSource,
            this,
            new Point(LOCATION_START_LINE_2.x, LOCATION_START_LINE_2.y));
    builder.build();

    return builder.getResultTransform();
  }

  private TransformMeta addTargetTableInput(HubUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    // Generate the SQL to read from the Hub target table.
    //
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");

    // First add all the business key(s)
    List<String> bkQuotedBkFields = new ArrayList<>();
    for (BusinessKey key : businessKeys) {
      String quotedBkField =
          ctx.targetDatabaseMeta.quoteField(ctx.variables.resolve(key.getName()));
      bkQuotedBkFields.add(quotedBkField);
    }
    sql.append(StringUtils.join(bkQuotedBkFields, ","));

    // The source indicator field is called recordSourceFieldName
    // If it's nog specified we look at the global configuration.
    //
    String rsFieldName = calculateRecordSourceFieldName(ctx);
    sql.append(", NULL AS ").append(ctx.targetDatabaseMeta.quoteField(rsFieldName));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    sql.append(" ORDER BY ");
    sql.append(StringUtils.join(bkQuotedBkFields, ","));

    targetTableInputMeta.setSql(sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", ctx.targetTransformName, targetTableInputMeta);
    tm.setLocation(LOCATION_START_LINE_3);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private @NonNull String calculateRecordSourceFieldName(HubUpdateContext ctx) throws HopException {
    String rsFieldName = ctx.variables.resolve(recordSourceFieldName);
    if (StringUtils.isEmpty(rsFieldName)) {
      rsFieldName = ctx.variables.resolve(ctx.config.getRecordSourceField());
    }
    if (StringUtils.isEmpty(rsFieldName)) {
      throw new HopException(
          "No source field is specified for the target table in either the Hub or the data vault configuration.");
    }
    return rsFieldName;
  }

  private TransformMeta addMergeRows(
      HubUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform)
      throws HopException {
    if (referenceTransform == null || compareTransform == null) {
      return null;
    }

    // A bug in hop requires an extra dummy to read from both reference and compare transforms
    //
    TransformMeta referenceDummyTransform =
        addDummyTransform(
            pipelineMeta,
            referenceTransform,
            "Merge reference",
            LOCATION_START_LINE_3.x + SPACING_WIDTH,
            LOCATION_START_LINE_3.y);
    TransformMeta compareDummyTransform =
        addDummyTransform(
            pipelineMeta,
            compareTransform,
            "Merge compare",
            LOCATION_START_LINE_2.x + SPACING_WIDTH,
            LOCATION_START_LINE_2.y);

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceDummyTransform.getName());
    mergeRowsMeta.setCompareTransform(compareDummyTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    for (BusinessKey bk : businessKeys) {
      keyFields.add(bk.getName());
    }
    mergeRowsMeta.setKeyFields(keyFields);
    
    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 2 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceDummyTransform, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareDummyTransform, tm));

    return tm;
  }

  private TransformMeta addFilterNewRows(PipelineMeta pipelineMeta, TransformMeta mergeTransform)
      throws HopException {
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
    tm.setLocation(LOCATION_START_LINE_3.x + 3 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, tm));
    return tm;
  }

  private TransformMeta addCheckSum(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    CheckSumMeta checkSumMeta = new CheckSumMeta();
    checkSumMeta.setCheckSumType(ctx.checkSumType);

    // A hash key is calculated over all the business key field
    //
    for (BusinessKey bk : businessKeys) {
      checkSumMeta.getFields().add(new Field(bk.getName()));
    }

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

    HashKeyDataType hdt =
        (ctx.config != null) ? ctx.config.getHashKeyDataType() : HashKeyDataType.BINARY;
    if (hdt == HashKeyDataType.BINARY) {
      checkSumMeta.setResultType(ResultType.BINARY);
    } else if (hdt == HashKeyDataType.HEX) {
      checkSumMeta.setResultType(ResultType.HEXADECIMAL);
    } else {
      // STRING -> the decimal-dash string format (0-255 separated by "-")
      checkSumMeta.setResultType(ResultType.STRING);
    }

    TransformMeta tm = new TransformMeta("CheckSum", "calc_" + resultFieldName, checkSumMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 4 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, Date loadDate, TransformMeta predecessor)
      throws HopException {
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
    tm.setLocation(LOCATION_START_LINE_3.x + 5 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addTableOutput(
      HubUpdateContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor)
      throws HopException {
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
      tm.setLocation(LOCATION_START_LINE_3.x + 6 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return tm;
    } catch (Exception e) {
      throw new HopException("Error creating Table Output transform", e);
    }
  }

  /**
   * Find the business key with the given name.
   *
   * @param keyName The name of the key to look for.
   * @return The key or null if it couldn't be found.
   */
  public BusinessKey findBusinessKey(String keyName) {
    for (BusinessKey key : businessKeys) {
      if (key.getName().equalsIgnoreCase(keyName)) {
        return key;
      }
    }
    return null;
  }

  public List<String> getBusinessKeyFieldNames() {
    List<String> names = new ArrayList<>();
    for (BusinessKey key : businessKeys) {
      names.add(key.getName());
    }
    return names;
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

    final DataVaultSource dataVaultSource;
    final IDvSource dvSource;
    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String targetTableName;
    final String pipelineName;

    final String sourceTransformName;
    final String targetTransformName;
    final String hashKeyFieldName;

    HubUpdateContext(
        DvHub hub,
        DataVaultModel model,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultConfiguration config,
        CheckSumType checkSumType,
        DataVaultSource dataVaultSource,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String targetTableName,
        String pipelineName,
        String sourceTransformName,
        String targetTransformName,
        String hashKeyFieldName)
        throws HopException {
      this.hub = hub;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
      this.hashAlgorithm = (config != null) ? config.getHashAlgorithm() : HashAlgorithm.MD5;
      this.checkSumType = checkSumType;
      this.dataVaultSource = dataVaultSource;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
      this.sourceTransformName = sourceTransformName;
      this.targetTransformName = targetTransformName;
      this.hashKeyFieldName = hashKeyFieldName != null ? hashKeyFieldName : "hashkey_HK";

      this.dvSource = dataVaultSource.getDvSource(metadataProvider);
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
        DvHub hub,
        DataVaultSource specificSource)
        throws HopException {
      if (metadataProvider == null || model == null || hub == null) {
        return null;
      }

      if (specificSource == null) {
        throw new HopException("Please specify a specific data vault source");
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

      String sourceTransformName;
      sourceTransformName = specificSource.getName();
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

      // Source side loads (using specificSource or first from hub's recordSources)
      DatabaseMeta sourceDatabaseMeta = null;
      String sourceDbName = null;
      String sourceSchema = null;
      String sourceTable = null;
      List<String> pkSourceFieldNames = new ArrayList<>();
      String sourceIndicator = null;
      String sourceIndicatorField = null;

      // Basic validation example inside context (more can be added; serious ones throw above)
      // e.g. we could collect warnings but for now the critical loads already validated via throws.

      return new HubUpdateContext(
          hub,
          model,
          metadataProvider,
          variables,
          config,
          checkSumType,
          specificSource,
          targetDatabaseMeta,
          targetDbName,
          targetTableName,
          pipelineName,
          sourceTransformName,
          targetTransformName,
          hashKeyFieldName);
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

      // 2. Then business keys (type from the metadata)
      //
      for (BusinessKey bk : businessKeys) {
        String srcFieldName = bk.getSourceFieldName();
        if (Utils.isEmpty(srcFieldName)) {
          srcFieldName = bk.getName();
        }

        int type = ValueMetaFactory.getIdForValueMeta(variables.resolve(bk.getDataType()));
        int length = Const.toInt(variables.resolve(bk.getLength()), -1);
        int precision = Const.toInt(variables.resolve(bk.getPrecision()), -1);

        IValueMeta bkMeta = ValueMetaFactory.createValueMeta(srcFieldName, type, length, precision);

        if (bkMeta == null || bkMeta.getType() == IValueMeta.TYPE_NONE) {
          throw new HopException(
              "Please specify a valid data type for business key "
                  + bk.getName()
                  + " in Hub "
                  + getName());
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

  @Override
  public int ensureSpecialRecords(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    return DvSpecialRecordSupport.ensureHubSpecialRecords(
        this, metadataProvider, variables, model, loadDate, loggingObject);
  }
}
