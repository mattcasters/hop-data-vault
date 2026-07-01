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

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;

import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metadata.dimensional.DmBridge;
import org.apache.hop.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.IDmFactLikeTable;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Paints a dimensional model canvas with dimension/fact tables, relationships, and notes. */
@Getter
@Setter
public class DimensionalModelPainter extends BasePainter {

  private static final Class<?> PKG = DimensionalModelPainter.class;

  private final DimensionalModel model;
  private String mouseOverTableName;
  private Map<String, IDmTable> tableByName = new HashMap<>();
  private IDmTable startRelationshipTable;
  private Point relationshipDragEndLocation;
  private IDmTable candidateRelationshipTarget;

  public DimensionalModelPainter(
      DimensionalModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
  }

  public void setRelationshipDragInfo(
      IDmTable startRelationshipTable,
      Point relationshipDragEndLocation,
      IDmTable candidateRelationshipTarget) {
    this.startRelationshipTable = startRelationshipTable;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipTarget = candidateRelationshipTarget;
  }

  public void drawDimensionalModel(IHopMetadataProvider metadataProvider) {
    if (model == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }
    buildTableIndex();

    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    gc.setTransform((float) offset.x, (float) offset.y, magnification);
    gc.setAntialias(true);
    if (gridSize > 1) {
      drawGrid();
    }

    drawRelationshipLines();
    drawRelationshipCandidateLine();
    drawNotes(model.getNotes());
    drawTables();
    drawRect(selectionRegion);

    gc.setTransform(0.0f, 0.0f, 1.0f);

    boolean notesEmpty = !drawNotes || model.getNotes().isEmpty();
    if (model.getTables().isEmpty() && notesEmpty) {
      drawEmptyHint();
    }

    drawNavigationView();
  }

  private void buildTableIndex() {
    tableByName = new HashMap<>();
    if (model == null) {
      return;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        tableByName.put(table.getName(), table);
      }
    }
  }

  private void drawRelationshipLines() {
    gc.setForeground(EColor.BLACK);
    gc.setLineWidth(1);
    for (IDmTable table : model.getTables()) {
      if (table instanceof IDmFactLikeTable factLike) {
        drawFactDimensionConnections(factLike);
      }
      if (table instanceof DmBridge bridge) {
        drawBridgeDimensionConnections(bridge);
      }
      if (table instanceof DmDimension dimension) {
        drawOutriggerConnections(dimension);
      }
      if (table instanceof DmDimensionAlias alias) {
        drawAliasInheritanceConnection(alias);
      }
    }
  }

  private void drawAliasInheritanceConnection(DmDimensionAlias alias) {
    if (alias == null || Utils.isEmpty(alias.getReferencedDimensionName())) {
      return;
    }
    IDmTable referenced = tableByName.get(alias.getReferencedDimensionName());
    if (referenced == null || referenced.getLocation() == null || alias.getLocation() == null) {
      return;
    }
    Bounds aliasBounds = tableBounds(alias, alias.getLocation());
    Bounds referencedBounds = tableBounds(referenced, referenced.getLocation());
    gc.setLineStyle(ELineStyle.DOT);
    gc.setForeground(EColor.DARKGRAY);
    ModelGraphConnectionGeometry.drawConnectionSpline(gc, aliasBounds, referencedBounds);
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.BLACK);
  }

  private void drawFactDimensionConnections(IDmFactLikeTable fact) {
    Point factLocation = fact.getLocation();
    if (factLocation == null) {
      return;
    }
    Bounds factBounds = tableBounds(fact, factLocation);
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      IDmTable dimension = tableByName.get(role.getDimensionTableName());
      if (dimension == null || dimension.getLocation() == null) {
        continue;
      }
      Bounds dimensionBounds = tableBounds(dimension, dimension.getLocation());
      ModelGraphConnectionGeometry.drawConnectionSpline(gc, dimensionBounds, factBounds);
    }
  }

  private void drawBridgeDimensionConnections(DmBridge bridge) {
    Point bridgeLocation = bridge.getLocation();
    if (bridgeLocation == null) {
      return;
    }
    Bounds bridgeBounds = tableBounds(bridge, bridgeLocation);
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref == null || Utils.isEmpty(ref.getDimensionTableName())) {
        continue;
      }
      IDmTable dimension = tableByName.get(ref.getDimensionTableName());
      if (dimension == null || dimension.getLocation() == null) {
        continue;
      }
      Bounds dimensionBounds = tableBounds(dimension, dimension.getLocation());
      ModelGraphConnectionGeometry.drawConnectionSpline(gc, dimensionBounds, bridgeBounds);
    }
  }

  private void drawOutriggerConnections(DmDimension dimension) {
    Point dimensionLocation = dimension.getLocation();
    if (dimensionLocation == null) {
      return;
    }
    Bounds dimensionBounds = tableBounds(dimension, dimensionLocation);
    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      if (outrigger == null || Utils.isEmpty(outrigger.getDimensionTableName())) {
        continue;
      }
      IDmTable outriggerTable = tableByName.get(outrigger.getDimensionTableName());
      if (outriggerTable == null || outriggerTable.getLocation() == null) {
        continue;
      }
      Bounds outriggerBounds = tableBounds(outriggerTable, outriggerTable.getLocation());
      ModelGraphConnectionGeometry.drawConnectionSpline(gc, dimensionBounds, outriggerBounds);
    }
  }

  private void drawRelationshipCandidateLine() {
    if (startRelationshipTable == null || relationshipDragEndLocation == null) {
      return;
    }
    Bounds sourceBounds = tableBounds(startRelationshipTable, startRelationshipTable.getLocation());
    if (sourceBounds == null) {
      return;
    }
    Point logEnd = screenDragEndToLogical(relationshipDragEndLocation);
    if (logEnd == null) {
      return;
    }

    boolean validTarget = isValidRelationshipPair(startRelationshipTable, candidateRelationshipTarget);
    try {
      gc.setForeground(validTarget ? EColor.BLUE : EColor.DARKGRAY);
      gc.setLineWidth(2);
      gc.setLineStyle(ELineStyle.DASH);
      if (candidateRelationshipTarget != null
          && candidateRelationshipTarget.getLocation() != null
          && validTarget) {
        Bounds targetBounds =
            tableBounds(candidateRelationshipTarget, candidateRelationshipTarget.getLocation());
        ModelGraphConnectionGeometry.drawConnectionSpline(gc, sourceBounds, targetBounds);
      } else {
        Bounds cursorBounds = ModelGraphConnectionGeometry.pointBounds(logEnd.x, logEnd.y);
        Point lineStart =
            ModelGraphConnectionGeometry.anchorToward(sourceBounds, cursorBounds);
        ModelGraphConnectionGeometry.drawConnectionSpline(
            gc, lineStart, logEnd, sourceBounds, cursorBounds);
      }
      gc.setLineStyle(ELineStyle.SOLID);
      int marker = 4;
      gc.fillRoundRectangle(logEnd.x - marker, logEnd.y - marker, marker * 2, marker * 2, marker * 2, marker * 2);
    } finally {
      gc.setLineWidth(1);
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setForeground(EColor.BLACK);
    }
  }

  private static boolean isDimensionLike(IDmTable table) {
    return table instanceof DmDimension || table instanceof DmJunkDimension;
  }

  private static boolean isFactLikeTable(IDmTable table) {
    return table instanceof IDmFactLikeTable;
  }

  private static boolean isBridgeTable(IDmTable table) {
    return table instanceof DmBridge;
  }

  private static boolean isValidRelationshipPair(IDmTable a, IDmTable b) {
    if (a == null || b == null || a == b) {
      return false;
    }
    if (isFactLikeTable(a) && isDimensionLike(b)) {
      return true;
    }
    if (isFactLikeTable(b) && isDimensionLike(a)) {
      return true;
    }
    if (isBridgeTable(a) && isDimensionLike(b)) {
      return true;
    }
    if (isBridgeTable(b) && isDimensionLike(a)) {
      return true;
    }
    return a instanceof DmDimension && b instanceof DmDimension;
  }

  private Bounds tableBounds(IDmTable table, Point location) {
    if (location == null) {
      return null;
    }
    int boxWidth = 140;
    int boxHeight = 70;
    if (table instanceof DmTableBase base) {
      boxWidth = Math.max(140, base.getDrawnBoxWidth());
      boxHeight = Math.max(70, base.getDrawnBoxHeight());
    }
    Point screenLoc = real2screen(location.x, location.y);
    return new Bounds(screenLoc.x, screenLoc.y, boxWidth, boxHeight);
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (model == null) {
      return;
    }
    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(1);
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmTableBase base) || base.getLocation() == null) {
        continue;
      }
      int w = Math.max(4, (int) Math.round(Math.max(140, base.getDrawnBoxWidth()) * scaleX));
      int h = Math.max(4, (int) Math.round(Math.max(70, base.getDrawnBoxHeight()) * scaleY));
      int x = (int) Math.round(graphX + base.getLocation().x * scaleX);
      int y = (int) Math.round(graphY + base.getLocation().y * scaleY);
      gc.fillRectangle(x, y, w, h);
    }
  }

  private void drawTables() {
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmTableBase base)) {
        continue;
      }
      Point loc = base.getLocation();
      if (loc == null) {
        continue;
      }
      int boxWidth = computeBoxWidth(base);
      int boxHeight = computeBoxHeight(base);
      base.setDrawnBoxWidth(boxWidth);
      base.setDrawnBoxHeight(boxHeight);
      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;
      int[] color = tableTypeColor(base.getTableType());

      gc.setBackground(EColor.WHITE);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, 8, 8);
      gc.setLineWidth(base.isSelected() ? 2 : 1);
      gc.setForeground(color[0], color[1], color[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, 8, 8);
      gc.setLineWidth(1);

      String label = Const.NVL(base.getName(), tableTypeLabel(base.getTableType()));
      gc.setFont(EFont.GRAPH);
      gc.setForeground(EColor.BLACK);
      boolean underline = label.equals(mouseOverTableName);
      if (underline) {
        gc.drawText(label, x + 8, y + 8, true);
        Point extent = gc.textExtent(label);
        gc.drawLine(x + 8, y + 8 + extent.y, x + 8 + extent.x, y + 8 + extent.y);
      } else {
        gc.drawText(label, x + 8, y + 8, true);
      }

      drawTableSubtitle(base, x, y, color);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, table, label));
        Point nameExtent = gc.textExtent(label);
        ModelGraphTableNameHitArea.Bounds nameHit =
            ModelGraphTableNameHitArea.bounds(x + 8, y + 8, nameExtent);
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_NAME,
                nameHit.x(),
                nameHit.y(),
                nameHit.width(),
                nameHit.height(),
                offset,
                table,
                label));
      }
    }
  }

  private int computeBoxWidth(DmTableBase base) {
    String label = Const.NVL(base.getName(), tableTypeLabel(base.getTableType()));
    gc.setFont(EFont.GRAPH);
    int width = gc.textExtent(label).x + 16;
    if (base instanceof DmDimensionAlias alias) {
      gc.setFont(EFont.SMALL);
      String sourceModel =
          DmDimensionResolutionSupport.resolveAliasSourceModelDisplayName(model, alias, variables);
      if (!Utils.isEmpty(sourceModel)) {
        width = Math.max(width, gc.textExtent(sourceModel).x + 16);
      }
    }
    return Math.max(140, width);
  }

  private int computeBoxHeight(DmTableBase base) {
    return 70;
  }

  private void drawTableSubtitle(DmTableBase base, int x, int y, int[] borderColor) {
    gc.setFont(EFont.SMALL);
    if (base instanceof DmDimensionAlias alias) {
      String typeLabel = tableTypeLabel(DmTableType.DIMENSION_ALIAS);
      gc.setForeground(EColor.BLACK);
      gc.drawText(typeLabel, x + 8, y + 28, true);
      String sourceModel =
          DmDimensionResolutionSupport.resolveAliasSourceModelDisplayName(model, alias, variables);
      if (!Utils.isEmpty(sourceModel)) {
        Point typeExtent = gc.textExtent(typeLabel);
        gc.setForeground(borderColor[0], borderColor[1], borderColor[2]);
        gc.drawText(sourceModel, x + 8, y + 28 + typeExtent.y + 2, true);
        gc.setForeground(EColor.BLACK);
      }
      return;
    }
    gc.setForeground(EColor.BLACK);
    gc.drawText(tableSubtitle(base), x + 8, y + 28, true);
  }

  private static String tableTypeLabel(DmTableType tableType) {
    if (tableType == null) {
      return "";
    }
    return tableType.getDescription();
  }

  private static String tableSubtitle(DmTableBase base) {
    return tableTypeLabel(base.getTableType());
  }

  private static int[] tableTypeColor(DmTableType tableType) {
    return switch (tableType) {
      case FACT, PERIODIC_SNAPSHOT_FACT, ACCUMULATING_SNAPSHOT_FACT, AGGREGATE_FACT ->
          new int[] {180, 100, 40};
      case FACTLESS_FACT -> new int[] {200, 130, 60};
      case JUNK_DIMENSION -> new int[] {120, 90, 160};
      case BRIDGE -> new int[] {90, 150, 90};
      case DIMENSION -> new int[] {60, 120, 180};
      case DIMENSION_ALIAS -> new int[] {100, 140, 180};
    };
  }

  private void drawEmptyHint() {
    String hint = BaseMessages.getString(PKG, "DimensionalModelPainter.EmptyHint");
    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setFont(EFont.GRAPH);
    gc.setForeground(EColor.DARKGRAY);
    Point extent = gc.textExtent(hint);
    int x = Math.max(16, (area.x - extent.x) / 2);
    int y = Math.max(16, (area.y - extent.y) / 2);
    gc.drawText(hint, x, y, true);
  }
}