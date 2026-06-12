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
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
import org.jspecify.annotations.NonNull;

/**
 * Data Vault 2.0 Link metadata definition.
 *
 * <p>A Link represents a relationship (many-to-many or association) between Hubs. The Link's hash
 * key is typically computed from the hash keys of the participating Hubs (plus any additional
 * descriptors for the relationship).
 */
@GuiPlugin
@Getter
@Setter
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
  @HopMetadataProperty private List<String> hubNames = new ArrayList<>();

  @HopMetadataProperty(key = "linkSource", groupKey = "linkSources")
  private List<DvLinkSource> linkSources = new ArrayList<>();

  /**
   * Optional driving key(s) - used when the same Hub appears more than once in a link (e.g. "from
   * location" vs "to location" in a route). To know where these key names are sourced from, we need
   * to look in the source data.
   */
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

  /**
   * Optional per-link name for the record source column (overrides the global one from
   * DataVaultConfiguration). Supports variables.
   */
  @GuiWidgetElement(
      order = "0510",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvLink.RecordSourceFieldName.Label",
      toolTip = "i18n::DvLink.RecordSourceFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceFieldName;

  public DvLink() {
    super();
    this.tableType = DvTableType.LINK;
  }

  public DvLink(String name) {
    super(name);
    this.tableType = DvTableType.LINK;
  }

  @Override
  public void check(List<ICheckResult> remarks, IHopMetadataProvider metadataProvider, IVariables variables) {
    super.check(remarks, metadataProvider, variables);
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
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasLinkHashKeyFieldName", linkHashKeyFieldName),
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
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasDrivingKeys", drivingKeyNames.size()),
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

    // New multi-source structure validation
    if (linkSources != null && !linkSources.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.HasLinkSources", linkSources.size()),
              this));
      for (DvLinkSource ls : linkSources) {
        if (ls != null && ls.getSource() != null && !Utils.isEmpty(ls.getSource().getName())) {
          DataVaultSource source = ls.getSource();
          List<SourceField> availableSourceFields;
          try {
            availableSourceFields = source.getFields(metadataProvider);
          } catch (HopException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    "Error loading fields from record source '"
                        + source.getName()
                        + "': "
                        + e.getMessage(),
                    this));
            continue;
          }
          if (availableSourceFields == null) {
            availableSourceFields = new ArrayList<>();
          }
          // loop over the available hubSourceKeyFields and check the source fields
          if (ls.getHubSourceKeyFields() != null) {
            for (HubSourceKeyField hkf : ls.getHubSourceKeyFields()) {
              if (hkf == null) continue;
              // check sourceBusinessKeyFields
              if (hkf.getSourceBusinessKeyFields() != null) {
                for (BusinessKeySource bks : hkf.getSourceBusinessKeyFields()) {
                  if (bks != null && !Utils.isEmpty(bks.getSourceFieldName())) {
                    boolean found = false;
                    for (SourceField sf : availableSourceFields) {
                      if (bks.getSourceFieldName().equals(sf.getName())) {
                        found = true;
                        break;
                      }
                    }
                    if (!found) {
                      remarks.add(
                          new CheckResult(
                              ICheckResult.TYPE_RESULT_ERROR,
                              "Source field '"
                                  + bks.getSourceFieldName()
                                  + "' (for business key '"
                                  + bks.getBusinessKeyField()
                                  + "') in hub '"
                                  + hkf.getHubName()
                                  + "' not available in record source '"
                                  + source.getName()
                                  + "'",
                              this));
                    }
                  }
                }
              }
              // check drivingKeySources
              if (hkf.getDrivingKeySources() != null) {
                for (DrivingKeySource dks : hkf.getDrivingKeySources()) {
                  if (dks != null && !Utils.isEmpty(dks.getSourceField())) {
                    boolean found = false;
                    for (SourceField sf : availableSourceFields) {
                      if (dks.getSourceField().equals(sf.getName())) {
                        found = true;
                        break;
                      }
                    }
                    if (!found) {
                      remarks.add(
                          new CheckResult(
                              ICheckResult.TYPE_RESULT_ERROR,
                              "Source field '"
                                  + dks.getSourceField()
                                  + "' (for driving key '"
                                  + dks.getDrivingKey()
                                  + "') in hub '"
                                  + hkf.getHubName()
                                  + "' not available in record source '"
                                  + source.getName()
                                  + "'",
                              this));
                    }
                  }
                }
              }
            }
          }
        }
      }
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
        return Collections.emptyList();
      }

      List<PipelineMeta> result = new ArrayList<>();

      for (DvLinkSource linkSource : linkSources) {
        LinkUpdateContext ctx =
            LinkUpdateContext.create(metadataProvider, variables, model, this, linkSource);

        PipelineMeta pipelineMeta = new PipelineMeta();
        String baseName = ctx.pipelineName;
        String srcName = linkSource.getSource().getName();
        pipelineMeta.setName(baseName + "_" + srcName);

        addExecSqlIfNeeded(ctx, pipelineMeta);

        TransformMeta sourceInputTransform = addSourceTableInput(ctx, pipelineMeta, linkSource);
        TransformMeta predecessorTransform = sourceInputTransform;

        // Compute hub hashes for each participating hub (from their BKs in the source)
        List<String> hubHashNames = new ArrayList<>();
        int index = 0;
        for (String hubName : hubNames) {
          DvHub hub = model.findHub(hubName);
          if (hub == null) {
            throw new HopException(
                "Unable to find hub '" + hubName + "' in the model for Link table " + getName());
          }

          String hubHashName = variables.resolve(hub.getHashKeyFieldName());
          hubHashNames.add(hubHashName);
          List<String> hubBkFields = hub.getBusinessKeyFieldNames();
          TransformMeta checkSumTransform =
              addCheckSumForFields(
                  pipelineMeta,
                  predecessorTransform,
                  ctx.checkSumType,
                  hubBkFields,
                  hubHashName,
                  ctx.config,
                  index++);
          pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessorTransform, checkSumTransform));
          predecessorTransform = checkSumTransform;
        }

        // Final checksum for the Link Hash itself
        TransformMeta linkHashCalc =
            addCheckSumForFields(
                pipelineMeta,
                predecessorTransform,
                ctx.checkSumType,
                hubHashNames,
                linkHashKeyFieldName,
                ctx.config,
                index);
        pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessorTransform, linkHashCalc));
        predecessorTransform = linkHashCalc;

        TransformMeta selectTransform =
            addSourceSelectRows(ctx, pipelineMeta, predecessorTransform);

        // Target side
        TransformMeta targetInputTransform =
            addTargetTableInput(ctx, pipelineMeta);

        TransformMeta sortTransform = addSortRows(pipelineMeta, selectTransform, linkHashKeyFieldName);

        TransformMeta mergeTransform =
            addMergeRows(ctx, pipelineMeta, sortTransform, targetInputTransform);

        TransformMeta filterTransform = addFilterNewRows(pipelineMeta, mergeTransform);
        TransformMeta constantTransform =
            addConstantForLoadDate(ctx, pipelineMeta, loadDate, filterTransform);

        IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);

        addTableOutput(ctx, pipelineMeta, targetLayout, constantTransform);

        result.add(pipelineMeta);
      }

      return result;
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
      if (!Utils.isEmpty(config.getRecordSourceField())) {
        rsFieldName = config.getRecordSourceField();
      }
      rsFieldName = variables.resolve(rsFieldName);
      if (Utils.isEmpty(rsFieldName)) rsFieldName = "RECORD_SOURCE";
      String lengthString =
          !Utils.isEmpty(config.getRecordSourceFieldLength())
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

  private TransformMeta addSourceTableInput(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, DvLinkSource linkSource)
      throws HopException {

    DvDatabaseLinkSourcePipelineBuilder builder =
        new DvDatabaseLinkSourcePipelineBuilder(
            ctx.variables,
            ctx.metadataProvider,
            ctx.model,
            pipelineMeta,
            ctx.dataVaultSource,
            ctx.dvSource,
            this,
            new Point(LOCATION_START_LINE_2.x, LOCATION_START_LINE_2.y));
    builder.setDvLinkSource(linkSource);
    builder.build();
    return builder.getResultTransform();
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

    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private TransformMeta addSourceSelectRows(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    // Select the link table hash key
    //
    String hashKeyField = ctx.variables.resolve(linkHashKeyFieldName);
    if (StringUtils.isNotEmpty(hashKeyField)) {
      SelectField selectField = new SelectField();
      selectField.setName(hashKeyField);
      selectFields.add(selectField);
    } else {
      throw new HopException("Please specify a hash key field name for link " + getName());
    }

    // We want to keep only the hash keys from the hubs and the one from the link itself.
    //
    for (String hubName : hubNames) {
      DvHub hub = ctx.model.findHub(hubName);
      SelectField selectField = new SelectField();
      selectField.setName(ctx.variables.resolve(hub.getHashKeyFieldName()));
      selectFields.add(selectField);
    }

    // The driving keys need to be renamed to their target table name
    //
    for (String drivingKeyName : drivingKeyNames) {
      // Look up the source name for the record source in the context.
      String drivingKeySourceField = findSourceFieldOfDrivingKey(ctx, drivingKeyName);
      if (drivingKeySourceField == null) {
        throw new HopException(
            "Unable to find a source field for driving key "
                + drivingKeyName
                + " in Link "
                + getName()
                + " when building a pipeline to handle source "
                + ctx.dataVaultSource.getName());
      }
      SelectField selectField = new SelectField();
      selectField.setName(drivingKeySourceField);
      selectField.setRename(ctx.variables.resolve(drivingKeyName));
      selectFields.add(selectField);
    }

    // Also keep the record source
    //
    SelectField sourceField = new SelectField();
    sourceField.setName(findRecordSourceFieldName(ctx));
    selectFields.add(sourceField);

    TransformMeta tm = new TransformMeta("SelectValues", "select_values", selectMeta);
    // Place it on the compare flow after the last checksum, before the sort
    Point loc =
        new Point(
            LOCATION_START_LINE_2.x + SPACING_WIDTH * (hubNames.size() + 2),
            LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private String findSourceFieldOfDrivingKey(LinkUpdateContext ctx, String drivingKeyName) {
    for (DvLinkSource linkSource : linkSources) {
      if (linkSource.source.equals(ctx.dataVaultSource)) {
        // This is the source we're loading in this pipeline
        // See if we have the source for the driving key.
        for (HubSourceKeyField hubSourceKeyField : linkSource.hubSourceKeyFields) {
          for (DrivingKeySource drivingKeySource : hubSourceKeyField.getDrivingKeySources()) {
            if (drivingKeyName.equalsIgnoreCase(drivingKeySource.getDrivingKey())) {
              return drivingKeySource.getSourceField();
            }
          }
        }
      }
    }
    return null;
  }

  private TransformMeta addTargetTableInput(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    String quotedLinkHash = ctx.targetDatabaseMeta.quoteField(linkHashKeyFieldName);

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(quotedLinkHash);

    // Also select the hub hash columns that are stored in the link (for completeness / potential
    // value compare)
    //
    for (String hubName : hubNames) {
      DvHub hub = ctx.model.findHub(hubName);
      sql.append(", ").append(ctx.targetDatabaseMeta.quoteField(hub.getHashKeyFieldName()));
    }

    // The name of the source is described in the Link itself
    //
    String rsFieldName = findRecordSourceFieldName(ctx);
    sql.append(", NULL AS ").append(ctx.targetDatabaseMeta.quoteField(rsFieldName));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    sql.append(" ORDER BY ");
    sql.append(quotedLinkHash);

    targetTableInputMeta.setSql(sql.toString());

    String transformName = "target " + ctx.targetDatabaseMeta.getName() + "." + ctx.targetTableName;

    TransformMeta tm = new TransformMeta("TableInput", transformName, targetTableInputMeta);
    tm.setLocation(LOCATION_START_LINE_3);
    pipelineMeta.addTransform(tm);

    return tm;
  }

  private @NonNull String findRecordSourceFieldName(LinkUpdateContext ctx) throws HopException {
    String rsFieldName = recordSourceFieldName;
    if (StringUtils.isEmpty(rsFieldName)) {
      rsFieldName = ctx.variables.resolve(ctx.config.getRecordSourceField());
    }
    if (StringUtils.isEmpty(rsFieldName)) {
      throw new HopException(
          "Please specify a field to contain the record source in Link "
              + getName()
              + " or in the data vault configuration.");
    }
    return rsFieldName;
  }

  private TransformMeta addMergeRows(
      LinkUpdateContext ctx,
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
            referenceTransform.getLocation().x + SPACING_WIDTH,
            referenceTransform.getLocation().y);
    TransformMeta compareDummyTransform =
        addDummyTransform(
            pipelineMeta,
            compareTransform,
            "Merge compare",
            compareTransform.getLocation().x + SPACING_WIDTH,
            compareTransform.getLocation().y);

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceDummyTransform.getName());
    mergeRowsMeta.setCompareTransform(compareDummyTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    // We merge on the hash key of this table.
    //
    List<String> keyFields = new ArrayList<>();
    keyFields.add(linkHashKeyFieldName);
    mergeRowsMeta.setKeyFields(keyFields);

    // We pass through the source business hash keys, the driving keys, and the record source field
    // from the "compare"
    // data stream.
    //
    // The link table hash key
    mergeRowsMeta
        .getPassThroughFields()
        .add(new PassThroughField(linkHashKeyFieldName, null, false));

    // The hash keys of the hubs
    for (String hubName : hubNames) {
      DvHub hub = ctx.model.findHub(hubName);
      mergeRowsMeta
          .getPassThroughFields()
          .add(new PassThroughField(hub.getHashKeyFieldName(), null, false));
    }

    for (String drivingKeyName : drivingKeyNames) {
      mergeRowsMeta.getPassThroughFields().add(new PassThroughField(drivingKeyName, null, false));
    }

    // We also pass through the record source
    //
    String rsFieldName = findRecordSourceFieldName(ctx);
    mergeRowsMeta.getPassThroughFields().add(new PassThroughField(rsFieldName, null, false));

    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 2 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceDummyTransform, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareDummyTransform, tm));

    return tm;
  }

  private TransformMeta addFilterNewRows(PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (predecessor == null) {
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
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addSortRows(
      PipelineMeta pipelineMeta, TransformMeta predecessor, String sortFieldName) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    SortRowsField sf = new SortRowsField();
    sf.setFieldName(sortFieldName);
    sf.setAscending(true);
    sf.setCaseSensitive(true);
    sortRowsMeta.getSortFields().add(sf);

    TransformMeta tm = new TransformMeta("SortRows", "sort_" + sortFieldName, sortRowsMeta);
    Point loc =
        new Point(
            LOCATION_START_LINE_2.x + SPACING_WIDTH * (hubNames.size() + 3),
            LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, Date loadDate, TransformMeta predecessor)
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
    tm.setLocation(LOCATION_START_LINE_3.x + 4 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addTableOutput(
      LinkUpdateContext ctx,
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
      tm.setLocation(LOCATION_START_LINE_3.x + 5 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return tm;
    } catch (Exception e) {
      throw new HopException("Error creating Table Output transform", e);
    }
  }

  // The main addSortRows is defined above and used for both the link hash sort and the helper.

  /**
   * Represents the source key field mappings for one participating hub in a link. Allows mapping
   * the business key fields from the link's record source to the hub's business keys, supporting
   * different naming conventions (e.g. _fk vs _id).
   */
  @Getter
  @Setter
  public static class HubSourceKeyField {
    /** The name of the hub (matches an entry in hubNames). */
    @HopMetadataProperty private String hubName;

    /**
     * The list of source field names in the link's record source that correspond to this hub's
     * business keys.
     */
    @HopMetadataProperty(key = "businessKeySource", groupKey = "businessKeySources")
    private List<BusinessKeySource> sourceBusinessKeyFields;

    /** We need to know the sources of the driving key fields in the target table. */
    @HopMetadataProperty(key = "drivingKeySource", groupKey = "drivingKeySources")
    private List<DrivingKeySource> drivingKeySources;

    public HubSourceKeyField() {
      sourceBusinessKeyFields = new ArrayList<>();
      drivingKeySources = new ArrayList<>();
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

    final DataVaultSource dataVaultSource;
    final IDvSource dvSource;

    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String targetTableName;
    final String pipelineName;

    LinkUpdateContext(
        DvLink link,
        DataVaultModel model,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultConfiguration config,
        CheckSumType checkSumType,
        DataVaultSource dataVaultSource,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String targetTableName,
        String pipelineName)
        throws HopException {
      this.link = link;
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

      this.dvSource = dataVaultSource.getDvSource(metadataProvider);
    }

    static LinkUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvLink link,
        DvLinkSource linkSource)
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

      String targetTransformName = "target " + targetTableName;

      String linkHashKeyFieldName = link.getLinkHashKeyFieldName();
      if (Utils.isEmpty(linkHashKeyFieldName)) {
        linkHashKeyFieldName = link.getName() + "_LK";
      }

      if (linkSource == null) {
        throw new HopException("Please provide a link source to create a DV link");
      }

      // Use per-source data if provided (new multi-source model), else fall back to top-level
      // legacy fields
      DataVaultSource effectiveSource = linkSource.getSource();
      if (effectiveSource == null) {
        throw new HopException("Please provide a valid record source in Link " + link.getName());
      }

      return new LinkUpdateContext(
          link,
          model,
          metadataProvider,
          variables,
          config,
          checkSumType,
          effectiveSource,
          targetDatabaseMeta,
          targetDbName,
          targetTableName,
          pipelineName);
    }
  }

  @Getter
  @Setter
  public static class DvLinkSource {
    /** The Data Vault Source (record source) for this link feed. */
    @HopMetadataProperty(storeWithName = true)
    private DataVaultSource source;

    /**
     * Per-hub source business key field mappings for this specific source. Tells the system, for
     * each participating hub, which columns in *this* source correspond to the hub's business keys
     * (order must match the hub's business keys).
     */
    @HopMetadataProperty(key = "hubSourceKeyField", groupKey = "hubSourceKeyFields")
    private List<HubSourceKeyField> hubSourceKeyFields;

    public DvLinkSource() {
      hubSourceKeyFields = new ArrayList<>();
    }
  }
}
