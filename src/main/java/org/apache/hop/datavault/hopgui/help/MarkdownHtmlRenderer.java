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

package org.apache.hop.datavault.hopgui.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/** Converts the same markdown subset as {@link org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer} to HTML. */
public final class MarkdownHtmlRenderer {

  private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.*)$");
  private static final Pattern BULLET_PATTERN = Pattern.compile("^(\\s*)[-*]\\s+(.*)$");
  private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)$");
  private static final Pattern FENCE_PATTERN = Pattern.compile("^```(.*)$");
  private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
  private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)");
  private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*|__([^_]+)__");
  private static final Pattern ITALIC_PATTERN =
      Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)");

  private MarkdownHtmlRenderer() {}

  public static String toHtmlDocument(String title, String markdown) {
    String safeTitle = Utils.isEmpty(title) ? "Help" : title;
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>%s</title>
        <style>
        body { font-family: system-ui, -apple-system, sans-serif; line-height: 1.5; max-width: 48rem; margin: 2rem auto; padding: 0 1rem; color: #24292f; }
        h1 { font-size: 1.5rem; margin-top: 1.5rem; }
        h2 { font-size: 1.25rem; margin-top: 1.25rem; }
        h3 { font-size: 1.1rem; margin-top: 1rem; }
        p { margin: 0.75rem 0; }
        ul, ol { margin: 0.75rem 0; padding-left: 1.5rem; }
        code { font-family: ui-monospace, monospace; background: #f6f8fa; padding: 0.1em 0.3em; border-radius: 3px; }
        pre { background: #f6f8fa; padding: 1rem; overflow-x: auto; border-radius: 4px; }
        pre code { background: none; padding: 0; }
        a { color: #0969da; }
        </style>
        </head>
        <body>
        %s
        </body>
        </html>
        """
        .formatted(escapeHtml(safeTitle), toHtmlBody(markdown));
  }

  static String toHtmlBody(String markdown) {
    if (Utils.isEmpty(markdown)) {
      return "";
    }
    String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    StringBuilder html = new StringBuilder();
    boolean inCodeBlock = false;
    StringBuilder codeBlock = new StringBuilder();
    ListState listState = ListState.NONE;

    for (String line : lines) {
      Matcher fenceMatcher = FENCE_PATTERN.matcher(line);
      if (fenceMatcher.matches()) {
        if (inCodeBlock) {
          html.append("<pre><code>").append(escapeHtml(codeBlock.toString())).append("</code></pre>\n");
          codeBlock.setLength(0);
        }
        inCodeBlock = !inCodeBlock;
        listState = closeList(html, listState);
        continue;
      }

      if (inCodeBlock) {
        if (!codeBlock.isEmpty()) {
          codeBlock.append('\n');
        }
        codeBlock.append(line);
        continue;
      }

      if (line.isEmpty()) {
        listState = closeList(html, listState);
        continue;
      }

      Matcher headingMatcher = HEADING_PATTERN.matcher(line);
      if (headingMatcher.matches()) {
        listState = closeList(html, listState);
        int level = headingMatcher.group(1).length();
        html.append('<').append('h').append(level).append('>');
        html.append(parseInlineHtml(headingMatcher.group(2)));
        html.append("</h").append(level).append(">\n");
        continue;
      }

      Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
      if (bulletMatcher.matches()) {
        listState = openList(html, listState, ListState.UNORDERED);
        html.append("<li>").append(parseInlineHtml(bulletMatcher.group(2))).append("</li>\n");
        continue;
      }

      Matcher numberedMatcher = NUMBERED_PATTERN.matcher(line);
      if (numberedMatcher.matches()) {
        listState = openList(html, listState, ListState.ORDERED);
        html.append("<li>").append(parseInlineHtml(numberedMatcher.group(3))).append("</li>\n");
        continue;
      }

      listState = closeList(html, listState);
      html.append("<p>").append(parseInlineHtml(line)).append("</p>\n");
    }

    if (inCodeBlock && !codeBlock.isEmpty()) {
      html.append("<pre><code>").append(escapeHtml(codeBlock.toString())).append("</code></pre>\n");
    }
    closeList(html, listState);
    return html.toString();
  }

  private enum ListState {
    NONE,
    UNORDERED,
    ORDERED
  }

  private static ListState openList(StringBuilder html, ListState current, ListState desired) {
    if (current == desired) {
      return current;
    }
    closeList(html, current);
    html.append(desired == ListState.UNORDERED ? "<ul>\n" : "<ol>\n");
    return desired;
  }

  private static ListState closeList(StringBuilder html, ListState current) {
    if (current == ListState.UNORDERED) {
      html.append("</ul>\n");
    } else if (current == ListState.ORDERED) {
      html.append("</ol>\n");
    }
    return ListState.NONE;
  }

  private static String parseInlineHtml(String input) {
    if (Utils.isEmpty(input)) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    parseInlineSegment(input, html);
    return html.toString();
  }

  private static void parseInlineSegment(String input, StringBuilder html) {
    if (Utils.isEmpty(input)) {
      return;
    }

    InlineMatch match = findEarliestInlineMatch(input);
    if (match == null) {
      html.append(escapeHtml(input));
      return;
    }

    if (match.start() > 0) {
      html.append(escapeHtml(input.substring(0, match.start())));
    }

    switch (match.kind()) {
      case CODE -> html.append("<code>").append(escapeHtml(match.content())).append("</code>");
      case BOLD -> html.append("<strong>").append(escapeHtml(match.content())).append("</strong>");
      case ITALIC -> html.append("<em>").append(escapeHtml(match.content())).append("</em>");
      case LINK ->
          html.append("<a href=\"")
              .append(escapeHtmlAttribute(match.href()))
              .append("\">")
              .append(escapeHtml(match.content()))
              .append("</a>");
    }

    parseInlineSegment(input.substring(match.end()), html);
  }

  private static InlineMatch findEarliestInlineMatch(String input) {
    List<InlineMatch> matches = new ArrayList<>();
    collectPatternMatch(input, INLINE_CODE_PATTERN, InlineKind.CODE, matches, 1);
    collectLinkMatches(input, matches);
    collectBoldMatches(input, matches);
    collectItalicMatches(input, matches);
    return matches.stream().min(Comparator.comparingInt(InlineMatch::start)).orElse(null);
  }

  private static void collectPatternMatch(
      String input, Pattern pattern, InlineKind kind, List<InlineMatch> matches, int contentGroup) {
    Matcher matcher = pattern.matcher(input);
    if (matcher.find()) {
      matches.add(
          new InlineMatch(
              matcher.start(), matcher.end(), matcher.group(contentGroup), kind, null));
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
              InlineKind.LINK,
              matcher.group(2)));
    }
  }

  private static void collectBoldMatches(String input, List<InlineMatch> matches) {
    Matcher matcher = BOLD_PATTERN.matcher(input);
    if (matcher.find()) {
      String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      matches.add(new InlineMatch(matcher.start(), matcher.end(), content, InlineKind.BOLD, null));
    }
  }

  private static void collectItalicMatches(String input, List<InlineMatch> matches) {
    Matcher matcher = ITALIC_PATTERN.matcher(input);
    if (matcher.find()) {
      String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      matches.add(new InlineMatch(matcher.start(), matcher.end(), content, InlineKind.ITALIC, null));
    }
  }

  private static String escapeHtml(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      switch (ch) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        default -> escaped.append(ch);
      }
    }
    return escaped.toString();
  }

  private static String escapeHtmlAttribute(String text) {
    return escapeHtml(text).replace("'", "&#39;");
  }

  private enum InlineKind {
    CODE,
    BOLD,
    ITALIC,
    LINK
  }

  private record InlineMatch(int start, int end, String content, InlineKind kind, String href) {}
}