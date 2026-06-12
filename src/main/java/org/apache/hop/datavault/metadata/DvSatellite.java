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

import static java.util.Collections.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
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
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
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
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.pipeline.transforms.groupby.Aggregation;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupingField;
import org.jspecify.annotations.NonNull;

/**
 * Data Vault 2.0 Satellite metadata definition.
 *
 * <p>Satellites hold the time-variant descriptive attributes for either a Hub or a Link. Changes
 * are captured by inserting new rows (insert-only pattern in DV 2.0).
 */
@GuiPlugin
@Getter
@Setter
public class DvSatellite extends DvTableBase
    implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  private static final Class<?> PKG = DvSatellite.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_SATELLITE_DIALOG";

  private static final Point LOCATION_START_LINE_1 = new Point(160, 48);
  private static final Point LOCATION_START_LINE_2 = new Point(160, 160);
  private static final Point LOCATION_START_LINE_3 = new Point(160, 320);

  public static final int SPACING_WIDTH = 160;

  /**
   * The Hub this satellite describes (if hub satellite). Use either hubName or linkName, not both.
   */
  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.METADATA,
      metadata = DvHub.class,
      label = "i18n::DvSatellite.HubName.Label",
      toolTip = "i18n::DvSatellite.HubName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "hub")
  private String hubName;

  /** The Link this satellite describes (if link satellite). */
  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.METADATA,
      metadata = DvLink.class,
      label = "i18n::DvSatellite.LinkName.Label",
      toolTip = "i18n::DvSatellite.LinkName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(key = "link")
  private String linkName;

  // Satellite attributes (nested POJO list). @GuiWidgetElement LIST not available in
  // GuiElementType.
  @HopMetadataProperty private List<SatelliteAttribute> attributes = new ArrayList<>();

  /** For multi-active satellites (e.g. multiple phone numbers per customer at same time). */
  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvSatellite.MultiActive.Label",
      toolTip = "i18n::DvSatellite.MultiActive.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean multiActive;

  /** Name of the driving key column in a multi-active satellite (if applicable). */
  @GuiWidgetElement(
      order = "0710",
      type = GuiElementType.TEXT,
      label = "i18n::DvSatellite.DrivingKey.Label",
      toolTip = "i18n::DvSatellite.DrivingKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String drivingKey;

  @HopMetadataProperty(storeWithName = true)
  private DataVaultSource recordSource;

  public DvSatellite() {
    super();
    this.tableType = DvTableType.SATELLITE;
  }

  public DvSatellite(String name) {
    super(name);
    this.tableType = DvTableType.SATELLITE;
  }

  @Override
  public void check(List<ICheckResult> remarks, IHopMetadataProvider metadataProvider, IVariables variables) {
    super.check(remarks, metadataProvider, variables);
    if (Utils.isEmpty(hubName) && Utils.isEmpty(linkName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.NotLinked"),
              this));
    } else if (!Utils.isEmpty(hubName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.LinkedToHub", hubName),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.LinkedToLink", linkName),
              this));
    }

    if (Utils.isEmpty(attributes)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.NoAttributes"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvSatellite.CheckResult.HasAttributes", attributes.size()),
              this));
      for (SatelliteAttribute attr : attributes) {
        if (Utils.isEmpty(attr.getName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvSatellite.CheckResult.AttributeNoName"),
                  this));
        }
      }

      // Verify that all specified attributes exist in the record source's fields.
      if (recordSource != null && metadataProvider != null && !Utils.isEmpty(attributes)) {
        try {
          List<SourceField> sourceFields = recordSource.getFields(metadataProvider);
          Set<String> sourceNames = new HashSet<>();
          for (SourceField sf : sourceFields) {
            if (!Utils.isEmpty(sf.getName())) {
              sourceNames.add(sf.getName());
            }
          }
          for (SatelliteAttribute attr : attributes) {
            String n = attr.getName();
            if (!Utils.isEmpty(n) && !sourceNames.contains(n)) {
              remarks.add(
                  new CheckResult(
                      ICheckResult.TYPE_RESULT_ERROR,
                      "Attribute '"
                          + n
                          + "' not found in record source '"
                          + (recordSource.getName() != null ? recordSource.getName() : "")
                          + "'",
                      this));
            }
          }
        } catch (HopException e) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  "Error looking up fields in record source: " + e.getMessage(),
                  this));
        }
      }
    }

    if (Utils.isEmpty(drivingKey)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.NoDrivingKey"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.HasDrivingKey", drivingKey),
              this));
    }
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate)
      throws HopException {
    try {
      if (metadataProvider == null || model == null) {
        return emptyList();
      }

      SatelliteUpdateContext ctx =
          SatelliteUpdateContext.create(metadataProvider, variables, model, this);
      if (ctx == null) {
        return emptyList();
      }

      // Clear caches of source and target database
      //
      DbCache.clearAll();

      PipelineMeta pipelineMeta = new PipelineMeta();
      pipelineMeta.setName(ctx.pipelineName);

      // Add the optional DDL transform first (not wired into the main flow)
      addExecSqlIfNeeded(ctx, pipelineMeta);

      // Source TableInput (from record source) - selects hub bks (for hash calc) + sat attrs + rs
      TransformMeta sourceInputTransform = addSourceTableInput(ctx, recordSource, pipelineMeta);

      // CheckSum to compute the (hub's) hash key from the bks in source
      TransformMeta checkSumTransform = addHashKeyCheckSum(ctx, pipelineMeta, sourceInputTransform);

      // CheckSum to compute the (hub's) hash key from the bks in source
      TransformMeta sourceSelectTransform =
          addSourceSelectRows(ctx, pipelineMeta, checkSumTransform);

      // Sort the records by hash key
      TransformMeta sortHkTransform = addSortRows(ctx, pipelineMeta, sourceSelectTransform);

      // Target TableInput (hash + attributes from sat table, for diff + value comparison)
      TransformMeta targetInputTransform = addTargetTableInput(ctx, pipelineMeta);

      // Perform CDC with a merge rows (diff) transform
      TransformMeta mergeTransform =
          addMergeRows(ctx, pipelineMeta, sortHkTransform, targetInputTransform);

      // Filter Rows: keep rows that are not 'identical' (i.e. new or changed attribute values
      // for an existing hub in the satellite). We achieve this by testing for 'identical' and
      // negating the condition.
      TransformMeta filterTransform = addFilterNewRows(pipelineMeta, mergeTransform);

      // Add Constant transform for the static load date (provided to the method)
      TransformMeta constantTransform =
          addConstantForLoadDate(ctx, pipelineMeta, loadDate, filterTransform);

      // Add Table Output at the end to write new rows (all target fields except "flag")
      IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);

      addTableOutput(ctx, pipelineMeta, targetLayout, constantTransform);

      return List.of(pipelineMeta);
    } catch (Exception e) {
      throw new HopException(
          "Error generating update pipeline for Satellite target " + getName(), e);
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

      // 1. First column: hash key from the linked hub (look up hub if needed)
      String hashKeyName = "hashkey";
      if (!Utils.isEmpty(hubName)) {
        DvHub linkedHub = model.findHub(hubName);
        if (linkedHub == null) {
          throw new HopException("Please provide an existing hub in satelite " + getName());
        }
        hashKeyName = linkedHub.getHashKeyFieldName();
        if (Utils.isEmpty(hashKeyName)) {
          if (!Utils.isEmpty(linkedHub.getBusinessKeys())) {
            String bkName = linkedHub.getBusinessKeys().get(0).getName();
            hashKeyName = bkName + "_HK";
          }
        }
      } // else link support omitted for now, fallback

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

      // Determine attributes
      List<SourceField> sourceFields = null;
      if (getRecordSource() != null) {
        sourceFields = getRecordSource().getFields(metadataProvider);
      } else {
        throw new HopException("Please specify a record source for Satellite " + getName());
      }

      List<SatelliteAttribute> satAttrs = getAttributes();
      if (Utils.isEmpty(satAttrs) && sourceFields != null) {
        // no fields specified: take all from record source
        for (SourceField sf : sourceFields) {
          IValueMeta attrMeta = createValueMetaFromSourceField(sf);
          rowMeta.addValueMeta(attrMeta);
        }
      } else {
        for (SatelliteAttribute attr : satAttrs) {
          IValueMeta attrMeta = createValueMetaFromAttribute(attr);
          // If the SatelliteAttribute did not specify length/precision, try to take it
          // from the matching SourceField in the Data Vault Source (the source of truth for
          // the originating database column definitions).
          if (sourceFields != null && (attrMeta.getLength() <= 0 || attrMeta.getPrecision() <= 0)) {
            for (SourceField sf : sourceFields) {
              if (attr.getName().equals(sf.getName())) {
                if (attrMeta.getLength() <= 0) {
                  attrMeta.setLength(Const.toInt(sf.getLength(), -1));
                }
                if (attrMeta.getPrecision() <= 0) {
                  attrMeta.setPrecision(Const.toInt(sf.getPrecision(), -1));
                }
                break;
              }
            }
          }
          rowMeta.addValueMeta(attrMeta);
        }
      }

      // Record source column
      String rsFieldName = "RECORD_SOURCE";
      if (config != null && !Utils.isEmpty(config.getRecordSourceField())) {
        rsFieldName = config.getRecordSourceField();
      }
      rsFieldName = variables.resolve(rsFieldName);
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = "RECORD_SOURCE";
      }
      String lengthString =
          (config != null && !Utils.isEmpty(config.getRecordSourceFieldLength()))
              ? config.getRecordSourceFieldLength()
              : "100";
      lengthString = variables.resolve(lengthString);
      int rsLength = Const.toInt(lengthString, 100);
      IValueMeta rsMeta = new ValueMetaString(rsFieldName);
      rsMeta.setLength(rsLength);
      rowMeta.addValueMeta(rsMeta);

      // Load date
      String loadDateField = config.getLoadDateField();
      if (Utils.isEmpty(loadDateField)) {
        loadDateField = "LOAD_DATE";
      }
      IValueMeta loadMeta = new ValueMetaTimestamp(loadDateField);
      rowMeta.addValueMeta(loadMeta);

      return rowMeta;

    } catch (Exception e) {
      throw new HopException("Error building target table layout for satellite", e);
    }
  }

  private IValueMeta createValueMetaFromSourceField(SourceField sf) throws HopException {
    String name = sf.getName();
    int type = sf.getHopType();
    if (type <= 0) {
      type = IValueMeta.TYPE_STRING;
    }
    try {
      IValueMeta vm = ValueMetaFactory.createValueMeta(name, type);
      // Take length and precision from the Data Vault Source definition (SourceField)
      vm.setLength(Const.toInt(sf.getLength(), -1));
      vm.setPrecision(Const.toInt(sf.getPrecision(), -1));
      return vm;
    } catch (org.apache.hop.core.exception.HopPluginException e) {
      throw new HopException("Error creating value meta for source field " + name, e);
    }
  }

  private IValueMeta createValueMetaFromAttribute(SatelliteAttribute attr) throws HopException {
    String name = attr.getName();
    String dt = attr.getDataType();
    int typeId = IValueMeta.TYPE_STRING;
    if (!Utils.isEmpty(dt)) {
      typeId = ValueMetaFactory.getIdForValueMeta(dt);
      if (typeId <= 0) typeId = IValueMeta.TYPE_STRING;
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

  // --- Small methods for pipeline generation, modeled after DvHub but adapted for satellites ---
  // Satellites: hash key is the (linked) hub's hash; "pks" for hash calc are the hub bks from sat's
  // record source; attributes come from sat definition or all source fields.

  private void addExecSqlIfNeeded(SatelliteUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      return;
    }
    IRowMeta targetFields = getTargetTableLayout(ctx.metadataProvider, ctx.variables, ctx.model);
    if (targetFields == null) {
      return;
    }
    String ddl = "";
    org.apache.hop.core.logging.ILoggingObject loggingObject =
        new org.apache.hop.core.logging.SimpleLoggingObject(
            "DvSatellite.generateUpdatePipeline",
            org.apache.hop.core.logging.LoggingObjectType.GENERAL,
            null);
    try (org.apache.hop.core.database.Database db =
        new org.apache.hop.core.database.Database(
            loggingObject, ctx.variables, ctx.targetDatabaseMeta)) {
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

  private TransformMeta addSourceTableInput(
      SatelliteUpdateContext ctx, DataVaultSource dataVaultSource, PipelineMeta pipelineMeta)
      throws HopException {
    DvDatabaseSatelliteSourcePipelineBuilder builder =
        new DvDatabaseSatelliteSourcePipelineBuilder(
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

  private TransformMeta addHashKeyCheckSum(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    CheckSumMeta checkSumMeta = new CheckSumMeta();
    checkSumMeta.setCheckSumType(ctx.checkSumType);

    List<Field> checkFields = new ArrayList<>();
    for (String fieldName : ctx.pkSourceFieldNames) {
      checkFields.add(new Field(fieldName));
    }
    checkSumMeta.setFields(checkFields);

    String resultFieldName = ctx.hashKeyFieldName;
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
    tm.setLocation(LOCATION_START_LINE_2.x + SPACING_WIDTH, LOCATION_START_LINE_2.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addSourceSelectRows(
      DvSatellite.SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    // We just want to get rid of the business key fields.
    // These are defined in the attached Hub
    //
    DvHub hub = ctx.model.findHub(getHubName());
    if (hub == null) {
      throw new HopException(
          "Unable to find the hub (" + getHubName() + ") to satellite " + getName());
    }

    // We select the hash key and the attributes plus the source field
    //
    List<String> fields = new ArrayList<>();
    fields.add(ctx.hashKeyFieldName);
    fields.addAll(ctx.satAttrFieldNames);
    fields.add(ctx.recordSourceField);

    for (String field : fields) {
      SelectField selectField = new SelectField();
      selectField.setName(field);
      selectFields.add(selectField);
    }

    TransformMeta tm = new TransformMeta("SelectValues", "hk plus attr", selectMeta);
    // Place it on the compare flow after the last checksum, before the sort
    Point loc = new Point(LOCATION_START_LINE_2.x + 2 * SPACING_WIDTH, LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private TransformMeta addSortRows(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    SortRowsField sortField = new SortRowsField();
    sortField.setFieldName(ctx.hashKeyFieldName);
    sortField.setAscending(true);
    sortField.setCaseSensitive(
        true); // hash values (binary or hex strings) should compare case-sensitively
    sortRowsMeta.getSortFields().add(sortField);

    // Other SortRowsMeta defaults (tmp dir, sort size, no compress, not unique-only) are
    // acceptable.

    String sortTransformName = "sort " + ctx.hashKeyFieldName;
    TransformMeta tm = new TransformMeta("SortRows", sortTransformName, sortRowsMeta);
    tm.setLocation(LOCATION_START_LINE_2.x + 3 * SPACING_WIDTH, LOCATION_START_LINE_2.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addTargetTableInput(SatelliteUpdateContext ctx, PipelineMeta pipelineMeta) {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    String hashField = ctx.hashKeyFieldName;
    String quotedHash = ctx.targetDatabaseMeta.quoteField(hashField);

    // Resolve load date field name (from config, supports variables) so we can ORDER BY it.
    // We ORDER BY hash + load_date (without selecting load_date) so that the Group By below
    // using LAST_INCL_NULL will retain only the most recent record per satellite hash key.
    String loadDateField = determineTargetLoadDateField(ctx);
    String quotedLoadDate = ctx.targetDatabaseMeta.quoteField(loadDateField);

    // Select the hash key + all satellite attributes from the target so that MergeRows can
    // compare attribute *values* (not just presence of the hub key).
    // We ORDER BY hash + load_date so the following GroupBy (LAST_INCL_NULL) selects the last
    // (most recent) version of each satellite record.
    StringBuilder sql = new StringBuilder("SELECT ");
    List<String> selectFields = new ArrayList<>();

    // Query the attributes before the hash key to make it easier to match row layouts
    // in the merge rows transform.
    //
    for (String attr : ctx.satAttrFieldNames) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(attr));
    }
    selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.recordSourceField));
    selectFields.add(quotedHash);

    sql.append(String.join(", ", selectFields));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    sql.append(" ORDER BY ");
    sql.append(quotedHash);
    sql.append(", ");
    sql.append(quotedLoadDate);

    targetTableInputMeta.setSql(sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", ctx.targetTransformName, targetTableInputMeta);
    tm.setLocation(LOCATION_START_LINE_3);
    pipelineMeta.addTransform(tm);

    // After the target table input, insert a Group By that groups on the hash key and takes
    // LAST_INCL_NULL for all other fields. This ensures we feed only the latest satellite
    // version (per hash) into the Merge Rows (avoiding duplicate keys from target history).
    GroupByMeta groupByMeta = new GroupByMeta();
    List<GroupingField> groupingFields = new ArrayList<>();
    groupingFields.add(new GroupingField(ctx.hashKeyFieldName));
    groupByMeta.setGroupingFields(groupingFields);

    List<Aggregation> aggregations = new ArrayList<>();
    for (String attr : ctx.satAttrFieldNames) {
      Aggregation agg = new Aggregation();
      agg.setSubject(attr);
      agg.setField(attr);
      agg.setTypeLabel("LAST_INCL_NULL");
      aggregations.add(agg);
    }
    // Also aggregate the record source field with LAST_INCL_NULL
    Aggregation rsAgg = new Aggregation();
    rsAgg.setSubject(ctx.recordSourceField);
    rsAgg.setField(ctx.recordSourceField);
    rsAgg.setTypeLabel("LAST_INCL_NULL");
    aggregations.add(rsAgg);

    groupByMeta.setAggregations(aggregations);

    TransformMeta groupTm =
        new TransformMeta(
            "GroupBy", "group_last_" + ctx.hashKeyFieldName, groupByMeta);
    groupTm.setLocation(LOCATION_START_LINE_3.x + SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(groupTm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(tm, groupTm));

    return groupTm;
  }

  private static String determineTargetLoadDateField(SatelliteUpdateContext ctx) {
    String loadDateField = "LOAD_DATE";
    if (ctx.config != null && !Utils.isEmpty(ctx.config.getLoadDateField())) {
      loadDateField = ctx.config.getLoadDateField();
    }
    loadDateField = ctx.variables.resolve(loadDateField);
    return loadDateField;
  }

  private TransformMeta addMergeRows(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform) {
    if (referenceTransform == null || compareTransform == null) {
      return null;
    }

    // A bug in hop requires an extra dummy to read from both reference and compare transforms
    //
    TransformMeta referenceDummyTransform = addDummyTransform(
            pipelineMeta, referenceTransform, "Merge reference",
            LOCATION_START_LINE_3.x + 2 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    TransformMeta compareDummyTransform = addDummyTransform(
            pipelineMeta, compareTransform, "Merge compare",
            LOCATION_START_LINE_2.x + 4 * SPACING_WIDTH, LOCATION_START_LINE_2.y);

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceDummyTransform.getName());
    mergeRowsMeta.setCompareTransform(compareDummyTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    keyFields.add(ctx.hashKeyFieldName);
    mergeRowsMeta.setKeyFields(keyFields);

    // Value fields: the satellite attributes. MergeRows will compare these (when keys/hash match)
    // to decide between 'identical' and 'changed'.
    for (SatelliteAttribute attribute : attributes) {
      if (attribute.isIncludeInChangeDataCapture()) {
        mergeRowsMeta.getValueFields().add(attribute.getName());
      }
    }

    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 3 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
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
      Condition newCondition = new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), "new"));
      Condition changedCondition = new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("changed"), "changed"));
      changedCondition.setOperator(Condition.Operator.OR);

      Condition condition = new Condition();
      condition.addCondition(newCondition);
      condition.addCondition(changedCondition);

      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating filter condition (not identical)", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "new or changed", filterRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 4 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, tm));

    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      Date loadDate,
      TransformMeta predecessor)
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
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor)
      throws HopException {
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
      tm.setLocation(LOCATION_START_LINE_3.x + 6 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return tm;
    } catch (Exception e) {
      throw new HopException("Error creating Table Output transform", e);
    }
  }

  /**
   * Context object holding all loaded / derived objects for the satellite update pipeline
   * generation.
   */
  private static class SatelliteUpdateContext {
    final DvSatellite satellite;
    final DataVaultModel model;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;

    final DataVaultConfiguration config;
    final CheckSumType checkSumType;

    final DataVaultSource dataVaultSource;
    final IDvSource dvSource;
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
    final List<String> pkSourceFieldNames; // hub bks from sat source, for hash calc
    final List<String> satAttrFieldNames; // sat attrs to carry through
    final String hashKeyFieldName; // from linked hub

    // Record source indicator support
    final String sourceIndicator;
    final String sourceIndicatorField;
    final String recordSourceField;

    SatelliteUpdateContext(
        DvSatellite satellite,
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
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        String sourceSchema,
        String sourceTable,
        List<String> pkSourceFieldNames,
        List<String> satAttrFieldNames,
        String hashKeyFieldName,
        String sourceIndicator,
        String sourceIndicatorField,
        String recordSourceField)
        throws HopException {
      this.satellite = satellite;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
      this.checkSumType = checkSumType;
      this.dataVaultSource = dataVaultSource;
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
      this.satAttrFieldNames = satAttrFieldNames != null ? satAttrFieldNames : new ArrayList<>();
      this.hashKeyFieldName = hashKeyFieldName != null ? hashKeyFieldName : "hashkey";
      this.sourceIndicator = sourceIndicator;
      this.sourceIndicatorField = sourceIndicatorField;
      this.recordSourceField = recordSourceField != null ? recordSourceField : "RECORD_SOURCE";

      this.dvSource = dataVaultSource.getDvSource(metadataProvider);
    }

    static SatelliteUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvSatellite sat)
        throws HopException {
      if (metadataProvider == null || model == null || sat == null) {
        return null;
      }

      if (sat.recordSource == null) {
        throw new HopException("Please specify a record source for satellite " + sat.getName());
      }
      DataVaultSource recordSource = sat.getRecordSource();

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
          !Utils.isEmpty(sat.getTableName()) ? sat.getTableName() : sat.getName();
      String pipelineName = "sat-" + targetTableName;

      String sourceTransformName = sat.getName();
      String targetTransformName = "target_" + targetTableName;

      // Look up linked hub for hash key name + bks to select for hash calc
      DvHub linkedHub = null;
      String hashKeyFieldName = "hashkey";
      List<String> hubBkNames = new ArrayList<>();
      if (!Utils.isEmpty(sat.getHubName())) {
        linkedHub = model.findHub(sat.getHubName());
        if (linkedHub == null) {
          throw new HopException("Please link satellite " + sat.getName() + " to a hub");
        }
        hashKeyFieldName = linkedHub.getHashKeyFieldName();
        if (Utils.isEmpty(hashKeyFieldName)) {
          if (!Utils.isEmpty(linkedHub.getBusinessKeys())) {
            for (BusinessKey bk : linkedHub.getBusinessKeys()) {
              hubBkNames.add(bk.getName());
            }
            hashKeyFieldName = linkedHub.getBusinessKeys().get(0).getName() + "_HK";
          }
        } else {
          for (BusinessKey bk : linkedHub.getBusinessKeys()) {
            hubBkNames.add(bk.getName());
          }
        }
      }

      // Source side loads (recordSource -> ... + select hub bks + sat attrs)
      DatabaseMeta sourceDatabaseMeta = null;
      String sourceDbName = null;
      String sourceSchema = null;
      String sourceTable = null;
      List<String> pkSourceFieldNames = new ArrayList<>();
      List<String> satAttrFieldNames = new ArrayList<>();
      String sourceIndicator = null;
      String sourceIndicatorField = null;
      String recordSourceName = recordSource.getName();
      if (recordSource.getSourceType() == DataVaultSourceType.DATABASE
          && !Utils.isEmpty(recordSource.getSourceTableName())) {
        DvDatabaseSource dbSource =
            metadataProvider
                .getSerializer(DvDatabaseSource.class)
                .load(recordSource.getSourceTableName());
        if (dbSource != null) {
          sourceDbName = dbSource.getDatabaseName();
          sourceSchema = dbSource.getSchemaName();
          sourceTable = dbSource.getTableName();
          sourceDatabaseMeta =
              metadataProvider.getSerializer(DatabaseMeta.class).load(sourceDbName);
          if (sourceDatabaseMeta == null) {
            throw new HopException("Database connection not found in metadata: " + sourceDbName);
          }

          sourceIndicator = recordSource.getSourceIndicator();
          sourceIndicatorField = recordSource.getSourceIndicatorField();

          List<SourceField> sourceFields = recordSource.getFields(metadataProvider);

          // hub bks (pks for hash calc)
          for (String hubBk : hubBkNames) {
            for (SourceField sf : sourceFields) {
              if (hubBk.equals(sf.getName())) {
                String fieldName = variables.resolve(sf.getName());
                pkSourceFieldNames.add(fieldName);
                break;
              }
            }
          }

          // sat attrs
          if (Utils.isEmpty(sat.getAttributes())) {
            // all from source
            for (SourceField sf : sourceFields) {
              if (!sf.isPrimaryKey()) {
                satAttrFieldNames.add(variables.resolve(sf.getName()));
              }
            }
          } else {
            for (SatelliteAttribute sa : sat.getAttributes()) {
              satAttrFieldNames.add(sa.getName());
            }
          }
        }
      }

      return new SatelliteUpdateContext(
          sat,
          model,
          metadataProvider,
          variables,
          config,
          checkSumType,
          recordSource,
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
          pkSourceFieldNames,
          satAttrFieldNames,
          hashKeyFieldName,
          sourceIndicator,
          sourceIndicatorField,
          recordSourceField);
    }
  }
}
