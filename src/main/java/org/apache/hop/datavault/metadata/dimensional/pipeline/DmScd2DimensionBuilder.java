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

package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.Condition;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.update.UpdateField;
import org.apache.hop.pipeline.transforms.update.UpdateKeyField;
import org.apache.hop.pipeline.transforms.update.UpdateLookupField;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;

/** Generates SCD Type 2 dimension pipelines using the proven satellite MergeRows pattern. */
public final class DmScd2DimensionBuilder {

  private static final Point LOCATION_TARGET = new Point(100, 280);

  private DmScd2DimensionBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmDimension dimension,
      Date loadTimestamp)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, dimension);
    if (ctx == null) {
      throw new HopException("Unable to create build context for dimension " + dimension.getName());
    }
    if (loadTimestamp == null) {
      loadTimestamp = new Date();
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceTableInput(ctx, pipelineMeta);
    TransformMeta sortTransform = addSortNaturalKeys(ctx, pipelineMeta, sourceTransform, dimension);
    TransformMeta compareTransform =
        addEffectiveDateConstants(ctx, pipelineMeta, loadTimestamp, sortTransform);
    TransformMeta targetTransform = addTargetTableInput(ctx, pipelineMeta, dimension);
    TransformMeta mergeTransform =
        addMergeRows(ctx, pipelineMeta, compareTransform, targetTransform, dimension);
    TransformMeta filterTransform = addFilterNewOrChanged(pipelineMeta, mergeTransform);
    IRowMeta targetLayout = dimension.getTargetTableLayout(metadataProvider, variables, model);
    TransformMeta tableOutputTransform =
        DmPipelineBuilderSupport.addTableOutput(
            ctx, pipelineMeta, targetLayout, filterTransform, false);
    TransformMeta filterPreviousTransform =
        addFilterHasPreviousVersion(pipelineMeta, tableOutputTransform);
    addClosePreviousVersion(ctx, pipelineMeta, filterPreviousTransform, dimension);

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  private static TransformMeta addSortNaturalKeys(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    for (String naturalKey : DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      SortRowsField sortField = new SortRowsField();
      sortField.setFieldName(naturalKey);
      sortField.setAscending(true);
      sortRowsMeta.getSortFields().add(sortField);
    }

    TransformMeta tm =
        new TransformMeta("SortRows", "sort_" + ctx.targetTableName, sortRowsMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addEffectiveDateConstants(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      Date loadTimestamp,
      TransformMeta predecessor)
      throws HopException {
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);
    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    String openEnd = ctx.config.getOpenEndSentinel();
    if (ctx.variables != null) {
      openEnd = ctx.variables.resolve(openEnd);
    }

    ValueMetaDate valueMeta = new ValueMetaDate("ld");
    valueMeta.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    String loadDateString = valueMeta.getString(loadTimestamp);

    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField fromField = new ConstantField(dateFromField, "Date", loadDateString);
    fromField.setFieldFormat(valueMeta.getConversionMask());
    constantMeta.getFields().add(fromField);

    ConstantField toField = new ConstantField(dateToField, "String", openEnd);
    constantMeta.getFields().add(toField);

    ConstantField loadField = new ConstantField(loadDateField, "Date", loadDateString);
    loadField.setFieldFormat(valueMeta.getConversionMask());
    constantMeta.getFields().add(loadField);

    TransformMeta tm =
        new TransformMeta("Constant", "add_effective_dates", constantMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addTargetTableInput(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      DmDimension dimension) {
    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String openEnd = ctx.config.getOpenEndSentinel();
    if (ctx.variables != null) {
      openEnd = ctx.variables.resolve(openEnd);
    }

    List<String> selectFields = new ArrayList<>();
    for (String attribute : DmPipelineBuilderSupport.scd2AttributeFieldNames(dimension, ctx.variables)) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(attribute));
    }
    for (String naturalKey : DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(naturalKey));
    }
    selectFields.add(ctx.targetDatabaseMeta.quoteField(dateToField));

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    sql.append(" WHERE ");
    sql.append(ctx.targetDatabaseMeta.quoteField(dateToField));
    sql.append(" = '");
    sql.append(openEnd.replace("'", "''"));
    sql.append("'");

    DvSqlSupport.assignDisplaySql(targetTableInputMeta, sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", "target_" + ctx.targetTableName, targetTableInputMeta);
    tm.setLocation(LOCATION_TARGET.x, LOCATION_TARGET.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addMergeRows(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform,
      DmDimension dimension) {
    TransformMeta referenceDummy =
        DmPipelineBuilderSupport.addDummyTransform(
            pipelineMeta,
            referenceTransform,
            "merge_reference",
            LOCATION_TARGET.x + DmPipelineBuilderSupport.SPACING_WIDTH,
            LOCATION_TARGET.y);
    TransformMeta compareDummy =
        DmPipelineBuilderSupport.addDummyTransform(
            pipelineMeta,
            compareTransform,
            "merge_compare",
            compareTransform.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
            compareTransform.getLocation().y);

    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceDummy.getName());
    mergeRowsMeta.setCompareTransform(compareDummy.getName());
    mergeRowsMeta.setFlagField("flag");
    mergeRowsMeta.setKeyFields(DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables));
    mergeRowsMeta
        .getValueFields()
        .addAll(DmPipelineBuilderSupport.scd2AttributeFieldNames(dimension, ctx.variables));

    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);
    mergeRowsMeta
        .getPassThroughFields()
        .add(
            new PassThroughField(
                dateFromField, DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD, true));

    TransformMeta tm = new TransformMeta("MergeRows", "merge_diff", mergeRowsMeta);
    tm.setLocation(
        compareTransform.getLocation().x + 2 * DmPipelineBuilderSupport.SPACING_WIDTH,
        compareTransform.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceDummy, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareDummy, tm));
    return tm;
  }

  private static TransformMeta addFilterNewOrChanged(
      PipelineMeta pipelineMeta, TransformMeta mergeTransform) throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition newCondition =
          new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), "new"));
      Condition changedCondition =
          new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("changed"), "changed"));
      changedCondition.setOperator(Condition.Operator.OR);

      Condition condition = new Condition();
      condition.addCondition(newCondition);
      condition.addCondition(changedCondition);
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating SCD2 filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "new_or_changed", filterRowsMeta);
    tm.setLocation(
        mergeTransform.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        mergeTransform.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, tm));
    return tm;
  }

  private static TransformMeta addFilterHasPreviousVersion(
      PipelineMeta pipelineMeta, TransformMeta predecessor) throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    Condition condition =
        new Condition(
            DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD,
            Condition.Function.NOT_NULL,
            null,
            null);
    filterRowsMeta.getCompare().setCondition(condition);

    TransformMeta tm =
        new TransformMeta("FilterRows", "has_previous_version", filterRowsMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addClosePreviousVersion(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    if (predecessor == null) {
      return null;
    }

    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);

    UpdateMeta updateMeta = new UpdateMeta();
    updateMeta.setConnection(ctx.targetDbName);

    UpdateLookupField lookup = new UpdateLookupField();
    lookup.setTableName(ctx.targetTableName);
    for (String naturalKey : DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      lookup.getLookupKeys().add(new UpdateKeyField(naturalKey, naturalKey, "="));
    }
    lookup
        .getLookupKeys()
        .add(
            new UpdateKeyField(
                DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD, dateFromField, "="));
    lookup.getUpdateFields().add(new UpdateField(dateToField, dateFromField));
    updateMeta.setLookupField(lookup);

    TransformMeta tm =
        new TransformMeta("Update", "close_" + ctx.targetTableName, updateMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }
}