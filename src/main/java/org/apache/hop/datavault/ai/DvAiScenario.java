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

package org.apache.hop.datavault.ai;

import org.apache.hop.core.util.Utils;

/** Advisory scenario selecting the system prompt template. */
public enum DvAiScenario {
  SOURCE_ANALYSIS("source-analysis"),
  TYPE_MAPPING("type-mapping"),
  DV_MODELING("dv-modeling"),
  HOP_INTEGRATION("hop-integration"),
  ERROR_DIAGNOSIS("error-diagnosis"),
  GENERAL("general");

  private final String promptResource;

  DvAiScenario(String promptResource) {
    this.promptResource = promptResource;
  }

  public String getPromptResource() {
    return promptResource;
  }

  public static DvAiScenario resolve(String value) {
    if (Utils.isEmpty(value)) {
      return GENERAL;
    }
    for (DvAiScenario scenario : values()) {
      if (scenario.name().equalsIgnoreCase(value.trim())) {
        return scenario;
      }
    }
    return GENERAL;
  }
}