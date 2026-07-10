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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.quality.engine.QualityEvaluationContext;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.QualitySeverity;

final class EvaluatorSupport {

  private EvaluatorSupport() {}

  static DataQualityFinding finding(
      DataQualityRule rule,
      QualityEvaluationContext context,
      String fieldName,
      String message,
      String actual,
      String expected,
      Map<String, String> metrics) {
    QualitySeverity severity =
        rule.getSeverity() != null ? rule.getSeverity() : QualitySeverity.BLOCKING;
    return DataQualityFinding.builder()
        .subjectKey(context.getSubjectKey())
        .ruleId(rule.getId())
        .ruleName(rule.getName())
        .type(rule.getType())
        .severity(severity)
        .fieldName(fieldName)
        .message(message)
        .actualSummary(actual)
        .expectedSummary(expected)
        .metrics(metrics != null ? metrics : Map.of())
        .build();
  }

  static String resolveField(DataQualityRule rule) {
    return rule != null ? rule.getFieldName() : null;
  }

  static boolean missingField(DataQualityRule rule) {
    return Utils.isEmpty(resolveField(rule));
  }

  static Map<String, String> metric(String key, String value) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  static Double parseDouble(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Long parseLong(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
