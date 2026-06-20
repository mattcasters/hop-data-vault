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

import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;

/** Cycle breaking strategy for ELK layered layout. */
public enum ElkCycleBreaking {
  GREEDY(CycleBreakingStrategy.GREEDY),
  DEPTH_FIRST(CycleBreakingStrategy.DEPTH_FIRST),
  INTERACTIVE(CycleBreakingStrategy.INTERACTIVE),
  MODEL_ORDER(CycleBreakingStrategy.MODEL_ORDER),
  GREEDY_MODEL_ORDER(CycleBreakingStrategy.GREEDY_MODEL_ORDER);

  private final CycleBreakingStrategy elkStrategy;

  ElkCycleBreaking(CycleBreakingStrategy elkStrategy) {
    this.elkStrategy = elkStrategy;
  }

  public CycleBreakingStrategy toElkStrategy() {
    return elkStrategy;
  }
}