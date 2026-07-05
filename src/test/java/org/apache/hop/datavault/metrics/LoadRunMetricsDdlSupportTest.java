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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.datavault.metadata.DvBulkLoadPluginSupport;
import org.junit.jupiter.api.Test;

class LoadRunMetricsDdlSupportTest {

  @Test
  void postgresDdlCreatesSchemaAndLoadRunTable() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID));

    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE SCHEMA IF NOT EXISTS dv_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS dv_ops.load_run")));
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("pipeline_run_configuration")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS dv_ops.load_insight")));
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
  void mysqlDdlCreatesDatabaseAndLoadRunTable() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID));

    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE DATABASE IF NOT EXISTS dv_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS dv_ops.load_run")));
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