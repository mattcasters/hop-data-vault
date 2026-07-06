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
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;

/** Helpers for cross-model Data Vault table references on a subject-area canvas. */
public final class DvTableReferenceSupport {

  private DvTableReferenceSupport() {}

  public static DvTableType effectiveTableType(IDvTable table) {
    if (table instanceof DvTableReference reference && reference.getReferencedTableType() != null) {
      return reference.getReferencedTableType();
    }
    return table != null ? table.getTableType() : null;
  }

  public static boolean hasTableReference(DataVaultModel model, String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return false;
    }
    IDvTable table = model.findTable(tableName);
    return table instanceof DvTableReference;
  }

  public static List<String> listAvailableTableNames(
      DataVaultModel externalModel, DataVaultModel subjectModel, DvTableType tableType) {
    if (externalModel == null || tableType == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDvTable table : externalModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || table.getTableType() != tableType
          || table instanceof DvTableReference) {
        continue;
      }
      if (subjectModel != null && subjectModel.findTable(table.getName()) != null) {
        continue;
      }
      names.add(table.getName());
    }
    Collections.sort(names);
    return names;
  }

  public static DvTableReference createReference(
      IDvTable externalTable, String externalModelFilename, Point location) {
    if (externalTable == null
        || Utils.isEmpty(externalTable.getName())
        || externalTable.getTableType() == null
        || externalTable.getTableType() == DvTableType.TABLE_REFERENCE
        || Utils.isEmpty(externalModelFilename)) {
      return null;
    }
    DvTableReference reference = new DvTableReference();
    reference.setName(externalTable.getName());
    reference.setReferencedTableName(externalTable.getName());
    reference.setReferencedModelFilename(externalModelFilename);
    reference.setReferencedTableType(externalTable.getTableType());
    reference.setTableName(
        !Utils.isEmpty(externalTable.getTableName())
            ? externalTable.getTableName()
            : externalTable.getName());
    if (location != null) {
      reference.setLocation(new Point(location.x, location.y));
    }
    return reference;
  }
}