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

import org.eclipse.swt.widgets.Combo;

/** Shared parsing and enum helpers for {@link ElkLayout} editors. */
public final class ElkLayoutValues {

  private ElkLayoutValues() {}

  public static int parseNonNegativeInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(0, parsed);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static int parsePositiveInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
    if (value == null || value.isBlank()) {
      return enumClass.getEnumConstants()[0];
    }
    try {
      return Enum.valueOf(enumClass, value.trim());
    } catch (IllegalArgumentException e) {
      return enumClass.getEnumConstants()[0];
    }
  }

  public static <E extends Enum<E>> void populateEnumCombo(Combo combo, Class<E> enumClass) {
    for (E constant : enumClass.getEnumConstants()) {
      combo.add(constant.name());
    }
  }

  public static void selectEnumCombo(Combo combo, Enum<?> value) {
    if (value == null) {
      combo.select(0);
      return;
    }
    int index = combo.indexOf(value.name());
    combo.select(index >= 0 ? index : 0);
  }
}