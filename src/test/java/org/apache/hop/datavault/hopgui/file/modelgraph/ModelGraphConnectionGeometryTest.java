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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.ConnectionAnchors;
import org.junit.jupiter.api.Test;

class ModelGraphConnectionGeometryTest {

  private static final Bounds BOX_A = new Bounds(0, 0, 100, 50);
  private static final Bounds BOX_WIDE = new Bounds(0, 0, 200, 40);

  @Test
  void anchorWhenTargetBelow() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, below);
    assertEquals(new Point(50, 50), anchors.from());
    assertEquals(new Point(50, 120), anchors.to());
  }

  @Test
  void anchorWhenTargetAbove() {
    Bounds above = new Bounds(10, -80, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, above);
    assertEquals(new Point(50, 0), anchors.from());
    assertEquals(new Point(50, -40), anchors.to());
  }

  @Test
  void anchorWhenTargetToTheRight() {
    Bounds right = new Bounds(200, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, right);
    assertEquals(new Point(100, 25), anchors.from());
    assertEquals(new Point(200, 25), anchors.to());
  }

  @Test
  void anchorWhenTargetToTheLeft() {
    Bounds left = new Bounds(-120, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, left);
    assertEquals(new Point(0, 25), anchors.from());
    assertEquals(new Point(-40, 25), anchors.to());
  }

  @Test
  void tieBreakPrefersVertical() {
    Bounds diagonal = new Bounds(100, 50, 80, 40);
    Point fromAnchor = ModelGraphConnectionGeometry.anchorToward(BOX_WIDE, diagonal);
    assertEquals(new Point(100, 40), fromAnchor);
  }

  @Test
  void overlappingBoxesRemainStable() {
    Bounds overlap = new Bounds(40, 10, 60, 30);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, overlap);
    assertEquals(new Point(50, 50), anchors.from());
    assertEquals(new Point(70, 10), anchors.to());
  }
}