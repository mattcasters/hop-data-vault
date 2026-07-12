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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.datavault.metadata.DvBulkLoadPluginSupport;
import org.junit.jupiter.api.Test;

class LoadRunMetricsDdlSupportTest {

  @Test
  void defaultDdlUsesConnectionDefaultWithoutCreateSchema() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE SCHEMA")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("pipeline_run_configuration")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("workflow_execution_id")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_insight")));
    assertTrue(
        statements.stream().noneMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS dv_ops.")));
  }

  @Test
  void postgresDdlUsesCustomOperationsSchema() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID), "retail_ops");

    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE SCHEMA IF NOT EXISTS retail_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS retail_ops.load_run")));
  }

  @Test
  void mysqlDdlUsesConnectionDefaultWithoutCreateDatabase() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID));

    assertTrue(
        statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")),
        "MySQL should not create a separate ops database");
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("TINYINT(1)")));
  }

  @Test
  void singlestoreDdlUsesConnectionDefaultLikeMysql() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
  }

  @Test
  void mysqlCustomSchemaStillQualifiesTables() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID), "custom_ops");

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS custom_ops.load_run")));
    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")));
  }

  @Test
  void resolvePhysicalOperationsSchemaKeepsExplicitAndEmptyDefault() {
    DatabaseMeta mysql = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID);
    DatabaseMeta postgres =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);

    assertEquals(
        "", LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(null, mysql));
    assertEquals(
        "", LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema("  ", postgres));
    assertEquals(
        "dv_ops",
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema("dv_ops", postgres));
    assertEquals(
        "custom_ops",
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema("custom_ops", mysql));
    assertTrue(LoadRunMetricsCatalogPublisher.isMysqlFamily(mysql));
    assertFalse(LoadRunMetricsCatalogPublisher.isMysqlFamily(postgres));
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    DatabaseMeta databaseMeta =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return pluginId;
          }
        };
    databaseMeta.setName("test-db");
    return databaseMeta;
  }
}
