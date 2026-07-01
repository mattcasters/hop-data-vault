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

package org.apache.hop.datavault.metrics;

/** Variables and naming conventions for Data Vault update pipeline metrics collection. */
public final class DvUpdateMetricsConstants {

  /** Sentinel variable set on the orchestrator; inherited by staged update pipelines. */
  public static final String VAR_RUN_ID = "DV_UPDATE_METRICS_RUN_ID";

  /** Optional model name for log context (set together with {@link #VAR_RUN_ID}). */
  public static final String VAR_MODEL_NAME = "DV_UPDATE_MODEL_NAME";

  public static final String ORCHESTRATOR_NAME_PREFIX = "DV Update Orchestrator - ";

  public static final String SOURCE_TRANSFORM_PREFIX = "source ";

  /** Dimensional model pipelines use {@code source_<tableName>} table inputs. */
  public static final String DIMENSIONAL_SOURCE_TRANSFORM_PREFIX = "source_";

  public static final String TARGET_TRANSFORM_PREFIX = "target_";

  public static final String TARGET_TRANSFORM_DB_PREFIX = "target ";

  public static final String WRITE_TRANSFORM_PREFIX = "write_to_";

  private DvUpdateMetricsConstants() {}
}