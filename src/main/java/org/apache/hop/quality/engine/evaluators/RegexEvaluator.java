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

package org.apache.hop.quality.engine.evaluators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.quality.engine.IDataQualityRuleEvaluator;
import org.apache.hop.quality.engine.QualityEvaluationContext;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.quality.profile.FieldProfile;

/**
 * Evaluates REGEX rules using SQL pushdown metrics when present, otherwise client-side matching
 * against profiled value distributions (Java regex — sample/fall-through path only).
 */
public final class RegexEvaluator implements IDataQualityRuleEvaluator {

  public static final String MATCH_MODE_FULL = "FULL";
  public static final String MATCH_MODE_FIND = "FIND";
  public static final String MATCH_MODE_PARTIAL = "PARTIAL";

  public static final String PATH_PUSHDOWN = "pushdown";
  public static final String PATH_SAMPLE = "sample";

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.REGEX;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    String patternText = rule.parameter(DataQualityRule.PARAM_PATTERN);
    if (Utils.isEmpty(patternText)) {
      return List.of(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              EvaluatorSupport.resolveField(rule),
              "REGEX pattern is required",
              "pattern missing",
              "non-empty pattern",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
    }

    String fieldName = EvaluatorSupport.resolveField(rule);
    FieldProfile field = context.getProfile().findField(fieldName);
    if (field == null) {
      return List.of();
    }

    boolean nullAllowed = rule.parameterBoolean(DataQualityRule.PARAM_NULL_ALLOWED, true);
    List<DataQualityFinding> findings = new ArrayList<>();

    if (!nullAllowed && field.getNullCount() > 0) {
      findings.add(
          EvaluatorSupport.finding(
              rule,
              context,
              fieldName,
              "Field '"
                  + fieldName
                  + "' has "
                  + field.getNullCount()
                  + " null value(s) but nullAllowed=false",
              "nullCount=" + field.getNullCount(),
              "nullCount=0",
              EvaluatorSupport.metrics(
                  "nullCount", String.valueOf(field.getNullCount()), "nullAllowed", "false")));
    }

    FieldProfile.RegexRuleProfile regexProfile =
        field.findRegexProfile(rule.getId() != null ? rule.getId() : "");
    if (regexProfile == null && rule.getId() != null) {
      regexProfile = field.findRegexProfile(rule.getId());
    }
    // Also try name-based key used by some collectors when id blank
    if (regexProfile == null) {
      regexProfile = field.findRegexProfile(ruleKey(rule));
    }

    if (regexProfile != null && PATH_PUSHDOWN.equalsIgnoreCase(regexProfile.getPath())) {
      Long mismatch = regexProfile.getMismatchCount();
      if (mismatch != null && mismatch > 0) {
        Map<String, String> metrics =
            EvaluatorSupport.metrics(
                "mismatchCount", String.valueOf(mismatch), "path", PATH_PUSHDOWN);
        findings.add(
            EvaluatorSupport.finding(
                rule,
                context,
                fieldName,
                "Field '"
                    + fieldName
                    + "' has "
                    + mismatch
                    + " value(s) not matching regex",
                "mismatchCount=" + mismatch,
                "pattern=" + patternText,
                metrics));
      }
      return findings;
    }

    if (regexProfile != null && regexProfile.isSkipped()) {
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "REGEX evaluation skipped (no pushdown or sample available); use SQL_ASSERTION for"
                  + " high-cardinality free-text",
              "skipped",
              "evaluated",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
      return findings;
    }

    // Sample / client path (collector metrics or valueCounts)
    if (regexProfile != null && PATH_SAMPLE.equalsIgnoreCase(regexProfile.getPath())) {
      if (regexProfile.getSampleMismatchCount() > 0) {
        Map<String, String> metrics =
            EvaluatorSupport.metrics(
                "path",
                PATH_SAMPLE,
                "sampleSize",
                String.valueOf(regexProfile.getSampleSize()));
        metrics.put("mismatchCount", String.valueOf(regexProfile.getSampleMismatchCount()));
        findings.add(
            EvaluatorSupport.finding(
                rule,
                context,
                fieldName,
                "Field '"
                    + fieldName
                    + "' has sample value(s) not matching regex",
                "sampleMismatchCount=" + regexProfile.getSampleMismatchCount(),
                "pattern=" + patternText,
                metrics));
      } else if (regexProfile.isCoverageIncomplete()) {
        Map<String, String> metrics =
            EvaluatorSupport.metrics(
                "coverageIncomplete",
                "true",
                "sampleLimit",
                String.valueOf(regexProfile.getSampleLimit()));
        metrics.put("path", PATH_SAMPLE);
        findings.add(
            EvaluatorSupport.findingWithSeverity(
                rule,
                context,
                fieldName,
                "REGEX coverage incomplete (sample limit "
                    + regexProfile.getSampleLimit()
                    + ")",
                "sampleSize=" + regexProfile.getSampleSize(),
                "full coverage",
                metrics,
                QualitySeverity.INFO));
      }
      return findings;
    }

    // Fall-through: evaluate against collected value distribution with Java regex
    if (field.getValueCounts().isEmpty() && field.getDistinctValues().isEmpty()) {
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "REGEX evaluation skipped (no value distribution); use SQL_ASSERTION or ensure"
                  + " profiling collected values",
              "skipped",
              "evaluated",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
      return findings;
    }

    Pattern pattern;
    try {
      pattern = compile(rule, patternText);
    } catch (PatternSyntaxException e) {
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "Invalid Java regex pattern: " + e.getDescription(),
              "pattern=" + patternText,
              "valid regex",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
      return findings;
    }

    boolean fullMatch = isFullMatch(rule);
    long invalid = 0;
    long sampleSize = 0;
    for (Map.Entry<String, Long> entry : field.getValueCounts().entrySet()) {
      String value = entry.getKey();
      if (value == null) {
        continue;
      }
      sampleSize++;
      if (!matches(pattern, value, fullMatch)) {
        invalid += entry.getValue() != null ? entry.getValue() : 1L;
      }
    }
    // If only distinct set without counts
    if (field.getValueCounts().isEmpty()) {
      for (String value : field.getDistinctValues()) {
        if (value == null) {
          continue;
        }
        sampleSize++;
        if (!matches(pattern, value, fullMatch)) {
          invalid++;
        }
      }
    }

    if (invalid > 0) {
      Map<String, String> metrics =
          EvaluatorSupport.metrics(
              "path", PATH_SAMPLE, "sampleSize", String.valueOf(sampleSize));
      metrics.put("mismatchCount", String.valueOf(invalid));
      findings.add(
          EvaluatorSupport.finding(
              rule,
              context,
              fieldName,
              "Field '"
                  + fieldName
                  + "' has "
                  + invalid
                  + " value(s) not matching regex",
              "mismatchCount=" + invalid,
              "pattern=" + patternText,
              metrics));
    } else if (field.isDistinctTruncated()) {
      int limit =
          field.getDistinctValues().size() > 0
              ? field.getDistinctValues().size()
              : (int) sampleSize;
      Map<String, String> metrics =
          EvaluatorSupport.metrics(
              "coverageIncomplete", "true", "sampleLimit", String.valueOf(limit));
      metrics.put("path", PATH_SAMPLE);
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "REGEX coverage incomplete (sample limit " + limit + ")",
              "sampleSize=" + sampleSize,
              "full coverage",
              metrics,
              QualitySeverity.INFO));
    }

    return findings;
  }

  public static String ruleKey(DataQualityRule rule) {
    if (rule.getId() != null && !rule.getId().isBlank()) {
      return rule.getId();
    }
    if (rule.getName() != null && !rule.getName().isBlank()) {
      return rule.getName();
    }
    return String.valueOf(System.identityHashCode(rule));
  }

  public static boolean isFullMatch(DataQualityRule rule) {
    String mode = rule.parameter(DataQualityRule.PARAM_MATCH_MODE, MATCH_MODE_FULL);
    if (mode == null) {
      return true;
    }
    String normalized = mode.trim();
    return MATCH_MODE_FULL.equalsIgnoreCase(normalized)
        || (!MATCH_MODE_FIND.equalsIgnoreCase(normalized)
            && !MATCH_MODE_PARTIAL.equalsIgnoreCase(normalized));
  }

  public static Pattern compile(DataQualityRule rule, String patternText) {
    boolean caseSensitive = rule.parameterBoolean(DataQualityRule.PARAM_CASE_SENSITIVE, true);
    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    return Pattern.compile(patternText, flags);
  }

  public static boolean matches(Pattern pattern, String value, boolean fullMatch) {
    if (fullMatch) {
      return pattern.matcher(value).matches();
    }
    return pattern.matcher(value).find();
  }
}
