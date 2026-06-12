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

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultModel;
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

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_UPDATE_ACTION";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDataVaultUpdate.DataVaultModel.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DataVaultModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataVaultModelFile;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::ActionDataVaultUpdate.PipelineRunConfiguration.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.LogModelCheckFailures.Label",
      toolTip = "i18n::ActionDataVaultUpdate.LogModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean logModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.AbortOnModelCheckFailures.Label",
      toolTip = "i18n::ActionDataVaultUpdate.AbortOnModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean abortOnModelCheckFailures = true;

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
  }

  @Override
  public Object clone() {
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

      for (IDvTable table : tables) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Log.GeneratingForTable", table.getName()));

        List<PipelineMeta> pipelineMetas =
            table.generateUpdatePipelines(getMetadataProvider(), getVariables(), model, loadDate);

        if (pipelineMetas == null || pipelineMetas.isEmpty()) {
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
