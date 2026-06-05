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
import java.util.List;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.transforms.sql.ExecSqlMeta;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.checksum.CheckSumMeta;
import org.apache.hop.pipeline.transforms.checksum.CheckSumMeta.CheckSumType;
import org.apache.hop.pipeline.transforms.checksum.CheckSumMeta.ResultType;
import org.apache.hop.pipeline.transforms.checksum.Field;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;
import org.apache.hop.ui.hopgui.HopGui;

/**
 * Data Vault 2.0 Hub metadata definition.
 *
 * <p>A Hub represents a core business concept (e.g. Customer, Product, Order).
 * It contains the business key(s) and the surrogate hash key derived from them.
 */
@GuiPlugin
public class DvHub extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  private static final Class<?> PKG = DvHub.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_HUB_DIALOG";

  /**
   * The business keys that uniquely identify the hub instance.
   * Composite keys are supported by having multiple entries (order matters for hashing).
   */
  // List of business keys. The @GuiWidgetElement annotation is omitted because
  // GuiElementType does not (currently) include a LIST type. Nested POJO lists are
  // supported via @HopMetadataProperty and will be handled by the metadata editor / serialization.
  @HopMetadataProperty
  private List<BusinessKey> businessKeys = new ArrayList<>();

  public DvHub() {
    super();
    this.tableType = DvTableType.HUB;
  }

  public DvHub(String name) {
    super(name);
    this.tableType = DvTableType.HUB;
  }

  public List<BusinessKey> getBusinessKeys() {
    return businessKeys;
  }

  public void setBusinessKeys(List<BusinessKey> businessKeys) {
    if (!java.util.Objects.equals(this.businessKeys, businessKeys)) {
      setChanged();
    }
    this.businessKeys = businessKeys;
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
  public PipelineMeta generateUpdatePipeline(HopGui hopGui, DataVaultModel model) throws HopException {
    if (hopGui == null || model == null) {
      return null;
    }
    String tableName = !Utils.isEmpty(getTableName()) ? getTableName() : getName();
    String pipelineName = "hub-" + tableName;
    String transformName = getRecordSource();
    if (Utils.isEmpty(transformName)) {
      transformName = getName();
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(pipelineName);

    // Load DataVaultConfiguration referenced by the model to determine hash method and suffix
    DataVaultConfiguration config = null;
    String configName = model.getConfigurationName();
    if (!Utils.isEmpty(configName)) {
      config =
          hopGui.getMetadataProvider().getSerializer(DataVaultConfiguration.class).load(configName);
    }
    HashAlgorithm hashAlgorithm = (config != null) ? config.getHashAlgorithm() : HashAlgorithm.MD5;
    CheckSumType checkSumType = CheckSumType.MD5;
    if (hashAlgorithm != null) {
      switch (hashAlgorithm) {
        case MD5:
          checkSumType = CheckSumType.MD5;
          break;
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
          checkSumType = CheckSumType.MD5;
      }
    }
    String hubHashKeySuffix = (config != null) ? config.getHubHashKeySuffix() : "_HK";

    // Load target database from config for the second Table Input
    DatabaseMeta targetDatabaseMeta = null;
    String targetDbName = (config != null) ? config.getTargetDatabase() : null;
    if (!Utils.isEmpty(targetDbName)) {
      targetDatabaseMeta =
          hopGui.getMetadataProvider().getSerializer(DatabaseMeta.class).load(targetDbName);
      if (targetDatabaseMeta == null) {
        throw new HopException(
            "Target database connection not found in metadata: " + targetDbName);
      }
    }

    String targetTableName =
        !Utils.isEmpty(getTableName()) ? getTableName() : getName();

    // Execute DDL if needed using Exec SQL Script transform at (50, 500)
    IRowMeta targetFields = getTargetTableLayout(hopGui, model);
    String ddl = "";
    if (targetDatabaseMeta != null && targetFields != null) {
      ILoggingObject loggingObject =
          new SimpleLoggingObject("DvHub.generateUpdatePipeline", LoggingObjectType.GENERAL, null);
      try (Database db = new Database(loggingObject, hopGui.getVariables(), targetDatabaseMeta)) {
        db.connect();
        ddl = db.getDDL(targetTableName, targetFields);
      } catch (Exception e) {
        throw new HopException("Error getting DDL for target table: " + targetTableName, e);
      }
    }
    if (!Utils.isEmpty(ddl)) {
      ExecSqlMeta execSqlMeta = new ExecSqlMeta();
      execSqlMeta.setConnection(targetDbName);
      execSqlMeta.setSql(ddl);
      TransformMeta execSqlTransformMeta =
          new TransformMeta("ExecSql", "Create/update target table " + targetTableName, execSqlMeta);
      execSqlTransformMeta.setLocation(50, 500);
      pipelineMeta.addTransform(execSqlTransformMeta);
    }

    TableInputMeta tableInputMeta = new TableInputMeta();

    // Configure Table Input using recordSource -> DataVaultSource -> DvDatabaseSource (via metadata provider)
    String recordSourceName = getRecordSource();
    List<String> pkSourceFieldNames = new ArrayList<>();
    if (!Utils.isEmpty(recordSourceName)) {
      DataVaultSource dataVaultSource =
          hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(recordSourceName);
      if (dataVaultSource != null
          && dataVaultSource.getSourceType() == DataVaultSourceType.DATABASE
          && !Utils.isEmpty(dataVaultSource.getSourceTableName())) {
        DvDatabaseSource dbSource =
            hopGui
                .getMetadataProvider()
                .getSerializer(DvDatabaseSource.class)
                .load(dataVaultSource.getSourceTableName());
        if (dbSource != null) {
          tableInputMeta.setConnection(dbSource.getDatabaseName());
          String schema = dbSource.getSchemaName();
          String dbTable = dbSource.getTableName();

          // Load and verify DatabaseMeta
          DatabaseMeta databaseMeta =
              hopGui
                  .getMetadataProvider()
                  .getSerializer(DatabaseMeta.class)
                  .load(dbSource.getDatabaseName());
          if (databaseMeta == null) {
            throw new HopException(
                "Database connection not found in metadata: " + dbSource.getDatabaseName());
          }

          // Get source fields (for columns and PKs for ORDER BY)
          List<SourceField> sourceFields =
              dataVaultSource.getFields(hopGui.getMetadataProvider());

          // For Hubs, ignore all fields except the source fields indicated as primary key (business keys).
          List<String> pkQuotedFields = new ArrayList<>();
          for (SourceField sf : sourceFields) {
            if (sf.isPrimaryKey()) {
              String fieldName = hopGui.getVariables().resolve(sf.getName());
              pkSourceFieldNames.add(fieldName);
              pkQuotedFields.add(databaseMeta.quoteField(fieldName));
            }
          }

          // Build SELECT list using only PK fields (or * fallback if no PKs)
          StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
          if (pkQuotedFields.isEmpty()) {
            sql.append("*");
          } else {
            sql.append(String.join(", ", pkQuotedFields));
          }

          // FROM with properly quoted schema.table combination
          sql.append(" FROM ");
          sql.append(
              databaseMeta.getQuotedSchemaTableCombination(
                  hopGui.getVariables(), schema, dbTable));

          // ORDER BY using the PK fields (quoted)
          if (!pkQuotedFields.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", pkQuotedFields));
          }

          tableInputMeta.setSql(sql.toString());
        }
      }
    }

    TransformMeta transformMeta =
        new TransformMeta("TableInput", transformName, tableInputMeta);
    transformMeta.setLocation(100, 100);
    pipelineMeta.addTransform(transformMeta);

    String targetTransformName = null;
    TransformMeta targetTransformMeta = null;
    // Add another Table Input transform below the first at (100, 250)
    // using the target database from DataVaultConfiguration.targetDatabase
    if (targetDatabaseMeta != null) {
      TableInputMeta targetTableInputMeta = new TableInputMeta();
      targetTableInputMeta.setConnection(targetDbName);

      // business key name from the hub definition
      String businessKeyName = "id";
      if (!Utils.isEmpty(businessKeys)) {
        businessKeyName = businessKeys.get(0).getName();
      }

      // Build SQL using target dbMeta for proper quoting of schema/table and fields
      StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
      String quotedBK =
          targetDatabaseMeta.quoteField(
              hopGui.getVariables().resolve(businessKeyName));
      sql.append(quotedBK);
      sql.append(" FROM ");
      sql.append(
          targetDatabaseMeta.getQuotedSchemaTableCombination(
              hopGui.getVariables(), null, targetTableName));
      sql.append(" ORDER BY ");
      sql.append(quotedBK);

      targetTableInputMeta.setSql(sql.toString());

      targetTransformName = "target_" + targetTableName;
      targetTransformMeta =
          new TransformMeta("TableInput", targetTransformName, targetTableInputMeta);
      targetTransformMeta.setLocation(100, 250);
      pipelineMeta.addTransform(targetTransformMeta);
    }

    // Add "Merge Rows (Diff)" transform in between the source TableInput and the CheckSum
    TransformMeta mergeTransformMeta = null;
    if (targetTransformMeta != null) {
      MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
      mergeRowsMeta.setReferenceTransform(targetTransformName);  // target as reference
      mergeRowsMeta.setCompareTransform(transformName);  // source as compare
      mergeRowsMeta.setFlagField("flag");

      // add the business key fields as the keys to compare
      List<String> keyFields = new ArrayList<>();
      for (BusinessKey bk : businessKeys) {
        keyFields.add(bk.getName());
      }
      mergeRowsMeta.setKeyFields(keyFields);

      // Add an extra passThroughField for the business key field (referenceField=false)
      List<PassThroughField> passThroughFields = new ArrayList<>();
      if (!businessKeys.isEmpty()) {
        String bkField = businessKeys.get(0).getName();
        passThroughFields.add(new PassThroughField(bkField, null, false));
      }
      mergeRowsMeta.setPassThroughFields(passThroughFields);

      mergeTransformMeta =
          new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
      mergeTransformMeta.setLocation(150, 175);  // in between source (100,100) and checksum (250,100)
      pipelineMeta.addTransform(mergeTransformMeta);
    }

    // Add CheckSum transform at (250,100) to create the hub hash key
    CheckSumMeta checkSumMeta = new CheckSumMeta();
    checkSumMeta.setCheckSumType(checkSumType);

    // Set input fields to the PK business key source fields (only those for hubs)
    List<Field> checkFields = new ArrayList<>();
    for (String fieldName : pkSourceFieldNames) {
      checkFields.add(new Field(fieldName));
    }
    checkSumMeta.setFields(checkFields);

    // Result field name = business key name (from hub) + hub hash key suffix from config
    String bkName = "hashkey";
    if (!Utils.isEmpty(businessKeys)) {
      bkName = businessKeys.get(0).getName();
    }
    String resultFieldName = bkName + (hubHashKeySuffix != null ? hubHashKeySuffix : "_HK");
    checkSumMeta.setResultFieldName(resultFieldName);

    // Optionally set result type based on config hash key data type (binary recommended for hash keys)
    if (config != null && config.getHashKeyDataType() == HashKeyDataType.BINARY) {
      checkSumMeta.setResultType(ResultType.BINARY);
    } else {
      checkSumMeta.setResultType(ResultType.STRING);
    }

    TransformMeta checkSumTransformMeta =
        new TransformMeta("CheckSum", "calc_" + resultFieldName, checkSumMeta);
    checkSumTransformMeta.setLocation(250, 100);
    pipelineMeta.addTransform(checkSumTransformMeta);

    // Connect the transforms
    if (mergeTransformMeta != null) {
      // source TableInput -> MergeRows
      pipelineMeta.addPipelineHop(new PipelineHopMeta(transformMeta, mergeTransformMeta));
      // target TableInput -> MergeRows
      pipelineMeta.addPipelineHop(new PipelineHopMeta(targetTransformMeta, mergeTransformMeta));
      // MergeRows -> CheckSum
      pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransformMeta, checkSumTransformMeta));
    } else {
      // fallback direct if no target/merge
      pipelineMeta.addPipelineHop(new PipelineHopMeta(transformMeta, checkSumTransformMeta));
    }

    return pipelineMeta;
  }

  @Override
  public IRowMeta getTargetTableLayout(HopGui hopGui, DataVaultModel model) {
    if (hopGui == null || model == null) {
      return null;
    }

    IRowMeta rowMeta = new RowMeta();

    try {
      // Load the DataVaultConfiguration using the HopGui metadata provider
      DataVaultConfiguration config = null;
      String configName = model.getConfigurationName();
      if (!Utils.isEmpty(configName)) {
        config =
            hopGui.getMetadataProvider().getSerializer(DataVaultConfiguration.class).load(configName);
      }
      if (config == null) {
        config = new DataVaultConfiguration(); // defaults
      }

      // 1. First column: calculated hash key name with type from config
      String bkName = "hashkey";
      if (!Utils.isEmpty(businessKeys)) {
        bkName = businessKeys.get(0).getName();
      }
      String hashKeySuffix = config.getHubHashKeySuffix() != null ? config.getHubHashKeySuffix() : "_HK";
      String hashKeyName = bkName + hashKeySuffix;

      IValueMeta hashMeta;
      if (config.getHashKeyDataType() == HashKeyDataType.BINARY) {
        hashMeta = new ValueMetaBinary(hashKeyName);
        // optionally set length based on algo, but not specified in task
      } else {
        hashMeta = new ValueMetaString(hashKeyName);
      }
      rowMeta.addValueMeta(hashMeta);

      // Load source for business key types
      List<SourceField> sourceFields = null;
      String recordSourceName = getRecordSource();
      if (!Utils.isEmpty(recordSourceName)) {
        DataVaultSource dataVaultSource =
            hopGui.getMetadataProvider().getSerializer(DataVaultSource.class).load(recordSourceName);
        if (dataVaultSource != null) {
          sourceFields = dataVaultSource.getFields(hopGui.getMetadataProvider());
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
                // copy length/precision from bk or sf if available
                if (!Utils.isEmpty(bk.getLength())) {
                  try {
                    bkMeta.setLength(Integer.parseInt(bk.getLength()));
                  } catch (Exception ignore) {
                  }
                }
                if (!Utils.isEmpty(bk.getDataType()) && bkMeta.getType() == IValueMeta.TYPE_NONE) {
                  // fallback
                }
              }
              break;
            }
          }
        }
        if (bkMeta == null) {
          // fallback using bk dataType or string
          String dt = bk.getDataType();
          int typeId = IValueMeta.TYPE_STRING;
          if (!Utils.isEmpty(dt)) {
            typeId = ValueMetaFactory.getIdForValueMeta(dt);
            if (typeId <= 0) typeId = IValueMeta.TYPE_STRING;
          }
          bkMeta = ValueMetaFactory.createValueMeta(srcFieldName, typeId);
          if (!Utils.isEmpty(bk.getLength())) {
            try {
              bkMeta.setLength(Integer.parseInt(bk.getLength()));
            } catch (Exception ignore) {
            }
          }
        }
        rowMeta.addValueMeta(bkMeta);
      }

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
      throw new RuntimeException("Error building target table layout for hub", e);
    }
  }
}
