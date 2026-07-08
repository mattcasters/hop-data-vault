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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvSqlOrderBySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void appendsCollationForStringBusinessKeyOnSqlServer() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHubOrderByCollation("SQL_Latin1_General_CP1_CI_AS");

    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setDataType("String");

    StringBuilder sql = new StringBuilder("SELECT 1 FROM t");
    DvSqlOrderBySupport.appendOrderBy(
        sql,
        List.of(businessKey),
        List.of("[ITMREF_0]"),
        databaseMeta,
        config,
        new Variables());

    assertTrue(sql.toString().contains("ORDER BY [ITMREF_0] COLLATE SQL_Latin1_General_CP1_CI_AS"));
  }

  @Test
  void perBusinessKeyCollationOverridesModelDefault() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHubOrderByCollation("SQL_Latin1_General_CP1_CI_AS");

    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setDataType("String");
    businessKey.setOrderByCollation("French_CI_AS");

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[ITMREF_0]", businessKey, databaseMeta, config, new Variables());

    assertEquals("[ITMREF_0] COLLATE French_CI_AS", expression);
  }

  @Test
  void skipsCollationForIntegerBusinessKey() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHubOrderByCollation("SQL_Latin1_General_CP1_CI_AS");

    BusinessKey businessKey = new BusinessKey("customer_id");
    businessKey.setDataType("Integer");

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[customer_id]", businessKey, databaseMeta, config, new Variables());

    assertEquals("[customer_id]", expression);
    assertFalse(expression.contains("COLLATE"));
  }

  @Test
  void skipsCollationForNonSqlServerDatabase() {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("CRM");
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHubOrderByCollation("SQL_Latin1_General_CP1_CI_AS");

    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setDataType("String");

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[ITMREF_0]", businessKey, databaseMeta, config, new Variables());

    assertEquals("[ITMREF_0]", expression);
  }

  private static DatabaseMeta mssqlDatabaseMeta() {
    return databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }
}