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

package org.apache.hop.datavault.metrics;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvBulkLoadPluginSupport;

/** Creates {@code dv_ops} load-metrics tables on first publish when they are missing. */
public final class LoadRunMetricsDdlSupport {

  private LoadRunMetricsDdlSupport() {}

  public static void ensureMetricsTables(
      Database db, DatabaseMeta databaseMeta, ILogChannel log) throws HopException {
    ensureMetricsTables(
        db, databaseMeta, LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME, log);
  }

  public static void ensureMetricsTables(
      Database db, DatabaseMeta databaseMeta, String operationsSchema, ILogChannel log)
      throws HopException {
    if (db == null || databaseMeta == null) {
      return;
    }
    String schema = resolveSchema(operationsSchema);
    if (allMetricsTablesExist(db, schema)) {
      ensureLoadRunPipelineRunConfigurationColumn(db, schema, databaseMeta, log);
      ensureLoadRunWorkflowExecutionIdColumn(db, schema, databaseMeta, log);
      WorkflowLoadOverviewDdlSupport.ensureOverviewTables(db, databaseMeta, schema, log);
      return;
    }

    String ddl = String.join(";\n", buildCreateStatements(databaseMeta, schema)) + ";";
    if (log != null) {
      log.logBasic(
          "Creating load-run metrics tables in " + schema + " on " + databaseMeta.getName());
    }
    db.execStatements(ddl);
    WorkflowLoadOverviewDdlSupport.ensureOverviewTables(db, databaseMeta, schema, log);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta) {
    return buildCreateStatements(databaseMeta, LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta, String operationsSchema) {
    String schema = resolveSchema(operationsSchema);
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase()
            : "";
    return switch (pluginId) {
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
          DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID -> mysqlStatements(schema);
      default -> postgresStatements(schema);
    };
  }

  private static boolean allMetricsTablesExist(Database db, String schema) throws HopException {
    return db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)
        && db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC)
        && db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC)
        && db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT);
  }

  private static String resolveSchema(String operationsSchema) {
    if (Utils.isEmpty(operationsSchema)) {
      return LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME;
    }
    return operationsSchema.trim();
  }

  private static List<String> postgresStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add("CREATE SCHEMA IF NOT EXISTS " + schema);
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id           VARCHAR(64)  NOT NULL,
          started_at       TIMESTAMP    NULL,
          finished_at      TIMESTAMP    NULL,
          model_type       VARCHAR(16)  NULL,
          model_name       VARCHAR(255) NULL,
          workflow_name    VARCHAR(255) NULL,
          workflow_execution_id     VARCHAR(64)  NULL,
          log_channel_id              VARCHAR(64)  NULL,
          pipeline_run_configuration  VARCHAR(255) NULL,
          success                     BOOLEAN      NULL,
          error_count                 BIGINT       NULL,
          PRIMARY KEY (run_id)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id                 VARCHAR(64)  NOT NULL,
          pipeline_name          VARCHAR(255) NOT NULL,
          element_type           VARCHAR(64)  NULL,
          element_name           VARCHAR(255) NULL,
          source_name            VARCHAR(255) NULL,
          source_rows_read       BIGINT       NULL,
          target_rows_read       BIGINT       NULL,
          target_rows_inserted   BIGINT       NULL,
          errors                 BIGINT       NULL,
          PRIMARY KEY (run_id, pipeline_name)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id                VARCHAR(64)  NOT NULL,
          pipeline_name         VARCHAR(255) NOT NULL,
          transform_name        VARCHAR(255) NOT NULL,
          logical_role          VARCHAR(64)  NULL,
          element_type          VARCHAR(64)  NULL,
          element_name          VARCHAR(255) NULL,
          parent_element_name   VARCHAR(255) NULL,
          rows_read             BIGINT       NULL,
          rows_written          BIGINT       NULL,
          rows_updated          BIGINT       NULL,
          rows_rejected         BIGINT       NULL,
          errors                BIGINT       NULL,
          duration_ms           BIGINT       NULL,
          PRIMARY KEY (run_id, pipeline_name, transform_name)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id               VARCHAR(64)  NOT NULL,
          insight_seq          BIGINT       NOT NULL,
          severity             VARCHAR(16)  NULL,
          code                 VARCHAR(64)  NULL,
          message              VARCHAR(2000) NULL,
          element_name         VARCHAR(255) NULL,
          related_element_name VARCHAR(255) NULL,
          metric_json          VARCHAR(4000) NULL,
          PRIMARY KEY (run_id, insight_seq)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT));
    return statements;
  }

  private static List<String> mysqlStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add("CREATE DATABASE IF NOT EXISTS " + schema);
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id           VARCHAR(64)  NOT NULL,
          started_at       TIMESTAMP    NULL,
          finished_at      TIMESTAMP    NULL,
          model_type       VARCHAR(16)  NULL,
          model_name       VARCHAR(255) NULL,
          workflow_name    VARCHAR(255) NULL,
          workflow_execution_id     VARCHAR(64)  NULL,
          log_channel_id              VARCHAR(64)  NULL,
          pipeline_run_configuration  VARCHAR(255) NULL,
          success                     TINYINT(1)   NULL,
          error_count                 BIGINT       NULL,
          PRIMARY KEY (run_id)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id                 VARCHAR(64)  NOT NULL,
          pipeline_name          VARCHAR(255) NOT NULL,
          element_type           VARCHAR(64)  NULL,
          element_name           VARCHAR(255) NULL,
          source_name            VARCHAR(255) NULL,
          source_rows_read       BIGINT       NULL,
          target_rows_read       BIGINT       NULL,
          target_rows_inserted   BIGINT       NULL,
          errors                 BIGINT       NULL,
          PRIMARY KEY (run_id, pipeline_name)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id                VARCHAR(64)  NOT NULL,
          pipeline_name         VARCHAR(255) NOT NULL,
          transform_name        VARCHAR(255) NOT NULL,
          logical_role          VARCHAR(64)  NULL,
          element_type          VARCHAR(64)  NULL,
          element_name          VARCHAR(255) NULL,
          parent_element_name   VARCHAR(255) NULL,
          rows_read             BIGINT       NULL,
          rows_written          BIGINT       NULL,
          rows_updated          BIGINT       NULL,
          rows_rejected         BIGINT       NULL,
          errors                BIGINT       NULL,
          duration_ms           BIGINT       NULL,
          PRIMARY KEY (run_id, pipeline_name, transform_name)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s.%s (
          run_id               VARCHAR(64)  NOT NULL,
          insight_seq          BIGINT       NOT NULL,
          severity             VARCHAR(16)  NULL,
          code                 VARCHAR(64)  NULL,
          message              VARCHAR(2000) NULL,
          element_name         VARCHAR(255) NULL,
          related_element_name VARCHAR(255) NULL,
          metric_json          VARCHAR(4000) NULL,
          PRIMARY KEY (run_id, insight_seq)
        )"""
            .formatted(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT));
    return statements;
  }

  static void ensureLoadRunPipelineRunConfigurationColumn(
      Database db, String schema, DatabaseMeta databaseMeta, ILogChannel log) throws HopException {
    if (db == null || databaseMeta == null) {
      return;
    }
    String resolvedSchema = resolveSchema(schema);
    if (!db.checkTableExists(resolvedSchema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)) {
      return;
    }
    if (db.checkColumnExists(
        resolvedSchema,
        LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN,
        "pipeline_run_configuration")) {
      return;
    }
    String qualifiedTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, resolvedSchema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
    String alterSql = "ALTER TABLE " + qualifiedTable + " ADD pipeline_run_configuration VARCHAR(255) NULL";
    if (log != null) {
      log.logBasic(
          "Adding pipeline_run_configuration column to "
              + resolvedSchema
              + "."
              + LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
    }
    db.execStatement(alterSql);
  }

  static void ensureLoadRunWorkflowExecutionIdColumn(
      Database db, String schema, DatabaseMeta databaseMeta, ILogChannel log) throws HopException {
    if (db == null || databaseMeta == null) {
      return;
    }
    String resolvedSchema = resolveSchema(schema);
    if (!db.checkTableExists(resolvedSchema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)) {
      return;
    }
    if (db.checkColumnExists(
        resolvedSchema,
        LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN,
        "workflow_execution_id")) {
      return;
    }
    String qualifiedTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, resolvedSchema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
    String alterSql = "ALTER TABLE " + qualifiedTable + " ADD workflow_execution_id VARCHAR(64) NULL";
    if (log != null) {
      log.logBasic(
          "Adding workflow_execution_id column to "
              + resolvedSchema
              + "."
              + LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
    }
    db.execStatement(alterSql);
  }
}