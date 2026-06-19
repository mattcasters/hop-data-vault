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

import org.apache.hop.core.gui.IGc;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.graphics.Color;

/**
 * Fixed visual styles for {@link org.apache.hop.datavault.metadata.DvNote} types. Centralizes
 * colors and icons so the painter and dialog preview stay consistent. Colors come from {@link
 * GuiResource} so they respect Hop dark-mode contrast settings.
 */
public final class DvNoteStyle {

  private DvNoteStyle() {}

  public record RgbColor(int red, int green, int blue) {}

  private static RgbColor fromColor(Color color) {
    return new RgbColor(color.getRed(), color.getGreen(), color.getBlue());
  }

  private static GuiResource resources() {
    return GuiResource.getInstance();
  }

  public static RgbColor backgroundColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resources();
    return switch (type) {
      case GENERAL -> fromColor(res.getColorDemoGray());
      case IMPORTANT -> fromColor(res.getColorYellow());
      case DANGEROUS -> fromColor(res.getColorLightRed());
      case INFORMATION -> fromColor(res.getColorBlueCustomGrid());
    };
  }

  public static RgbColor borderColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resources();
    return switch (type) {
      case GENERAL -> fromColor(res.getColorDarkGray());
      case IMPORTANT, INFORMATION -> fromColor(res.getColorWhite());
      case DANGEROUS -> fromColor(res.getColorRed());
    };
  }

  public static RgbColor textColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resources();
    return switch (type) {
      case IMPORTANT, INFORMATION -> fromColor(res.getColorWhite());
      default -> fromColor(res.getColorBlack());
    };
  }

  /** Hyperlink foreground; contrasts with each note type background. */
  public static RgbColor linkColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resources();
    return switch (type) {
      case IMPORTANT, INFORMATION -> fromColor(res.getColorWhite());
      default -> fromColor(res.getColorBlack());
    };
  }

  public static int borderWidth(DvNoteType type, boolean selected) {
    int base =
        switch (type == null ? DvNoteType.GENERAL : type) {
          case DANGEROUS, IMPORTANT -> 2;
          default -> 1;
        };
    return selected ? base + 1 : base;
  }

  /** Optional accent icon for the note type; {@code null} means no icon. */
  public static IGc.EImage icon(DvNoteType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case IMPORTANT -> IGc.EImage.INFO;
      case DANGEROUS -> IGc.EImage.ERROR;
      case INFORMATION -> IGc.EImage.INFO_DISABLED;
      default -> null;
    };
  }
}