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

package org.apache.hop.quality.resolve;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.metadata.DataQualityRuleSetMeta;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.RecordQualityRuleBinding;

/** Resolves catalog rule bindings into concrete {@link DataQualityRule} instances. */
public final class QualityRuleResolver {

  private QualityRuleResolver() {}

  public static List<DataQualityRule> resolve(
      List<RecordQualityRuleBinding> bindings, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<DataQualityRule> resolved = new ArrayList<>();
    if (bindings == null) {
      return resolved;
    }
    for (RecordQualityRuleBinding binding : bindings) {
      if (binding == null || !binding.isEnabled()) {
        continue;
      }
      DataQualityRule rule = resolveOne(binding, metadataProvider);
      if (rule != null && rule.isEnabled()) {
        resolved.add(rule);
      }
    }
    return resolved;
  }

  private static DataQualityRule resolveOne(
      RecordQualityRuleBinding binding, IHopMetadataProvider metadataProvider) throws HopException {
    DataQualityRule base = null;
    if (binding.getInlineRule() != null) {
      base = binding.getInlineRule().copy();
    } else if (!Utils.isEmpty(binding.getRuleSetName()) && !Utils.isEmpty(binding.getRuleId())) {
      if (metadataProvider == null) {
        throw new HopException(
            "Metadata provider is required to resolve rule set '" + binding.getRuleSetName() + "'");
      }
      DataQualityRuleSetMeta set =
          metadataProvider
              .getSerializer(DataQualityRuleSetMeta.class)
              .load(binding.getRuleSetName());
      if (set == null) {
        throw new HopException("Data quality rule set '" + binding.getRuleSetName() + "' not found");
      }
      DataQualityRule libraryRule = set.findRule(binding.getRuleId());
      if (libraryRule == null) {
        throw new HopException(
            "Rule id '"
                + binding.getRuleId()
                + "' not found in rule set '"
                + binding.getRuleSetName()
                + "'");
      }
      base = libraryRule.copy();
    }
    if (base == null) {
      return null;
    }
    if (binding.getSeverityOverride() != null) {
      base.setSeverity(binding.getSeverityOverride());
    }
    if (!Utils.isEmpty(binding.getFieldNameOverride())) {
      base.setFieldName(binding.getFieldNameOverride());
    }
    base.ensureId();
    return base;
  }
}
