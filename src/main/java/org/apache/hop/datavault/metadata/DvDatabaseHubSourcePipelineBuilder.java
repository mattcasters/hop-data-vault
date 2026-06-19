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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

@Getter
@Setter
public class DvDatabaseHubSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {
  public DvDatabaseHubSourcePipelineBuilder(IVariables variables, IHopMetadataProvider metadataProvider, DataVaultModel model, PipelineMeta pipelineMeta, DataVaultSource recordSource, IDvSource dvSource, DvHub dvTable, Point startPoint) {
    super(variables, metadataProvider, model, pipelineMeta, recordSource, dvSource, dvTable, startPoint);
  }

  protected String getSql() throws HopException {
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    DvHub hub = (DvHub) dvTable;
    DvDatabaseSource source = (DvDatabaseSource) dvSource;
    List<String> pkQuotedFields = getQuotedPkFields(hub, sourceDbMeta);

    // PK
    appendFields(sql, pkQuotedFields);

    // Source indicator
    appendComma(sql);
    appendSourceField(hub, sql, sourceDbMeta);

    // FROM
    appendFrom(sourceDbMeta, source, sql);

    // ORDER BY
    appendOrderByPk(sql, pkQuotedFields);

    return sql.toString();
  }
}
