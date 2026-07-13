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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/**
 * SQL-sourced business table materialised as a database view or table ({@code CREATE OR REPLACE
 * VIEW|TABLE … AS query}) with dbt-style {@code ref()} / {@code source()} templates.
 */
@Getter
@Setter
public class BvBusinessTable extends BvTableBase {

  private static final Class<?> PKG = BvBusinessTable.class;

  /** Authoring SQL containing optional {@code {{ ref(...) }}} / {@code {{ source(...) }}} macros. */
  @HopMetadataProperty private String sqlQuery;

  @HopMetadataProperty(storeWithCode = true)
  private BvSqlReferenceStyle referenceStyle = BvSqlReferenceStyle.DBT;

  @HopMetadataProperty(storeWithCode = true)
  private BvSqlMaterialization materialization = BvSqlMaterialization.VIEW;

  @HopMetadataProperty(key = "sql_source", groupKey = "sql_sources")
  private List<BvSqlSource> sources = new ArrayList<>();

  @HopMetadataProperty(key = "sql_ref", groupKey = "sql_refs")
  private List<BvSqlRef> sqlRefs = new ArrayList<>();

  public BvBusinessTable() {
    super(BvTableType.BUSINESS_TABLE);
  }

  public List<BvSqlSource> getSources() {
    if (sources == null) {
      sources = new ArrayList<>();
    }
    return sources;
  }

  public List<BvSqlRef> getSqlRefs() {
    if (sqlRefs == null) {
      sqlRefs = new ArrayList<>();
    }
    return sqlRefs;
  }

  public BvSqlReferenceStyle getReferenceStyleOrDefault() {
    return referenceStyle != null ? referenceStyle : BvSqlReferenceStyle.DBT;
  }

  public BvSqlMaterialization getMaterializationOrDefault() {
    return materialization != null ? materialization : BvSqlMaterialization.VIEW;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);

    if (Utils.isEmpty(sqlQuery)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvBusinessTable.CheckResult.MissingSql", getName()),
              this));
      return;
    }

    if (getReferenceStyleOrDefault() != BvSqlReferenceStyle.DBT) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvBusinessTable.CheckResult.UnsupportedReferenceStyle", getName()),
              this));
    }

    List<BvSqlRef> refs = BvSqlRefResolver.syncRefsFromSql(this, model, dataVaultModel);
    List<String> unresolved = BvSqlRefResolver.listUnresolvedRefLabels(refs);
    for (String label : unresolved) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvBusinessTable.CheckResult.UnresolvedRef", getName(), label),
              this));
    }

    List<BvSqlTemplateParser.MacroOccurrence> macros = BvSqlTemplateParser.parse(sqlQuery);
    Set<String> usedSources = new HashSet<>();
    for (BvSqlTemplateParser.MacroOccurrence macro : macros) {
      if (macro.kind() != BvSqlTemplateParser.MacroKind.SOURCE) {
        continue;
      }
      String key =
          macro.sourceName().trim().toLowerCase()
              + "\0"
              + macro.sourceTableName().trim().toLowerCase();
      usedSources.add(key);
      BvSqlSource declared =
          BvSqlRefResolver.findSource(this, macro.sourceName(), macro.sourceTableName());
      if (declared == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvBusinessTable.CheckResult.MissingSourceDeclaration",
                    getName(),
                    macro.sourceName(),
                    macro.sourceTableName()),
                this));
      } else if (Utils.isEmpty(declared.getTableName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvBusinessTable.CheckResult.IncompleteSource",
                    getName(),
                    macro.sourceName()),
                this));
      }
    }

    for (BvSqlSource source : getSources()) {
      if (source == null || Utils.isEmpty(source.getSourceName())) {
        continue;
      }
      String key =
          source.getSourceName().trim().toLowerCase()
              + "\0"
              + (source.getTableName() != null ? source.getTableName().trim().toLowerCase() : "");
      if (!usedSources.contains(key)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "BvBusinessTable.CheckResult.UnusedSource",
                    getName(),
                    source.getSourceName(),
                    source.getTableName()),
                this));
      }
    }

    if (model != null) {
      String cycle = BvSqlDependencySupport.findCycleDescription(model.getTables());
      if (cycle != null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BvBusinessTable.CheckResult.SqlRefCycle", getName(), cycle),
                this));
      }
    }
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvSqlViewPipelineSupport.generateBuildPipelines(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    // CREATE OR REPLACE VIEW|TABLE runs in the build pipeline after dependent SCD2/PIT loads.
    return List.of();
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    // Layout is defined by the SQL query result; no static column model in MVP.
    return new RowMeta();
  }
}
