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

package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvBulkLoadCommandSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesMssqlStagingAction() {
    DatabaseMeta mssql = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID);
    assertEquals(
        DvBulkLoadCommandSupport.MSSQL_BULK_LOAD_ACTION_ID,
        DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId(mssql));
  }

  @Test
  void postgresCopySqlIncludesHeaderAndDelimiter() throws Exception {
    DatabaseMeta postgres =
        new DatabaseMeta(
            "postgres-test", "PostgreSQL", "Native", "", "localhost", "test", "postgres", "");
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setBulkLoadDelimiter("|");
    config.setBulkLoadEnclosure("'");
    config.setBulkLoadEncoding("UTF-8");

    String sql =
        DvBulkLoadCommandSupport.buildPostgresCopySql(
            postgres,
            config,
            new Variables(),
            "hub_customer",
            List.of("CUSTOMER_HK", "LOAD_DATE"),
            "/tmp/hub-customer-0.csv");

    assertTrue(sql.contains("COPY"));
    assertTrue(sql.contains("HEADER true"));
    assertTrue(sql.contains("DELIMITER '|'"));
    assertTrue(sql.contains("/tmp/hub-customer-0.csv"));
    assertTrue(sql.contains("CUSTOMER_HK"));
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