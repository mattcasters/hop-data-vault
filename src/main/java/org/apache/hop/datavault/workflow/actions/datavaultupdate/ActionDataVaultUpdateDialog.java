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

package org.apache.hop.datavault.workflow.actions.datavaultupdate;

import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class ActionDataVaultUpdateDialog extends ActionDialog {
  private static final Class<?> PKG = ActionDataVaultUpdate.class;

  public static final String PARENT_WIDGET_ID = ActionDataVaultUpdate.GUI_PLUGIN_ELEMENT_PARENT_ID;

  private final ActionDataVaultUpdate action;
  private GuiCompositeWidgets widgets;

  public ActionDataVaultUpdateDialog(
      Shell parent, ActionDataVaultUpdate action, WorkflowMeta workflowMeta, IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;

    if (this.action.getName() == null || this.action.getName().isEmpty()) {
      this.action.setName(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Name"));
    }
  }

  @Override
  public IAction open() {
    Shell parent = getParent();
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Title", action.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    // Buttons at the bottom
    Button wOK = new Button(shell, SWT.PUSH);
    wOK.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOK.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOK, wCancel}, margin, null);

    // Name
    Label wlName = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.left = new FormAttachment(0, 0);
    fdlName.top = new FormAttachment(0, margin);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(middle, 0);
    fdName.top = new FormAttachment(0, margin);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    // Rest of the widgets are created automatically from the @GuiWidgetElement annotations
    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(action, null, shell, PARENT_WIDGET_ID, wName);

    // Set content on the widgets
    setWidgetsContent();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return action;
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    widgets.setWidgetsContents(action, shell, PARENT_WIDGET_ID);
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    widgets.getWidgetsContents(action, PARENT_WIDGET_ID);
  }

  private void ok() {
    getWidgetsContent();
    action.setChanged();
    dispose();
  }

  private void cancel() {
    dispose();
  }
}
