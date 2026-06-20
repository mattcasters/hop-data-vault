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

package org.apache.hop.datavault.layout;

import org.eclipse.elk.alg.layered.options.LayeringStrategy;

/** Layer assignment strategy for ELK layered layout. */
public enum ElkLayeringStrategy {
  NETWORK_SIMPLEX(LayeringStrategy.NETWORK_SIMPLEX),
  LONGEST_PATH(LayeringStrategy.LONGEST_PATH),
  LONGEST_PATH_SOURCE(LayeringStrategy.LONGEST_PATH_SOURCE),
  COFFMAN_GRAHAM(LayeringStrategy.COFFMAN_GRAHAM),
  INTERACTIVE(LayeringStrategy.INTERACTIVE),
  STRETCH_WIDTH(LayeringStrategy.STRETCH_WIDTH),
  MIN_WIDTH(LayeringStrategy.MIN_WIDTH),
  BF_MODEL_ORDER(LayeringStrategy.BF_MODEL_ORDER),
  DF_MODEL_ORDER(LayeringStrategy.DF_MODEL_ORDER);

  private final LayeringStrategy elkStrategy;

  ElkLayeringStrategy(LayeringStrategy elkStrategy) {
    this.elkStrategy = elkStrategy;
  }

  public LayeringStrategy toElkStrategy() {
    return elkStrategy;
  }
}