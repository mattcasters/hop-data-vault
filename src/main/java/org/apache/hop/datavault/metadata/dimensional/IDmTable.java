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

package org.apache.hop.datavault.metadata.dimensional;

import java.util.List;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Common interface for dimension and fact tables on a {@link DimensionalModel} canvas. */
@HopMetadataObject(xmlKey = "tableType", objectFactory = IDmTable.DmTableFactory.class)
public interface IDmTable extends IGuiPosition, IBaseMeta, IHasName, IChanged, ICheckResultSource {

  String getName();

  void setName(String name);

  String getTableName();

  void setTableName(String tableName);

  String getDescription();

  void setDescription(String description);

  DmTableType getTableType();

  void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model);

  final class DmTableFactory implements IHopMetadataObjectFactory {

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (DmTableType.DIMENSION.name().equals(id)) {
        return new DmDimension();
      }
      if (DmTableType.FACT.name().equals(id)) {
        return new DmFact();
      }
      throw new HopException("Unable to recognize dimensional table type with ID '" + id + "'");
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (!(object instanceof IDmTable dmTable)) {
        throw new HopException(
            "Object is not of class IDmTable but of " + object.getClass().getName());
      }
      DmTableType tableType = dmTable.getTableType();
      if (tableType == null) {
        throw new HopException("Dimensional table has no table type set");
      }
      return tableType.name();
    }
  }
}