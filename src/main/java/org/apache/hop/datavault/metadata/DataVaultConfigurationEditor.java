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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * The editor for Data Vault Configuration metadata.
 *
 * <p>This editor uses {@link GuiCompositeWidgets} to automatically create and populate the UI
 * elements based on the {@link GuiWidgetElement} annotations in {@link DataVaultConfiguration}.
 */
@GuiPlugin(description = "Editor for Data Vault Configuration metadata")
public class DataVaultConfigurationEditor extends MetadataEditor<DataVaultConfiguration> {

  private static final Class<?> PKG = DataVaultConfigurationEditor.class;

  private Text wName;
  private GuiCompositeWidgets widgets;
  private Composite wGeneralTabComp;
  private Composite wUnknownTabComp;
  private Composite wInvalidTabComp;
  private Composite wColumnsTabComp;

  public DataVaultConfigurationEditor(
      HopGui hopGui,
      MetadataManager<DataVaultConfiguration> manager,
      DataVaultConfiguration metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int margin = PropsUi.getMargin();
    int middle = props.getMiddlePct();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Name.Label"));
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
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.General.Label"),
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.General.ToolTip"));
    wUnknownTabComp =
        addTab(
            wTabFolder,
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.Unknown.Label"),
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.Unknown.ToolTip"));
    wInvalidTabComp =
        addTab(
            wTabFolder,
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.Invalid.Label"),
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.Invalid.ToolTip"));
    wColumnsTabComp =
        addTab(
            wTabFolder,
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.Columns.Label"),
            BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Tab.Columns.ToolTip"));

    widgets = new GuiCompositeWidgets(manager.getVariables());
    widgets.createCompositeWidgets(
        getMetadata(),
        null,
        wGeneralTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        getMetadata(),
        null,
        wUnknownTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        getMetadata(),
        null,
        wInvalidTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        getMetadata(),
        null,
        wColumnsTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID,
        null);

    wTabFolder.setSelection(0);

    setWidgetsContent();
    resetChanged();

    wName.addListener(SWT.Modify, e -> setChanged());
    widgets.setWidgetsListener(
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
    DataVaultConfiguration meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    widgets.setWidgetsContents(
        meta, wGeneralTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
    widgets.setWidgetsContents(
        meta, wUnknownTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID);
    widgets.setWidgetsContents(
        meta, wInvalidTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID);
    widgets.setWidgetsContents(
        meta, wColumnsTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID);
  }

  @Override
  public void getWidgetsContent(DataVaultConfiguration meta) {
    meta.setName(wName.getText());
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID);
  }
}