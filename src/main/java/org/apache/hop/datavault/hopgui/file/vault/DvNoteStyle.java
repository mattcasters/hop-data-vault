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

  private static GuiResource resourcesOrNull() {
    try {
      return GuiResource.getInstance();
    } catch (Throwable ignored) {
      // Headless CLI (hop svg) and unit tests have no SWT GuiResource implementation.
      return null;
    }
  }

  public static RgbColor backgroundColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return switch (type) {
        case GENERAL -> fromColor(res.getColorDemoGray());
        case IMPORTANT -> fromColor(res.getColorYellow());
        case WARNING -> fromColor(res.getColorLightRed());
        case INFORMATION -> fromColor(res.getColorBlueCustomGrid());
      };
    }
    return switch (type) {
      case GENERAL -> new RgbColor(230, 230, 230);
      case IMPORTANT -> new RgbColor(255, 220, 100);
      case WARNING -> new RgbColor(255, 200, 200);
      case INFORMATION -> new RgbColor(180, 210, 255);
    };
  }

  public static RgbColor borderColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return switch (type) {
        case GENERAL -> fromColor(res.getColorDarkGray());
        case IMPORTANT, INFORMATION -> fromColor(res.getColorWhite());
        case WARNING -> fromColor(res.getColorRed());
      };
    }
    return switch (type) {
      case GENERAL -> new RgbColor(80, 80, 80);
      case IMPORTANT, INFORMATION -> new RgbColor(255, 255, 255);
      case WARNING -> new RgbColor(200, 0, 0);
    };
  }

  public static RgbColor textColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return switch (type) {
        case IMPORTANT, INFORMATION -> fromColor(res.getColorWhite());
        default -> fromColor(res.getColorBlack());
      };
    }
    return switch (type) {
      case IMPORTANT, INFORMATION -> new RgbColor(255, 255, 255);
      default -> new RgbColor(0, 0, 0);
    };
  }

  /** Hyperlink foreground; contrasts with each note type background. */
  public static RgbColor linkColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return switch (type) {
        case IMPORTANT, INFORMATION -> fromColor(res.getColorWhite());
        default -> fromColor(res.getColorBlack());
      };
    }
    return switch (type) {
      case IMPORTANT, INFORMATION -> new RgbColor(255, 255, 255);
      default -> new RgbColor(0, 0, 0);
    };
  }

  public static int borderWidth(DvNoteType type, boolean selected) {
    int base =
        switch (type == null ? DvNoteType.GENERAL : type) {
          case WARNING, IMPORTANT -> 2;
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
      case WARNING -> IGc.EImage.ERROR;
      case INFORMATION -> IGc.EImage.INFO_DISABLED;
      default -> null;
    };
  }
}