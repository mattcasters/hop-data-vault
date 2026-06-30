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
import java.util.List;
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
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactMeasure;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
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
  private TableView wNaturalKeys;
  private TableView wAttributes;
  private TableView wOutriggers;
  private TableView wDimensionRoles;
  private TableView wMeasures;
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
    }
  }

  private void addDimensionAliasTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionAlias.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.DimensionAlias.ToolTip"));

    Label wlReferencedDimension = new Label(comp, SWT.RIGHT);
    wlReferencedDimension.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.ReferencedDimension.Label"));
    PropsUi.setLook(wlReferencedDimension);
    wlReferencedDimension.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wReferencedDimension = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wReferencedDimension);
    wReferencedDimension.setItems(listBaseDimensionNames());
    wReferencedDimension.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());
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
    wSourceSql.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wSourceConnection, margin)
            .right()
            .bottom()
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

    ColumnInfo[] naturalKeyColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.NaturalKeys.Column.Field"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    wNaturalKeys =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            naturalKeyColumns,
            3,
            null,
            PropsUi.getInstance());
    wNaturalKeys.setLayoutData(new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addAttributesTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Attributes.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Attributes.ToolTip"));

    ColumnInfo[] attributeColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.Field"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Attributes.Column.ScdPolicy"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              EnumDialogSupport.comboOptions(DmScdUpdatePolicy.class),
              true)
        };
    wAttributes =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            attributeColumns,
            3,
            null,
            PropsUi.getInstance());
    wAttributes.setLayoutData(new FormDataBuilder().left().top(0, margin).right().bottom().result());
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
    ColumnInfo[] roleColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.Dimension"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              dimensionNames,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.Role"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DimensionRoles.Column.ForeignKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
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
        new FormDataBuilder().left().top(0, margin).right().bottom().result());
  }

  private void addMeasuresTab() {
    Composite comp =
        HopGuiDimensionalModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Measures.Label"),
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.Tab.Measures.ToolTip"));

    ColumnInfo[] measureColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.Column.Field"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.Measures.Column.Additive"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"},
              true)
        };
    wMeasures =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            measureColumns,
            3,
            null,
            PropsUi.getInstance());
    wMeasures.setLayoutData(new FormDataBuilder().left().top(0, margin).right().bottom().result());
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
      if (wReferencedDimension != null && !Utils.isEmpty(alias.getReferencedDimensionName())) {
        int index = wReferencedDimension.indexOf(alias.getReferencedDimensionName());
        if (index >= 0) {
          wReferencedDimension.select(index);
        }
      }
    }

    if (dimension && input instanceof DmDimension dmDimension) {
      EnumDialogSupport.selectCombo(wScdType, dmDimension.getScdTypeOrDefault());
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
    }

    if (factLike && input instanceof IDmFactLikeTable factLikeTable) {
      wDimensionRoles.clearAll();
      for (DmFactDimensionRole role : factLikeTable.getDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
          continue;
        }
        TableItem item = new TableItem(wDimensionRoles.table, SWT.NONE);
        item.setText(1, role.getDimensionTableName());
        if (!Utils.isEmpty(role.getRoleName())) {
          item.setText(2, role.getRoleName());
        }
        if (!Utils.isEmpty(role.getForeignKeyColumn())) {
          item.setText(3, role.getForeignKeyColumn());
        }
      }
      wDimensionRoles.removeEmptyRows();
      wDimensionRoles.setRowNums();
      wDimensionRoles.optWidth(true);

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
      if (wReferencedDimension != null) {
        alias.setReferencedDimensionName(wReferencedDimension.getText());
      }
      alias.syncPhysicalTableName(model, variables);
      input.setTableName(alias.getTableName());
    }

    if (dimension && input instanceof DmDimension dmDimension) {
      dmDimension.setScdType(
          EnumDialogSupport.readCombo(
              wScdType, DmDimensionScdType.class, DmDimensionScdType.TYPE1));
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

    if (factLike && input instanceof IDmFactLikeTable factLike) {
      if (factLike instanceof DmFact dmFact) {
        dmFact.getDimensionRoles().clear();
        for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
          String dimensionName = item.getText(1);
          if (Utils.isEmpty(dimensionName)) {
            continue;
          }
          dmFact
              .getDimensionRoles()
              .add(new DmFactDimensionRole(dimensionName, item.getText(2), item.getText(3)));
        }
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
        for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
          String dimensionName = item.getText(1);
          if (Utils.isEmpty(dimensionName)) {
            continue;
          }
          factlessFact
              .getDimensionRoles()
              .add(new DmFactDimensionRole(dimensionName, item.getText(2), item.getText(3)));
        }
      } else if (factLike instanceof DmPeriodicSnapshotFact periodicFact) {
        periodicFact.getDimensionRoles().clear();
        for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
          String dimensionName = item.getText(1);
          if (Utils.isEmpty(dimensionName)) {
            continue;
          }
          periodicFact
              .getDimensionRoles()
              .add(new DmFactDimensionRole(dimensionName, item.getText(2), item.getText(3)));
        }
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
        for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
          String dimensionName = item.getText(1);
          if (Utils.isEmpty(dimensionName)) {
            continue;
          }
          accumulatingFact
              .getDimensionRoles()
              .add(new DmFactDimensionRole(dimensionName, item.getText(2), item.getText(3)));
        }
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
        for (TableItem item : wDimensionRoles.getNonEmptyItems()) {
          String dimensionName = item.getText(1);
          if (Utils.isEmpty(dimensionName)) {
            continue;
          }
          aggregateFact
              .getDimensionRoles()
              .add(new DmFactDimensionRole(dimensionName, item.getText(2), item.getText(3)));
        }
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
}