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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Target table layout helpers aligned with Hop warehouse transform contracts. */
public final class DmLayoutSupport {

  /** Hop PostgreSQL DDL maps integer types without length to DOUBLE PRECISION. */
  static final int DEFAULT_INTEGER_FIELD_LENGTH = 9;

  private DmLayoutSupport() {}

  public static IRowMeta buildDimensionTargetTableLayout(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables)
      throws HopException {
    return buildDimensionTargetTableLayout(dimension, config, variables, null);
  }

  public static IRowMeta buildDimensionTargetTableLayout(
      DmDimension dimension,
      DimensionalConfiguration config,
      IVariables variables,
      IRowMeta sourceRowMeta)
      throws HopException {
    if (dimension == null) {
      throw new HopException("Dimension table layout requires a dimension");
    }
    DimensionalConfiguration resolvedConfig =
        config != null ? config : new DimensionalConfiguration();
    DmDimensionScdType scdType = dimension.getScdTypeOrDefault();
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();

    if (scdType == DmDimensionScdType.TYPE2) {
      addSurrogateKeyColumn(rowMeta, added, dimension, resolvedConfig, variables);
      for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
        addSourceMappedColumn(
            rowMeta, added, resolveFieldName(naturalKey.getFieldName(), variables), sourceRowMeta);
      }
      addColumn(rowMeta, added, resolvedConfig.resolveVersionField(variables), new ValueMetaInteger());
      addColumn(
          rowMeta, added, resolvedConfig.resolveDateFromField(variables), new ValueMetaTimestamp());
      addColumn(
          rowMeta, added, resolvedConfig.resolveDateToField(variables), new ValueMetaTimestamp());
      addColumn(
          rowMeta,
          added,
          resolvedConfig.resolveCurrentFlagField(variables),
          new ValueMetaBoolean());
    } else if (scdType == DmDimensionScdType.TYPE3 || dimensionUsesHybridAttributes(dimension)) {
      addSurrogateKeyColumn(rowMeta, added, dimension, resolvedConfig, variables);
      for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
        addSourceMappedColumn(
            rowMeta, added, resolveFieldName(naturalKey.getFieldName(), variables), sourceRowMeta);
      }
      addColumn(rowMeta, added, resolvedConfig.resolveVersionField(variables), new ValueMetaInteger());
      addColumn(
          rowMeta, added, resolvedConfig.resolveDateFromField(variables), new ValueMetaTimestamp());
      addColumn(
          rowMeta, added, resolvedConfig.resolveDateToField(variables), new ValueMetaTimestamp());
    } else {
      for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
        addSourceMappedColumn(
            rowMeta, added, resolveFieldName(naturalKey.getFieldName(), variables), sourceRowMeta);
      }
    }

    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      addSourceMappedColumn(
          rowMeta, added, resolveFieldName(attribute.getFieldName(), variables), sourceRowMeta);
      if (attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_PREVIOUS) {
        addColumn(rowMeta, added, resolvePreviousFieldName(attribute, variables));
      }
    }

    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      addColumn(rowMeta, added, resolveFieldName(outrigger.getForeignKeyColumn(), variables));
    }

    addColumn(rowMeta, added, resolvedConfig.resolveLoadDateField(variables), new ValueMetaTimestamp());
    return rowMeta;
  }

  public static IRowMeta buildFactTargetTableLayout(
      DmFact fact, DimensionalModel model, DimensionalConfiguration config, IVariables variables)
      throws HopException {
    if (fact == null) {
      throw new HopException("Fact table layout requires a fact table");
    }
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();

    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      String fkColumn = resolveFieldName(role.getForeignKeyColumn(), variables);
      if (Utils.isEmpty(fkColumn)) {
        String dimensionName = role.getDimensionTableName();
        if (!Utils.isEmpty(dimensionName) && model != null) {
          DmDimension dmDimension =
              DmDimensionResolutionSupport.resolveDimension(model, dimensionName, variables);
          if (dmDimension != null) {
            DimensionalConfiguration resolvedConfig =
                config != null ? config : new DimensionalConfiguration();
            fkColumn = defaultFactForeignKeyColumn(dmDimension, role, resolvedConfig, variables);
          }
        }
      }
      addColumn(rowMeta, added, fkColumn, new ValueMetaInteger());
    }

    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      addColumn(rowMeta, added, resolveFieldName(measure.getFieldName(), variables), new ValueMetaNumber());
    }

    return rowMeta;
  }

  public static IRowMeta buildJunkDimensionTargetTableLayout(
      DmJunkDimension junkDimension, DimensionalConfiguration config, IVariables variables)
      throws HopException {
    if (junkDimension == null) {
      throw new HopException("Junk dimension layout requires a junk dimension table");
    }
    DimensionalConfiguration resolvedConfig =
        config != null ? config : new DimensionalConfiguration();
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();
    addJunkSurrogateKeyColumn(rowMeta, added, junkDimension, resolvedConfig, variables);
    for (DmNaturalKeyField keyField : junkDimension.getKeyFieldsOrEmpty()) {
      addColumn(rowMeta, added, resolveFieldName(keyField.getFieldName(), variables));
    }
    addColumn(rowMeta, added, resolvedConfig.resolveLoadDateField(variables), new ValueMetaTimestamp());
    return rowMeta;
  }

  public static IRowMeta buildFactlessFactTargetTableLayout(
      DmFactlessFact fact, DimensionalModel model, DimensionalConfiguration config, IVariables variables)
      throws HopException {
    return buildFactLikeTargetTableLayout(
        fact.getDimensionRolesOrEmpty(), List.of(), model, config, variables);
  }

  public static IRowMeta buildPeriodicSnapshotFactTargetTableLayout(
      DmPeriodicSnapshotFact fact,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables)
      throws HopException {
    if (fact == null) {
      throw new HopException("Periodic snapshot layout requires a fact table");
    }
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();
    addColumn(rowMeta, added, resolveFieldName(fact.getSnapshotDateField(), variables), new ValueMetaTimestamp());
    appendFactLikeColumns(rowMeta, added, fact.getDimensionRolesOrEmpty(), fact.getMeasuresOrEmpty(), model, config, variables);
    return rowMeta;
  }

  public static IRowMeta buildAccumulatingSnapshotFactTargetTableLayout(
      DmAccumulatingSnapshotFact fact,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables)
      throws HopException {
    if (fact == null) {
      throw new HopException("Accumulating snapshot layout requires a fact table");
    }
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();
    for (DmNaturalKeyField grainKey : fact.getGrainKeysOrEmpty()) {
      addColumn(rowMeta, added, resolveFieldName(grainKey.getFieldName(), variables));
    }
    appendFactLikeColumns(rowMeta, added, fact.getDimensionRolesOrEmpty(), fact.getMeasuresOrEmpty(), model, config, variables);
    return rowMeta;
  }

  public static IRowMeta buildBridgeTargetTableLayout(
      DmBridge bridge, DimensionalModel model, DimensionalConfiguration config, IVariables variables)
      throws HopException {
    if (bridge == null) {
      throw new HopException("Bridge layout requires a bridge table");
    }
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      String fkColumn = resolveFieldName(ref.getForeignKeyColumn(), variables);
      if (Utils.isEmpty(fkColumn) && model != null && !Utils.isEmpty(ref.getDimensionTableName())) {
        IDmTable dimension = model.findTable(ref.getDimensionTableName());
        if (dimension instanceof DmDimension dmDimension) {
          DimensionalConfiguration resolvedConfig =
              config != null ? config : new DimensionalConfiguration();
          fkColumn =
              defaultFactForeignKeyColumn(
                  dmDimension,
                  new DmFactDimensionRole(ref.getDimensionTableName(), ref.getForeignKeyColumn()),
                  resolvedConfig,
                  variables);
        }
      }
      addColumn(rowMeta, added, fkColumn, new ValueMetaInteger());
    }
    if (!Utils.isEmpty(bridge.getWeightField())) {
      addColumn(rowMeta, added, resolveFieldName(bridge.getWeightField(), variables), new ValueMetaNumber());
    }
    return rowMeta;
  }

  public static IRowMeta buildAggregateFactTargetTableLayout(
      DmAggregateFact fact, DimensionalModel model, DimensionalConfiguration config, IVariables variables)
      throws HopException {
    return buildFactLikeTargetTableLayout(
        fact.getDimensionRolesOrEmpty(), fact.getMeasuresOrEmpty(), model, config, variables);
  }

  public static String resolvePreviousFieldName(DmDimensionAttribute attribute, IVariables variables) {
    if (attribute == null) {
      return null;
    }
    String previousField = attribute.getPreviousFieldName();
    if (variables != null) {
      previousField = variables.resolve(previousField);
    }
    if (!Utils.isEmpty(previousField)) {
      return previousField;
    }
    String fieldName = attribute.getFieldName();
    if (variables != null) {
      fieldName = variables.resolve(fieldName);
    }
    if (Utils.isEmpty(fieldName)) {
      return null;
    }
    return fieldName + "_prev";
  }

  public static String defaultFactForeignKeyColumn(
      DmDimension dimension,
      DmFactDimensionRole role,
      DimensionalConfiguration config,
      IVariables variables) {
    if (!Utils.isEmpty(role.getForeignKeyColumn())) {
      return resolveFieldName(role.getForeignKeyColumn(), variables);
    }
    if (!Utils.isEmpty(role.getRoleName())) {
      return resolveFieldName(role.getRoleName() + "_key", variables);
    }
    String namingSource = resolveFieldName(role.getDimensionTableName(), variables);
    if (Utils.isEmpty(namingSource) && dimension != null) {
      namingSource = dimension.getName();
    }
    if (!Utils.isEmpty(namingSource)) {
      String base = namingSource;
      if (base.startsWith("dim_")) {
        base = base.substring(4);
      }
      return resolveFieldName(base + "_key", variables);
    }
    return resolveFieldName(config.resolveDimKeyField(variables), variables);
  }

  /**
   * Warehouse key column used by Dimension Lookup when resolving a fact join to this dimension.
   * Type 1 dimensions without a generated surrogate use their first natural key (for example
   * {@code date_key} on calendar dimensions).
   */
  public static String resolveDimensionLookupKeyField(
      DmDimension dimension, DimensionalConfiguration config, IVariables variables) {
    if (dimension == null) {
      return resolveFieldName(config.resolveDimKeyField(variables), variables);
    }
    if (!DmSurrogateKeySupport.usesSurrogateColumn(dimension)) {
      for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
        String fieldName = resolveFieldName(naturalKey.getFieldName(), variables);
        if (!Utils.isEmpty(fieldName)) {
          return fieldName;
        }
      }
    }
    return DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
  }

  private static void addSurrogateKeyColumn(
      RowMeta rowMeta,
      Set<String> added,
      DmDimension dimension,
      DimensionalConfiguration config,
      IVariables variables)
      throws HopException {
    if (!DmSurrogateKeySupport.usesSurrogateColumn(dimension)) {
      return;
    }
    String surrogateField =
        DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
    if (Utils.isEmpty(surrogateField)) {
      return;
    }
    if (DmSurrogateKeySupport.usesStringSurrogate(dimension)) {
      addColumn(rowMeta, added, surrogateField);
    } else {
      addColumn(rowMeta, added, surrogateField, new ValueMetaInteger());
    }
  }

  private static void addJunkSurrogateKeyColumn(
      RowMeta rowMeta,
      Set<String> added,
      DmJunkDimension junkDimension,
      DimensionalConfiguration config,
      IVariables variables)
      throws HopException {
    String surrogateField =
        DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junkDimension, config, variables);
    if (Utils.isEmpty(surrogateField)) {
      return;
    }
    if (DmSurrogateKeySupport.usesStringSurrogate(junkDimension)) {
      addColumn(rowMeta, added, surrogateField);
    } else {
      addColumn(rowMeta, added, surrogateField, new ValueMetaInteger());
    }
  }

  public static Set<String> layoutFieldNames(IRowMeta layout) {
    Set<String> names = new HashSet<>();
    if (layout == null) {
      return names;
    }
    for (int i = 0; i < layout.size(); i++) {
      String name = layout.getValueMeta(i).getName();
      if (!Utils.isEmpty(name)) {
        names.add(name);
      }
    }
    return names;
  }

  private static void addSourceMappedColumn(
      RowMeta rowMeta, Set<String> added, String fieldName, IRowMeta sourceRowMeta)
      throws HopException {
    if (!Utils.isEmpty(fieldName) && sourceRowMeta != null) {
      IValueMeta sourceMeta = sourceRowMeta.searchValueMeta(fieldName);
      if (sourceMeta != null) {
        IValueMeta targetMeta = sourceMeta.clone();
        targetMeta.setName(fieldName);
        addColumn(rowMeta, added, targetMeta);
        return;
      }
    }
    addColumn(rowMeta, added, fieldName);
  }

  private static void addColumn(RowMeta rowMeta, Set<String> added, String fieldName)
      throws HopException {
    addColumn(rowMeta, added, new ValueMetaString(fieldName));
  }

  private static void addColumn(
      RowMeta rowMeta,
      Set<String> added,
      String fieldName,
      ValueMetaInteger valueMetaTemplate)
      throws HopException {
    ValueMetaInteger valueMeta = new ValueMetaInteger(fieldName);
    valueMeta.setLength(DEFAULT_INTEGER_FIELD_LENGTH);
    valueMeta.setPrecision(0);
    addColumn(rowMeta, added, valueMeta);
  }

  private static void addColumn(
      RowMeta rowMeta,
      Set<String> added,
      String fieldName,
      ValueMetaTimestamp valueMetaTemplate)
      throws HopException {
    ValueMetaTimestamp valueMeta = new ValueMetaTimestamp(fieldName);
    addColumn(rowMeta, added, valueMeta);
  }

  private static void addColumn(
      RowMeta rowMeta,
      Set<String> added,
      String fieldName,
      ValueMetaBoolean valueMetaTemplate)
      throws HopException {
    ValueMetaBoolean valueMeta = new ValueMetaBoolean(fieldName);
    addColumn(rowMeta, added, valueMeta);
  }

  private static void addColumn(
      RowMeta rowMeta,
      Set<String> added,
      String fieldName,
      ValueMetaNumber valueMetaTemplate)
      throws HopException {
    ValueMetaNumber valueMeta = new ValueMetaNumber(fieldName);
    addColumn(rowMeta, added, valueMeta);
  }

  private static void addColumn(
      RowMeta rowMeta, Set<String> added, org.apache.hop.core.row.IValueMeta valueMeta)
      throws HopException {
    if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
      return;
    }
    String fieldName = valueMeta.getName();
    if (!added.add(fieldName)) {
      throw new HopException("Duplicate target column '" + fieldName + "' in table layout");
    }
    rowMeta.addValueMeta(valueMeta);
  }

  private static IRowMeta buildFactLikeTargetTableLayout(
      List<DmFactDimensionRole> dimensionRoles,
      List<DmFactMeasure> measures,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    Set<String> added = new HashSet<>();
    appendFactLikeColumns(rowMeta, added, dimensionRoles, measures, model, config, variables);
    return rowMeta;
  }

  private static void appendFactLikeColumns(
      RowMeta rowMeta,
      Set<String> added,
      List<DmFactDimensionRole> dimensionRoles,
      List<DmFactMeasure> measures,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables)
      throws HopException {
    for (DmFactDimensionRole role : dimensionRoles) {
      String fkColumn = resolveFieldName(role.getForeignKeyColumn(), variables);
      if (Utils.isEmpty(fkColumn)) {
        String dimensionName = role.getDimensionTableName();
        if (!Utils.isEmpty(dimensionName) && model != null) {
          DmDimension dmDimension =
              DmDimensionResolutionSupport.resolveDimension(model, dimensionName, variables);
          if (dmDimension != null) {
            DimensionalConfiguration resolvedConfig =
                config != null ? config : new DimensionalConfiguration();
            fkColumn = defaultFactForeignKeyColumn(dmDimension, role, resolvedConfig, variables);
          }
        }
      }
      addColumn(rowMeta, added, fkColumn, new ValueMetaInteger());
    }
    for (DmFactMeasure measure : measures) {
      addColumn(rowMeta, added, resolveFieldName(measure.getFieldName(), variables), new ValueMetaNumber());
    }
  }

  private static boolean dimensionUsesHybridAttributes(DmDimension dimension) {
    if (dimension == null) {
      return false;
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

  private static String resolveFieldName(String fieldName, IVariables variables) {
    if (variables != null) {
      fieldName = variables.resolve(fieldName);
    }
    return fieldName;
  }
}