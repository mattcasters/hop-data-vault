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

package org.apache.hop.datavault.workflow.actions.validatedefinitions;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class ActionValidateResourceDefinitionsDialog extends ActionDialog {

  private static final Class<?> PKG = ActionValidateResourceDefinitions.class;

  /** GuiElements default ids match field names on the action. */
  private static final String WIDGET_RESOURCE_GROUP = "resourceDefinitionGroup";

  private static final String WIDGET_TARGET_VERSION = "targetCatalogVersion";
  private static final String WIDGET_BASELINE_VERSION = "baselineCatalogVersion";

  private final ActionValidateResourceDefinitions action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private Composite wSettingsComp;

  public ActionValidateResourceDefinitionsDialog(
      Shell parent,
      ActionValidateResourceDefinitions action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (Utils.isEmpty(action.getName())) {
      action.setName(BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(
        BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Title", action.getName()),
        action);

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.ACTION_VALIDATE_RESOURCE_DEFINITIONS);

    wSettingsComp = new Composite(shell, SWT.NONE);
    PropsUi.setLook(wSettingsComp);
    wSettingsComp.setLayout(new FormLayout());
    wSettingsComp.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wSpacer, margin))
            .right()
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());

    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        action,
        null,
        wSettingsComp,
        ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID,
        null);

    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            action.setChanged();
            if (WIDGET_RESOURCE_GROUP.equals(widgetId)) {
              // Keep action.resourceDefinitionGroup in sync so version lists re-resolve correctly.
              compositeWidgets.getWidgetsContents(
                  action, ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID);
              refreshCatalogVersionCombos();
            }
          }
        });

    setWidgetsContent();
    refreshCatalogVersionCombos();

    boolean changedBeforeOpen = action.hasChanged();
    focusActionName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    if (cancelled) {
      action.setChanged(changedBeforeOpen);
      return null;
    }
    return action;
  }

  @Override
  protected void onActionNameModified() {
    action.setChanged();
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    widgets.setWidgetsContents(
        action, wSettingsComp, ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID);
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    widgets.getWidgetsContents(
        action, ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID);
  }

  /**
   * Reload catalog version tags for ComboVar fields after the resource definition group changes.
   * Preserves any free-typed value (including variable expressions).
   */
  private void refreshCatalogVersionCombos() {
    if (widgets == null) {
      return;
    }
    String previousTarget = getComboText(WIDGET_TARGET_VERSION);
    String previousBaseline = getComboText(WIDGET_BASELINE_VERSION);

    List<String> tags =
        action.getCatalogVersionTagOptions(
            LogChannel.UI, HopGui.getInstance().getMetadataProvider());
    String[] items = tags.toArray(new String[0]);
    widgets.setComboValues(WIDGET_TARGET_VERSION, items);
    widgets.setComboValues(WIDGET_BASELINE_VERSION, items);

    setComboText(WIDGET_TARGET_VERSION, previousTarget);
    setComboText(WIDGET_BASELINE_VERSION, previousBaseline);
  }

  private String getComboText(String widgetId) {
    Control control = widgets.getWidgetsMap().get(widgetId);
    if (control instanceof ComboVar comboVar) {
      return Const.NVL(comboVar.getText(), "");
    }
    if (control instanceof Combo combo) {
      return Const.NVL(combo.getText(), "");
    }
    return "";
  }

  private void setComboText(String widgetId, String text) {
    Control control = widgets.getWidgetsMap().get(widgetId);
    String value = Const.NVL(text, "");
    if (control instanceof ComboVar comboVar) {
      comboVar.setText(value);
    } else if (control instanceof Combo combo) {
      combo.setText(value);
    }
  }

  private void ok() {
    cancelled = false;
    getWidgetsContent();
    action.setChanged();
    dispose();
  }

  private void cancel() {
    cancelled = true;
    dispose();
  }
}
