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

package org.apache.hop.datavault.metadata.dimensional.pipeline;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.IDmFactLikeTable;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Generates fact-like load pipelines with role-playing dimension lookups. */
public final class DmFactLikeLoadBuilder {

  private DmFactLikeLoadBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      IDmFactLikeTable factLike)
      throws HopException {
    if (!(factLike instanceof DmTableBase table)) {
      throw new HopException("Fact-like table must extend DmTableBase");
    }
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, table);
    if (ctx == null) {
      throw new HopException("Unable to create build context for table " + factLike.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);

    TransformMeta predecessor = DmPipelineBuilderSupport.addSourceTableInput(ctx, pipelineMeta);
    for (DmFactDimensionRole role : factLike.getDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      DmDimension dimension =
          DmDimensionResolutionSupport.resolveDimension(
              model, role.getDimensionTableName(), ctx.variables);
      if (dimension == null) {
        throw new HopException(
            "Table "
                + factLike.getName()
                + " references unknown dimension "
                + role.getDimensionTableName());
      }
      predecessor =
          DmDimensionLookupBuilder.addFactDimensionLookup(
              ctx, pipelineMeta, predecessor, dimension, role);
    }

    IRowMeta targetLayout = factLike.getTargetTableLayout(metadataProvider, variables, model);
    DmPipelineBuilderSupport.addTableOutput(ctx, pipelineMeta, targetLayout, predecessor, false);

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }
}