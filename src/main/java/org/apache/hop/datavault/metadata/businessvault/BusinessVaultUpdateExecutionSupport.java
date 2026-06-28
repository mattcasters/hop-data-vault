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
import java.util.List;

/** Execution ordering helpers for Business Vault Update workflows. */
public final class BusinessVaultUpdateExecutionSupport {

  private BusinessVaultUpdateExecutionSupport() {}

  public static boolean isPipelineExecutableTableType(BvTableType tableType) {
    return tableType == BvTableType.SCD2 || tableType == BvTableType.PIT;
  }

  /** Returns SCD2 tables first, then PIT tables, preserving each group's model order. */
  public static List<IBvTable> orderTablesForPipelineExecution(List<IBvTable> tables) {
    List<IBvTable> scd2Tables = new ArrayList<>();
    List<IBvTable> pitTables = new ArrayList<>();
    if (tables == null) {
      return List.of();
    }
    for (IBvTable table : tables) {
      if (table == null || table.getTableType() == null) {
        continue;
      }
      if (table.getTableType() == BvTableType.SCD2) {
        scd2Tables.add(table);
      } else if (table.getTableType() == BvTableType.PIT) {
        pitTables.add(table);
      }
    }
    List<IBvTable> ordered = new ArrayList<>(scd2Tables.size() + pitTables.size());
    ordered.addAll(scd2Tables);
    ordered.addAll(pitTables);
    return ordered;
  }
}