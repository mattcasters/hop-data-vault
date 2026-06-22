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

Apache Hop plugin for **Data Vault 2.0** modeling, validation, and model-driven loading. Version **0.0.8-SNAPSHOT** targets **Apache Hop 2.18.1** and **Java 21**.

The plugin provides Hop metadata types for hubs, links, satellites, and record sources; a visual **`.hdv` model editor** with embedded per-model configuration; a **Data Vault Update** workflow action that generates and runs load pipelines; and a sample Hop project under `project/` for end-to-end testing.

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

`DvTableBase` / `IDvTable` let generic code treat hubs, links, and satellites uniformly. `IDvTable` extends Hop's `IGuiPosition`, `IBaseMeta`, and `IHasName` so DV tables are draggable nodes on the visual canvas.

### Embedded model configuration

Each `.hdv` file carries its own settings (edited via **Edit model** on the toolbar):

- Target database (one vault database per model)
- Hash algorithm: MD5 (default), SHA1, SHA256, SHA512
- Hash key data type: BINARY (recommended), HEX, or STRING
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

### Search

- **`DataVaultModelSearchAnalyser`** indexes Data Vault models for Hop's metadata search (name, description, configuration, tables, and properties).

### Hop GUI options

Under **Configuration → Data Vault 2.0** (`DataVaultConfigOptionPlugin`):

- **Draw hash keys in model** — show hash key column names on table cards
- **Maximum undo/redo operations kept in memory** — snapshot undo stack size for `.hdv` files (default 200)

![Hop GUI Data Vault options](docs/images/hopgui-configuration-perspecive-data-vault-options.png)

## Documentation

AsciiDoc reference material lives under `docs/`:

| Document | Topic |
|----------|--------|
| [`docs/datavault-plugin.adoc`](docs/datavault-plugin.adoc) | Plugin overview, visual editor, workflow |
| [`docs/datavault-configuration.adoc`](docs/datavault-configuration.adoc) | Embedded model configuration (all dialog tabs) |
| [`docs/datavault-source.adoc`](docs/datavault-source.adoc) | Record sources (General tab) |
| [`docs/datavault-source-database.adoc`](docs/datavault-source-database.adoc) | Database tab (embedded in each source) |
| [`docs/dv-hub.adoc`](docs/dv-hub.adoc) / [`dv-link.adoc`](docs/dv-link.adoc) / [`dv-satellite.adoc`](docs/dv-satellite.adoc) | Table metadata |
| [`docs/datavault-update-action.adoc`](docs/datavault-update-action.adoc) | Workflow action (Model / DDL / Source tabs) |
| [`docs/issue-27-source-target-type-validation.md`](docs/issue-27-source-target-type-validation.md) | Source-to-target type validation (design summary) |

Screenshots for the model dialog tabs, notes, and the update action are in [`docs/images/`](docs/images/).

The sample project guide is **[project/PROJECT.md](project/PROJECT.md)** — models, workflows, unit tests, and screenshots.

## Sample project

The `project/` folder is a self-contained Hop project with metadata, datasets, source CSVs, models, pipelines, and workflows. See **[project/PROJECT.md](project/PROJECT.md)** for prerequisites, layout, test-suite details, and screenshots.

**Prerequisites:** register `project/` as a Hop project named `hop-data-vault`, configure **`CRM`** and **`Vault`** database connections (PostgreSQL tested), and install the plugin.

**Run all tests** from the command line (adjust the Hop path in `project/run-tests.sh` if needed):

```bash
project/run-tests.sh
```

That runs `tests/run-tests.hwf`, which executes four suites: basic vault1, multi-active satellite, link satellite, and load end date. To run one workflow:

```bash
project/run-tests.sh tests/load-end-date/update-load-end-date.hwf
```

## Building

```bash
mvn clean package
```

Artifacts:

- `target/hop-datavault-0.0.8-SNAPSHOT.jar`
- `target/hop-datavault-0.0.8-SNAPSHOT.zip` (ready-to-unzip plugin layout)

## Installation (external plugin)

1. Unzip the assembly zip into your Hop installation, or manually copy the jar to:
   ```
   $HOP_HOME/plugins/misc/datavault/hop-datavault-0.0.8-SNAPSHOT.jar
   ```
2. Restart Hop GUI.
3. New metadata types appear under **Metadata → Data Vault**. The **Data Vault Update** action is available in workflows. `.hdv` files open in the visual modeler.

## Usage

1. Define **Data Vault Sources** for your staging / CRM tables (General tab for record source indicator and group; Database tab for connection and field layout).
2. Create a **Data Vault Model** (`.hdv`): add hubs, links, and satellites on the canvas, connect relationships, and add notes as needed.
3. Click **Edit model** to set the target database, hashing rules, sentinel records, and pipeline options.
4. Use **Check model**, **Generate DDL**, or **Debug** on the toolbar to validate and inspect before production loads. Use the canvas or table **context menus** (icon actions) to add and edit objects.
5. Add a **Data Vault Update** action to a workflow, point it at the `.hdv` file, and run.

For multi-active satellites, set **`drivingKey`** (vault column) and **`drivingKeySourceField`** (source column). For scheduled partial loads, tag sources with **`group`** and set **`recordSourceGroup`** on the update action.

For load end dating, enable **`useLoadEndDate`** in the model configuration and set **`loadEndDateField`** (e.g. `x_load_end_ts`). Current satellite rows are those where the end-date column is null:

```sql
SELECT * FROM sat_customer WHERE x_load_end_ts IS NULL
```

## Common Data Vault 2.0 options included

- Hashing: MD5 / SHA1 / SHA256 / SHA512
- Binary, HEX, or String hash keys (BINARY recommended)
- Trimming + casing normalization
- Delimiter + null placeholder
- Unknown and invalid sentinel record handling
- Column naming conventions (load timestamp, record source)
- Hashdiff vs **load end date** satellite patterns
- Multi-active satellites via driving keys
- Record source groups for partial model updates

## Roadmap / ideas

- PIT tables, bridges, reference tables
- Additional source types beyond database tables
- Richer list editing in metadata dialogs