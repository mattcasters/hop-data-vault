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

package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.combinationlookup.CFields;
import org.apache.hop.pipeline.transforms.combinationlookup.CombinationLookupMeta;
import org.apache.hop.pipeline.transforms.combinationlookup.KeyField;
import org.apache.hop.pipeline.transforms.combinationlookup.ReturnFields;

/** Generates junk dimension pipelines using Combination Lookup. */
public final class DmCombinationLookupBuilder {

  private DmCombinationLookupBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmJunkDimension junkDimension)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, junkDimension);
    if (ctx == null) {
      throw new HopException(
          "Unable to create build context for junk dimension " + junkDimension.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    addCombinationLookup(ctx, pipelineMeta, sourceTransform, junkDimension);

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  private static TransformMeta addCombinationLookup(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmJunkDimension junkDimension) {
    CombinationLookupMeta lookupMeta = new CombinationLookupMeta();
    lookupMeta.setConnectionName(ctx.targetDbName);
    lookupMeta.setTableName(ctx.targetTableName);
    lookupMeta.setReplaceFields(true);
    lookupMeta.setCommitSize(
        Integer.parseInt(ctx.config.resolveTargetTableCommitSize(ctx.variables)));

    CFields fields = new CFields();
    List<KeyField> keyFields = new ArrayList<>();
    for (DmNaturalKeyField keyField : junkDimension.getKeyFieldsOrEmpty()) {
      if (keyField == null || Utils.isEmpty(keyField.getFieldName())) {
        continue;
      }
      String field = keyField.getFieldName();
      if (ctx.variables != null) {
        field = ctx.variables.resolve(field);
      }
      keyFields.add(new KeyField(field, field));
    }
    fields.setKeyFields(keyFields);

    DimensionalConfiguration config = ctx.config;
    ReturnFields returnFields = new ReturnFields();
    returnFields.setTechnicalKeyField(
        DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junkDimension, config, ctx.variables));
    returnFields.setUseAutoIncrement(true);
    returnFields.setTechKeyCreation(CombinationLookupMeta.CREATION_METHOD_AUTOINC);
    returnFields.setLastUpdateField(config.resolveLoadDateField(ctx.variables));
    fields.setReturnFields(returnFields);
    lookupMeta.setFields(fields);

    TransformMeta tm =
        new TransformMeta(
            "CombinationLookup", "combo_" + ctx.targetTableName, lookupMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }
}