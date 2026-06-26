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

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Paints a dimensional model canvas with dimension/fact tables and notes (scaffold). */
@Getter
@Setter
public class DimensionalModelPainter extends BasePainter {

  private static final Class<?> PKG = DimensionalModelPainter.class;

  private final DimensionalModel model;
  private String mouseOverTableName;

  public DimensionalModelPainter(
      DimensionalModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
  }

  public void drawDimensionalModel(IHopMetadataProvider metadataProvider) {
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

    drawNotes(model.getNotes());
    drawTables();
    drawRect(selectionRegion);

    gc.setTransform(0.0f, 0.0f, 1.0f);

    if (model.getTables().isEmpty() && model.getNotes().isEmpty()) {
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

      String label = Const.NVL(base.getName(), base.getTableType().name());
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

      gc.setFont(EFont.SMALL);
      gc.drawText(base.getTableType().name(), x + 8, y + 28, true);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, table, label));
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

  private int computeBoxWidth(DmTableBase base) {
    String label = Const.NVL(base.getName(), base.getTableType().name());
    gc.setFont(EFont.GRAPH);
    Point extent = gc.textExtent(label);
    return Math.max(140, extent.x + 16);
  }

  private int computeBoxHeight(DmTableBase base) {
    return 70;
  }

  private static int[] tableTypeColor(DmTableType tableType) {
    if (tableType == DmTableType.FACT) {
      return new int[] {180, 100, 40};
    }
    return new int[] {60, 120, 180};
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