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
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.gui.SnapAllignDistribute;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.hopgui.file.dimensional.delegates.HopGuiDimensionalSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.hopgui.CanvasFacade;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.jspecify.annotations.Nullable;

/** Hop GUI editor for Kimball dimensional models (dimensions, facts, and notes). */
@GuiPlugin(
    id = "HopGuiDimensionalModelGraph",
    description = "i18n::HopGuiDimensionalModelGraph.Description")
@Getter
@Setter
public class HopGuiDimensionalModelGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher {

  private static final Class<?> PKG = HopGuiDimensionalModelGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiDimensionalModelGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiDimensionalModelGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN =
      "HopGuiDimensionalModelGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT =
      "HopGuiDimensionalModelGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 =
      "HopGuiDimensionalModelGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT =
      "HopGuiDimensionalModelGraph-ToolBar-10040-Zoom-Fit";
  public static final String TOOLBAR_ITEM_EDIT_MODEL =
      "HopGuiDimensionalModelGraph-ToolBar-10050-Edit-Model";
  public static final String TOOLBAR_ITEM_CHECK_MODEL =
      "HopGuiDimensionalModelGraph-ToolBar-10060-Check-Model";
  public static final String TOOLBAR_ITEM_SELECT_ALL =
      "HopGuiDimensionalModelGraph-ToolBar-20010-Select-All";
  public static final String TOOLBAR_ITEM_UNSELECT_ALL =
      "HopGuiDimensionalModelGraph-ToolBar-20020-Unselect-All";
  public static final String TOOLBAR_ITEM_UNDO = "HopGuiDimensionalModelGraph-ToolBar-20070-Undo";
  public static final String TOOLBAR_ITEM_REDO = "HopGuiDimensionalModelGraph-ToolBar-20080-Redo";

  private final HopDimensionalFileType fileType;
  private final HopGuiDimensionalSnapshotUndo snapshotUndo = new HopGuiDimensionalSnapshotUndo();
  private DimensionalModel model;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;
  private boolean changed = false;
  private String filename;

  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverTableName;
  private IDmTable currentTable;

  public HopGuiDimensionalModelGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      DimensionalModel model,
      HopDimensionalFileType fileType) {
    super(hopGui, parent, perspective);
    this.model = model;
    this.fileType = fileType;

    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());

    if (model == null) {
      return;
    }

    setLayout(new FormLayout());
    addToolBar();

    canvas = new Canvas(this, SWT.NO_BACKGROUND);
    FormData fdCanvas = new FormData();
    fdCanvas.left = new FormAttachment(0, 0);
    fdCanvas.top = new FormAttachment(0, toolBar.getBounds().height);
    fdCanvas.right = new FormAttachment(100, 0);
    fdCanvas.bottom = new FormAttachment(100, 0);
    canvas.setLayoutData(fdCanvas);

    canvas.addPaintListener(this::paintControl);
    registerCanvasMouseListeners();

    hopGui.replaceKeyboardShortcutListeners(this);
    canvas.setFocus();
    setZoomLabel();
    layout(true, true);
  }

  private void addToolBar() {
    try {
      IToolbarContainer toolBarContainer =
          ToolbarFacade.createToolbarContainer(this, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
      toolBar = toolBarContainer.getControl();
      toolBarWidgets = new GuiToolbarWidgets();
      toolBarWidgets.registerGuiPluginObject(this);
      toolBarWidgets.createToolbarWidgets(toolBarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID);
      FormData layoutData = new FormData();
      layoutData.left = new FormAttachment(0, 0);
      layoutData.top = new FormAttachment(0, 0);
      layoutData.right = new FormAttachment(100, 0);
      toolBar.setLayoutData(layoutData);
      toolBar.pack();
      PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);
      updateGui();
    } catch (Exception e) {
      hopGui.getLog().logError("Error setting up the toolbar for HopGuiDimensionalModelGraph: ", e);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || model == null) {
      return;
    }

    boolean needsDoubleBuffering =
        Const.isWindows() && "GUI".equalsIgnoreCase(Const.getHopPlatformRuntime());

    Image image = null;
    GC swtGc = e.gc;

    if (needsDoubleBuffering) {
      image = new Image(hopGui.getDisplay(), area.x, area.y);
      swtGc = new GC(image);
    }

    drawDimensionalModelImage(swtGc, area.x, area.y);

    if (needsDoubleBuffering) {
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawDimensionalModelImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    maximum = model.getMaximum();

    try {
      areaOwners.clear();
      DimensionalModelPainter painter =
          new DimensionalModelPainter(model, gc, variables, width, height);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification((float) (magnification * PropsUi.getNativeZoomFactor()));
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setMetadataProvider(hopGui.getMetadataProvider());
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverTableName(mouseOverTableName);
      painter.setMouseOverNoteLink(mouseOverNoteLink);
      painter.setSelectionRegion(selectionRegion);
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.drawDimensionalModel(hopGui.getMetadataProvider());

      captureNavigationViewGeometry(painter);

      CanvasFacade.setData(canvas, magnification, offset, model);
    } finally {
      gc.dispose();
    }
  }

  @Override
  public void redraw() {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  public static HopGuiDimensionalModelGraph getInstance() {
    IHopPerspective activePerspective = HopGui.getInstance().getActivePerspective();
    if (activePerspective instanceof ExplorerPerspective explorerPerspective) {
      if (explorerPerspective.getActiveFileTypeHandler()
          instanceof HopGuiDimensionalModelGraph graph) {
        return graph;
      }
    }
    return null;
  }

  @Override
  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new DimensionalMouseInteractions();
  }

  private @Nullable IDmTable getAreaOwnerTable(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getParent() instanceof IDmTable table) {
      return table;
    }
    return null;
  }

  private void centerOnTable(IDmTable table) {
    if (table == null) {
      return;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = 140;
    int boxH = 70;
    if (table instanceof DmTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        boxW = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        boxH = base.getDrawnBoxHeight();
      }
    }
    centerOnCanvasLocation(loc, boxW, boxH);
  }

  private void moveSelectedObjects(int dx, int dy) {
    List<IDmTable> selectedTables = getSelectedTables();
    List<DvNote> selectedNotes = getSelectedNotes();
    if (selectedTables.isEmpty() && selectedNotes.isEmpty()) {
      return;
    }
    for (IDmTable table : selectedTables) {
      Point loc = table.getLocation();
      if (loc.x + dx < 0) {
        dx = -loc.x;
      }
      if (loc.y + dy < 0) {
        dy = -loc.y;
      }
    }
    for (DvNote note : selectedNotes) {
      Point loc = note.getLocation();
      if (loc.x + dx < 0) {
        dx = -loc.x;
      }
      if (loc.y + dy < 0) {
        dy = -loc.y;
      }
    }
    for (IDmTable table : selectedTables) {
      Point loc = table.getLocation();
      PropsUi.setLocation(table, loc.x + dx, loc.y + dy);
      table.setChanged();
    }
    for (DvNote note : selectedNotes) {
      Point loc = note.getLocation();
      PropsUi.setLocation(note, loc.x + dx, loc.y + dy);
    }
  }

  @Override
  protected void updateGraphAfterNavigationPan() {
    updateGui();
  }

  private void clearTableDragState() {
    positionChangeUndoMarked = false;
    iconDragStart = null;
    iconDragCommitted = false;
    dragSelection = false;
    iconOffset = null;
    currentTable = null;
    clearNavigationViewportState();
    clearSelectionRegion();
  }

  @Override
  protected AreaOwner getVisibleAreaOwner(int x, int y) {
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      if (areaOwner.contains(x, y)) {
        return areaOwner;
      }
    }
    return null;
  }

  private boolean isTableInLassoScreenRect(
      IDmTable table, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (table == null) {
      return false;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = 140;
    int th = 70;
    if (table instanceof DmTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        tw = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        th = base.getDrawnBoxHeight();
      }
    }
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int tMaxX = tMinX + Math.max(1, tw);
    int tMaxY = tMinY + Math.max(1, th);
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private List<IDmTable> getSelectedTables() {
    List<IDmTable> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null && table.isSelected()) {
        selected.add(table);
      }
    }
    return selected;
  }

  private String getUniqueTableName(String base) {
    if (model == null) {
      return base;
    }
    int num = 1;
    String candidate;
    do {
      candidate = base + " " + num;
      num++;
    } while (model.findTable(candidate) != null);
    return candidate;
  }

  private void addTableAtClick(IDmTable table, Point click) {
    if (model == null || table == null) {
      return;
    }
    String name = getUniqueTableName(table.getTableType().name());
    table.setName(name);
    table.setTableName(name.toLowerCase().replace(' ', '_'));
    PropsUi.setLocation(table, click != null ? click.x : 50, click != null ? click.y : 50);
    model.getTables().add(table);
    setChanged();
    redraw();
  }

  private void showDimensionalContextDialog(Event e, Point real) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Background.Message");
      IGuiContextHandler contextHandler = new HopGuiDimensionalContext(model, this, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showTableContextDialog(Event e, IDmTable table) {
    if (table == null) {
      return;
    }
    try {
      Point real = screen2real(e.x, e.y);
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiDimensionalModelGraph.Context.Table.Message", table.getName());
      IGuiContextHandler contextHandler =
          new HopGuiDimensionalTableContext(model, this, table, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDimension.Tooltip",
      image = "dimensional_model.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmDimension(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-fact",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddFact.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddFact.Tooltip",
      image = "dimensional_model.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void addFact(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmFact(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-note",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addNote(HopGuiDimensionalContext context) {
    Point click = context.getClick();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (dmModel == null || graph == null) {
      return;
    }
    graph.markUndoPoint();
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.GENERAL);
    note.setText("");
    PropsUi.setLocation(note, click != null ? click.x : 50, click != null ? click.y : 50);
    PropsUi.setSize(note, ConstUi.NOTE_MIN_SIZE, ConstUi.NOTE_MIN_SIZE);
    dmModel.getNotes().add(note);
    graph.editNote(note, false);
    graph.setChanged();
  }

  @GuiContextAction(
      id = "dm-graph-edit-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.EditNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void editNoteAction(HopGuiDimensionalNoteContext context) {
    DvNote note = context.getNote();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (note != null && graph != null) {
      graph.editNote(note);
    }
  }

  @GuiContextAction(
      id = "dm-graph-delete-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiDimensionalModelGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiDimensionalNoteContext context) {
    DvNote note = context.getNote();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (note != null && dmModel != null && graph != null) {
      graph.markUndoPoint();
      if (dmModel.getNotes().remove(note)) {
        graph.setChanged();
        graph.redraw();
      }
    }
  }

  @GuiContextAction(
      id = "dm-graph-delete-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiDimensionalModelGraph.Context.DeleteTable.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.DeleteTable.Tooltip",
      image = "ui/images/delete.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void deleteTable(HopGuiDimensionalTableContext context) {
    IDmTable table = context.getTable();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (table == null || graph == null || dmModel == null) {
      return;
    }
    graph.markUndoPoint();
    dmModel.getTables().removeIf(t -> t != null && table.getName().equals(t.getName()));
    graph.setChanged();
    graph.redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EDIT_MODEL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.EditModel.Tooltip",
      image = "dimensional_model.svg")
  public void editModelProperties() {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    HopGuiDimensionalModelDialog dialog =
        new HopGuiDimensionalModelDialog(getShell(), hopGui, model);
    if (dialog.open()) {
      commitDialogUndo(beforeChange);
      setChanged();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CHECK_MODEL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.CheckModel.Tooltip",
      image = "ui/images/check.svg")
  public void checkModel() {
    if (model == null) {
      return;
    }
    showCheckResultsDialog(model.check(hopGui.getMetadataProvider(), getVariables()));
  }

  private void showCheckResultsDialog(List<ICheckResult> remarks) {
    CheckResultDialog dialog = new CheckResultDialog(hopGui.getShell(), remarks);
    dialog.open();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_LEVEL,
      label = "  Zoom: ",
      toolTip = "Zoom in or out",
      type = GuiToolbarElementType.COMBO,
      alignRight = true,
      comboValuesMethod = "getZoomLevels")
  public void zoomLevel() {
    performZoomLevelChanged();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    performZoomIn();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    performZoomOut();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    performZoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.ZoomFit.Tooltip",
      image = "ui/images/zoom-fit.svg")
  @Override
  public void zoomFitToScreen() {
    performZoomFitToScreen();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNDO,
      toolTip = "i18n:org.apache.hop.ui.hopgui:HopGui.Toolbar.Undo.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/undo.svg",
      separator = true)
  @GuiKeyboardShortcut(control = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, key = 'z')
  @Override
  public void undo() {
    try {
      applySnapshotChange(snapshotUndo.undo(model, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Message"),
          e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REDO,
      toolTip = "i18n:org.apache.hop.ui.hopgui:HopGui.Toolbar.Redo.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/redo.svg")
  @GuiKeyboardShortcut(control = true, shift = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, shift = true, key = 'z')
  @Override
  public void redo() {
    try {
      applySnapshotChange(snapshotUndo.redo(model, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Message"),
          e);
    }
  }

  @GuiKeyboardShortcut(control = true, key = 'a')
  @GuiOsxKeyboardShortcut(command = true, key = 'a')
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SELECT_ALL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.SelectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/select-all.svg")
  @Override
  public void selectAll() {
    if (model == null) {
      return;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(true);
      }
    }
    for (DvNote note : model.getNotes()) {
      if (note != null) {
        note.setSelected(true);
      }
    }
    redraw();
  }

  @GuiKeyboardShortcut(key = SWT.ESC)
  @GuiOsxKeyboardShortcut(key = SWT.ESC)
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNSELECT_ALL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.UnselectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/unselect-all.svg")
  @Override
  public void unselectAll() {
    mouseInteractions().unselectAllOnCanvas();
    clearSelectionRegion();
    redraw();
  }

  @Override
  protected ModelGraphSnapshotUndo<?> getSnapshotUndo() {
    return snapshotUndo.getDelegate();
  }

  @Override
  protected Object getModelForUndo() {
    return model;
  }

  @Override
  protected void restoreModelSnapshot(Object restored) throws Exception {
    if (restored instanceof DimensionalModel dimensionalModel) {
      clearTableDragState();
      clearNoteDragState();
      clearSelectionRegion();
      areaOwners.clear();
      mouseOverTableName = null;
      mouseOverNoteLink = null;
      model = dimensionalModel;
      setChanged();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
      canvas.setFocus();
    }
  }

  @Override
  protected void clearSelectionRegion() {
    selectionRegion = null;
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setToolTipText(null);
      canvas.setData("mode", "null");
      setCursor(null);
    }
    mouseOverTableName = null;
    mouseOverNoteLink = null;
  }

  @Override
  protected String undoRecordErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Record.Title");
  }

  @Override
  protected String undoRecordErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Record.Message");
  }

  @Override
  protected String undoApplyErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Title");
  }

  @Override
  protected String undoApplyErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Message");
  }

  @Override
  protected String undoToolbarItemId() {
    return TOOLBAR_ITEM_UNDO;
  }

  @Override
  protected String redoToolbarItemId() {
    return TOOLBAR_ITEM_REDO;
  }

  @Override
  protected GuiToolbarWidgets getToolBarWidgets() {
    return toolBarWidgets;
  }

  @Override
  protected String getZoomLevelToolbarItemId() {
    return TOOLBAR_ITEM_ZOOM_LEVEL;
  }

  @Override
  protected List<DvNote> getModelNotes() {
    return model != null ? model.getNotes() : List.of();
  }

  @Override
  protected IGuiContextHandler createNoteContextHandler(DvNote note, Point real) {
    return new HopGuiDimensionalNoteContext(model, this, note, real);
  }

  @Override
  protected String getNoteContextDialogMessage() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoteContext.Message");
  }

  @Override
  protected String getNoteLinkTableTooltip(String target) {
    return BaseMessages.getString(
        PKG, "HopGuiDimensionalModelGraph.NoteLink.TableTooltip", target);
  }

  @Override
  protected String getNoteLinkErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoteLink.Error.Title");
  }

  @Override
  protected String getNoteLinkUrlErrorMessage(String target) {
    return BaseMessages.getString(
        PKG, "HopGuiDimensionalModelGraph.NoteLink.UrlError.Message", target);
  }

  @Override
  protected String getNoteLinkTableNotFoundMessage(String tableName) {
    return BaseMessages.getString(
        PKG, "HopGuiDimensionalModelGraph.NoteLink.TableNotFound.Message", tableName);
  }

  @Override
  protected void navigateToNoteLinkTable(String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return;
    }
    IDmTable table = model.findTable(tableName);
    if (table == null) {
      showNoteLinkTableNotFound(tableName);
      return;
    }
    mouseInteractions().unselectAllOnCanvas();
    table.setSelected(true);
    centerOnTable(table);
    redraw();
    updateGui();
  }

  @Override
  public Object getSubject() {
    return model;
  }

  @Override
  public String getName() {
    return model != null ? model.getName() : "Dimensional Model";
  }

  @Override
  public void setName(String name) {
    if (model != null) {
      markUndoPoint();
      model.setName(name);
      setChanged();
    }
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
  }

  @Override
  public IHopFileType getFileType() {
    return fileType;
  }

  @Override
  public String getFilename() {
    return model != null ? model.getFilename() : filename;
  }

  @Override
  public void setFilename(String filename) {
    if (model != null) {
      model.setFilename(filename);
    } else {
      this.filename = filename;
    }
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
  }

  @Override
  public void save() throws HopException {
    if (fileType != null) {
      fileType.saveFile(hopGui, this);
    }
  }

  @Override
  public void saveAs(String filename) throws HopException {
    if (fileType != null) {
      fileType.saveFileAs(hopGui, this, filename);
    }
  }

  @Override
  public void start() {
    // not applicable
  }

  @Override
  public void stop() {
    // not applicable
  }

  @Override
  public void pause() {
    // not applicable
  }

  @Override
  public void resume() {
    // not applicable
  }

  @Override
  public void preview() {
    // not applicable
  }

  @Override
  public void debug() {
    // not applicable
  }

  @Override
  public void copySelectedToClipboard() {
    // clipboard not implemented in PR4 scaffold
  }

  @Override
  public void cutSelectedToClipboard() {
    // clipboard not implemented in PR4 scaffold
  }

  @Override
  public void pasteFromClipboard() {
    // clipboard not implemented in PR4 scaffold
  }

  @Override
  public void deleteSelected() {
    // delete via context menu only in PR4 scaffold
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
    if (perspective != null) {
      perspective.updateTabItem(this);
      perspective.updateTreeItem(this);
    }
    enableUndoToolbarItems();
  }

  @Override
  public boolean isCloseable() {
    try {
      if (hopGui.fileDelegate.isClosing()) {
        return true;
      }
      if (hasChanged()) {
        MessageBox messageDialog =
            new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        messageDialog.setText(
            BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiDimensionalModelGraph.SaveFile.Dialog.Message", buildTabName()));
        int answer = messageDialog.open();
        if ((answer & SWT.YES) != 0) {
          if (Utils.isEmpty(getFilename())) {
            String chosenFilename =
                BaseDialog.presentFileDialog(
                    true,
                    hopGui.getActiveShell(),
                    fileType.getFilterExtensions(),
                    fileType.getFilterNames(),
                    true);
            if (chosenFilename == null) {
              return false;
            }
            saveAs(hopGui.getVariables().resolve(chosenFilename));
          } else {
            save();
          }
          return true;
        }
        return (answer & SWT.NO) != 0;
      }
      return true;
    } catch (Exception e) {
      new ErrorDialog(
          hopShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Error.Message"),
          e);
    }
    return false;
  }

  private String buildTabName() {
    String realFilename = variables.resolve(getFilename());
    if (Utils.isEmpty(realFilename)) {
      return getName();
    }
    int lastSlash = Math.max(realFilename.lastIndexOf('/'), realFilename.lastIndexOf('\\'));
    if (lastSlash >= 0 && lastSlash < realFilename.length() - 1) {
      return realFilename.substring(lastSlash + 1);
    }
    return realFilename;
  }

  @Override
  public void close() {
    // perspective removes the tab
  }

  @Override
  public boolean hasChanged() {
    return changed || (model != null && model.hasChanged());
  }

  public void setChanged() {
    this.changed = true;
    if (model != null) {
      model.setChanged();
    }
    updateGui();
    redraw();
  }

  public void clearChanged() {
    this.changed = false;
    if (model != null) {
      model.clearChanged();
    }
    updateGui();
    redraw();
  }

  @Override
  public Map<String, Object> getStateProperties() {
    return buildCanvasStateProperties();
  }

  @Override
  public void applyStateProperties(Map<String, Object> stateProperties) {
    applyCanvasStateProperties(stateProperties);
    redraw();
  }

  @Override
  public IVariables getVariables() {
    return variables;
  }

  @Override
  public SnapAllignDistribute createSnapAlignDistribute() {
    List<IDmTable> selection = getSelectedTables();
    int[] indices = new int[selection.size()];
    for (int i = 0; i < selection.size(); i++) {
      indices[i] = model.getTables().indexOf(selection.get(i));
    }
    return new SnapAllignDistribute(model, selection, indices, null, this);
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }

  private final class DimensionalMouseInteractions implements ModelGraphMouseInteractions {

    @Override
    public ModelGraphHit resolveHit(int logicalX, int logicalY) {
      AreaOwner areaOwner = getVisibleAreaOwner(logicalX, logicalY);
      IDmTable table = getAreaOwnerTable(areaOwner);
      DvNote note = getAreaOwnerNote(areaOwner);
      AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
      return new ModelGraphHit(areaOwner, areaType, note, table);
    }

    @Override
    public boolean handleObjectMouseDown(
        Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
      Object obj = hit.canvasObject();
      if (!(obj instanceof IDmTable tableHit)) {
        return false;
      }
      AreaOwner.AreaType areaType = hit.areaType();

      if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
        avoidContextDialog = true;
        clearTableDragState();
        return true;
      }

      if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
        prepareExclusiveDragSelection(
            control, tableHit.isSelected(), () -> tableHit.setSelected(true));
        currentTable = tableHit;
        iconDragStart = new Point(real.x, real.y);
        iconDragCommitted = false;
        Point loc = tableHit.getLocation() != null ? tableHit.getLocation() : new Point(0, 0);
        iconOffset = new Point(real.x - loc.x, real.y - loc.y);
        clearNoteDragState();
        clearSelectionRegion();
        redraw();
        return true;
      }

      return false;
    }

    @Override
    public boolean isRelationshipDragActive() {
      return false;
    }

    @Override
    public void handleRelationshipMouseMove(Event e) {
      // no relationship drag in dimensional model scaffold
    }

    @Override
    public boolean handleRelationshipMouseUp(Event e, Point real) {
      return false;
    }

    @Override
    public boolean handleObjectMouseMove(Point real, boolean leftButtonDown) {
      if (!leftButtonDown || currentTable == null || resize != null) {
        return false;
      }
      currentTable.setSelected(true);
      if (iconOffset == null) {
        iconOffset = new Point(0, 0);
      }
      Point icon = new Point(real.x - iconOffset.x, real.y - iconOffset.y);
      boolean doRedraw = false;
      if (tryCommitIconDrag(real)) {
        doRedraw = true;
      }
      if (iconDragCommitted && currentTable.isSelected()) {
        int dx = icon.x - currentTable.getLocation().x;
        int dy = icon.y - currentTable.getLocation().y;
        moveSelectedObjects(dx, dy);
        avoidContextDialog = true;
        doRedraw = true;
      }
      return doRedraw;
    }

    @Override
    public boolean handleNoteMouseMove(Point real) {
      if (selectedNote == null || noteOffset == null) {
        return false;
      }
      Point notePos = new Point(real.x - noteOffset.x, real.y - noteOffset.y);
      int dx = notePos.x - selectedNote.getLocation().x;
      int dy = notePos.y - selectedNote.getLocation().y;
      if (dx == 0 && dy == 0) {
        return false;
      }
      if (!noteWasMoved) {
        markPositionUndoPoint();
      }
      moveSelectedObjects(dx, dy);
      noteWasMoved = true;
      avoidContextDialog = true;
      return true;
    }

    @Override
    public boolean hasCancellableDragState() {
      return currentTable != null
          || iconDragStart != null
          || currentNote != null
          || noteDragStart != null
          || selectionRegion != null;
    }

    @Override
    public void cancelActiveDragsOnBackgroundClick() {
      clearTableDragState();
    }

    @Override
    public void clearObjectDragState() {
      clearTableDragState();
    }

    @Override
    public void unselectAllOnCanvas() {
      if (model == null) {
        return;
      }
      for (IDmTable table : model.getTables()) {
        if (table != null) {
          table.setSelected(false);
        }
      }
      unselectAllNotes();
    }

    @Override
    public void selectInLassoRegion(
        int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
      if (model == null) {
        return;
      }
      for (IDmTable table : model.getTables()) {
        if (isTableInLassoScreenRect(table, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          table.setSelected(true);
        }
      }
      for (DvNote note : model.getNotes()) {
        if (isNoteInLassoScreenRect(note, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          note.setSelected(true);
        }
      }
    }

    @Override
    public void afterLassoSelection() {
      updateGui();
    }

    @Override
    public boolean handleCommittedDragMouseUp(Event e) {
      if (e.button != 1) {
        return false;
      }
      if (!iconDragCommitted && !dragSelection && !noteWasMoved) {
        return false;
      }
      setChanged();
      clearTableDragState();
      clearNoteDragState();
      avoidContextDialog = false;
      return true;
    }

    @Override
    public boolean handlePureClickMouseUp(Event e, Point real) {
      if (lastClick == null || lastClick.x != real.x || lastClick.y != real.y) {
        if (e.button == 1) {
          clearTableDragState();
          clearNoteDragState();
          avoidContextDialog = false;
        }
        return false;
      }

      if (handleNoteLinkClickAt(real)) {
        clearTableDragState();
        clearNoteDragState();
        avoidContextDialog = false;
        return true;
      }

      DvNote noteHit = currentNote;
      if (noteHit != null) {
        handleNoteBodyClick(e, noteHit, real, isControlDown(e));
        clearNoteDragState();
        avoidContextDialog = false;
        return true;
      }

      IDmTable hit = currentTable;
      if (hit == null) {
        if (!avoidContextDialog) {
          showDimensionalContextDialog(e, real);
          avoidContextDialog = true;
        } else {
          avoidContextDialog = false;
        }
        return true;
      }

      if (isControlDown(e)) {
        hit.setSelected(!hit.isSelected());
        redraw();
      } else if (!avoidContextDialog) {
        showTableContextDialog(e, hit);
      }
      clearTableDragState();
      avoidContextDialog = false;
      return true;
    }

    @Override
    public boolean clearHoverState() {
      if (mouseOverTableName == null && mouseOverNoteLink == null) {
        return false;
      }
      mouseOverTableName = null;
      mouseOverNoteLink = null;
      return true;
    }

    @Override
    public boolean updateHoverState(AreaOwner areaOwner, Point real) {
      String newOver = null;
      if (areaOwner != null
          && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
          && !dragSelection
          && selectionRegion == null) {
        if (areaOwner.getParent() instanceof IDmTable dmTable && dmTable.getName() != null) {
          newOver = dmTable.getName();
        }
      }
      boolean doRedraw = false;
      if ((mouseOverTableName == null && newOver != null)
          || (mouseOverTableName != null && !mouseOverTableName.equals(newOver))) {
        mouseOverTableName = newOver;
        doRedraw = true;
      }
      return doRedraw;
    }

    @Override
    public void onLassoMouseDownAfter() {
      mouseOverTableName = null;
    }

    @Override
    public boolean isNoteMouseDownAllowed() {
      return true;
    }

    @Override
    public boolean isLassoMoveAllowed() {
      return true;
    }

    @Override
    public boolean allowEmptyLassoClearOnMouseUp() {
      return true;
    }

    @Override
    public boolean isNoteResizeHoverBlocked() {
      return dragSelection || selectionRegion != null || noteWasMoved || iconDragCommitted;
    }

    @Override
    public void prepareNavigationViewportDrag() {
      clearTableDragState();
      clearNoteDragState();
      clearSelectionRegion();
    }

    @Override
    public void refreshGui() {
      updateGui();
    }
  }
}