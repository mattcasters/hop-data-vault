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

package org.apache.hop.datavault.workflow.actions.datavaultupdate;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvGeneratedPipelineSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** This defines a Data Vault Update action. */
@Action(
    id = "DATA_VAULT_UPDATE",
    name = "i18n::ActionDataVaultUpdate.Name",
    description = "i18n::ActionDataVaultUpdate.Description",
    image = "datavault_model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionDataVaultUpdate.Keywords",
    documentationUrl = "/workflow/actions/datavaultupdate.html")
@GuiPlugin(description = "Data Vault Update action")
@Getter
@Setter
public class ActionDataVaultUpdate extends ActionBase implements Cloneable, IAction {
  private static final Class<?> PKG = ActionDataVaultUpdate.class;

  /** Root dialog identifier (action name field lives outside the tab folder). */
  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_UPDATE_ACTION";

  public static final String GUI_PLUGIN_ELEMENT_MODEL_TAB_ID =
      "DATAVAULT_UPDATE_ACTION_MODEL_TAB";

  public static final String GUI_PLUGIN_ELEMENT_DDL_TAB_ID = "DATAVAULT_UPDATE_ACTION_DDL_TAB";

  public static final String GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID =
      "DATAVAULT_UPDATE_ACTION_SOURCE_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDataVaultUpdate.DataVaultModel.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DataVaultModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String dataVaultModelFile;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::ActionDataVaultUpdate.PipelineRunConfiguration.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.LogModelCheckFailures.Label",
      toolTip = "i18n::ActionDataVaultUpdate.LogModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean logModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.AbortOnModelCheckFailures.Label",
      toolTip = "i18n::ActionDataVaultUpdate.AbortOnModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean abortOnModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.UpdateTargetStructure.Label",
      toolTip = "i18n::ActionDataVaultUpdate.UpdateTargetStructure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean updateTargetDatabaseStructure = true;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDataVaultUpdate.DdlSqlFilename.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DdlSqlFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private String ddlSqlFilename;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.FailIfDdlNeeded.Label",
      toolTip = "i18n::ActionDataVaultUpdate.FailIfDdlNeeded.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean failIfDdlNeeded;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.RecordSourceGroup.Label",
      toolTip = "i18n::ActionDataVaultUpdate.RecordSourceGroup.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private String recordSourceGroup;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.EnsureSpecialRecords.Label",
      toolTip = "i18n::ActionDataVaultUpdate.EnsureSpecialRecords.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private boolean ensureSpecialRecords = true;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.DoNotUpdateTargetDatabase.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DoNotUpdateTargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private boolean doNotUpdateTargetDatabase;

  public ActionDataVaultUpdate(String name) {
    super(name, "");
    dataVaultModelFile = null;
    pipelineRunConfiguration = null;
  }

  public ActionDataVaultUpdate() {
    this("");
  }

  public ActionDataVaultUpdate(ActionDataVaultUpdate meta) {
    super(meta);
    this.dataVaultModelFile = meta.dataVaultModelFile;
    this.pipelineRunConfiguration = meta.pipelineRunConfiguration;
    this.logModelCheckFailures = meta.logModelCheckFailures;
    this.abortOnModelCheckFailures = meta.abortOnModelCheckFailures;
    this.updateTargetDatabaseStructure = meta.updateTargetDatabaseStructure;
    this.ddlSqlFilename = meta.ddlSqlFilename;
    this.failIfDdlNeeded = meta.failIfDdlNeeded;
    this.ensureSpecialRecords = meta.ensureSpecialRecords;
    this.doNotUpdateTargetDatabase = meta.doNotUpdateTargetDatabase;
    this.recordSourceGroup = meta.recordSourceGroup;
  }

  @Override
  public ActionDataVaultUpdate clone() {
    return new ActionDataVaultUpdate(this);
  }


  @Override
  public String getDialogClassName() {
    return ActionDataVaultUpdateDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    try {

      String realRunConfig = resolve(pipelineRunConfiguration);
      if (Utils.isEmpty(realRunConfig)) {
        logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.NoRunConfig"));
        return result;
      }

      DataVaultModel model = loadReferencedDataModel(getMetadataProvider(), this);

      // Perform model check if requested
      if (logModelCheckFailures || abortOnModelCheckFailures) {
        List<ICheckResult> remarks = model.check(getMetadataProvider(), this);
        for (ICheckResult remark : remarks) {
          if (remark.getType() == ICheckResult.TYPE_RESULT_WARNING
              || remark.getType() == ICheckResult.TYPE_RESULT_ERROR) {
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionDataVaultUpdate.Log.ModelCheckResult",
                    remark.getTypeDesc(),
                    remark.getText()));
          }
        }

        if (abortOnModelCheckFailures) {
          boolean hasError =
              remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
          if (hasError) {
            logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.AbortingOnModelCheck"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }
        }
      }

      List<IDvTable> tables = model.getTables();
      if (tables == null || tables.isEmpty()) {
        logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.NoTables"));
        result.setResult(true);
        result.setNrErrors(0);
        return result;
      }

      Date loadDate = new Date(); // static load date for this batch update
      boolean success = true;
      int totalErrors = 0;
      String realRecordSourceGroup = resolve(recordSourceGroup);

      if (!Utils.isEmpty(realRecordSourceGroup)) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Log.UsingRecordSourceGroup", realRecordSourceGroup));
      }

      String realDdlSqlFilename = resolve(ddlSqlFilename);
      boolean processDdl =
          updateTargetDatabaseStructure
              || failIfDdlNeeded
              || !Utils.isEmpty(realDdlSqlFilename);

      if (processDdl) {
        Map<DatabaseMeta, List<String>> ddlMap = new HashMap<>();
        for (IDvTable table : tables) {
          try {
            Map<DatabaseMeta, List<String>> tableDdl =
                table.generateUpdateDdl(getMetadataProvider(), getVariables(), model);
            if (tableDdl != null) {
              for (Map.Entry<DatabaseMeta, List<String>> e : tableDdl.entrySet()) {
                ddlMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
              }
            }
          } catch (Exception ex) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Error.DdlGenerateFailed", table.getName()),
                ex);
            totalErrors++;
            success = false;
          }
        }

        if (hasDdlStatements(ddlMap)) {
          if (failIfDdlNeeded) {
            logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.AbortingOnDdlNeeded"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }

          if (!Utils.isEmpty(realDdlSqlFilename)) {
            try {
              writeDdlToFile(ddlMap, realDdlSqlFilename);
              logBasic(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Log.DdlSavedToFile", realDdlSqlFilename));
            } catch (Exception e) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Error.DdlSaveFailed", realDdlSqlFilename),
                  e);
              totalErrors++;
              success = false;
            }
          }

          if (updateTargetDatabaseStructure) {
            ILoggingObject loggingObject =
                new SimpleLoggingObject("ActionDataVaultUpdate", LoggingObjectType.GENERAL, null);
            for (Map.Entry<DatabaseMeta, List<String>> entry : ddlMap.entrySet()) {
              DatabaseMeta dbMeta = entry.getKey();
              if (dbMeta == null || entry.getValue() == null) {
                continue;
              }
              try (Database db = new Database(loggingObject, getVariables(), dbMeta)) {
                db.connect();
                for (String ddl : entry.getValue()) {
                  if (!Utils.isEmpty(ddl)) {
                    logBasic(
                        BaseMessages.getString(
                            PKG, "ActionDataVaultUpdate.Log.ExecutingDdl", dbMeta.getName()));
                    db.execStatements(ddl);
                  }
                }
              } catch (Exception e) {
                logError(
                    BaseMessages.getString(
                        PKG, "ActionDataVaultUpdate.Error.DdlExecutionFailed", dbMeta.getName()),
                    e);
                totalErrors++;
                success = false;
              }
            }
          } else {
            logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.SkippingDdlExecution"));
          }
        } else {
          logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.NoDdlNeeded"));
        }
      }

      if (doNotUpdateTargetDatabase) {
        logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.SkippingDataUpdate"));
        result.setResult(success);
        result.setNrErrors(success ? 0 : (totalErrors > 0 ? totalErrors : 1));
        return result;
      }

      if (ensureSpecialRecords) {
        ILoggingObject specialRecordsLoggingObject =
            new SimpleLoggingObject(
                "ActionDataVaultUpdate.ensureSpecialRecords", LoggingObjectType.GENERAL, null);
        int totalInserted = 0;
        for (DvTableType tableType : new DvTableType[] {DvTableType.HUB, DvTableType.LINK}) {
          for (IDvTable table : tables) {
            if (table.getTableType() != tableType) {
              continue;
            }
            try {
              int inserted =
                  table.ensureSpecialRecords(
                      getMetadataProvider(), getVariables(), model, loadDate, specialRecordsLoggingObject);
              totalInserted += inserted;
              if (inserted > 0) {
                logBasic(
                    BaseMessages.getString(
                        PKG,
                        "ActionDataVaultUpdate.Log.SpecialRecordsInserted",
                        table.getName(),
                        inserted));
              }
            } catch (Exception ex) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Error.SpecialRecordsFailed", table.getName()),
                  ex);
              totalErrors++;
              success = false;
            }
          }
        }
        if (totalInserted > 0) {
          logBasic(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Log.SpecialRecordsTotal", totalInserted));
        }
      }

      for (IDvTable table : tables) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Log.GeneratingForTable", table.getName()));

        List<PipelineMeta> pipelineMetas =
            table.generateUpdatePipelines(
                getMetadataProvider(),
                getVariables(),
                model,
                loadDate,
                realRecordSourceGroup);

        if (pipelineMetas == null || pipelineMetas.isEmpty()) {
          if (!Utils.isEmpty(realRecordSourceGroup)) {
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionDataVaultUpdate.Log.SkippingTableForGroup",
                    table.getName(),
                    realRecordSourceGroup));
            continue;
          }
          logError(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Error.GenerateFailed", table.getName()));
          totalErrors++;
          success = false;
          continue;
        }

        DataVaultConfiguration pipelineConfig = model.getConfigurationOrDefault();

        for (PipelineMeta pipelineMeta : pipelineMetas) {
          if (pipelineMeta == null) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Error.GenerateFailed", table.getName()));
            totalErrors++;
            success = false;
            continue;
          }

          pipelineMeta.lookupReferencesAfterLoading();

          String savedPipelineFile =
              DvGeneratedPipelineSupport.saveBeforeExecution(
                  pipelineConfig, getVariables(), pipelineMeta);
          if (!Utils.isEmpty(savedPipelineFile)) {
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionDataVaultUpdate.Log.SavedGeneratedPipeline",
                    pipelineMeta.getName(),
                    savedPipelineFile));
          }

          logBasic("====================================================================");
          logBasic(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Log.RunningPipeline", table.getName(), realRunConfig));

          IPipelineEngine<PipelineMeta> engine =
              PipelineEngineFactory.createPipelineEngine(
                  getVariables(), realRunConfig, getMetadataProvider(), pipelineMeta);

          engine.setLogLevel(getLogLevel());
          engine.setParent(this);
          engine.setParentWorkflow(getParentWorkflow());
          engine.execute();
          engine.waitUntilFinished();

          Result pipeResult = engine.getResult();
          if (pipeResult != null) {
            result.setNrLinesRead(result.getNrLinesRead() + pipeResult.getNrLinesRead());
            result.setNrLinesWritten(result.getNrLinesWritten() + pipeResult.getNrLinesWritten());
            result.setNrLinesInput(result.getNrLinesInput() + pipeResult.getNrLinesInput());
            result.setNrLinesOutput(result.getNrLinesOutput() + pipeResult.getNrLinesOutput());
            result.setNrLinesUpdated(result.getNrLinesUpdated() + pipeResult.getNrLinesUpdated());
            result.setNrLinesDeleted(result.getNrLinesDeleted() + pipeResult.getNrLinesDeleted());
            result.setNrErrors(result.getNrErrors() + pipeResult.getNrErrors());

            if (pipeResult.getNrErrors() > 0 || !pipeResult.getResult()) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Error.PipelineFailed", table.getName()));
              success = false;
            }
          }
        }
      }

      result.setResult(success);
      if (success) {
        result.setNrErrors(0);
      } else {
        result.setNrErrors(totalErrors > 0 ? totalErrors : 1);
      }

      return result;

    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.General"), e);
      result.setResult(false);
      result.setNrErrors(1);
      return result;
    }
  }

  private boolean hasDdlStatements(Map<DatabaseMeta, List<String>> ddlMap) {
    if (ddlMap == null || ddlMap.isEmpty()) {
      return false;
    }
    for (List<String> ddlList : ddlMap.values()) {
      if (ddlList == null) {
        continue;
      }
      for (String ddl : ddlList) {
        if (!Utils.isEmpty(ddl)) {
          return true;
        }
      }
    }
    return false;
  }

  private void writeDdlToFile(Map<DatabaseMeta, List<String>> ddlMap, String filename)
      throws IOException, HopException {
    FileObject file = HopVfs.getFileObject(filename, getVariables());
    try (OutputStreamWriter writer =
        new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
      for (Map.Entry<DatabaseMeta, List<String>> entry : ddlMap.entrySet()) {
        DatabaseMeta dbMeta = entry.getKey();
        if (dbMeta != null) {
          writer.write("-- Target database: ");
          writer.write(dbMeta.getName());
          writer.write(Const.CR);
        }
        if (entry.getValue() != null) {
          for (String ddl : entry.getValue()) {
            if (!Utils.isEmpty(ddl)) {
              writer.write(ddl);
              writer.write(Const.CR);
            }
          }
        }
        writer.write(Const.CR);
      }
    }
  }

  private DataVaultModel loadReferencedDataModel(IHopMetadataProvider provider, IVariables variables)
      throws HopException {
    String realModelFile = variables.resolve(dataVaultModelFile);
    if (Utils.isEmpty(realModelFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.NoModelFile"));
    }
    // Load the DataVaultModel from .hdv file (XML)
    FileObject file = HopVfs.getFileObject(realModelFile, getVariables());
    Document document = XmlHandler.loadXmlFile(file);
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    if (rootNode == null) {
      rootNode = document.getDocumentElement();
    }

    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, provider);
    model.setFilename(realModelFile);

    return model;
  }

  /**
   * @return The objects referenced in the transform, like a a pipeline, a workflow, a mapper, a
   *     reducer, a combiner, ...
   */
  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
            BaseMessages.getString(PKG, "ActionDataVaultUpdate.ReferencedObject.Description"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {
      StringUtils.isNotEmpty(dataVaultModelFile),
    };
  }

  /**
   * Load the referenced object
   *
   * @param index the referenced object index to load (in case there are multiple references)
   * @param metadataProvider metadataProvider
   * @param variables the variable variables to use
   * @return the referenced object once loaded
   * @throws HopException
   */
  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return loadReferencedDataModel(metadataProvider, variables);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }

  @Override
  public boolean supportsDrillDown() {
    return true;
  }
}
