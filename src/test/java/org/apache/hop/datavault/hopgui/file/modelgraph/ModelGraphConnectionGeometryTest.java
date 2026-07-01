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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.config.DataVaultConfig;
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

  @Test
  void splinePolylineHasExpectedVertexCount() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, below);
    int segments = 12;
    int[] polyline =
        ModelGraphConnectionGeometry.splinePolyline(
            anchors.from(), anchors.to(), BOX_A, below, segments);
    assertEquals((segments + 1) * 2, polyline.length);
  }

  @Test
  void splinePolylineStartsAndEndsAtAnchors() {
    Bounds right = new Bounds(200, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, right);
    int[] polyline =
        ModelGraphConnectionGeometry.splinePolyline(
            anchors.from(), anchors.to(), BOX_A, right, 20);
    assertEquals(anchors.from().x, polyline[0]);
    assertEquals(anchors.from().y, polyline[1]);
    int last = polyline.length - 2;
    assertEquals(anchors.to().x, polyline[last]);
    assertEquals(anchors.to().y, polyline[last + 1]);
  }

  @Test
  void controlLengthClampsForShortAndLongConnections() {
    assertEquals(3.5, ModelGraphConnectionGeometry.controlLength(new Point(0, 0), new Point(10, 0)), 0.01);
    assertEquals(70, ModelGraphConnectionGeometry.controlLength(new Point(0, 0), new Point(200, 0)), 0.01);
    assertEquals(150, ModelGraphConnectionGeometry.controlLength(new Point(0, 0), new Point(500, 0)), 0.01);
  }

  @Test
  void effectiveSegmentCountScalesWithScreenLength() {
    assertEquals(20, ModelGraphConnectionGeometry.effectiveSegmentCount(120, 20));
    assertEquals(38, ModelGraphConnectionGeometry.effectiveSegmentCount(300, 20));
    assertEquals(38, ModelGraphConnectionGeometry.effectiveSegmentCount(300, 30));
    assertEquals(200, ModelGraphConnectionGeometry.effectiveSegmentCount(5000, 20));
  }

  @Test
  void splineLeavesBoxPerpendicularToBottomEdge() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, below);
    int[] polyline =
        ModelGraphConnectionGeometry.splinePolyline(
            anchors.from(), anchors.to(), BOX_A, below, 20);
    assertTrue(polyline[3] > polyline[1], "first segment should continue downward from bottom edge");
  }

  @Test
  void splineSegmentsReadsConfiguredValue() {
    DataVaultConfig config = new DataVaultConfig();
    config.setModelGraphSplineSegments(8);
    assertEquals(8, config.getModelGraphSplineSegments());
    config.setModelGraphSplineSegments(0);
    assertEquals(DataVaultConfig.DEFAULT_MODEL_GRAPH_SPLINE_SEGMENTS, config.getModelGraphSplineSegments());
  }
}