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

package org.apache.hop.datavault.workflow.actions.datavaultupdate;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.DvGeneratedPipelineSupport;
import org.apache.hop.datavault.metadata.DvIntegerSettingValidationSupport;
import org.apache.hop.datavault.metadata.DvPipelineOrchestratorSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;

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
      order = "0450",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.DetailedDataTypeChecking.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DetailedDataTypeChecking.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean detailedDataTypeChecking = true;

  @GuiWidgetElement(
      order = "0350",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.ParallelPipelineCopies.Label",
      toolTip = "i18n::ActionDataVaultUpdate.ParallelPipelineCopies.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String parallelPipelineCopies = "1";

  @GuiWidgetElement(
      order = "0360",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.PipelineStagingFolder.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PipelineStagingFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineStagingFolder;

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
    this.detailedDataTypeChecking = meta.detailedDataTypeChecking;
    this.updateTargetDatabaseStructure = meta.updateTargetDatabaseStructure;
    this.ddlSqlFilename = meta.ddlSqlFilename;
    this.failIfDdlNeeded = meta.failIfDdlNeeded;
    this.ensureSpecialRecords = meta.ensureSpecialRecords;
    this.doNotUpdateTargetDatabase = meta.doNotUpdateTargetDatabase;
    this.recordSourceGroup = meta.recordSourceGroup;
    this.parallelPipelineCopies = meta.parallelPipelineCopies;
    this.pipelineStagingFolder = meta.pipelineStagingFolder;
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
        DvModelCheckOptions checkOptions = new DvModelCheckOptions();
        checkOptions.setDetailedDataTypeChecking(detailedDataTypeChecking);
        List<ICheckResult> remarks = model.check(getMetadataProvider(), this, checkOptions);
        for (ICheckResult remark : remarks) {
          String message =
              BaseMessages.getString(
                  PKG,
                  "ActionDataVaultUpdate.Log.ModelCheckResult",
                  remark.getTypeDesc(),
                  remark.getText());
          if (remark.getType() == ICheckResult.TYPE_RESULT_ERROR) {
            logError(message);
          } else if (remark.getType() == ICheckResult.TYPE_RESULT_WARNING) {
            logBasic(message);
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
        List<String> ddlStatements = new ArrayList<>();
        for (IDvTable table : tables) {
          try {
            for (String ddl :
                table.generateUpdateDdl(getMetadataProvider(), getVariables(), model)) {
              if (!Utils.isEmpty(ddl)) {
                ddlStatements.add(ddl);
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

        if (hasDdlStatements(ddlStatements)) {
          if (failIfDdlNeeded) {
            logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.AbortingOnDdlNeeded"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }

          DatabaseMeta targetDatabase =
              DvSpecialRecordSupport.loadTargetDatabase(
                  getMetadataProvider(), model.getConfigurationOrDefault());

          if (!Utils.isEmpty(realDdlSqlFilename)) {
            try {
              writeDdlToFile(ddlStatements, targetDatabase, realDdlSqlFilename);
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
            if (targetDatabase == null) {
              logError(
                  BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.DdlExecutionFailed", ""));
              totalErrors++;
              success = false;
            } else {
              ILoggingObject loggingObject =
                  new SimpleLoggingObject("ActionDataVaultUpdate", LoggingObjectType.GENERAL, null);
              try (Database db = new Database(loggingObject, getVariables(), targetDatabase)) {
                db.connect();
                for (String ddl : ddlStatements) {
                  if (!Utils.isEmpty(ddl)) {
                    logBasic(
                        BaseMessages.getString(
                            PKG,
                            "ActionDataVaultUpdate.Log.ExecutingDdl",
                            targetDatabase.getName()));
                    db.execStatements(ddl);
                  }
                }
              } catch (Exception e) {
                logError(
                    BaseMessages.getString(
                        PKG,
                        "ActionDataVaultUpdate.Error.DdlExecutionFailed",
                        targetDatabase.getName()),
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

      DataVaultConfiguration pipelineConfig = model.getConfigurationOrDefault();
      try {
        validatePipelineIntegerSettings(pipelineConfig);
      } catch (HopException e) {
        logError(e.getMessage());
        result.setResult(false);
        result.setNrErrors(1);
        return result;
      }

      LogLevel pipelineLogLevel = pipelineConfig.resolveExecutionLogLevel();
      List<PipelineMeta> allPipelineMetas = new ArrayList<>();

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
          allPipelineMetas.add(pipelineMeta);

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
        }
      }

      if (!allPipelineMetas.isEmpty()) {
        String stagingFolder =
            resolve(DvPipelineOrchestratorSupport.resolveStagingFolder(
                pipelineStagingFolder, getVariables(), model.getName()));
        int parallelCopies =
            DvPipelineOrchestratorSupport.resolveParallelCopies(
                parallelPipelineCopies, getVariables());

        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionDataVaultUpdate.Log.StagingPipelines",
                stagingFolder,
                allPipelineMetas.size()));
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionDataVaultUpdate.Log.ParallelCopies",
                parallelCopies));

        try {
          DvPipelineOrchestratorSupport.prepareStagingFolder(stagingFolder, getVariables());
          DvPipelineOrchestratorSupport.stagePipelines(
              stagingFolder, getVariables(), allPipelineMetas);

          PipelineMeta orchestrator =
              DvPipelineOrchestratorSupport.buildOrchestratorPipeline(
                  stagingFolder, realRunConfig, parallelCopies, model.getName());

          logBasic(
              BaseMessages.getString(
                  PKG,
                  "ActionDataVaultUpdate.Log.RunningOrchestrator",
                  orchestrator.getName(),
                  realRunConfig));

          Result orchestratorResult =
              DvPipelineOrchestratorSupport.runOrchestrator(
                  orchestrator,
                  realRunConfig,
                  pipelineLogLevel != null ? pipelineLogLevel : getLogLevel(),
                  this,
                  getParentWorkflow(),
                  getVariables(),
                  getMetadataProvider());

          DvPipelineOrchestratorSupport.mergeResult(result, orchestratorResult);

          if (orchestratorResult.getNrErrors() > 0 || !orchestratorResult.getResult()) {
            logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.OrchestratorFailed"));
            success = false;
          }
        } finally {
          try {
            DvPipelineOrchestratorSupport.cleanupStagingFolder(stagingFolder, getVariables());
            logBasic(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Log.StagingCleanup", stagingFolder));
          } catch (HopException e) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Error.StagingCleanupFailed", stagingFolder),
                e);
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

  private void validatePipelineIntegerSettings(DataVaultConfiguration pipelineConfig)
      throws HopException {
    DvIntegerSettingValidationSupport.requireModelPipelineIntegerSettings(
        pipelineConfig, getVariables());
    DvIntegerSettingValidationSupport.requirePositiveInteger(
        parallelPipelineCopies,
        getVariables(),
        "1",
        BaseMessages.getString(
            DvIntegerSettingValidationSupport.class,
            "DvIntegerSettingValidation.Label.ParallelPipelineCopies"));
  }

  private boolean hasDdlStatements(List<String> ddlStatements) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return false;
    }
    for (String ddl : ddlStatements) {
      if (!Utils.isEmpty(ddl)) {
        return true;
      }
    }
    return false;
  }

  private void writeDdlToFile(
      List<String> ddlStatements, DatabaseMeta targetDatabase, String filename)
      throws IOException, HopException {
    FileObject file = HopVfs.getFileObject(filename, getVariables());
    try (OutputStreamWriter writer =
        new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
      if (targetDatabase != null) {
        writer.write("-- Target database: ");
        writer.write(targetDatabase.getName());
        writer.write(Const.CR);
      }
      for (String ddl : ddlStatements) {
        if (!Utils.isEmpty(ddl)) {
          writer.write(ddl);
          writer.write(Const.CR);
        }
      }
      writer.write(Const.CR);
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
