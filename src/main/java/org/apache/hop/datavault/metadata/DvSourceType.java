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

/**
 * Enumeration of supported source system types for a Data Vault model.
 *
 * <p>Each source type describes how rows are obtained (e.g. from an RDBMS table/query,
 * a CSV file, a Parquet file, etc). The common aspects (name + expected row layout via
 * fields) live in the base; type-specific connection details live in the concrete
 * implementations (DvDatabaseSource, ...).
 */
public enum DvSourceType {
  DATABASE("Database (RDBMS)"),
  CSV("CSV / delimited text file"),
  PARQUET("Parquet file"),
  ;

  private final String description;

  DvSourceType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
