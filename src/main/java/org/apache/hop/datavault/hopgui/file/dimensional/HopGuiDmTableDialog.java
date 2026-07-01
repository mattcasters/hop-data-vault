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

package org.apache.hop.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.metadata.dimensional.DmAccumulatingSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.DmAggregateFact;
import org.apache.hop.datavault.metadata.dimensional.DmBridge;
import org.apache.hop.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAttribute;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactlessFact;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmPeriodicSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.IDmFactLikeTable;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmModelLoadSupport;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactMeasure;
import org.apache.hop.datavault.metadata.dimensional.DmLayoutSupport;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmJunkSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a dimension or fact table on the dimensional model canvas. */
public class HopGuiDmTableDialog {
  private static final Class<?> PKG = HopGuiDmTableDialog.class;

  private final Shell parent;
  private final IDmTable input;
  private final DimensionalModel model;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private Shell shell;

  private CTabFolder wTabFolder;
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private MetaSelectionLine<DatabaseMeta> wSourceConnection;
  private TextComposite wSourceSql;
  private Combo wScdType;
  private Combo wSurrogateKeyStrategy;
  private Text wSurrogateKeyField;
  private Text wSurrogateKeySourceField;
  private TableView wNaturalKeys;
  private ColumnInfo naturalKeyFieldColumn;
  private TableView wAttributes;
  private ColumnInfo attributeFieldColumn;
  private TableView wOutriggers;
  private TableView wDimensionRoles;
  private Combo wDimensionLookupDateField;
  private ColumnInfo dimensionJoinSourceFieldColumn;
  private TableView wMeasures;
  private ColumnInfo measureFieldColumn;
  private Text wReferencedModelFilename;
  private Combo wReferencedDimension;

  private boolean ok;
  private final boolean dimension;
  private final boolean dimensionAlias;
  private final boolean junk;
  private final boolean bridge;
  private final boolean factLike;
  private final boolean factless;

  private int margin;
  private int middle;

  public HopGuiDmTableDialog(
      Shell parent,
      IDmTable table,
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = table;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    DmTableType tableType = table.getTableType();
    this.dimension = tableType == DmTableType.DIMENSION;
    this.dimensionAlias = tableType == DmTableType.DIMENSION_ALIAS;
    this.junk = tableType == DmTableType.JUNK_DIMENSION;
    this.bridge = tableType == DmTableType.BRIDGE;
    this.factLike = table instanceof IDmFactLikeTable;
    this.factless = tableType == DmTableType.FACTLESS_FACT;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "HopGuiDmTableDialog.Title",
            Const.NVL(input.getName(), input.getTableType().name())));
    shell.setSize(
        620,
        dimensionAlias ? 360 : dimension ? 620 : junk ? 480 : bridge ? 520 : factless ? 520 : 580);

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();
    middle = PropsUi.getInstance().getMiddlePct();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wOk, -2 * margin).result());

    addGeneralTab();
    if (!dimensionAlias) {
      addSourceTab();
    }
    if (dimensionAlias) {
      addDimensionAliasTab();
    } else if (junk) {
      addJunkKeyFieldsTab();
    } else if (bridge) {
      addBridgeDimensionsTab();
    } else if (dimension) {
      addNaturalKeysTab();
      addAttributesTab();
      addOutriggersTab();
    } else if (factLike) {
      addDimensionRolesTab();
      if (!factless) {
        addMeasuresTab();
      }
    }

    wTabFolder.setSelection(0);
    shell.layout(true, true);

    getData();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void addGeneralTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.General.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.General.ToolTip"));

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlTableName = new Label(comp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    wlTableName.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());

    wTableName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    wTableName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wTableName, margin).right(middle, -margin).result());

    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wTableName, margin).right().result());

    if (dimensionAlias) {
      wTableName.setEditable(false);
    }

    if (dimension) {
      Label wlScdType = new Label(comp, SWT.RIGHT);
      wlScdType.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.ScdType.Label"));
      PropsUi.setLook(wlScdType);
      wlScdType.setLayoutData(
          new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());

      wScdType = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
      PropsUi.setLook(wScdType);
      EnumDialogSupport.populateCombo(wScdType, DmDimensionScdType.class);
      wScdType.setLayoutData(
          new FormDataBuilder().left(middle, 0).top(wDescription, margin).right().result());
      addSurrogateKeyControls(comp, wScdType);
    } else if (junk) {
      addJunkSurrogateKeyControls(comp, wDescription);
    }
  }

  private void addSurrogateKeyControls(Composite comp, org.eclipse.swt.widgets.Control topControl) {
    Label wlSurrogateKeyStrategy = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyStrategy.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyStrategy.Label"));
    wlSurrogateKeyStrategy.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyStrategy.ToolTip"));
    PropsUi.setLook(wlSurrogateKeyStrategy);
    wlSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left().top(topControl, margin).right(middle, -margin).result());

    wSurrogateKeyStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyStrategy);
    EnumDialogSupport.populateCombo(wSurrogateKeyStrategy, DmSurrogateKeyStrategy.class);
    wSurrogateKeyStrategy.addListener(SWT.Selection, e -> refreshSurrogateKeyFieldState());
    wSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(topControl, margin).right().result());

    Label wlSurrogateKeyField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyField.Label"));
    PropsUi.setLook(wlSurrogateKeyField);
    wlSurrogateKeyField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyStrategy, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeyField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyField);
    wSurrogateKeyField.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSurrogateKeyStrategy, margin)
            .right()
            .result());

    Label wlSurrogateKeySourceField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeySourceField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeySourceField.Label"));
    wlSurrogateKeySourceField.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeySourceField.ToolTip"));
    PropsUi.setLook(wlSurrogateKeySourceField);
    wlSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyField, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeySourceField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeySourceField);
    wSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSurrogateKeyField, margin)
            .right()
            .result());
    refreshSurrogateKeyFieldState();
  }

  private void addJunkSurrogateKeyControls(Composite comp, org.eclipse.swt.widgets.Control topControl) {
    Label wlSurrogateKeyStrategy = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyStrategy.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyStrategy.Label"));
    PropsUi.setLook(wlSurrogateKeyStrategy);
    wlSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left().top(topControl, margin).right(middle, -margin).result());

    wSurrogateKeyStrategy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyStrategy);
    EnumDialogSupport.populateCombo(wSurrogateKeyStrategy, DmJunkSurrogateKeyStrategy.class);
    wSurrogateKeyStrategy.addListener(SWT.Selection, e -> refreshSurrogateKeyFieldState());
    wSurrogateKeyStrategy.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(topControl, margin).right().result());

    Label wlSurrogateKeyField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeyField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeyField.Label"));
    PropsUi.setLook(wlSurrogateKeyField);
    wlSurrogateKeyField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyStrategy, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeyField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeyField);
    wSurrogateKeyField.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSurrogateKeyStrategy, margin)
            .right()
            .result());

    Label wlSurrogateKeySourceField = new Label(comp, SWT.RIGHT);
    wlSurrogateKeySourceField.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SurrogateKeySourceField.Label"));
    PropsUi.setLook(wlSurrogateKeySourceField);
    wlSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSurrogateKeyField, margin)
            .right(middle, -margin)
            .result());

    wSurrogateKeySourceField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSurrogateKeySourceField);
    wSurrogateKeySourceField.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSurrogateKeyField, margin)
            .right()
            .result());
    refreshSurrogateKeyFieldState();
  }

  private void refreshSurrogateKeyFieldState() {
    if (wSurrogateKeyStrategy == null || wSurrogateKeyStrategy.isDisposed()) {
      return;
    }
    boolean dimensionTable = dimension && input instanceof DmDimension;
    boolean junkTable = junk && input instanceof DmJunkDimension;
    if (!dimensionTable && !junkTable) {
      return;
    }
    boolean noneStrategy = false;
    boolean sourceStrategy = false;
    if (dimensionTable) {
      DmSurrogateKeyStrategy strategy =
          EnumDialogSupport.readCombo(
              wSurrogateKeyStrategy, DmSurrogateKeyStrategy.class, DmSurrogateKeyStrategy.NONE);
      noneStrategy = strategy == DmSurrogateKeyStrategy.NONE;
      sourceStrategy = strategy == DmSurrogateKeyStrategy.USE_SOURCE_FIELD;
    } else if (junkTable) {
      DmJunkSurrogateKeyStrategy strategy =
          EnumDialogSupport.readCombo(
              wSurrogateKeyStrategy,
              DmJunkSurrogateKeyStrategy.class,
              DmJunkSurrogateKeyStrategy.AUTO_INCREMENT);
      sourceStrategy = strategy == DmJunkSurrogateKeyStrategy.USE_SOURCE_FIELD;
    }
    if (wSurrogateKeyField != null && !wSurrogateKeyField.isDisposed()) {
      wSurrogateKeyField.setEnabled(!noneStrategy);
    }
    if (wSurrogateKeySourceField != null && !wSurrogateKeySourceField.isDisposed()) {
      wSurrogateKeySourceField.setEnabled(sourceStrategy);
    }
  }

  private void addDimensionAliasTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionAlias.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionAlias.ToolTip"));

    Label wlReferencedModelFilename = new Label(comp, SWT.RIGHT);
    wlReferencedModelFilename.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Label"));
    wlReferencedModelFilename.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.ToolTip"));
    PropsUi.setLook(wlReferencedModelFilename);
    wlReferencedModelFilename.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    Button wBrowseReferencedModel = new Button(comp, SWT.PUSH);
    wBrowseReferencedModel.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Browse.Label"));
    wBrowseReferencedModel.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedModelFilename.Browse.ToolTip"));
    PropsUi.setLook(wBrowseReferencedModel);
    wBrowseReferencedModel.setLayoutData(
        new FormDataBuilder().right().top(0, margin).result());
    wBrowseReferencedModel.addListener(SWT.Selection, e -> browseReferencedModelFilename());

    Label wlReferencedDimension = new Label(comp, SWT.RIGHT);
    wlReferencedDimension.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedDimension.Label"));
    PropsUi.setLook(wlReferencedDimension);
    wlReferencedDimension.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wReferencedModelFilename, margin)
            .right(middle, -margin)
            .result());

    wReferencedModelFilename = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wReferencedModelFilename);
    wReferencedModelFilename.addListener(SWT.Modify, e -> refreshReferencedDimensionChoices());
    wReferencedModelFilename.setLayoutData(
            new FormDataBuilder().left(middle, 0).top(0, margin).right(wBrowseReferencedModel, -margin).result());

    wReferencedDimension = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wReferencedDimension);
    refreshReferencedDimensionChoices();
    wReferencedDimension.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wReferencedModelFilename, margin)
            .right()
            .result());
  }

  private void browseReferencedModelFilename() {
    String selectedFile =
        BaseDialog.presentFileDialog(
            false,
            shell,
            new String[] {"*" + HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION},
            new String[] {HopDimensionalFileType.DIMENSIONAL_FILE_TYPE_DESCRIPTION},
            false);
    if (Utils.isEmpty(selectedFile)) {
      return;
    }
    wReferencedModelFilename.setText(variables.resolve(selectedFile));
    refreshReferencedDimensionChoices();
  }

  private void refreshReferencedDimensionChoices() {
    if (wReferencedDimension == null || wReferencedDimension.isDisposed()) {
      return;
    }
    String previousSelection = wReferencedDimension.getText();
    wReferencedDimension.setItems(listReferencedDimensionNames());
    if (!Utils.isEmpty(previousSelection)) {
      int index = wReferencedDimension.indexOf(previousSelection);
      if (index >= 0) {
        wReferencedDimension.select(index);
      }
    }
  }

  private String[] listReferencedDimensionNames() {
    String externalModelPath =
        wReferencedModelFilename != null && !wReferencedModelFilename.isDisposed()
            ? variables.resolve(wReferencedModelFilename.getText())
            : null;
    if (!Utils.isEmpty(externalModelPath)) {
      try {
        DimensionalModel externalModel =
            DmModelLoadSupport.loadDimensionalModel(
                externalModelPath,
                model != null ? model.getFilename() : null,
                variables,
                metadataProvider);
        return DmModelLoadSupport.listBaseDimensionNames(externalModel);
      } catch (HopException e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(
                PKG, "HopGuiDmTableDialog.ReferencedModelFilename.LoadError.Header"),
            BaseMessages.getString(
                PKG, "HopGuiDmTableDialog.ReferencedModelFilename.LoadError.Message"),
            e);
      }
    }
    return listBaseDimensionNames();
  }

  private void addSourceTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Source.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Source.ToolTip"));

    wSourceConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DatabaseMeta.class,
            comp,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceConnection.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceConnection.ToolTip"));
    wSourceConnection.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().result());
    try {
      wSourceConnection.fillItems();
    } catch (HopException e) {
      // best effort
    }

    Label wlSourceSql = new Label(comp, SWT.RIGHT | SWT.TOP);
    wlSourceSql.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourceSql.Label"));
    PropsUi.setLook(wlSourceSql);
    wlSourceSql.setLayoutData(
        new FormDataBuilder().left().top(wSourceConnection, margin).right(middle, -margin).result());

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSourceSql = new StyledTextComp(variables, comp, sqlStyle);
    } else {
      wSourceSql = new SQLStyledTextComp(variables, comp, sqlStyle);
      wSourceSql.addLineStyleListener(getSqlReservedWords());
    }
    PropsUi.setLook(wSourceSql, Props.WIDGET_STYLE_FIXED);

    Button wSourcePreview = new Button(comp, SWT.PUSH);
    wSourcePreview.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePreview.Label"));
    wSourcePreview.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.SourcePreview.ToolTip"));
    PropsUi.setLook(wSourcePreview);
    wSourcePreview.setLayoutData(new FormDataBuilder().left(middle, 0).bottom().result());
    wSourcePreview.addListener(SWT.Selection, e -> previewSourceSql());

    wSourceSql.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSourceConnection, margin)
            .right()
            .bottom(wSourcePreview, -margin)
            .result());
  }

  private List<String> getSqlReservedWords() {
    DatabaseMeta connection = resolveSourceDatabaseMeta();
    if (connection == null) {
      return List.of();
    }
    return List.of(connection.getReservedWords());
  }

  private DatabaseMeta resolveSourceDatabaseMeta() {
    if (metadataProvider == null) {
      return null;
    }
    String connectionName = null;
    if (wSourceConnection != null && !wSourceConnection.isDisposed()) {
      connectionName = variables.resolve(wSourceConnection.getText());
    }
    if (Utils.isEmpty(connectionName)) {
      DimensionalConfiguration config =
          model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
      connectionName = input.getSourceOrDefault().resolveSourceConnection(config, variables);
    }
    if (Utils.isEmpty(connectionName)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (HopException e) {
      return null;
    }
  }

  private void addJunkKeyFieldsTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.JunkKeyFields.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.JunkKeyFields.ToolTip"));

    ColumnInfo[] keyFieldColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.JunkKeyFields.Column.Field"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wNaturalKeys =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            keyFieldColumns,
            3,
            null,
            PropsUi.getInstance());
    wNaturalKeys.setLayoutData(new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addBridgeDimensionsTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.BridgeDimensions.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.BridgeDimensions.ToolTip"));

    String[] dimensionNames = listDimensionNames();
    ColumnInfo[] bridgeColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.BridgeDimensions.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.BridgeDimensions.Column.ForeignKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wOutriggers =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            bridgeColumns,
            2,
            null,
            PropsUi.getInstance());
    wOutriggers.setLayoutData(new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addNaturalKeysTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.NaturalKeys.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.NaturalKeys.ToolTip"));

    naturalKeyFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] naturalKeyColumns = new ColumnInfo[] {naturalKeyFieldColumn};

    Button wGetKeys = new Button(comp, SWT.PUSH);
    wGetKeys.setText(BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.GetKeys.Label"));
    wGetKeys.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.GetKeys.ToolTip"));
    PropsUi.setLook(wGetKeys);
    wGetKeys.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetKeys.addListener(SWT.Selection, e -> getNaturalKeysFromSource());

    wNaturalKeys =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            naturalKeyColumns,
            3,
            null,
            PropsUi.getInstance());
    wNaturalKeys.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wGetKeys, -margin).result());
  }

  private void addAttributesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Attributes.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Attributes.ToolTip"));

    attributeFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] attributeColumns =
        new ColumnInfo[] {
          attributeFieldColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.ScdPolicy"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              EnumDialogSupport.comboOptions(DmScdUpdatePolicy.class),
              true)
        };

    Button wGetAttributes = new Button(comp, SWT.PUSH);
    wGetAttributes.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.GetAttributes.Label"));
    wGetAttributes.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.GetAttributes.ToolTip"));
    PropsUi.setLook(wGetAttributes);
    wGetAttributes.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetAttributes.addListener(SWT.Selection, e -> getAttributesFromSource());

    wAttributes =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            attributeColumns,
            3,
            null,
            PropsUi.getInstance());
    wAttributes.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wGetAttributes, -margin).result());
  }

  private void addOutriggersTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Outriggers.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Outriggers.ToolTip"));

    String[] dimensionNames = listDimensionNames();
    ColumnInfo[] outriggerColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Outriggers.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Outriggers.Column.ForeignKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wOutriggers =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            outriggerColumns,
            2,
            null,
            PropsUi.getInstance());
    wOutriggers.setLayoutData(new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addDimensionRolesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionRoles.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionRoles.ToolTip"));

    String[] dimensionNames = listDimensionNames();
    dimensionJoinSourceFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.SourceField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    dimensionJoinSourceFieldColumn.setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.SourceField.ToolTip"));
    ColumnInfo factKeyColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.ForeignKey"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    factKeyColumn.setToolTip(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.ForeignKey.ToolTip"));
    ColumnInfo preloadCacheColumn =
        new ColumnInfo(
            BaseMessages.getString(
                PKG, "HopGuiDmTableDialog.DimensionRoles.Column.PreloadCache"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {"N", "Y"},
            true);
    preloadCacheColumn.setToolTip(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DimensionRoles.Column.PreloadCache.ToolTip"));
    ColumnInfo[] roleColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          dimensionJoinSourceFieldColumn,
          factKeyColumn,
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiDmTableDialog.DimensionRoles.Column.TruncateToDate"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"N", "Y"},
              true),
          preloadCacheColumn
        };

    Label wlDimensionLookupDate = new Label(comp, SWT.RIGHT);
    wlDimensionLookupDate.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.LookupDateField.Label"));
    PropsUi.setLook(wlDimensionLookupDate);
    wlDimensionLookupDate.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wlDimensionLookupDate.setToolTipText(
        BaseMessages.getString(
            PKG, "HopGuiDmTableDialog.DimensionRoles.LookupDateField.ToolTip"));

    wDimensionLookupDateField = new Combo(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDimensionLookupDateField);
    wDimensionLookupDateField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Button wGetJoins = new Button(comp, SWT.PUSH);
    wGetJoins.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.GetJoins.Label"));
    wGetJoins.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.GetJoins.ToolTip"));
    PropsUi.setLook(wGetJoins);
    wGetJoins.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetJoins.addListener(SWT.Selection, e -> getJoinsFromSource());

    wDimensionRoles =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            roleColumns,
            3,
            null,
            PropsUi.getInstance());
    wDimensionRoles.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wDimensionLookupDateField, margin)
            .right()
            .bottom(wGetJoins, -margin)
            .result());
  }

  private void addMeasuresTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Measures.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Measures.ToolTip"));

    measureFieldColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.Column.Field"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    ColumnInfo[] measureColumns =
        new ColumnInfo[] {
          measureFieldColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.Column.Additive"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"},
              true)
        };

    Button wGetMeasures = new Button(comp, SWT.PUSH);
    wGetMeasures.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.GetMeasures.Label"));
    wGetMeasures.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.GetMeasures.ToolTip"));
    PropsUi.setLook(wGetMeasures);
    wGetMeasures.setLayoutData(new FormDataBuilder().left().bottom().result());
    wGetMeasures.addListener(SWT.Selection, e -> getMeasuresFromSource());

    wMeasures =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            measureColumns,
            3,
            null,
            PropsUi.getInstance());
    wMeasures.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wGetMeasures, -margin).result());
  }

  private String[] listDimensionNames() {
    List<String> names = new ArrayList<>();
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        if ((table instanceof DmDimension || table instanceof DmDimensionAlias)
            && !Utils.isEmpty(table.getName())) {
          if (input == null || !table.getName().equals(input.getName())) {
            names.add(table.getName());
          }
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] listBaseDimensionNames() {
    List<String> names = new ArrayList<>();
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        if (table instanceof DmDimension && !Utils.isEmpty(table.getName())) {
          if (input == null || !table.getName().equals(input.getName())) {
            names.add(table.getName());
          }
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getTableName())) {
      wTableName.setText(input.getTableName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    if (!dimensionAlias) {
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceConnection())) {
        wSourceConnection.setText(input.getSourceOrDefault().getSourceConnection());
      }
      if (!Utils.isEmpty(input.getSourceOrDefault().getSourceSql())) {
        wSourceSql.setText(input.getSourceOrDefault().getSourceSql());
      }
    }

    if (junk && input instanceof DmJunkDimension junkDimension) {
      if (junkDimension.getSurrogateKeyStrategy() != null) {
        EnumDialogSupport.selectCombo(wSurrogateKeyStrategy, junkDimension.getSurrogateKeyStrategy());
      } else {
        EnumDialogSupport.selectCombo(
            wSurrogateKeyStrategy, DmSurrogateKeySupport.resolveJunkStrategy(junkDimension));
      }
      if (wSurrogateKeyField != null && !Utils.isEmpty(junkDimension.getSurrogateKeyField())) {
        wSurrogateKeyField.setText(junkDimension.getSurrogateKeyField());
      }
      if (wSurrogateKeySourceField != null
          && !Utils.isEmpty(junkDimension.getSurrogateKeySourceField())) {
        wSurrogateKeySourceField.setText(junkDimension.getSurrogateKeySourceField());
      }
      refreshSurrogateKeyFieldState();
      wNaturalKeys.clearAll();
      for (DmNaturalKeyField keyField : junkDimension.getKeyFieldsOrEmpty()) {
        if (keyField == null || Utils.isEmpty(keyField.getFieldName())) {
          continue;
        }
        TableItem item = new TableItem(wNaturalKeys.table, SWT.NONE);
        item.setText(1, keyField.getFieldName());
      }
      wNaturalKeys.removeEmptyRows();
      wNaturalKeys.setRowNums();
      wNaturalKeys.optWidth(true);
    }

    if (bridge && input instanceof DmBridge dmBridge) {
      wOutriggers.clearAll();
      for (DmBridgeDimensionRef ref : dmBridge.getDimensionRefsOrEmpty()) {
        if (ref == null || Utils.isEmpty(ref.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wOutriggers.table, SWT.NONE);
        item.setText(1, ref.getDimensionTableName());
        if (!Utils.isEmpty(ref.getForeignKeyColumn())) {
          item.setText(2, ref.getForeignKeyColumn());
        }
      }
      wOutriggers.removeEmptyRows();
      wOutriggers.setRowNums();
      wOutriggers.optWidth(true);
    }

    if (dimensionAlias && input instanceof DmDimensionAlias alias) {
      if (wReferencedModelFilename != null
          && !Utils.isEmpty(alias.getReferencedModelFilename())) {
        wReferencedModelFilename.setText(alias.getReferencedModelFilename());
      }
      refreshReferencedDimensionChoices();
      if (wReferencedDimension != null && !Utils.isEmpty(alias.getReferencedDimensionName())) {
        int index = wReferencedDimension.indexOf(alias.getReferencedDimensionName());
        if (index >= 0) {
          wReferencedDimension.select(index);
        }
      }
    }

    if (dimension && input instanceof DmDimension dmDimension) {
      EnumDialogSupport.selectCombo(wScdType, dmDimension.getScdTypeOrDefault());
      if (dmDimension.getSurrogateKeyStrategy() != null) {
        EnumDialogSupport.selectCombo(wSurrogateKeyStrategy, dmDimension.getSurrogateKeyStrategy());
      } else {
        EnumDialogSupport.selectCombo(
            wSurrogateKeyStrategy, DmSurrogateKeySupport.resolveStrategy(dmDimension));
      }
      if (wSurrogateKeyField != null && !Utils.isEmpty(dmDimension.getSurrogateKeyField())) {
        wSurrogateKeyField.setText(dmDimension.getSurrogateKeyField());
      }
      if (wSurrogateKeySourceField != null
          && !Utils.isEmpty(dmDimension.getSurrogateKeySourceField())) {
        wSurrogateKeySourceField.setText(dmDimension.getSurrogateKeySourceField());
      }
      refreshSurrogateKeyFieldState();
      wNaturalKeys.clearAll();
      for (DmNaturalKeyField naturalKey : dmDimension.getNaturalKeysOrEmpty()) {
        if (naturalKey == null || Utils.isEmpty(naturalKey.getFieldName())) {
          continue;
        }
        TableItem item = new TableItem(wNaturalKeys.table, SWT.NONE);
        item.setText(1, naturalKey.getFieldName());
      }
      wNaturalKeys.removeEmptyRows();
      wNaturalKeys.setRowNums();
      wNaturalKeys.optWidth(true);

      wAttributes.clearAll();
      for (DmDimensionAttribute attribute : dmDimension.getAttributesOrEmpty()) {
        if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
          continue;
        }
        TableItem item = new TableItem(wAttributes.table, SWT.NONE);
        item.setText(1, attribute.getFieldName());
        if (attribute.getScdUpdatePolicy() != null) {
          item.setText(2, EnumDialogSupport.descriptionOf(attribute.getScdUpdatePolicy()));
        }
      }
      wAttributes.removeEmptyRows();
      wAttributes.setRowNums();
      wAttributes.optWidth(true);

      wOutriggers.clearAll();
      for (DmDimensionOutriggerRef outrigger : dmDimension.getOutriggersOrEmpty()) {
        if (outrigger == null || Utils.isEmpty(outrigger.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wOutriggers.table, SWT.NONE);
        item.setText(1, outrigger.getDimensionTableName());
        if (!Utils.isEmpty(outrigger.getForeignKeyColumn())) {
          item.setText(2, outrigger.getForeignKeyColumn());
        }
      }
      wOutriggers.removeEmptyRows();
      wOutriggers.setRowNums();
      wOutriggers.optWidth(true);

      refreshFieldComboChoices();
    }

    if (factLike && input instanceof IDmFactLikeTable factLikeTable) {
      if (wDimensionLookupDateField != null && input instanceof DmTableBase table) {
        refreshDimensionLookupDateComboChoices();
        if (!Utils.isEmpty(table.getDimensionLookupDateField())) {
          wDimensionLookupDateField.setText(table.getDimensionLookupDateField());
        }
      }
      wDimensionRoles.clearAll();
      for (DmFactDimensionRole role : factLikeTable.getDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wDimensionRoles.table, SWT.NONE);
        item.setText(1, role.getDimensionTableName());
        if (!Utils.isEmpty(role.getSourceFieldName())) {
          item.setText(2, role.getSourceFieldName());
        }
        if (!Utils.isEmpty(role.getForeignKeyColumn())) {
          item.setText(3, role.getForeignKeyColumn());
        }
        item.setText(4, role.isTruncateToDateKey() ? "Y" : "N");
        item.setText(5, role.isPreloadLookupCache() ? "Y" : "N");
      }
      wDimensionRoles.removeEmptyRows();
      wDimensionRoles.setRowNums();
      wDimensionRoles.optWidth(true);
      refreshDimensionJoinComboChoices();

      if (!factless && wMeasures != null) {
        wMeasures.clearAll();
        for (DmFactMeasure measure : factLikeTable.getMeasuresOrEmpty()) {
          if (measure == null || Utils.isEmpty(measure.getFieldName())) {
            continue;
          }
          TableItem item = new TableItem(wMeasures.table, SWT.NONE);
          item.setText(1, measure.getFieldName());
          item.setText(2, measure.isAdditive() ? "Y" : "N");
        }
        wMeasures.removeEmptyRows();
        wMeasures.setRowNums();
        wMeasures.optWidth(true);

        refreshMeasureComboChoices();
      }
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setDescription(wDescription.getText());
    if (!dimensionAlias) {
      input.setTableName(wTableName.getText());
      input.getSourceOrDefault().setSourceConnection(wSourceConnection.getText());
      input.getSourceOrDefault().setSourceSql(wSourceSql.getText());
    }

    if (junk && input instanceof DmJunkDimension junkDimension) {
      if (wSurrogateKeyStrategy != null) {
        junkDimension.setSurrogateKeyStrategy(
            EnumDialogSupport.readCombo(
                wSurrogateKeyStrategy,
                DmJunkSurrogateKeyStrategy.class,
                DmJunkSurrogateKeyStrategy.AUTO_INCREMENT));
      }
      if (wSurrogateKeyField != null) {
        junkDimension.setSurrogateKeyField(wSurrogateKeyField.getText());
      }
      if (wSurrogateKeySourceField != null) {
        junkDimension.setSurrogateKeySourceField(wSurrogateKeySourceField.getText());
      }
      junkDimension.getKeyFields().clear();
      for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
        String fieldName = item.getText(1);
        if (!Utils.isEmpty(fieldName)) {
          junkDimension.getKeyFields().add(new DmNaturalKeyField(fieldName));
        }
      }
    }

    if (bridge && input instanceof DmBridge dmBridge) {
      dmBridge.getDimensionRefs().clear();
      for (TableItem item : wOutriggers.getNonEmptyItems()) {
        String dimensionName = item.getText(1);
        if (Utils.isEmpty(dimensionName)) {
          continue;
        }
        dmBridge.getDimensionRefs().add(new DmBridgeDimensionRef(dimensionName, item.getText(2)));
      }
    }

    if (dimensionAlias && input instanceof DmDimensionAlias alias) {
      if (wReferencedModelFilename != null) {
        alias.setReferencedModelFilename(wReferencedModelFilename.getText());
      }
      if (wReferencedDimension != null) {
        alias.setReferencedDimensionName(wReferencedDimension.getText());
      }
      alias.syncPhysicalTableName(model, variables, metadataProvider);
      input.setTableName(alias.getTableName());
    }

    if (dimension && input instanceof DmDimension dmDimension) {
      dmDimension.setScdType(
          EnumDialogSupport.readCombo(
              wScdType, DmDimensionScdType.class, DmDimensionScdType.TYPE1));
      if (wSurrogateKeyStrategy != null) {
        dmDimension.setSurrogateKeyStrategy(
            EnumDialogSupport.readCombo(
                wSurrogateKeyStrategy,
                DmSurrogateKeyStrategy.class,
                DmSurrogateKeyStrategy.AUTO_INCREMENT));
      }
      if (wSurrogateKeyField != null) {
        dmDimension.setSurrogateKeyField(wSurrogateKeyField.getText());
      }
      if (wSurrogateKeySourceField != null) {
        dmDimension.setSurrogateKeySourceField(wSurrogateKeySourceField.getText());
      }
      dmDimension.getNaturalKeys().clear();
      for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
        String fieldName = item.getText(1);
        if (!Utils.isEmpty(fieldName)) {
          dmDimension.getNaturalKeys().add(new DmNaturalKeyField(fieldName));
        }
      }
      dmDimension.getAttributes().clear();
      for (TableItem item : wAttributes.getNonEmptyItems()) {
        String fieldName = item.getText(1);
        if (Utils.isEmpty(fieldName)) {
          continue;
        }
        DmScdUpdatePolicy policy =
            EnumDialogSupport.lookupText(
                item.getText(2), DmScdUpdatePolicy.class, DmScdUpdatePolicy.TYPE1);
        dmDimension.getAttributes().add(new DmDimensionAttribute(fieldName, policy));
      }
      dmDimension.getOutriggers().clear();
      for (TableItem item : wOutriggers.getNonEmptyItems()) {
        String dimensionName = item.getText(1);
        if (Utils.isEmpty(dimensionName)) {
          continue;
        }
        dmDimension.getOutriggers().add(new DmDimensionOutriggerRef(dimensionName, item.getText(2)));
      }
    }

    if (factLike && input instanceof DmTableBase table) {
      if (wDimensionLookupDateField != null) {
        table.setDimensionLookupDateField(wDimensionLookupDateField.getText());
      }
    }

    if (factLike && input instanceof IDmFactLikeTable factLike) {
      if (factLike instanceof DmFact dmFact) {
        dmFact.getDimensionRoles().clear();
        dmFact.getDimensionRoles().addAll(readDimensionRolesFromTable());
        dmFact.getMeasures().clear();
        if (!factless && wMeasures != null) {
          for (TableItem item : wMeasures.getNonEmptyItems()) {
            String fieldName = item.getText(1);
            if (Utils.isEmpty(fieldName)) {
              continue;
            }
            boolean additive = !"N".equalsIgnoreCase(item.getText(2));
            dmFact.getMeasures().add(new DmFactMeasure(fieldName, additive));
          }
        }
      } else if (factLike instanceof DmFactlessFact factlessFact) {
        factlessFact.getDimensionRoles().clear();
        factlessFact.getDimensionRoles().addAll(readDimensionRolesFromTable());
      } else if (factLike instanceof DmPeriodicSnapshotFact periodicFact) {
        periodicFact.getDimensionRoles().clear();
        periodicFact.getDimensionRoles().addAll(readDimensionRolesFromTable());
        periodicFact.getMeasures().clear();
        if (wMeasures != null) {
          for (TableItem item : wMeasures.getNonEmptyItems()) {
            String fieldName = item.getText(1);
            if (Utils.isEmpty(fieldName)) {
              continue;
            }
            boolean additive = !"N".equalsIgnoreCase(item.getText(2));
            periodicFact.getMeasures().add(new DmFactMeasure(fieldName, additive));
          }
        }
      } else if (factLike instanceof DmAccumulatingSnapshotFact accumulatingFact) {
        accumulatingFact.getDimensionRoles().clear();
        accumulatingFact.getDimensionRoles().addAll(readDimensionRolesFromTable());
        accumulatingFact.getMeasures().clear();
        if (wMeasures != null) {
          for (TableItem item : wMeasures.getNonEmptyItems()) {
            String fieldName = item.getText(1);
            if (Utils.isEmpty(fieldName)) {
              continue;
            }
            boolean additive = !"N".equalsIgnoreCase(item.getText(2));
            accumulatingFact.getMeasures().add(new DmFactMeasure(fieldName, additive));
          }
        }
      } else if (factLike instanceof DmAggregateFact aggregateFact) {
        aggregateFact.getDimensionRoles().clear();
        aggregateFact.getDimensionRoles().addAll(readDimensionRolesFromTable());
        aggregateFact.getMeasures().clear();
        if (wMeasures != null) {
          for (TableItem item : wMeasures.getNonEmptyItems()) {
            String fieldName = item.getText(1);
            if (Utils.isEmpty(fieldName)) {
              continue;
            }
            boolean additive = !"N".equalsIgnoreCase(item.getText(2));
            aggregateFact.getMeasures().add(new DmFactMeasure(fieldName, additive));
          }
        }
      }
    }

    ok = true;
    shell.dispose();
  }

  private void cancel() {
    ok = false;
    shell.dispose();
  }

  private void previewSourceSql() {
    DmSourceSqlGuiSupport.previewSourceSql(
        shell,
        variables,
        metadataProvider,
        resolveSourceDatabaseMeta(),
        wSourceSql.getText());
  }

  private void getNaturalKeysFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      applyFieldNamesToTable(wNaturalKeys, naturalKeyFieldColumn, fieldNames, null);
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getMeasuresFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      applyFieldNamesToTable(wMeasures, measureFieldColumn, fieldNames, "Y");
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getJoinsFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      Set<String> measureFields = new LinkedHashSet<>();
      if (wMeasures != null) {
        for (TableItem item : wMeasures.getNonEmptyItems()) {
          if (!Utils.isEmpty(item.getText(1))) {
            measureFields.add(item.getText(1));
          }
        }
      }
      if (dimensionJoinSourceFieldColumn != null) {
        dimensionJoinSourceFieldColumn.setComboValues(
            ConstUi.sortFieldNames(fieldNames.toArray(new String[0])));
      }
      wDimensionRoles.clearAll();
      for (String fieldName : fieldNames) {
        if (Utils.isEmpty(fieldName)
            || measureFields.contains(fieldName)
            || fieldName.toLowerCase().endsWith("_key")) {
          continue;
        }
        String dimensionName = inferDimensionNameForSourceField(fieldName);
        if (!isKnownDimensionName(dimensionName)) {
          continue;
        }
        TableItem item = new TableItem(wDimensionRoles.table, SWT.NONE);
        item.setText(1, dimensionName);
        item.setText(2, fieldName);
        item.setText(3, defaultForeignKeyForDimensionName(dimensionName));
        item.setText(4, looksLikeDateSourceField(fieldName) ? "Y" : "N");
        item.setText(5, "N");
      }
      wDimensionRoles.removeEmptyRows();
      wDimensionRoles.setRowNums();
      wDimensionRoles.optWidth(true);
      refreshDimensionJoinComboChoices();
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private void getAttributesFromSource() {
    try {
      List<String> fieldNames = loadSourceFieldNames();
      List<String> naturalKeyNames = readTableFieldNames(wNaturalKeys);
      List<String> attributeFields = new ArrayList<>();
      for (String fieldName : fieldNames) {
        if (!naturalKeyNames.contains(fieldName)) {
          attributeFields.add(fieldName);
        }
      }
      applyFieldNamesToTable(
          wAttributes,
          attributeFieldColumn,
          attributeFields,
          EnumDialogSupport.descriptionOf(DmScdUpdatePolicy.TYPE1));
    } catch (HopException e) {
      showSourceFieldError(e);
    }
  }

  private List<String> loadSourceFieldNames() throws HopException {
    DatabaseMeta databaseMeta = resolveSourceDatabaseMeta();
    return DmSourceSqlGuiSupport.resolveFieldNames(
        variables, databaseMeta, wSourceSql.getText());
  }

  private void applyFieldNamesToTable(
      TableView tableView,
      ColumnInfo fieldColumn,
      List<String> fieldNames,
      String defaultSecondColumnValue) {
    if (fieldColumn != null) {
      fieldColumn.setComboValues(ConstUi.sortFieldNames(fieldNames.toArray(new String[0])));
    }
    tableView.clearAll();
    for (String fieldName : fieldNames) {
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      TableItem item = new TableItem(tableView.table, SWT.NONE);
      item.setText(1, fieldName);
      if (!Utils.isEmpty(defaultSecondColumnValue)) {
        item.setText(2, defaultSecondColumnValue);
      }
    }
    tableView.removeEmptyRows();
    tableView.setRowNums();
    tableView.optWidth(true);
  }

  private static List<String> readTableFieldNames(TableView tableView) {
    List<String> names = new ArrayList<>();
    for (TableItem item : tableView.getNonEmptyItems()) {
      String fieldName = item.getText(1);
      if (!Utils.isEmpty(fieldName)) {
        names.add(fieldName);
      }
    }
    return names;
  }

  private List<DmFactDimensionRole> readDimensionRolesFromTable() {
    List<DmFactDimensionRole> roles = new ArrayList<>();
    for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
      String dimensionName = item.getText(1);
      if (Utils.isEmpty(dimensionName)) {
        continue;
      }
      DmFactDimensionRole role = new DmFactDimensionRole();
      role.setDimensionTableName(dimensionName);
      role.setSourceFieldName(item.getText(2));
      role.setForeignKeyColumn(item.getText(3));
      role.setTruncateToDateKey("Y".equalsIgnoreCase(item.getText(4)));
      role.setPreloadLookupCache("Y".equalsIgnoreCase(item.getText(5)));
      roles.add(role);
    }
    return roles;
  }

  private String defaultForeignKeyForDimensionName(String dimensionName) {
    DmDimension dimension =
        model != null
            ? DmDimensionResolutionSupport.resolveDimension(model, dimensionName, variables)
            : null;
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    DmFactDimensionRole tempRole = new DmFactDimensionRole();
    tempRole.setDimensionTableName(dimensionName);
    return DmLayoutSupport.defaultFactForeignKeyColumn(dimension, tempRole, config, variables);
  }

  private boolean isKnownDimensionName(String dimensionName) {
    if (model == null || Utils.isEmpty(dimensionName)) {
      return false;
    }
    return DmDimensionResolutionSupport.isDimensionLike(model, dimensionName);
  }

  private static String inferDimensionNameForSourceField(String fieldName) {
    return "dim_" + fieldName;
  }

  private static boolean looksLikeDateSourceField(String fieldName) {
    return fieldName != null && fieldName.toLowerCase().contains("date");
  }

  private void refreshDimensionJoinComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wDimensionRoles != null) {
      for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(2))) {
          choices.add(item.getText(2));
        }
      }
    }
    if (dimensionJoinSourceFieldColumn != null) {
      dimensionJoinSourceFieldColumn.setComboValues(
          ConstUi.sortFieldNames(choices.toArray(new String[0])));
    }
    refreshDimensionLookupDateComboChoices(choices);
  }

  private void refreshDimensionLookupDateComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    refreshDimensionLookupDateComboChoices(choices);
  }

  private void refreshDimensionLookupDateComboChoices(Set<String> choices) {
    if (wDimensionLookupDateField == null) {
      return;
    }
    if (input instanceof DmTableBase table
        && !Utils.isEmpty(table.getDimensionLookupDateField())) {
      choices.add(table.getDimensionLookupDateField());
    }
    String current = wDimensionLookupDateField.getText();
    wDimensionLookupDateField.setItems(ConstUi.sortFieldNames(choices.toArray(new String[0])));
    if (!Utils.isEmpty(current)) {
      wDimensionLookupDateField.setText(current);
    }
  }

  private void refreshMeasureComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wMeasures != null) {
      for (TableItem item : wMeasures.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
      }
    }
    if (measureFieldColumn != null) {
      measureFieldColumn.setComboValues(ConstUi.sortFieldNames(choices.toArray(new String[0])));
    }
  }

  private void refreshFieldComboChoices() {
    Set<String> choices = new LinkedHashSet<>();
    try {
      choices.addAll(loadSourceFieldNames());
    } catch (HopException ignored) {
      // Use fields already configured on the table when the source cannot be resolved yet.
    }
    if (wNaturalKeys != null) {
      for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
      }
    }
    if (wAttributes != null) {
      for (TableItem item : wAttributes.getNonEmptyItems()) {
        if (!Utils.isEmpty(item.getText(1))) {
          choices.add(item.getText(1));
        }
      }
    }
    String[] sorted = ConstUi.sortFieldNames(choices.toArray(new String[0]));
    if (naturalKeyFieldColumn != null) {
      naturalKeyFieldColumn.setComboValues(sorted);
    }
    if (attributeFieldColumn != null) {
      attributeFieldColumn.setComboValues(sorted);
    }
    refreshDimensionJoinComboChoices();
  }

  private void showSourceFieldError(HopException exception) {
    new ErrorDialog(
        shell,
        BaseMessages.getString(Props.class, "System.Dialog.Error.Title"),
        exception.getMessage(),
        exception);
  }
}