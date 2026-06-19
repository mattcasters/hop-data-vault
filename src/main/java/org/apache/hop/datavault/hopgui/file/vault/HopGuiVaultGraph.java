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

package org.apache.hop.datavault.hopgui.file.vault;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IRedrawable;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.gui.SnapAllignDistribute;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
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
import org.apache.hop.ui.hopgui.file.IGraphSnapAlignDistribute;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.shared.HopGuiAbstractGraph;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolItem;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

/**
 * Basic implementation of the vault graph / editor for Data Vault models in Hop GUI. Uses a canvas
 * and DataVaultModelPainter to draw the model. No undo/redo support for this initial version.
 * Implements IHopFileTypeHandler so it can be used as a tab in the explorer perspective.
 */
@GuiPlugin(id = "HopGuiVaultGraph", description = "i18n::HopGuiVaultGraph.Description")
@Getter
@Setter
public class HopGuiVaultGraph extends HopGuiAbstractGraph
    implements IHopFileTypeHandler, IGuiRefresher, IGraphSnapAlignDistribute, IRedrawable {
  private static final Class<?> PKG = HopGuiVaultGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiVaultGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL = "HopGuiVaultGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN = "HopGuiVaultGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT = "HopGuiVaultGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 = "HopGuiVaultGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT = "HopGuiVaultGraph-ToolBar-10040-Zoom-Fit";

  public static final String TOOLBAR_ITEM_SELECT_ALL = "HopGuiVaultGraph-ToolBar-20010-Select-All";
  public static final String TOOLBAR_ITEM_UNSELECT_ALL =
      "HopGuiVaultGraph-ToolBar-20020-Unselect-All";

  public static final String TOOLBAR_ITEM_EDIT_MODEL = "HopGuiVaultGraph-ToolBar-10050-Edit-Model";

  public static final String TOOLBAR_ITEM_CHECK_MODEL =
      "HopGuiVaultGraph-ToolBar-10060-Check-Model";

  public static final String TOOLBAR_ITEM_DEBUG = "HopGuiVaultGraph-ToolBar-10070-Debug";

  public static final String TOOLBAR_ITEM_GENERATE_DDL =
      "HopGuiVaultGraph-ToolBar-10080-Generate-Ddl";

  public static final String TOOLBAR_ITEM_IMPORT_SOURCES =
      "HopGuiVaultGraph-ToolBar-10085-Import-Sources";

  public static final String TOOLBAR_ITEM_SHOW_HASH_KEYS =
      "HopGuiVaultGraph-ToolBar-10090-Show-Hash-Keys";

  public static final String STATE_MAGNIFICATION = "magnification";
  public static final String STATE_SCROLL_X_SELECTION = "offset-x";
  public static final String STATE_SCROLL_Y_SELECTION = "offset-y";

  private final ExplorerPerspective perspective;
  private final HopVaultFileType fileType;
  private DataVaultModel model;

  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;

  private boolean changed = false;

  private boolean avoidContextDialog;

  // Area owners for fine-grained hit testing (name vs body of table icon), like pipeline/workflow
  // graphs.
  // Used for mouse-over underline on name, click-name=edit, click-body=context.
  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverTableName;
  private DvNoteLinkHit mouseOverNoteLink;

  // Relationship drag state (middle-mouse-button or shift+left-button drag from table to table)
  // to create hub<->satellite or hub<->link relationships (stored as name refs in the tables).
  private IDvTable startRelationshipTable;
  private Point relationshipDragEndLocation; // screen coordinates for temp line
  private IDvTable candidateRelationshipTarget;

  private Point lastClick;

  private static final int ICON_DRAG_THRESHOLD_PX = 3;

  // Drag state for moving tables: left-click + drag on table icon body (not the name part).
  // Moves the clicked table + all currently selected tables. Position relative to initial click.
  private Point iconDragStart;
  private boolean iconDragCommitted;
  private boolean dragSelection;
  private IDvTable currentTable;

  // Drag state for moving notes on the canvas.
  private DvNote currentNote;
  private DvNote selectedNote;
  private Point noteOffset;
  private Point noteDragStart;
  private boolean noteWasMoved;

  // Lasso / rubber-band multi-select: started on background left-click+drag.
  // Uses SCREEN coordinates (drawn on raw GC after painter, untransformed).
  // On start (unless CTRL held) we unselect all. On release, select tables whose
  // drawn screen rect overlaps the lasso rect.
  private Rectangle selectionRegion;

  private double lastNavigationScale;
  private double lastNavigationGraphOriginX;
  private double lastNavigationGraphOriginY;
  private boolean navigatingWithViewport;
  private Point
      navigationGrabOffset; // mouse offset inside the viewPort rect at drag start (screen px)

  /** No-arg constructor for the Hop GUI menu plugin dispatcher ({@link #menuFileExportToSvg()}). */
  public HopGuiVaultGraph() {
    this(createMenuDispatchParent(), HopGui.getInstance(), null, null, null);
  }

  private static Composite createMenuDispatchParent() {
    Shell shell = HopGui.getInstance().getShell();
    Composite parent = new Composite(shell, SWT.NONE);
    parent.setVisible(false);
    return parent;
  }

  public HopGuiVaultGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      DataVaultModel model,
      HopVaultFileType fileType) {
    super(hopGui, parent, SWT.NO_BACKGROUND);
    this.hopGui = hopGui;
    this.perspective = perspective;
    this.model = model;
    this.fileType = fileType;

    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());

    if (model == null) {
      return;
    }

    // The layout in the widget is done using a FormLayout to allow toolbar at top + canvas
    setLayout(new FormLayout());

    // Add a tool-bar at the top of the composite (like HopGuiPipelineGraph / HopGuiWorkflowGraph)
    addToolBar();

    // Create the canvas for drawing, positioned below the toolbar
    canvas = new Canvas(this, SWT.NO_BACKGROUND);
    FormData fdCanvas = new FormData();
    fdCanvas.left = new FormAttachment(0, 0);
    fdCanvas.top = new FormAttachment(0, toolBar.getBounds().height);
    fdCanvas.right = new FormAttachment(100, 0);
    fdCanvas.bottom = new FormAttachment(100, 0);
    canvas.setLayoutData(fdCanvas);

    // Paint listener to draw the model
    canvas.addPaintListener(this::paintControl);

    // Mouse listener for context dialog on left click (background) + table edit + relationship drag
    // (middle/shift)
    canvas.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseDown(MouseEvent e) {
            mouseDownEvent(e);
          }
        });
    canvas.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseUp(MouseEvent e) {
            mouseUpEvent(e);
          }
        });
    canvas.addMouseMoveListener(e -> mouseMoveEvent(e));
    canvas.addMouseWheelListener(this::mouseScrolled);
    canvas.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseDoubleClick(MouseEvent e) {
            mouseDoubleClickEvent(e);
          }
        });

    // Track listener to clear tooltips (and hover state) when mouse leaves the canvas area.
    canvas.addMouseTrackListener(
        new MouseTrackAdapter() {
          @Override
          public void mouseExit(MouseEvent e) {
            canvas.setToolTipText(null);
            if (mouseOverTableName != null || mouseOverNoteLink != null) {
              mouseOverTableName = null;
              mouseOverNoteLink = null;
              redraw();
            }
          }
        });

    hopGui.replaceKeyboardShortcutListeners(this);

    // Make sure we can receive focus etc.
    canvas.setFocus();

    // Initial zoom label
    setZoomLabel();

    // Ensure layout
    layout(true, true);
  }

  private void mouseMoveEvent(MouseEvent e) {
    Point real = screen2real(e.x, e.y);
    boolean shift = (e.stateMask & SWT.SHIFT) != 0;
    boolean control = (e.stateMask & SWT.MOD1) != 0;

    // Drag the view around with middle button on the background?
    //
    if (viewDrag && lastClick != null) {
      dragView(viewDragStart, new Point(e.x, e.y));
      return;
    }

    // Live panning via navigation viewport drag (dragging the blue rect in the minimap)
    if (navigatingWithViewport
        && (e.stateMask & SWT.BUTTON1) != 0
        && viewPort != null
        && graphPort != null
        && lastNavigationScale > 0.0) {
      mouseMoveViewport(e);
      return;
    }

    boolean doRedraw = false;

    // Resizing the current note
    if (resize != null && selectedNote != null) {
      resizeDvNote(selectedNote, real);
      return;
    }

    if (startRelationshipTable != null) {
      relationshipDragEndLocation = new Point(e.x, e.y);
      candidateRelationshipTarget = findTableAtScreen(e.x, e.y);
      doRedraw = true;
    }

    // Table drag move (left button on body after threshold)
    if (currentTable != null
        && (e.stateMask & SWT.BUTTON1) != 0
        && startRelationshipTable == null
        && resize == null) {
      currentTable.setSelected(true);
      doRedraw = mouseMoveTable(real, doRedraw);
    }

    // Note drag move (left button; mirrors Hop pipeline graph note dragging)
    if (selectedNote != null
        && (e.stateMask & SWT.BUTTON1) != 0
        && startRelationshipTable == null
        && resize == null) {
      doRedraw = mouseMoveNote(real, doRedraw);
    }

    // Update lasso rubber band if active (bg left drag)
    if (selectionRegion != null
        && (e.stateMask & SWT.BUTTON1) != 0
        && startRelationshipTable == null) {
      selectionRegion.width = real.x + (int) (offset.x) - selectionRegion.x;
      selectionRegion.height = real.y + (int) (offset.y) - selectionRegion.y;
      doRedraw = true;
    }

    if (selectionRegion != null && (mouseOverTableName != null || mouseOverNoteLink != null)) {
      mouseOverTableName = null;
      mouseOverNoteLink = null;
      doRedraw = true;
    }

    // We're not re-drawing so we're not moving or dragging stuff.
    //
    if (!doRedraw) {
      // Update mouse-over for table name underline (only on name area, not during
      // drag/rel/lasso)
      //
      AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
      IDvTable hit = getAreaOwnerTable(areaOwner);

      // Show description tooltip when hovering over the info icon (if present for the table)
      mouseMoveShowInfoTooltip(areaOwner);

      doRedraw = mouseMoveOverTableName(areaOwner, doRedraw);
      doRedraw = mouseMoveOverNoteLink(areaOwner, doRedraw);
      doRedraw = mouseMoveOverNoteResize(areaOwner, real, doRedraw);
    }

    if (doRedraw) {
      redraw();
      updateGui();
    }
  }

  private boolean mouseMoveOverTableName(AreaOwner areaOwner, boolean doRedraw) {
    String newOver = null;
    if (areaOwner != null
        && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
        && startRelationshipTable == null
        && !dragSelection
        && selectionRegion == null) {
      newOver = (String) areaOwner.getOwner();
    }
    if ((mouseOverTableName == null && newOver != null)
        || (mouseOverTableName != null && !mouseOverTableName.equals(newOver))) {
      doRedraw = true;
    }
    mouseOverTableName = newOver;
    return doRedraw;
  }

  private void mouseMoveShowInfoTooltip(AreaOwner areaOwner) {
    if (areaOwner != null && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_INFO_ICON) {
      IDvTable t = null;
      Object o = areaOwner.getOwner();
      if (o instanceof IDvTable tt) {
        t = tt;
      } else if (areaOwner.getParent() instanceof IDvTable tt) {
        t = tt;
      }
      String tip = (t != null && !Utils.isEmpty(t.getDescription())) ? t.getDescription() : null;
      if (!java.util.Objects.equals(canvas.getToolTipText(), tip)) {
        canvas.setToolTipText(tip);
      }
    } else if (canvas.getToolTipText() != null) {
      canvas.setToolTipText(null);
    }
  }

  private boolean mouseMoveTable(Point real, boolean doRedraw) {
    if (iconOffset == null) {
      iconOffset = new Point(0, 0);
    }
    Point icon = new Point(real.x - iconOffset.x, real.y - iconOffset.y);

    if (!iconDragCommitted && iconDragStart != null) {
      int dxs = real.x - iconDragStart.x;
      int dys = real.y - iconDragStart.y;
      int threshSq = ICON_DRAG_THRESHOLD_PX * ICON_DRAG_THRESHOLD_PX;
      if (dxs * dxs + dys * dys > threshSq) {
        iconDragCommitted = true;
        dragSelection = true;
        doRedraw = true;
      }
    }

    if (iconDragCommitted && currentTable != null && currentTable.isSelected()) {
      int dx = icon.x - currentTable.getLocation().x;
      int dy = icon.y - currentTable.getLocation().y;
      moveSelectedObjects(dx, dy);
      avoidContextDialog = true;
      doRedraw = true;
    }
    return doRedraw;
  }

  private void mouseMoveViewport(MouseEvent e) {
    int desiredLeft = e.x - navigationGrabOffset.x;
    int desiredTop = e.y - navigationGrabOffset.y;

    Rectangle gp = graphPort;
    int vw = viewPort.width;
    int vh = viewPort.height;

    // Clamp the view rect so it stays fully inside the graphPort (minimap content rect)
    int minL = gp.x;
    int minT = gp.y;
    int maxL = gp.x + gp.width - vw;
    int maxT = gp.y + gp.height - vh;
    if (maxL < minL) maxL = minL;
    if (maxT < minT) maxT = minT;

    int clampedLeft = Math.max(minL, Math.min(desiredLeft, maxL));
    int clampedTop = Math.max(minT, Math.min(desiredTop, maxT));

    // Map the clamped view rect position back to graph visible area using the captured
    // navigation transform (same math as painter used to place the view rect).
    double newVisLeft = (clampedLeft - lastNavigationGraphOriginX) / lastNavigationScale;
    double newVisTop = (clampedTop - lastNavigationGraphOriginY) / lastNavigationScale;

    int newOx = (int) Math.round(-newVisLeft);
    int newOy = (int) Math.round(-newVisTop);

    if (newOx != offset.x || newOy != offset.y) {
      offset.x = newOx;
      offset.y = newOy;
      redraw();
      updateGui();
    }
    return;
  }

  private void mouseUpEvent(MouseEvent e) {
    canvas.setToolTipText(null);
    Point real = screen2real(e.x, e.y);

    if (navigatingWithViewport) {
      // End viewport drag panning. State is cleared; the final offset was already applied
      // live during mouseMove (and will be reflected on next paint's port recompute).
      navigatingWithViewport = false;
      navigationGrabOffset = null;
      avoidContextDialog = true;
      redraw();
      return;
    }

    if (viewDrag) {
      viewDrag = false;
      viewDragStart = null;
    }

    if (resize != null && selectedNote != null) {
      setChanged();
      resize = null;
      selectedNote = null;
      resizeArea = null;
      setCursor(null);
      clearNoteDragState();
      avoidContextDialog = true;
      return;
    }

    // A single click on the background isn't a selection.
    // We need to show the context dialog for the model.
    //
    if (startRelationshipTable == null && selectionRegion != null && selectionRegion.isEmpty()) {
      avoidContextDialog = false;
      selectionRegion = null;
    }

    if (startRelationshipTable != null) {
      mouseUpCreateRelationship(e);
      return;
    } else if (selectionRegion != null) {
      // Finish lasso drag: update final size
      selectionRegion.width = real.x - selectionRegion.x;
      selectionRegion.height = real.y - selectionRegion.y;

      int absW = Math.abs(selectionRegion.width);
      int absH = Math.abs(selectionRegion.height);

      if (absW < ICON_DRAG_THRESHOLD_PX && absH < ICON_DRAG_THRESHOLD_PX) {
        mouseUpClearSelectionRegion();
        // fall through
      } else {
        mouseUpSelectTablesInRegion();
        return;
      }
    }

    if (e.button == 1) {
      if (iconDragCommitted || dragSelection || noteWasMoved) {
        // end table/note drag (moves were applied live during mouseMove)
        setChanged();
        clearTableDragState();
        clearNoteDragState();
        avoidContextDialog = false;
        return;
      }

      if (lastClick != null && lastClick.x == real.x && lastClick.y == real.y) {
        // pure click (no drag)
        DvNoteLinkHit linkHit = getAreaOwnerNoteLink(getVisibleAreaOwner(real.x, real.y));
        if (linkHit != null) {
          followNoteLink(linkHit);
          clearTableDragState();
          clearNoteDragState();
          avoidContextDialog = false;
          return;
        }

        DvNote noteHit = currentNote;
        if (noteHit != null) {
          boolean control = (e.stateMask & SWT.MOD1) != 0;
          if (control) {
            noteHit.setSelected(!noteHit.isSelected());
            redraw();
          } else if (!avoidContextDialog) {
            showNoteContextDialog(e, noteHit, real);
          }
          avoidContextDialog = false;
          clearNoteDragState();
          return;
        }

        IDvTable hit = currentTable;
        if (hit == null) {
          // A single click on the background
          if (!avoidContextDialog) {
            showVaultContextDialog(e, real);
            avoidContextDialog = true;
          } else {
            avoidContextDialog = false;
          }
        } else {
          // User clicked on a table
          boolean control = (e.stateMask & SWT.MOD1) != 0;
          if (control) {
            hit.setSelected(!hit.isSelected());
            redraw();
          } else {
            if (!avoidContextDialog) {
              showTableContextDialog(e, hit);
            }
          }
          avoidContextDialog = false;
        }
      } else {
        // moved without committing drag
        clearTableDragState();
        clearNoteDragState();
        avoidContextDialog = false;
      }
    } else if (avoidContextDialog) {
      avoidContextDialog = false;
    }

    // In case a lasso was left (defensive)
    if (selectionRegion != null) {
      selectionRegion = null;
      setCursor(null);
    }
  }

  private void mouseUpClearSelectionRegion() {
    // Essentially a click (not a drag), clear lasso and fall through so pure-click
    // logic can show the background context dialog if appropriate.
    selectionRegion = null;
    canvas.setData("mode", "null");
    setCursor(null);
    redraw();
  }

  private void mouseUpSelectTablesInRegion() {
    // Real lasso: select tables whose visual (screen) area overlaps the lasso rect.
    int x1 = selectionRegion.x;
    int y1 = selectionRegion.y;
    int x2 = x1 + selectionRegion.width;
    int y2 = y1 + selectionRegion.height;
    int minX = Math.min(x1, x2);
    int maxX = Math.max(x1, x2);
    int minY = Math.min(y1, y2);
    int maxY = Math.max(y1, y2);

    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (isTableInLassoScreenRect(table, minX, minY, maxX, maxY)) {
          table.setSelected(true);
        }
      }
    }
    if (model != null && model.getNotes() != null) {
      for (DvNote note : model.getNotes()) {
        if (isNoteInLassoScreenRect(note, minX, minY, maxX, maxY)) {
          note.setSelected(true);
        }
      }
    }

    selectionRegion = null;
    canvas.setData("mode", "null");
    setCursor(null);
    avoidContextDialog = true;
    redraw();
    updateGui();
  }

  private void mouseUpCreateRelationship(MouseEvent e) {
    // Complete relationship drag if dropped on a different valid table
    IDvTable target = findTableAtScreen(e.x, e.y);
    if (target != null && target != startRelationshipTable) {
      createRelationship(startRelationshipTable, target);
    }
    cancelRelationshipDrag();
    clearTableDragState();
    avoidContextDialog = true;
    redraw();
  }

  private void mouseDownEvent(MouseEvent e) {
    canvas.setToolTipText(null);
    Point real = screen2real(e.x, e.y);
    lastClick = new Point(real.x, real.y);
    boolean shift = (e.stateMask & SWT.SHIFT) != 0;
    boolean control = (e.stateMask & SWT.MOD1) != 0;

    // Navigation viewport drag takes precedence: click+drag inside the blue viewPort rect
    // (in the painter's bottom-right minimap) pans the main graph by updating offset.
    // The painter keeps viewPort inside graphPort; we do the same here during live drag.
    //
    if (e.button == 1
        && viewPort != null
        && graphPort != null
        && viewPort.contains(e.x, e.y)
        && lastNavigationScale > 0.0) {
      mouseDownOnViewport(e);
      return;
    }

    // The stored are owners simply reflect where something was drawn.
    //
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    IDvTable hit = getAreaOwnerTable(areaOwner);
    AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();

    if (hit != null) {
      // Clicking the info icon (description badge) should not start a drag or open
      // context menu.
      // The description is already visible via tooltip on hover.
      //
      if (AreaOwner.AreaType.TRANSFORM_INFO_ICON == areaType) {
        avoidContextDialog = true;
        clearTableDragState();
        return;
      }

      // We start dragging anywhere in the table box
      //
      if (AreaOwner.AreaType.TRANSFORM_ICON == areaType && e.button == 2
          || (e.button == 1 && shift)) {
        // Middle button or Shift+Left: start dragging a relationship from this table
        // (hub<->sat or hub<->link). Completion on mouseUp.
        startRelationshipTable = hit;
        relationshipDragEndLocation = new Point(e.x, e.y);
        candidateRelationshipTarget = hit;
        mouseOverTableName = null;
        clearTableDragState();
        clearSelectionRegion();
        avoidContextDialog = true;
        redraw();
        return;
      }
      // A left click on the name of the underlined table name
      //
      if (e.button == 1 && AreaOwner.AreaType.TRANSFORM_NAME == areaType) {
        // Left click on the (underlined) name: open edit dialog (hyperlink behavior)
        avoidContextDialog = true;
        editTable(hit);
        return;
      }

      // A click somewhere in the background
      //
      if (hit != null && e.button == 1 && AreaOwner.AreaType.TRANSFORM_ICON == areaType) {
        // Left click on table body/icon: setup drag (defer context/select to mouseUp if
        // no drag)
        //
        currentTable = hit;
        iconDragStart = new Point(real.x, real.y);
        iconDragCommitted = false;
        Point p = hit.getLocation() != null ? hit.getLocation() : new Point(0, 0);
        iconOffset = new Point(real.x - p.x, real.y - p.y);
        clearNoteDragState();
        clearSelectionRegion();
        redraw();
        return;
      }
    }

    DvNote noteHit = getAreaOwnerNote(areaOwner);
    if (noteHit != null
        && areaOwner != null
        && areaOwner.getAreaType() == AreaOwner.AreaType.NOTE
        && e.button == 1
        && startRelationshipTable == null) {
      currentNote = noteHit;
      selectedNote = noteHit;
      noteWasMoved = false;
      if (!control) {
        if (!noteHit.isSelected()) {
          unselectAllOnCanvas();
          noteHit.setSelected(true);
        }
      }
      Point loc = noteHit.getLocation() != null ? noteHit.getLocation() : new Point(0, 0);
      noteOffset = new Point(real.x - loc.x, real.y - loc.y);
      noteDragStart = new Point(real.x, real.y);
      resize = getResize(areaOwner.getArea(), real);
      if (resize != null) {
        resizeArea =
            new Rectangle(
                loc.x,
                loc.y,
                Math.max(noteHit.getWidth(), noteHit.getMinimumWidth()),
                Math.max(noteHit.getHeight(), noteHit.getMinimumHeight()));
      }
      clearTableDragState();
      clearSelectionRegion();
      redraw();
      return;
    }

    // Background click: cancel active relationship drag or table drag (like hop drag cancel
    // on bg)
    //
    if (startRelationshipTable != null
        || currentTable != null
        || iconDragStart != null
        || currentNote != null
        || noteDragStart != null
        || selectionRegion != null) {
      cancelRelationshipDrag();
      clearTableDragState();
      clearNoteDragState();
      clearSelectionRegion();
      avoidContextDialog = true;
      redraw();
      return;
    }

    // Start lasso (rubber-band) selection on left-click background drag.
    // Unless CTRL is held, unselect all tables at start of lasso (per spec).
    //
    if (e.button == 1 && hit == null && noteHit == null) {
      if (!control) {
        unselectAllOnCanvas();
      }

      selectionRegion = new Rectangle((int) (real.x + offset.x), (int) (real.y + offset.y), 0, 0);
      mouseOverTableName = null;
      canvas.setData("mode", "select");
      setCursor(getDisplay().getSystemCursor(SWT.CURSOR_CROSS));
      avoidContextDialog = true;
      redraw();
      return;
    }

    // Click to drag the background
    //
    if (setupDragView(e.button, control, new Point(e.x, e.y))) {
      return;
    }

    redraw();
  }

  private @Nullable IDvTable getAreaOwnerTable(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }

    if (areaOwner.getParent() instanceof IDvTable hit) {
      return hit;
    }
    return null;
  }

  private @Nullable DvNote getAreaOwnerNote(AreaOwner areaOwner) {
    if (areaOwner != null
        && areaOwner.getAreaType() == AreaOwner.AreaType.NOTE
        && areaOwner.getOwner() instanceof DvNote note) {
      return note;
    }
    return null;
  }

  private @Nullable DvNoteLinkHit getAreaOwnerNoteLink(AreaOwner areaOwner) {
    if (areaOwner != null
        && areaOwner.getAreaType() == AreaOwner.AreaType.CUSTOM
        && areaOwner.getOwner() instanceof DvNoteLinkHit linkHit) {
      return linkHit;
    }
    return null;
  }

  private boolean mouseMoveOverNoteLink(AreaOwner areaOwner, boolean doRedraw) {
    DvNoteLinkHit newOver = getAreaOwnerNoteLink(areaOwner);
    if ((mouseOverNoteLink == null && newOver != null)
        || (mouseOverNoteLink != null && !noteLinksEqual(mouseOverNoteLink, newOver))) {
      doRedraw = true;
    }
    mouseOverNoteLink = newOver;

    Cursor hand = getDisplay().getSystemCursor(SWT.CURSOR_HAND);
    if (newOver != null) {
      if (!java.util.Objects.equals(canvas.getCursor(), hand)) {
        setCursor(hand);
        doRedraw = true;
      }
      String target = newOver.link().target().trim();
      String tip =
          DvNoteTextParser.isUrlTarget(target)
              ? target
              : BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.TableTooltip", target);
      if (!java.util.Objects.equals(canvas.getToolTipText(), tip)) {
        canvas.setToolTipText(tip);
      }
    } else if (canvas.getCursor() == hand) {
      setCursor(null);
      doRedraw = true;
    }
    return doRedraw;
  }

  private static boolean noteLinksEqual(DvNoteLinkHit a, DvNoteLinkHit b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    DvNoteTextParser.Segment linkA = a.link();
    DvNoteTextParser.Segment linkB = b.link();
    return a.note() == b.note()
        && linkA != null
        && linkB != null
        && linkA.link() == linkB.link()
        && java.util.Objects.equals(linkA.label(), linkB.label())
        && java.util.Objects.equals(linkA.target(), linkB.target());
  }

  private void followNoteLink(DvNoteLinkHit linkHit) {
    if (linkHit == null || linkHit.link() == null || Utils.isEmpty(linkHit.link().target())) {
      return;
    }
    String target = linkHit.link().target().trim();
    if (DvNoteTextParser.isUrlTarget(target)) {
      try {
        EnvironmentUtils.getInstance().openUrl(target);
      } catch (HopException e) {
        new ErrorDialog(
            getShell(),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.Error.Title"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.UrlError.Message", target),
            e);
      }
      return;
    }
    navigateToTable(target);
  }

  private void navigateToTable(String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return;
    }
    IDvTable table = model.findTable(tableName);
    if (table == null) {
      MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_WARNING);
      box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.Error.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.TableNotFound.Message", tableName));
      box.open();
      return;
    }
    unselectAllOnCanvas();
    table.setSelected(true);
    centerOnTable(table);
    redraw();
    updateGui();
  }

  private void centerOnTable(IDvTable table) {
    if (table == null || canvas == null || canvas.isDisposed()) {
      return;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = 140;
    int boxH = 70;
    if (table instanceof DvTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        boxW = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        boxH = base.getDrawnBoxHeight();
      }
    }
    float mag = calculateCorrectedMagnification();
    org.eclipse.swt.graphics.Rectangle bounds = canvas.getBounds();
    double tableCenterX = loc.x + boxW / 2.0;
    double tableCenterY = loc.y + boxH / 2.0;
    offset.x = bounds.width / (2.0 * mag) - tableCenterX;
    offset.y = bounds.height / (2.0 * mag) - tableCenterY;
    validateOffset();
  }

  private void mouseDoubleClickEvent(MouseEvent e) {
    Point real = screen2real(e.x, e.y);
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    DvNote note = getAreaOwnerNote(areaOwner);
    if (note != null) {
      editNote(note);
    }
  }

  private boolean mouseMoveNote(Point real, boolean doRedraw) {
    if (selectedNote == null || noteOffset == null) {
      return doRedraw;
    }
    Point notePos = new Point(real.x - noteOffset.x, real.y - noteOffset.y);
    int dx = notePos.x - selectedNote.getLocation().x;
    int dy = notePos.y - selectedNote.getLocation().y;
    if (dx != 0 || dy != 0) {
      moveSelectedObjects(dx, dy);
      noteWasMoved = true;
      avoidContextDialog = true;
      doRedraw = true;
    }
    return doRedraw;
  }

  private boolean mouseMoveOverNoteResize(AreaOwner areaOwner, Point real, boolean doRedraw) {
    if (startRelationshipTable != null
        || dragSelection
        || selectionRegion != null
        || noteWasMoved
        || iconDragCommitted) {
      return doRedraw;
    }
    Resize resizeOver = null;
    if (areaOwner != null && areaOwner.getAreaType() == AreaOwner.AreaType.NOTE) {
      resizeOver = getResize(areaOwner.getArea(), real);
    }
    Cursor cursor = resizeOver != null ? getDisplay().getSystemCursor(resizeOver.getCursor()) : null;
    if (!java.util.Objects.equals(canvas.getCursor(), cursor)) {
      setCursor(cursor);
      doRedraw = true;
    }
    return doRedraw;
  }

  private void clearNoteDragState() {
    noteDragStart = null;
    noteWasMoved = false;
    noteOffset = null;
    currentNote = null;
    if (resize == null) {
      selectedNote = null;
      resizeArea = null;
    }
  }

  private void unselectAllOnCanvas() {
    unselectAllTables();
    unselectAllNotes();
  }

  private void unselectAllNotes() {
    if (model != null && model.getNotes() != null) {
      for (DvNote note : model.getNotes()) {
        if (note != null) {
          note.setSelected(false);
        }
      }
    }
  }

  private List<DvNote> getSelectedNotes() {
    List<DvNote> list = new ArrayList<>();
    if (model != null && model.getNotes() != null) {
      for (DvNote note : model.getNotes()) {
        if (note != null && note.isSelected()) {
          list.add(note);
        }
      }
    }
    return list;
  }

  private void moveSelectedObjects(int dx, int dy) {
    List<IDvTable> selectedTables = getSelectedTables();
    List<DvNote> selectedNotes = getSelectedNotes();
    if (selectedTables.isEmpty() && selectedNotes.isEmpty()) {
      return;
    }
    for (IDvTable table : selectedTables) {
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
    for (IDvTable table : selectedTables) {
      Point loc = table.getLocation();
      PropsUi.setLocation(table, loc.x + dx, loc.y + dy);
      table.setChanged();
    }
    for (DvNote note : selectedNotes) {
      Point loc = note.getLocation();
      PropsUi.setLocation(note, loc.x + dx, loc.y + dy);
    }
  }

  private boolean isNoteInLassoScreenRect(
      DvNote note, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (note == null) {
      return false;
    }
    Point loc = note.getLocation();
    if (loc == null) {
      return false;
    }
    int nw = Math.max(1, Math.max(note.getWidth(), note.getMinimumWidth()));
    int nh = Math.max(1, Math.max(note.getHeight(), note.getMinimumHeight()));
    int nMinX = loc.x + (int) offset.x;
    int nMinY = loc.y + (int) offset.y;
    int nMaxX = nMinX + nw;
    int nMaxY = nMinY + nh;
    boolean xOverlap = Math.max(lassoMinX, nMinX) < Math.min(lassoMaxX, nMaxX);
    boolean yOverlap = Math.max(lassoMinY, nMinY) < Math.min(lassoMaxY, nMaxY);
    return xOverlap && yOverlap;
  }

  private void resizeDvNote(DvNote note, Point real) {
    if (note == null || resize == null || resizeArea == null) {
      return;
    }
    switch (resize) {
      case EAST -> {
        int width = real.x - resizeArea.x;
        if (width < note.getMinimumWidth()) {
          width = note.getMinimumWidth();
        }
        PropsUi.setSize(note, width, note.getHeight());
      }
      case NORTH -> {
        int y = Math.max(0, real.y);
        if (y > resizeArea.y + resizeArea.height - note.getMinimumHeight()) {
          y = resizeArea.y + resizeArea.height - note.getMinimumHeight();
        }
        PropsUi.setLocation(note, resizeArea.x, y);
        PropsUi.setSize(
            note, note.getWidth(), resizeArea.y + resizeArea.height - note.getLocation().y);
      }
      case NORTH_EAST -> {
        int width = real.x - resizeArea.x;
        if (width < note.getMinimumWidth()) {
          width = note.getMinimumWidth();
        }
        int y = Math.max(0, real.y);
        if (y > resizeArea.y + resizeArea.height - note.getMinimumHeight()) {
          y = resizeArea.y + resizeArea.height - note.getMinimumHeight();
        }
        PropsUi.setLocation(note, resizeArea.x, y);
        PropsUi.setSize(
            note, width, resizeArea.y + resizeArea.height - note.getLocation().y);
      }
      case NORTH_WEST -> {
        int x = Math.max(0, real.x);
        if (x > resizeArea.x + resizeArea.width - note.getMinimumWidth()) {
          x = resizeArea.x + resizeArea.width - note.getMinimumWidth();
        }
        int y = Math.max(0, real.y);
        if (y > resizeArea.y + resizeArea.height - note.getMinimumHeight()) {
          y = resizeArea.y + resizeArea.height - note.getMinimumHeight();
        }
        PropsUi.setLocation(note, x, y);
        PropsUi.setSize(
            note,
            resizeArea.x + resizeArea.width - note.getLocation().x,
            resizeArea.height + resizeArea.y - note.getLocation().y);
      }
      case SOUTH -> {
        int height = real.y - resizeArea.y;
        if (height < note.getMinimumHeight()) {
          height = note.getMinimumHeight();
        }
        PropsUi.setSize(note, note.getWidth(), height);
      }
      case SOUTH_EAST -> {
        int width = real.x - resizeArea.x;
        if (width < note.getMinimumWidth()) {
          width = note.getMinimumWidth();
        }
        int height = real.y - resizeArea.y;
        if (height < note.getMinimumHeight()) {
          height = note.getMinimumHeight();
        }
        PropsUi.setSize(note, width, height);
      }
      case SOUTH_WEST -> {
        int x = Math.max(0, real.x);
        if (x > resizeArea.x + resizeArea.width - note.getMinimumWidth()) {
          x = resizeArea.x + resizeArea.width - note.getMinimumWidth();
        }
        int height = real.y - resizeArea.y;
        if (height < note.getMinimumHeight()) {
          height = note.getMinimumHeight();
        }
        PropsUi.setLocation(note, x, resizeArea.y);
        PropsUi.setSize(
            note, resizeArea.x + resizeArea.width - note.getLocation().x, height);
      }
      case WEST -> {
        int x = Math.max(0, real.x);
        if (x > resizeArea.x + resizeArea.width - note.getMinimumWidth()) {
          x = resizeArea.x + resizeArea.width - note.getMinimumWidth();
        }
        PropsUi.setLocation(note, x, resizeArea.y);
        PropsUi.setSize(
            note, resizeArea.x + resizeArea.width - note.getLocation().x, note.getHeight());
      }
    }
    redraw();
  }

  private void editNote(DvNote note) {
    if (note == null) {
      return;
    }
    DvNoteDialog dialog = new DvNoteDialog(getShell(), note);
    if (dialog.open()) {
      setChanged();
      canvas.setFocus();
    }
  }

  private void showNoteContextDialog(MouseEvent e, DvNote note, Point real) {
    if (note == null) {
      return;
    }
    try {
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteContext.Message");
      IGuiContextHandler contextHandler = new HopGuiVaultNoteContext(model, this, note, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      System.err.println("Error showing note context dialog: " + ex.getMessage());
    } finally {
      canvas.setFocus();
    }
  }

  private void mouseDownOnViewport(MouseEvent e) {
    // Clear the other options
    //
    avoidContextDialog = true;
    cancelRelationshipDrag();
    clearTableDragState();
    clearNoteDragState();
    clearSelectionRegion();

    // Retain the viewport and flag
    //
    navigatingWithViewport = true;
    navigationGrabOffset = new Point(e.x - viewPort.x, e.y - viewPort.y);

    redraw();
  }

  public static HopGuiVaultGraph getInstance() {
    IHopPerspective activePerspective = HopGui.getInstance().getActivePerspective();
    if (activePerspective instanceof ExplorerPerspective perspective) {
      IHopFileTypeHandler typeHandler = perspective.getActiveFileTypeHandler();
      if (typeHandler instanceof HopGuiVaultGraph vaultGraph) {
        return vaultGraph;
      }
    }
    return null;
  }

  private void addToolBar() {
    try {
      // Create a new toolbar at the top of the main composite...
      // Use ToolbarFacade + IToolbarContainer for compatibility with Hop 2.x (SWT + web)
      //
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

      // enable / disable the icons in the toolbar too.
      //
      updateGui();

    } catch (Throwable t) {
      // Log error minimally without requiring a log channel in this graph (no undo/redo etc)
      System.err.println("Error setting up the toolbar for HopGuiVaultGraph: " + t.getMessage());
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || model == null) {
      return; // nothing to do!
    }

    // Do double buffering to prevent flickering on Windows
    //
    boolean needsDoubleBuffering =
        Const.isWindows() && "GUI".equalsIgnoreCase(Const.getHopPlatformRuntime());

    Image image = null;
    GC swtGc = e.gc;

    if (needsDoubleBuffering) {
      image = new Image(hopGui.getDisplay(), area.x, area.y);
      swtGc = new GC(image);
    }

    drawVaultModelImage(swtGc, area.x, area.y);

    if (needsDoubleBuffering) {
      // Draw the image onto the canvas and get rid of the resources
      //
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawVaultModelImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    Point area = new Point(canvas.getBounds().width, canvas.getBounds().height);
    maximum = model.getMaximum();

    try {
      DataVaultModelPainter painter =
          new DataVaultModelPainter(model, gc, variables, area.x, area.y);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification((float) (magnification * PropsUi.getNativeZoomFactor()));
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setSelectionRegion(selectionRegion);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverTableName(mouseOverTableName);
      painter.setMouseOverNoteLink(mouseOverNoteLink);
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.setShowHashKeyFieldNames(
          DataVaultConfigSingleton.getConfig().isDrawingHashKeysInModel());
      painter.setMaximum(model.getMaximum());

      // Pass current (if any) relationship drag state so painter can render the candidate line
      // (in logical coords, before tables).
      painter.setRelationshipDragInfo(
          startRelationshipTable, relationshipDragEndLocation, candidateRelationshipTarget);

      painter.drawDataVaultModel();

      // Capture navigation view geometry (if enabled) so mouseDown/Move can implement panning by
      // dragging inside the viewport rectangle of the minimap.
      graphPort = painter.getGraphPort();
      viewPort = painter.getViewPort();
      lastNavigationScale = painter.getNavigationScale();
      lastNavigationGraphOriginX = painter.getNavigationGraphOriginX();
      lastNavigationGraphOriginY = painter.getNavigationGraphOriginY();

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

  // --- Zoom / toolbar actions with @GuiToolbarElement (modeled on HopGuiPipelineGraph) ---

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_LEVEL,
      label = "  Zoom: ",
      toolTip = "Zoom in or out",
      type = GuiToolbarElementType.COMBO,
      alignRight = true,
      comboValuesMethod = "getZoomLevels")
  public void zoomLevel() {
    readMagnification();
    redraw();
  }

  public List<String> getZoomLevels() {
    return Arrays.asList(
        "25%",
        "50%", "75%", "100%", "150%", "200%", "300%", "400%", "500%", "600%", "700%", "800%",
        "900%", "1000%");
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    magnification += 0.1f;
    if (magnification > 10f) {
      magnification = 10f;
    }
    clearSelectionRegion();
    setZoomLabel();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    magnification -= 0.1f;
    if (magnification < 0.1f) {
      magnification = 0.1f;
    }
    clearSelectionRegion();
    setZoomLabel();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    super.zoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomFit.Tooltip",
      image = "ui/images/zoom-fit.svg")
  public void fitToScreen() {
    super.zoomFitToScreen();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EDIT_MODEL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.EditModel.Tooltip",
      image = "datavault_model.svg")
  public void editModelProperties() {
    editModelProperties(model);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_IMPORT_SOURCES,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ImportSources.Tooltip",
      image = "ui/images/schema.svg")
  public void importDatabaseSourceTables() {
    DvDatabaseSourceImportSupport.importDatabaseTables(
        hopGui.getShell(), hopGui, hopGui.getVariables(), hopGui.getMetadataProvider());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CHECK_MODEL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.CheckModel.Tooltip",
      image = "ui/images/check.svg")
  public void checkModel() {
    if (model == null) {
      return;
    }
    List<ICheckResult> remarks = model.check(hopGui.getMetadataProvider(), hopGui.getVariables());
    CheckResultDialog dialog = new CheckResultDialog(hopGui.getShell(), remarks);
    String tableName = dialog.open();
    if (tableName != null) {
      IDvTable table = model.findTable(tableName);
      if (table != null) {
        editTable(table);
      }
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_GENERATE_DDL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.GenerateDdl.Tooltip",
      image = "ui/images/database.svg")
  public void generateModelDdl() {
    if (model == null || model.getTables() == null) {
      return;
    }

    Map<DatabaseMeta, List<String>> ddlMap = new HashMap<>();
    for (IDvTable table : model.getTables()) {
      try {
        Map<DatabaseMeta, List<String>> tableDdl =
            table.generateUpdateDdl(hopGui.getMetadataProvider(), hopGui.getVariables(), model);
        if (tableDdl != null) {
          for (Map.Entry<DatabaseMeta, List<String>> e : tableDdl.entrySet()) {
            ddlMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
          }
        }
      } catch (Exception e) {
        String tableName =
            !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
        new ErrorDialog(
            hopGui.getShell(), "Error", "Error generating DDL for table '" + tableName + "'", e);
      }
    }

    boolean foundDdl = false;
    for (Map.Entry<DatabaseMeta, List<String>> entry : ddlMap.entrySet()) {
      DatabaseMeta dbMeta = entry.getKey();
      if (dbMeta == null || entry.getValue() == null) {
        continue;
      }
      List<String> ddlStatements = new ArrayList<>();
      for (String ddl : entry.getValue()) {
        if (!Utils.isEmpty(ddl)) {
          ddlStatements.add(ddl);
          foundDdl = true;
        }
      }
      if (ddlStatements.isEmpty()) {
        continue;
      }
      String sql = String.join("\n", ddlStatements);
      SqlEditor sqlEditor =
          new SqlEditor(
              hopGui.getShell(),
              SWT.NONE,
              hopGui.getVariables(),
              dbMeta,
              DbCache.getInstance(),
              sql);
      sqlEditor.open();
    }

    if (!foundDdl) {
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.NoDdlNeeded.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiVaultGraph.NoDdlNeeded.Message"));
      box.open();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DEBUG,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Debug.Tooltip",
      image = "ui/images/debug.svg")
  public void debugPipelines() {
    if (model == null || model.getTables() == null) {
      return;
    }
    for (IDvTable table : model.getTables()) {
      // Only show the pipelines of the selected tables if one or more tables are selected.
      //
      if (!table.isSelected() && model.nrSelectedTables() > 0) {
        continue;
      }
      openUpdatePipeline(table);
    }
  }

  public void openUpdatePipeline(IDvTable table) {
    String tableName =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    try {
      // Provide a static load date value for this batch (used in Constant transform + stored in
      // target)
      Timestamp loadDate = Timestamp.from(Instant.now());

      List<PipelineMeta> pipelineMetas =
          table.generateUpdatePipelines(
              hopGui.getMetadataProvider(), hopGui.getVariables(), model, loadDate, null);
      if (pipelineMetas == null || pipelineMetas.isEmpty()) {
        return;
      }

      for (PipelineMeta pipelineMeta : pipelineMetas) {
        if (pipelineMeta == null) continue;

        // Serialize to XML and back before opening.
        // This ensures the transforms are loaded via the Hop plugin registry / proper classloaders
        // (instead of direct compile-time classes) which prevents class loading issues when
        // opening e.g. the Table Input transform dialog in the generated pipeline.
        String xml = pipelineMeta.getXml(hopGui.getVariables());
        Node pipelineNode = XmlHandler.loadXmlString(xml, PipelineMeta.XML_TAG);
        PipelineMeta reloaded = new PipelineMeta(pipelineNode, hopGui.getMetadataProvider());

        // Open in HopGui (not saved). For multi-source hubs we open one per source.
        HopGui.getExplorerPerspective().addPipeline(reloaded);
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(), "Error", "Error generating debug pipeline for '" + tableName + "'", e);
    }
  }

  // --- @GuiContextAction methods for background canvas context (left click) ---
  // The parentId links to HopGuiVaultContext so they appear in the dialog presented on canvas
  // click.
  // Location is taken from the click point passed in the context (de-magnified model coords).

  @GuiContextAction(
      id = "vault-graph-add-hub",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Hub",
      tooltip = "Add a new Hub table at the click location",
      image = "datavault_hub.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void addHub(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    DvHub hub = new DvHub(getUniqueTableNameFromModel("Hub", realModel));
    PropsUi.setLocation(hub, click != null ? click.x : 50, click != null ? click.y : 50);
    realModel.getTables().add(hub);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-add-satellite",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Satellite",
      tooltip = "Add a new Satellite table at the click location",
      image = "datavault_satellite.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void addSatellite(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    DvSatellite sat = new DvSatellite(getUniqueTableNameFromModel("Satellite", realModel));
    PropsUi.setLocation(sat, click != null ? click.x : 50, click != null ? click.y : 50);
    realModel.getTables().add(sat);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-add-link",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Link",
      tooltip = "Add a new Link table at the click location",
      image = "datavault_link.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void addLink(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    DvLink link = new DvLink(getUniqueTableNameFromModel("Link", realModel));
    PropsUi.setLocation(link, click.x, click.y);
    realModel.getTables().add(link);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-add-note",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.AddNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void addNote(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null || realGraph == null) {
      return;
    }
    if (realModel.getNotes() == null) {
      realModel.setNotes(new ArrayList<>());
    }
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.GENERAL);
    note.setText("");
    PropsUi.setLocation(note, click != null ? click.x : 50, click != null ? click.y : 50);
    PropsUi.setSize(note, ConstUi.NOTE_MIN_SIZE, ConstUi.NOTE_MIN_SIZE);
    realModel.getNotes().add(note);
    realGraph.editNote(note);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-edit-model",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit model properties",
      tooltip = "Edit the properties of this Data Vault model",
      image = "datavault_model.svg",
      category = "Data Vault",
      categoryOrder = "10")
  public void editModelProperties(HopGuiVaultContext context) {
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realGraph != null && realModel != null) {
      realGraph.editModelProperties(realModel);
    }
  }

  @GuiContextAction(
      id = "vault-graph-import-sources",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.ImportSources.Name",
      tooltip = "i18n::HopGuiVaultGraph.ImportSources.Tooltip",
      image = "ui/images/schema.svg",
      category = "Import",
      categoryOrder = "1")
  public void importDatabaseSourceTables(HopGuiVaultContext context) {
    importDatabaseSourceTables();
  }

  // --- @GuiContextAction methods for table context (left click on icon body, not name) ---
  // The parentId links to HopGuiVaultTableContext. Edit does same as name-click; Delete removes
  // table.

  @GuiContextAction(
      id = "vault-graph-edit-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit",
      tooltip = "Edit the properties of this table",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editTableAction(HopGuiVaultTableContext context) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (t != null && realGraph != null) {
      realGraph.editTable(t);
    }
  }

  @GuiContextAction(
      id = "vault-graph-delete-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "Delete",
      tooltip = "Delete this table from the model",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void deleteTable(HopGuiVaultTableContext context) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (t != null && realModel != null && realModel.getTables() != null) {
      realModel.getTables().remove(t);
      if (realGraph != null) {
        realGraph.setChanged();
      }
    }
  }

  @GuiContextAction(
      id = "vault-graph-show-table-pipeline",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "Show update pipeline",
      tooltip = "Generate and show the pipeline used to update this table",
      image = "ui/images/debug.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void debugTablePipeline(HopGuiVaultTableContext context) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (t != null && realGraph != null) {
      realGraph.openUpdatePipeline(t);
    }
  }

  @GuiContextAction(
      id = "vault-graph-edit-note",
      parentId = HopGuiVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.EditNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editNoteAction(HopGuiVaultNoteContext context) {
    DvNote note = context.getNote();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (note != null && realGraph != null) {
      realGraph.editNote(note);
    }
  }

  @GuiContextAction(
      id = "vault-graph-delete-note",
      parentId = HopGuiVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiVaultGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiVaultNoteContext context) {
    DvNote note = context.getNote();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (note != null && realModel != null && realModel.getNotes() != null) {
      realModel.getNotes().remove(note);
      if (realGraph != null) {
        realGraph.setChanged();
      }
    }
  }

  // Small helper so the action methods (which may be called on a dummy graph instance via
  // lambda builder) can still compute unique names using the *real* model from context.
  private String getUniqueTableNameFromModel(String base, DataVaultModel m) {
    if (m == null || m.getTables() == null) {
      return base;
    }
    int num = 1;
    String candidate;
    do {
      candidate = base + " " + num;
      num++;
    } while (hasTableWithNameInModel(candidate, m));
    return candidate;
  }

  private boolean hasTableWithNameInModel(String name, DataVaultModel m) {
    for (IDvTable t : m.getTables()) {
      if (t != null && t.getName() != null && t.getName().equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  public void setZoomLabel() {
    if (toolBarWidgets == null) {
      return;
    }
    Combo combo = (Combo) toolBarWidgets.getWidgetsMap().get(TOOLBAR_ITEM_ZOOM_LEVEL);
    if (combo == null || combo.isDisposed()) {
      return;
    }
    String newString = Math.round(magnification * 100) + "%";
    String oldString = combo.getText();
    if (!newString.equals(oldString)) {
      combo.setText(newString);
    }
  }

  /** Allows for magnifying to any percentage entered by the user... */
  private void readMagnification() {
    if (toolBarWidgets == null) {
      return;
    }
    Combo zoomLabel = (Combo) toolBarWidgets.getWidgetsMap().get(TOOLBAR_ITEM_ZOOM_LEVEL);
    if (zoomLabel == null || zoomLabel.isDisposed()) {
      return;
    }
    String possibleText = zoomLabel.getText().replace("%", "");

    try {
      magnification = Float.parseFloat(possibleText) / 100;
      if (zoomLabel.getText().indexOf('%') < 0) {
        zoomLabel.setText(zoomLabel.getText().concat("%"));
      }
    } catch (Exception e) {
      // ignore invalid input silently for basic impl (core shows dialog)
    }

    // factor for future scroll adjustments (not used yet)

    clearSelectionRegion();
    // no-op for now
  }

  // --- Context menu / click handling support ---

  private IDvTable findTableAtScreen(int screenX, int screenY) {
    if (model == null || model.getTables() == null) {
      return null;
    }
    float cMag = calculateCorrectedMagnification();
    for (IDvTable table : model.getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      // Since setTransform handles mag/pan, visual positions are approx (loc * mag + offset)
      // Box sizes (drawnBox) are now in logical units, so visual size = logical * mag
      int sx = (int) (loc.x * cMag + offset.x);
      int sy = (int) (loc.y * cMag + offset.y);
      int tw = 140;
      int th = 70;
      if (table instanceof DvTableBase base) {
        if (base.getDrawnBoxWidth() > 0) tw = base.getDrawnBoxWidth();
        if (base.getDrawnBoxHeight() > 0) th = base.getDrawnBoxHeight();
      }
      int sw = (int) (tw * cMag);
      int sh = (int) (th * cMag);
      if (screenX >= sx && screenX < sx + sw && screenY >= sy && screenY < sy + sh) {
        return table;
      }
    }
    return null;
  }

  /**
   * Test if the table's current drawn visual rectangle (in screen coords, using drawnBox size +
   * magnification + offset, exactly like findTableAtScreen and AreaOwner registration) overlaps the
   * given lasso rect (in screen pixels). Used to decide which tables to select on lasso up.
   */
  private boolean isTableInLassoScreenRect(
      IDvTable table, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (table == null) {
      return false;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = 140;
    int th = 70;
    if (table instanceof DvTableBase base) {
      if (base.getDrawnBoxWidth() > 0) tw = base.getDrawnBoxWidth();
      if (base.getDrawnBoxHeight() > 0) th = base.getDrawnBoxHeight();
    }
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int sw = Math.max(1, tw);
    int sh = Math.max(1, th);

    int tMaxX = tMinX + sw;
    int tMaxY = tMinY + sh;

    // Overlap test (intersection, not strict containment): select the table if its visual area
    // touches the lasso rect at all. This is practical for lasso selection.
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  /** Lookup area owner at screen mouse coords (for name vs icon body hits, hover etc). */
  public AreaOwner getVisibleAreaOwner(int x, int y) {
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      Rectangle area = areaOwner.getArea();
      if (areaOwner.contains(x, y)) {
        return areaOwner;
      }
    }
    return null;
  }

  private void unselectAllTables() {
    if (model != null && model.getTables() != null) {
      for (IDvTable t : model.getTables()) {
        if (t != null) {
          t.setSelected(false);
        }
      }
    }
  }

  private List<IDvTable> getSelectedTables() {
    List<IDvTable> list = new ArrayList<>();
    if (model != null && model.getTables() != null) {
      for (IDvTable t : model.getTables()) {
        if (t != null && t.isSelected()) {
          list.add(t);
        }
      }
    }
    return list;
  }

  private void clearTableDragState() {
    iconDragStart = null;
    iconDragCommitted = false;
    dragSelection = false;
    iconOffset = null;
    currentTable = null;
    navigatingWithViewport = false;
    navigationGrabOffset = null;
    clearSelectionRegion();
  }

  private void clearSelectionRegion() {
    selectionRegion = null;
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setToolTipText(null);
      canvas.setData("mode", "null");
      setCursor(null);
    }
  }

  private void editTable(IDvTable table) {
    if (table == null) {
      return;
    }
    boolean changed = false;
    Shell parentShell = getShell();
    if (table.getTableType() == DvTableType.HUB) {
      DvHubDialog dialog = new DvHubDialog(parentShell, hopGui, (DvHub) table);
      changed = dialog.open();
    } else if (table.getTableType() == DvTableType.SATELLITE) {
      DvSatelliteDialog dialog =
          new DvSatelliteDialog(parentShell, hopGui, (DvSatellite) table, model);
      changed = dialog.open();
    } else if (table.getTableType() == DvTableType.LINK) {
      DvLinkDialog dialog =
          new DvLinkDialog(parentShell, hopGui, (DvLink) table, model);
      changed = dialog.open();
    }
    if (changed) {
      table.setChanged();
      setChanged();
      canvas.setFocus();
    }
  }

  private void editModelProperties(DataVaultModel modelToEdit) {
    if (modelToEdit == null) {
      return;
    }
    HopGuiDataVaultModelDialog dialog =
        new HopGuiDataVaultModelDialog(getShell(), hopGui, modelToEdit);
    if (dialog.open()) {
      setChanged();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
    }
  }

  private void cancelRelationshipDrag() {
    startRelationshipTable = null;
    relationshipDragEndLocation = null;
    candidateRelationshipTarget = null;
    navigatingWithViewport = false;
    navigationGrabOffset = null;
    clearTableDragState();
    clearSelectionRegion();
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setData("mode", "null");
    }
  }

  private boolean isValidRelationshipPair(IDvTable a, IDvTable b) {
    if (a == null || b == null || a == b) {
      return false;
    }
    DvTableType ta = a.getTableType();
    DvTableType tb = b.getTableType();
    boolean hubSat =
        (ta == DvTableType.HUB && tb == DvTableType.SATELLITE)
            || (ta == DvTableType.SATELLITE && tb == DvTableType.HUB);
    boolean hubLink =
        (ta == DvTableType.HUB && tb == DvTableType.LINK)
            || (ta == DvTableType.LINK && tb == DvTableType.HUB);
    boolean linkSat =
        (ta == DvTableType.LINK && tb == DvTableType.SATELLITE)
            || (ta == DvTableType.SATELLITE && tb == DvTableType.LINK);
    return hubSat || hubLink || linkSat;
  }

  /**
   * Create a relationship between the two tables by mutating the appropriate name reference(s)
   * inside the table objects (which live in the model's tables list). Supports hub-satellite,
   * hub-link, and link-satellite relationships. Sets changed on affected table(s) and model.
   */
  private void createRelationship(IDvTable from, IDvTable to) {
    if (from == null || to == null || from == to) {
      return;
    }
    if (!isValidRelationshipPair(from, to)) {
      return;
    }

    DvTableType t1 = from.getTableType();
    DvTableType t2 = to.getTableType();
    boolean modelChanged = false;

    if ((t1 == DvTableType.HUB && t2 == DvTableType.SATELLITE)
        || (t1 == DvTableType.SATELLITE && t2 == DvTableType.HUB)) {
      DvSatellite sat = (t2 == DvTableType.SATELLITE) ? (DvSatellite) to : (DvSatellite) from;
      DvHub hub = (t1 == DvTableType.HUB) ? (DvHub) from : (DvHub) to;
      String hubName = hub.getName();
      if (hubName != null) {
        String current = sat.getHubName();
        if (!java.util.Objects.equals(current, hubName)) {
          sat.setHubName(hubName);
          // Prefer hub over link for the parent; clear alternate to keep model clean
          sat.setLinkName(null);
          modelChanged = true;
        }
      }
    } else if ((t1 == DvTableType.HUB && t2 == DvTableType.LINK)
        || (t1 == DvTableType.LINK && t2 == DvTableType.HUB)) {
      DvLink link = (t2 == DvTableType.LINK) ? (DvLink) to : (DvLink) from;
      DvHub hub = (t1 == DvTableType.HUB) ? (DvHub) from : (DvHub) to;
      String hubName = hub.getName();
      if (hubName != null) {
        List<String> currentHubs = link.getHubNames();
        if (currentHubs == null) {
          currentHubs = new ArrayList<>();
        }
        if (!currentHubs.contains(hubName)) {
          List<String> newHubs = new ArrayList<>(currentHubs);
          newHubs.add(hubName);
          link.setHubNames(newHubs);
          modelChanged = true;
        }
      }
    } else if ((t1 == DvTableType.LINK && t2 == DvTableType.SATELLITE)
        || (t1 == DvTableType.SATELLITE && t2 == DvTableType.LINK)) {
      DvSatellite sat = (t2 == DvTableType.SATELLITE) ? (DvSatellite) to : (DvSatellite) from;
      DvLink link = (t1 == DvTableType.LINK) ? (DvLink) from : (DvLink) to;
      String linkName = link.getName();
      String satName = sat.getName();
      if (linkName != null) {
        String current = sat.getLinkName();
        if (!java.util.Objects.equals(current, linkName)) {
          sat.setLinkName(linkName);
          sat.setHubName(null);
          modelChanged = true;
        }
      }
      if (linkName != null && satName != null) {
        List<String> currentSats = link.getLinkSatelliteNames();
        if (currentSats == null) {
          currentSats = new ArrayList<>();
        }
        if (!currentSats.contains(satName)) {
          List<String> newSats = new ArrayList<>(currentSats);
          newSats.add(satName);
          link.setLinkSatelliteNames(newSats);
          modelChanged = true;
        }
      }
    }

    if (modelChanged) {
      setChanged();
    }
  }

  /**
   * Draw a temporary dashed line from the center of the source table to the current mouse position
   * while dragging a relationship. Uses screen coordinates (overlay after painter).
   */
  private void showVaultContextDialog(MouseEvent e, Point real) {
    try {
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = "Select the action to execute or the table type to add:";
      IGuiContextHandler contextHandler = new HopGuiVaultContext(model, this, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      System.err.println("Error showing vault context dialog: " + ex.getMessage());
    } finally {
      canvas.setFocus();
    }
  }

  private void showTableContextDialog(MouseEvent e, IDvTable table) {
    if (table == null) return;
    try {
      Point real = screen2real(e.x, e.y);
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = "Select action for table '" + table.getName() + "':";
      IGuiContextHandler contextHandler = new HopGuiVaultTableContext(model, this, table, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      System.err.println("Error showing table context dialog: " + ex.getMessage());
    } finally {
      canvas.setFocus();
    }
  }

  // --- IHopFileTypeHandler implementation ---

  @Override
  public Object getSubject() {
    return model;
  }

  @Override
  public String getName() {
    return model != null ? model.getName() : "Data Vault Model";
  }

  @Override
  public void setName(String name) {
    if (model != null) {
      model.setName(name);
      setChanged();
    }
    // Update tab label (text derived from name) and tooltip in explorer perspective, like Hop code
    // does via perspective.updateTabItem
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

  // Note: DataVaultModel may not have filename yet, we can extend or keep in handler
  private String filename;

  @Override
  public void setFilename(String filename) {
    if (model != null) {
      model.setFilename(filename);
    } else {
      this.filename = filename;
    }
    // Update the tab label (name may be derived from filename basename) + tooltip with full path.
    // Exact same pattern as used in HopDataOrchestrationPerspective.updateTabLabel and
    // ExplorerPerspective.updateTabItem + BaseExplorerFileTypeHandler.
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = "10051-menu-data-vault-model-export-to-svg",
      label = "i18n::HopGui.Menu.File.ExportToSVG",
      image = "ui/images/image.svg",
      parentId = HopGui.ID_MAIN_MENU_FILE)
  public void menuFileExportToSvg() {
    IHopFileTypeHandler active = HopGui.getExplorerPerspective().getActiveFileTypeHandler();
    if (active instanceof HopGuiVaultGraph vaultGraph) {
      vaultGraph.exportModelToSvg();
      return;
    }
    HopGui.getInstance().fileDelegate.exportToSvg();
  }

  public void exportModelToSvg() {
    if (model == null) {
      return;
    }
    try {
      boolean showHashKeyFieldNames =
          DataVaultConfigSingleton.getConfig().isDrawingHashKeysInModel();
      String svgXml =
          DataVaultModelSvgPainter.generateDataVaultModelSvg(
              model, 1.0f, variables, showHashKeyFieldNames);

      String proposedName = Const.NVL(model.getName(), "data-vault-model") + ".svg";
      String proposedFilename =
          variables.getVariable("user.home") + File.separator + proposedName;

      String filename =
          BaseDialog.presentFileDialog(
              true,
              hopGui.getShell(),
              null,
              variables,
              HopVfs.getFileObject(proposedFilename),
              new String[] {"*.svg"},
              new String[] {"SVG Files"},
              true);
      if (filename == null) {
        return;
      }

      String realFilename = variables.resolve(filename);
      var file = HopVfs.getFileObject(realFilename);
      if (file.exists()) {
        MessageBox box =
            new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
        box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Exists.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Exists.Message"));
        if ((box.open() & SWT.YES) == 0) {
          return;
        }
      }

      try (OutputStream outputStream = HopVfs.getOutputStream(file, false)) {
        outputStream.write(svgXml.getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Error.Message"),
          e);
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
    // Not applicable for model
  }

  @Override
  public void stop() {
    // Not applicable
  }

  @Override
  public void pause() {
    // Not applicable
  }

  @Override
  public void resume() {
    // Not applicable
  }

  @Override
  public void preview() {
    // Not applicable
  }

  @Override
  public void debug() {
    // Not applicable
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
  }

  @GuiKeyboardShortcut(control = true, key = 'a')
  @GuiOsxKeyboardShortcut(command = true, key = 'a')
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SELECT_ALL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.SelectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/select-all.svg")
  @Override
  public void selectAll() {
    if (model != null) {
      if (model.getTables() != null) {
        for (IDvTable t : model.getTables()) {
          if (t != null) {
            t.setSelected(true);
          }
        }
      }
      if (model.getNotes() != null) {
        for (DvNote note : model.getNotes()) {
          if (note != null) {
            note.setSelected(true);
          }
        }
      }
      redraw();
    }
  }

  @GuiKeyboardShortcut(key = SWT.ESC)
  @GuiOsxKeyboardShortcut(key = SWT.ESC)
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNSELECT_ALL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.UnselectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/unselect-all.svg")
  @Override
  public void unselectAll() {
    unselectAllOnCanvas();
    clearSelectionRegion();
    redraw();
  }

  @Override
  public void copySelectedToClipboard() {
    // TODO
  }

  @Override
  public void cutSelectedToClipboard() {
    // TODO
  }

  @Override
  public void deleteSelected() {
    // TODO
  }

  @Override
  public void pasteFromClipboard() {
    // TODO
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
            BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiVaultGraph.SaveFile.Dialog.Message", buildTabName()));
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
          BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Message"),
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
    // The perspective will remove the tab
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
  public void undo() {
    // no undo for basic version
  }

  @Override
  public void redo() {
    // no redo for basic version
  }

  @Override
  public Map<String, Object> getStateProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put("magnification", magnification);
    if (offset != null) {
      props.put("offsetX", offset.x);
      props.put("offsetY", offset.y);
    }
    return props;
  }

  @Override
  public void applyStateProperties(Map<String, Object> stateProperties) {
    Double fMagnification = (Double) stateProperties.get(STATE_MAGNIFICATION);
    magnification = fMagnification == null ? 1.0f : fMagnification.floatValue();
    setZoomLabel();

    // Offsets used to be integers so don't automatically map to Double.
    //
    Object xOffset = stateProperties.get(STATE_SCROLL_X_SELECTION);
    if (xOffset != null) {
      offset.x = Double.parseDouble(xOffset.toString());
    }
    Object yOffset = stateProperties.get(STATE_SCROLL_Y_SELECTION);
    if (yOffset != null) {
      offset.y = Double.parseDouble(yOffset.toString());
    }
    redraw();
  }

  int[] getSelectedTableIndices(List<IDvTable> selection) {
    int[] indices = new int[selection.size()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = model.getTables().indexOf(selection.get(i));
    }
    return indices;
  }

  @Override
  public SnapAllignDistribute createSnapAlignDistribute() {
    List<IDvTable> selection = getSelectedTables();
    int[] indices = getSelectedTableIndices(selection);

    return new SnapAllignDistribute(model, selection, indices, hopGui.undoDelegate, this);
  }

  @Override
  public IVariables getVariables() {
    return variables;
  }

  // --- Other required from abstract or interfaces ---

  // hasChanged and setChanged already overridden above for the interface + abstract

  // IActionContextHandlersProvider
  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>(); // basic, no extra
  }

  public void setModel(DataVaultModel model) {
    cancelRelationshipDrag();
    clearTableDragState();
    clearSelectionRegion();
    areaOwners.clear();
    mouseOverTableName = null;
    mouseOverNoteLink = null;
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setToolTipText(null);
    }
    lastClick = null;
    this.model = model;
    redraw();
  }
}
