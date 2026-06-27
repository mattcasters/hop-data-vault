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

package org.apache.hop.datavault.metadata;

import static java.util.Collections.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
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
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMeta;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMetaFactory;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.groupby.Aggregation;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupingField;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;
import org.apache.hop.pipeline.transforms.update.UpdateField;
import org.apache.hop.pipeline.transforms.update.UpdateKeyField;
import org.apache.hop.pipeline.transforms.update.UpdateLookupField;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;
import org.apache.hop.pipeline.transforms.selectvalues.DeleteField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.append.AppendMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;

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

  /** Stream field carrying the superseded row load date from the target (reference) leg. */
  private static final String PREVIOUS_LOAD_DATE_FIELD = "previous_load_date";

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

  /**
   * Name of the driving key column for multi-active satellites (e.g. multiple phone numbers per
   * customer). Leave empty for a standard single-active satellite.
   */
  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::DvSatellite.DrivingKey.Label",
      toolTip = "i18n::DvSatellite.DrivingKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String drivingKey;

  /**
   * Source column name in the record source that supplies the driving key value. Renamed to {@link
   * #drivingKey} in the update pipeline.
   */
  @GuiWidgetElement(
      order = "0710",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvSatellite.DrivingKeySourceField.Label",
      toolTip = "i18n::DvSatellite.DrivingKeySourceField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String drivingKeySourceField;

  @HopMetadataProperty private String recordSource;

  /** When enabled, a separate STS table tracks active/deleted status per load (full snapshot only). */
  @HopMetadataProperty private boolean statusTrackingEnabled;

  /** Physical table for status history (defaults to sts_ plus parent name when empty). */
  @HopMetadataProperty private String statusTableName;

  @HopMetadataProperty private String statusFieldName = "record_status";

  @HopMetadataProperty private String activeStatusValue = "ACTIVE";

  @HopMetadataProperty private String deletedStatusValue = "DELETED";

  public static final String DEFAULT_STATUS_FIELD_NAME = "record_status";
  public static final String DEFAULT_ACTIVE_STATUS_VALUE = "ACTIVE";
  public static final String DEFAULT_DELETED_STATUS_VALUE = "DELETED";
  public static final int DEFAULT_STATUS_FIELD_LENGTH = 20;

  public String getRecordSourceName() {
    return recordSource;
  }

  public void setRecordSourceName(String recordSourceName) {
    if (!java.util.Objects.equals(this.recordSource, recordSourceName)) {
      setChanged();
    }
    this.recordSource = recordSourceName;
  }

  public DataVaultSource resolveRecordSource(
      IVariables variables, IHopMetadataProvider metadataProvider, DataVaultModel model)
      throws HopException {
    if (Utils.isEmpty(recordSource)) {
      return null;
    }
    return DvSourceCatalogService.resolveSource(recordSource, model, variables, metadataProvider);
  }

  public boolean hasDrivingKey() {
    return !Utils.isEmpty(drivingKey) && !Utils.isEmpty(drivingKeySourceField);
  }

  public boolean isStatusTrackingEnabled() {
    return statusTrackingEnabled;
  }

  public String resolveStatusTableName(IVariables variables, DataVaultModel model) {
    if (!Utils.isEmpty(statusTableName)) {
      return variables.resolve(statusTableName);
    }
    String parent =
        !Utils.isEmpty(hubName)
            ? hubName
            : (!Utils.isEmpty(linkName)
                ? linkName
                : (!Utils.isEmpty(getTableName()) ? getTableName() : getName()));
    return "sts_" + variables.resolve(parent);
  }

  public String resolveStatusFieldName(IVariables variables) {
    String name =
        Utils.isEmpty(statusFieldName) ? DEFAULT_STATUS_FIELD_NAME : statusFieldName;
    return variables.resolve(name);
  }

  public String resolveActiveStatusValue(IVariables variables) {
    String value =
        Utils.isEmpty(activeStatusValue) ? DEFAULT_ACTIVE_STATUS_VALUE : activeStatusValue;
    return variables.resolve(value);
  }

  public String resolveDeletedStatusValue(IVariables variables) {
    String value =
        Utils.isEmpty(deletedStatusValue) ? DEFAULT_DELETED_STATUS_VALUE : deletedStatusValue;
    return variables.resolve(value);
  }

  public DvSatellite() {
    super();
    this.tableType = DvTableType.SATELLITE;
  }

  public DvSatellite(String name) {
    super(name);
    this.tableType = DvTableType.SATELLITE;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvModelCheckOptions options,
      DataVaultModel model) {
    super.check(remarks, metadataProvider, variables, options, model);
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
      if (!DvIntegrationSupport.relaxesSourceValidation(this)
          && !Utils.isEmpty(recordSource)
          && metadataProvider != null
          && !Utils.isEmpty(attributes)) {
        try {
          DataVaultSource resolvedSource = resolveRecordSource(variables, metadataProvider, model);
          List<SourceField> sourceFields = resolvedSource.getFields(metadataProvider);
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
                          + Const.NVL(recordSource, "")
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

    if (!hasDrivingKey()) {
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
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvSatellite.CheckResult.HasDrivingKeySourceField", drivingKeySourceField),
              this));
    }

    if (isStatusTrackingEnabled()) {
      checkStatusTracking(remarks, metadataProvider, variables, model);
    }

    if (!DvIntegrationSupport.relaxesSourceValidation(this) && metadataProvider != null) {
      DvModelCheckOptions effectiveOptions =
          options != null ? options : DvModelCheckOptions.fastOnly();
      DvFieldMappingValidationSupport.validateSatelliteMappings(
          this, model, effectiveOptions, metadataProvider, variables, this, remarks);
    }
  }

  private void checkStatusTracking(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model) {
    if (Utils.isEmpty(recordSource)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.StsNoRecordSource"),
              this));
      return;
    }
    try {
      DataVaultSource resolvedSource = resolveRecordSource(variables, metadataProvider, model);
      if (!resolvedSource.isFullSnapshotFeed()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DvSatellite.CheckResult.StsRequiresFullSnapshot", recordSource),
                this));
      }
    } catch (HopException e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Error loading record source for status tracking: " + e.getMessage(),
              this));
    }
    String stsTable = resolveStatusTableName(variables, null);
    if (Utils.isEmpty(stsTable)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.StsNoTableName"),
              this));
    }
    String statusField = resolveStatusFieldName(variables);
    if (Utils.isEmpty(statusField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.StsNoStatusField"),
              this));
    }
    String active = resolveActiveStatusValue(variables);
    String deleted = resolveDeletedStatusValue(variables);
    if (Utils.isEmpty(active) || Utils.isEmpty(deleted)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.StsEmptyStatusValue"),
              this));
    } else if (active.equals(deleted)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.StsEqualStatusValues"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvSatellite.CheckResult.StsEnabled", stsTable),
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
    try {
      if (metadataProvider == null || model == null) {
        return emptyList();
      }

      if (DvIntegrationSupport.isExternalRead(this)) {
        return emptyList();
      }
      if (DvIntegrationSupport.isCustomPipelines(this)) {
        return DvIntegrationSupport.loadCustomUpdatePipelines(this, metadataProvider, variables);
      }

      DataVaultSource resolvedRecordSource = resolveRecordSource(variables, metadataProvider, model);
      if (resolvedRecordSource != null
          && !resolvedRecordSource.matchesRecordSourceGroup(recordSourceGroup, variables)) {
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

      // Source TableInput (from record source) - hub/link bks + sat attrs + record source
      TransformMeta sourceInputTransform =
          addSourceTableInput(ctx, resolvedRecordSource, pipelineMeta);

      TransformMeta hashChainEndTransform =
          ctx.linkSatellite
              ? addLinkHashKeyChain(ctx, pipelineMeta, sourceInputTransform)
              : addDvHashKey(ctx, pipelineMeta, sourceInputTransform);

      TransformMeta sourceSelectTransform =
          addSourceSelectRows(ctx, pipelineMeta, hashChainEndTransform);

      // Sort the records by hash key
      TransformMeta sortHkTransform = addSortRows(ctx, pipelineMeta, sourceSelectTransform);

      // Target TableInput (hash + attributes from sat table, for diff + value comparison)
      TransformMeta targetInputTransform = addTargetTableInput(ctx, pipelineMeta);

      // Compare leg: with end-dating the load date is added before the diff; otherwise after filter.
      TransformMeta compareTransform =
          useLoadEndDate(ctx)
              ? addConstantForLoadDate(ctx, pipelineMeta, loadDate, sortHkTransform)
              : sortHkTransform;

      // Perform CDC with a merge rows (diff) transform
      TransformMeta mergeTransform =
          addMergeRows(ctx, pipelineMeta, compareTransform, targetInputTransform);

      // Filter Rows: keep rows that are not 'identical' (i.e. new or changed attribute values
      // for an existing hub in the satellite). We achieve this by testing for 'identical' and
      // negating the condition.
      TransformMeta filterTransform = addFilterNewRows(pipelineMeta, mergeTransform);

      TransformMeta insertPredecessor = filterTransform;
      if (!useLoadEndDate(ctx)) {
        insertPredecessor = addConstantForLoadDate(ctx, pipelineMeta, loadDate, filterTransform);
      } else {
        insertPredecessor =
            addConstantForOpenLoadEndDate(ctx, pipelineMeta, insertPredecessor);
      }

      // Add Table Output to insert new satellite versions
      IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);

      TransformMeta tableOutputTransform =
          addTableOutput(ctx, pipelineMeta, targetLayout, insertPredecessor);

      if (useLoadEndDate(ctx)) {
        TransformMeta filterPreviousTransform =
            addFilterHasPreviousLoadDate(ctx, pipelineMeta, tableOutputTransform);
        addUpdateLoadEndDate(ctx, pipelineMeta, filterPreviousTransform);
      }

      List<PipelineMeta> pipelines = new ArrayList<>();
      pipelines.add(pipelineMeta);

      if (isStatusTrackingEnabled()
          && resolvedRecordSource != null
          && resolvedRecordSource.isFullSnapshotFeed()) {
        pipelines.add(generateStatusTrackingPipeline(ctx, loadDate));
      }

      DvGeneratedPipelineSupport.applyLayout(pipelines);
      return pipelines;
    } catch (Exception e) {
      throw new HopException(
          "Error generating update pipeline for Satellite target " + getName(), e);
    }
  }

  @Override
  public List<String> generateUpdateDdl(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    if (DvIntegrationSupport.shouldSkipDdl(this)) {
      return emptyList();
    }
    List<String> result = super.generateUpdateDdl(metadataProvider, variables, model);
    if (metadataProvider == null || model == null) {
      return result;
    }

    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config != null && config.isUseLoadEndDate()) {
      appendLoadEndDateHashKeyIndexDdl(result, metadataProvider, variables, model, config);
    }

    if (!isStatusTrackingEnabled()) {
      return result;
    }

    DatabaseMeta targetDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
    if (targetDatabaseMeta == null) {
      return result;
    }

    String stsTableName = resolveStatusTableName(variables, model);
    IRowMeta stsLayout = getStatusTargetTableLayout(metadataProvider, variables, model);
    if (stsLayout == null || Utils.isEmpty(stsTableName)) {
      return result;
    }

    ILoggingObject loggingObject =
        new SimpleLoggingObject(
            getClass().getSimpleName() + ".generateUpdateDdl.sts",
            LoggingObjectType.GENERAL,
            null);
    try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
      db.connect();
      String ddl =
          generateTargetTableDdl(
              db,
              config,
              targetDatabaseMeta,
              stsTableName,
              stsLayout,
              metadataProvider,
              variables,
              model);
      if (!Utils.isEmpty(ddl)) {
        result.add(ddl);
      }
    } catch (Exception e) {
      throw new HopException("Error getting DDL for STS target table: " + stsTableName, e);
    }
    return result;
  }

  @Override
  protected String[] resolveShardKeyColumns(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      IRowMeta targetFields) {
    String[] columns = super.resolveShardKeyColumns(metadataProvider, variables, model, targetFields);
    if (columns.length == 0) {
      return columns;
    }

    DataVaultConfiguration config = model != null ? model.getConfigurationOrDefault() : null;
    if (config == null || !config.isSingleStoreShardKeyIncludeDrivingKeys() || !hasDrivingKey()) {
      return columns;
    }

    String drivingKeyColumn = variables != null ? variables.resolve(drivingKey) : drivingKey;
    if (Utils.isEmpty(drivingKeyColumn)) {
      return columns;
    }
    return new String[] {columns[0], drivingKeyColumn};
  }

  /**
   * Adds a hash-key index on the satellite target table when load-end-date updates are enabled.
   * Supports the Update transform that closes prior satellite rows by parent hash key.
   */
  private void appendLoadEndDateHashKeyIndexDdl(
      List<String> result,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      DataVaultConfiguration config)
      throws HopException {
    DatabaseMeta targetDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
    if (targetDatabaseMeta == null) {
      return;
    }

    IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);
    if (targetLayout == null || targetLayout.isEmpty()) {
      return;
    }

    String hashKeyField = targetLayout.getValueMeta(0).getName();
    String targetTableName = !Utils.isEmpty(getTableName()) ? getTableName() : getName();
    if (Utils.isEmpty(hashKeyField) || Utils.isEmpty(targetTableName)) {
      return;
    }

    String schemaTable =
        targetDatabaseMeta.getQuotedSchemaTableCombination(variables, null, targetTableName);
    String[] idxFields = new String[] {hashKeyField};

    ILoggingObject loggingObject =
        new SimpleLoggingObject(
            getClass().getSimpleName() + ".generateUpdateDdl.index",
            LoggingObjectType.GENERAL,
            null);
    try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
      db.connect();
      if (!db.checkIndexExists(schemaTable, idxFields)) {
        String indexName = "idx_" + targetTableName + "_hk";
        String indexDdl =
            db.getCreateIndexStatement(schemaTable, indexName, idxFields, false, false, false, true);
        if (!Utils.isEmpty(indexDdl)) {
          result.add(indexDdl);
        }
      }
    } catch (Exception e) {
      throw new HopException(
          "Error getting hash key index DDL for satellite target table: " + targetTableName, e);
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
      DataVaultConfiguration config = model.getConfigurationOrDefault();

      // 1. First column: parent hash key (hub hash for hub satellites, link hash for link
      // satellites)
      String hashKeyName = "hashkey";
      DvHub linkedHub = null;
      if (!Utils.isEmpty(hubName)) {
        linkedHub = model.findHub(hubName);
        if (linkedHub == null) {
          throw new HopException("Please provide an existing hub in satellite " + getName());
        }
        hashKeyName = linkedHub.getHashKeyFieldName();
        if (Utils.isEmpty(hashKeyName)) {
          if (!Utils.isEmpty(linkedHub.getBusinessKeys())) {
            String bkName = linkedHub.getBusinessKeys().get(0).getName();
            hashKeyName = bkName + "_hk";
          }
        }
      } else if (!Utils.isEmpty(linkName)) {
        DvLink linkedLink = model.findLink(linkName);
        if (linkedLink == null) {
          throw new HopException("Please provide an existing link in satellite " + getName());
        }
        hashKeyName = linkedLink.getLinkHashKeyFieldName();
        if (Utils.isEmpty(hashKeyName)) {
          hashKeyName = linkedLink.getName() + "_LK";
        }
      }

      IValueMeta hashMeta;
      HashKeyDataType hdt = config.resolveHashKeyDataType();
      HashAlgorithm algo = config.resolveHashAlgorithm();
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
        // STRING: the decimal-dash format produced by DvHashKey
        // max length = N*3 + (N-1)  e.g. 63 for MD5
        int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
        hashMeta = new ValueMetaString(hashKeyName);
        hashMeta.setLength(stringMax);
      }
      rowMeta.addValueMeta(hashMeta);

      // Determine attributes
      boolean externalRead = DvIntegrationSupport.isExternalRead(this);
      List<SourceField> sourceFields = null;
      if (!Utils.isEmpty(recordSource)) {
        try {
          DataVaultSource resolvedSource = resolveRecordSource(variables, metadataProvider, model);
          if (resolvedSource != null) {
            sourceFields = resolvedSource.getFields(metadataProvider);
          }
        } catch (Exception e) {
          if (!externalRead) {
            if (e instanceof HopException hopException) {
              throw hopException;
            }
            throw new HopException(
                "Error resolving record source for satellite " + getName(), e);
          }
        }
      } else if (!externalRead) {
        throw new HopException("Please specify a record source for Satellite " + getName());
      }

      List<SatelliteAttribute> satAttrs = getAttributes();
      if (externalRead && Utils.isEmpty(satAttrs)) {
        throw new HopException(
            "External satellite "
                + getName()
                + " requires explicit attributes in the model (no catalog source needed).");
      }

      if (hasDrivingKey()) {
        String drivingKeyName = variables.resolve(drivingKey);
        IValueMeta drivingKeyMeta = null;
        if (sourceFields != null) {
          for (SourceField sf : sourceFields) {
            if (drivingKeySourceField.equals(sf.getName())) {
              drivingKeyMeta = createValueMetaFromSourceField(sf);
              drivingKeyMeta.setName(drivingKeyName);
              break;
            }
          }
        }
        if (drivingKeyMeta == null && externalRead && !Utils.isEmpty(satAttrs)) {
          for (SatelliteAttribute attr : satAttrs) {
            if (drivingKeySourceField.equals(attr.getName())) {
              drivingKeyMeta = createValueMetaFromAttribute(attr);
              drivingKeyMeta.setName(drivingKeyName);
              break;
            }
          }
        }
        if (drivingKeyMeta == null) {
          throw new HopException(
              "Driving key source field '"
                  + drivingKeySourceField
                  + "' not found in record source for satellite "
                  + getName());
        }
        rowMeta.addValueMeta(drivingKeyMeta);
      }

      if (Utils.isEmpty(satAttrs) && sourceFields != null) {
        List<SourceField> autoAttributeFields =
            linkedHub != null
                ? selectHubSatelliteAutoAttributeSourceFields(
                    linkedHub, this, variables, sourceFields)
                : sourceFields;
        for (SourceField sf : autoAttributeFields) {
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
      loadDateField = variables.resolve(loadDateField);
      IValueMeta loadMeta = new ValueMetaTimestamp(loadDateField);
      rowMeta.addValueMeta(loadMeta);

      if (config.isUseLoadEndDate()) {
        String loadEndDateField = config.getLoadEndDateField();
        if (Utils.isEmpty(loadEndDateField)) {
          loadEndDateField = "LOAD_END_DATE";
        }
        loadEndDateField = variables.resolve(loadEndDateField);
        IValueMeta loadEndMeta = new ValueMetaTimestamp(loadEndDateField);
        rowMeta.addValueMeta(loadEndMeta);
      }

      return rowMeta;

    } catch (Exception e) {
      throw new HopException("Error building target table layout for satellite", e);
    }
  }

  /**
   * Target row layout for the optional Status Tracking Satellite table (parent hash, optional
   * driving key, status column, record source, load date).
   */
  public IRowMeta getStatusTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    if (metadataProvider == null || model == null) {
      return null;
    }

    IRowMeta rowMeta = new RowMeta();
    DataVaultConfiguration config = model.getConfigurationOrDefault();

    String hashKeyName = resolveParentHashKeyFieldName(model, variables);
    rowMeta.addValueMeta(createHashKeyValueMeta(hashKeyName, config));

    if (hasDrivingKey()) {
      List<SourceField> sourceFields =
          resolveRecordSource(variables, metadataProvider, model).getFields(metadataProvider);
      String drivingKeyName = variables.resolve(drivingKey);
      IValueMeta drivingKeyMeta = null;
      for (SourceField sf : sourceFields) {
        if (drivingKeySourceField.equals(sf.getName())) {
          drivingKeyMeta = createValueMetaFromSourceField(sf);
          drivingKeyMeta.setName(drivingKeyName);
          break;
        }
      }
      if (drivingKeyMeta == null) {
        throw new HopException(
            "Driving key source field '"
                + drivingKeySourceField
                + "' not found in record source for satellite "
                + getName());
      }
      rowMeta.addValueMeta(drivingKeyMeta);
    }

    String statusField = resolveStatusFieldName(variables);
    IValueMeta statusMeta = new ValueMetaString(statusField);
    int statusLength = DEFAULT_STATUS_FIELD_LENGTH;
    if (config != null && !Utils.isEmpty(config.getRecordSourceFieldLength())) {
      statusLength = Const.toInt(variables.resolve(config.getRecordSourceFieldLength()), statusLength);
    }
    statusMeta.setLength(statusLength);
    rowMeta.addValueMeta(statusMeta);

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
    int rsLength = Const.toInt(variables.resolve(lengthString), 100);
    IValueMeta rsMeta = new ValueMetaString(rsFieldName);
    rsMeta.setLength(rsLength);
    rowMeta.addValueMeta(rsMeta);

    String loadDateField = config.getLoadDateField();
    if (Utils.isEmpty(loadDateField)) {
      loadDateField = "LOAD_DATE";
    }
    loadDateField = variables.resolve(loadDateField);
    rowMeta.addValueMeta(new ValueMetaTimestamp(loadDateField));

    return rowMeta;
  }

  private String resolveParentHashKeyFieldName(DataVaultModel model, IVariables variables)
      throws HopException {
    if (!Utils.isEmpty(hubName)) {
      DvHub linkedHub = model.findHub(hubName);
      if (linkedHub == null) {
        throw new HopException("Please provide an existing hub in satellite " + getName());
      }
      String hashKeyName = linkedHub.getHashKeyFieldName();
      if (Utils.isEmpty(hashKeyName) && !Utils.isEmpty(linkedHub.getBusinessKeys())) {
        hashKeyName = linkedHub.getBusinessKeys().get(0).getName() + "_hk";
      }
      return hashKeyName;
    }
    if (!Utils.isEmpty(linkName)) {
      DvLink linkedLink = model.findLink(linkName);
      if (linkedLink == null) {
        throw new HopException("Please provide an existing link in satellite " + getName());
      }
      String hashKeyName = variables.resolve(linkedLink.getLinkHashKeyFieldName());
      if (Utils.isEmpty(hashKeyName)) {
        hashKeyName = linkedLink.getName() + "_LK";
      }
      return hashKeyName;
    }
    throw new HopException("Satellite " + getName() + " is not linked to a hub or link");
  }

  private IValueMeta createHashKeyValueMeta(String hashKeyName, DataVaultConfiguration config)
      throws HopException {
    HashKeyDataType hdt = config.resolveHashKeyDataType();
    HashAlgorithm algo = config.resolveHashAlgorithm();
    if (algo == null) {
      algo = HashAlgorithm.MD5;
    }
    int digestBytes = algo.getDigestLength();

    IValueMeta hashMeta;
    if (hdt == HashKeyDataType.BINARY) {
      hashMeta = new ValueMetaBinary(hashKeyName);
      hashMeta.setLength(digestBytes);
    } else if (hdt == HashKeyDataType.HEX) {
      hashMeta = new ValueMetaString(hashKeyName);
      hashMeta.setLength(digestBytes * 2);
    } else {
      int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
      hashMeta = new ValueMetaString(hashKeyName);
      hashMeta.setLength(stringMax);
    }
    return hashMeta;
  }

  @Override
  public int ensureSpecialRecords(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    // Satellites do not carry unknown/invalid sentinel rows; parent hub/link hashes are ensured
    // separately.
    return 0;
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

  private TransformMeta addSourceTableInput(
      SatelliteUpdateContext ctx, DataVaultSource dataVaultSource, PipelineMeta pipelineMeta)
      throws HopException {
    DvSourcePipelineBuilder builder =
        DvSourcePipelineBuilderFactory.forSatellite(
            ctx.variables,
            ctx.metadataProvider,
            ctx.model,
            pipelineMeta,
            dataVaultSource,
            ctx.dvSource,
            this,
            new Point(LOCATION_START_LINE_2.x, LOCATION_START_LINE_2.y));
    if (ctx.linkSatellite) {
      if (builder instanceof DvDatabaseSatelliteSourcePipelineBuilder dbBuilder) {
        dbBuilder.setLinkedLink(ctx.linkedLink);
        dbBuilder.setDvLinkHubSource(ctx.linkHubSource);
        dbBuilder.setDvLinkSatelliteSource(ctx.linkSatelliteSource);
      } else if (builder
          instanceof org.apache.hop.datavault.metadata.file.DvCsvSatelliteSourcePipelineBuilder csvBuilder) {
        csvBuilder.setLinkedLink(ctx.linkedLink);
        csvBuilder.setDvLinkHubSource(ctx.linkHubSource);
        csvBuilder.setDvLinkSatelliteSource(ctx.linkSatelliteSource);
      }
    }
    builder.build();

    return builder.getResultTransform();
  }

  /**
   * For link satellites: compute each participating hub hash from mapped source business key
   * fields, then compute the link hash from those hub hashes.
   */
  private TransformMeta addLinkHashKeyChain(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    TransformMeta current = predecessor;
    List<String> hubHashNames = new ArrayList<>();
    int index = 0;
    for (SatelliteUpdateContext.HubHashCalcStep step : ctx.hubHashCalcSteps) {
      current =
          addDvHashKeyForFields(
              ctx,
              pipelineMeta,
              current,
              step.inputFieldNames(),
              step.hashKeyFieldName(),
              index++);
      hubHashNames.add(step.hashKeyFieldName());
    }
    return addDvHashKeyForFields(
        ctx,
        pipelineMeta,
        current,
        hubHashNames,
        ctx.hashKeyFieldName,
        index);
  }

  private TransformMeta addDvHashKeyForFields(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<String> inputFieldNames,
      String resultFieldName,
      int index) {
    DvHashKeyMeta hashKeyMeta =
        DvHashKeyMetaFactory.create(ctx.config, inputFieldNames, resultFieldName);

    TransformMeta tm = new TransformMeta("DvHashKey", "calc_" + resultFieldName, hashKeyMeta);
    tm.setLocation(
        LOCATION_START_LINE_2.x + (index + 1) * SPACING_WIDTH, LOCATION_START_LINE_2.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addDvHashKey(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    String resultFieldName = ctx.hashKeyFieldName;
    DvHashKeyMeta hashKeyMeta =
        DvHashKeyMetaFactory.create(ctx.config, ctx.pkSourceFieldNames, resultFieldName);

    TransformMeta tm = new TransformMeta("DvHashKey", "calc_" + resultFieldName, hashKeyMeta);
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

    if (!ctx.linkSatellite) {
      DvHub hub = ctx.model.findHub(getHubName());
      if (hub == null) {
        throw new HopException(
            "Unable to find the hub (" + getHubName() + ") to satellite " + getName());
      }
    }

    SelectField hashField = new SelectField();
    hashField.setName(ctx.hashKeyFieldName);
    selectFields.add(hashField);

    if (ctx.hasDrivingKey()) {
      SelectField drivingKeyField = new SelectField();
      drivingKeyField.setName(ctx.drivingKeySourceFieldName);
      drivingKeyField.setRename(ctx.drivingKeyFieldName);
      selectFields.add(drivingKeyField);
    }

    for (int i = 0; i < ctx.satAttrFieldNames.size(); i++) {
      String sourceName = ctx.satAttrSourceFieldNames.get(i);
      String targetName = ctx.satAttrFieldNames.get(i);
      SelectField selectField = new SelectField();
      selectField.setName(sourceName);
      if (!sourceName.equals(targetName)) {
        selectField.setRename(targetName);
      }
      selectFields.add(selectField);
    }

    SelectField recordSourceSelect = new SelectField();
    recordSourceSelect.setName(ctx.recordSourceField);
    selectFields.add(recordSourceSelect);

    TransformMeta tm = new TransformMeta("SelectValues", "hk plus attr", selectMeta);
    int selectX =
        LOCATION_START_LINE_2.x + (ctx.hashChainLength() + 1) * SPACING_WIDTH;
    Point loc = new Point(selectX, LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private TransformMeta addSortRows(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    return addSortRows(ctx, pipelineMeta, predecessor, "sort " + ctx.hashKeyFieldName);
  }

  private TransformMeta addSortRows(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String sortTransformName) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    SortRowsField sortField = new SortRowsField();
    sortField.setFieldName(ctx.hashKeyFieldName);
    sortField.setAscending(true);
    sortField.setCaseSensitive(
        true); // hash values (binary or hex strings) should compare case-sensitively
    sortRowsMeta.getSortFields().add(sortField);

    if (ctx.hasDrivingKey()) {
      SortRowsField drivingKeySort = new SortRowsField();
      drivingKeySort.setFieldName(ctx.drivingKeyFieldName);
      drivingKeySort.setAscending(true);
      drivingKeySort.setCaseSensitive(true);
      sortRowsMeta.getSortFields().add(drivingKeySort);
    }

    DvSortRowsSupport.applyConfiguration(sortRowsMeta, ctx.config, ctx.variables);

    TransformMeta tm = new TransformMeta("SortRows", sortTransformName, sortRowsMeta);
    tm.setLocation(
        LOCATION_START_LINE_2.x + (ctx.hashChainLength() + 2) * SPACING_WIDTH,
        LOCATION_START_LINE_2.y);
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
    // With load end dating, the WHERE clause limits the read to current rows (open end date);
    // the Group By below still deduplicates per hash key using LAST_INCL_NULL.
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
    if (ctx.hasDrivingKey()) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.recordSourceField));
    if (useLoadEndDate(ctx)) {
      selectFields.add(quotedLoadDate);
    }
    selectFields.add(quotedHash);

    sql.append(String.join(", ", selectFields));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    if (useLoadEndDate(ctx)) {
      String loadEndDateField = determineTargetLoadEndDateField(ctx);
      sql.append(" WHERE ");
      sql.append(ctx.targetDatabaseMeta.quoteField(loadEndDateField));
      sql.append(" IS NULL");
    }
    sql.append(" ORDER BY ");
    sql.append(quotedHash);
    if (ctx.hasDrivingKey()) {
      sql.append(", ");
      sql.append(ctx.targetDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
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
    if (ctx.hasDrivingKey()) {
      groupingFields.add(new GroupingField(ctx.drivingKeyFieldName));
    }
    groupByMeta.setGroupingFields(groupingFields);

    List<Aggregation> aggregations = new ArrayList<>();
    for (String attr : ctx.satAttrFieldNames) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
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

    if (useLoadEndDate(ctx)) {
      Aggregation loadDateAgg = new Aggregation();
      loadDateAgg.setSubject(loadDateField);
      loadDateAgg.setField(loadDateField);
      loadDateAgg.setTypeLabel("LAST_INCL_NULL");
      aggregations.add(loadDateAgg);
    }

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

  private TransformMeta addReferenceLoadDateTypeSelectValues(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    if (predecessor == null) {
      return null;
    }

    String loadDateField = determineTargetLoadDateField(ctx);

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    SelectMetadataChange metaChange = new SelectMetadataChange();
    metaChange.setName(loadDateField);
    metaChange.setType(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_DATE));
    selectMeta.getSelectOption().getMeta().add(metaChange);

    TransformMeta tm =
        new TransformMeta(
            "SelectValues", "ref_load_date_as_date", selectMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addMergeRows(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform) {
    if (referenceTransform == null || compareTransform == null) {
      return null;
    }

    boolean loadEndDate = useLoadEndDate(ctx);
    int referenceDummyX =
        LOCATION_START_LINE_3.x + (loadEndDate ? 3 : 2) * SPACING_WIDTH;
    int mergeX = LOCATION_START_LINE_3.x + (loadEndDate ? 5 : 3) * SPACING_WIDTH;

    TransformMeta referenceMergeTransform = referenceTransform;

    // Whether it's a date or a Timestamp in the source data, we force it to a Date to
    // match the Date from the Add Constant values transform in the source branch.
    //
    if (loadEndDate) {
      referenceMergeTransform =
          addReferenceLoadDateTypeSelectValues(ctx, pipelineMeta, referenceMergeTransform);
    }

    // A bug in hop requires an extra dummy to read from both reference and compare transforms
    //
    referenceMergeTransform =
            addDummyTransform(
                    pipelineMeta,
                    referenceMergeTransform,
                    "Merge reference",
                    referenceDummyX,
                    LOCATION_START_LINE_3.y);

    TransformMeta compareDummyTransform =
        addDummyTransform(
            pipelineMeta,
            compareTransform,
            "Merge compare",
            compareTransform.getLocation().x + SPACING_WIDTH,
            compareTransform.getLocation().y);

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceMergeTransform.getName());
    mergeRowsMeta.setCompareTransform(compareDummyTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    keyFields.add(ctx.hashKeyFieldName);
    if (ctx.hasDrivingKey()) {
      keyFields.add(ctx.drivingKeyFieldName);
    }
    mergeRowsMeta.setKeyFields(keyFields);

    // Value fields: the satellite attributes. MergeRows will compare these (when keys/hash match)
    // to decide between 'identical' and 'changed'.
    for (SatelliteAttribute attribute : attributes) {
      if (attribute.isIncludeInChangeDataCapture()) {
        mergeRowsMeta.getValueFields().add(attribute.getName());
      }
    }

    if (loadEndDate) {
      String loadDateField = determineTargetLoadDateField(ctx);
      mergeRowsMeta
          .getPassThroughFields()
          .add(new PassThroughField(loadDateField, PREVIOUS_LOAD_DATE_FIELD, true));
    }

    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(mergeX, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceMergeTransform, tm));
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
    tm.setLocation(mergeTransform.getLocation().x + SPACING_WIDTH, mergeTransform.getLocation().y);
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
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private TransformMeta addConstantForOpenLoadEndDate(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    String loadEndDateField = determineTargetLoadEndDateField(ctx);

    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField cf = new ConstantField(loadEndDateField, "Date", "");
    cf.setFieldFormat(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    constantMeta.getFields().add(cf);

    TransformMeta tm =
        new TransformMeta("Constant", "open_" + loadEndDateField, constantMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addFilterHasPreviousLoadDate(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    Condition condition =
        new Condition(PREVIOUS_LOAD_DATE_FIELD, Condition.Function.NOT_NULL, null, null);
    filterRowsMeta.getCompare().setCondition(condition);

    TransformMeta tm =
        new TransformMeta("FilterRows", "has_previous_load_date", filterRowsMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addUpdateLoadEndDate(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (predecessor == null || ctx.targetDatabaseMeta == null) {
      return null;
    }

    String loadDateField = determineTargetLoadDateField(ctx);
    String loadEndDateField = determineTargetLoadEndDateField(ctx);
    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      tableName = getName();
    }

    UpdateMeta updateMeta = new UpdateMeta();
    updateMeta.setConnection(ctx.targetDbName);

    UpdateLookupField lookup = new UpdateLookupField();
    lookup.setTableName(tableName);
    lookup
        .getLookupKeys()
        .add(new UpdateKeyField(ctx.hashKeyFieldName, ctx.hashKeyFieldName, "="));
    if (ctx.hasDrivingKey()) {
      lookup
          .getLookupKeys()
          .add(
              new UpdateKeyField(ctx.drivingKeyFieldName, ctx.drivingKeyFieldName, "="));
    }
    lookup
        .getLookupKeys()
        .add(new UpdateKeyField(PREVIOUS_LOAD_DATE_FIELD, loadDateField, "="));
    lookup.getUpdateFields().add(new UpdateField(loadEndDateField, loadDateField));
    updateMeta.setLookupField(lookup);

    TransformMeta tm =
        new TransformMeta("Update", "close_" + tableName, updateMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static boolean useLoadEndDate(SatelliteUpdateContext ctx) {
    return ctx.config != null && ctx.config.isUseLoadEndDate();
  }

  private static String determineTargetLoadEndDateField(SatelliteUpdateContext ctx) {
    String loadEndDateField = "LOAD_END_DATE";
    if (ctx.config != null && !Utils.isEmpty(ctx.config.getLoadEndDateField())) {
      loadEndDateField = ctx.config.getLoadEndDateField();
    }
    return ctx.variables.resolve(loadEndDateField);
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
      tableOutputMeta.setCommitSize(
          ctx.config.resolveTargetTableCommitSize(ctx.variables));

      if (targetLayout != null) {
        for (IValueMeta vm : targetLayout.getValueMetaList()) {
          String name = vm.getName();
          if (!"flag".equalsIgnoreCase(name)
              && !PREVIOUS_LOAD_DATE_FIELD.equalsIgnoreCase(name)) {
            tableOutputMeta.getFields().add(new TableOutputField(name, name));
          }
        }
      }

      TransformMeta tm = new TransformMeta("TableOutput", "write_to_" + tableName, tableOutputMeta);
      tm.setCopiesString(ctx.config.resolveTargetTableParallelCopies(ctx.variables));
      tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return tm;
    } catch (Exception e) {
      throw new HopException("Error creating Table Output transform", e);
    }
  }

  private static final Point LOCATION_STS_LINE_1 = new Point(160, 48);
  private static final Point LOCATION_STS_LINE_2 = new Point(160, 200);
  private static final Point LOCATION_STS_LINE_3 = new Point(160, 360);

  private PipelineMeta generateStatusTrackingPipeline(SatelliteUpdateContext ctx, Date loadDate)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    String stsTableName = resolveStatusTableName(ctx.variables, ctx.model);
    String pipelineName =
        ctx.config.buildStsPipelineName(
            ctx.variables, stsTableName, ctx.dataVaultSource.getName());
    pipelineMeta.setName(pipelineName);

    TransformMeta sourceInputTransform =
        addSourceTableInput(ctx, ctx.dataVaultSource, pipelineMeta, LOCATION_STS_LINE_2);

    TransformMeta hashChainEndTransform =
        ctx.linkSatellite
            ? addLinkHashKeyChain(ctx, pipelineMeta, sourceInputTransform, LOCATION_STS_LINE_2)
            : addDvHashKey(ctx, pipelineMeta, sourceInputTransform, LOCATION_STS_LINE_2);

    TransformMeta sourceKeysTransform =
        addStsSourceKeySelect(ctx, pipelineMeta, hashChainEndTransform);

    TransformMeta sortedSourceKeys =
        addSortRows(ctx, pipelineMeta, sourceKeysTransform, "sts_sort_source_" + ctx.hashKeyFieldName);
    // Fan out to both the active-status branch and the deletion merge compare leg.
    sortedSourceKeys.setDistributes(false);

    TransformMeta activeStatusTransform =
        addStsSelectOutputFields(
            ctx,
            pipelineMeta,
            addConstantStatusValue(
                ctx, pipelineMeta, sortedSourceKeys, resolveActiveStatusValue(ctx.variables)),
            resolveStatusFieldName(ctx.variables),
            "sts_active_output_fields");

    TransformMeta stsTargetInput = addStsTargetTableInput(ctx, pipelineMeta, stsTableName);
    TransformMeta deletedStatusTransform = null;
    if (stsTargetInput != null) {
      TransformMeta sortedStsKeys =
          addSortRows(ctx, pipelineMeta, stsTargetInput, "sts_sort_target_" + ctx.hashKeyFieldName);
      TransformMeta mergeTransform =
          addStsDeletionMerge(ctx, pipelineMeta, sortedSourceKeys, sortedStsKeys);
      deletedStatusTransform =
          addStsSelectOutputFields(
              ctx,
              pipelineMeta,
              addConstantStatusValue(
                  ctx,
                  pipelineMeta,
                  addDropFlagField(pipelineMeta, addFilterDeletedRows(pipelineMeta, mergeTransform)),
                  resolveDeletedStatusValue(ctx.variables)),
              resolveStatusFieldName(ctx.variables),
              "sts_deleted_output_fields");
    }

    TransformMeta unionTransform = activeStatusTransform;
    if (deletedStatusTransform != null) {
      unionTransform = addAppendRows(pipelineMeta, activeStatusTransform, deletedStatusTransform);
    }

    TransformMeta withLoadDate =
        addConstantForLoadDate(ctx, pipelineMeta, loadDate, unionTransform);
    IRowMeta stsLayout = getStatusTargetTableLayout(ctx.metadataProvider, ctx.variables, ctx.model);
    addStsTableOutput(ctx, pipelineMeta, stsTableName, stsLayout, withLoadDate);

    return pipelineMeta;
  }

  private TransformMeta addSourceTableInput(
      SatelliteUpdateContext ctx,
      DataVaultSource dataVaultSource,
      PipelineMeta pipelineMeta,
      Point startLine)
      throws HopException {
    DvSourcePipelineBuilder builder =
        DvSourcePipelineBuilderFactory.forSatellite(
            ctx.variables,
            ctx.metadataProvider,
            ctx.model,
            pipelineMeta,
            dataVaultSource,
            ctx.dvSource,
            this,
            new Point(startLine.x, startLine.y));
    if (ctx.linkSatellite) {
      if (builder instanceof DvDatabaseSatelliteSourcePipelineBuilder dbBuilder) {
        dbBuilder.setLinkedLink(ctx.linkedLink);
        dbBuilder.setDvLinkHubSource(ctx.linkHubSource);
        dbBuilder.setDvLinkSatelliteSource(ctx.linkSatelliteSource);
      } else if (builder
          instanceof org.apache.hop.datavault.metadata.file.DvCsvSatelliteSourcePipelineBuilder csvBuilder) {
        csvBuilder.setLinkedLink(ctx.linkedLink);
        csvBuilder.setDvLinkHubSource(ctx.linkHubSource);
        csvBuilder.setDvLinkSatelliteSource(ctx.linkSatelliteSource);
      }
    }
    builder.build();
    return builder.getResultTransform();
  }

  private TransformMeta addLinkHashKeyChain(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point startLine) {
    TransformMeta current = predecessor;
    List<String> hubHashNames = new ArrayList<>();
    int index = 0;
    for (SatelliteUpdateContext.HubHashCalcStep step : ctx.hubHashCalcSteps) {
      current =
          addDvHashKeyForFields(
              ctx,
              pipelineMeta,
              current,
              step.inputFieldNames(),
              step.hashKeyFieldName(),
              index++,
              startLine);
      hubHashNames.add(step.hashKeyFieldName());
    }
    return addDvHashKeyForFields(
        ctx,
        pipelineMeta,
        current,
        hubHashNames,
        ctx.hashKeyFieldName,
        index,
        startLine);
  }

  private TransformMeta addDvHashKey(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point startLine) {
    DvHashKeyMeta hashKeyMeta =
        DvHashKeyMetaFactory.create(ctx.config, ctx.pkSourceFieldNames, ctx.hashKeyFieldName);
    TransformMeta tm = new TransformMeta("DvHashKey", "calc_" + ctx.hashKeyFieldName, hashKeyMeta);
    tm.setLocation(startLine.x + SPACING_WIDTH, startLine.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addDvHashKeyForFields(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<String> inputFieldNames,
      String resultFieldName,
      int index,
      Point startLine) {
    DvHashKeyMeta hashKeyMeta =
        DvHashKeyMetaFactory.create(ctx.config, inputFieldNames, resultFieldName);
    TransformMeta tm = new TransformMeta("DvHashKey", "calc_" + resultFieldName, hashKeyMeta);
    tm.setLocation(startLine.x + (index + 1) * SPACING_WIDTH, startLine.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addStsSourceKeySelect(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    SelectField hashField = new SelectField();
    hashField.setName(ctx.hashKeyFieldName);
    selectFields.add(hashField);

    if (ctx.hasDrivingKey()) {
      SelectField drivingKeyField = new SelectField();
      drivingKeyField.setName(ctx.drivingKeySourceFieldName);
      drivingKeyField.setRename(ctx.drivingKeyFieldName);
      selectFields.add(drivingKeyField);
    }

    SelectField recordSourceSelect = new SelectField();
    recordSourceSelect.setName(ctx.recordSourceField);
    selectFields.add(recordSourceSelect);

    TransformMeta tm = new TransformMeta("SelectValues", "sts source keys", selectMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addStsTargetTableInput(
      SatelliteUpdateContext ctx, PipelineMeta pipelineMeta, String stsTableName) {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    String statusField = resolveStatusFieldName(ctx.variables);
    String activeValue = resolveActiveStatusValue(ctx.variables);
    String loadDateField = determineTargetLoadDateField(ctx);
    String quotedHash = ctx.targetDatabaseMeta.quoteField(ctx.hashKeyFieldName);
    String quotedStatus = ctx.targetDatabaseMeta.quoteField(statusField);
    String quotedLoadDate = ctx.targetDatabaseMeta.quoteField(loadDateField);
    String quotedRecordSource = ctx.targetDatabaseMeta.quoteField(ctx.recordSourceField);

    List<String> selectFields = new ArrayList<>();
    selectFields.add(quotedStatus);
    if (ctx.hasDrivingKey()) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    selectFields.add(quotedRecordSource);
    selectFields.add(quotedLoadDate);
    selectFields.add(quotedHash);

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, stsTableName));
    sql.append(" ORDER BY ");
    sql.append(quotedHash);
    if (ctx.hasDrivingKey()) {
      sql.append(", ");
      sql.append(ctx.targetDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    sql.append(", ");
    sql.append(quotedLoadDate);

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);
    targetTableInputMeta.setSql(sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", "sts_target_" + stsTableName, targetTableInputMeta);
    tm.setLocation(LOCATION_STS_LINE_3);
    pipelineMeta.addTransform(tm);

    GroupByMeta groupByMeta = new GroupByMeta();
    List<GroupingField> groupingFields = new ArrayList<>();
    groupingFields.add(new GroupingField(ctx.hashKeyFieldName));
    if (ctx.hasDrivingKey()) {
      groupingFields.add(new GroupingField(ctx.drivingKeyFieldName));
    }
    groupByMeta.setGroupingFields(groupingFields);

    List<Aggregation> aggregations = new ArrayList<>();
    Aggregation statusAgg = new Aggregation();
    statusAgg.setSubject(statusField);
    statusAgg.setField(statusField);
    statusAgg.setTypeLabel("LAST_INCL_NULL");
    aggregations.add(statusAgg);

    Aggregation rsAgg = new Aggregation();
    rsAgg.setSubject(ctx.recordSourceField);
    rsAgg.setField(ctx.recordSourceField);
    rsAgg.setTypeLabel("LAST_INCL_NULL");
    aggregations.add(rsAgg);
    groupByMeta.setAggregations(aggregations);

    TransformMeta groupTm =
        new TransformMeta("GroupBy", "sts_last_status_" + ctx.hashKeyFieldName, groupByMeta);
    groupTm.setLocation(LOCATION_STS_LINE_3.x + SPACING_WIDTH, LOCATION_STS_LINE_3.y);
    pipelineMeta.addTransform(groupTm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(tm, groupTm));

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition condition =
          new Condition(
              statusField,
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), activeValue));
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new RuntimeException("Error creating STS active filter", e);
    }

    TransformMeta filterTm = new TransformMeta("FilterRows", "sts_active_keys", filterRowsMeta);
    filterTm.setLocation(groupTm.getLocation().x + SPACING_WIDTH, groupTm.getLocation().y);
    pipelineMeta.addTransform(filterTm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(groupTm, filterTm));

    SelectValuesMeta keySelectMeta = new SelectValuesMeta();
    List<SelectField> keyFields = keySelectMeta.getSelectOption().getSelectFields();
    SelectField hashSelect = new SelectField();
    hashSelect.setName(ctx.hashKeyFieldName);
    keyFields.add(hashSelect);
    if (ctx.hasDrivingKey()) {
      SelectField drivingKeySelect = new SelectField();
      drivingKeySelect.setName(ctx.drivingKeyFieldName);
      keyFields.add(drivingKeySelect);
    }
    SelectField recordSourceSelect = new SelectField();
    recordSourceSelect.setName(ctx.recordSourceField);
    keyFields.add(recordSourceSelect);

    TransformMeta keySelectTm =
        new TransformMeta("SelectValues", "sts_active_key_set", keySelectMeta);
    keySelectTm.setLocation(filterTm.getLocation().x + SPACING_WIDTH, filterTm.getLocation().y);
    pipelineMeta.addTransform(keySelectTm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(filterTm, keySelectTm));

    return keySelectTm;
  }

  private TransformMeta addStsDeletionMerge(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform) {
    TransformMeta compareDummy =
        addDummyTransform(
            pipelineMeta,
            compareTransform,
            "STS merge compare",
            compareTransform.getLocation().x + SPACING_WIDTH,
            compareTransform.getLocation().y);
    TransformMeta referenceDummy =
        addDummyTransform(
            pipelineMeta,
            referenceTransform,
            "STS merge reference",
            referenceTransform.getLocation().x + SPACING_WIDTH,
            referenceTransform.getLocation().y);

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceDummy.getName());
    mergeRowsMeta.setCompareTransform(compareDummy.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    keyFields.add(ctx.hashKeyFieldName);
    if (ctx.hasDrivingKey()) {
      keyFields.add(ctx.drivingKeyFieldName);
    }
    mergeRowsMeta.setKeyFields(keyFields);

    TransformMeta tm = new TransformMeta("MergeRows", "sts_deletion_merge", mergeRowsMeta);
    tm.setLocation(LOCATION_STS_LINE_3.x + 4 * SPACING_WIDTH, LOCATION_STS_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceDummy, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareDummy, tm));
    return tm;
  }

  private TransformMeta addDropFlagField(PipelineMeta pipelineMeta, TransformMeta predecessor) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    DeleteField flagDelete = new DeleteField();
    flagDelete.setName("flag");
    selectMeta.getSelectOption().getDeleteName().add(flagDelete);

    TransformMeta tm = new TransformMeta("SelectValues", "sts_drop_flag", selectMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addStsSelectOutputFields(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String statusFieldName,
      String transformName) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    SelectField hashSelect = new SelectField();
    hashSelect.setName(ctx.hashKeyFieldName);
    selectFields.add(hashSelect);
    if (ctx.hasDrivingKey()) {
      SelectField drivingKeySelect = new SelectField();
      drivingKeySelect.setName(ctx.drivingKeyFieldName);
      selectFields.add(drivingKeySelect);
    }
    SelectField recordSourceSelect = new SelectField();
    recordSourceSelect.setName(ctx.recordSourceField);
    selectFields.add(recordSourceSelect);
    if (!Utils.isEmpty(statusFieldName)) {
      SelectField statusSelect = new SelectField();
      statusSelect.setName(statusFieldName);
      selectFields.add(statusSelect);
    }

    TransformMeta tm = new TransformMeta("SelectValues", transformName, selectMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addFilterDeletedRows(PipelineMeta pipelineMeta, TransformMeta mergeTransform)
      throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition condition =
          new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), "deleted"));
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating STS deleted filter", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "sts_deleted_keys", filterRowsMeta);
    tm.setLocation(mergeTransform.getLocation().x + SPACING_WIDTH, mergeTransform.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, tm));
    return tm;
  }

  private TransformMeta addConstantStatusValue(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String statusValue)
      throws HopException {
    String statusField = resolveStatusFieldName(ctx.variables);
    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField cf = new ConstantField(statusField, "String", statusValue);
    constantMeta.getFields().add(cf);

    TransformMeta tm =
        new TransformMeta("Constant", "set_" + statusField + "_" + statusValue, constantMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addAppendRows(
      PipelineMeta pipelineMeta, TransformMeta headTransform, TransformMeta tailTransform) {
    AppendMeta appendMeta = new AppendMeta();
    appendMeta.headTransformName = headTransform.getName();
    appendMeta.tailTransformName = tailTransform.getName();

    TransformMeta tm = new TransformMeta("Append", "sts_union", appendMeta);
    tm.setLocation(
        Math.max(headTransform.getLocation().x, tailTransform.getLocation().x) + SPACING_WIDTH,
        headTransform.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(headTransform, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(tailTransform, tm));
    return tm;
  }

  private TransformMeta addStsTableOutput(
      SatelliteUpdateContext ctx,
      PipelineMeta pipelineMeta,
      String stsTableName,
      IRowMeta targetLayout,
      TransformMeta predecessor)
      throws HopException {
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return null;
    }

    TableOutputMeta tableOutputMeta = new TableOutputMeta();
    tableOutputMeta.setConnection(ctx.targetDbName);
    tableOutputMeta.setTableName(stsTableName);
    tableOutputMeta.setSpecifyFields(true);
    tableOutputMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (!"flag".equalsIgnoreCase(name)) {
          tableOutputMeta.getFields().add(new TableOutputField(name, name));
        }
      }
    }

    TransformMeta tm =
        new TransformMeta("TableOutput", "write_to_" + stsTableName, tableOutputMeta);
    tm.setCopiesString(ctx.config.resolveTargetTableParallelCopies(ctx.variables));
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
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
    final List<String> pkSourceFieldNames; // hub bks from sat source, for hash calc (hub satellites)
    final List<String> satAttrFieldNames; // satellite attribute names in the target table
    final List<String> satAttrSourceFieldNames; // attribute column names in the source stream
    final String hashKeyFieldName; // hub hash or link hash column name

    final boolean linkSatellite;
    final DvLink linkedLink;
    final DvLink.DvLinkHubSource linkHubSource;
    final DvLink.DvLinkSatelliteSource linkSatelliteSource;
    final List<HubHashCalcStep> hubHashCalcSteps;

    // Record source indicator support
    final String sourceIndicator;
    final String sourceIndicatorField;
    final String recordSourceField;
    final String drivingKeyFieldName;
    final String drivingKeySourceFieldName;

    SatelliteUpdateContext(
        DvSatellite satellite,
        DataVaultModel model,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultConfiguration config,
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
        List<String> satAttrSourceFieldNames,
        String hashKeyFieldName,
        boolean linkSatellite,
        DvLink linkedLink,
        DvLink.DvLinkHubSource linkHubSource,
        DvLink.DvLinkSatelliteSource linkSatelliteSource,
        List<HubHashCalcStep> hubHashCalcSteps,
        String sourceIndicator,
        String sourceIndicatorField,
        String recordSourceField,
        String drivingKeyFieldName,
        String drivingKeySourceFieldName)
        throws HopException {
      this.satellite = satellite;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
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
      this.satAttrSourceFieldNames =
          satAttrSourceFieldNames != null ? satAttrSourceFieldNames : new ArrayList<>();
      this.hashKeyFieldName = hashKeyFieldName != null ? hashKeyFieldName : "hashkey";
      this.linkSatellite = linkSatellite;
      this.linkedLink = linkedLink;
      this.linkHubSource = linkHubSource;
      this.linkSatelliteSource = linkSatelliteSource;
      this.hubHashCalcSteps = hubHashCalcSteps != null ? hubHashCalcSteps : new ArrayList<>();
      this.sourceIndicator = sourceIndicator;
      this.sourceIndicatorField = sourceIndicatorField;
      this.recordSourceField = recordSourceField != null ? recordSourceField : "RECORD_SOURCE";
      this.drivingKeyFieldName = drivingKeyFieldName;
      this.drivingKeySourceFieldName = drivingKeySourceFieldName;

      this.dvSource = dataVaultSource.getDvSource(metadataProvider);
    }

    boolean hasDrivingKey() {
      return !Utils.isEmpty(drivingKeyFieldName) && !Utils.isEmpty(drivingKeySourceFieldName);
    }

    int hashChainLength() {
      return linkSatellite ? hubHashCalcSteps.size() + 1 : 1;
    }

    record HubHashCalcStep(List<String> inputFieldNames, String hashKeyFieldName) {}

    static SatelliteUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvSatellite sat)
        throws HopException {
      if (metadataProvider == null || model == null || sat == null) {
        return null;
      }

      if (Utils.isEmpty(sat.recordSource)) {
        throw new HopException("Please specify a record source for satellite " + sat.getName());
      }
      DataVaultSource recordSource = sat.resolveRecordSource(variables, metadataProvider, model);

      DataVaultConfiguration config = model.getConfigurationOrDefault();

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
      String pipelineName =
          config.buildSatellitePipelineName(variables, targetTableName, recordSource.getName());

      String sourceTransformName = sat.getName();
      String targetTransformName = "target_" + targetTableName;

      boolean linkSatellite = !Utils.isEmpty(sat.getLinkName());
      boolean hubSatellite = !Utils.isEmpty(sat.getHubName());
      if (!linkSatellite && !hubSatellite) {
        throw new HopException(
            "Please link satellite " + sat.getName() + " to a hub or a link");
      }
      if (linkSatellite && hubSatellite) {
        throw new HopException(
            "Satellite " + sat.getName() + " cannot be linked to both a hub and a link");
      }

      DvLink linkedLink = null;
      DvLink.DvLinkHubSource linkHubSource = null;
      DvLink.DvLinkSatelliteSource linkSatelliteSource = null;
      List<HubHashCalcStep> hubHashCalcSteps = new ArrayList<>();

      String hashKeyFieldName = "hashkey";
      List<String> hubBkNames = new ArrayList<>();
      DvHub linkedHub = null;

      if (hubSatellite) {
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
            hashKeyFieldName = linkedHub.getBusinessKeys().get(0).getName() + "_hk";
          }
        } else {
          for (BusinessKey bk : linkedHub.getBusinessKeys()) {
            hubBkNames.add(bk.getName());
          }
        }
      } else {
        linkedLink = model.findLink(sat.getLinkName());
        if (linkedLink == null) {
          throw new HopException("Please link satellite " + sat.getName() + " to a link");
        }
        hashKeyFieldName = variables.resolve(linkedLink.getLinkHashKeyFieldName());
        if (Utils.isEmpty(hashKeyFieldName)) {
          hashKeyFieldName = linkedLink.getName() + "_LK";
        }
        linkHubSource = findLinkHubSource(linkedLink, recordSource);
        linkSatelliteSource = findLinkSatelliteSource(linkedLink, recordSource);

        for (String hubName : linkedLink.getHubNames()) {
          DvHub hub = model.findHub(hubName);
          if (hub == null) {
            throw new HopException(
                "Hub '"
                    + hubName
                    + "' referenced by link "
                    + linkedLink.getName()
                    + " was not found in the model");
          }
          DvLink.HubSourceKeyField hubSourceKeyField =
              findHubSourceKeyField(linkHubSource, hubName);
          List<String> inputFieldNames = new ArrayList<>();
          if (hubSourceKeyField.getSourceBusinessKeyFields() != null) {
            for (BusinessKeySource bks : hubSourceKeyField.getSourceBusinessKeyFields()) {
              if (bks != null && !Utils.isEmpty(bks.getSourceFieldName())) {
                inputFieldNames.add(variables.resolve(bks.getSourceFieldName()));
              }
            }
          }
          if (inputFieldNames.isEmpty()) {
            throw new HopException(
                "No source business key fields mapped for hub "
                    + hubName
                    + " on link "
                    + linkedLink.getName()
                    + " and record source "
                    + recordSource.getName());
          }
          String hubHashFieldName = variables.resolve(hub.getHashKeyFieldName());
          if (Utils.isEmpty(hubHashFieldName)) {
            if (!Utils.isEmpty(hub.getBusinessKeys())) {
              hubHashFieldName = hub.getBusinessKeys().get(0).getName() + "_hk";
            } else {
              hubHashFieldName = hub.getName() + "_hk";
            }
          }
          hubHashCalcSteps.add(new HubHashCalcStep(inputFieldNames, hubHashFieldName));
        }
      }

      DatabaseMeta sourceDatabaseMeta = null;
      String sourceDbName = null;
      String sourceSchema = null;
      String sourceTable = null;
      List<String> pkSourceFieldNames = new ArrayList<>();
      List<String> satAttrFieldNames = new ArrayList<>();
      List<String> satAttrSourceFieldNames = new ArrayList<>();
      String sourceIndicator = null;
      String sourceIndicatorField = null;

      IDvSource dvSource = recordSource.getDvSourceOrDefault();
      if (dvSource instanceof DvDatabaseSource dbSource) {
        sourceDbName = dbSource.getDatabaseName();
        sourceSchema = dbSource.getSchemaName();
        sourceTable = dbSource.getTableName();
        if (!Utils.isEmpty(sourceDbName)) {
          sourceDatabaseMeta =
              metadataProvider.getSerializer(DatabaseMeta.class).load(sourceDbName);
          if (sourceDatabaseMeta == null) {
            throw new HopException("Database connection not found in metadata: " + sourceDbName);
          }
        }
      }

      sourceIndicator = recordSource.getSourceIndicator();
      sourceIndicatorField = recordSource.getSourceIndicatorField();

      List<SourceField> sourceFields = recordSource.getFields(metadataProvider);

      if (hubSatellite) {
        for (String hubBk : hubBkNames) {
          pkSourceFieldNames.add(variables.resolve(hubBk));
        }

        loadHubSatelliteAttributeFields(
            linkedHub,
            sat,
            variables,
            sourceFields,
            satAttrFieldNames,
            satAttrSourceFieldNames);
      } else {
        loadLinkSatelliteAttributeFields(
            sat,
            variables,
            linkSatelliteSource,
            sourceFields,
            satAttrFieldNames,
            satAttrSourceFieldNames);
      }

      String drivingKeyFieldName = null;
      String drivingKeySourceFieldName = null;
      if (sat.hasDrivingKey()) {
        drivingKeyFieldName = variables.resolve(sat.getDrivingKey());
        drivingKeySourceFieldName = variables.resolve(sat.getDrivingKeySourceField());
      }

      return new SatelliteUpdateContext(
          sat,
          model,
          metadataProvider,
          variables,
          config,
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
          satAttrSourceFieldNames,
          hashKeyFieldName,
          linkSatellite,
          linkedLink,
          linkHubSource,
          linkSatelliteSource,
          hubHashCalcSteps,
          sourceIndicator,
          sourceIndicatorField,
          recordSourceField,
          drivingKeyFieldName,
          drivingKeySourceFieldName);
    }
  }

  private static DvLink.DvLinkHubSource findLinkHubSource(DvLink link, DataVaultSource source)
      throws HopException {
    if (link.getLinkHubSources() == null) {
      throw new HopException("Link " + link.getName() + " has no hub record sources defined");
    }
    for (DvLink.DvLinkHubSource lhs : link.getLinkHubSources()) {
      if (lhs != null
          && !Utils.isEmpty(lhs.getSourceName())
          && source.getName().equals(lhs.getSourceName())) {
        return lhs;
      }
    }
    throw new HopException(
        "No hub source mapping on link "
            + link.getName()
            + " for record source "
            + source.getName());
  }

  private static DvLink.DvLinkSatelliteSource findLinkSatelliteSource(
      DvLink link, DataVaultSource source) throws HopException {
    if (link.getLinkSatelliteSources() == null) {
      throw new HopException(
          "Link " + link.getName() + " has no link satellite record sources defined");
    }
    for (DvLink.DvLinkSatelliteSource lss : link.getLinkSatelliteSources()) {
      if (lss != null
          && !Utils.isEmpty(lss.getSourceName())
          && source.getName().equals(lss.getSourceName())) {
        return lss;
      }
    }
    throw new HopException(
        "No satellite source mapping on link "
            + link.getName()
            + " for record source "
            + source.getName());
  }

  private static DvLink.HubSourceKeyField findHubSourceKeyField(
      DvLink.DvLinkHubSource linkHubSource, String hubName) throws HopException {
    if (linkHubSource.getHubSourceKeyFields() != null) {
      for (DvLink.HubSourceKeyField hskf : linkHubSource.getHubSourceKeyFields()) {
        if (hskf != null && hubName.equals(hskf.getHubName())) {
          return hskf;
        }
      }
    }
    throw new HopException(
        "No hub source key field mapping for hub "
            + hubName
            + " in link hub source "
            + linkHubSource.getSourceName());
  }

  public static Set<String> excludedHubSatelliteSourceFieldNames(
      DvHub hub, DvSatellite sat, IVariables variables) {
    return excludedHubSatelliteSourceFieldNames(
        hub,
        variables,
        sat.hasDrivingKey() ? sat.getDrivingKey() : null,
        sat.hasDrivingKey() ? sat.getDrivingKeySourceField() : null);
  }

  public static Set<String> excludedHubSatelliteSourceFieldNames(
      DvHub hub, IVariables variables, String drivingKey, String drivingKeySourceField) {
    Set<String> excluded = new HashSet<>();
    if (hub != null && hub.getBusinessKeys() != null) {
      for (BusinessKey bk : hub.getBusinessKeys()) {
        if (bk == null) {
          continue;
        }
        String sourceField = bk.getSourceFieldName();
        if (Utils.isEmpty(sourceField)) {
          sourceField = bk.getName();
        }
        if (!Utils.isEmpty(sourceField)) {
          excluded.add(variables.resolve(sourceField));
        }
      }
    }
    if (!Utils.isEmpty(drivingKey) && !Utils.isEmpty(drivingKeySourceField)) {
      excluded.add(variables.resolve(drivingKeySourceField));
      excluded.add(variables.resolve(drivingKey));
    }
    return excluded;
  }

  private static List<SourceField> selectHubSatelliteAutoAttributeSourceFields(
      DvHub hub,
      DvSatellite sat,
      IVariables variables,
      List<SourceField> sourceFields) {
    Set<String> excluded = excludedHubSatelliteSourceFieldNames(hub, sat, variables);
    List<SourceField> selected = new ArrayList<>();
    for (SourceField sf : sourceFields) {
      String name = variables.resolve(sf.getName());
      if (!excluded.contains(name)) {
        selected.add(sf);
      }
    }
    return selected;
  }

  private static void loadHubSatelliteAttributeFields(
      DvHub hub,
      DvSatellite sat,
      IVariables variables,
      List<SourceField> sourceFields,
      List<String> satAttrFieldNames,
      List<String> satAttrSourceFieldNames) {
    if (Utils.isEmpty(sat.getAttributes())) {
      for (SourceField sf :
          selectHubSatelliteAutoAttributeSourceFields(hub, sat, variables, sourceFields)) {
        String name = variables.resolve(sf.getName());
        satAttrFieldNames.add(name);
        satAttrSourceFieldNames.add(name);
      }
    } else {
      for (SatelliteAttribute sa : sat.getAttributes()) {
        satAttrFieldNames.add(sa.getName());
        satAttrSourceFieldNames.add(sa.getName());
      }
    }
  }

  private static void loadLinkSatelliteAttributeFields(
      DvSatellite sat,
      IVariables variables,
      DvLink.DvLinkSatelliteSource linkSatelliteSource,
      List<SourceField> sourceFields,
      List<String> satAttrFieldNames,
      List<String> satAttrSourceFieldNames)
      throws HopException {
    DvLink.SatelliteSourceKeyField satelliteSourceKeyField = null;
    if (linkSatelliteSource.getSatelliteSourceKeyFields() != null) {
      for (DvLink.SatelliteSourceKeyField skf :
          linkSatelliteSource.getSatelliteSourceKeyFields()) {
        if (skf != null && sat.getName().equals(skf.getSatelliteName())) {
          satelliteSourceKeyField = skf;
          break;
        }
      }
    }
    if (satelliteSourceKeyField != null
        && satelliteSourceKeyField.getAttributeSources() != null
        && !satelliteSourceKeyField.getAttributeSources().isEmpty()) {
      for (AttributeSource as : satelliteSourceKeyField.getAttributeSources()) {
        if (as == null || Utils.isEmpty(as.getAttributeField())) {
          continue;
        }
        satAttrFieldNames.add(variables.resolve(as.getAttributeField()));
        String sourceName =
            Utils.isEmpty(as.getSourceFieldName())
                ? as.getAttributeField()
                : as.getSourceFieldName();
        satAttrSourceFieldNames.add(variables.resolve(sourceName));
      }
      return;
    }

    if (Utils.isEmpty(sat.getAttributes())) {
      throw new HopException(
          "Please specify link satellite attribute mappings for satellite " + sat.getName());
    }
    for (SatelliteAttribute sa : sat.getAttributes()) {
      satAttrFieldNames.add(sa.getName());
      satAttrSourceFieldNames.add(sa.getName());
    }
  }
}
