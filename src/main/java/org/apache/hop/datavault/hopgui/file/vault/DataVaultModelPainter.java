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

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic painter for a Data Vault model using IGc.
 * Draws tables (hubs, links, satellites) at their locations with icons and names.
 * Draws simple lines from links to their participating hubs.
 * Draws simple lines from satellites to their parent hubs (or links).
 * Supports mouse-over underline on table names (for hyperlink style) and populates AreaOwners
 * for name (TRANSFORM_NAME) vs body (TRANSFORM_ICON) to support differentiated clicks.
 * If a table has a non-empty description, draws a small INFO_DISABLED icon at top-left and
 * registers a TRANSFORM_INFO_ICON area (for tooltip on hover).
 */
public class DataVaultModelPainter {

  private final DataVaultModel model;
  private final IGc gc;
  private final int width;
  private final int height;
  private final float magnification;
  private final Point offset;
  private final Map<String, IDvTable> tableByName;
  private final List<AreaOwner> areaOwners;
  private final String mouseOverTableName;

  // Transient drag state for drawing candidate relationship line (middle-btn or shift+left drag)
  // while creating hub<->satellite or hub<->link. These are set by the owning graph before each draw.
  private IDvTable startRelationshipTable;
  private Point relationshipDragEndLocation; // screen coords from mouse
  private IDvTable candidateRelationshipTarget;

  private static final int ICON_SIZE = 32;
  private static final int MARGIN = 5;
  private static final int MINI_ICON_SIZE = 16;
  // Note: TABLE_* removed in favor of per-table dynamic sizes based on textExtent()

  public DataVaultModelPainter(
      DataVaultModel model,
      IGc gc,
      int width,
      int height,
      float magnification,
      Point offset,
      List<AreaOwner> areaOwners,
      String mouseOverTableName) {
    this.model = model;
    this.gc = gc;
    this.width = width;
    this.height = height;
    this.magnification = magnification;
    this.offset = offset != null ? offset : new Point(0, 0);
    this.tableByName = new HashMap<>();
    this.areaOwners = areaOwners != null ? areaOwners : new ArrayList<>();
    this.mouseOverTableName = mouseOverTableName;

    this.gc.setAntialias(true);

    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (table.getName() != null) {
          tableByName.put(table.getName(), table);
        }
      }
    }
  }

  /**
   * Set (or clear) the transient relationship drag state used to render a candidate line
   * from a source table center to the current mouse position (screen coords). Called by
   * HopGuiVaultGraph before each paint during a drag operation.
   */
  public void setRelationshipDragInfo(
      IDvTable startRelationshipTable,
      Point relationshipDragEndLocation,
      IDvTable candidateRelationshipTarget) {
    this.startRelationshipTable = startRelationshipTable;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipTarget = candidateRelationshipTarget;
  }

  public void draw() {
    if (model == null || model.getTables() == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }
    // reset the transform before clearing the background
    gc.setTransform(0.0f, 0.0f, 1.0f);

    // Simple light gray background
    gc.setBackground(IGc.EColor.BACKGROUND);
    gc.fillRectangle(0, 0, width, height);

    // set the appropriate transform before drawing the tables and relations
    gc.setTransform(offset.x, offset.y, magnification);

    // Draw connections first (behind tables)
    gc.setForeground(IGc.EColor.BLACK);
    gc.setLineWidth(1);
    for (IDvTable table : model.getTables()) {
      if (table.getTableType() == DvTableType.LINK) {
        drawLinkConnections((DvLink) table);
      }
      if (table.getTableType() == DvTableType.SATELLITE) {
        drawSatelliteConnections(table);
      }
    }

    // Draw the (temporary) candidate relationship line (if dragging), before tables so it renders
    // underneath the table cards, just like permanent connections. Uses logical coords under the
    // active transform.
    drawRelationshipCandidateLine();

    // Draw the tables
    for (IDvTable table : model.getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        loc = new Point(50, 50);
      }
      // Use raw model coordinates; the gc.setTransform() handles magnification and panning (offset)
      drawTable(table, loc.x, loc.y);
    }
  }

  private void drawLinkConnections(DvLink link) {
    Point linkLoc = link.getLocation();
    if (linkLoc == null) return;

    // Raw model coords; transform handles mag + pan
    Point linkBox = calculateTableBoxSize(link);
    int lx = linkLoc.x + linkBox.x / 2;
    int ly = linkLoc.y + linkBox.y / 2;

    for (String hubName : link.getHubNames()) {
      IDvTable hub = tableByName.get(hubName);
      if (hub != null) {
        Point hloc = hub.getLocation();
        if (hloc != null) {
          Point hubBox = calculateTableBoxSize(hub);
          int hx = hloc.x + hubBox.x / 2;
          int hy = hloc.y + hubBox.y / 2;
          gc.drawLine(hx, hy, lx, ly);
        }
      }
    }
  }

  private void drawSatelliteConnections(IDvTable sat) {
    Point satLoc = sat.getLocation();
    if (satLoc == null) return;

    // Raw model coords; transform handles mag + pan
    Point satBox = calculateTableBoxSize(sat);
    int sx = satLoc.x + satBox.x / 2;
    int sy = satLoc.y + satBox.y / 2;

    if (sat instanceof DvSatellite) {
      DvSatellite satellite = (DvSatellite) sat;
      // Prefer hub, as per standard DV2.0; fall back to link if no hub
      String parentName = satellite.getHubName();
      if (parentName == null || parentName.isEmpty()) {
        parentName = satellite.getLinkName();
      }
      if (parentName != null && !parentName.isEmpty()) {
        IDvTable parent = tableByName.get(parentName);
        if (parent != null) {
          Point pLoc = parent.getLocation();
          if (pLoc != null) {
            Point pBox = calculateTableBoxSize(parent);
            int px = pLoc.x + pBox.x / 2;
            int py = pLoc.y + pBox.y / 2;
            gc.drawLine(px, py, sx, sy);
          }
        }
      }
    }
  }

  private void drawTable(IDvTable table, int x, int y) {
    // x, y are raw model coordinates; gc.setTransform handles offset + magnification
    Point box = calculateTableBoxSize(table);
    int w = box.x;
    int h = box.y;

    // Draw rounded rectangle background
    gc.setBackground(IGc.EColor.WHITE);
    gc.setForeground(IGc.EColor.BLACK);
    gc.fillRectangle(x, y, w, h);
    gc.setLineWidth( table.isSelected() ? 2 : 1 );
    gc.drawRectangle(x, y, w, h);
    gc.setLineWidth(1);

    // Draw icon using SVG  top-left + margin
    try {
      String imagePath = getImagePath(table.getTableType());
      if (imagePath != null) {
        ClassLoader classLoader = this.getClass().getClassLoader();
        SvgFile svgFile = new SvgFile(imagePath, classLoader);
        gc.drawImage(svgFile, x + MARGIN, y + MARGIN, ICON_SIZE, ICON_SIZE, magnification, 0);
      }
    } catch (Exception e) {
      // Fallback: just draw a small rect if image fails
      gc.setBackground(IGc.EColor.BLUE);
      gc.fillRectangle(x + MARGIN, y + MARGIN, ICON_SIZE, ICON_SIZE);
    }

    // Type label below the icon, small print
    String typeLabel = getTypeLabel(table.getTableType());
    gc.setFont(IGc.EFont.SMALL);
    gc.setForeground(IGc.EColor.BLACK);
    gc.drawText(typeLabel, x + MARGIN, y + MARGIN + ICON_SIZE + MARGIN, true);

    // Name of the table to the right of the icon
    gc.setFont(IGc.EFont.GRAPH);
    gc.setForeground(IGc.EColor.BLACK);
    String name = table.getName() != null ? table.getName() : "?";
    gc.drawText(name, x + MARGIN + ICON_SIZE + MARGIN, y + MARGIN, true);

    // Compute name position/extent for hover underline and area registration (name part for edit click)
    int nameLogX = x + MARGIN + ICON_SIZE + MARGIN;
    int nameLogY = y + MARGIN;
    Point nameExtent = gc.textExtent(name);

    // Underline the name on mouse-over (hyperlink style, like transforms/actions in Hop GUI)
    if (name.equals(mouseOverTableName)) {
      gc.setLineWidth(1);
      gc.drawLine(
          nameLogX, nameLogY + nameExtent.y, nameLogX + nameExtent.x, nameLogY + nameExtent.y);
    }

    // Register AreaOwners for sub-hit detection (screen coords, using 0 offset so contains works with screen mouse)
    if (areaOwners != null) {
      // Whole table body/icon for context menu
      int sx = (int) (x * magnification) + offset.x;
      int sy = (int) (y * magnification) + offset.y;
      int sw = (int) (box.x * magnification);
      int sh = (int) (box.y * magnification);
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_ICON,
              sx,
              sy,
              sw,
              sh,
              new DPoint(0, 0),
              null,
              table));

      // Name sub-area (slightly padded) for underline hover + click-to-edit
      int nsx = (int) (nameLogX * magnification) + offset.x;
      int nsy = (int) (nameLogY * magnification) + offset.y;
      int nsw = (int) (nameExtent.x * magnification);
      int nsh = (int) (nameExtent.y * magnification);
      // padding to make clickable
      nsx -= 2;
      nsy -= 1;
      nsw += 4;
      nsh += 2;
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_NAME, nsx, nsy, nsw, nsh, new DPoint(0, 0), table, name));
    }

    // If the table has a description, draw a small info icon at the top-left corner (slightly
    // protruding) and register a TRANSFORM_INFO_ICON area owner (screen coords) so that mouse-over
    // can show the description as a tooltip (modeled after PipelinePainter.drawTransformInformationIndicator).
    if (!Utils.isEmpty(table.getDescription())) {
      int xInfo = x - (MINI_ICON_SIZE / 2) - 1;
      int yInfo = y - (MINI_ICON_SIZE / 2) - 1;
      try {
        gc.drawImage(IGc.EImage.INFO_DISABLED, xInfo, yInfo, magnification);
      } catch (Exception e) {
        // optional decoration; ignore draw failure
      }
      if (areaOwners != null) {
        int isx = (int) (xInfo * magnification) + offset.x;
        int isy = (int) (yInfo * magnification) + offset.y;
        int isw = (int) (MINI_ICON_SIZE * magnification);
        int ish = (int) (MINI_ICON_SIZE * magnification);
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_INFO_ICON,
                isx,
                isy,
                isw,
                ish,
                new DPoint(0, 0),
                null,
                table));
      }
    }

    gc.setFont(IGc.EFont.GRAPH); // reset?
  }

  private String getImagePath(DvTableType type) {
    switch (type) {
      case HUB:
        return "datavault_hub.svg";
      case LINK:
        return "datavault_link.svg";
      case SATELLITE:
        return "datavault_satellite.svg";
      default:
        return "datavault_model.svg";
    }
  }

  private String getTypeLabel(DvTableType type) {
    if (type == null) {
      return "";
    }
    switch (type) {
      case HUB:
        return "Hub";
      case SATELLITE:
        return "Satellite";
      case LINK:
        return "Link";
      default:
        return type.name();
    }
  }

  /**
   * Compute the box size (in logical/model units) for the table card.
   * Uses textExtent() for the name width. Height based on icon + type label below.
   * Also caches the size on the table for use in hit tests etc.
   * The gc.setTransform() will apply magnification and panning to these logical sizes/positions.
   */
  private Point calculateTableBoxSize(IDvTable table) {
    String name = table.getName() != null ? table.getName() : "?";
    String typeLabel = getTypeLabel(table.getTableType());

    gc.setFont(IGc.EFont.GRAPH);
    Point nameExtent = gc.textExtent(name);
    gc.setFont(IGc.EFont.SMALL);
    Point typeExtent = gc.textExtent(typeLabel);
    gc.setFont(IGc.EFont.GRAPH);

    int iconP = ICON_SIZE;
    int margP = MARGIN;
    int boxW = margP + iconP + margP + nameExtent.x + margP;
    int boxH = margP + iconP + margP + typeExtent.y + margP;

    if (table instanceof DvTableBase base) {
      base.setDrawnBoxWidth(boxW);
      base.setDrawnBoxHeight(boxH);
    }
    return new Point(boxW, boxH);
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
   * Draw the temporary candidate relationship line (dashed) from the center of the source table
   * to the current drag end location (mouse). The end is provided in screen coords and converted
   * to logical here so the line draws correctly under the active gc transform (offset + mag).
   * This is invoked before drawing tables (after connections) so the line appears behind/under
   * the table visuals.
   */
  private void drawRelationshipCandidateLine() {
    if (startRelationshipTable == null || relationshipDragEndLocation == null) {
      return;
    }
    Point loc = startRelationshipTable.getLocation();
    if (loc == null) {
      return;
    }

    // Ensure box size is computed for the source (in case this is before its drawTable call this frame)
    Point box = calculateTableBoxSize(startRelationshipTable);
    int tw = box.x;
    int th = box.y;

    // Start center in LOGICAL coords
    int cx = loc.x + tw / 2;
    int cy = loc.y + th / 2;

    // Convert SCREEN drag end to LOGICAL coords (so drawLine uses consistent units under transform)
    Point logEnd;
    if (magnification <= 0) {
      logEnd =
          new Point(
              relationshipDragEndLocation.x - offset.x, relationshipDragEndLocation.y - offset.y);
    } else {
      logEnd =
          new Point(
              (int) ((relationshipDragEndLocation.x - offset.x) / magnification),
              (int) ((relationshipDragEndLocation.y - offset.y) / magnification));
    }

    // Save/restore styles like other drawing
    // (note: we don't change bg)
    int oldWidth = 1; // default
    IGc.ELineStyle oldStyle = IGc.ELineStyle.SOLID;
    // We can't easily query current, so just set and reset to safe values at end
    try {
      boolean validTarget =
          isValidRelationshipPair(startRelationshipTable, candidateRelationshipTarget);
      gc.setForeground(validTarget ? IGc.EColor.BLUE : IGc.EColor.DARKGRAY);
      gc.setLineWidth(2);
      gc.setLineStyle(IGc.ELineStyle.DASH);
      gc.drawLine(cx, cy, logEnd.x, logEnd.y);

      // Small endpoint marker (approx circle using round rect)
      gc.setLineStyle(IGc.ELineStyle.SOLID);
      int m = 4;
      gc.fillRoundRectangle(logEnd.x - m, logEnd.y - m, m * 2, m * 2, m * 2, m * 2);
    } finally {
      gc.setLineWidth(1);
      gc.setLineStyle(IGc.ELineStyle.SOLID);
      gc.setForeground(IGc.EColor.BLACK);
    }
  }
}
