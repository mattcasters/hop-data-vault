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

package org.apache.hop.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmDimensionLookupBuilder;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmInsertUpdateBuilder;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmScd2DimensionBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Kimball dimension table. */
@Getter
@Setter
public class DmDimension extends DmTableBase {

  @HopMetadataProperty(storeWithCode = true)
  private DmDimensionScdType scdType = DmDimensionScdType.TYPE1;

  @HopMetadataProperty(key = "natural_key", groupKey = "natural_keys")
  private List<DmNaturalKeyField> naturalKeys = new ArrayList<>();

  @HopMetadataProperty(key = "attribute", groupKey = "attributes")
  private List<DmDimensionAttribute> attributes = new ArrayList<>();

  @HopMetadataProperty(key = "outrigger", groupKey = "outriggers")
  private List<DmDimensionOutriggerRef> outriggers = new ArrayList<>();

  @HopMetadataProperty private String surrogateKeyField;

  @HopMetadataProperty(storeWithCode = true)
  private DmSurrogateKeyStrategy surrogateKeyStrategy;

  @HopMetadataProperty private String surrogateKeySourceField;

  public DmDimension() {
    super(DmTableType.DIMENSION);
  }

  public DmDimensionScdType getScdTypeOrDefault() {
    return scdType != null ? scdType : DmDimensionScdType.TYPE1;
  }

  public List<DmNaturalKeyField> getNaturalKeysOrEmpty() {
    return naturalKeys != null ? naturalKeys : List.of();
  }

  public List<DmDimensionAttribute> getAttributesOrEmpty() {
    return attributes != null ? attributes : List.of();
  }

  public List<DmDimensionOutriggerRef> getOutriggersOrEmpty() {
    return outriggers != null ? outriggers : List.of();
  }

  public boolean usesHybridDimensionLookup() {
    if (getScdTypeOrDefault() == DmDimensionScdType.TYPE3) {
      return true;
    }
    for (DmDimensionAttribute attribute : getAttributesOrEmpty()) {
      if (attribute == null || attribute.getScdUpdatePolicy() == null) {
        continue;
      }
      if (attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_CURRENT
          || attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_PREVIOUS) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    super.check(remarks, metadataProvider, variables, model);
    DmValidationSupport.validateDimension(remarks, this, model, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    IRowMeta sourceRowMeta =
        DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
            metadataProvider, variables, model, this);
    return DmLayoutSupport.buildDimensionTargetTableLayout(
        this, config, variables, sourceRowMeta);
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp)
      throws HopException {
    PipelineMeta pipelineMeta;
    if (getScdTypeOrDefault() == DmDimensionScdType.TYPE2) {
      pipelineMeta =
          DmScd2DimensionBuilder.generatePipeline(
              metadataProvider, variables, model, this, loadTimestamp);
    } else if (usesHybridDimensionLookup()) {
      pipelineMeta =
          DmDimensionLookupBuilder.generateHybridDimensionPipeline(
              metadataProvider, variables, model, this);
    } else {
      pipelineMeta =
          DmInsertUpdateBuilder.generatePipeline(metadataProvider, variables, model, this);
    }
    return List.of(pipelineMeta);
  }
}