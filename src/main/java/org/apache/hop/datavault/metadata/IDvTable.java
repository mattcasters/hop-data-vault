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

import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;

/**
 * Common interface for all Data Vault 2.0 table-like structures:
 * Hubs, Links, and Satellites.
 *
 * <p>This allows generic code (transforms, generators, validators, DDL builders, etc.)
 * to treat any DV table uniformly for the most common attributes without
 * caring about the specific subtype.
 *
 * <p>Extending IGuiPosition / IBaseMeta / IHasName / IChanged allows DV table definitions
 * to be used as first-class draggable/positionable objects inside a visual Data Vault
 * modeling perspective (analogous to TransformMeta / ActionMeta in pipelines/workflows).
 */
@HopMetadataObject(xmlKey = "tableType", objectFactory = IDvTable.DvTableFactory.class)
public interface IDvTable extends IGuiPosition, IBaseMeta, IHasName, IChanged {

  /** Logical / metadata name of this DV object. Usually matches the table name unless overridden. */
  String getName();

  /** The physical table name in the target database. */
  String getTableName();

  /** Human-readable description of this table / concept. */
  String getDescription();

  /** Name of the {@link DataVaultSource} metadata element providing the default or suggested
   * record source value for rows loaded into this table. */
  String getRecordSource();

  /** Returns the type of this Data Vault table (HUB, SATELLITE or LINK). */
  DvTableType getTableType();

  /**
   * Factory used by the Hop metadata serializer to handle polymorphic serialization of
   * {@link IDvTable} implementations (DvHub, DvLink, DvSatellite) inside lists such as the one in
   * {@link DataVaultModel}.
   */
  final class DvTableFactory implements IHopMetadataObjectFactory {

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (DvTableType.HUB.name().equals(id)) {
        return new DvHub();
      }
      if (DvTableType.SATELLITE.name().equals(id)) {
        return new DvSatellite();
      }
      if (DvTableType.LINK.name().equals(id)) {
        return new DvLink();
      }
      throw new HopException("Unable to recognize Data Vault table type with ID '" + id + "'");
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (!(object instanceof IDvTable dvTable)) {
        throw new HopException(
            "Object is not of class IDvTable but of " + object.getClass().getName() + "'");
      }
      DvTableType tableType = dvTable.getTableType();
      if (tableType == null) {
        throw new HopException("Data Vault table has no table type set");
      }
      return tableType.name();
    }
  }
}
