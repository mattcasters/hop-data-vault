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

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.fileinput.FileTypeFilter;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.getfilenames.FileItem;
import org.apache.hop.pipeline.transforms.getfilenames.FilterItem;
import org.apache.hop.pipeline.transforms.getfilenames.GetFileNamesMeta;
import org.apache.hop.pipeline.transforms.pipelineexecutor.PipelineExecutorMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/**
 * Stages generated DV update pipelines and runs them through a Get File Names → Pipeline Executor
 * orchestrator for round-robin parallel execution.
 */
public final class DvPipelineOrchestratorSupport {

  public static final String DEFAULT_STAGING_PREFIX = "${java.io.tmpdir}/dv2/";
  public static final String HPL_FILE_MASK = ".*\\.hpl$";

  private DvPipelineOrchestratorSupport() {}

  /** Resolves the staging folder, defaulting to {@code ${java.io.tmpdir}/dv2/<model-name>/}. */
  public static String resolveStagingFolder(
      String configuredFolder, IVariables variables, String modelName) {
    String folder = configuredFolder;
    if (variables != null) {
      folder = variables.resolve(folder);
    }
    if (Utils.isEmpty(folder)) {
      folder = DEFAULT_STAGING_PREFIX + sanitizeModelName(modelName) + "/";
    }
    return ensureTrailingSlash(folder);
  }

  /** Resolves parallel pipeline executor copies (minimum 1). */
  public static int resolveParallelCopies(String copiesSetting, IVariables variables) {
    String value = copiesSetting;
    if (variables != null) {
      value = variables.resolve(value);
    }
    if (Utils.isEmpty(value)) {
      return 1;
    }
    try {
      int copies = Integer.parseInt(value.trim());
      return Math.max(1, copies);
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  /** Creates the staging folder and removes any previously staged {@code .hpl} files. */
  public static void prepareStagingFolder(String folder, IVariables variables)
      throws HopException {
    try {
      FileObject stagingFolder = HopVfs.getFileObject(folder, variables);
      if (stagingFolder.exists()) {
        FileObject[] children = stagingFolder.getChildren();
        if (children != null) {
          for (FileObject child : children) {
            if (child != null
                && child.getType().hasContent()
                && child.getName().getBaseName().endsWith(PipelineMeta.PIPELINE_EXTENSION)) {
              child.delete();
            }
          }
        }
      } else {
        stagingFolder.createFolder();
      }
    } catch (Exception e) {
      throw new HopException("Unable to prepare pipeline staging folder " + folder, e);
    }
  }

  /** Writes each generated pipeline to the staging folder. */
  public static void stagePipelines(
      String folder, IVariables variables, List<PipelineMeta> pipelines) throws HopException {
    if (pipelines == null || pipelines.isEmpty()) {
      return;
    }
    for (PipelineMeta pipelineMeta : pipelines) {
      if (pipelineMeta == null) {
        continue;
      }
      String pipelineFilename =
          appendPath(folder, pipelineMeta.getName() + PipelineMeta.PIPELINE_EXTENSION);
      pipelineMeta.setFilename(pipelineFilename);
      writePipeline(variables, pipelineMeta, pipelineFilename);
    }
  }

  /**
   * Builds a pipeline that lists staged {@code .hpl} files and executes them via Pipeline Executor.
   */
  public static PipelineMeta buildOrchestratorPipeline(
      String stagingFolder, String runConfiguration, int parallelCopies, String modelName) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("DV Update Orchestrator - " + modelName);

    GetFileNamesMeta getFileNamesMeta = new GetFileNamesMeta();
    getFileNamesMeta.setDefault();
    getFileNamesMeta.getFilterItemList().clear();
    getFileNamesMeta
        .getFilterItemList()
        .add(new FilterItem(FileTypeFilter.ONLY_FILES.toString()));

    List<FileItem> filesList = new ArrayList<>();
    filesList.add(new FileItem(stagingFolder, HPL_FILE_MASK, "", "N", "N"));
    getFileNamesMeta.setFilesList(filesList);

    TransformMeta getFileNamesTransform =
        new TransformMeta("Get pipeline files", getFileNamesMeta);
    getFileNamesTransform.setLocation(100, 100);
    pipelineMeta.addTransform(getFileNamesTransform);

    PipelineExecutorMeta executorMeta = new PipelineExecutorMeta();
    executorMeta.setDefault();
    executorMeta.setFilenameInField(true);
    executorMeta.setFilenameField("filename");
    executorMeta.setRunConfigurationName(runConfiguration);
    executorMeta.setInheritingAllVariables(true);

    TransformMeta executorTransform =
        new TransformMeta("Execute update pipelines", executorMeta);
    executorTransform.setLocation(300, 100);
    executorTransform.setCopiesString(String.valueOf(Math.max(1, parallelCopies)));
    pipelineMeta.addTransform(executorTransform);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(getFileNamesTransform, executorTransform));

    return pipelineMeta;
  }

  /** Runs the orchestrator pipeline and returns its result. */
  public static Result runOrchestrator(
      PipelineMeta orchestrator,
      String runConfiguration,
      LogLevel logLevel,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(
            variables, runConfiguration, metadataProvider, orchestrator);

    if (logLevel != null) {
      engine.setLogLevel(logLevel);
    }
    engine.setParent(parent);
    engine.setParentWorkflow(parentWorkflow);
    engine.execute();
    engine.waitUntilFinished();

    Result orchestratorResult = engine.getResult();
    return orchestratorResult != null ? orchestratorResult : new Result();
  }

  /** Merges pipeline counters from {@code source} into {@code target}. */
  public static void mergeResult(Result target, Result source) {
    if (target == null || source == null) {
      return;
    }
    target.setNrLinesRead(target.getNrLinesRead() + source.getNrLinesRead());
    target.setNrLinesWritten(target.getNrLinesWritten() + source.getNrLinesWritten());
    target.setNrLinesInput(target.getNrLinesInput() + source.getNrLinesInput());
    target.setNrLinesOutput(target.getNrLinesOutput() + source.getNrLinesOutput());
    target.setNrLinesUpdated(target.getNrLinesUpdated() + source.getNrLinesUpdated());
    target.setNrLinesDeleted(target.getNrLinesDeleted() + source.getNrLinesDeleted());
    target.setNrErrors(target.getNrErrors() + source.getNrErrors());
  }

  private static void writePipeline(
      IVariables variables, PipelineMeta pipelineMeta, String pipelineFilename)
      throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(pipelineFilename, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      String xml = pipelineMeta.getXml(variables);
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        writer.write(xml);
      }
    } catch (Exception e) {
      throw new HopException(
          "Unable to stage generated pipeline '"
              + pipelineMeta.getName()
              + "' to "
              + pipelineFilename,
          e);
    }
  }

  private static String sanitizeModelName(String modelName) {
    if (Utils.isEmpty(modelName)) {
      return "model";
    }
    return modelName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String ensureTrailingSlash(String folder) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder;
    }
    return folder + "/";
  }

  private static String appendPath(String folder, String filename) {
    return ensureTrailingSlash(folder) + filename;
  }
}