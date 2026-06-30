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

import java.util.ArrayList;
import java.util.List;

/** Execution ordering helpers for Data Vault model update runs. */
public final class DvUpdateExecutionSupport {

  private DvUpdateExecutionSupport() {}

  /** Returns hubs, then links, then satellites (model order preserved within each type). */
  public static List<IDvTable> orderTablesForPipelineExecution(List<IDvTable> tables) {
    List<IDvTable> hubs = new ArrayList<>();
    List<IDvTable> links = new ArrayList<>();
    List<IDvTable> satellites = new ArrayList<>();
    if (tables == null) {
      return List.of();
    }
    for (IDvTable table : tables) {
      if (table == null || table.getTableType() == null) {
        continue;
      }
      switch (table.getTableType()) {
        case HUB -> hubs.add(table);
        case LINK -> links.add(table);
        case SATELLITE -> satellites.add(table);
      }
    }
    List<IDvTable> ordered = new ArrayList<>(hubs.size() + links.size() + satellites.size());
    ordered.addAll(hubs);
    ordered.addAll(links);
    ordered.addAll(satellites);
    return ordered;
  }
}