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

package org.apache.hop.datavault.hopgui.file.modelgraph;

import org.apache.hop.core.gui.Point;

/** Edge-anchor helpers for table-to-table relationship lines in model graph painters. */
public final class ModelGraphConnectionGeometry {

  private ModelGraphConnectionGeometry() {}

  /** Axis-aligned table card bounds in the painter's draw coordinate space. */
  public record Bounds(int x, int y, int width, int height) {

    public Bounds {
      width = Math.max(1, width);
      height = Math.max(1, height);
    }

    public int centerX() {
      return x + width / 2;
    }

    public int centerY() {
      return y + height / 2;
    }
  }

  public record ConnectionAnchors(Point from, Point to) {}

  /**
   * Returns the midpoint on the side of {@code from} that faces {@code to}, using dominant-axis
   * routing (horizontal when |dx|*h dominates, otherwise vertical; ties prefer vertical).
   */
  public static Point anchorToward(Bounds from, Bounds to) {
    int dx = to.centerX() - from.centerX();
    int dy = to.centerY() - from.centerY();

    boolean horizontal =
        !rectanglesOverlap(from, to)
            && Math.abs(dx) * from.height() > Math.abs(dy) * from.width();
    if (horizontal) {
      if (dx > 0) {
        return rightMid(from);
      }
      return leftMid(from);
    }
    if (dy > 0) {
      return bottomMid(from);
    }
    if (dy < 0) {
      return topMid(from);
    }
    if (dx > 0) {
      return bottomMid(from);
    }
    if (dx < 0) {
      return topMid(from);
    }
    return bottomMid(from);
  }

  private static boolean rectanglesOverlap(Bounds a, Bounds b) {
    return a.x < b.x + b.width()
        && a.x + a.width() > b.x
        && a.y < b.y + b.height()
        && a.y + a.height() > b.y;
  }

  /** Returns edge anchors on each box facing the other. */
  public static ConnectionAnchors anchorsBetween(Bounds a, Bounds b) {
    return new ConnectionAnchors(anchorToward(a, b), anchorToward(b, a));
  }

  /** Degenerate bounds centered on a point (for drag lines toward the cursor). */
  public static Bounds pointBounds(int x, int y) {
    return new Bounds(x, y, 1, 1);
  }

  private static Point topMid(Bounds bounds) {
    return new Point(bounds.centerX(), bounds.y);
  }

  private static Point bottomMid(Bounds bounds) {
    return new Point(bounds.centerX(), bounds.y + bounds.height());
  }

  private static Point leftMid(Bounds bounds) {
    return new Point(bounds.x, bounds.centerY());
  }

  private static Point rightMid(Bounds bounds) {
    return new Point(bounds.x + bounds.width(), bounds.centerY());
  }
}