<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing,
software distributed under this License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Hop Data Vault 2.0 Plugin

Apache Hop plugin for **Data Vault 2.0** and **Business Vault** modeling, validation, and model-driven loading. Version **0.0.15-SNAPSHOT** targets **Apache Hop 2.18.1** and **Java 21**.

The plugin provides Hop metadata types for hubs, links, satellites, and record sources; visual **`.hdv`** and **`.hbv`** model editors; **Data Vault Update** and **Business Vault Update** workflow actions; integration modes for hybrid warehouses (Hop-managed, external read-only, custom pipelines); optional **AI Help**; and a sample Hop project under `project/` for end-to-end testing.

## Features

### Metadata types

- **Data Vault sources** (data catalog, `DV_SOURCE` record definitions)
  - Logical feed name, static or column-based record source indicator, optional **`group`** for partial loads (e.g. `hourly`, `daily`)
  - Embedded **database** source definition (connection, schema, table, field layout with primary-key flags)
  - Managed in the **Data Catalog** perspective under `hop/{project}/sources`

- **Data Vault Hub** (`data-vault-hub`)
  - Business keys (composite supported) with per-source field mapping
  - Multiple record sources per hub (one pipeline per source, one physical table)

- **Data Vault Link** (`data-vault-link`)
  - Participating hubs, driving keys for role-playing, hub source key field mappings
  - Optional link satellite flag

- **Data Vault Satellite** (`data-vault-satellite`)
  - Attach to a hub **or** link
  - Attributes with include-in-CDC flag
  - **Multi-active** satellites via **`drivingKey`** and **`drivingKeySourceField`**

- **Data Vault Model** (`data-vault-model`, `.hdv` files)
  - Groups hubs, links, satellites, and canvas annotation notes for one EDW / subject area
  - Embeds **configuration** inline: target database, hashing, unknown/invalid sentinel rows, standard column names, target loading, and generated-pipeline options
  - **Integration mode** per table: Hop managed (default), external read-only, or custom pipelines

- **Business Vault Model** (`business-vault-model`, `.hbv` files)
  - Links to a `.hdv` model; defines SCD2 and PIT consumption tables
  - Embeds BV configuration: target database, SCD2 defaults, valid from/to sentinels, pipeline prefixes

`DvTableBase` / `IDvTable` let generic code treat hubs, links, and satellites uniformly. `IDvTable` extends Hop's `IGuiPosition`, `IBaseMeta`, and `IHasName` so DV tables are draggable nodes on the visual canvas.

### Embedded model configuration

Each `.hdv` file carries its own settings (edited via **Edit model** on the toolbar):

- Target database (one vault database per model)
- Hash algorithm: MD5 (default), SHA1, SHA256, SHA512
- Hash key data type: HEX (default), STRING, or BINARY (BINARY needs Hop 2.19.0+ for correct sorting; see [issue 7346](https://github.com/apache/hop/issues/7346))
- Trimming, casing, delimiter, null placeholder, hash content prefix/suffix
- Unknown and **invalid** sentinel record generation and values
- Standard column names (load date, record source, optional load end date)
- Target table batch size; optional folder and name prefixes for generated pipelines

![Edit Data Vault Model](docs/images/data-vault-model-dialog.png)

### Visual modeler (`.hdv` files)

- Hop file type plugin for **Data Vault Model** files (`.hdv`) with **Save As** support
- Graphical canvas: drag tables, lasso-select, copy/cut/paste/delete, snapshot **undo/redo**
- **Context menus with icon actions** (left-click) — no right-click or double-click; designed for web/tablet use
  - Click the **background** to add hubs, links, satellites, notes, paste, import sources, or edit model properties
  - Click a **table icon** (not the name) for Edit, Delete, or Show update pipeline
  - Click a **table name** to open its properties dialog directly
  - Click a **note** for Edit or Delete
- Drag relationships (middle-click or Shift+left) between hub↔satellite, hub↔link, and link↔satellite
- **Annotation notes** with types, resize handles, and link syntax (`[Label](TableName)` navigates in-model)
- Toolbar: Edit model, Import sources, **Check model**, **Generate DDL**, Debug, zoom
- Optional display of hash key names on table cards (Hop GUI configuration)

![Model canvas with notes](docs/images/data-vault-model-note-rendered.png)

### Model validation (Check model)

**Check model** (toolbar) and the Data Vault Update action share the same validation engine. Beyond structural checks (missing keys, broken references, duplicate names), the plugin validates **source-to-target field type compatibility** before load pipelines run:

- **Hub** business keys vs source columns (per record source)
- **Satellite** attributes, auto-attributes, and driving keys vs source columns
- **Link** hub-key mappings vs source columns and hub business key definitions

**Detailed data type checking** (default in the GUI and in the update action) resolves types from the **live source schema** for database sources. **Fast** mode uses stored field metadata only (no database round-trip). Mismatched types and source lengths/precisions larger than the target are reported as errors; stored-metadata drift from the live schema is reported as a warning.

### Data Vault Update workflow action

The **`DATA_VAULT_UPDATE`** action reads a `.hdv` model and:

- Runs model checks (optional log / abort on failure; errors are written with `logError`)
- **Detailed data type checking** checkbox on the Model tab (default: enabled)
- Optionally ensures unknown and invalid sentinel rows in hubs and links
- Generates CREATE/ALTER DDL for the model's target database (execute, export to file, or fail if DDL needed)
- Generates update pipelines per table (and per hub record source where applicable)
- Stages pipelines and runs them via a **parallel orchestrator** (`parallelPipelineCopies`, optional staging folder)
- Executes pipelines using a selected pipeline run configuration
- Supports **`recordSourceGroup`** to load only sources whose `group` matches (empty = all sources)

![Data Vault Update action](docs/images/action-data-vault-update.png)

### Business Vault (`.hbv` files)

- Visual **Business Vault model editor** linked to a `.hdv` file
- **SCD2 tables** — functional timelines from DV satellite history (`valid_from` / `valid_to`)
- **Multi-satellite merge** — explicit field mappings (e.g. Customer 360 from four satellites)
- **PIT tables** — point-in-time helpers referencing hubs and satellites
- Toolbar: Edit model, Check model, Reload DV model, Debug (generate build pipelines)
- Navigate from BV to referenced DV tables in Hop GUI

![Customer 360 Business Vault model](docs/images/business-vault-model-customer-360.png)

### Business Vault Update workflow action

The **`BUSINESS_VAULT_UPDATE`** action reads a `.hbv` model and:

- Validates the BV model and linked DV model
- Optionally generates CREATE TABLE DDL on the BV target database
- Generates and runs SCD2 build pipelines (parallel orchestration, optional catalog publish)

### Integration modes (hybrid warehouses)

Per hub, link, or satellite:

- **Hop managed** — generated DDL and update pipelines (default)
- **External read-only** — table loaded elsewhere (dbt, SQL); Hop documents for BV; canvas shows `(ext)`
- **Custom pipelines** — Hop orchestrates your `.hpl` files; canvas shows `(custom)`

### AI Help

**AI Help** on the Data Vault model toolbar provides LLM-assisted source analysis, modeling proposals, and troubleshooting with review-before-apply. See [`docs/ai-advisory.md`](docs/ai-advisory.md).

### Search

- **`DataVaultModelSearchAnalyser`** indexes Data Vault models for Hop's metadata search (name, description, configuration, tables, and properties).

### Hop GUI options

Under **Configuration → Data Vault 2.0** (`DataVaultConfigOptionPlugin`):

- **Draw hash keys in model** — show hash key column names on table cards
- **Maximum undo/redo operations kept in memory** — snapshot undo stack size for `.hdv` files (default 200)

![Hop GUI Data Vault options](docs/images/hopgui-configuration-perspecive-data-vault-options.png)

## Documentation

Full index: **[docs/README.md](docs/README.md)**

| Audience | Document |
|----------|----------|
| Managers / architects | [`docs/presentations/hop-data-vault-overview.md`](docs/presentations/hop-data-vault-overview.md) |
| Modelers (tutorial) | [`docs/getting-started-modeler.adoc`](docs/getting-started-modeler.adoc) |

**Data Vault reference** (AsciiDoc under `docs/`):

| Document | Topic |
|----------|--------|
| [`docs/datavault-plugin.adoc`](docs/datavault-plugin.adoc) | Plugin overview, visual editor, workflows |
| [`docs/datavault-configuration.adoc`](docs/datavault-configuration.adoc) | Embedded `.hdv` configuration |
| [`docs/dv-integration-modes.adoc`](docs/dv-integration-modes.adoc) | Hop managed / external / custom pipelines |
| [`docs/dv-hub.adoc`](docs/dv-hub.adoc) / [`dv-link.adoc`](docs/dv-link.adoc) / [`dv-satellite.adoc`](docs/dv-satellite.adoc) | Table metadata |
| [`docs/datavault-update-action.adoc`](docs/datavault-update-action.adoc) | Data Vault Update action |

**Business Vault reference:**

| Document | Topic |
|----------|--------|
| [`docs/business-vault-overview.adoc`](docs/business-vault-overview.adoc) | `.hbv` modeler and table types |
| [`docs/business-vault-scd2.adoc`](docs/business-vault-scd2.adoc) | SCD2 and multi-satellite merge |
| [`docs/business-vault-configuration.adoc`](docs/business-vault-configuration.adoc) | Embedded `.hbv` configuration |
| [`docs/business-vault-update-action.adoc`](docs/business-vault-update-action.adoc) | Business Vault Update action |

Also: [`docs/ai-advisory.md`](docs/ai-advisory.md), [`docs/datavault-source.adoc`](docs/datavault-source.adoc), [`docs/datavault-source-database.adoc`](docs/datavault-source-database.adoc).

Screenshots are in [`docs/images/`](docs/images/). The sample project guide is **[project/PROJECT.md](project/PROJECT.md)**.

## Sample project

The `project/` folder is a self-contained Hop project with metadata, datasets, source CSVs, models, pipelines, and workflows. See **[project/PROJECT.md](project/PROJECT.md)** for prerequisites, layout, test-suite details, and screenshots.

**Prerequisites:** register `project/` as a Hop project named `hop-data-vault`, configure **`CRM`** and **`Vault`** database connections (PostgreSQL tested), and install the plugin.

**Run all tests** from the command line (adjust the Hop path in `project/run-tests.sh` if needed):

```bash
project/run-tests.sh
```

That runs `tests/run-tests.hwf`, which executes the full suite (basic vault1, multi-active satellite, link satellite, load end date, status tracking, multi-source hub, hash key tests, and multi-satellite Business Vault). To run one workflow:

```bash
project/run-tests.sh tests/load-end-date/update-load-end-date.hwf
```

## Building

```bash
mvn clean package
```

Artifacts:

- `target/hop-datavault-0.0.15-SNAPSHOT.jar`
- `target/hop-datavault-0.0.15-SNAPSHOT.zip` (ready-to-unzip plugin layout)

## Installation (external plugin)

1. Unzip the assembly zip into your Hop installation, or manually copy the jar to:
   ```
   $HOP_HOME/plugins/misc/datavault/hop-datavault-0.0.15-SNAPSHOT.jar
   ```
2. Restart Hop GUI.
3. New metadata types appear under **Metadata → Data Vault**. **Data Vault Update** and **Business Vault Update** actions are available in workflows. `.hdv` and `.hbv` files open in the visual modelers.

## Usage

1. Define **Data Vault Sources** for your staging / CRM tables (General tab for record source indicator and group; Database tab for connection and field layout).
2. Create a **Data Vault Model** (`.hdv`): add hubs, links, and satellites on the canvas, connect relationships, and add notes as needed.
3. Click **Edit model** to set the target database, hashing rules, sentinel records, and pipeline options.
4. Use **Check model**, **Generate DDL**, or **Debug** on the toolbar to validate and inspect before production loads. Use the canvas or table **context menus** (icon actions) to add and edit objects.
5. Add a **Data Vault Update** action to a workflow, point it at the `.hdv` file, and run.
6. Optionally create a **Business Vault model** (`.hbv`), link it to the `.hdv`, define SCD2 tables, and run **Business Vault Update**.

For multi-active satellites, set **`drivingKey`** (vault column) and **`drivingKeySourceField`** (source column). For scheduled partial loads, tag sources with **`group`** and set **`recordSourceGroup`** on the update action.

For load end dating, enable **`useLoadEndDate`** in the model configuration and set **`loadEndDateField`** (e.g. `x_load_end_ts`). Current satellite rows are those where the end-date column is null:

```sql
SELECT * FROM sat_customer WHERE x_load_end_ts IS NULL
```

## Common Data Vault 2.0 options included

- Hashing: MD5 / SHA1 / SHA256 / SHA512
- HEX (default), String, or Binary hash keys (Binary needs Hop 2.19.0+; see [issue 7346](https://github.com/apache/hop/issues/7346))
- Trimming + casing normalization
- Delimiter + null placeholder
- Unknown and invalid sentinel record handling
- Column naming conventions (load timestamp, record source)
- Hashdiff vs **load end date** satellite patterns
- Multi-active satellites via driving keys
- Record source groups for partial model updates

## Roadmap / ideas

- Dimensional modeler (Kimball-style from DV/BV metadata)
- Business Vault field dictionary (naming and typing rules)
- Marquez / OpenLineage lineage export
- Bridge tables and additional source types
- Richer list editing in metadata dialogs