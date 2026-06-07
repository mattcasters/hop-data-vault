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
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaDate;
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
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;

/**
 * Data Vault 2.0 Link metadata definition.
 *
 * <p>A Link represents a relationship (many-to-many or association) between Hubs. The Link's hash
 * key is typically computed from the hash keys of the participating Hubs (plus any additional
 * descriptors for the relationship).
 */
@GuiPlugin
public class DvLink extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  private static final Class<?> PKG = DvLink.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_LINK_DIALOG";

  private static final Point LOCATION_START_LINE_1 = new Point(160, 48);
  private static final Point LOCATION_START_LINE_2 = new Point(160, 160);
  private static final Point LOCATION_START_LINE_3 = new Point(160, 320);

  public static final int SPACING_WIDTH = 160;

  /**
   * Names of the Hubs that participate in this link (order can matter for hashing in some
   * implementations). These are references by metadata name (storeWithName behavior when used in a
   * model).
   */
  // Participating hubs (references by name). Gui list widget omitted (no LIST in GuiElementType).
  @HopMetadataProperty private List<String> hubNames = new ArrayList<>();

  /**
   * Optional driving key(s) - used when the same Hub appears more than once in a link (e.g. "from
   * location" vs "to location" in a route).
   */
  // Driving keys list - Gui annotation omitted for same reason.
  @HopMetadataProperty private List<String> drivingKeyNames = new ArrayList<>();

  /**
   * Optional name for the link's surrogate hash key column. If empty, defaults to the link name +
   * "_LK".
   */
  @GuiWidgetElement(
      order = "0650",
      type = GuiElementType.TEXT,
      label = "i18n::DvLink.LinkHashKeyFieldName.Label",
      toolTip = "i18n::DvLink.LinkHashKeyFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String linkHashKeyFieldName;

  /**
   * Per-hub source field mappings for computing the hub hashes from this link's record source. Used
   * when the link source uses different column names than the hub's business key names (e.g.
   * customer_fk instead of customer_id, or _id / _fk conventions).
   */
  @HopMetadataProperty(key = "hubSourceKeyField", groupKey = "hubSourceKeyFields")
  private List<HubSourceKeyField> hubSourceKeyFields = new ArrayList<>();

  /** Whether this link carries additional relationship attributes (i.e. has its own satellite). */
  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvLink.HasDescriptiveAttributes.Label",
      toolTip = "i18n::DvLink.HasDescriptiveAttributes.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean hasDescriptiveAttributes;

  public DvLink() {
    super();
    this.tableType = DvTableType.LINK;
  }

  public DvLink(String name) {
    super(name);
    this.tableType = DvTableType.LINK;
  }

  public List<String> getHubNames() {
    return hubNames;
  }

  public void setHubNames(List<String> hubNames) {
    if (!java.util.Objects.equals(this.hubNames, hubNames)) {
      setChanged();
    }
    this.hubNames = hubNames;
  }

  public List<String> getDrivingKeyNames() {
    return drivingKeyNames;
  }

  public void setDrivingKeyNames(List<String> drivingKeyNames) {
    if (!java.util.Objects.equals(this.drivingKeyNames, drivingKeyNames)) {
      setChanged();
    }
    this.drivingKeyNames = drivingKeyNames;
  }

  public String getLinkHashKeyFieldName() {
    return linkHashKeyFieldName;
  }

  public void setLinkHashKeyFieldName(String linkHashKeyFieldName) {
    if (!java.util.Objects.equals(this.linkHashKeyFieldName, linkHashKeyFieldName)) {
      setChanged();
    }
    this.linkHashKeyFieldName = linkHashKeyFieldName;
  }

  public List<HubSourceKeyField> getHubSourceKeyFields() {
    return hubSourceKeyFields;
  }

  public void setHubSourceKeyFields(List<HubSourceKeyField> hubSourceKeyFields) {
    if (!java.util.Objects.equals(this.hubSourceKeyFields, hubSourceKeyFields)) {
      setChanged();
    }
    this.hubSourceKeyFields = hubSourceKeyFields != null ? hubSourceKeyFields : new ArrayList<>();
  }

  public boolean isHasDescriptiveAttributes() {
    return hasDescriptiveAttributes;
  }

  public void setHasDescriptiveAttributes(boolean hasDescriptiveAttributes) {
    if (this.hasDescriptiveAttributes != hasDescriptiveAttributes) {
      setChanged();
    }
    this.hasDescriptiveAttributes = hasDescriptiveAttributes;
  }

  @Override
  public void check(List<ICheckResult> remarks) {
    super.check(remarks);
    if (Utils.isEmpty(hubNames) || hubNames.size() < 2) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NotEnoughHubs"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.ConnectedToHubs", hubNames.size()),
              this));
    }

    if (Utils.isEmpty(linkHashKeyFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NoLinkHashKeyFieldName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.HasLinkHashKeyFieldName", linkHashKeyFieldName),
              this));
    }

    if (Utils.isEmpty(drivingKeyNames)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NoDrivingKeys"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.HasDrivingKeys", drivingKeyNames.size()),
              this));
      for (String dk : drivingKeyNames) {
        if (Utils.isEmpty(dk)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvLink.CheckResult.DrivingKeyNoName"),
                  this));
        }
      }
    }

    if (hasDescriptiveAttributes) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvLink.CheckResult.HasDescriptiveAttributes"),
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

      LinkUpdateContext ctx = LinkUpdateContext.create(metadataProvider, variables, model, this);
      if (ctx == null) {
        return null;
      }

      PipelineMeta pipelineMeta = new PipelineMeta();
      pipelineMeta.setName(ctx.pipelineName);

      addExecSqlIfNeeded(ctx, pipelineMeta);

      TransformMeta sourceTransform = addSourceTableInput(ctx, pipelineMeta);

      // Compute hub hashes for each participating hub (from their BKs in the source)
      List<TransformMeta> hubHashTransforms = new ArrayList<>();
      TransformMeta lastHubHash = sourceTransform;
      for (int i = 0; i < ctx.participatingHubs.size(); i++) {
        String hubHashName = ctx.hubHashFieldNames.get(i);
        // The source fields for this hub's BKs
        List<String> hubBkFields = ctx.hubBkFieldGroups.get(i);
        TransformMeta hubHashCalc =
            addCheckSumForFields(
                pipelineMeta,
                lastHubHash,
                ctx.checkSumType,
                hubBkFields,
                hubHashName,
                ctx.config,
                i);
        hubHashTransforms.add(hubHashCalc);
        lastHubHash = hubHashCalc;
      }

      // Final checksum for the Link Hash itself: inputs are the computed hub hashes + driving keys
      List<String> linkHashInputs = new ArrayList<>(ctx.hubHashFieldNames);
      linkHashInputs.addAll(ctx.drivingKeyFieldNames);
      TransformMeta linkHashCalc =
          addCheckSumForFields(
              pipelineMeta,
              lastHubHash,
              ctx.checkSumType,
              linkHashInputs,
              ctx.linkHashKeyFieldName,
              ctx.config,
              ctx.participatingHubs.size());

      // Wire the checksum chain (source -> hub hashes -> link hash)
      TransformMeta prev = sourceTransform;
      for (TransformMeta h : hubHashTransforms) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(prev, h));
        prev = h;
      }
      pipelineMeta.addPipelineHop(new PipelineHopMeta(prev, linkHashCalc));

      // Target side for diff (on the link hash)
      TransformMeta targetTransform = addTargetTableInput(ctx, pipelineMeta);

      TransformMeta selectTransform = null;
      TransformMeta sortTransform = null;
      if (targetTransform != null) {
        // Select only the calculated hash key fields (link hash + hub hashes) + record source
        // so that the row layout matches exactly what the target side provides to MergeRows.
        selectTransform = addSelectRows(ctx, pipelineMeta, linkHashCalc);

        // Sort on the link hash (required for MergeRows)
        sortTransform = addSortRows(pipelineMeta, selectTransform, ctx, ctx.linkHashKeyFieldName);
      }

      // MergeRows on the link hash
      TransformMeta mergeTransform =
          addMergeRowsIfNeeded(ctx, pipelineMeta, sortTransform, targetTransform);

      // Filter new
      TransformMeta filterTransform = addFilterNewRowsIfNeeded(pipelineMeta, mergeTransform);

      // Constant load date
      TransformMeta constantTransform = addConstantForLoadDate(ctx, pipelineMeta, loadDate);

      // Table output
      IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);
      TransformMeta tableOutputTransform = addTableOutput(ctx, pipelineMeta, targetLayout);

      addPipelineHops(
          pipelineMeta,
          sourceTransform,
          targetTransform,
          mergeTransform,
          filterTransform,
          linkHashCalc,
          selectTransform,
          sortTransform,
          constantTransform,
          tableOutputTransform);

      return pipelineMeta;
    } catch (Exception e) {
      throw new HopException("Error generating update pipeline for Link target " + getName(), e);
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
      DataVaultConfiguration config = null;
      String configName = model.getConfigurationName();
      if (!Utils.isEmpty(configName)) {
        config = metadataProvider.getSerializer(DataVaultConfiguration.class).load(configName);
      }
      if (config == null) {
        config = new DataVaultConfiguration();
      }

      // 1. The link's own hash key (LHK)
      String linkHashName = getLinkHashKeyFieldName();
      if (Utils.isEmpty(linkHashName)) {
        linkHashName = getName() + "_LK";
      }
      HashKeyDataType hdt = config.getHashKeyDataType();
      HashAlgorithm algo = config.getHashAlgorithm();
      if (algo == null) algo = HashAlgorithm.MD5;
      int digestBytes = algo.getDigestLength();

      IValueMeta linkHashMeta;
      if (hdt == HashKeyDataType.BINARY) {
        linkHashMeta = new ValueMetaBinary(linkHashName);
        linkHashMeta.setLength(digestBytes);
      } else if (hdt == HashKeyDataType.HEX) {
        linkHashMeta = new ValueMetaString(linkHashName);
        linkHashMeta.setLength(digestBytes * 2);
      } else {
        int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
        linkHashMeta = new ValueMetaString(linkHashName);
        linkHashMeta.setLength(stringMax);
      }
      rowMeta.addValueMeta(linkHashMeta);

      // 2. The participating hub hash keys (in the order defined in the link)
      for (String hubName : hubNames) {
        DvHub hub = model.findHub(hubName);
        if (hub == null) {
          throw new HopException("Linked hub not found: " + hubName + " for link " + getName());
        }
        String hubHashCol = hub.getHashKeyFieldName();
        if (Utils.isEmpty(hubHashCol)) {
          if (!Utils.isEmpty(hub.getBusinessKeys())) {
            hubHashCol = hub.getBusinessKeys().get(0).getName() + "_HK";
          } else {
            hubHashCol = hub.getName() + "_HK";
          }
        }
        // Same type/length as a normal hub hash
        IValueMeta hubHashMeta;
        if (hdt == HashKeyDataType.BINARY) {
          hubHashMeta = new ValueMetaBinary(hubHashCol);
          hubHashMeta.setLength(digestBytes);
        } else if (hdt == HashKeyDataType.HEX) {
          hubHashMeta = new ValueMetaString(hubHashCol);
          hubHashMeta.setLength(digestBytes * 2);
        } else {
          int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
          hubHashMeta = new ValueMetaString(hubHashCol);
          hubHashMeta.setLength(stringMax);
        }
        rowMeta.addValueMeta(hubHashMeta);
      }

      // 3. Record source
      String rsFieldName = "RECORD_SOURCE";
      if (config != null && !Utils.isEmpty(config.getRecordSourceField())) {
        rsFieldName = config.getRecordSourceField();
      }
      rsFieldName = variables.resolve(rsFieldName);
      if (Utils.isEmpty(rsFieldName)) rsFieldName = "RECORD_SOURCE";
      String lengthString =
          (config != null && !Utils.isEmpty(config.getRecordSourceFieldLength()))
              ? config.getRecordSourceFieldLength()
              : "100";
      lengthString = variables.resolve(lengthString);
      int rsLength = Const.toInt(lengthString, 100);
      IValueMeta rsMeta = new ValueMetaString(rsFieldName);
      rsMeta.setLength(rsLength);
      rowMeta.addValueMeta(rsMeta);

      // 4. Load date
      String loadDateField = config.getLoadDateField();
      if (Utils.isEmpty(loadDateField)) loadDateField = "LOAD_DATE";
      IValueMeta loadMeta = new ValueMetaTimestamp(loadDateField);
      rowMeta.addValueMeta(loadMeta);

      return rowMeta;
    } catch (Exception e) {
      throw new HopException("Error building target table layout for link " + getName(), e);
    }
  }

  // --- Helper methods and context (modeled after DvSatellite) ---

  private void addExecSqlIfNeeded(LinkUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) return;
    IRowMeta targetFields = getTargetTableLayout(ctx.metadataProvider, ctx.variables, ctx.model);
    if (targetFields == null) return;
    String ddl = "";
    SimpleLoggingObject loggingObject =
        new SimpleLoggingObject("DvLink.generateUpdatePipeline", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, ctx.variables, ctx.targetDatabaseMeta)) {
      db.connect();
      ddl = db.getDDL(ctx.targetTableName, targetFields);
    } catch (Exception e) {
      throw new HopException("Error getting DDL for target table: " + ctx.targetTableName, e);
    }
    if (!Utils.isEmpty(ddl)) {
      org.apache.hop.pipeline.transforms.sql.ExecSqlMeta execSqlMeta =
          new org.apache.hop.pipeline.transforms.sql.ExecSqlMeta();
      execSqlMeta.setConnection(ctx.targetDbName);
      execSqlMeta.setSql(ddl);
      TransformMeta execSqlTransformMeta =
          new TransformMeta(
              "ExecSql", "Create/update target table " + ctx.targetTableName, execSqlMeta);
      execSqlTransformMeta.setLocation(LOCATION_START_LINE_1);
      pipelineMeta.addTransform(execSqlTransformMeta);
    }
  }

  private TransformMeta addSourceTableInput(LinkUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    TableInputMeta tableInputMeta = new TableInputMeta();

    if (ctx.sourceDatabaseMeta != null) {
      tableInputMeta.setConnection(ctx.sourceDbName);

      List<String> quotedFields = new ArrayList<>();
      for (String fieldName : ctx.pkSourceFieldNames) {
        quotedFields.add(ctx.sourceDatabaseMeta.quoteField(fieldName));
      }
      for (String fieldName : ctx.drivingKeyFieldNames) {
        if (!quotedFields.contains(fieldName)) {
          quotedFields.add(ctx.sourceDatabaseMeta.quoteField(fieldName));
        }
      }

      StringBuilder sql = new StringBuilder("SELECT ");
      if (quotedFields.isEmpty()) {
        sql.append("*");
      } else {
        sql.append(String.join(", ", quotedFields));
      }

      // RS indicator
      String rsFieldName = ctx.recordSourceField;
      if (!Utils.isEmpty(ctx.sourceIndicator)) {
        String resolved = ctx.variables.resolve(ctx.sourceIndicator);
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

      tableInputMeta.setSql(sql.toString());
    }

    TransformMeta tm = new TransformMeta("TableInput", ctx.sourceTransformName, tableInputMeta);
    tm.setLocation(LOCATION_START_LINE_2);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addCheckSumForFields(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      CheckSumType checkSumType,
      List<String> inputFieldNames,
      String resultFieldName,
      DataVaultConfiguration config,
      int index) {
    CheckSumMeta checkSumMeta = new CheckSumMeta();
    checkSumMeta.setCheckSumType(checkSumType);

    List<Field> checkFields = new ArrayList<>();
    for (String f : inputFieldNames) {
      checkFields.add(new Field(f));
    }
    checkSumMeta.setFields(checkFields);
    checkSumMeta.setResultFieldName(resultFieldName);

    if (config != null && config.getHashKeyDataType() == HashKeyDataType.BINARY) {
      checkSumMeta.setResultType(ResultType.BINARY);
    } else {
      checkSumMeta.setResultType(ResultType.STRING);
    }

    TransformMeta tm = new TransformMeta("CheckSum", "calc_" + resultFieldName, checkSumMeta);
    // Place progressively to the right
    Point loc =
        new Point(LOCATION_START_LINE_2.x + (index + 1) * SPACING_WIDTH, LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addSelectRows(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    List<String> fieldsToSelect = new ArrayList<>();
    fieldsToSelect.add(ctx.linkHashKeyFieldName);
    fieldsToSelect.addAll(ctx.hubHashFieldNames);
    fieldsToSelect.add(ctx.recordSourceField);

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = new ArrayList<>();
    for (String fieldName : fieldsToSelect) {
      SelectField sf = new SelectField();
      sf.setName(fieldName);
      selectFields.add(sf);
    }
    selectMeta.getSelectOption().setSelectFields(selectFields);

    TransformMeta tm =
        new TransformMeta("SelectValues", "select_values", selectMeta);
    // Place it on the compare flow after the last checksum, before the sort
    Point loc =
        new Point(
            LOCATION_START_LINE_2.x + SPACING_WIDTH * (ctx.participatingHubs.size() + 2),
            LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addTargetTableInput(LinkUpdateContext ctx, PipelineMeta pipelineMeta) {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    String linkHashField = ctx.linkHashKeyFieldName;
    String quotedLinkHash = ctx.targetDatabaseMeta.quoteField(linkHashField);

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(quotedLinkHash);

    // Also select the hub hash columns that are stored in the link (for completeness / potential
    // value compare)
    for (String h : ctx.hubHashFieldNames) {
      sql.append(", ").append(ctx.targetDatabaseMeta.quoteField(h));
    }

    // Project the record source field on the target side as well (using NULL) so that
    // the input row meta to MergeRows is compatible when we passthrough the indicator
    // value from the source/compare leg.
    String rsFieldName = ctx.recordSourceField;
    if (Utils.isEmpty(rsFieldName)) {
      rsFieldName = "RECORD_SOURCE";
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
    sql.append(quotedLinkHash);

    targetTableInputMeta.setSql(sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", ctx.targetTransformName, targetTableInputMeta);
    tm.setLocation(LOCATION_START_LINE_3);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addMergeRowsIfNeeded(
      LinkUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta sourceToMergeTransform,
      TransformMeta targetTransform) {
    if (targetTransform == null || sourceToMergeTransform == null) {
      return null;
    }

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(ctx.targetTransformName);
    mergeRowsMeta.setCompareTransform(sourceToMergeTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    keyFields.add(ctx.linkHashKeyFieldName);
    mergeRowsMeta.setKeyFields(keyFields);

    List<PassThroughField> passThroughFields = new ArrayList<>();
    passThroughFields.add(new PassThroughField(ctx.linkHashKeyFieldName, null, false));
    for (String h : ctx.hubHashFieldNames) {
      passThroughFields.add(new PassThroughField(h, null, false));
    }
    for (String d : ctx.drivingKeyFieldNames) {
      passThroughFields.add(new PassThroughField(d, null, false));
    }
    String rsFieldName = ctx.recordSourceField;
    if (!Utils.isEmpty(rsFieldName)) {
      passThroughFields.add(new PassThroughField(rsFieldName, null, false));
    }
    mergeRowsMeta.setPassThroughFields(passThroughFields);

    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x+2*SPACING_WIDTH,  LOCATION_START_LINE_3.y);
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
    tm.setLocation(LOCATION_START_LINE_3.x+3*SPACING_WIDTH,  LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addSortRows(
          PipelineMeta pipelineMeta, TransformMeta after, LinkUpdateContext ctx, String sortFieldName) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    SortRowsField sf = new SortRowsField();
    sf.setFieldName(sortFieldName);
    sf.setAscending(true);
    sf.setCaseSensitive(true);
    sortRowsMeta.getSortFields().add(sf);

    TransformMeta tm = new TransformMeta("SortRows", "sort_" + sortFieldName, sortRowsMeta);
    Point loc = new Point(LOCATION_START_LINE_2.x + SPACING_WIDTH*(ctx.participatingHubs.size()+3), LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, Date loadDate) throws HopException {
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
    tm.setLocation(LOCATION_START_LINE_3.x + 4*SPACING_WIDTH,  LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private TransformMeta addTableOutput(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, IRowMeta targetLayout) throws HopException {
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return null;
    }
    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      tableName = getName();
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
      tm.setLocation(LOCATION_START_LINE_3.x + 5*SPACING_WIDTH,  LOCATION_START_LINE_3.y);
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
      TransformMeta linkHashCalc,
      TransformMeta selectTransform,
      TransformMeta sortTransform,
      TransformMeta constantTransform,
      TransformMeta tableOutputTransform) {

    // source -> hub hashes -> link hash calc -> select -> sort
    if (sourceTransform != null && linkHashCalc != null) {
      // The intermediate hub hashes are chained inside the creation; we only need the final hop to
      // the link calc if not already
      // For simplicity we rely on the creation order for intermediate; here we ensure the last calc
      // to select
    }

    if (selectTransform != null && linkHashCalc != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(linkHashCalc, selectTransform));
    }

    if (sortTransform != null && selectTransform != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(selectTransform, sortTransform));
    }

    if (mergeTransform != null) {
      if (targetTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(targetTransform, mergeTransform));
      }
      if (sortTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(sortTransform, mergeTransform));
      }
      if (filterTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, filterTransform));
        if (constantTransform != null) {
          pipelineMeta.addPipelineHop(new PipelineHopMeta(filterTransform, constantTransform));
        }
      } else if (constantTransform != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, constantTransform));
      }
    } else if (linkHashCalc != null && constantTransform != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(linkHashCalc, constantTransform));
    }

    if (constantTransform != null && tableOutputTransform != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(constantTransform, tableOutputTransform));
    }
  }

  // The main addSortRows is defined above and used for both the link hash sort and the helper.

  /**
   * Represents the source key field mappings for one participating hub in a link. Allows mapping
   * the business key fields from the link's record source to the hub's business keys, supporting
   * different naming conventions (e.g. _fk vs _id).
   */
  public static class HubSourceKeyField {

    /** The name of the hub (matches an entry in hubNames). */
    @HopMetadataProperty private String hubName;

    /**
     * The list of source field names in the link's record source that correspond to this hub's
     * business keys (in the same order).
     */
    @HopMetadataProperty(key = "sourceBusinessKeyField", groupKey = "sourceBusinessKeyFields")
    private List<String> sourceBusinessKeyFields = new ArrayList<>();

    public HubSourceKeyField() {}

    public String getHubName() {
      return hubName;
    }

    public void setHubName(String hubName) {
      this.hubName = hubName;
    }

    public List<String> getSourceBusinessKeyFields() {
      return sourceBusinessKeyFields;
    }

    public void setSourceBusinessKeyFields(List<String> sourceBusinessKeyFields) {
      this.sourceBusinessKeyFields =
          sourceBusinessKeyFields != null ? sourceBusinessKeyFields : new ArrayList<>();
    }
  }

  /** Context for Link update pipeline generation. */
  private static class LinkUpdateContext {
    final DvLink link;
    final DataVaultModel model;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;

    final DataVaultConfiguration config;
    final CheckSumType checkSumType;

    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String targetTableName;
    final String pipelineName;

    final String sourceTransformName;
    final String targetTransformName;
    final DatabaseMeta sourceDatabaseMeta;
    final String sourceDbName;
    final String sourceSchema;
    final String sourceTable;

    final List<String> pkSourceFieldNames; // all raw BK fields from all hubs + driving
    final List<List<String>> hubBkFieldGroups; // per hub, the BK source fields for its checksum
    final List<String> hubHashFieldNames; // the result names for each hub hash
    final List<DvHub> participatingHubs;
    final List<String> drivingKeyFieldNames;

    final String linkHashKeyFieldName;

    // Record source indicator
    final String sourceIndicator;
    final String sourceIndicatorField;
    final String recordSourceField;

    LinkUpdateContext(
        DvLink link,
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
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        String sourceSchema,
        String sourceTable,
        List<String> pkSourceFieldNames,
        List<List<String>> hubBkFieldGroups,
        List<String> hubHashFieldNames,
        List<DvHub> participatingHubs,
        List<String> drivingKeyFieldNames,
        String linkHashKeyFieldName,
        String sourceIndicator,
        String sourceIndicatorField,
        String recordSourceField) {
      this.link = link;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
      this.checkSumType = checkSumType;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
      this.sourceTransformName = sourceTransformName;
      this.targetTransformName = targetTransformName;
      this.sourceDatabaseMeta = sourceDatabaseMeta;
      this.sourceDbName = sourceDbName;
      this.sourceSchema = sourceSchema;
      this.sourceTable = sourceTable;
      this.pkSourceFieldNames = pkSourceFieldNames != null ? pkSourceFieldNames : new ArrayList<>();
      this.hubBkFieldGroups = hubBkFieldGroups != null ? hubBkFieldGroups : new ArrayList<>();
      this.hubHashFieldNames = hubHashFieldNames != null ? hubHashFieldNames : new ArrayList<>();
      this.participatingHubs = participatingHubs != null ? participatingHubs : new ArrayList<>();
      this.drivingKeyFieldNames =
          drivingKeyFieldNames != null ? drivingKeyFieldNames : new ArrayList<>();
      this.linkHashKeyFieldName =
          linkHashKeyFieldName != null ? linkHashKeyFieldName : (link.getName() + "_LK");
      this.sourceIndicator = sourceIndicator;
      this.sourceIndicatorField = sourceIndicatorField;
      this.recordSourceField = recordSourceField != null ? recordSourceField : "RECORD_SOURCE";
    }

    static LinkUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvLink link)
        throws HopException {
      if (metadataProvider == null || model == null || link == null) {
        return null;
      }

      DataVaultConfiguration config = null;
      String configName = model.getConfigurationName();
      if (!Utils.isEmpty(configName)) {
        config = metadataProvider.getSerializer(DataVaultConfiguration.class).load(configName);
      }

      // Hash algo -> CheckSumType (same logic as hubs/sats)
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
          !Utils.isEmpty(link.getTableName()) ? link.getTableName() : link.getName();
      String pipelineName = "link-" + targetTableName;

      String sourceTransformName = link.getRecordSource();
      if (Utils.isEmpty(sourceTransformName)) {
        sourceTransformName = link.getName();
      }
      String targetTransformName = "target_" + targetTableName;

      String linkHashKeyFieldName = link.getLinkHashKeyFieldName();
      if (Utils.isEmpty(linkHashKeyFieldName)) {
        linkHashKeyFieldName = link.getName() + "_LK";
      }

      // Load participating hubs and collect source fields
      List<DvHub> participatingHubs = new ArrayList<>();
      List<String> allPk = new ArrayList<>();
      List<List<String>> hubBkGroups = new ArrayList<>();
      List<String> hubHashNames = new ArrayList<>();
      List<String> drivingKeys = new ArrayList<>();

      String recordSourceName = link.getRecordSource();

      List<HubSourceKeyField> userHubSourceKeyFields = link.getHubSourceKeyFields();
      for (int i = 0; i < link.getHubNames().size(); i++) {
        String hubName = link.getHubNames().get(i);
        DvHub hub = model.findHub(hubName);
        if (hub == null) {
          throw new HopException(
              "Participating hub not found for link " + link.getName() + ": " + hubName);
        }
        participatingHubs.add(hub);

        String hubHashName = hub.getHashKeyFieldName();
        if (Utils.isEmpty(hubHashName)) {
          if (!Utils.isEmpty(hub.getBusinessKeys())) {
            hubHashName = hub.getBusinessKeys().get(0).getName() + "_HK";
          } else {
            hubHashName = hub.getName() + "_HK";
          }
        }
        hubHashNames.add(hubHashName);

        List<String> thisHubBks = new ArrayList<>();

        // Use user-provided mapping for this hub in the link source, if present and non-empty.
        // Otherwise fall back to the hub's own business key names (current assumption of name
        // match).
        List<String> mappedFields = null;
        if (userHubSourceKeyFields != null && i < userHubSourceKeyFields.size()) {
          HubSourceKeyField field = userHubSourceKeyFields.get(i);
          if (field != null && hubName.equals(field.getHubName())) {
            mappedFields = field.getSourceBusinessKeyFields();
          }
        }
        if (mappedFields != null && !mappedFields.isEmpty()) {
          for (String f : mappedFields) {
            String resolved = variables.resolve(f);
            if (!thisHubBks.contains(resolved)) thisHubBks.add(resolved);
            if (!allPk.contains(resolved)) allPk.add(resolved);
          }
        } else {
          // Fallback: use names from the hub definition
          for (BusinessKey bk : hub.getBusinessKeys()) {
            String srcField = bk.getSourceFieldName();
            if (Utils.isEmpty(srcField)) srcField = bk.getName();
            String resolved = variables.resolve(srcField);
            if (!thisHubBks.contains(resolved)) thisHubBks.add(resolved);
            if (!allPk.contains(resolved)) allPk.add(resolved);
          }
        }
        hubBkGroups.add(thisHubBks);
      }

      // Driving keys
      for (String dk : link.getDrivingKeyNames()) {
        String resolved = variables.resolve(dk);
        if (!drivingKeys.contains(resolved)) drivingKeys.add(resolved);
        if (!allPk.contains(resolved)) allPk.add(resolved);
      }

      // Source side DB info (same as sat/hub)
      DatabaseMeta sourceDatabaseMeta = null;
      String sourceDbName = null;
      String sourceSchema = null;
      String sourceTable = null;
      String sourceIndicator = null;
      String sourceIndicatorField = null;

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
          }
        }
      }

      return new LinkUpdateContext(
          link,
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
          sourceDatabaseMeta,
          sourceDbName,
          sourceSchema,
          sourceTable,
          allPk,
          hubBkGroups,
          hubHashNames,
          participatingHubs,
          drivingKeys,
          linkHashKeyFieldName,
          sourceIndicator,
          sourceIndicatorField,
          recordSourceField);
    }
  }
}
