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

package org.apache.hop.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.quality.profile.SqlAssertionRunner;
import org.junit.jupiter.api.Test;

class SqlAssertionRunnerTest {

  @Test
  void prepareSqlExpandsSchemaTableAndTrimsTrailingSemicolon() throws Exception {
    Variables vars = new Variables();
    vars.setVariable("LIMIT", "10");
    String prepared =
        SqlAssertionRunner.prepareSql(
            "SELECT * FROM ${schemaTable} LIMIT ${LIMIT} ;", vars, "\"public\".\"t\"");
    assertEquals("SELECT * FROM \"public\".\"t\" LIMIT 10", prepared);
  }

  @Test
  void prepareSqlRejectsMultiStatement() {
    Variables vars = new Variables();
    HopException ex =
        assertThrows(
            HopException.class,
            () ->
                SqlAssertionRunner.prepareSql(
                    "SELECT 1; DROP TABLE x", vars, "\"public\".\"t\""));
    assertTrue(
        ex.getMessage().toLowerCase().contains("multi-statement")
            || ex.getMessage().toLowerCase().contains("semicolon"));
  }

  @Test
  void prepareSqlRejectsEmpty() {
    Variables vars = new Variables();
    assertThrows(HopException.class, () -> SqlAssertionRunner.prepareSql("   ", vars, "t"));
    assertThrows(HopException.class, () -> SqlAssertionRunner.prepareSql(";", vars, "t"));
  }

  @Test
  void allowlistAcceptsSelectAndWith() throws Exception {
    DataQualityRule rule = assertionRule(Map.of());
    SqlAssertionRunner.validateSelectOnly("SELECT 1 FROM t", rule);
    SqlAssertionRunner.validateSelectOnly("with cte as (select 1 as x) select * from cte", rule);
    SqlAssertionRunner.validateSelectOnly(
        "/* leading */\n-- comment\nSELECT count(*) FROM t", rule);
  }

  @Test
  void allowlistRejectsNonSelect() {
    DataQualityRule rule = assertionRule(Map.of());
    HopException ex =
        assertThrows(
            HopException.class,
            () -> SqlAssertionRunner.validateSelectOnly("DELETE FROM t WHERE 1=1", rule));
    assertTrue(
        ex.getMessage().toLowerCase().contains("select")
            || ex.getMessage().toLowerCase().contains("denylist"));
  }

  @Test
  void denylistRejectsIntoOutfile() {
    DataQualityRule rule = assertionRule(Map.of());
    HopException into =
        assertThrows(
            HopException.class,
            () -> SqlAssertionRunner.validateSelectOnly("SELECT * INTO outfile FROM t", rule));
    assertTrue(into.getMessage().toLowerCase().contains("denylist"));
  }

  @Test
  void denylistRejectsInsertUpdateDelete() {
    DataQualityRule rule = assertionRule(Map.of());
    assertThrows(
        HopException.class,
        () -> SqlAssertionRunner.validateSelectOnly("INSERT INTO t SELECT 1", rule));
    assertThrows(
        HopException.class,
        () -> SqlAssertionRunner.validateSelectOnly("UPDATE t SET a=1 WHERE b=2", rule));
    assertThrows(
        HopException.class,
        () -> SqlAssertionRunner.validateSelectOnly("DELETE FROM t WHERE 1=1", rule));
    assertThrows(
        HopException.class,
        () -> SqlAssertionRunner.validateSelectOnly("SELECT 1 FROM t; DROP TABLE x", rule));
  }

  @Test
  void zeroRowsPassesWhenEmptyAndFailsWhenRowPresent() throws Exception {
    DataQualityRule rule = assertionRule(Map.of("expect", "ZERO_ROWS"));
    assertTrue(
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ZERO_ROWS", null, false, null)
            .isEmpty());

    List<DataQualityFinding> findings =
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ZERO_ROWS", null, true, 1);
    assertEquals(1, findings.size());
    assertEquals(QualitySeverity.BLOCKING, findings.get(0).getSeverity());
    assertEquals(DataQualityRuleType.SQL_ASSERTION, findings.get(0).getType());
  }

  @Test
  void oneRowTruePassesOnTruthyAndFailsOtherwise() throws Exception {
    DataQualityRule rule = assertionRule(Map.of("expect", "ONE_ROW_TRUE"));
    assertTrue(
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ONE_ROW_TRUE", null, true, true)
            .isEmpty());
    assertTrue(
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ONE_ROW_TRUE", null, true, 1)
            .isEmpty());

    assertEquals(
        1,
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ONE_ROW_TRUE", null, false, null)
            .size());
    assertEquals(
        1,
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ONE_ROW_TRUE", null, true, false)
            .size());
    assertEquals(
        1,
        SqlAssertionRunner.evaluateExpectation(rule, "s", "ONE_ROW_TRUE", null, true, 0).size());
  }

  @Test
  void scalarEqComparesFirstColumn() throws Exception {
    DataQualityRule rule = assertionRule(Map.of("expect", "SCALAR_EQ", "expectValue", "42"));
    assertTrue(
        SqlAssertionRunner.evaluateExpectation(rule, "s", "SCALAR_EQ", "42", true, 42).isEmpty());
    assertTrue(
        SqlAssertionRunner.evaluateExpectation(rule, "s", "SCALAR_EQ", "42", true, "42")
            .isEmpty());

    List<DataQualityFinding> mismatch =
        SqlAssertionRunner.evaluateExpectation(rule, "s", "SCALAR_EQ", "42", true, 7);
    assertEquals(1, mismatch.size());
    assertEquals("7", mismatch.get(0).getActualSummary());

    assertEquals(
        1,
        SqlAssertionRunner.evaluateExpectation(rule, "s", "SCALAR_EQ", "42", false, null).size());
  }

  @Test
  void isTruthyAndScalarEqualsHelpers() {
    assertTrue(SqlAssertionRunner.isTruthy(Boolean.TRUE));
    assertTrue(SqlAssertionRunner.isTruthy(1));
    assertTrue(SqlAssertionRunner.isTruthy("yes"));
    assertFalse(SqlAssertionRunner.isTruthy(null));
    assertFalse(SqlAssertionRunner.isTruthy(0));
    assertFalse(SqlAssertionRunner.isTruthy("false"));

    assertTrue(SqlAssertionRunner.scalarEquals(5, "5"));
    assertTrue(SqlAssertionRunner.scalarEquals(null, null));
    assertFalse(SqlAssertionRunner.scalarEquals(null, "x"));
    assertFalse(SqlAssertionRunner.scalarEquals("a", "b"));
  }

  @Test
  void normalizeExpectDefaultsAndRejectsUnknown() {
    assertEquals("ZERO_ROWS", SqlAssertionRunner.normalizeExpect(null));
    assertEquals("ZERO_ROWS", SqlAssertionRunner.normalizeExpect(""));
    assertEquals("ONE_ROW_TRUE", SqlAssertionRunner.normalizeExpect("one_row_true"));
    assertThrows(IllegalArgumentException.class, () -> SqlAssertionRunner.normalizeExpect("NOPE"));
  }

  @Test
  void parseTimeoutDefaultsTo60() {
    assertEquals(60, SqlAssertionRunner.parseTimeout(null));
    assertEquals(60, SqlAssertionRunner.parseTimeout(""));
    assertEquals(60, SqlAssertionRunner.parseTimeout("abc"));
    assertEquals(30, SqlAssertionRunner.parseTimeout("30"));
    assertEquals(0, SqlAssertionRunner.parseTimeout("-5"));
  }

  @Test
  void fieldNamePropagatesToFindingForMessaging() throws Exception {
    DataQualityRule rule = assertionRule(Map.of("expect", "ZERO_ROWS"));
    rule.setFieldName("email");
    List<DataQualityFinding> findings =
        SqlAssertionRunner.evaluateExpectation(rule, "ns/subject", "ZERO_ROWS", null, true, "bad");
    assertEquals(1, findings.size());
    assertEquals("email", findings.get(0).getFieldName());
    assertEquals("ns/subject", findings.get(0).getSubjectKey());
  }

  @Test
  void sqlAssertionEnumPresent() {
    boolean found = false;
    for (DataQualityRuleType t : DataQualityRuleType.values()) {
      if (t == DataQualityRuleType.SQL_ASSERTION) {
        found = true;
        break;
      }
    }
    assertTrue(found);
  }

  private static DataQualityRule assertionRule(Map<String, String> params) {
    DataQualityRule rule = new DataQualityRule();
    rule.setType(DataQualityRuleType.SQL_ASSERTION);
    rule.setName("assert");
    rule.setSeverity(QualitySeverity.BLOCKING);
    rule.setParameters(new LinkedHashMap<>(params));
    rule.ensureId();
    return rule;
  }
}
