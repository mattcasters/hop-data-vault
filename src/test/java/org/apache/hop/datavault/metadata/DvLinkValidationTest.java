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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLinkValidationTest {

  private static final Class<?> PKG = DvLink.class;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void checkErrorsWhenLinkHubSourcesMissing() {
    DvLink link = new DvLink("lnk_order");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.NoLinkHubSources"))));
  }

  @Test
  void externalReadLinkSkipsLinkHubSourceRequirement() {
    DvLink link = new DvLink("lnk_order");
    link.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.NoLinkHubSources"))));
  }

  @Test
  void generateUpdatePipelinesThrowsWhenLinkHubSourcesMissing() {
    DataVaultModel model = new DataVaultModel();
    DvLink link = new DvLink("lnk_order");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");
    model.getTables().add(link);

    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();

    HopException ex =
        assertThrows(
            HopException.class,
            () ->
                link.generateUpdatePipelines(
                    metadataProvider, new Variables(), model, new Date(), null));

    assertTrue(
        ex.getMessage()
            .contains(
                BaseMessages.getString(PKG, "DvLink.Error.NoLinkHubSources", link.getName())));
  }

  @Test
  void targetTableLayoutUsesPerLinkRecordSourceFieldOverride() throws HopException {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setRecordSourceField("x_record_source");

    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setHashKeyFieldName("customer_hk");
    DvHub hubOrder = new DvHub("hub_order");
    hubOrder.setHashKeyFieldName("order_hk");

    DvLink link = new DvLink("lnk_order");
    link.setRecordSourceFieldName("record_source");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");
    model.getTables().addAll(List.of(hubCustomer, hubOrder, link));

    IRowMeta layout =
        link.getTargetTableLayout(new MemoryMetadataProvider(), new Variables(), model);

    assertTrue(
        layout.getValueMetaList().stream()
            .anyMatch(meta -> "record_source".equals(meta.getName())));
    assertFalse(
        layout.getValueMetaList().stream()
            .anyMatch(meta -> "x_record_source".equals(meta.getName())));
  }

  @Test
  void validateLinkRecordSourceFieldsFlagsMissingSourceIndicator() {
    DataVaultModel model = new DataVaultModel();
    DvLink link = new DvLink("lnk_order");
    link.setRecordSourceFieldName("record_source");

    DataVaultSource source = new DataVaultSource("order-header");
    source.getDvSourceOrDefault().setFields(List.of());

    DvLink.DvLinkHubSource linkSource =
        new DvLink.DvLinkHubSource() {
          @Override
          public DataVaultSource resolveSource(
              org.apache.hop.core.variables.IVariables variables,
              IHopMetadataProvider metadataProvider,
              DataVaultModel model)
              throws HopException {
            return source;
          }
        };
    linkSource.setSourceName("order-header");
    link.getLinkHubSources().add(linkSource);
    model.getTables().add(link);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateLinkRecordSourceFields(
        link,
        model,
        DvModelCheckOptions.fastOnly(),
        new MemoryMetadataProvider(),
        new Variables(),
        link,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .contains("order-header")
                            && r.getText().contains("lnk_order")));
  }
}