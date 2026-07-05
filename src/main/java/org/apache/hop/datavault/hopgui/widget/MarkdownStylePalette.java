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

package org.apache.hop.datavault.hopgui.widget;

import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.graphics.Color;

/** Grok Build-inspired markdown accent colors for StyledText rendering. */
public final class MarkdownStylePalette {

  // Headings: bright cyan → amber → soft violet (terminal markdown ladder)
  private static final int H1_RGB = 0x67E8F9; // cyan-300
  private static final int H2_RGB = 0xFCD34D; // amber-300
  private static final int H3_RGB = 0xA78BFA; // violet-400

  // Code: stylish blue foreground on deep navy background
  private static final int CODE_FOREGROUND_RGB = 0x79B8FF;
  private static final int CODE_BACKGROUND_RGB = 0x0F2744;
  private static final int CODE_BLOCK_BACKGROUND_RGB = 0x0B1F38;

  private MarkdownStylePalette() {}

  public static Color heading1(GuiResource resources) {
    return rgb(resources, H1_RGB);
  }

  public static Color heading2(GuiResource resources) {
    return rgb(resources, H2_RGB);
  }

  public static Color heading3(GuiResource resources) {
    return rgb(resources, H3_RGB);
  }

  public static Color codeForeground(GuiResource resources) {
    return rgb(resources, CODE_FOREGROUND_RGB);
  }

  public static Color codeBackground(GuiResource resources) {
    return rgb(resources, CODE_BACKGROUND_RGB);
  }

  public static Color codeBlockBackground(GuiResource resources) {
    return rgb(resources, CODE_BLOCK_BACKGROUND_RGB);
  }

  public static Color link(GuiResource resources) {
    Color hopBlue = resources.getColorLightBlue();
    return hopBlue != null ? hopBlue : rgb(resources, CODE_FOREGROUND_RGB);
  }

  private static Color rgb(GuiResource resources, int hex) {
    int r = (hex >> 16) & 0xFF;
    int g = (hex >> 8) & 0xFF;
    int b = hex & 0xFF;
    return resources.getColor(r, g, b);
  }
}