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

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Resolves per-dimension and junk-dimension surrogate key settings with backward-compatible defaults. */
public final class DmSurrogateKeySupport {

  private DmSurrogateKeySupport() {}

  public static DmSurrogateKeyStrategy resolveStrategy(DmDimension dimension) {
    if (dimension == null) {
      return DmSurrogateKeyStrategy.AUTO_INCREMENT;
    }
    if (dimension.getSurrogateKeyStrategy() != null) {
      return dimension.getSurrogateKeyStrategy();
    }
    DmDimensionScdType scdType = dimension.getScdTypeOrDefault();
    if (scdType == DmDimensionScdType.TYPE1 && !dimensionUsesHybridAttributes(dimension)) {
      return DmSurrogateKeyStrategy.NONE;
    }
    return DmSurrogateKeyStrategy.AUTO_INCREMENT;
  }

  public static DmJunkSurrogateKeyStrategy resolveJunkStrategy(DmJunkDimension junkDimension) {
    if (junkDimension == null || junkDimension.getSurrogateKeyStrategy() == null) {
      return DmJunkSurrogateKeyStrategy.AUTO_INCREMENT;
    }
    return junkDimension.getSurrogateKeyStrategy();
  }

  public static boolean usesSurrogateColumn(DmDimension dimension) {
    return resolveStrategy(dimension) != DmSurrogateKeyStrategy.NONE;
  }

  public static boolean usesSurrogateColumn(DmJunkDimension junkDimension) {
    DmJunkSurrogateKeyStrategy strategy = resolveJunkStrategy(junkDimension);
    return strategy != null;
  }

  public static String resolveSurrogateKeyField(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables) {
    if (!usesSurrogateColumn(dimension)) {
      return null;
    }
    String explicit = resolve(dimension.getSurrogateKeyField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    DimensionalConfiguration resolvedConfig =
        config != null ? config : new DimensionalConfiguration();
    return resolvedConfig.resolveDimKeyField(variables);
  }

  public static String resolveSurrogateKeySourceField(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables) {
    String explicit = resolve(dimension.getSurrogateKeySourceField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return resolveSurrogateKeyField(dimension, config, variables);
  }

  public static String resolveJunkSurrogateKeyField(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables) {
    String explicit = resolve(junkDimension.getSurrogateKeyField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    DimensionalConfiguration resolvedConfig =
        config != null ? config : new DimensionalConfiguration();
    return resolvedConfig.resolveDimKeyField(variables);
  }

  public static String resolveJunkSurrogateKeySourceField(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables) {
    String explicit = resolve(junkDimension.getSurrogateKeySourceField(), variables);
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return resolveJunkSurrogateKeyField(junkDimension, config, variables);
  }

  public static boolean usesStringSurrogate(DmDimension dimension) {
    return resolveStrategy(dimension) == DmSurrogateKeyStrategy.USE_SOURCE_FIELD;
  }

  public static boolean usesStringSurrogate(DmJunkDimension junkDimension) {
    DmJunkSurrogateKeyStrategy strategy = resolveJunkStrategy(junkDimension);
    return strategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD
        || strategy == DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY;
  }

  public static String deriveEntitySurrogateName(String dimensionName) {
    if (Utils.isEmpty(dimensionName)) {
      return null;
    }
    String base = dimensionName;
    if (base.startsWith("dim_")) {
      base = base.substring(4);
    }
    return base + "_key";
  }

  private static boolean dimensionUsesHybridAttributes(DmDimension dimension) {
    if (dimension == null) {
      return false;
    }
    if (dimension.getScdTypeOrDefault() == DmDimensionScdType.TYPE3) {
      return true;
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute != null
          && (attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_CURRENT
              || attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_PREVIOUS)) {
        return true;
      }
    }
    return false;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}