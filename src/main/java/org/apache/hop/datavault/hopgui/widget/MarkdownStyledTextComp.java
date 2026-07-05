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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.RenderedMarkdown;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.SpanKind;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.StyleSpan;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;

/** Read-only composite that renders markdown advice with SWT StyledText style ranges. */
public class MarkdownStyledTextComp extends Composite {

  private final StyledText styledText;
  private final Font heading1Font;
  private final Font heading2Font;
  private final Font heading3Font;
  private final Font boldFont;
  private final Font defaultFont;
  private final Font codeFont;
  private final Color heading1Color;
  private final Color heading2Color;
  private final Color heading3Color;
  private final Color codeForeground;
  private final Color codeBackground;
  private final Color codeBlockBackground;
  private final Color linkColor;

  public MarkdownStyledTextComp(Composite parent, int style) {
    super(parent, style);
    PropsUi.setLook(this);
    setLayout(new FormLayout());

    GuiResource resources = GuiResource.getInstance();
    heading1Font = resources.getFontLarge();
    heading2Font = resources.getFontMediumBold();
    heading3Font = resources.getFontBold();
    boldFont = resources.getFontBold();
    defaultFont = resources.getFontDefault();
    codeFont = resolveCodeFont(resources);
    heading1Color = MarkdownStylePalette.heading1(resources);
    heading2Color = MarkdownStylePalette.heading2(resources);
    heading3Color = MarkdownStylePalette.heading3(resources);
    codeForeground = MarkdownStylePalette.codeForeground(resources);
    codeBackground = MarkdownStylePalette.codeBackground(resources);
    codeBlockBackground = MarkdownStylePalette.codeBlockBackground(resources);
    linkColor = MarkdownStylePalette.link(resources);

    styledText =
        new StyledText(
            this, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
    PropsUi.setLook(styledText);
    styledText.setMargins(4, 4, 4, 4);
    FormData fdText = new FormData();
    fdText.left = new FormAttachment(0, 0);
    fdText.right = new FormAttachment(100, 0);
    fdText.top = new FormAttachment(0, 0);
    fdText.bottom = new FormAttachment(100, 0);
    styledText.setLayoutData(fdText);
  }

  public void setMarkdown(String markdown) {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);
    styledText.setText(rendered.displayText());
    styledText.setStyleRanges(toStyleRanges(rendered.spans()));
  }

  public int getPreferredHeight(int width) {
    if (width <= 0) {
      width = 400;
    }
    return Math.max(styledText.computeSize(width, SWT.DEFAULT).y + 8, styledText.getLineHeight());
  }

  private StyleRange[] toStyleRanges(List<StyleSpan> spans) {
    if (spans == null || spans.isEmpty()) {
      return new StyleRange[0];
    }
    List<StyleRange> ranges = new ArrayList<>(spans.size());
    for (StyleSpan span : spans) {
      if (span.length() <= 0) {
        continue;
      }
      StyleRange range = new StyleRange();
      range.start = span.start();
      range.length = span.length();
      applyKind(range, span.kind());
      ranges.add(range);
    }
    return ranges.toArray(StyleRange[]::new);
  }

  private void applyKind(StyleRange range, SpanKind kind) {
    switch (kind) {
      case HEADING_1 -> {
        range.font = heading1Font;
        range.fontStyle = SWT.BOLD;
        range.foreground = heading1Color;
      }
      case HEADING_2 -> {
        range.font = heading2Font;
        range.fontStyle = SWT.BOLD;
        range.foreground = heading2Color;
      }
      case HEADING_3 -> {
        range.font = heading3Font;
        range.fontStyle = SWT.BOLD;
        range.foreground = heading3Color;
      }
      case BOLD -> {
        range.font = boldFont;
        range.fontStyle = SWT.BOLD;
      }
      case ITALIC -> {
        range.font = defaultFont;
        range.fontStyle = SWT.ITALIC;
      }
      case CODE -> {
        range.font = codeFont;
        range.foreground = codeForeground;
        range.background = codeBackground;
      }
      case CODE_BLOCK -> {
        range.font = codeFont;
        range.foreground = codeForeground;
        range.background = codeBlockBackground;
      }
      case LINK -> {
        range.foreground = linkColor;
        range.underline = true;
        range.underlineStyle = SWT.UNDERLINE_SINGLE;
      }
      default -> {
        // no-op
      }
    }
  }

  private static Font resolveCodeFont(GuiResource resources) {
    Font fixed = resources.getFontFixed();
    if (fixed != null) {
      return fixed;
    }
    Font tiny = resources.getFontTiny();
    return tiny != null ? tiny : resources.getFontDefault();
  }

  public String getDisplayText() {
    return styledText.getText();
  }

  public void setPlainText(String text) {
    String value = text != null ? text : "";
    if (Utils.isEmpty(value)) {
      styledText.setText("");
      styledText.setStyleRanges(new StyleRange[0]);
      return;
    }
    setMarkdown(value);
  }
}