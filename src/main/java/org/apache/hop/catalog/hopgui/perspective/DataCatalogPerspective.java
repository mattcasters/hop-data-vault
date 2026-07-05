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

package org.apache.hop.catalog.hopgui.perspective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewRunner;
import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.bus.HopGuiEvents;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.core.widget.TreeMemory;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.catalog.hopgui.perspective.importmenu.DataCatalogImportMenu;
import org.apache.hop.ui.hopgui.perspective.HopPerspectivePlugin;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/** Hop GUI perspective for browsing data catalog connections and record definitions. */
@HopPerspectivePlugin(
    id = "350-DataCatalogPerspective",
    name = "i18n::DataCatalogPerspective.Name",
    description = "i18n::DataCatalogPerspective.Description",
    image = "data_catalog.svg")
@GuiPlugin(
    name = "i18n::DataCatalogPerspective.Name",
    description = "i18n::DataCatalogPerspective.GuiPlugin.Description")
public class DataCatalogPerspective implements IHopPerspective {

  public static final Class<?> PKG = DataCatalogPerspective.class;
  public static final String ID_PERSPECTIVE_TOOLBAR_ITEM = "20035-perspective-data-catalog";
  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "DataCatalogPerspective-Toolbar";
  public static final String TOOLBAR_ITEM_IMPORT =
      "DataCatalogPerspective-Toolbar-10005-Import";
  public static final String TOOLBAR_ITEM_REFRESH = "DataCatalogPerspective-Toolbar-10000-Refresh";
  public static final String TOOLBAR_ITEM_PREVIEW = "DataCatalogPerspective-Toolbar-10015-Preview";
  public static final String TOOLBAR_ITEM_DELETE = "DataCatalogPerspective-Toolbar-10010-Delete";
  private static final String TREE_KEY = "Data catalog perspective tree";

  @Getter private static DataCatalogPerspective instance;

  private HopGui hopGui;
  private Composite composite;
  private SashForm sash;
  private Tree tree;
  private GuiToolbarWidgets toolBarWidgets;
  private RecordDefinitionDetailsPanel detailsPanel;
  private RecordDefinition selectedRecordDefinition;
  private Combo wRecordFilter;
  private DataCatalogRecordListFilter recordListFilter = DataCatalogRecordListFilter.ALL;

  public DataCatalogPerspective() {
    instance = this;
  }

  @Override
  public String getId() {
    return "data-catalog-perspective";
  }

  @Override
  public void activate() {
    hopGui.setActivePerspective(this);
  }

  @Override
  public void perspectiveActivated() {
    refresh();
  }

  @Override
  public boolean isActive() {
    return hopGui.isActivePerspective(this);
  }

  @Override
  public void initialize(HopGui hopGui, Composite parent) {
    this.hopGui = hopGui;

    composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new FormLayout());
    PropsUi.setLook(composite);
    composite.setLayoutData(new FormDataBuilder().fullSize().result());

    sash = new SashForm(composite, SWT.HORIZONTAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, 0);
    fdSash.top = new FormAttachment(0, 0);
    fdSash.right = new FormAttachment(100, 0);
    fdSash.bottom = new FormAttachment(100, 0);
    sash.setLayoutData(fdSash);

    createTreePane(sash);
    createDetailsPane(sash);
    sash.setWeights(new int[] {30, 70});

    refresh();

    hopGui
        .getEventsHandler()
        .addEventListener(
            getClass().getName(), e -> refresh(), HopGuiEvents.MetadataChanged.name());
  }

  private void createTreePane(Composite parent) {
    Composite treeComposite = new Composite(parent, SWT.BORDER);
    treeComposite.setLayout(new FormLayout());
    PropsUi.setLook(treeComposite);

    Label wlTree = new Label(treeComposite, SWT.LEFT);
    PropsUi.setLook(wlTree);
    wlTree.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Tree.Label"));
    FormData fdlTree = new FormData();
    fdlTree.left = new FormAttachment(0, PropsUi.getMargin());
    fdlTree.top = new FormAttachment(0, PropsUi.getMargin());
    wlTree.setLayoutData(fdlTree);

    Label wlFilter = new Label(treeComposite, SWT.LEFT);
    PropsUi.setLook(wlFilter);
    wlFilter.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Filter.Label"));
    FormData fdlFilter = new FormData();
    fdlFilter.left = new FormAttachment(0, PropsUi.getMargin());
    fdlFilter.top = new FormAttachment(wlTree, PropsUi.getMargin());
    wlFilter.setLayoutData(fdlFilter);

    wRecordFilter = new Combo(treeComposite, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wRecordFilter);
    for (DataCatalogRecordListFilter filter : DataCatalogRecordListFilter.values()) {
      wRecordFilter.add(filter.label());
    }
    wRecordFilter.select(0);
    wRecordFilter.addListener(
        SWT.Selection,
        e -> {
          recordListFilter = DataCatalogRecordListFilter.fromIndex(wRecordFilter.getSelectionIndex());
          refresh();
        });
    FormData fdFilter = new FormData();
    fdFilter.left = new FormAttachment(wlFilter, PropsUi.getMargin());
    fdFilter.top = new FormAttachment(wlTree, PropsUi.getMargin());
    fdFilter.right = new FormAttachment(100, -PropsUi.getMargin());
    wRecordFilter.setLayoutData(fdFilter);

    IToolbarContainer toolBarContainer =
        ToolbarFacade.createToolbarContainer(
            treeComposite, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
    Control toolBar = toolBarContainer.getControl();
    toolBarWidgets = new GuiToolbarWidgets();
    toolBarWidgets.registerGuiPluginObject(this);
    toolBarWidgets.createToolbarWidgets(toolBarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID);
    FormData fdToolBar = new FormData();
    fdToolBar.left = new FormAttachment(0, 0);
    fdToolBar.top = new FormAttachment(wRecordFilter, PropsUi.getMargin());
    fdToolBar.right = new FormAttachment(100, 0);
    toolBar.setLayoutData(fdToolBar);
    toolBar.pack();
    PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);

    tree = new Tree(treeComposite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(tree);
    FormData fdTree = new FormData();
    fdTree.left = new FormAttachment(0, 0);
    fdTree.top = new FormAttachment(toolBar, 0);
    fdTree.right = new FormAttachment(100, 0);
    fdTree.bottom = new FormAttachment(100, 0);
    tree.setLayoutData(fdTree);

    tree.addListener(SWT.Selection, e -> updateSelection());
    TreeMemory.addTreeListener(tree, TREE_KEY);
  }

  private void createDetailsPane(Composite parent) {
    Composite detailsComposite = new Composite(parent, SWT.BORDER);
    detailsComposite.setLayout(new FormLayout());
    PropsUi.setLook(detailsComposite);

    Label wlDetails = new Label(detailsComposite, SWT.LEFT);
    PropsUi.setLook(wlDetails);
    wlDetails.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Details.Label"));
    FormData fdlDetails = new FormData();
    fdlDetails.left = new FormAttachment(0, PropsUi.getMargin());
    fdlDetails.top = new FormAttachment(0, PropsUi.getMargin());
    wlDetails.setLayoutData(fdlDetails);

    Composite detailsBody = new Composite(detailsComposite, SWT.NONE);
    PropsUi.setLook(detailsBody);
    FormData fdBody = new FormData();
    fdBody.left = new FormAttachment(0, 0);
    fdBody.top = new FormAttachment(wlDetails, PropsUi.getMargin());
    fdBody.right = new FormAttachment(100, 0);
    fdBody.bottom = new FormAttachment(100, 0);
    detailsBody.setLayoutData(fdBody);

    detailsPanel = new RecordDefinitionDetailsPanel(detailsBody, hopGui.getVariables(), this::refresh);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_IMPORT,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Import.Tooltip",
      image = "ui/images/add.svg")
  public void openImportMenu() {
    DataCatalogImportMenu.open(
        hopGui, null, resolveSelectedCatalogConnectionName(), this::refresh);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REFRESH,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Refresh.Tooltip",
      image = "ui/images/refresh.svg")
  @GuiKeyboardShortcut(key = SWT.F5)
  @GuiOsxKeyboardShortcut(key = SWT.F5)
  /** Activates this perspective and selects a record definition in the tree and details panel. */
  public void selectRecordDefinition(String catalogConnectionName, RecordDefinitionKey key)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName) || key == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.SelectRecord.MissingTarget"));
    }
    if (tree == null || tree.isDisposed()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.SelectRecord.TreeUnavailable"));
    }
    activate();
    String recordKey = key.toString();
    if (!trySelectRecord(catalogConnectionName, recordKey)) {
      refresh();
      if (!trySelectRecord(catalogConnectionName, recordKey)) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DataCatalogPerspective.Error.SelectRecord.NotFound",
                recordKey,
                catalogConnectionName));
      }
    }
    updateSelection();
  }

  public void refresh() {
    if (tree == null || tree.isDisposed()) {
      return;
    }

    RecordDefinitionRegistry.getInstance().invalidate();

    TreeItem[] selection = tree.getSelection();
    String selectedCatalog = null;
    String selectedRecordKey = null;
    if (selection.length > 0) {
      DataCatalogTreeNode node = (DataCatalogTreeNode) selection[0].getData();
      if (node != null) {
        selectedCatalog = node.getCatalogConnectionName();
        if (node.getType() == DataCatalogTreeNode.Type.RECORD && node.getRecordKey() != null) {
          selectedRecordKey = node.getRecordKey().toString();
        }
      }
    }

    tree.removeAll();
    try {
      populateTree();
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.Refresh.Title"),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.Refresh.Message"),
          e);
    }

    restoreSelection(selectedCatalog, selectedRecordKey);
    updateSelection();
    updateToolbar();
  }

  private void populateTree() throws HopException {
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
    IVariables variables = hopGui.getVariables();
    List<DataCatalogMeta> connections = listEnabledConnections(metadataProvider);

    Map<String, List<RecordDefinitionRef>> refsByCatalog = new LinkedHashMap<>();
    List<RecordDefinitionRef> allRefs =
        RecordDefinitionRegistry.getInstance()
            .listAll(recordListFilter.toQuery(), variables, metadataProvider);
    for (RecordDefinitionRef ref : allRefs) {
      refsByCatalog
          .computeIfAbsent(ref.getCatalogConnectionName(), name -> new ArrayList<>())
          .add(ref);
    }

    for (DataCatalogMeta connection : connections) {
      TreeItem catalogItem = new TreeItem(tree, SWT.NONE);
      catalogItem.setText(Const.NVL(connection.getName(), ""));
      catalogItem.setData(DataCatalogTreeNode.catalog(connection.getName()));

      List<RecordDefinitionRef> refs =
          refsByCatalog.getOrDefault(connection.getName(), List.of()).stream()
              .sorted(
                  Comparator.comparing(
                          (RecordDefinitionRef ref) ->
                              ref.getKey() != null ? ref.getKey().getNamespace() : "",
                          String.CASE_INSENSITIVE_ORDER)
                      .thenComparing(
                          ref -> ref.getKey() != null ? ref.getKey().getName() : "",
                          String.CASE_INSENSITIVE_ORDER))
              .toList();

      for (RecordDefinitionRef ref : refs) {
        TreeItem recordItem = new TreeItem(catalogItem, SWT.NONE);
        recordItem.setText(displayRecordName(ref));
        recordItem.setData(DataCatalogTreeNode.record(connection.getName(), ref));
      }

      catalogItem.setExpanded(true);
      TreeMemory.getInstance().storeExpanded(TREE_KEY, catalogItem, true);
    }
  }

  private static String displayRecordName(RecordDefinitionRef ref) {
    if (ref.getKey() == null) {
      return "";
    }
    return ref.getKey().toString();
  }

  private boolean trySelectRecord(String catalogConnectionName, String recordKey) {
    restoreSelection(catalogConnectionName, recordKey);
    return isRecordSelected(catalogConnectionName, recordKey);
  }

  private boolean isRecordSelected(String catalogConnectionName, String recordKey) {
    if (tree == null || tree.isDisposed() || tree.getSelectionCount() != 1) {
      return false;
    }
    DataCatalogTreeNode node = (DataCatalogTreeNode) tree.getSelection()[0].getData();
    return node != null
        && node.getType() == DataCatalogTreeNode.Type.RECORD
        && catalogConnectionName.equals(node.getCatalogConnectionName())
        && node.getRecordKey() != null
        && recordKey.equals(node.getRecordKey().toString());
  }

  private void restoreSelection(String selectedCatalog, String selectedRecordKey) {
    if (Utils.isEmpty(selectedCatalog)) {
      return;
    }

    for (TreeItem catalogItem : tree.getItems()) {
      DataCatalogTreeNode catalogNode = (DataCatalogTreeNode) catalogItem.getData();
      if (catalogNode == null
          || !selectedCatalog.equals(catalogNode.getCatalogConnectionName())) {
        continue;
      }

      if (Utils.isEmpty(selectedRecordKey)) {
        tree.setSelection(catalogItem);
        return;
      }

      for (TreeItem recordItem : catalogItem.getItems()) {
        DataCatalogTreeNode recordNode = (DataCatalogTreeNode) recordItem.getData();
        if (recordNode != null
            && recordNode.getRecordKey() != null
            && selectedRecordKey.equals(recordNode.getRecordKey().toString())) {
          tree.setSelection(recordItem);
          return;
        }
      }

      tree.setSelection(catalogItem);
      return;
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_PREVIEW,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Preview.Tooltip",
      image = "ui/images/preview.svg")
  public void previewSelectedRecord() {
    if (selectedRecordDefinition == null
        || !RecordDefinitionPreviewSupport.supportsPreview(selectedRecordDefinition)) {
      return;
    }
    RecordDefinitionPreviewRunner.run(
        hopGui.getShell(),
        selectedRecordDefinition,
        hopGui.getVariables(),
        hopGui.getMetadataProvider());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DELETE,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Delete.Tooltip",
      image = "ui/images/delete.svg")
  @GuiKeyboardShortcut(key = SWT.DEL)
  @GuiOsxKeyboardShortcut(key = SWT.DEL)
  public void onDeleteRecord() {
    if (tree == null || tree.isDisposed() || tree.getSelectionCount() != 1) {
      return;
    }

    DataCatalogTreeNode node = (DataCatalogTreeNode) tree.getSelection()[0].getData();
    if (node == null || node.getType() != DataCatalogTreeNode.Type.RECORD) {
      return;
    }

    MessageBox confirm =
        new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
    confirm.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Delete.Title"));
    confirm.setMessage(
        BaseMessages.getString(
            PKG,
            "DataCatalogPerspective.Delete.Message",
            node.getRecordKey().toString(),
            node.getCatalogConnectionName()));
    if (confirm.open() != SWT.YES) {
      return;
    }

    try {
      RecordDefinitionRegistry.getInstance()
          .delete(
              node.getCatalogConnectionName(),
              node.getRecordKey(),
              hopGui.getVariables(),
              hopGui.getMetadataProvider());
      refresh();
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.Delete.Title"),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.Delete.Message"),
          e);
    }
  }

  private void updateSelection() {
    selectedRecordDefinition = null;

    if (tree.getSelectionCount() < 1) {
      detailsPanel.clear();
      updateToolbar();
      return;
    }

    DataCatalogTreeNode node = (DataCatalogTreeNode) tree.getSelection()[0].getData();
    if (node == null || node.getType() != DataCatalogTreeNode.Type.RECORD) {
      detailsPanel.clear();
      updateToolbar();
      return;
    }

    try {
      RecordDefinition definition =
          RecordDefinitionRegistry.getInstance()
              .read(
                  node.getCatalogConnectionName(),
                  node.getRecordKey(),
                  hopGui.getVariables(),
                  hopGui.getMetadataProvider());
      selectedRecordDefinition = definition;
      detailsPanel.setRecordDefinition(node.getCatalogConnectionName(), definition);
    } catch (HopException e) {
      detailsPanel.clear();
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.LoadRecord.Title"),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.LoadRecord.Message"),
          e);
    }
    updateToolbar();
  }

  private String resolveSelectedCatalogConnectionName() {
    if (tree == null || tree.isDisposed() || tree.getSelectionCount() != 1) {
      return null;
    }
    DataCatalogTreeNode node = (DataCatalogTreeNode) tree.getSelection()[0].getData();
    if (node == null) {
      return null;
    }
    return node.getCatalogConnectionName();
  }

  private void updateToolbar() {
    if (toolBarWidgets == null) {
      return;
    }
    boolean recordSelected = false;
    if (tree.getSelectionCount() == 1) {
      DataCatalogTreeNode node = (DataCatalogTreeNode) tree.getSelection()[0].getData();
      recordSelected = node != null && node.getType() == DataCatalogTreeNode.Type.RECORD;
    }
    toolBarWidgets.enableToolbarItem(TOOLBAR_ITEM_DELETE, recordSelected);
    toolBarWidgets.enableToolbarItem(
        TOOLBAR_ITEM_PREVIEW,
        selectedRecordDefinition != null
            && RecordDefinitionPreviewSupport.supportsPreview(selectedRecordDefinition));
  }

  private List<DataCatalogMeta> listEnabledConnections(IHopMetadataProvider metadataProvider)
      throws HopException {
    List<DataCatalogMeta> connections = new ArrayList<>();
    IHopMetadataSerializer<DataCatalogMeta> serializer =
        metadataProvider.getSerializer(DataCatalogMeta.class);
    List<String> names = serializer.listObjectNames();
    names.sort(String.CASE_INSENSITIVE_ORDER);
    for (String name : names) {
      DataCatalogMeta meta = serializer.load(name);
      if (meta != null && meta.isEnabled()) {
        connections.add(meta);
      }
    }
    return connections;
  }

  @Override
  public Control getControl() {
    return composite;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }
}