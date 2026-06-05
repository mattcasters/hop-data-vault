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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Common abstract base for all Data Vault source definitions (Database, CSV, etc.).
 *
 * <p>Provides:
 * <ul>
 *   <li>metadata name (inherited)</li>
 *   <li>description</li>
 *   <li>list of expected source fields (the logical row layout)</li>
 *   <li>sourceType discriminator (for polymorphism)</li>
 * </ul>
 *
 * <p>Concrete subclasses add the connection details for their kind of source (e.g. which
 * DatabaseMeta to use for DATABASE sources).
 */
public abstract class DvSourceBase extends HopMetadataBase implements IHopMetadata, IDvSource {

  @HopMetadataProperty
  protected String description;

  /**
   * The expected fields / columns in rows coming from this source.
   * This is the "source schema" used for mapping to DV hubs/links/satellites.
   */
  @HopMetadataProperty(key = "field", groupKey = "fields")
  protected List<SourceField> fields = new ArrayList<>();

  /** Set by concrete subclass; used for polymorphic (de)serialization. */
  @HopMetadataProperty
  protected DvSourceType sourceType;

  protected final ChangedFlag changedFlag = new ChangedFlag();

  protected DvSourceBase() {
    super();
  }

  protected DvSourceBase(String name) {
    super(name);
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    if (!java.util.Objects.equals(this.description, description)) {
      setChanged();
    }
    this.description = description;
  }

  @Override
  public List<SourceField> getFields() {
    return fields;
  }

  @Override
  public void setFields(List<SourceField> fields) {
    if (!java.util.Objects.equals(this.fields, fields)) {
      setChanged();
    }
    this.fields = fields;
  }

  @Override
  public DvSourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(DvSourceType sourceType) {
    if (!java.util.Objects.equals(this.sourceType, sourceType)) {
      setChanged();
    }
    this.sourceType = sourceType;
  }

  /**
   * Convenience: fall back to the metadata name when no explicit description is present.
   */
  public String getEffectiveDescription() {
    if (description != null && !description.isBlank()) {
      return description;
    }
    return getName();
  }

  // -------------------------------------------------------------------------------------
  // IChanged implementation (so that sources participate in model dirty state when embedded)
  // -------------------------------------------------------------------------------------

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean ch) {
    changedFlag.setChanged(ch);
  }

  @Override
  public void clearChanged() {
    changedFlag.clearChanged();
  }
}
