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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvBusinessTable;
import org.apache.hop.datavault.metadata.businessvault.BvSqlMaterialization;
import org.apache.hop.datavault.metadata.businessvault.BvSqlRef;
import org.apache.hop.datavault.metadata.businessvault.BvSqlRefResolver;
import org.apache.hop.datavault.metadata.businessvault.BvSqlReferenceStyle;
import org.apache.hop.datavault.metadata.businessvault.BvSqlSource;
import org.apache.hop.datavault.metadata.businessvault.BvSqlTemplateParser;
import org.apache.hop.datavault.metadata.businessvault.BvSqlViewPipelineSupport;
import org.apache.hop.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.ShowMessageDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a SQL-sourced Business Vault business table (view or table materialization). */
public class HopGuiBvBusinessTableDialog {
  private static final Class<?> PKG = HopGuiBvBusinessTableDialog.class;

  private final Shell parent;
  private final BvBusinessTable input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private Shell shell;

  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Combo wMaterialization;
  private Combo wReferenceStyle;
  private TextComposite wSqlQuery;
  private TableView wSources;
  private TableView wRefs;

  private boolean ok;

  public HopGuiBvBusinessTableDialog(
      Shell parent,
      BvBusinessTable table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    this.parent = parent;
    this.input = table;
    this.businessVaultModel = businessVaultModel;
    this.dataVaultModel = dataVaultModel;
    this.variables = variables;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiBvBusinessTableDialog.Title", Const.NVL(input.getName(), "SQL")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Name.Label"));
    PropsUi.setLook(wlName);
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

    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(wName, margin);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wName, margin);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wTableName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wTableName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    Label wlMaterialization = new Label(shell, SWT.RIGHT);
    wlMaterialization.setText(
        BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Materialization.Label"));
    PropsUi.setLook(wlMaterialization);
    FormData fdlMaterialization = new FormData();
    fdlMaterialization.left = new FormAttachment(0, 0);
    fdlMaterialization.top = new FormAttachment(wDescription, margin);
    fdlMaterialization.right = new FormAttachment(middle, -margin);
    wlMaterialization.setLayoutData(fdlMaterialization);

    wMaterialization = new Combo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wMaterialization);
    wMaterialization.setItems(BvSqlMaterialization.getDescriptions());
    FormData fdMaterialization = new FormData();
    fdMaterialization.left = new FormAttachment(middle, 0);
    fdMaterialization.top = new FormAttachment(wDescription, margin);
    fdMaterialization.right = new FormAttachment(100, 0);
    wMaterialization.setLayoutData(fdMaterialization);

    Label wlReferenceStyle = new Label(shell, SWT.RIGHT);
    wlReferenceStyle.setText(
        BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.ReferenceStyle.Label"));
    PropsUi.setLook(wlReferenceStyle);
    FormData fdlReferenceStyle = new FormData();
    fdlReferenceStyle.left = new FormAttachment(0, 0);
    fdlReferenceStyle.top = new FormAttachment(wMaterialization, margin);
    fdlReferenceStyle.right = new FormAttachment(middle, -margin);
    wlReferenceStyle.setLayoutData(fdlReferenceStyle);

    wReferenceStyle = new Combo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wReferenceStyle);
    wReferenceStyle.setItems(BvSqlReferenceStyle.getDescriptions());
    FormData fdReferenceStyle = new FormData();
    fdReferenceStyle.left = new FormAttachment(middle, 0);
    fdReferenceStyle.top = new FormAttachment(wMaterialization, margin);
    fdReferenceStyle.right = new FormAttachment(100, 0);
    wReferenceStyle.setLayoutData(fdReferenceStyle);

    CTabFolder tabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(tabFolder);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wReferenceStyle, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(100, -50);
    tabFolder.setLayoutData(fdTabFolder);

    // --- SQL tab ---
    CTabItem sqlTab = new CTabItem(tabFolder, SWT.NONE);
    sqlTab.setText(BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Tab.Sql"));
    Composite sqlComp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(sqlComp);
    sqlComp.setLayout(new FormLayout());
    sqlTab.setControl(sqlComp);

    Button wSyncRefs = new Button(sqlComp, SWT.PUSH);
    wSyncRefs.setText(BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.SyncRefs.Label"));
    PropsUi.setLook(wSyncRefs);
    FormData fdSyncRefs = new FormData();
    fdSyncRefs.left = new FormAttachment(0, 0);
    fdSyncRefs.top = new FormAttachment(0, margin);
    wSyncRefs.setLayoutData(fdSyncRefs);
    wSyncRefs.addListener(SWT.Selection, e -> syncRefsFromSqlUi());

    Button wPreviewSql = new Button(sqlComp, SWT.PUSH);
    wPreviewSql.setText(
        BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.PreviewResolved.Label"));
    PropsUi.setLook(wPreviewSql);
    FormData fdPreviewSql = new FormData();
    fdPreviewSql.left = new FormAttachment(wSyncRefs, margin);
    fdPreviewSql.top = new FormAttachment(0, margin);
    wPreviewSql.setLayoutData(fdPreviewSql);
    wPreviewSql.addListener(SWT.Selection, e -> previewResolvedSql());

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSqlQuery = new StyledTextComp(variables, sqlComp, sqlStyle);
    } else {
      wSqlQuery = new SQLStyledTextComp(variables, sqlComp, sqlStyle);
      wSqlQuery.addLineStyleListener(getSqlReservedWords());
    }
    PropsUi.setLook(wSqlQuery, Props.WIDGET_STYLE_FIXED);
    FormData fdSqlQuery = new FormData();
    fdSqlQuery.left = new FormAttachment(0, 0);
    fdSqlQuery.top = new FormAttachment(wSyncRefs, margin);
    fdSqlQuery.right = new FormAttachment(100, 0);
    fdSqlQuery.bottom = new FormAttachment(100, 0);
    wSqlQuery.setLayoutData(fdSqlQuery);

    // --- Sources tab ---
    CTabItem sourcesTab = new CTabItem(tabFolder, SWT.NONE);
    sourcesTab.setText(BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Tab.Sources"));
    Composite sourcesComp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(sourcesComp);
    sourcesComp.setLayout(new FormLayout());
    sourcesTab.setControl(sourcesComp);

    Button wAddFromSql = new Button(sourcesComp, SWT.PUSH);
    wAddFromSql.setText(
        BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Sources.AddFromSql"));
    PropsUi.setLook(wAddFromSql);
    FormData fdAddFromSql = new FormData();
    fdAddFromSql.left = new FormAttachment(0, 0);
    fdAddFromSql.top = new FormAttachment(0, margin);
    wAddFromSql.setLayoutData(fdAddFromSql);
    wAddFromSql.addListener(SWT.Selection, e -> addSourcesFromSql());

    ColumnInfo[] sourceCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Sources.Column.SourceName"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Sources.Column.Database"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Sources.Column.Schema"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Sources.Column.Table"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Sources.Column.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    wSources =
        new TableView(
            variables,
            sourcesComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            sourceCols,
            1,
            null,
            PropsUi.getInstance());
    FormData fdSources = new FormData();
    fdSources.left = new FormAttachment(0, 0);
    fdSources.top = new FormAttachment(wAddFromSql, margin);
    fdSources.right = new FormAttachment(100, 0);
    fdSources.bottom = new FormAttachment(100, 0);
    wSources.setLayoutData(fdSources);

    // --- Refs tab ---
    CTabItem refsTab = new CTabItem(tabFolder, SWT.NONE);
    refsTab.setText(BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Tab.References"));
    Composite refsComp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(refsComp);
    refsComp.setLayout(new FormLayout());
    refsTab.setControl(refsComp);

    ColumnInfo[] refCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Refs.Column.Model"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Refs.Column.Object"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Refs.Column.Kind"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Refs.Column.ResolvedTable"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
        };
    wRefs =
        new TableView(
            variables,
            refsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            refCols,
            1,
            null,
            PropsUi.getInstance());
    FormData fdRefs = new FormData();
    fdRefs.left = new FormAttachment(0, 0);
    fdRefs.top = new FormAttachment(0, margin);
    fdRefs.right = new FormAttachment(100, 0);
    fdRefs.bottom = new FormAttachment(100, 0);
    wRefs.setLayoutData(fdRefs);

    tabFolder.setSelection(0);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_TABLE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    getData();
    BaseTransformDialog.setSize(shell, 720, 640);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    EnumDialogSupport.selectCombo(wMaterialization, input.getMaterializationOrDefault());
    EnumDialogSupport.selectCombo(wReferenceStyle, input.getReferenceStyleOrDefault());
    wSqlQuery.setText(Const.NVL(input.getSqlQuery(), ""));
    populateSources(input.getSources());
    populateRefs(input.getSqlRefs());
  }

  private void populateSources(List<BvSqlSource> sources) {
    wSources.table.removeAll();
    if (sources != null) {
      for (BvSqlSource source : sources) {
        if (source == null) {
          continue;
        }
        TableItem item = new TableItem(wSources.table, SWT.NONE);
        item.setText(1, Const.NVL(source.getSourceName(), ""));
        item.setText(2, Const.NVL(source.getDatabaseName(), ""));
        item.setText(3, Const.NVL(source.getSchemaName(), ""));
        item.setText(4, Const.NVL(source.getTableName(), ""));
        item.setText(5, Const.NVL(source.getDescription(), ""));
      }
    }
    if (wSources.table.getItemCount() == 0) {
      new TableItem(wSources.table, SWT.NONE);
    }
    wSources.setRowNums();
    wSources.optWidth(true);
  }

  private void populateRefs(List<BvSqlRef> refs) {
    wRefs.table.removeAll();
    if (refs != null) {
      for (BvSqlRef ref : refs) {
        if (ref == null) {
          continue;
        }
        TableItem item = new TableItem(wRefs.table, SWT.NONE);
        item.setText(1, Const.NVL(ref.getModelName(), ""));
        item.setText(2, Const.NVL(ref.getObjectName(), ""));
        item.setText(
            3, ref.getResolvedKind() != null ? ref.getResolvedKind().getCode() : "");
        item.setText(4, Const.NVL(ref.getResolvedTableName(), ""));
      }
    }
    if (wRefs.table.getItemCount() == 0) {
      new TableItem(wRefs.table, SWT.NONE);
    }
    wRefs.setRowNums();
    wRefs.optWidth(true);
  }

  private void applyTo(BvBusinessTable target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    target.setMaterialization(
        BvSqlMaterialization.lookupDescription(wMaterialization.getText()));
    target.setReferenceStyle(BvSqlReferenceStyle.lookupDescription(wReferenceStyle.getText()));
    target.setSqlQuery(wSqlQuery.getText());
    target.setSources(readSources());
    BvSqlRefResolver.syncRefsFromSql(target, businessVaultModel, dataVaultModel);
    BvSqlRefResolver.ensureDvCanvasAliases(
        businessVaultModel, target.getSqlRefs(), dataVaultModel);
  }

  private List<BvSqlSource> readSources() {
    List<BvSqlSource> sources = new ArrayList<>();
    for (int i = 0; i < wSources.nrNonEmpty(); i++) {
      TableItem item = wSources.getNonEmpty(i);
      String sourceName = item.getText(1);
      String tableName = item.getText(4);
      if (Utils.isEmpty(sourceName) && Utils.isEmpty(tableName)) {
        continue;
      }
      BvSqlSource source = new BvSqlSource();
      source.setSourceName(sourceName);
      source.setDatabaseName(item.getText(2));
      source.setSchemaName(item.getText(3));
      source.setTableName(tableName);
      source.setDescription(item.getText(5));
      sources.add(source);
    }
    return sources;
  }

  private void syncRefsFromSqlUi() {
    try {
      BvBusinessTable draft = new BvBusinessTable();
      applyTo(draft);
      populateRefs(draft.getSqlRefs());
      populateSources(draft.getSources());
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void addSourcesFromSql() {
    List<BvSqlSource> existing = readSources();
    List<BvSqlSource> fromSql = BvSqlTemplateParser.extractSourceUsages(wSqlQuery.getText());
    for (BvSqlSource usage : fromSql) {
      if (BvSqlRefResolver.findSource(
              wrapSources(existing), usage.getSourceName(), usage.getTableName())
          == null) {
        existing.add(usage);
      }
    }
    populateSources(existing);
  }

  private static BvBusinessTable wrapSources(List<BvSqlSource> sources) {
    BvBusinessTable t = new BvBusinessTable();
    t.setSources(sources);
    return t;
  }

  private void previewResolvedSql() {
    try {
      BvBusinessTable draft = new BvBusinessTable();
      applyTo(draft);
      String resolvedQuery =
          BvSqlRefResolver.resolveSql(
              draft, businessVaultModel, dataVaultModel, null, variables, null);
      String ddl =
          BvSqlViewPipelineSupport.buildCreateStatement(draft, null, variables, resolvedQuery);
      new ShowMessageDialog(
              shell,
              SWT.OK | SWT.ICON_INFORMATION,
              BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.PreviewResolved.Title"),
              ddl,
              true)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void validate() {
    try {
      BvBusinessTable draft = new BvBusinessTable();
      applyTo(draft);
      List<ICheckResult> remarks = new ArrayList<>();
      draft.check(
          remarks,
          HopGui.getInstance().getMetadataProvider(),
          variables,
          businessVaultModel,
          dataVaultModel);
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBvBusinessTableDialog.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void ok() {
    applyTo(input);
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }

  /** Reserved words from the BV target database, used for SQL syntax highlighting. */
  private List<String> getSqlReservedWords() {
    try {
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      if (metadataProvider == null || businessVaultModel == null) {
        return List.of();
      }
      DatabaseMeta targetDatabase =
          BvTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, businessVaultModel.getConfigurationOrDefault());
      if (targetDatabase == null) {
        return List.of();
      }
      String[] reserved = targetDatabase.getReservedWords();
      return reserved != null ? List.of(reserved) : List.of();
    } catch (Exception e) {
      return List.of();
    }
  }
}
