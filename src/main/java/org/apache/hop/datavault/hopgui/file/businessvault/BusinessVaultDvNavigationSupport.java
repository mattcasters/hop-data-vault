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

package org.apache.hop.datavault.hopgui.file.businessvault;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/** Opens the linked Data Vault model and navigates to a referenced table. */
public final class BusinessVaultDvNavigationSupport {

  private static final Class<?> PKG = BusinessVaultDvNavigationSupport.class;

  private BusinessVaultDvNavigationSupport() {}

  public static void navigateToDvTable(
      HopGui hopGui, BusinessVaultModel bvModel, String dvTableName, IVariables variables)
      throws HopException {
    if (hopGui == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingHopGui"));
    }
    if (bvModel == null || Utils.isEmpty(bvModel.getDataVaultModelPath())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BusinessVaultDvNavigationSupport.Error.MissingDataVaultModelPath"));
    }
    if (Utils.isEmpty(dvTableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingTableName"));
    }

    String resolvedTableName = variables != null ? variables.resolve(dvTableName) : dvTableName;
    if (Utils.isEmpty(resolvedTableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "BusinessVaultDvNavigationSupport.Error.MissingTableName"));
    }

    DataVaultModel dvModel =
        BusinessVaultDvModelResolver.loadReferencedModel(
            bvModel.getDataVaultModelPath(), variables, hopGui.getMetadataProvider());
    IDvTable table = dvModel.findTable(resolvedTableName);
    if (table == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BusinessVaultDvNavigationSupport.Error.TableNotFound",
              resolvedTableName,
              bvModel.getDataVaultModelPath()));
    }

    String resolvedPath = HopVfs.normalize(variables.resolve(bvModel.getDataVaultModelPath()));

    ExplorerPerspective explorer = HopGui.getExplorerPerspective();
    hopGui.setActivePerspective(explorer);
    explorer.activate();

    HopVaultFileType fileType = new HopVaultFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, resolvedPath, variables);
    if (!(handler instanceof HopGuiVaultGraph vaultGraph)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BusinessVaultDvNavigationSupport.Error.UnexpectedFileHandler"));
    }
    vaultGraph.navigateToTable(resolvedTableName);
  }
}