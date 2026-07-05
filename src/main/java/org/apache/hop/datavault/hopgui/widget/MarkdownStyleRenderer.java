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
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/** Converts a practical markdown subset into display text and style spans for SWT StyledText. */
public final class MarkdownStyleRenderer {

  private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.*)$");
  private static final Pattern BULLET_PATTERN = Pattern.compile("^(\\s*)[-*]\\s+(.*)$");
  private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)$");
  private static final Pattern FENCE_PATTERN = Pattern.compile("^```(.*)$");
  private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
  private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)");
  private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*|__([^_]+)__");
  private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)");

  private MarkdownStyleRenderer() {}

  public enum SpanKind {
    BOLD,
    ITALIC,
    CODE,
    LINK,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    CODE_BLOCK
  }

  public record StyleSpan(int start, int length, SpanKind kind) {}

  public record RenderedMarkdown(String displayText, List<StyleSpan> spans) {}

  public static RenderedMarkdown render(String markdown) {
    if (Utils.isEmpty(markdown)) {
      return new RenderedMarkdown("", List.of());
    }
    try {
      return renderInternal(markdown);
    } catch (RuntimeException ex) {
      return new RenderedMarkdown(markdown, List.of());
    }
  }

  private static RenderedMarkdown renderInternal(String markdown) {
    String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    StringBuilder display = new StringBuilder();
    List<StyleSpan> spans = new ArrayList<>();
    boolean inCodeBlock = false;

    for (String line : lines) {
      Matcher fenceMatcher = FENCE_PATTERN.matcher(line);
      if (fenceMatcher.matches()) {
        inCodeBlock = !inCodeBlock;
        continue;
      }

      if (inCodeBlock) {
        appendLine(display, line);
        if (!line.isEmpty()) {
          int lineStart = display.length() - line.length();
          spans.add(new StyleSpan(lineStart, line.length(), SpanKind.CODE_BLOCK));
        }
        continue;
      }

      ParsedLine parsed = parseBlockLine(line);
      if (parsed.text().isEmpty()) {
        appendLine(display, "");
        continue;
      }
      int lineStart = display.length();
      if (display.length() > 0) {
        display.append('\n');
        lineStart = display.length();
      }
      display.append(parsed.text());
      for (StyleSpan span : parsed.spans()) {
        spans.add(
            new StyleSpan(lineStart + span.start(), span.length(), span.kind()));
      }
    }

    String displayText = display.toString();
    if (displayText.isEmpty()) {
      return new RenderedMarkdown(markdown, List.of());
    }
    return new RenderedMarkdown(displayText, List.copyOf(spans));
  }

  private static void appendLine(StringBuilder display, String line) {
    if (display.length() > 0) {
      display.append('\n');
    }
    display.append(line);
  }

  private record ParsedLine(String text, List<StyleSpan> spans) {}

  private static ParsedLine parseBlockLine(String line) {
    Matcher headingMatcher = HEADING_PATTERN.matcher(line);
    if (headingMatcher.matches()) {
      ParsedLine inline = parseInline(headingMatcher.group(2));
      List<StyleSpan> spans = new ArrayList<>(inline.spans());
      if (!inline.text().isEmpty()) {
        spans.add(
            new StyleSpan(
                0, inline.text().length(), headingKind(headingMatcher.group(1).length())));
      }
      return new ParsedLine(inline.text(), spans);
    }

    Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
    if (bulletMatcher.matches()) {
      String indent = bulletMatcher.group(1);
      ParsedLine inline = parseInline(bulletMatcher.group(2));
      String prefix = indent + "• ";
      return shiftSpans(prefix + inline.text(), inline.spans(), prefix.length());
    }

    Matcher numberedMatcher = NUMBERED_PATTERN.matcher(line);
    if (numberedMatcher.matches()) {
      String indent = numberedMatcher.group(1);
      String number = numberedMatcher.group(2);
      ParsedLine inline = parseInline(numberedMatcher.group(3));
      String prefix = indent + number + ". ";
      return shiftSpans(prefix + inline.text(), inline.spans(), prefix.length());
    }

    return parseInline(line);
  }

  private static ParsedLine shiftSpans(String text, List<StyleSpan> spans, int offset) {
    if (offset == 0) {
      return new ParsedLine(text, spans);
    }
    List<StyleSpan> shifted = new ArrayList<>(spans.size());
    for (StyleSpan span : spans) {
      shifted.add(new StyleSpan(span.start() + offset, span.length(), span.kind()));
    }
    return new ParsedLine(text, shifted);
  }

  private static ParsedLine parseInline(String input) {
    if (Utils.isEmpty(input)) {
      return new ParsedLine("", List.of());
    }
    StringBuilder display = new StringBuilder();
    List<StyleSpan> spans = new ArrayList<>();
    parseInlineSegment(input, display, spans);
    return new ParsedLine(display.toString(), spans);
  }

  private static void parseInlineSegment(String input, StringBuilder display, List<StyleSpan> spans) {
    if (Utils.isEmpty(input)) {
      return;
    }

    InlineMatch match = findEarliestInlineMatch(input);
    if (match == null) {
      display.append(input);
      return;
    }

    if (match.start() > 0) {
      display.append(input, 0, match.start());
    }

    int spanStart = display.length();
    display.append(match.content());
    if (!match.content().isEmpty()) {
      spans.add(new StyleSpan(spanStart, match.content().length(), match.kind()));
    }

    parseInlineSegment(input.substring(match.end()), display, spans);
  }

  private static InlineMatch findEarliestInlineMatch(String input) {
    List<InlineMatch> matches = new ArrayList<>();
    collectPatternMatch(input, INLINE_CODE_PATTERN, SpanKind.CODE, matches, 1);
    collectLinkMatches(input, matches);
    collectBoldMatches(input, matches);
    collectItalicMatches(input, matches);

    return matches.stream().min(Comparator.comparingInt(InlineMatch::start)).orElse(null);
  }

  private static void collectPatternMatch(
      String input,
      Pattern pattern,
      SpanKind kind,
      List<InlineMatch> matches,
      int contentGroup) {
    Matcher matcher = pattern.matcher(input);
    if (matcher.find()) {
      matches.add(
          new InlineMatch(
              matcher.start(),
              matcher.end(),
              matcher.group(contentGroup),
              kind));
    }
  }

  private static void collectLinkMatches(String input, List<InlineMatch> matches) {
    Matcher matcher = LINK_PATTERN.matcher(input);
    if (matcher.find()) {
      String label = matcher.group(1);
      matches.add(
          new InlineMatch(
              matcher.start(),
              matcher.end(),
              label != null ? label : "",
              SpanKind.LINK));
    }
  }

  private static void collectBoldMatches(String input, List<InlineMatch> matches) {
    Matcher matcher = BOLD_PATTERN.matcher(input);
    if (matcher.find()) {
      String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      matches.add(new InlineMatch(matcher.start(), matcher.end(), content, SpanKind.BOLD));
    }
  }

  private static void collectItalicMatches(String input, List<InlineMatch> matches) {
    Matcher matcher = ITALIC_PATTERN.matcher(input);
    if (matcher.find()) {
      String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      matches.add(new InlineMatch(matcher.start(), matcher.end(), content, SpanKind.ITALIC));
    }
  }

  private static SpanKind headingKind(int hashCount) {
    return switch (hashCount) {
      case 1 -> SpanKind.HEADING_1;
      case 2 -> SpanKind.HEADING_2;
      default -> SpanKind.HEADING_3;
    };
  }

  private record InlineMatch(int start, int end, String content, SpanKind kind) {}
}