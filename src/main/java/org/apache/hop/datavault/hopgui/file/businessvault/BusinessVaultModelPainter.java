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

import java.util.HashMap;
import java.util.List;
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
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.ConnectionAnchors;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.apache.hop.datavault.metadata.businessvault.BvDerivativeRef;
import org.apache.hop.datavault.metadata.businessvault.BvDvTableReference;
import org.apache.hop.datavault.metadata.businessvault.BvTableBase;
import org.apache.hop.datavault.metadata.businessvault.BvTableType;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Paints a Business Vault model canvas with DV references, BV tables, and derivative links. */
@Getter
@Setter
public class BusinessVaultModelPainter extends BasePainter {

  private static final Class<?> PKG = BusinessVaultModelPainter.class;
  private static final int REF_LINE_HEIGHT = 16;
  private static final int DERIVATIVES_TOP = 50;
  private static final int DERIVATIVES_BOTTOM_PADDING = 12;
  private static final int DV_ICON_SIZE = 32;
  private static final int DV_MARGIN = 5;


  private final BusinessVaultModel model;
  private String mouseOverBvTableName;
  private String mouseOverDvReferenceName;
  private IBvTable startRelationshipBvTable;
  private BvDvTableReference startRelationshipDvReference;
  private Point relationshipDragEndLocation;
  private IBvTable candidateRelationshipBvTable;
  private BvDvTableReference candidateRelationshipDvReference;

  public BusinessVaultModelPainter(
      BusinessVaultModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
  }

  public void setRelationshipDragInfo(
      IBvTable startBvTable,
      BvDvTableReference startDvReference,
      Point relationshipDragEndLocation,
      IBvTable candidateBvTable,
      BvDvTableReference candidateDvReference) {
    this.startRelationshipBvTable = startBvTable;
    this.startRelationshipDvReference = startDvReference;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipBvTable = candidateBvTable;
    this.candidateRelationshipDvReference = candidateDvReference;
  }

  public void drawBusinessVaultModel(IHopMetadataProvider metadataProvider) {
    if (model == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }

    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    gc.setTransform((float) offset.x, (float) offset.y, magnification);
    gc.setAntialias(true);
    if (gridSize > 1) {
      drawGrid();
    }

    Map<String, BvDvTableReference> dvRefByName = indexDvReferences();
    prepareDvReferenceBoxSizes();
    drawDerivativeConnections(dvRefByName);
    drawRelationshipCandidateLine();
    drawNotes(model.getNotes());
    drawDvTableReferences();
    drawBusinessVaultTables();
    drawRect(selectionRegion);

    gc.setTransform(0.0f, 0.0f, 1.0f);

    boolean notesEmpty = !drawNotes || model.getNotes().isEmpty();
    if (model.getTables().isEmpty()
        && model.getDvReferences().isEmpty()
        && notesEmpty) {
      drawEmptyHint();
    }

    drawNavigationView();
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (model == null) {
      return;
    }
    int minSize = 4;
    Map<String, BvDvTableReference> dvRefByName = indexDvReferences();

    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(1);
    for (IBvTable bvTable : model.getTables()) {
      if (!(bvTable instanceof BvTableBase base)) {
        continue;
      }
      Point bvLoc = base.getLocation();
      if (bvLoc == null) {
        continue;
      }
      for (BvDerivativeRef derivative : base.getDerivatives()) {
        if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
          continue;
        }
        BvDvTableReference target = dvRefByName.get(derivative.getDvTableName());
        if (target == null || target.getLocation() == null) {
          continue;
        }
        Bounds bvBounds =
            navigationBounds(
                graphX,
                graphY,
                scaleX,
                scaleY,
                bvLoc,
                base.getDrawnBoxWidth(),
                base.getDrawnBoxHeight(),
                minSize);
        Bounds dvBounds =
            navigationBounds(
                graphX,
                graphY,
                scaleX,
                scaleY,
                target.getLocation(),
                target.getDrawnBoxWidth(),
                target.getDrawnBoxHeight(),
                minSize);
        ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(bvBounds, dvBounds);
        gc.drawLine(anchors.from().x, anchors.from().y, anchors.to().x, anchors.to().y);
      }
    }

    gc.setForeground(EColor.BLACK);
    for (BvDvTableReference reference : model.getDvReferences()) {
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      int w =
          Math.max(
              minSize,
              (int) Math.ceil(Math.max(1, reference.getDrawnBoxWidth()) * scaleX));
      int h =
          Math.max(
              minSize,
              (int) Math.ceil(Math.max(1, reference.getDrawnBoxHeight()) * scaleY));
      int x = (int) (graphX + loc.x * scaleX);
      int y = (int) (graphY + loc.y * scaleY);
      gc.setBackground(EColor.LIGHTGRAY);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }

    for (IBvTable table : model.getTables()) {
      if (!(table instanceof BvTableBase base) || base.getLocation() == null) {
        continue;
      }
      int w = Math.max(minSize, (int) Math.ceil(Math.max(1, base.getDrawnBoxWidth()) * scaleX));
      int h = Math.max(minSize, (int) Math.ceil(Math.max(1, base.getDrawnBoxHeight()) * scaleY));
      int x = (int) (graphX + base.getLocation().x * scaleX);
      int y = (int) (graphY + base.getLocation().y * scaleY);
      int[] color = tableTypeColor(base.getTableType());
      gc.setBackground(color[0], color[1], color[2]);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
    gc.setBackground(EColor.WHITE);
    gc.setLineWidth(1);
  }

  private Map<String, BvDvTableReference> indexDvReferences() {
    Map<String, BvDvTableReference> byName = new HashMap<>();
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference != null && !Utils.isEmpty(reference.getDvTableName())) {
        byName.put(reference.getDvTableName(), reference);
      }
    }
    return byName;
  }

  private void drawDerivativeConnections(Map<String, BvDvTableReference> dvRefByName) {
    gc.setLineWidth(1);
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.DARKGRAY);
    for (IBvTable bvTable : model.getTables()) {
      if (!(bvTable instanceof BvTableBase base)) {
        continue;
      }
      if (base.getLocation() == null) {
        continue;
      }
      Bounds bvBounds = getBvTableBounds(base);
      for (BvDerivativeRef derivative : base.getDerivatives()) {
        if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
          continue;
        }
        BvDvTableReference target = dvRefByName.get(derivative.getDvTableName());
        if (target == null || target.getLocation() == null) {
          continue;
        }
        Bounds dvBounds = getDvReferenceBounds(target);
        ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(bvBounds, dvBounds);
        gc.drawLine(anchors.from().x, anchors.from().y, anchors.to().x, anchors.to().y);
      }
    }
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.BLACK);
  }

  private void drawRelationshipCandidateLine() {
    if (relationshipDragEndLocation == null
        || (startRelationshipBvTable == null && startRelationshipDvReference == null)) {
      return;
    }
    Bounds sourceBounds = getRelationshipStartBounds();
    if (sourceBounds == null) {
      return;
    }
    Point logEnd = screenDragEndToLogical(relationshipDragEndLocation);
    if (logEnd == null) {
      return;
    }

    Point lineStart;
    Point lineEnd;
    if (isCandidateRelationshipValid()) {
      Bounds targetBounds = getCandidateTargetBounds();
      if (targetBounds != null) {
        ConnectionAnchors anchors =
            ModelGraphConnectionGeometry.anchorsBetween(sourceBounds, targetBounds);
        lineStart = anchors.from();
        lineEnd = anchors.to();
      } else {
        lineStart =
            ModelGraphConnectionGeometry.anchorToward(
                sourceBounds, ModelGraphConnectionGeometry.pointBounds(logEnd.x, logEnd.y));
        lineEnd = logEnd;
      }
    } else {
      lineStart =
          ModelGraphConnectionGeometry.anchorToward(
              sourceBounds, ModelGraphConnectionGeometry.pointBounds(logEnd.x, logEnd.y));
      lineEnd = logEnd;
    }

    boolean validTarget = isCandidateRelationshipValid();
    gc.setForeground(validTarget ? EColor.BLUE : EColor.DARKGRAY);
    gc.setLineWidth(2);
    gc.setLineStyle(ELineStyle.DASH);
    gc.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y);
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setLineWidth(1);
    gc.setForeground(EColor.BLACK);
  }

  private Bounds getRelationshipStartBounds() {
    if (startRelationshipBvTable instanceof BvTableBase bvTable) {
      return getBvTableBounds(bvTable);
    }
    if (startRelationshipDvReference != null) {
      return getDvReferenceBounds(startRelationshipDvReference);
    }
    return null;
  }

  private Bounds getCandidateTargetBounds() {
    if (candidateRelationshipBvTable instanceof BvTableBase bvTable) {
      return getBvTableBounds(bvTable);
    }
    if (candidateRelationshipDvReference != null) {
      return getDvReferenceBounds(candidateRelationshipDvReference);
    }
    return null;
  }

  private Bounds getBvTableBounds(BvTableBase table) {
    Point loc = table.getLocation();
    if (loc == null) {
      return null;
    }
    Point screenLoc = real2screen(loc.x, loc.y);
    return new Bounds(
        screenLoc.x, screenLoc.y, table.getDrawnBoxWidth(), table.getDrawnBoxHeight());
  }

  private Bounds getDvReferenceBounds(BvDvTableReference reference) {
    Point loc = reference.getLocation();
    if (loc == null) {
      return null;
    }
    Point screenLoc = real2screen(loc.x, loc.y);
    return new Bounds(
        screenLoc.x, screenLoc.y, reference.getDrawnBoxWidth(), reference.getDrawnBoxHeight());
  }

  private static Bounds navigationBounds(
      double graphX,
      double graphY,
      double scaleX,
      double scaleY,
      Point loc,
      int boxWidth,
      int boxHeight,
      int minSize) {
    int w = Math.max(minSize, (int) Math.ceil(Math.max(1, boxWidth) * scaleX));
    int h = Math.max(minSize, (int) Math.ceil(Math.max(1, boxHeight) * scaleY));
    int x = (int) (graphX + loc.x * scaleX);
    int y = (int) (graphY + loc.y * scaleY);
    return new Bounds(x, y, w, h);
  }

  private boolean isCandidateRelationshipValid() {
    if (startRelationshipBvTable != null) {
      return candidateRelationshipDvReference != null
          && candidateRelationshipDvReference != startRelationshipDvReference
          && BusinessVaultDerivativeSupport.canAddDerivative(
              startRelationshipBvTable, candidateRelationshipDvReference);
    }
    if (startRelationshipDvReference != null) {
      return candidateRelationshipBvTable != null
          && BusinessVaultDerivativeSupport.canAddDerivative(
              candidateRelationshipBvTable, startRelationshipDvReference);
    }
    return false;
  }

  private void prepareDvReferenceBoxSizes() {
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
        continue;
      }
      if (reference.getLocation() == null) {
        continue;
      }
      calculateDvReferenceBoxSize(reference);
    }
  }

  private Point calculateDvReferenceBoxSize(BvDvTableReference reference) {
    String name = reference.getDvTableName() != null ? reference.getDvTableName() : "?";
    String typeLabel =
        reference.getDvTableType() != null ? reference.getDvTableType().name() : "?";

    gc.setFont(EFont.GRAPH);
    Point nameExtent = gc.textExtent(name);
    gc.setFont(EFont.SMALL);
    Point typeExtent = gc.textExtent(typeLabel);
    gc.setFont(EFont.GRAPH);

    int iconP = DV_ICON_SIZE;
    int margP = DV_MARGIN;
    int textColumnWidth = nameExtent.x;
    int textColumnHeight = nameExtent.y;
    int leftColumnHeight = iconP + margP + typeExtent.y;
    int boxW = margP + iconP + margP + textColumnWidth + margP;
    int boxH = margP + Math.max(leftColumnHeight, textColumnHeight) + margP;

    reference.setDrawnBoxWidth(boxW);
    reference.setDrawnBoxHeight(boxH);
    return new Point(boxW, boxH);
  }

  private void drawDvTableReferences() {
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      Point box = calculateDvReferenceBoxSize(reference);
      int boxWidth = box.x;
      int boxHeight = box.y;
      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      gc.setBackground(EColor.LIGHTGRAY);
      gc.setForeground(EColor.DARKGRAY);
      gc.setLineStyle(ELineStyle.DOT);
      gc.setLineWidth(reference.isSelected() ? 2 : 1);
      gc.fillRoundRectangle(
          x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.drawRoundRectangle(
          x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setLineWidth(1);

      drawDvReferenceIcon(reference, x, y);

      gc.setFont(EFont.SMALL);
      gc.setForeground(EColor.DARKGRAY);
      String typeLabel =
          reference.getDvTableType() != null ? reference.getDvTableType().name() : "?";
      gc.drawText(typeLabel, x + DV_MARGIN, y + DV_MARGIN + DV_ICON_SIZE + 2, true);

      gc.setFont(EFont.GRAPH);
      gc.setForeground(EColor.BLACK);
      String name = reference.getDvTableName();
      boolean underline = name.equals(mouseOverDvReferenceName);
      int nameX = x + DV_MARGIN + DV_ICON_SIZE + DV_MARGIN;
      int nameY = y + DV_MARGIN;
      gc.drawText(name, nameX, nameY, true);
      if (underline) {
        Point extent = gc.textExtent(name);
        gc.drawLine(nameX, nameY + extent.y, nameX + extent.x, nameY + extent.y);
      }

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON,
                x,
                y,
                boxWidth,
                boxHeight,
                offset,
                reference,
                name));
        Point nameExtent = gc.textExtent(name);
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_NAME,
                nameX,
                nameY,
                Math.max(1, nameExtent.x),
                Math.max(1, nameExtent.y),
                offset,
                reference,
                name));
      }
    }
  }

  private void drawDvReferenceIcon(BvDvTableReference reference, int x, int y) {
    DvTableType tableType = reference.getDvTableType();
    if (tableType == null) {
      return;
    }
    try {
      String imagePath =
          switch (tableType) {
            case HUB -> "datavault_hub.svg";
            case LINK -> "datavault_link.svg";
            case SATELLITE -> "datavault_satellite.svg";
          };
      SvgFile svgFile = new SvgFile(imagePath, getClass().getClassLoader());
      gc.drawImage(svgFile, x + DV_MARGIN, y + DV_MARGIN, DV_ICON_SIZE, DV_ICON_SIZE, magnification, 0);
    } catch (Exception e) {
      gc.setBackground(EColor.GRAY);
      gc.fillRectangle(x + DV_MARGIN, y + DV_MARGIN, DV_ICON_SIZE, DV_ICON_SIZE);
    }
  }

  private void drawBusinessVaultTables() {
    for (IBvTable table : model.getTables()) {
      if (!(table instanceof BvTableBase base)) {
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

      String label = Const.NVL(base.getName(), base.getTableType().name());
      gc.setFont(EFont.GRAPH);
      gc.setForeground(EColor.BLACK);
      boolean underline = label.equals(mouseOverBvTableName);
      if (underline) {
        gc.setLineWidth(1);
        gc.drawText(label, x + 8, y + 8, true);
        Point extent = gc.textExtent(label);
        gc.drawLine(x + 8, y + 8 + extent.y, x + 8 + extent.x, y + 8 + extent.y);
      } else {
        gc.drawText(label, x + 8, y + 8, true);
      }

      gc.setFont(EFont.SMALL);
      gc.drawText(base.getTableType().name(), x + 8, y + 28, true);
      drawDerivativeReferences(base, x, y);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, table, label));
        Point nameExtent = gc.textExtent(label);
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_NAME,
                x + 8,
                y + 8,
                Math.max(1, nameExtent.x),
                Math.max(1, nameExtent.y),
                offset,
                table,
                label));
      }
    }
  }

  private void drawDerivativeReferences(BvTableBase base, int x, int y) {
    List<BvDerivativeRef> derivatives = base.getDerivatives();
    if (derivatives == null || derivatives.isEmpty()) {
      return;
    }
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    int lineHeight = derivativeLineHeight();
    int yRef = y + DERIVATIVES_TOP;
    for (BvDerivativeRef derivative : derivatives) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      String typeLabel =
          derivative.getDvTableType() != null ? derivative.getDvTableType().name() : "?";
      gc.drawText(typeLabel + ": " + derivative.getDvTableName(), x + 8, yRef, true);
      yRef += lineHeight;
    }
    gc.setForeground(EColor.BLACK);
  }

  private int computeBoxWidth(BvTableBase base) {
    int width = 120;
    gc.setFont(EFont.GRAPH);
    String label = Const.NVL(base.getName(), base.getTableType().name());
    width = Math.max(width, gc.textExtent(label).x + 16);
    gc.setFont(EFont.SMALL);
    width = Math.max(width, gc.textExtent(base.getTableType().name()).x + 16);
    for (BvDerivativeRef derivative : base.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      String typeLabel =
          derivative.getDvTableType() != null ? derivative.getDvTableType().name() : "?";
      width =
          Math.max(
              width, gc.textExtent(typeLabel + ": " + derivative.getDvTableName()).x + 16);
    }
    return width;
  }

  private int computeBoxHeight(BvTableBase base) {
    int refCount = 0;
    for (BvDerivativeRef derivative : base.getDerivatives()) {
      if (derivative != null && !Utils.isEmpty(derivative.getDvTableName())) {
        refCount++;
      }
    }
    int lineHeight = derivativeLineHeight();
    return Math.max(
        60, DERIVATIVES_TOP + refCount * lineHeight + DERIVATIVES_BOTTOM_PADDING);
  }

  private int derivativeLineHeight() {
    gc.setFont(EFont.SMALL);
    return Math.max(REF_LINE_HEIGHT, gc.textExtent("Ay").y + 4);
  }

  private int[] tableTypeColor(BvTableType tableType) {
    return switch (tableType) {
      case SCD2 -> new int[] {20, 90, 160};
      case PIT -> new int[] {120, 70, 20};
      case BUSINESS_TABLE -> new int[] {40, 120, 60};
    };
  }

  private void drawEmptyHint() {
    String hint = BaseMessages.getString(PKG, "BusinessVaultModelPainter.EmptyHint");
    if (Utils.isEmpty(model.getDataVaultModelPath())) {
      hint = BaseMessages.getString(PKG, "BusinessVaultModelPainter.EmptyHintMissingDvPath");
    }
    gc.setFont(EFont.GRAPH);
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(hint, 40, 40, true);
  }
}