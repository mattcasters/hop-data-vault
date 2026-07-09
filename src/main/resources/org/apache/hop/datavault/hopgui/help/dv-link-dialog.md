<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at
    
         http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# Link editor

A **Link** represents a relationship between two or more **Hubs** — for example *Customer placed Order*, *Product on Order line*, or *Employee assigned to Department*. The link table stores the participating hub hash keys, a surrogate **link hash key**, the record source, and the load date.

Links are insert-only: a generated pipeline reads relationship rows from a source, computes hub and link hash keys, and inserts only combinations that do not already exist in the target link table.

## What the link table contains

| Column | Meaning |
|--------|---------|
| Link hash key | Surrogate key for the relationship (hash of participating hub hashes, plus driving keys when used) |
| Hub hash key(s) | One column per participating hub, in the order listed on the **Options** tab |
| Record source | Which feed supplied the row (from the catalog source configuration) |
| Load date | Batch load timestamp |

If the relationship itself has descriptive attributes that change over time (contract terms, quantity, status, …), attach a **link satellite** and map its source on the **Satellite sources** tab.

## Three layers of configuration

Link loading is easier to reason about when you separate three concerns:

1. **Structure** (Options + Driving keys) — which hubs participate, target table names, hash key column names.
2. **Link hub sources** — which catalog **DV_SOURCE** feeds the link table, and how each source column maps to each hub's business keys.
3. **Link satellite sources** — which catalog feed supplies link-satellite attributes, and how source columns map to satellite fields.

The **Hub sources** tab loads the link itself. The **Satellite sources** tab loads descriptive attributes on top of an existing link. They are configured independently even when both read the same physical table.

## Options tab

| Field | Description |
|-------|-------------|
| Integration mode | **Hop managed** (default), **External read-only**, or **Custom pipelines** |
| Physical table name | Target table in the vault database; defaults to the link name |
| Link hash key field name | Surrogate key column (e.g. `lnk_order_line_hk`). Empty defaults to link name + `_LK` |
| Record source field name | Optional per-link override for the record source **column** name in the link table |
| Has descriptive attributes | Documentation flag when you plan a link satellite; does not change link loading |
| Participating hubs | At least two hubs. **Order matters** — it defines hash-key column order and link hash calculation |
| Participating link satellites | Link satellites that describe this relationship (parent link is set on each satellite) |

Define hubs before adding them to a link. Use **Check model** to catch missing hubs, empty hub-source mappings, or type mismatches.

## Driving keys tab

Use **driving keys** when the same hub appears more than once in one link under different roles — for example a route with *from location* and *to location*, both referencing a Location hub.

| Concept | Where you set it |
|---------|------------------|
| Driving key **name** | **Driving keys** tab (target column names, e.g. `FROM_LOCATION_HK`) |
| Driving key **source column** | **Hub sources** → edit hub mapping → driving key source fields |

Driving key values are included in the link hash calculation and are usually stored in the link table for downstream use.

## Hub sources tab

Each row is one **catalog DV_SOURCE** that supplies relationship rows for this link. Hop generates **one update pipeline per hub source**.

For every source you must:

1. Select the **Data Vault Source** (catalog feed).
2. Click **Edit hub source mappings…** and, for **each participating hub**, map source columns to that hub's business keys.

See the **Link hub source** help (opened from that sub-dialog) for the detailed mapping workflow.

Typical pattern: an order-line table feeds `lnk_order_line`. The source has `order_id` and `product_id` columns. You map `order_id` → `hub_order.order_id` and `product_id` → `hub_product.product_id` even when column names match — explicit mappings are required for every hub.

## Satellite sources tab

When a link has descriptive attributes, create a **link satellite** (parent link set on the satellite's General tab) and list it under **Participating link satellites** on the Options tab.

Each row on **Satellite sources** is one catalog **DV_SOURCE** feeding those link satellites. Click **Edit satellite source mappings…** to map source columns to satellite attribute fields (and driving keys for multi-active link satellites).

The link hub source and link satellite source can point at the **same** catalog feed (common for bridge tables that carry both keys and attributes) or at different feeds.

## How link loading works

For each entry on **Hub sources**, the generated pipeline:

1. Reads mapped business key (and driving key) columns from the source.
2. Computes a hash key for each participating hub from those values.
3. Computes the link hash key from the hub hash keys (and driving keys).
4. Compares against the existing link table on the link hash key.
5. Inserts only new relationships, with load date and record source.

The record source **value** written to each row comes from the catalog source (fixed indicator or indicator field), not from a field on the link dialog.

## Worked example: order line link

`lnk_order_line` connects `hub_order` and `hub_product` from source `E2E-order-line`:

| Hub | Hub business key | Source column |
|-----|------------------|---------------|
| hub_order | order_id | order_id |
| hub_product | product_id | product_id |

Link satellite `sat_lnk_order_line` (quantity, unit_price, discount_pct) uses the same source `E2E-order-line` with attribute mappings on the **Satellite sources** tab. A driving key (`line_number`) on the satellite supports multiple lines per order–product pair.

## Tips

- Configure catalog **DV_SOURCE** feeds in the Data Catalog perspective before binding link sources.
- Map **every** participating hub for **every** hub source — partial mappings fail at runtime.
- Keep hub order consistent; it affects physical column order and hashing.
- Use driving keys whenever one hub plays multiple roles in the same link.
- Run **Check model** before updates to validate source field names exist in the catalog feed and types match hub business keys.
- For link satellites, set the parent link on the satellite editor, then wire **Satellite sources** here.