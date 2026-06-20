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

package org.apache.hop.datavault.metadata;

/**
 * Describes what each load from a {@link DataVaultSource} represents.
 *
 * <p>This distinction matters for Status Tracking Satellites and other snapshot-based CDC: deletion
 * detection is only valid when the feed is a complete image of the active source population, not a
 * delta of changes since the last run.
 */
public enum DvSourceDeliveryType {
  /** Delta feed: only new or changed rows since the previous extract. Default for most loads. */
  CHANGES_ONLY,

  /**
   * Full snapshot: the complete current-state record set for the modeled keys. Absence of a key in
   * a batch may indicate deletion (for STS tombstone processing).
   */
  FULL_SNAPSHOT
}