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
 */

package org.apache.hop.catalog.hopgui.perspective;

import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionRef;

/** Data attached to items in the data catalog perspective tree. */
public final class DataCatalogTreeNode {

  public enum Type {
    CATALOG,
    RECORD
  }

  private final Type type;
  private final String catalogConnectionName;
  private final RecordDefinitionKey recordKey;

  private DataCatalogTreeNode(
      Type type, String catalogConnectionName, RecordDefinitionKey recordKey) {
    this.type = type;
    this.catalogConnectionName = catalogConnectionName;
    this.recordKey = recordKey;
  }

  public static DataCatalogTreeNode catalog(String connectionName) {
    return new DataCatalogTreeNode(Type.CATALOG, connectionName, null);
  }

  public static DataCatalogTreeNode record(String connectionName, RecordDefinitionRef ref) {
    return new DataCatalogTreeNode(Type.RECORD, connectionName, ref.getKey());
  }

  public Type getType() {
    return type;
  }

  public String getCatalogConnectionName() {
    return catalogConnectionName;
  }

  public RecordDefinitionKey getRecordKey() {
    return recordKey;
  }
}