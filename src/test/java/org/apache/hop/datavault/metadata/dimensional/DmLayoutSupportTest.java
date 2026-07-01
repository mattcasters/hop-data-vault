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

package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmLayoutSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void type1DimensionLayoutIncludesNaturalKeysAttributesAndLoadDate() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension.getAttributes().add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE1));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout = DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertEquals(3, layout.size());
    assertTrue(layout.indexOfValue("customer_id") >= 0);
    assertTrue(layout.indexOfValue("customer_name") >= 0);
    assertTrue(layout.indexOfValue("load_dt") >= 0);
  }

  @Test
  void type2DimensionLayoutIncludesScdControlColumns() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    dimension.getAttributes().add(new DmDimensionAttribute("customer_name", DmScdUpdatePolicy.TYPE2));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout = DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("dim_key") >= 0);
    assertTrue(layout.indexOfValue("customer_id") >= 0);
    assertTrue(layout.indexOfValue("version") >= 0);
    assertEquals(
        DmLayoutSupport.DEFAULT_INTEGER_FIELD_LENGTH,
        layout.searchValueMeta("version").getLength());
    assertEquals(IValueMeta.TYPE_INTEGER, layout.searchValueMeta("version").getType());
    assertTrue(layout.indexOfValue("date_from") >= 0);
    assertTrue(layout.indexOfValue("date_to") >= 0);
    assertTrue(layout.indexOfValue("is_current") >= 0);
    assertTrue(layout.indexOfValue("customer_name") >= 0);
    assertTrue(layout.indexOfValue("load_dt") >= 0);
  }

  @Test
  void type1DateDimensionLayoutUsesSourceIntegerTypeForNaturalKey() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    dimension.getAttributes().add(new DmDimensionAttribute("full_date", DmScdUpdatePolicy.TYPE1));

    IRowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaInteger("date_key"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    IRowMeta layout =
        DmLayoutSupport.buildDimensionTargetTableLayout(
            dimension, config, new Variables(), sourceRowMeta);

    assertEquals(IValueMeta.TYPE_INTEGER, layout.searchValueMeta("date_key").getType());
  }

  @Test
  void type1DateDimensionUsesNaturalKeyForLookup() {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_date");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    assertEquals(
        "date_key",
        DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, new Variables()));
  }

  @Test
  void type2DimensionUsesConfiguredSurrogateKeyForLookup() {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    assertEquals(
        "dim_key",
        DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, new Variables()));
  }

  @Test
  void type2DimensionWithCustomSurrogateFieldUsesConfiguredColumn() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setSurrogateKeyField("customer_key");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout = DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("customer_key") >= 0);
    assertEquals("customer_key", DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, new Variables()));
  }

  @Test
  void useSourceFieldSurrogateUsesStringColumnInLayout() throws HopException {
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_customer");
    dimension.setScdType(DmDimensionScdType.TYPE2);
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.USE_SOURCE_FIELD);
    dimension.setSurrogateKeyField("customer_hk");
    dimension.setSurrogateKeySourceField("customer_hk");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout = DmLayoutSupport.buildDimensionTargetTableLayout(dimension, config, new Variables());

    assertTrue(layout.indexOfValue("customer_hk") >= 0);
    assertEquals(
        org.apache.hop.core.row.IValueMeta.TYPE_STRING,
        layout.searchValueMeta("customer_hk").getType());
  }

  @Test
  void factLayoutIncludesForeignKeysAndMeasures() throws HopException {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    model.getTables().add(customer);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.getDimensionRoles()
        .add(new DmFactDimensionRole("dim_customer", "Customer", "customer_key"));
    fact.getMeasures().add(new DmFactMeasure("quantity", true));
    fact.getMeasures().add(new DmFactMeasure("amount", true));

    DimensionalConfiguration config = new DimensionalConfiguration();
    var layout =
        DmLayoutSupport.buildFactTargetTableLayout(fact, model, config, new Variables());

    assertEquals(3, layout.size());
    assertTrue(layout.indexOfValue("customer_key") >= 0);
    assertTrue(layout.indexOfValue("quantity") >= 0);
    assertTrue(layout.indexOfValue("amount") >= 0);
  }
}