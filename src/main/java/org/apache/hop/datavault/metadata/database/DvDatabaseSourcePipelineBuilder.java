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

package org.apache.hop.datavault.metadata.database;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.jspecify.annotations.NonNull;

@Getter
@Setter
public abstract class DvDatabaseSourcePipelineBuilder extends DvSourcePipelineBuilder {
  protected DvDatabaseSource source;
  protected DatabaseMeta sourceDbMeta;

  protected DvDatabaseSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
    source = (DvDatabaseSource) dvSource;
  }

  @Override
  public void build() throws HopException {
    sourceDbMeta = loadDatabaseMeta(variables.resolve(source.getDatabaseName()));

    // We're mainly reading the business keys from the source database
    //
    String querySql = getSql();

    // We can generate a Table Input transform for this.
    //
    Point location = new Point(startPoint.x, startPoint.y);
    String sourceTransformName = calculateTransformName("source ", source);
    TransformMeta sourceTransformMeta =
        createTableInput(sourceTransformName, sourceDbMeta, querySql, location);
    pipelineMeta.addTransform(sourceTransformMeta);

    // We're done with this part for the database table source.
    // Inform the rest of the code where the next transform can connect to:
    //
    resultTransform = sourceTransformMeta;
  }

  protected abstract String getSql() throws HopException;

  protected void appendComma(StringBuilder sql) {
    sql.append(", ");
  }

  protected void appendFields(StringBuilder sql, List<String> quotedFields) {
    sql.append(String.join(", ", quotedFields));
  }

  protected @NonNull List<String> getQuotedPkFields(DvHub hub, DatabaseMeta sourceDbMeta)
      throws HopException {
    if (hub.getBusinessKeys().isEmpty()) {
      throw new HopException("Please specify at least one business key in Hub " + hub.getName());
    }
    List<String> pkQuotedFields = new ArrayList<>();

    for (BusinessKey key : hub.getBusinessKeys()) {
      if (StringUtils.isNotEmpty(key.getSourceFieldName())) {
        String quotedPk = sourceDbMeta.quoteField(variables.resolve(key.getSourceFieldName()));
        pkQuotedFields.add(quotedPk);
      }
    }
    return pkQuotedFields;
  }

  protected void appendOrderByPk(StringBuilder sql, List<String> pkQuotedFields) {
    if (!pkQuotedFields.isEmpty()) {
      sql.append(" ORDER BY ").append(String.join(", ", pkQuotedFields));
    }
  }

  protected void appendFrom(DatabaseMeta sourceDbMeta, DvDatabaseSource source, StringBuilder sql) {
    String schemaTable =
        sourceDbMeta.getQuotedSchemaTableCombination(
            variables, source.getSchemaName(), source.getTableName());

    sql.append(" FROM ");
    sql.append(schemaTable);
  }

  protected void appendSourceField(IDvTable table, StringBuilder sql, DatabaseMeta sourceDbMeta)
      throws HopException {
    // Add source indicator (record source) column.
    // First we need to determine the target name of this record source column.
    //
    String targetSourceFieldName = findTargetSourceFieldName(table);

    // Now we need to determine the actual value of the source
    //
    // - If there is a static sourceIndicator in DataVaultSource: use resolved constant value,
    // aliased.
    // - If there is a sourceIndicatorField in DataVaultSource: include the source column, aliased
    // to the (per-hub or
    // config) record source field name.
    //
    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      sql.append(sourceDbMeta.quoteField(sourceFieldName));
    } else {
      // We pick a static value
      //
      String sourceIndicator = recordSource.getSourceIndicator();
      if (StringUtils.isEmpty(sourceIndicator)) {
        throw new HopException(
            "Please specify a static source indicator or a field in source "
                + recordSource.getName());
      }
      sql.append("'").append(sourceIndicator).append("'");
    }
    sql.append(" AS ").append(sourceDbMeta.quoteField(targetSourceFieldName));
  }

  protected @NonNull String findTargetSourceFieldName(IDvTable table) throws HopException {
    String targetSourceFieldName =
        switch (table.getTableType()) {
          case HUB -> ((DvHub) table).getRecordSourceFieldName();
          case SATELLITE -> null; // TODO: should we add a record source column to satellites?
          case LINK -> ((DvLink) table).getRecordSourceFieldName();
        };
    if (StringUtils.isEmpty(targetSourceFieldName)) {
      // If we didn't find it in the table we need to look in the configuration.
      targetSourceFieldName = configuration.getRecordSourceField();
    }
    if (StringUtils.isEmpty(targetSourceFieldName)) {
      throw new HopException(
          "Please specify a source field name in either the table or the configuration for Hub "
              + table.getName());
    }
    return targetSourceFieldName;
  }

  protected String calculateTransformName(String prefix, DvDatabaseSource source) {
    StringBuilder name = new StringBuilder();
    name.append(prefix);
    name.append(" ");
    name.append(source.getDatabaseName());
    name.append(".");
    if (StringUtils.isNotEmpty(source.getSchemaName())) {
      name.append(source.getSchemaName());
      name.append(".");
    }
    name.append(source.getTableName());

    return name.toString();
  }

  protected TransformMeta createTableInput(
      String sourceTransformName, DatabaseMeta sourceDbMeta, String querySql, Point location) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(sourceDbMeta.getName());
    tableInputMeta.setSql(querySql);

    TransformMeta transformMeta =
        new TransformMeta("TableInput", sourceTransformName, tableInputMeta);
    transformMeta.setLocation(location.x, location.y);

    return transformMeta;
  }
}
