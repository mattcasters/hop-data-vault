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

import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;

/** Shared helpers for mapping source columns to Data Vault target field names. */
public final class DvSourceFieldMappingSupport {

  private DvSourceFieldMappingSupport() {}

  public static String findTargetSourceFieldName(
      DataVaultConfiguration configuration, DataVaultSource recordSource, IDvTable table)
      throws HopException {
    String targetSourceFieldName =
        switch (table.getTableType()) {
          case HUB -> ((DvHub) table).getRecordSourceFieldName();
          case SATELLITE -> null;
          case LINK -> ((DvLink) table).getRecordSourceFieldName();
        };
    if (StringUtils.isEmpty(targetSourceFieldName) && configuration != null) {
      targetSourceFieldName = configuration.getRecordSourceField();
    }
    if (StringUtils.isEmpty(targetSourceFieldName)) {
      throw new HopException(
          "Please specify a source field name in either the table or the configuration for "
              + table.getTableType()
              + " "
              + table.getName());
    }
    return targetSourceFieldName;
  }

  public static String resolveRecordSourceValue(DataVaultSource recordSource) throws HopException {
    String sourceFieldName = recordSource.getSourceIndicatorField();
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      return null;
    }
    String sourceIndicator = recordSource.getSourceIndicator();
    if (StringUtils.isEmpty(sourceIndicator)) {
      throw new HopException(
          "Please specify a static source indicator or a field in source " + recordSource.getName());
    }
    return sourceIndicator;
  }
}