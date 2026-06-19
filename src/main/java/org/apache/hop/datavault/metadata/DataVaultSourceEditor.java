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

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.datavault.metadata.database.DataVaultSourceDatabasePanel;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Editor for Data Vault Source metadata with embedded physical source configuration. */
@GuiPlugin(description = "Editor for Data Vault Source metadata")
public class DataVaultSourceEditor extends MetadataEditor<DataVaultSource> {

  private static final Class<?> PKG = DataVaultSourceEditor.class;

  private Text wName;
  private GuiCompositeWidgets generalWidgets;
  private Composite wGeneralTabComp;
  private Composite wDatabaseTabComp;
  private DataVaultSourceDatabasePanel databasePanel;

  public DataVaultSourceEditor(
      HopGui hopGui, MetadataManager<DataVaultSource> manager, DataVaultSource metadata) {
    super(hopGui, manager, metadata);
    // MetadataPerspective calls createButtonsForButtonBar before createControl.
    databasePanel =
        new DataVaultSourceDatabasePanel(
            hopGui.getShell(),
            hopGui,
            manager.getVariables(),
            manager.getMetadataProvider(),
            getMetadata(),
            this::setChanged,
            this::getSourceNameFromWidgets,
            this::suggestSourceName);
  }

  private String getSourceNameFromWidgets() {
    if (wName != null && !wName.isDisposed()) {
      return Const.NVL(wName.getText(), "").trim();
    }
    return Const.NVL(getMetadata().getName(), "").trim();
  }

  private void suggestSourceName(String name) {
    getMetadata().setName(name);
    if (wName != null && !wName.isDisposed()) {
      wName.setText(name);
    }
    setChanged();
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int margin = PropsUi.getMargin();
    int middle = props.getMiddlePct();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "DataVaultSourceEditor.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin * 2);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    CTabFolder wTabFolder = new CTabFolder(parent, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wName, margin))
            .right()
            .bottom()
            .result());

    wGeneralTabComp =
        addTab(
            wTabFolder,
            BaseMessages.getString(PKG, "DataVaultSourceEditor.Tab.General.Label"),
            BaseMessages.getString(PKG, "DataVaultSourceEditor.Tab.General.ToolTip"));
    wDatabaseTabComp =
        addTab(
            wTabFolder,
            BaseMessages.getString(PKG, "DataVaultSourceEditor.Tab.Database.Label"),
            BaseMessages.getString(PKG, "DataVaultSourceEditor.Tab.Database.ToolTip"));

    generalWidgets = new GuiCompositeWidgets(manager.getVariables());
    generalWidgets.createCompositeWidgets(
        getMetadata(),
        null,
        wGeneralTabComp,
        DataVaultSource.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID,
        null);

    databasePanel.createControl(wDatabaseTabComp);

    wTabFolder.setSelection(0);

    setWidgetsContent();
    resetChanged();

    wName.addListener(SWT.Modify, e -> setChanged());
    generalWidgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            setChanged();
          }
        });
  }

  private Composite addTab(CTabFolder tabFolder, String title, String toolTip) {
    CTabItem tabItem = new CTabItem(tabFolder, SWT.NONE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(title);
    tabItem.setToolTipText(toolTip);

    Composite composite = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(composite);
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    composite.setLayout(layout);
    tabItem.setControl(composite);
    return composite;
  }

  @Override
  public void setWidgetsContent() {
    DataVaultSource meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    generalWidgets.setWidgetsContents(
        meta, wGeneralTabComp, DataVaultSource.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
    if (!(meta.getDvSourceOrDefault() instanceof DvDatabaseSource)) {
      meta.setSource(new DvDatabaseSource());
    }
    databasePanel.setWidgetsContent();
  }

  @Override
  public void getWidgetsContent(DataVaultSource meta) {
    meta.setName(wName.getText());
    generalWidgets.getWidgetsContents(meta, DataVaultSource.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
    databasePanel.getWidgetsContent();
  }

  @Override
  public void refreshOnDialogActivate() {
    databasePanel.refreshOnDialogActivate();
  }

  @Override
  public Button[] createButtonsForButtonBar(Composite parent) {
    return databasePanel.createImportButtons(parent);
  }
}