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
 */

package org.apache.hop.datavault.catalog;

import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Maps {@link DataVaultSource} metadata to catalog {@link RecordDefinition} entries. */
public final class DvSourceCatalogMapper {

  private DvSourceCatalogMapper() {}

  public static RecordDefinition toRecordDefinition(
      DataVaultSource source,
      String namespace,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName)
      throws HopException {
    if (source == null || Utils.isEmpty(source.getName())) {
      throw new HopException("Data Vault source is missing a name");
    }

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, source.getName()));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setDescription(source.getDvSourceOrDefault().getDescription());
    definition.setFields(fieldsFromSource(source, variables, metadataProvider));
    definition.setOrigin(buildOrigin(source, model, updatedAt, workflowName));
    definition.getTags().add("DV Source");
    if (source.getDeliveryTypeOrDefault() != null) {
      definition.getTags().add(source.getDeliveryTypeOrDefault().name());
    }

    definition
        .getCustomProperties()
        .put("sourceIndicator", CatalogCustomProperty.string(source.getSourceIndicator()));
    definition
        .getCustomProperties()
        .put(
            "sourceIndicatorField",
            CatalogCustomProperty.string(source.getSourceIndicatorField()));
    definition.getCustomProperties().put("group", CatalogCustomProperty.string(source.getGroup()));
    definition
        .getCustomProperties()
        .put(
            "deliveryType",
            CatalogCustomProperty.string(
                source.getDeliveryTypeOrDefault() != null
                    ? source.getDeliveryTypeOrDefault().name()
                    : null));

    IDvSource dvSource = source.getDvSourceOrDefault();
    if (dvSource instanceof DvDatabaseSource dbSource) {
      definition
          .getCustomProperties()
          .put("databaseName", CatalogCustomProperty.string(dbSource.getDatabaseName()));
      definition
          .getCustomProperties()
          .put("schemaName", CatalogCustomProperty.string(dbSource.getSchemaName()));
      definition
          .getCustomProperties()
          .put("tableName", CatalogCustomProperty.string(dbSource.getTableName()));
    }

    return definition;
  }

  private static RecordOrigin buildOrigin(
      DataVaultSource source,
      DataVaultModel model,
      Date updatedAt,
      String workflowName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType("DATA_VAULT_SOURCE");
    if (model != null) {
      origin.setModelName(model.getName());
      origin.setModelFilename(model.getFilename());
      origin.setHopProject(model.getName());
    }
    origin.setModelElementName(source.getName());
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    return origin;
  }

  private static IRowMeta fieldsFromSource(
      DataVaultSource source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<SourceField> fields = source.getFields(metadataProvider);
    RowMeta rowMeta = new RowMeta();
    for (SourceField field : fields) {
      try {
        rowMeta.addValueMeta(valueMetaFromSourceField(field, variables));
      } catch (HopPluginException e) {
        throw new HopException("Unable to map source field '" + field.getName() + "'", e);
      }
    }
    return rowMeta;
  }

  private static org.apache.hop.core.row.IValueMeta valueMetaFromSourceField(
      SourceField sf, IVariables variables) throws HopPluginException {
    String name = variables != null ? variables.resolve(sf.getName()) : sf.getName();
    int type = sf.getHopType();
    if (type <= 0) {
      type = org.apache.hop.core.row.IValueMeta.TYPE_STRING;
    }
    return ValueMetaFactory.createValueMeta(
        name,
        type,
        Const.toInt(resolveVariable(variables, sf.getLength()), -1),
        Const.toInt(resolveVariable(variables, sf.getPrecision()), -1));
  }

  private static String resolveVariable(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}