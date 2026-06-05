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
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
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

  public static final String PARENT_WIDGET_ID =
      DataVaultConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID;

  private Composite parent;
  private Text wName;
  private GuiCompositeWidgets widgets;

  public DataVaultConfigurationEditor(
      HopGui hopGui,
      MetadataManager<DataVaultConfiguration> manager,
      DataVaultConfiguration metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    this.parent = parent;

    PropsUi props = PropsUi.getInstance();
    int margin = PropsUi.getMargin();
    int middle = props.getMiddlePct();

    // Name...
    //
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

    // Rest of the widgets are created automatically from the @GuiWidgetElement annotations
    //
    widgets = new GuiCompositeWidgets(manager.getVariables());
    widgets.createCompositeWidgets(getMetadata(), null, parent, PARENT_WIDGET_ID, wName);

    // Set content on the widgets...
    //
    setWidgetsContent();

    // Some widget set changed
    resetChanged();

    // Add changed listeners
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

  @Override
  public void setWidgetsContent() {
    DataVaultConfiguration meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    widgets.setWidgetsContents(meta, parent, PARENT_WIDGET_ID);
  }

  @Override
  public void getWidgetsContent(DataVaultConfiguration meta) {
    meta.setName(wName.getText());
    widgets.getWidgetsContents(meta, PARENT_WIDGET_ID);
  }
}
