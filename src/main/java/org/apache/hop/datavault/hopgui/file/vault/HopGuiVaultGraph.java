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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.core.Props;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * Basic implementation of the vault graph / editor for Data Vault models in Hop GUI.
 * Uses a canvas and DataVaultModelPainter to draw the model.
 * No undo/redo support for this initial version.
 * Implements IHopFileTypeHandler so it can be used as a tab in the explorer perspective.
 */
@GuiPlugin(
    id = "HopGuiVaultGraph",
    description = "i18n::HopGuiVaultGraph.Description")
public class HopGuiVaultGraph extends Composite implements IHopFileTypeHandler, IGuiRefresher {

  private static final Class<?> PKG = HopGuiVaultGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiVaultGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiVaultGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN = "HopGuiVaultGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT = "HopGuiVaultGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 = "HopGuiVaultGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT = "HopGuiVaultGraph-ToolBar-10040-Zoom-Fit";

  private final HopGui hopGui;
  private final ExplorerPerspective perspective;
  private final HopVaultFileType fileType;
  private DataVaultModel model;

  private IVariables variables;
  private Canvas canvas;
  private float magnification = 1.0f;
  private Point offset = new Point(0, 0);

  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;

  private boolean changed = false;

  private boolean avoidContextDialog;

  // Area owners for fine-grained hit testing (name vs body of table icon), like pipeline/workflow graphs.
  // Used for mouse-over underline on name, click-name=edit, click-body=context.
  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverTableName;

  // Relationship drag state (middle-mouse-button or shift+left-button drag from table to table)
  // to create hub<->satellite or hub<->link relationships (stored as name refs in the tables).
  private IDvTable startRelationshipTable;
  private Point relationshipDragEndLocation; // screen coordinates for temp line
  private IDvTable candidateRelationshipTarget;

  private Point lastClick;
  private int lastButton;

  private static final int ICON_DRAG_THRESHOLD_PX = 3;

  // Drag state for moving tables: left-click + drag on table icon body (not the name part).
  // Moves the clicked table + all currently selected tables. Position relative to initial click.
  private Point iconOffset;
  private Point iconDragStartScreen;
  private boolean iconDragCommitted;
  private boolean dragSelection;
  private Point[] previousTableLocations;
  private IDvTable currentTable;

  public HopGuiVaultGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      DataVaultModel model,
      HopVaultFileType fileType) {
    super(parent, SWT.NO_BACKGROUND);
    this.hopGui = hopGui;
    this.perspective = perspective;
    this.model = model;
    this.fileType = fileType;

    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());

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
    canvas.addPaintListener(event -> drawVaultModel(event.gc));

    // Mouse listener for context dialog on left click (background) + table edit + relationship drag (middle/shift)
    canvas.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseDown(MouseEvent e) {
            Point real = screen2real( e.x, e.y );
            lastClick = new Point( real.x, real.y );
            lastButton = e.button;
            boolean shift = (e.stateMask & SWT.SHIFT) != 0;
            boolean control = (e.stateMask & SWT.MOD1) != 0;

            AreaOwner areaOwner = getVisibleAreaOwner( e.x, e.y );
            IDvTable hit = null;
            if ( areaOwner != null ) {
              Object o = areaOwner.getOwner();
              if ( o instanceof IDvTable t ) {
                hit = t;
              } else if ( areaOwner.getParent() instanceof IDvTable t ) {
                hit = t;
              }
            }
            if ( hit == null ) {
              hit = findTableAtScreen( e.x, e.y ); // fallback e.g. for relationship start
            }

            if ( hit != null ) {
              if ( e.button == 2 || (e.button == 1 && shift) ) {
                // Middle button or Shift+Left: start dragging a relationship from this table
                // (hub<->sat or hub<->link). Completion on mouseUp.
                startRelationshipTable = hit;
                relationshipDragEndLocation = new Point( e.x, e.y );
                candidateRelationshipTarget = hit;
                mouseOverTableName = null;
                clearTableDragState();
                avoidContextDialog = true;
                redraw();
                return;
              }
              if ( e.button == 1 ) {
                if ( areaOwner != null
                    && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME ) {
                  // Left click on the (underlined) name: open edit dialog (hyperlink behavior)
                  editTable( hit );
                  avoidContextDialog = true;
                  return;
                } else {
                  // Left click on table body/icon: setup drag (defer context/select to mouseUp if no drag)
                  currentTable = hit;
                  iconDragStartScreen = new Point( e.x, e.y );
                  iconDragCommitted = false;
                  previousTableLocations = getSelectedTableLocations();
                  Point p = hit.getLocation() != null ? hit.getLocation() : new Point( 0, 0 );
                  iconOffset = new Point( real.x - p.x, real.y - p.y );
                  avoidContextDialog = true;
                  redraw();
                  return;
                }
              }
            }

            // Background click: cancel active relationship drag or table drag (like hop drag cancel on bg)
            if ( startRelationshipTable != null || currentTable != null || iconDragStartScreen != null ) {
              cancelRelationshipDrag();
              clearTableDragState();
              avoidContextDialog = true;
              redraw();
              return;
            }

            redraw();
          }

          @Override
          public void mouseUp(MouseEvent e) {
            if ( startRelationshipTable != null ) {
              // Complete relationship drag if dropped on a different valid table
              IDvTable target = findTableAtScreen( e.x, e.y );
              if ( target != null && target != startRelationshipTable ) {
                createRelationship( startRelationshipTable, target );
              }
              cancelRelationshipDrag();
              clearTableDragState();
              avoidContextDialog = true;
              redraw();
              return;
            }

            if ( e.button == 1 ) {
              if ( iconDragCommitted || dragSelection ) {
                // end table drag (moves were applied live during mouseMove)
                if ( model != null ) {
                  model.setChanged();
                }
                clearTableDragState();
                avoidContextDialog = false;
                redraw();
                return;
              }

              Point realClick = screen2real( e.x, e.y );
              if ( lastClick != null && lastClick.x == realClick.x && lastClick.y == realClick.y ) {
                // pure click (no drag)
                IDvTable hit = findTableAtScreen( e.x, e.y );
                if ( hit == null ) {
                  if ( !avoidContextDialog ) {
                    Point real = screen2real( e.x, e.y );
                    showVaultContextDialog( e, real );
                  } else {
                    avoidContextDialog = false;
                  }
                } else {
                  AreaOwner areaOwner = getVisibleAreaOwner( e.x, e.y );
                  if ( areaOwner != null
                      && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_ICON ) {
                    boolean control = ( e.stateMask & SWT.MOD1 ) != 0;
                    if ( control ) {
                      hit.setSelected( ! hit.isSelected() );
                      redraw();
                    } else {
                      showTableContextDialog( e, hit );
                    }
                  }
                  avoidContextDialog = false;
                }
              } else {
                // moved without committing drag
                clearTableDragState();
                avoidContextDialog = false;
              }
            } else if ( avoidContextDialog ) {
              avoidContextDialog = false;
            }

            lastButton = 0;
          }

          @Override
          public void mouseDoubleClick(MouseEvent e) {
            IDvTable hit = findTableAtScreen( e.x, e.y );
            if ( hit != null ) {
              editTable( hit );
            }
            clearTableDragState();
          }
        });

    // Separate move listener (our SWT MouseAdapter only covers MouseListener, not MouseMoveListener)
    canvas.addMouseMoveListener(
        new MouseMoveListener() {
          @Override
          public void mouseMove(MouseEvent e) {
            boolean doRedraw = false;
            if ( startRelationshipTable != null ) {
              relationshipDragEndLocation = new Point( e.x, e.y );
              candidateRelationshipTarget = findTableAtScreen( e.x, e.y );
              doRedraw = true;
            }

            // Table drag move (left button on body after threshold)
            if ( currentTable != null
                && ( e.stateMask & SWT.BUTTON1 ) != 0
                && startRelationshipTable == null ) {
              if ( iconOffset == null ) {
                iconOffset = new Point( 0, 0 );
              }
              Point real = screen2real( e.x, e.y );
              Point icon = new Point( real.x - iconOffset.x, real.y - iconOffset.y );

              if ( !iconDragCommitted && iconDragStartScreen != null ) {
                int dxs = e.x - iconDragStartScreen.x;
                int dys = e.y - iconDragStartScreen.y;
                int threshSq = ICON_DRAG_THRESHOLD_PX * ICON_DRAG_THRESHOLD_PX;
                if ( dxs * dxs + dys * dys > threshSq ) {
                  iconDragCommitted = true;
                  dragSelection = true;
                  doRedraw = true;
                }
              }

              if ( iconDragCommitted ) {
                if ( currentTable != null && !currentTable.isSelected() ) {
                  unselectAllTables();
                  currentTable.setSelected( true );
                  Point p = currentTable.getLocation();
                  previousTableLocations = new Point[] { p != null ? new Point( p.x, p.y ) : new Point( 0, 0 ) };
                  doRedraw = true;
                }
                int dx = icon.x - currentTable.getLocation().x;
                int dy = icon.y - currentTable.getLocation().y;
                moveSelectedTables( dx, dy );
                doRedraw = true;
              }
            }

            // Update mouse-over for table name underline (only on name area, not during drag/rel)
            AreaOwner areaOwner = getVisibleAreaOwner( e.x, e.y );
            String newOver = null;
            if ( areaOwner != null
                && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
                && startRelationshipTable == null
                && !dragSelection ) {
              newOver = (String) areaOwner.getOwner();
            }
            if ( (mouseOverTableName == null && newOver != null)
                || (mouseOverTableName != null && !mouseOverTableName.equals(newOver)) ) {
              doRedraw = true;
            }
            mouseOverTableName = newOver;

            if ( doRedraw ) {
              redraw();
            }
          }
        });

    // Make sure we can receive focus etc.
    canvas.setFocus();

    // Initial zoom label
    setZoomLabel();

    // Ensure layout
    layout(true, true);
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

  private void drawVaultModel(GC swtGc) {
    if (model == null) {
      return;
    }

    int width = canvas.getBounds().width;
    int height = canvas.getBounds().height;

    // Use SwtGc wrapper for IGc
    SwtGc gc = null;
    try {
      gc =
          new SwtGc(
              swtGc,
              width,
              height,
              PropsUi.getInstance().getIconSize());

      areaOwners.clear();
      DataVaultModelPainter painter =
          new DataVaultModelPainter(
              model, gc, width, height, magnification, offset, areaOwners, mouseOverTableName);
      // Pass current (if any) relationship drag state so painter can render the candidate line
      // (in logical coords, before tables).
      painter.setRelationshipDragInfo(
          startRelationshipTable, relationshipDragEndLocation, candidateRelationshipTarget);
      painter.draw();

    } catch (Exception e) {
      // Log error, draw message
      swtGc.drawText("Error drawing model: " + e.getMessage(), 10, 10);
    } finally {
      if (gc != null) {
        gc.dispose();
      }
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
        "25%", "50%", "75%", "100%", "150%", "200%", "300%", "400%", "500%", "600%", "700%", "800%",
        "900%", "1000%");
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  public void zoomIn() {
    magnification += 0.1f;
    if (magnification > 10f) {
      magnification = 10f;
    }
    setZoomLabel();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  public void zoomOut() {
    magnification -= 0.1f;
    if (magnification < 0.1f) {
      magnification = 0.1f;
    }
    setZoomLabel();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  public void zoom100Percent() {
    magnification = 1.0f;
    setZoomLabel();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomFit.Tooltip",
      image = "ui/images/zoom-fit.svg")
  public void fitToScreen() {
    if (model == null
        || model.getTables() == null
        || model.getTables().isEmpty()
        || canvas == null
        || canvas.isDisposed()) {
      magnification = 1.0f;
      setZoomLabel();
      redraw();
      return;
    }

    int canvasW = canvas.getBounds().width;
    int canvasH = canvas.getBounds().height;
    if (canvasW <= 20 || canvasH <= 20) {
      // not laid out yet, use defaults
      canvasW = 800;
      canvasH = 600;
    }

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;

    for (IDvTable t : model.getTables()) {
      Point loc = t.getLocation();
      if (loc == null) {
        loc = new Point(50, 50);
      }
      int tw = 140;
      int th = 70;
      if (t instanceof DvTableBase base) {
        if (base.getDrawnBoxWidth() > 0) tw = base.getDrawnBoxWidth();
        if (base.getDrawnBoxHeight() > 0) th = base.getDrawnBoxHeight();
      }
      minX = Math.min(minX, loc.x);
      minY = Math.min(minY, loc.y);
      maxX = Math.max(maxX, loc.x + tw);
      maxY = Math.max(maxY, loc.y + th);
    }

    int contentW = maxX - minX;
    int contentH = maxY - minY;
    if (contentW <= 0 || contentH <= 0) {
      magnification = 1.0f;
    } else {
      float zoomW = (float) (canvasW - 40) / contentW; // some padding
      float zoomH = (float) (canvasH - 40) / contentH;
      magnification = Math.min(zoomW, zoomH);
      if (magnification > 3f) magnification = 3f;
      if (magnification < 0.1f) magnification = 0.1f;
    }
    setZoomLabel();
    redraw();
  }

  // --- @GuiContextAction methods for background canvas context (left click) ---
  // The parentId links to HopGuiVaultContext so they appear in the dialog presented on canvas click.
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
  public void addHub( HopGuiVaultContext context ) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if ( realModel == null ) {
      return;
    }
    DvHub hub = new DvHub( getUniqueTableNameFromModel( "Hub", realModel ) );
    hub.setLocation( click != null ? new Point( click.x, click.y ) : new Point( 50, 50 ) );
    realModel.getTables().add( hub );
    realModel.setChanged();
    realGraph.redraw();
    realGraph.updateGui();
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
  public void addSatellite( HopGuiVaultContext context ) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if ( realModel == null ) {
      return;
    }
    DvSatellite sat = new DvSatellite( getUniqueTableNameFromModel( "Satellite", realModel ) );
    sat.setLocation( click != null ? new Point( click.x, click.y ) : new Point( 50, 50 ) );
    realModel.getTables().add( sat );
    realModel.setChanged();
    realGraph.redraw();
    realGraph.updateGui();
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
  public void addLink( HopGuiVaultContext context ) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if ( realModel == null ) {
      return;
    }
    DvLink link = new DvLink( getUniqueTableNameFromModel( "Link", realModel ) );
    link.setLocation( click != null ? new Point( click.x, click.y ) : new Point( 50, 50 ) );
    realModel.getTables().add( link );
    realModel.setChanged();
    realGraph.redraw();
    realGraph.updateGui();
  }

  // --- @GuiContextAction methods for table context (left click on icon body, not name) ---
  // The parentId links to HopGuiVaultTableContext. Edit does same as name-click; Delete removes table.

  @GuiContextAction(
      id = "vault-graph-edit-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit",
      tooltip = "Edit the properties of this table",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editTableAction( HopGuiVaultTableContext context ) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if ( t != null && realGraph != null ) {
      realGraph.editTable( t );
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
  public void deleteTable( HopGuiVaultTableContext context ) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if ( t != null && realModel != null && realModel.getTables() != null ) {
      realModel.getTables().remove( t );
      realModel.setChanged();
      if ( realGraph != null ) {
        realGraph.redraw();
        realGraph.updateGui();
      }
    }
  }

  // Small helper so the action methods (which may be called on a dummy graph instance via
  // lambda builder) can still compute unique names using the *real* model from context.
  private String getUniqueTableNameFromModel( String base, DataVaultModel m ) {
    if ( m == null || m.getTables() == null ) {
      return base;
    }
    int num = 1;
    String candidate;
    do {
      candidate = base + " " + num;
      num++;
    } while ( hasTableWithNameInModel( candidate, m ) );
    return candidate;
  }

  private boolean hasTableWithNameInModel( String name, DataVaultModel m ) {
    for ( IDvTable t : m.getTables() ) {
      if ( t != null && t.getName() != null && t.getName().equalsIgnoreCase( name ) ) {
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

    float oldMagnification = magnification;
    try {
      float possibleFloatMagnification = Float.parseFloat(possibleText) / 100;
      magnification = possibleFloatMagnification;
      if (zoomLabel.getText().indexOf('%') < 0) {
        zoomLabel.setText(zoomLabel.getText().concat("%"));
      }
    } catch (Exception e) {
      // ignore invalid input silently for basic impl (core shows dialog)
    }

    // factor for future scroll adjustments (not used yet)
    float factor = magnification / oldMagnification;
    // no-op for now
  }

  // --- Context menu / click handling support ---

  private Point screen2real( int x, int y ) {
    if ( magnification <= 0 ) {
      return new Point( x - offset.x, y - offset.y );
    }
    return new Point( (int) ((x - offset.x) / magnification ), (int) ((y - offset.y) / magnification ) );
  }

  private IDvTable findTableAtScreen( int screenX, int screenY ) {
    if ( model == null || model.getTables() == null ) {
      return null;
    }
    for ( IDvTable table : model.getTables() ) {
      Point loc = table.getLocation();
      if ( loc == null ) {
        continue;
      }
      // Since setTransform handles mag/pan, visual positions are approx (loc * mag + offset)
      // Box sizes (drawnBox) are now in logical units, so visual size = logical * mag
      int sx = (int) ( loc.x * magnification ) + offset.x;
      int sy = (int) ( loc.y * magnification ) + offset.y;
      int tw = 140;
      int th = 70;
      if (table instanceof DvTableBase base) {
        if (base.getDrawnBoxWidth() > 0) tw = base.getDrawnBoxWidth();
        if (base.getDrawnBoxHeight() > 0) th = base.getDrawnBoxHeight();
      }
      int sw = (int) (tw * magnification);
      int sh = (int) (th * magnification);
      if ( screenX >= sx && screenX < sx + sw && screenY >= sy && screenY < sy + sh ) {
        return table;
      }
    }
    return null;
  }

  /** Lookup area owner at screen mouse coords (for name vs icon body hits, hover etc). */
  public AreaOwner getVisibleAreaOwner(int x, int y) {
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      if (areaOwner.contains(x, y)) {
        return areaOwner;
      }
    }
    return null;
  }

  private void unselectAllTables() {
    if ( model != null && model.getTables() != null ) {
      for ( IDvTable t : model.getTables() ) {
        if ( t != null ) {
          t.setSelected( false );
        }
      }
    }
  }

  private List<IDvTable> getSelectedTables() {
    List<IDvTable> list = new ArrayList<>();
    if ( model != null && model.getTables() != null ) {
      for ( IDvTable t : model.getTables() ) {
        if ( t != null && t.isSelected() ) {
          list.add( t );
        }
      }
    }
    return list;
  }

  private Point[] getSelectedTableLocations() {
    List<IDvTable> sel = getSelectedTables();
    Point[] locs = new Point[ sel.size() ];
    for ( int i = 0; i < sel.size(); i++ ) {
      Point p = sel.get( i ).getLocation();
      locs[ i ] = ( p != null ) ? new Point( p.x, p.y ) : new Point( 0, 0 );
    }
    return locs;
  }

  private void clearTableDragState() {
    iconDragStartScreen = null;
    iconDragCommitted = false;
    dragSelection = false;
    iconOffset = null;
    previousTableLocations = null;
    currentTable = null;
  }

  private void moveSelectedTables( int dx, int dy ) {
    List<IDvTable> selected = getSelectedTables();
    if ( selected.isEmpty() ) {
      return;
    }
    // prevent negative coordinates
    for ( IDvTable t : selected ) {
      Point loc = t.getLocation();
      if ( loc == null ) {
        loc = new Point( 0, 0 );
      }
      if ( loc.x + dx < 0 ) {
        dx = -loc.x;
      }
      if ( loc.y + dy < 0 ) {
        dy = -loc.y;
      }
    }
    for ( IDvTable t : selected ) {
      Point loc = t.getLocation();
      if ( loc == null ) {
        loc = new Point( 0, 0 );
      }
      t.setLocation( new Point( loc.x + dx, loc.y + dy ) );
    }
  }

  private void editTable( IDvTable table ) {
    if ( table == null ) {
      return;
    }
    boolean changed = false;
    Shell parentShell = getShell();
    if ( table.getTableType() == DvTableType.HUB ) {
      HopGuiHubDialog dialog = new HopGuiHubDialog( parentShell, variables, (DvHub) table );
      changed = dialog.open();
    } else if ( table.getTableType() == DvTableType.SATELLITE ) {
      HopGuiSatelliteDialog dialog = new HopGuiSatelliteDialog( parentShell, variables, (DvSatellite) table );
      changed = dialog.open();
    } else if ( table.getTableType() == DvTableType.LINK ) {
      HopGuiLinkDialog dialog = new HopGuiLinkDialog( parentShell, variables, (DvLink) table );
      changed = dialog.open();
    }
    if ( changed ) {
      table.setChanged();
      if ( model != null ) {
        model.setChanged();
      }
      redraw();
    }
  }

  private void cancelRelationshipDrag() {
    startRelationshipTable = null;
    relationshipDragEndLocation = null;
    candidateRelationshipTarget = null;
    clearTableDragState();
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
    return hubSat || hubLink;
  }

  /**
   * Create a relationship between the two tables by mutating the appropriate name reference(s)
   * inside the table objects (which live in the model's tables list). Only hub-satellite and
   * hub-link are supported per the spec. Sets changed on affected table(s) and model.
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
    }

    if (modelChanged && model != null) {
      model.setChanged();
      redraw();
      updateGui();
    }
  }

  /**
   * Draw a temporary dashed line from the center of the source table to the current mouse
   * position while dragging a relationship. Uses screen coordinates (overlay after painter).
   */
  private void showVaultContextDialog( MouseEvent e, Point real ) {
    try {
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map( canvas, null, e.x, e.y );
      String message = "Select the action to execute or the table type to add:";
      IGuiContextHandler contextHandler = new HopGuiVaultContext( model, this, real );
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection( parent, message, new Point( p.x, p.y ), contextHandler );
    } catch ( Exception ex ) {
      System.err.println( "Error showing vault context dialog: " + ex.getMessage() );
    }
  }

  private void showTableContextDialog( MouseEvent e, IDvTable table ) {
    if ( table == null ) return;
    try {
      Point real = screen2real( e.x, e.y );
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map( canvas, null, e.x, e.y );
      String message = "Select action for table '" + table.getName() + "':";
      IGuiContextHandler contextHandler = new HopGuiVaultTableContext( model, this, table, real );
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection( parent, message, new Point( p.x, p.y ), contextHandler );
    } catch ( Exception ex ) {
      System.err.println( "Error showing table context dialog: " + ex.getMessage() );
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
    // Update tab label (text derived from name) and tooltip in explorer perspective, like Hop code does via perspective.updateTabItem
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
    // Exact same pattern as used in HopDataOrchestrationPerspective.updateTabLabel and ExplorerPerspective.updateTabItem + BaseExplorerFileTypeHandler.
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

  @Override
  public void selectAll() {
    // TODO
  }

  @Override
  public void unselectAll() {
    // TODO
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
    // Ask to save if changed
    if (hasChanged()) {
      // TODO: show dialog
      return true; // for basic
    }
    return true;
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
    redraw();
  }

  public void clearChanged() {
    this.changed = false;
    if (model != null) {
      model.clearChanged();
    }
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
    Object mag = stateProperties.get("magnification");
    if (mag != null) {
      magnification = ((Number) mag).floatValue();
    }
    Object ox = stateProperties.get("offsetX");
    Object oy = stateProperties.get("offsetY");
    if (ox != null && oy != null) {
      offset = new Point(((Number) ox).intValue(), ((Number) oy).intValue());
    }
    redraw();
    setZoomLabel();
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

  // Getters for model etc.
  public DataVaultModel getModel() {
    return model;
  }

  public void setModel(DataVaultModel model) {
    cancelRelationshipDrag();
    clearTableDragState();
    areaOwners.clear();
    mouseOverTableName = null;
    lastClick = null;
    lastButton = 0;
    this.model = model;
    redraw();
  }
}
