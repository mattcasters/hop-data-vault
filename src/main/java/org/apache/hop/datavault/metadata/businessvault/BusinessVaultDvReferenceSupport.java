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
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;

/** Helpers for Business Vault canvas references to Data Vault tables. */
public final class BusinessVaultDvReferenceSupport {

  private BusinessVaultDvReferenceSupport() {}

  public static boolean hasDvReference(BusinessVaultModel model, String dvTableName) {
    if (model == null || Utils.isEmpty(dvTableName)) {
      return false;
    }
    return model.getDvReferences().stream()
        .anyMatch(
            ref ->
                ref != null && dvTableName.equalsIgnoreCase(ref.getDvTableName()));
  }

  public static List<String> listAvailableDvTableNames(
      DataVaultModel dataVaultModel, BusinessVaultModel businessVaultModel, DvTableType tableType) {
    if (dataVaultModel == null || tableType == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDvTable table : dataVaultModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || table.getTableType() != tableType
          || hasDvReference(businessVaultModel, table.getName())) {
        continue;
      }
      names.add(table.getName());
    }
    Collections.sort(names);
    return names;
  }

  public static BvDvTableReference createReference(IDvTable dvTable, Point location) {
    if (dvTable == null || Utils.isEmpty(dvTable.getName()) || dvTable.getTableType() == null) {
      return null;
    }
    BvDvTableReference reference =
        new BvDvTableReference(dvTable.getName(), dvTable.getTableType());
    if (location != null) {
      reference.setLocation(new Point(location.x, location.y));
    }
    return reference;
  }
}