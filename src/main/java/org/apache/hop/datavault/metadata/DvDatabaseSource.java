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

package org.apache.hop.datavault.metadata;

import java.util.Objects;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Data Vault source implementation for a relational database (RDBMS).
 *
 * <p>References an existing Hop "RDBMS" / DatabaseMeta metadata object (key "rdbms") by name.
 * The actual table, view or query is typically resolved at runtime by a transform or generator
 * that also knows the desired source table / SQL.
 *
 * <p>The {@link #getFields()} list (inherited) defines the expected column layout and which
 * columns participate in the source primary key / natural key.
 */
@GuiPlugin
@HopMetadata(
    key = "data-vault-source-database",
    name = "i18n::DvDatabaseSource.name",
    description = "i18n::DvDatabaseSource.description",
    image = "datavault_model.svg",
    documentationUrl = "/metadata-types/data-vault-source-database.html")
public class DvDatabaseSource extends DvSourceBase implements IHopMetadata, IDvSource, IHasName {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_DATABASE_SOURCE_DIALOG";

  /**
   * Name of the RDBMS connection (a DatabaseMeta / "rdbms" metadata object) that this source
   * uses.
   */
  @HopMetadataProperty(key = "databaseName")
  private String databaseName;

  /**
   * Optional schema / catalog qualifier (implementation dependent).
   */
  @HopMetadataProperty
  private String schemaName;

  /**
   * Optional: the physical table or view name in the source database (can also be supplied
   * at runtime or via a query).
   */
  @HopMetadataProperty(key = "tableName")
  private String tableName;

  public DvDatabaseSource() {
    super();
    this.sourceType = DvSourceType.DATABASE;
  }

  public DvDatabaseSource(String name) {
    super(name);
    this.sourceType = DvSourceType.DATABASE;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public void setDatabaseName(String databaseName) {
    if (!Objects.equals(this.databaseName, databaseName)) {
      setChanged();
    }
    this.databaseName = databaseName;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public void setSchemaName(String schemaName) {
    if (!Objects.equals(this.schemaName, schemaName)) {
      setChanged();
    }
    this.schemaName = schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    if (!Objects.equals(this.tableName, tableName)) {
      setChanged();
    }
    this.tableName = tableName;
  }

  @Override
  public DvSourceType getSourceType() {
    return DvSourceType.DATABASE;
  }
}
