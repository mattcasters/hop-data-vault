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

package org.apache.hop.datavault.metadata.dimensional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Kimball validation rules for dimensional model tables. */
public final class DmValidationSupport {

  private static final Class<?> PKG = DmValidationSupport.class;

  private DmValidationSupport() {}

  public static void validateConfiguration(
      List<ICheckResult> remarks,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || model == null) {
      return;
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    if (Utils.isEmpty(config.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DmValidationSupport.CheckResult.MissingTargetDatabase"),
              null));
    } else if (metadataProvider != null) {
      try {
        DmTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), null));
      }
    }
    validateConformedDimensions(remarks, model, variables);
  }

  public static void validateJunkDimension(
      List<ICheckResult> remarks,
      DmJunkDimension junkDimension,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || junkDimension == null) {
      return;
    }
    if (junkDimension.getKeyFieldsOrEmpty().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingJunkKeyFields", junkDimension.getName()),
              junkDimension));
    }
    validateSourceConfiguration(remarks, junkDimension, variables);
    validateTargetLayout(remarks, junkDimension, null, metadataProvider, variables);
  }

  public static void validateFactlessFact(
      List<ICheckResult> remarks,
      DmFactlessFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    validateDimensionRoles(remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateSourceConfiguration(remarks, fact, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validatePeriodicSnapshotFact(
      List<ICheckResult> remarks,
      DmPeriodicSnapshotFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    if (Utils.isEmpty(resolve(fact.getSnapshotDateField(), variables))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingSnapshotDateField", fact.getName()),
              fact));
    }
    validateDimensionRoles(remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateMeasures(remarks, fact, variables);
    validateSourceConfiguration(remarks, fact, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validateAccumulatingSnapshotFact(
      List<ICheckResult> remarks,
      DmAccumulatingSnapshotFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    if (fact.getGrainKeysOrEmpty().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingGrainKeys", fact.getName()),
              fact));
    }
    validateDimensionRoles(remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateMeasures(remarks, fact, variables);
    validateSourceConfiguration(remarks, fact, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validateBridge(
      List<ICheckResult> remarks,
      DmBridge bridge,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || bridge == null) {
      return;
    }
    List<DmBridgeDimensionRef> refs = bridge.getDimensionRefsOrEmpty();
    if (refs.size() < 2) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingBridgeDimensions", bridge.getName()),
              bridge));
    }
    Set<String> fkColumns = new HashSet<>();
    for (DmBridgeDimensionRef ref : refs) {
      if (ref == null) {
        continue;
      }
      String dimensionName = resolve(ref.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingBridgeDimension",
                    bridge.getName()),
                bridge));
        continue;
      }
      IDmTable target = model != null ? model.findTable(dimensionName) : null;
      if (!(target instanceof DmDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownBridgeDimension",
                    bridge.getName(),
                    dimensionName),
                bridge));
      }
      String fkColumn = resolve(ref.getForeignKeyColumn(), variables);
      if (!Utils.isEmpty(fkColumn) && !fkColumns.add(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateBridgeForeignKey",
                    bridge.getName(),
                    fkColumn),
                bridge));
      }
    }
    validateSourceConfiguration(remarks, bridge, variables);
    validateTargetLayout(remarks, bridge, model, metadataProvider, variables);
  }

  public static void validateAggregateFact(
      List<ICheckResult> remarks,
      DmAggregateFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    String baseFact = resolve(fact.getBaseFactTableName(), variables);
    if (Utils.isEmpty(baseFact)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingBaseFact", fact.getName()),
              fact));
    } else if (model != null && model.findTable(baseFact) == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DmValidationSupport.CheckResult.UnknownBaseFact",
                  fact.getName(),
                  baseFact),
              fact));
    }
    validateDimensionRoles(remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
    validateMeasures(remarks, fact, variables);
    validateSourceConfiguration(remarks, fact, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  public static void validateConformedDimensions(
      List<ICheckResult> remarks, DimensionalModel model, IVariables variables) {
    if (remarks == null || model == null) {
      return;
    }
    Set<String> logicalNames = new HashSet<>();
    for (DmConformedDimensionRef ref : model.getConformedDimensionsOrEmpty()) {
      if (ref == null) {
        continue;
      }
      String logicalName = resolve(ref.getLogicalName(), variables);
      if (Utils.isEmpty(logicalName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "DmValidationSupport.CheckResult.MissingConformedLogicalName"),
                null));
        continue;
      }
      if (!logicalNames.add(logicalName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateConformedLogicalName",
                    logicalName),
                null));
      }
      String dimensionName = resolve(ref.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingConformedDimension",
                    logicalName),
                null));
        continue;
      }
      IDmTable table = model.findTable(dimensionName);
      if (!(table instanceof DmDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownConformedDimension",
                    logicalName,
                    dimensionName),
                null));
      }
    }
  }

  public static void validateDimension(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || dimension == null) {
      return;
    }
    validateNaturalKeys(remarks, dimension, variables);
    validateAttributes(remarks, dimension, variables);
    validateOutriggers(remarks, dimension, model, variables);
    validateSourceConfiguration(remarks, dimension, variables);
    validateTargetLayout(remarks, dimension, model, metadataProvider, variables);
  }

  public static void validateFact(
      List<ICheckResult> remarks,
      DmFact fact,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || fact == null) {
      return;
    }
    validateDimensionRoles(remarks, fact, model, variables);
    validateMeasures(remarks, fact, variables);
    validateSourceConfiguration(remarks, fact, variables);
    validateTargetLayout(remarks, fact, model, metadataProvider, variables);
  }

  private static void validateNaturalKeys(
      List<ICheckResult> remarks, DmDimension dimension, IVariables variables) {
    List<DmNaturalKeyField> naturalKeys = dimension.getNaturalKeysOrEmpty();
    if (naturalKeys.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingNaturalKeys", dimension.getName()),
              dimension));
      return;
    }
    Set<String> names = new HashSet<>();
    for (DmNaturalKeyField naturalKey : naturalKeys) {
      String fieldName = resolve(naturalKey != null ? naturalKey.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyNaturalKeyField",
                    dimension.getName()),
                dimension));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateNaturalKeyField",
                    dimension.getName(),
                    fieldName),
                dimension));
      }
    }
  }

  private static void validateAttributes(
      List<ICheckResult> remarks, DmDimension dimension, IVariables variables) {
    Set<String> names = new HashSet<>();
    for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
      String fieldName = resolve(naturalKey != null ? naturalKey.getFieldName() : null, variables);
      if (!Utils.isEmpty(fieldName)) {
        names.add(fieldName);
      }
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      String fieldName = resolve(attribute != null ? attribute.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.EmptyAttributeField",
                    dimension.getName()),
                dimension));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateAttributeField",
                    dimension.getName(),
                    fieldName),
                dimension));
      }
    }
  }

  private static void validateOutriggers(
      List<ICheckResult> remarks,
      DmDimension dimension,
      DimensionalModel model,
      IVariables variables) {
    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      if (outrigger == null) {
        continue;
      }
      String dimensionName = resolve(outrigger.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingOutriggerDimension",
                    dimension.getName()),
                dimension));
        continue;
      }
      IDmTable target = model != null ? model.findTable(dimensionName) : null;
      if (!(target instanceof DmDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownOutriggerDimension",
                    dimension.getName(),
                    dimensionName),
                dimension));
      }
      String fkColumn = resolve(outrigger.getForeignKeyColumn(), variables);
      if (Utils.isEmpty(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingOutriggerForeignKey",
                    dimension.getName(),
                    dimensionName),
                dimension));
      }
    }
  }

  private static void validateDimensionRoles(
      List<ICheckResult> remarks, DmFact fact, DimensionalModel model, IVariables variables) {
    validateDimensionRoles(
        remarks, fact.getName(), fact.getDimensionRolesOrEmpty(), model, variables, fact);
  }

  private static void validateDimensionRoles(
      List<ICheckResult> remarks,
      String tableName,
      List<DmFactDimensionRole> roles,
      DimensionalModel model,
      IVariables variables,
      IDmTable source) {
    if (roles.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingDimensionRoles", tableName),
              source));
      return;
    }
    Set<String> fkColumns = new HashSet<>();
    Set<String> roleNames = new HashSet<>();
    for (DmFactDimensionRole role : roles) {
      if (role == null) {
        continue;
      }
      String dimensionName = resolve(role.getDimensionTableName(), variables);
      if (Utils.isEmpty(dimensionName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.MissingRoleDimension",
                    tableName),
                source));
        continue;
      }
      IDmTable target = model != null ? model.findTable(dimensionName) : null;
      if (!(target instanceof DmDimension)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.UnknownRoleDimension",
                    tableName,
                    dimensionName),
                source));
      }
      String roleName = resolve(role.getRoleName(), variables);
      if (!Utils.isEmpty(roleName) && !roleNames.add(roleName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateRoleName",
                    tableName,
                    roleName),
                source));
      }
      String fkColumn = resolve(role.getForeignKeyColumn(), variables);
      if (!Utils.isEmpty(fkColumn) && !fkColumns.add(fkColumn)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateForeignKeyColumn",
                    tableName,
                    fkColumn),
                source));
      }
    }
  }

  private static void validateMeasures(
      List<ICheckResult> remarks, IDmFactLikeTable factLike, IVariables variables) {
    if (remarks == null || factLike == null) {
      return;
    }
    List<DmFactMeasure> measures = factLike.getMeasuresOrEmpty();
    if (measures.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingMeasures", factLike.getName()),
              factLike));
      return;
    }
    Set<String> names = new HashSet<>();
    for (DmFactMeasure measure : measures) {
      String fieldName = resolve(measure != null ? measure.getFieldName() : null, variables);
      if (Utils.isEmpty(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DmValidationSupport.CheckResult.EmptyMeasureField", factLike.getName()),
                factLike));
        continue;
      }
      if (!names.add(fieldName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DmValidationSupport.CheckResult.DuplicateMeasureField",
                    factLike.getName(),
                    fieldName),
                factLike));
      }
    }
  }

  private static void validateSourceConfiguration(
      List<ICheckResult> remarks, IDmTable table, IVariables variables) {
    if (remarks == null || table == null) {
      return;
    }
    String sourceSql = table.getSourceOrDefault().resolveSourceSql(variables);
    if (Utils.isEmpty(sourceSql)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmValidationSupport.CheckResult.MissingSourceSql", table.getName()),
              table));
    }
  }

  private static void validateTargetLayout(
      List<ICheckResult> remarks,
      IDmTable table,
      DimensionalModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (table == null) {
      return;
    }
    try {
      table.getTargetTableLayout(metadataProvider, variables, model);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), table));
    }
  }

  private static String resolve(String value, IVariables variables) {
    if (variables != null) {
      value = variables.resolve(value);
    }
    return value;
  }
}