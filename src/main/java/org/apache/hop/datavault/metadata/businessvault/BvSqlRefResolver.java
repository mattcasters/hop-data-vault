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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Resolves {@code ref()} / {@code source()} macros against Business Vault and Data Vault models and
 * rewrites authoring SQL to dialect-quoted physical names.
 */
public final class BvSqlRefResolver {

  private BvSqlRefResolver() {}

  /**
   * Parses SQL on the business table, resolves each {@code ref()}, and replaces {@link
   * BvBusinessTable#getSqlRefs()}.
   */
  public static List<BvSqlRef> syncRefsFromSql(
      BvBusinessTable table, BusinessVaultModel bvModel, DataVaultModel dvModel) {
    if (table == null) {
      return List.of();
    }
    List<BvSqlRef> refs = BvSqlTemplateParser.extractRefs(table.getSqlQuery());
    for (BvSqlRef ref : refs) {
      resolveRef(ref, table, bvModel, dvModel);
    }
    table.setSqlRefs(refs);
    syncDerivativesFromResolvedRefs(table, refs);
    return refs;
  }

  public static void resolveRef(
      BvSqlRef ref, BvBusinessTable self, BusinessVaultModel bvModel, DataVaultModel dvModel) {
    if (ref == null || Utils.isEmpty(ref.getObjectName())) {
      if (ref != null) {
        ref.setResolvedKind(BvSqlResolvedKind.UNKNOWN);
      }
      return;
    }
    ref.setResolvedKind(BvSqlResolvedKind.UNKNOWN);
    ref.setResolvedTableName(null);
    ref.setResolvedDvTableType(null);
    ref.setResolvedModelFilename(null);

    String objectName = ref.getObjectName().trim();
    String modelName = ref.getModelName();

    if (Utils.isEmpty(modelName)) {
      // Same BV model first, then linked DV.
      IBvTable bvTable = findBvTable(bvModel, objectName, self);
      if (bvTable != null) {
        ref.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
        ref.setResolvedTableName(physicalName(bvTable));
        if (bvModel != null && !Utils.isEmpty(bvModel.getFilename())) {
          ref.setResolvedModelFilename(bvModel.getFilename());
        }
        return;
      }
      IDvTable dvTable = findDvTable(dvModel, objectName);
      if (dvTable != null) {
        ref.setResolvedKind(BvSqlResolvedKind.DV_TABLE);
        ref.setResolvedTableName(physicalName(dvTable));
        ref.setResolvedDvTableType(dvTable.getTableType());
        if (dvModel != null && !Utils.isEmpty(dvModel.getFilename())) {
          ref.setResolvedModelFilename(dvModel.getFilename());
        }
      }
      return;
    }

    // Two-arg ref: match model basename against current BV / linked DV only in MVP.
    String modelKey = modelBasename(modelName);
    if (bvModel != null && modelMatches(bvModel.getFilename(), bvModel.getName(), modelKey)) {
      IBvTable bvTable = findBvTable(bvModel, objectName, self);
      if (bvTable != null) {
        ref.setResolvedKind(BvSqlResolvedKind.BV_TABLE);
        ref.setResolvedTableName(physicalName(bvTable));
        ref.setResolvedModelFilename(bvModel.getFilename());
        return;
      }
    }
    if (dvModel != null && modelMatches(dvModel.getFilename(), dvModel.getName(), modelKey)) {
      IDvTable dvTable = findDvTable(dvModel, objectName);
      if (dvTable != null) {
        ref.setResolvedKind(BvSqlResolvedKind.DV_TABLE);
        ref.setResolvedTableName(physicalName(dvTable));
        ref.setResolvedDvTableType(dvTable.getTableType());
        ref.setResolvedModelFilename(dvModel.getFilename());
      }
    }
  }

  /** Ensures DV derivatives list includes resolved DV sqlRefs for canvas links. */
  public static void syncDerivativesFromResolvedRefs(BvBusinessTable table, List<BvSqlRef> refs) {
    if (table == null || refs == null) {
      return;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null
          || ref.getResolvedKind() != BvSqlResolvedKind.DV_TABLE
          || Utils.isEmpty(ref.getObjectName())
          || ref.getResolvedDvTableType() == null) {
        continue;
      }
      String dvName =
          !Utils.isEmpty(ref.getResolvedTableName())
              ? ref.getResolvedTableName()
              : ref.getObjectName();
      // Prefer object name for derivative identity (matches canvas DV ref names).
      String derivativeName = ref.getObjectName();
      if (!BusinessVaultDerivativeSupport.hasDerivative(table, derivativeName)) {
        table
            .getDerivatives()
            .add(new BvDerivativeRef(derivativeName, ref.getResolvedDvTableType()));
      }
    }
  }

  /**
   * Ensures the BV model has canvas {@link BvDvTableReference} aliases for each resolved DV ref.
   */
  public static void ensureDvCanvasAliases(
      BusinessVaultModel bvModel, List<BvSqlRef> refs, DataVaultModel dvModel) {
    if (bvModel == null || refs == null) {
      return;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null
          || ref.getResolvedKind() != BvSqlResolvedKind.DV_TABLE
          || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      if (BusinessVaultDvReferenceSupport.hasDvReference(bvModel, ref.getObjectName())) {
        continue;
      }
      IDvTable dvTable = findDvTable(dvModel, ref.getObjectName());
      if (dvTable != null) {
        BvDvTableReference alias =
            BusinessVaultDvReferenceSupport.createReference(dvTable, null);
        if (alias != null) {
          bvModel.getDvReferences().add(alias);
        }
      } else if (ref.getResolvedDvTableType() != null) {
        bvModel
            .getDvReferences()
            .add(new BvDvTableReference(ref.getObjectName(), ref.getResolvedDvTableType()));
      }
    }
  }

  public static BvSqlSource findSource(BvBusinessTable table, String sourceName, String tableName) {
    if (table == null || Utils.isEmpty(sourceName) || Utils.isEmpty(tableName)) {
      return null;
    }
    for (BvSqlSource source : table.getSources()) {
      if (source == null) {
        continue;
      }
      if (sourceName.equalsIgnoreCase(source.getSourceName())
          && tableName.equalsIgnoreCase(source.getTableName())) {
        return source;
      }
    }
    return null;
  }

  /**
   * Rewrites authoring SQL replacing macros with quoted schema.table names using BV/DV target
   * connections when available.
   */
  public static String resolveSql(
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DatabaseMeta defaultDatabaseMeta)
      throws HopException {
    if (table == null || Utils.isEmpty(table.getSqlQuery())) {
      return table != null ? table.getSqlQuery() : null;
    }
    List<BvSqlRef> refs = table.getSqlRefs();
    if (refs == null || refs.isEmpty()) {
      refs = syncRefsFromSql(table, bvModel, dvModel);
    } else {
      for (BvSqlRef ref : refs) {
        resolveRef(ref, table, bvModel, dvModel);
      }
    }

    DatabaseMeta bvDb = defaultDatabaseMeta;
    if (bvDb == null && metadataProvider != null && bvModel != null) {
      bvDb =
          BvTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, bvModel.getConfigurationOrDefault());
    }
    DatabaseMeta dvDb = null;
    if (metadataProvider != null && dvModel != null) {
      try {
        dvDb =
            org.apache.hop.datavault.metadata.DvSpecialRecordSupport.loadTargetDatabase(
                metadataProvider, dvModel.getConfigurationOrDefault());
      } catch (HopException ignored) {
        // Fall back to BV database for qualification.
      }
    }

    final DatabaseMeta bvDatabase = bvDb;
    final DatabaseMeta dvDatabase = dvDb != null ? dvDb : bvDb;
    final List<BvSqlRef> resolvedRefs = refs;

    try {
      return BvSqlTemplateParser.rewrite(
          table.getSqlQuery(),
          macro -> rewriteMacro(macro, table, resolvedRefs, bvDatabase, dvDatabase, metadataProvider, variables));
    } catch (SqlRewriteException e) {
      throw new HopException(e.getMessage(), e.getCause());
    }
  }

  private static String rewriteMacro(
      BvSqlTemplateParser.MacroOccurrence macro,
      BvBusinessTable table,
      List<BvSqlRef> resolvedRefs,
      DatabaseMeta bvDatabase,
      DatabaseMeta dvDatabase,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    try {
      if (macro.kind() == BvSqlTemplateParser.MacroKind.REF) {
        BvSqlRef match = findMatchingRef(resolvedRefs, macro);
        String physical =
            match != null && !Utils.isEmpty(match.getResolvedTableName())
                ? match.getResolvedTableName()
                : macro.refObjectName();
        DatabaseMeta db =
            match != null && match.getResolvedKind() == BvSqlResolvedKind.DV_TABLE
                ? dvDatabase
                : bvDatabase;
        return quoteTable(db, variables, null, physical);
      }
      BvSqlSource source = findSource(table, macro.sourceName(), macro.sourceTableName());
      String schema = source != null ? source.getSchemaName() : null;
      String physical =
          source != null && !Utils.isEmpty(source.getTableName())
              ? source.getTableName()
              : macro.sourceTableName();
      DatabaseMeta db = bvDatabase;
      if (source != null
          && !Utils.isEmpty(source.getDatabaseName())
          && metadataProvider != null) {
        DatabaseMeta sourceDb =
            metadataProvider.getSerializer(DatabaseMeta.class).load(source.getDatabaseName());
        if (sourceDb != null) {
          db = sourceDb;
        }
      }
      return quoteTable(db, variables, schema, physical);
    } catch (HopException e) {
      throw new SqlRewriteException(e.getMessage(), e);
    }
  }

  private static final class SqlRewriteException extends RuntimeException {
    SqlRewriteException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static BvSqlRef findMatchingRef(
      List<BvSqlRef> refs, BvSqlTemplateParser.MacroOccurrence macro) {
    if (refs == null) {
      return null;
    }
    String model = macro.refModelName();
    String object = macro.refObjectName();
    for (BvSqlRef ref : refs) {
      if (ref == null || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      if (!ref.getObjectName().equalsIgnoreCase(object)) {
        continue;
      }
      boolean modelEmpty = Utils.isEmpty(model) && Utils.isEmpty(ref.getModelName());
      boolean modelMatch =
          !Utils.isEmpty(model)
              && !Utils.isEmpty(ref.getModelName())
              && model.equalsIgnoreCase(ref.getModelName());
      if (modelEmpty || modelMatch) {
        return ref;
      }
    }
    return null;
  }

  static String quoteTable(
      DatabaseMeta databaseMeta, IVariables variables, String schema, String tableName) {
    if (Utils.isEmpty(tableName)) {
      return tableName;
    }
    if (databaseMeta == null) {
      if (!Utils.isEmpty(schema)) {
        return schema + "." + tableName;
      }
      return tableName;
    }
    return databaseMeta.getQuotedSchemaTableCombination(variables, schema, tableName);
  }

  static IBvTable findBvTable(BusinessVaultModel model, String name, BvBusinessTable excludeSelf) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (excludeSelf != null && table == excludeSelf) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  static IDvTable findDvTable(DataVaultModel model, String name) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    for (IDvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  private static String physicalName(IBvTable table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  private static String physicalName(IDvTable table) {
    if (table == null) {
      return null;
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  static String modelBasename(String modelNameOrPath) {
    if (Utils.isEmpty(modelNameOrPath)) {
      return "";
    }
    String name = modelNameOrPath.trim().replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0 && slash < name.length() - 1) {
      name = name.substring(slash + 1);
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    return name;
  }

  static boolean modelMatches(String filename, String modelName, String key) {
    if (Utils.isEmpty(key)) {
      return false;
    }
    if (!Utils.isEmpty(modelName) && key.equalsIgnoreCase(modelBasename(modelName))) {
      return true;
    }
    return !Utils.isEmpty(filename) && key.equalsIgnoreCase(modelBasename(filename));
  }

  /** Returns unresolved ref descriptions for validation messages. */
  public static List<String> listUnresolvedRefLabels(List<BvSqlRef> refs) {
    List<String> labels = new ArrayList<>();
    if (refs == null) {
      return labels;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null) {
        continue;
      }
      if (ref.getResolvedKind() == null || ref.getResolvedKind() == BvSqlResolvedKind.UNKNOWN) {
        if (!Utils.isEmpty(ref.getModelName())) {
          labels.add("ref('" + ref.getModelName() + "', '" + ref.getObjectName() + "')");
        } else {
          labels.add("ref('" + ref.getObjectName() + "')");
        }
      }
    }
    return labels;
  }
}
