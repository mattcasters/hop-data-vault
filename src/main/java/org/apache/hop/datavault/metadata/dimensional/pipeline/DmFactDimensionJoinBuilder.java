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
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculationType;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMetaFunction;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;

/** Prepares fact stream fields and dimension lookups for fact-to-dimension joins. */
public final class DmFactDimensionJoinBuilder {

  private DmFactDimensionJoinBuilder() {}

  public static TransformMeta addFactDimensionJoin(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension,
      DmFactDimensionRole role) {
    if (predecessor == null || dimension == null || role == null) {
      return null;
    }
    predecessor = addDateKeyCalculatorIfNeeded(ctx, pipelineMeta, predecessor, dimension, role);
    return DmDimensionLookupBuilder.addFactDimensionLookup(
        ctx, pipelineMeta, predecessor, dimension, role);
  }

  public static List<DimensionLookupMeta.DLKey> buildFactLookupKeys(
      DmDimension dimension, DmFactDimensionRole role, DmPipelineBuilderSupport.BuildContext ctx) {
    List<DimensionLookupMeta.DLKey> keys = new ArrayList<>();
    List<String> naturalKeys =
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables);
    for (String naturalKey : naturalKeys) {
      DimensionLookupMeta.DLKey key = new DimensionLookupMeta.DLKey();
      key.setLookup(naturalKey);
      key.setName(resolveStreamKeyField(role, naturalKey, ctx.variables));
      keys.add(key);
    }
    return keys;
  }

  public static String resolveLookupTransformName(DmFactDimensionRole role, DmDimension dimension) {
    if (!Utils.isEmpty(role.getRoleName())) {
      return "lookup_" + role.getRoleName();
    }
    if (!Utils.isEmpty(role.getDimensionTableName())) {
      return "lookup_" + sanitizeTransformToken(role.getDimensionTableName());
    }
    return "lookup_" + sanitizeTransformToken(dimension.getName());
  }

  private static TransformMeta addDateKeyCalculatorIfNeeded(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension,
      DmFactDimensionRole role) {
    if (!role.isTruncateToDateKey()) {
      return predecessor;
    }
    List<String> naturalKeys =
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables);
    if (naturalKeys.isEmpty()) {
      return predecessor;
    }
    String naturalKey = naturalKeys.get(0);
    String sourceField = resolveSourceFieldName(role, naturalKey, ctx.variables);
    if (Utils.isEmpty(sourceField)) {
      return predecessor;
    }

    String token = sanitizeTransformToken(role.getDimensionTableName());
    String tempYear = "_dm_" + token + "_y";
    String tempMonth = "_dm_" + token + "_m";
    String tempDay = "_dm_" + token + "_d";
    String tempYearScaled = "_dm_" + token + "_y_scaled";
    String tempMonthScaled = "_dm_" + token + "_m_scaled";
    String tempYearMonth = "_dm_" + token + "_ym";
    String constTenThousand = "_dm_" + token + "_c10000";
    String constHundred = "_dm_" + token + "_c100";

    String integerType = IValueMeta.getTypeDescription(IValueMeta.TYPE_INTEGER);
    CalculatorMeta calculatorMeta = new CalculatorMeta();
    List<CalculatorMetaFunction> functions = new ArrayList<>();
    functions.add(
        tempFunction(tempYear, CalculationType.YEAR_OF_DATE, sourceField, integerType));
    functions.add(
        tempFunction(tempMonth, CalculationType.MONTH_OF_DATE, sourceField, integerType));
    functions.add(
        tempFunction(tempDay, CalculationType.DAY_OF_MONTH, sourceField, integerType));
    functions.add(
        tempFunction(constTenThousand, CalculationType.CONSTANT, "10000", integerType));
    functions.add(tempFunction(constHundred, CalculationType.CONSTANT, "100", integerType));
    functions.add(
        tempFunction(
            tempYearScaled, CalculationType.MULTIPLY, tempYear, constTenThousand, integerType));
    functions.add(
        tempFunction(
            tempMonthScaled, CalculationType.MULTIPLY, tempMonth, constHundred, integerType));
    functions.add(
        tempFunction(
            tempYearMonth, CalculationType.ADD, tempYearScaled, tempMonthScaled, integerType));
    functions.add(
        outputFunction(naturalKey, CalculationType.ADD, tempYearMonth, tempDay, integerType));
    calculatorMeta.setFunctions(functions);

    String transformName = "date_key_" + token;
    TransformMeta tm = new TransformMeta("Calculator", transformName, calculatorMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static CalculatorMetaFunction tempFunction(
      String fieldName, CalculationType calcType, String fieldA, String valueType) {
    return calculatorFunction(fieldName, calcType, fieldA, null, valueType, true);
  }

  private static CalculatorMetaFunction tempFunction(
      String fieldName,
      CalculationType calcType,
      String fieldA,
      String fieldB,
      String valueType) {
    return calculatorFunction(fieldName, calcType, fieldA, fieldB, valueType, true);
  }

  private static CalculatorMetaFunction outputFunction(
      String fieldName,
      CalculationType calcType,
      String fieldA,
      String fieldB,
      String valueType) {
    return calculatorFunction(fieldName, calcType, fieldA, fieldB, valueType, false);
  }

  private static CalculatorMetaFunction calculatorFunction(
      String fieldName,
      CalculationType calcType,
      String fieldA,
      String fieldB,
      String valueType,
      boolean removedFromResult) {
    CalculatorMetaFunction function = new CalculatorMetaFunction();
    function.setFieldName(fieldName);
    function.setCalcType(calcType);
    function.setFieldA(fieldA);
    if (!Utils.isEmpty(fieldB)) {
      function.setFieldB(fieldB);
    }
    function.setValueType(valueType);
    function.setRemovedFromResult(removedFromResult);
    return function;
  }

  static String resolveStreamKeyField(
      DmFactDimensionRole role, String naturalKey, IVariables variables) {
    if (role.isTruncateToDateKey()) {
      return resolve(naturalKey, variables);
    }
    String sourceField = resolveSourceFieldName(role, naturalKey, variables);
    if (!Utils.isEmpty(sourceField)) {
      return sourceField;
    }
    return resolve(naturalKey, variables);
  }

  static String resolveSourceFieldName(
      DmFactDimensionRole role, String naturalKey, IVariables variables) {
    String sourceField = resolve(role.getSourceFieldName(), variables);
    if (!Utils.isEmpty(sourceField)) {
      return sourceField;
    }
    return resolve(naturalKey, variables);
  }

  private static String sanitizeTransformToken(String value) {
    if (Utils.isEmpty(value)) {
      return "dimension";
    }
    return value.replaceAll("[^A-Za-z0-9_]+", "_");
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}