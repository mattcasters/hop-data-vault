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

import java.util.Arrays;
import java.util.List;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void buildCreateTableStatementIncludesShardKeyOnHashKey() {
    DatabaseMeta databaseMeta = singleStoreDatabaseMeta();
    IRowMeta fields = hubLayout();

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "hub_customer",
            fields,
            new String[] {"customer_hk"},
            null,
            true);

    assertTrue(ddl.toUpperCase().contains("CREATE TABLE"));
    assertTrue(ddl.contains("customer_hk"));
    assertTrue(ddl.toUpperCase().contains("BINARY"));
    assertTrue(ddl.toUpperCase().contains("SHARD KEY (CUSTOMER_HK)"));
  }

  @Test
  void buildCreateTableStatementIncludesDrivingKeyInShardKey() {
    DatabaseMeta databaseMeta = singleStoreDatabaseMeta();
    IRowMeta fields = multiActiveSatelliteLayout();

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            databaseMeta,
            new Variables(),
            "sat_customer_phone",
            fields,
            new String[] {"customer_hk", "phone_type"},
            null,
            true);

    assertTrue(ddl.toUpperCase().contains("SHARD KEY (CUSTOMER_HK, PHONE_TYPE)"));
  }

  @Test
  void isShardKeyDdlEnabledRequiresSingleStoreAndCheckbox() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setSingleStoreShardKeyOnHashKey(true);

    assertTrue(DvDdlSupport.isShardKeyDdlEnabled(config, singleStoreDatabaseMeta()));
    assertFalse(DvDdlSupport.isShardKeyDdlEnabled(config, null));

    config.setSingleStoreShardKeyOnHashKey(false);
    assertFalse(DvDdlSupport.isShardKeyDdlEnabled(config, singleStoreDatabaseMeta()));
  }

  @Test
  void isCreateTableDdlDetectsCreateStatements() {
    assertTrue(DvDdlSupport.isCreateTableDdl("CREATE TABLE foo (id INT);"));
    assertFalse(DvDdlSupport.isCreateTableDdl("ALTER TABLE foo ADD COLUMN bar INT;"));
    assertFalse(DvDdlSupport.isCreateTableDdl(""));
  }

  @Test
  void extractCreateTableNameParsesSimpleAndQualifiedNames() {
    assertEquals(
        "sat_syn_product",
        DvDdlSupport.extractCreateTableName(
            "CREATE TABLE sat_syn_product\n(\n  order_hk BYTEA\n);"));
    assertEquals(
        "sat_syn_product",
        DvDdlSupport.extractCreateTableName("CREATE TABLE vault.sat_syn_product (id INT);"));
    assertEquals(
        "sat_syn_product",
        DvDdlSupport.extractCreateTableName("CREATE TABLE \"sat_syn_product\" (id INT);"));
  }

  @Test
  void deduplicateCreateTableDdlKeepsFirstStatementPerTable() {
    String first =
        "CREATE TABLE sat_syn_product\n(\n  product_hk BYTEA\n, product_code VARCHAR(8)\n);";
    String second =
        "CREATE TABLE sat_syn_product\n(\n  order_hk BYTEA\n, product_code VARCHAR(8)\n);";
    String link = "CREATE TABLE lnk_syn_order\n(\n  lnk_syn_order_hk BYTEA\n);";

    List<String> deduplicated =
        DvDdlSupport.deduplicateCreateTableDdl(Arrays.asList(first, second, link));

    assertEquals(2, deduplicated.size());
    assertEquals(first, deduplicated.get(0));
    assertEquals(link, deduplicated.get(1));
  }

  private static DatabaseMeta singleStoreDatabaseMeta() {
    return new DatabaseMeta(
        "singlestore-test", "SingleStore (MemSQL)", "Native", "", "localhost", "test", "root", "");
  }

  private static IRowMeta hubLayout() {
    RowMeta rowMeta = new RowMeta();
    ValueMetaBinary hashKey = new ValueMetaBinary("customer_hk");
    hashKey.setLength(16);
    rowMeta.addValueMeta(hashKey);
    rowMeta.addValueMeta(new ValueMetaString("customer_id"));
    return rowMeta;
  }

  private static IRowMeta multiActiveSatelliteLayout() {
    RowMeta rowMeta = new RowMeta();
    ValueMetaBinary hashKey = new ValueMetaBinary("customer_hk");
    hashKey.setLength(16);
    rowMeta.addValueMeta(hashKey);
    rowMeta.addValueMeta(new ValueMetaString("phone_type"));
    rowMeta.addValueMeta(new ValueMetaString("phone_number"));
    return rowMeta;
  }
}