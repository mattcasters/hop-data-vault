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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvFieldMappingValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void validateSatelliteMappingsFlagsMissingHubBusinessKeyInSatelliteSource() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_syn_order");
    BusinessKey orderId = new BusinessKey("order_id");
    orderId.setSourceFieldName("order_id");
    orderId.setRecordSourceName("syn-order");
    orderId.setDataType("Integer");
    orderId.setLength("15");
    hub.setBusinessKeys(List.of(orderId));
    List<IDvTable> tables = new ArrayList<>();
    tables.add(hub);

    DvSatellite satellite = new TestSatellite("sat_syn_order", sourceWithFields("syn-product", "product_id"));
    satellite.setHubName("hub_syn_order");
    satellite.setRecordSourceName("syn-product");
    satellite.setAttributes(List.of(attribute("product_id")));
    tables.add(satellite);
    model.setTables(tables);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateSatelliteMappings(
        satellite,
        model,
        DvModelCheckOptions.fastOnly(),
        null,
        new Variables(),
        satellite,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("order_id")
                        && r.getText().contains("hub_syn_order")
                        && r.getText().contains("syn-product")));
  }

  @Test
  void validateSatelliteMappingsAcceptsHubBusinessKeyPresentInSatelliteSource() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_syn_order");
    BusinessKey orderId = new BusinessKey("order_id");
    orderId.setSourceFieldName("order_id");
    orderId.setRecordSourceName("syn-order");
    orderId.setDataType("Integer");
    orderId.setLength("15");
    hub.setBusinessKeys(List.of(orderId));
    List<IDvTable> tables = new ArrayList<>();
    tables.add(hub);

    DvSatellite satellite =
        new TestSatellite("sat_syn_order", sourceWithFields("syn-order", "order_id", "amount"));
    satellite.setHubName("hub_syn_order");
    satellite.setRecordSourceName("syn-order");
    satellite.setAttributes(List.of(attribute("amount")));
    tables.add(satellite);
    model.setTables(tables);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateSatelliteMappings(
        satellite,
        model,
        DvModelCheckOptions.fastOnly(),
        null,
        new Variables(),
        satellite,
        remarks);

    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("Hub business key source field")));
  }

  private static DataVaultSource sourceWithFields(String name, String... fieldNames) {
    DataVaultSource source = new DataVaultSource(name);
    List<SourceField> fields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      SourceField field = new SourceField();
      field.setName(fieldName);
      field.setSourceDataType("Integer");
      field.setLength("15");
      field.setHopType(IValueMeta.TYPE_INTEGER);
      fields.add(field);
    }
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static SatelliteAttribute attribute(String name) {
    SatelliteAttribute attribute = new SatelliteAttribute();
    attribute.setName(name);
    attribute.setDataType("Integer");
    attribute.setLength("15");
    return attribute;
  }

  private static final class TestSatellite extends DvSatellite {
    private final DataVaultSource recordSource;

    private TestSatellite(String name, DataVaultSource recordSource) {
      super(name);
      this.recordSource = recordSource;
    }

    @Override
    public DataVaultSource resolveRecordSource(
        org.apache.hop.core.variables.IVariables variables,
        IHopMetadataProvider metadataProvider,
        DataVaultModel model) {
      return recordSource;
    }
  }
}