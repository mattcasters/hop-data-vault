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

package org.apache.hop.catalog.versioning;

import java.util.List;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/** GUI entry points for tagging and listing catalog versions. */
public final class CatalogVersionGuiSupport {

  private static final Class<?> PKG = CatalogVersionGuiSupport.class;

  private CatalogVersionGuiSupport() {}

  public static CatalogVersionEntry tagVersionFromGroup(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return null;
    }
    Shell shell = hopGui.getShell();
    try {
      EnterStringDialog tagDialog =
          new EnterStringDialog(
              shell,
              "",
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.Title"),
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.Message"));
      tagDialog.setMandatory(true);
      String tag = tagDialog.open();
      if (Utils.isEmpty(tag)) {
        return null;
      }

      EnterStringDialog descriptionDialog =
          new EnterStringDialog(
              shell,
              "",
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.DescriptionTitle"),
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.DescriptionMessage"));
      String description = descriptionDialog.open();
      if (description == null) {
        description = "";
      }

      String createdBy = System.getProperty("user.name", "");
      CatalogVersionEntry entry =
          CatalogVersionService.createFromGroup(
              group,
              tag.trim(),
              description.trim(),
              createdBy,
              hopGui.getVariables(),
              hopGui.getMetadataProvider());

      if (shell != null && !shell.isDisposed()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.Success.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG,
                "CatalogVersionGuiSupport.Tag.Success.Message",
                entry.getTag(),
                Integer.toString(entry.getRecordCount())));
        box.open();
      }
      return entry;
    } catch (Exception e) {
      showError(shell, e);
      return null;
    }
  }

  public static void listVersionsForGroup(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    try {
      String connection = resolveConnection(group, hopGui.getVariables(), hopGui.getMetadataProvider());
      if (Utils.isEmpty(connection)) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.MissingConnection"));
        box.open();
        return;
      }

      List<CatalogVersionEntry> versions =
          CatalogVersionService.listVersions(
              connection, hopGui.getVariables(), hopGui.getMetadataProvider());
      if (versions.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Empty.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "CatalogVersionGuiSupport.List.Empty.Message", connection));
        box.open();
        return;
      }

      StringBuilder text = new StringBuilder();
      for (CatalogVersionEntry entry : versions) {
        if (entry == null) {
          continue;
        }
        text.append(entry.getTag())
            .append("  |  ")
            .append(entry.getCreatedAt() != null ? entry.getCreatedAt() : "")
            .append("  |  records=")
            .append(entry.getRecordCount());
        if (!Utils.isEmpty(entry.getDescription())) {
          text.append("  |  ").append(entry.getDescription());
        }
        text.append('\n');
      }

      EnterTextDialog dialog =
          new EnterTextDialog(
              shell,
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Title"),
              connection,
              text.toString(),
              true);
      dialog.setReadOnly();
      dialog.open();
    } catch (Exception e) {
      showError(shell, e);
    }
  }

  private static String resolveConnection(
      ResourceDefinitionGroupMeta group, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group != null && !Utils.isEmpty(group.getDataCatalogConnection())) {
      return variables != null
          ? variables.resolve(group.getDataCatalogConnection())
          : group.getDataCatalogConnection();
    }
    return org.apache.hop.datavault.catalog.DvSourceCatalogService.resolvePreferredCatalogConnection(
        null, variables, metadataProvider);
  }

  private static void showError(Shell shell, Exception e) {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    new ErrorDialog(
        shell,
        BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Error.Title"),
        BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Error.Message"),
        e instanceof HopException ? e : new HopException(e));
  }
}
