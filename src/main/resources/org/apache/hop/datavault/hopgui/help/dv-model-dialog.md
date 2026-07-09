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

# Data Vault model settings

Open with **Edit model** on the Data Vault graph toolbar. Settings apply to every hub, link, and satellite in this `.hdv` file unless a table dialog overrides a column name.

## General tab

- **Target database** — Connection where vault tables are created and loaded.
- **Hash algorithm** / **Hash key data type** — How surrogate keys are computed and stored (HEX, STRING, BINARY).
- **Hash content casing**, **delimiter**, **prefix/suffix**, **null placeholder**, **trim business keys** — Hash input formatting.

## Unknown / Invalid records tabs

Sentinel rows for unknown and invalid keys in hubs and links. Used when **Ensure unknown and invalid records** is enabled on Data Vault Update.

## Standard columns tab

Global names for columns present on vault tables:

- **Load date field name** — Default `LOAD_DATE`.
- **Record source field name** — Default `RECORD_SOURCE`. This is the **column name** in target tables. Hubs can override per table; hub satellites use the parent hub override when set.
- **Record source field length** — VARCHAR length for the record source column.

## Target loading tab

How generated pipelines write to the database (table output, bulk load, batch size, parallel copies, sort buffer).

## Generated pipelines tab

Folder and name prefixes for generated hub, link, and satellite pipeline files.