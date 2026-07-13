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

# SQL business table

Author a SQL query that materialises as a **view** or **table** in the Business Vault database.

## Materialization

- **View** — `CREATE OR REPLACE VIEW name AS query`
- **Table** — `CREATE OR REPLACE TABLE name AS query`

## Templates (dbt style)

- `{{ ref('object') }}` — BV table in this model or DV table in the linked vault model
- `{{ ref('model', 'object') }}` — object in a model matching that basename
- `{{ source('source', 'table') }}` — must be declared on the **Sources** tab

Use **Parse / sync refs** after editing SQL. **Preview resolved SQL** shows the full CREATE statement.

See `docs/business-vault-sql-view.adoc` for full documentation.
