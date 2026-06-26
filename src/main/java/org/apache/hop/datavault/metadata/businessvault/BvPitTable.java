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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Point-in-time table combining a DV hub grain with satellite snapshots. */
@Getter
@Setter
public class BvPitTable extends BvTableBase {

  private static final Class<?> PKG = BvPitTable.class;

  @HopMetadataProperty private String snapshotDateField = "snapshot_date";

  public BvPitTable() {
    super(BvTableType.PIT);
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);
    boolean hasHub =
        getDerivatives().stream()
            .anyMatch(ref -> ref != null && ref.getDvTableType() == DvTableType.HUB);
    boolean hasSatellite =
        getDerivatives().stream()
            .anyMatch(ref -> ref != null && ref.getDvTableType() == DvTableType.SATELLITE);
    if (!hasHub) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvPitTable.CheckResult.MissingHubDerivative", getName()),
              this));
    }
    if (!hasSatellite) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvPitTable.CheckResult.MissingSatelliteDerivative", getName()),
              this));
    }
  }
}