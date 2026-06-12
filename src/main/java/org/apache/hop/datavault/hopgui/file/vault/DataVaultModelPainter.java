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

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;

/**
 * Basic painter for a Data Vault model using IGc. Draws tables (hubs, links, satellites) at their
 * locations with icons and names. Draws simple lines from links to their participating hubs. Draws
 * simple lines from satellites to their parent hubs (or links). Supports mouse-over underline on
 * table names (for hyperlink style) and populates AreaOwners for name (TRANSFORM_NAME) vs body
 * (TRANSFORM_ICON) to support differentiated clicks. If a table has a non-empty description, draws
 * a small INFO_DISABLED icon at top-left and registers a TRANSFORM_INFO_ICON area (for tooltip on
 * hover). Optionally shows the resolved hash key field name in small print below the table name
 * ({@link #setShowHashKeyFieldNames(boolean)}).
 */
@Getter
@Setter
public class DataVaultModelPainter extends BasePainter {

  private final DataVaultModel model;
  private final Map<String, IDvTable> tableByName;

  // Transient drag state for drawing candidate relationship line (middle-btn or shift+left drag)
  // while creating hub<->satellite or hub<->link. These are set by the owning graph before each
  // draw.
  private IDvTable startRelationshipTable;
  private Point relationshipDragEndLocation; // screen coords from mouse
  private IDvTable candidateRelationshipTarget;

  /** When true, render the resolved hash key field name in small print below the table name. */
  private boolean showHashKeyFieldNames;

  private static final int ICON_SIZE = 32;
  private static final int HASH_KEY_LABEL_GAP = 2;
  private static final int MARGIN = 5;
  private static final int MINI_ICON_SIZE = 16;

  // Note: TABLE_* removed in favor of per-table dynamic sizes based on textExtent()

  public DataVaultModelPainter(
      DataVaultModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
    this.gc = gc;
    this.tableByName = new HashMap<>();

    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (table.getName() != null) {
          tableByName.put(table.getName(), table);
        }
      }
    }
  }

  /**
   * Set (or clear) the transient relationship drag state used to render a candidate line from a
   * source table center to the current mouse position (screen coords). Called by HopGuiVaultGraph
   * before each paint during a drag operation.
   */
  public void setRelationshipDragInfo(
      IDvTable startRelationshipTable,
      Point relationshipDragEndLocation,
      IDvTable candidateRelationshipTarget) {
    this.startRelationshipTable = startRelationshipTable;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipTarget = candidateRelationshipTarget;
  }

  public void drawDataVaultModel() {
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
    gc.fillRectangle(0, 0, area.x, area.y);

    // Set the appropriate transform before drawing the tables and relations
    // IMPORTANT: offset is not used in Hop 2.18 so we'll need to calculate manually
    //
    gc.setTransform((float) (offset.x), (float) (offset.y), magnification);

    drawDataVaultModelImage();
    drawRect(selectionRegion);

    // Draw the navigation view in native pixels to make calculation a bit easier.
    //
    gc.setTransform(0.0f, 0.0f, 1.0f);
    drawNavigationView();
  }

  public void drawDataVaultModelImage() {
    if (gridSize > 1) {
      drawGrid();
    }

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
      Point loc = real2screen(table.getLocation().x, table.getLocation().y);
      if (loc == null) {
        loc = new Point(50, 50);
      }
      drawTable(table, loc.x, loc.y);
    }
  }

  private void drawLinkConnections(DvLink link) {
    Point linkLocation = link.getLocation();
    if (linkLocation == null) return;
    Point linkLoc = real2screen(linkLocation.x, linkLocation.y);

    // Raw model coords; transform handles mag + pan
    Point linkBox = calculateTableBoxSize(link);
    int lx = linkLoc.x + linkBox.x / 2;
    int ly = linkLoc.y + linkBox.y / 2;

    for (String hubName : link.getHubNames()) {
      IDvTable hub = tableByName.get(hubName);
      if (hub != null) {
        Point hubLoc = hub.getLocation();
        if (hubLoc != null) {
          Point hLoc = real2screen(hubLoc.x, hubLoc.y);
          Point hubBox = calculateTableBoxSize(hub);
          int hx = hLoc.x + hubBox.x / 2;
          int hy = hLoc.y + hubBox.y / 2;
          gc.drawLine(hx, hy, lx, ly);
        }
      }
    }
  }

  private void drawSatelliteConnections(IDvTable sat) {
    Point satLocation = sat.getLocation();
    if (satLocation == null) return;
    Point satLoc = real2screen(satLocation.x, satLocation.y);

    // Raw model coords; transform handles mag + pan
    Point satBox = calculateTableBoxSize(sat);
    int sx = satLoc.x + satBox.x / 2;
    int sy = satLoc.y + satBox.y / 2;

    if (sat instanceof DvSatellite satellite) {
      // Prefer hub, as per standard DV2.0; fall back to link if no hub
      String parentName = satellite.getHubName();
      if (parentName == null || parentName.isEmpty()) {
        parentName = satellite.getLinkName();
      }
      if (parentName != null && !parentName.isEmpty()) {
        IDvTable parent = tableByName.get(parentName);
        if (parent != null) {
          Point pLocation = parent.getLocation();
          if (pLocation != null) {
            Point pLoc = real2screen(pLocation.x, pLocation.y);
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
    Point box = calculateTableBoxSize(table);

    // Draw rounded rectangle background
    drawTableBox(table, x, y, box);

    // Draw icon using SVG  top-left + margin
    drawTableIcon(table, x, y);

    // Type label below the icon, small print
    drawTableTypeLabel(table, x, y);

    // Name of the table to the right of the icon (and optional hash key field below it)
    Point nameExtent = drawTableName(table, x, y);
    if (showHashKeyFieldNames) {
      drawTableHashKeyFieldName(table, x, y, nameExtent);
    }

    // If the table has a description, draw a small info icon in the top-left corner.
    //
    drawTableDescriptionInfoIcon(table, x, y);
  }

  private Point drawTableName(IDvTable table, int x, int y) {
    gc.setFont(IGc.EFont.GRAPH);
    gc.setForeground(IGc.EColor.BLACK);
    String name = table.getName() != null ? table.getName() : "?";
    gc.drawText(name, x + MARGIN + ICON_SIZE + MARGIN, y + MARGIN, true);

    // Compute name position/extent for hover underline and area registration (name part for edit
    // click)
    int nameLogX = x + MARGIN + ICON_SIZE + MARGIN;
    int nameLogY = y + MARGIN;
    Point nameExtent = gc.textExtent(name);

    // Underline the name on mouse-over (hyperlink style, like transforms/actions in Hop GUI)
    if (name.equals(mouseOverTableName)) {
      gc.setLineWidth(1);
      gc.drawLine(
          nameLogX, nameLogY + nameExtent.y, nameLogX + nameExtent.x, nameLogY + nameExtent.y);
    }

    // Register AreaOwners for sub-hit detection (screen coords, using 0 offset so contains works
    // with screen mouse)
    if (areaOwners != null) {
      // Name sub-area (slightly padded) for underline hover + click-to-edit
      //
      int nsx = nameLogX;
      int nsy = nameLogY;
      int nsw = nameExtent.x;
      int nsh = nameExtent.y;
      // padding to make clickable
      nsx -= 1;
      nsy -= 1;
      nsw += 2;
      nsh += 2;
      areaOwners.add(
          new AreaOwner(AreaType.TRANSFORM_NAME, nsx, nsy, nsw, nsh, offset, table, name));
    }
    return nameExtent;
  }

  private void drawTableHashKeyFieldName(IDvTable table, int x, int y, Point nameExtent) {
    String hashKeyFieldName = getHashKeyFieldNameForDisplay(table);
    if (Utils.isEmpty(hashKeyFieldName)) {
      return;
    }

    int nameLogX = x + MARGIN + ICON_SIZE + MARGIN;
    int nameLogY = y + MARGIN + nameExtent.y + HASH_KEY_LABEL_GAP;

    gc.setFont(IGc.EFont.SMALL);
    gc.setForeground(IGc.EColor.DARKGRAY);
    gc.drawText(hashKeyFieldName, nameLogX, nameLogY, true);
  }

  private String getHashKeyFieldNameForDisplay(IDvTable table) {
    if (table == null) {
      return null;
    }

    String hashKeyFieldName = null;
    switch (table.getTableType()) {
      case HUB -> {
        DvHub hub = (DvHub) table;
        hashKeyFieldName = hub.getHashKeyFieldName();
        if (Utils.isEmpty(hashKeyFieldName) && !Utils.isEmpty(hub.getBusinessKeys())) {
          BusinessKey firstKey = hub.getBusinessKeys().get(0);
          if (firstKey != null && !Utils.isEmpty(firstKey.getName())) {
            hashKeyFieldName = firstKey.getName() + "_HK";
          }
        }
        if (Utils.isEmpty(hashKeyFieldName)) {
          hashKeyFieldName = "hashkey_HK";
        }
      }
      case LINK -> {
        DvLink link = (DvLink) table;
        hashKeyFieldName = link.getLinkHashKeyFieldName();
        if (Utils.isEmpty(hashKeyFieldName)) {
          hashKeyFieldName = link.getName() + "_LK";
        }
      }
      case SATELLITE -> {
        DvSatellite satellite = (DvSatellite) table;
        if (!Utils.isEmpty(satellite.getHubName())) {
          DvHub linkedHub = model.findHub(satellite.getHubName());
          if (linkedHub != null) {
            hashKeyFieldName = linkedHub.getHashKeyFieldName();
            if (Utils.isEmpty(hashKeyFieldName) && !Utils.isEmpty(linkedHub.getBusinessKeys())) {
              BusinessKey firstKey = linkedHub.getBusinessKeys().get(0);
              if (firstKey != null && !Utils.isEmpty(firstKey.getName())) {
                hashKeyFieldName = firstKey.getName() + "_HK";
              }
            }
          }
        } else if (!Utils.isEmpty(satellite.getLinkName())) {
          IDvTable linkedTable = tableByName.get(satellite.getLinkName());
          if (linkedTable instanceof DvLink linkedLink) {
            hashKeyFieldName = linkedLink.getLinkHashKeyFieldName();
            if (Utils.isEmpty(hashKeyFieldName)) {
              hashKeyFieldName = linkedLink.getName() + "_LK";
            }
          }
        }
        if (Utils.isEmpty(hashKeyFieldName)) {
          hashKeyFieldName = "hashkey";
        }
      }
      default -> {
        return null;
      }
    }

    return variables != null ? variables.resolve(hashKeyFieldName) : hashKeyFieldName;
  }

  private void drawTableTypeLabel(IDvTable table, int x, int y) {
    String typeLabel = getTypeLabel(table.getTableType());
    gc.setFont(IGc.EFont.SMALL);
    gc.setForeground(IGc.EColor.BLACK);
    gc.drawText(typeLabel, x + MARGIN, y + MARGIN + ICON_SIZE + MARGIN, true);
  }

  private void drawTableBox(IDvTable table, int x, int y, Point box) {
    gc.setBackground(IGc.EColor.WHITE);
    gc.setForeground(IGc.EColor.BLACK);
    gc.fillRoundRectangle(x, y, box.x, box.y, CORNER_RADIUS_5, CORNER_RADIUS_5);
    gc.setLineWidth(table.isSelected() ? 2 : 1);
    gc.drawRoundRectangle(x, y, box.x, box.y, CORNER_RADIUS_5, CORNER_RADIUS_5);
    gc.setLineWidth(1);

    // The TRANSFORM_ICON area type is used to mean the whole box including the icon and 2 texts.
    //
    areaOwners.add(
        new AreaOwner(AreaType.TRANSFORM_ICON, x, y, box.x, box.y, offset, table, table.getName()));
  }

  private void drawTableIcon(IDvTable table, int x, int y) {
    try {
      String imagePath = getImagePath(table.getTableType());
      ClassLoader classLoader = this.getClass().getClassLoader();
      SvgFile svgFile = new SvgFile(imagePath, classLoader);
      gc.drawImage(svgFile, x + MARGIN, y + MARGIN, ICON_SIZE, ICON_SIZE, magnification, 0);
    } catch (Exception e) {
      // Fallback: just draw a small rect if image fails
      gc.setBackground(IGc.EColor.BLUE);
      gc.fillRectangle(x + MARGIN, y + MARGIN, ICON_SIZE, ICON_SIZE);
    }
  }

  private void drawTableDescriptionInfoIcon(IDvTable table, int x, int y) {
    if (table == null || StringUtils.isEmpty(table.getDescription())) {
      return;
    }
    int xInfo = x - (MINI_ICON_SIZE / 2) - 1;
    int yInfo = y - (MINI_ICON_SIZE / 2) - 1;
    try {
      gc.drawImage(IGc.EImage.INFO_DISABLED, xInfo, yInfo, magnification);
    } catch (Exception e) {
      // optional decoration; ignore draw failure
    }
    areaOwners.add(
        new AreaOwner(
            AreaType.TRANSFORM_INFO_ICON,
            xInfo,
            yInfo,
            MINI_ICON_SIZE,
            MINI_ICON_SIZE,
            offset,
            table,
            table.getDescription()));
  }

  private String getImagePath(DvTableType type) {
    return switch (type) {
      case HUB -> "datavault_hub.svg";
      case LINK -> "datavault_link.svg";
      case SATELLITE -> "datavault_satellite.svg";
      default -> "datavault_model.svg";
    };
  }

  private String getTypeLabel(DvTableType type) {
    if (type == null) {
      return "";
    }
    return switch (type) {
      case HUB -> "Hub";
      case SATELLITE -> "Satellite";
      case LINK -> "Link";
      default -> type.name();
    };
  }

  /**
   * Compute the box size (in logical/model units) for the table card. Uses textExtent() for the
   * name width. Height based on icon + type label below. Also caches the size on the table for use
   * in hit tests etc. The transform will apply magnification and panning to these logical
   * sizes/positions.
   */
  private Point calculateTableBoxSize(IDvTable table) {
    String name = table.getName() != null ? table.getName() : "?";
    String typeLabel = getTypeLabel(table.getTableType());

    gc.setFont(IGc.EFont.GRAPH);
    Point nameExtent = gc.textExtent(name);
    gc.setFont(IGc.EFont.SMALL);
    Point typeExtent = gc.textExtent(typeLabel);
    Point hashKeyExtent = new Point(0, 0);
    if (showHashKeyFieldNames) {
      String hashKeyFieldName = getHashKeyFieldNameForDisplay(table);
      if (!Utils.isEmpty(hashKeyFieldName)) {
        hashKeyExtent = gc.textExtent(hashKeyFieldName);
      }
    }
    gc.setFont(IGc.EFont.GRAPH);

    int iconP = ICON_SIZE;
    int margP = MARGIN;
    int textColumnWidth = Math.max(nameExtent.x, hashKeyExtent.x);
    int textColumnHeight =
        nameExtent.y
            + (hashKeyExtent.y > 0 ? HASH_KEY_LABEL_GAP + hashKeyExtent.y : 0);
    int leftColumnHeight = iconP + margP + typeExtent.y;
    int boxW = margP + iconP + margP + textColumnWidth + margP;
    int boxH = margP + Math.max(leftColumnHeight, textColumnHeight) + margP;

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
   * Draw the temporary candidate relationship line (dashed) from the center of the source table to
   * the current drag end location (mouse). The end is provided in screen coords and converted to
   * logical here so the line draws correctly under the active gc transform (offset + mag). This is
   * invoked before drawing tables (after connections) so the line appears behind/under the table
   * visuals.
   */
  private void drawRelationshipCandidateLine() {
    if (startRelationshipTable == null || relationshipDragEndLocation == null) {
      return;
    }
    Point startLocation = startRelationshipTable.getLocation();
    Point loc = real2screen(startLocation.x, startLocation.y);
    if (loc == null) {
      return;
    }

    // Ensure box size is computed for the source (in case this is before its drawTable call this
    // frame)
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
              (int) (relationshipDragEndLocation.x - offset.x),
              (int) (relationshipDragEndLocation.y - offset.y));
    } else {
      logEnd =
          new Point(
              (int) ((relationshipDragEndLocation.x - offset.x) / magnification),
              (int) ((relationshipDragEndLocation.y - offset.y) / magnification));
    }

    // Save/restore styles like other drawing
    // (note: we don't change bg)
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

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (model == null || maximum == null) {
      return;
    }
    // Minimum size in viewport pixels so transforms remain visible
    int minSize = 4;
    // Draw hops as lines first (behind transforms)
    gc.setForeground(IGc.EColor.DARKGRAY);
    gc.setLineWidth(1);

    // Draw tables as small rectangles
    gc.setForeground(IGc.EColor.BLACK);
    for (IDvTable table : model.getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      int w = Math.max(minSize, (int) Math.ceil(iconSize * scaleX * 2));
      int h = Math.max(minSize, (int) Math.ceil(iconSize * scaleY));
      int x = (int) (graphX + loc.x * scaleX);
      int y = (int) (graphY + loc.y * scaleY);
      switch (table.getTableType()) {
        case HUB:
          gc.setBackground(IGc.EColor.GREEN);
          break;
        case SATELLITE:
          gc.setBackground(IGc.EColor.RED);
          break;
        case LINK:
          gc.setBackground(IGc.EColor.YELLOW);
          break;
      }
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
    gc.setBackground(IGc.EColor.WHITE);
  }
}
