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

package org.apache.hop.datavault.metadata.businessvault;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.layout.DvPipelineElkLayout;
import org.apache.hop.datavault.layout.ElkLayout;
import org.apache.hop.datavault.layout.ElkLayoutAlgorithm;
import org.apache.hop.pipeline.PipelineMeta;

/** Saves generated Business Vault build pipelines when configured to do so. */
public final class BvGeneratedPipelineSupport {

  private BvGeneratedPipelineSupport() {}

  public static void applyLayout(PipelineMeta pipelineMeta) throws HopException {
    DvPipelineElkLayout.layout(pipelineMeta);
  }

  public static void applyLayout(List<PipelineMeta> pipelines) throws HopException {
    DvPipelineElkLayout.layout(pipelines);
  }

  /** Applies ELK layered layout for generated SCD2 pipelines (multi-input graphs). */
  public static void applyScd2Layout(PipelineMeta pipelineMeta) throws HopException {
    DvPipelineElkLayout.layout(pipelineMeta, scd2ElkLayout());
  }

  public static void applyScd2Layout(List<PipelineMeta> pipelines) throws HopException {
    DvPipelineElkLayout.layout(pipelines, scd2ElkLayout());
  }

  private static ElkLayout scd2ElkLayout() {
    ElkLayout layout = new ElkLayout(DataVaultConfigSingleton.getConfig().getElkLayout());
    layout.setAlgorithm(ElkLayoutAlgorithm.LAYERED);
    return layout;
  }

  public static String saveBeforeExecution(
      BusinessVaultConfiguration config, IVariables variables, PipelineMeta pipelineMeta)
      throws HopException {
    if (config == null || pipelineMeta == null) {
      return null;
    }

    String folder = config.getGeneratedPipelineFolder();
    if (Utils.isEmpty(folder)) {
      return null;
    }
    if (variables != null) {
      folder = variables.resolve(folder);
    }
    if (Utils.isEmpty(folder)) {
      return null;
    }

    String pipelineFilename =
        appendPath(folder, pipelineMeta.getName() + PipelineMeta.PIPELINE_EXTENSION);
    pipelineMeta.setFilename(pipelineFilename);

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
      return pipelineFilename;
    } catch (Exception e) {
      throw new HopException(
          "Unable to save generated pipeline '"
              + pipelineMeta.getName()
              + "' to "
              + pipelineFilename,
          e);
    }
  }

  private static String appendPath(String folder, String filename) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder + filename;
    }
    return folder + "/" + filename;
  }
}