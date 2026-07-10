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

package org.apache.hop.quality.profile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/** Per-field metrics collected during a measure pass. */
@Getter
public class FieldProfile {

  private final String fieldName;
  private long nullCount;
  private long emptyStringCount;
  private long nonNullCount;
  private Comparable<?> minValue;
  private Comparable<?> maxValue;
  private final Set<String> distinctValues = new LinkedHashSet<>();
  private final Map<String, Long> valueCounts = new LinkedHashMap<>();
  private boolean distinctTruncated;

  public FieldProfile(String fieldName) {
    this.fieldName = fieldName;
  }

  public void observeNull() {
    nullCount++;
  }

  public void observeEmptyString() {
    emptyStringCount++;
    nonNullCount++;
  }

  public void observeValue(Object raw, String display, int maxDistinct) {
    observeValueCount(raw, display, 1L, maxDistinct);
  }

  /** Bulk observe for SQL GROUP BY counts. */
  public void observeValueCount(Object raw, String display, long count, int maxDistinct) {
    if (count <= 0) {
      return;
    }
    nonNullCount += count;
    if (raw instanceof Comparable<?> comparable) {
      updateMinMax(comparable);
    }
    if (display == null) {
      return;
    }
    valueCounts.merge(display, count, Long::sum);
    if (distinctValues.size() < maxDistinct) {
      distinctValues.add(display);
    } else if (!distinctValues.contains(display)) {
      distinctTruncated = true;
    }
  }

  public void setMinMaxFromSql(Comparable<?> min, Comparable<?> max) {
    if (min != null) {
      updateMinMax(min);
    }
    if (max != null) {
      updateMinMax(max);
    }
  }

  public void addNullCount(long count) {
    if (count > 0) {
      nullCount += count;
    }
  }

  public void addEmptyStringCount(long count) {
    if (count > 0) {
      emptyStringCount += count;
      nonNullCount += count;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void updateMinMax(Comparable value) {
    if (minValue == null || value.compareTo(minValue) < 0) {
      minValue = value;
    }
    if (maxValue == null || value.compareTo(maxValue) > 0) {
      maxValue = value;
    }
  }

  public long getObservedCount() {
    return nullCount + nonNullCount;
  }
}
