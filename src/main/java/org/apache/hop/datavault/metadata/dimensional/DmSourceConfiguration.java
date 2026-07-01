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

package org.apache.hop.datavault.metadata.dimensional;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Staging source for a dimensional table load pipeline. */
@Getter
@Setter
public class DmSourceConfiguration {

  /** Optional database connection override; defaults to model source or target database. */
  @HopMetadataProperty private String sourceConnection;

  /** SQL executed by the generated source {@code TableInput}. */
  @HopMetadataProperty private String sourceSql;

  public String resolveSourceConnection(
      DimensionalConfiguration config, IVariables variables) {
    String connection = sourceConnection;
    if (variables != null) {
      connection = variables.resolve(connection);
    }
    if (!Utils.isEmpty(connection)) {
      return connection;
    }
    if (config != null && !Utils.isEmpty(config.getSourceDatabase())) {
      String sourceDb = config.getSourceDatabase();
      if (variables != null) {
        sourceDb = variables.resolve(sourceDb);
      }
      if (!Utils.isEmpty(sourceDb)) {
        return sourceDb;
      }
    }
    if (config != null) {
      return config.getTargetDatabase();
    }
    return null;
  }

  public String resolveSourceSql(IVariables variables) {
    String sql = sourceSql;
    if (variables != null) {
      sql = variables.resolve(sql);
    }
    return sql;
  }
}