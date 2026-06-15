# Hop Data Vault 2.0 Plugin

Apache Hop plugin for **Data Vault 2.0** modeling, validation, and model-driven loading. Version **0.0.7-SNAPSHOT** targets **Apache Hop 2.18.0** and **Java 21**.

The plugin provides Hop metadata types for logical DV models and physical/update configuration, a visual **`.hdv` model editor**, a **Data Vault Update** workflow action that generates and runs load pipelines, and a sample Hop project under `project/` for end-to-end testing.

## Features

### Metadata types

- **Data Vault Configuration** (`data-vault-configuration`)
  - Hash algorithm: MD5 (default), SHA1, SHA256, SHA512
  - Hash key data type: BINARY (recommended) or STRING (hex)
  - Trimming of business keys (on/off)
  - Hash content casing: UPPER (recommended), LOWER, NONE
  - Business key / concat delimiter (e.g. `||`)
  - Null placeholder for consistent hashing
  - Unknown / "ghost" record generation + value
  - Naming conventions: `_HK` suffixes, `LOAD_DATE`, `RECORD_SOURCE`, `_HASHDIFF`, etc.
  - Satellite patterns: hashdiff vs load-end-date

- **Data Vault Source** (`data-vault-source`, `data-vault-source-database`)
  - Record source definition with optional **`group`** label for partial model updates (e.g. `hourly`, `daily`)
  - Database sources with connection, table, and field mappings

- **Data Vault Hub** (`data-vault-hub`)
  - Table name, description, record sources, configuration reference
  - Business keys (composite supported) with source field mapping

- **Data Vault Link** (`data-vault-link`)
  - Participating hubs (by metadata name)
  - Driving keys (for role-playing / same hub multiple times)
  - Optional link satellite flag

- **Data Vault Satellite** (`data-vault-satellite`)
  - Attach to Hub **or** Link (via metadata reference)
  - Attributes with include-in-hashdiff flag
  - **Multi-active** satellites via **`drivingKey`** and **`drivingKeySourceField`** (one satellite row per hub key + driving key)

- **Data Vault Model** (`data-vault-model`)
  - Groups hubs, links, and satellites for one EDW / subject area
  - References a default Data Vault Configuration

`DvTableBase` / `IDvTable` let generic code treat Hubs, Links, and Satellites uniformly. `IDvTable` extends Hop's `IGuiPosition`, `IBaseMeta`, and `IHasName` so DV tables are draggable nodes on the visual modeling canvas.

### Visual modeler (`.hdv` files)

- Hop file type plugin for **Data Vault Model** files (`.hdv`)
- Graphical canvas to place and connect hubs, links, and satellites
- Model validation and editing dialogs for each table type

### Data Vault Update workflow action

The **`DATA_VAULT_UPDATE`** action reads a `.hdv` model and:

- Runs model checks (optional log / abort on failure)
- Optionally creates or alters target tables in the vault database (DDL generation)
- Generates update pipelines per hub, link, and satellite (and per record source where applicable)
- Executes those pipelines using a selected pipeline run configuration
- Supports optional **`recordSourceGroup`** to load only record sources whose `group` matches (empty = all sources)

### Search

- **`DataVaultModelSearchAnalyser`** indexes Data Vault models for Hop's metadata search (name, description, configuration, tables, and properties).

### Hop configuration option

- **`DataVaultConfigOptionPlugin`** exposes Data Vault-related settings in Hop's configuration system.

## Sample project

The `project/` folder is a self-contained Hop project with metadata, datasets, source CSVs, models, pipelines, and workflows. See **[project/PROJECT.md](project/PROJECT.md)** for prerequisites, layout, and workflow details.

**Quick start:** open the `project/` folder as a Hop project, configure the `CRM` and `Vault` database connections, then run:

```
project/tests/satellite-multi-active/run-tests.hwf
```

That orchestrator runs the basic `vault1` end-to-end test (`tests/basic/update-vault1.hwf`) and the multi-active satellite test (`tests/satellite-multi-active/update-customer-phone.hwf`).

## Building

```bash
mvn clean package
```

Artifacts:

- `target/hop-datavault-0.0.7-SNAPSHOT.jar`
- `target/hop-datavault-0.0.7-SNAPSHOT.zip` (ready-to-unzip plugin layout)

## Installation (external plugin)

1. Unzip the assembly zip into your Hop installation, or manually copy the jar to:
   ```
   $HOP_HOME/plugins/misc/datavault/hop-datavault-0.0.7-SNAPSHOT.jar
   ```
2. Restart Hop GUI.
3. New metadata types appear under **Metadata → Data Vault**. The **Data Vault Update** action is available in workflows. `.hdv` files open in the visual modeler.

## Usage

1. Create a **Data Vault Configuration** (recommended).
2. Define **Data Vault Sources** (and database source details) for your staging / CRM tables.
3. Create Hubs, Links, and Satellites (or build them on the `.hdv` canvas).
4. Assemble a **Data Vault Model** referencing those objects and the configuration.
5. Add a **Data Vault Update** action to a workflow, point it at the `.hdv` file, and run.

For multi-active satellites, set **`drivingKey`** (vault column name) and **`drivingKeySourceField`** (source column mapped into the driving key). For scheduled partial loads, tag record sources with **`group`** and set **`recordSourceGroup`** on the update action.

## Common Data Vault 2.0 options included

- Hashing: MD5 / SHA1 / SHA256 / SHA512
- Binary vs String hash keys (BINARY recommended)
- Trimming + casing normalization
- Delimiter + null placeholder
- Unknown record / ghost record handling
- Column naming conventions (LDTS / LOAD_DATE, RSRC, HASHDIFF, HK suffixes)
- Hashdiff vs end-dating satellite patterns
- Multi-active satellites via driving keys

## Roadmap / ideas

- PIT tables, bridges, reference tables
- Additional source types beyond database tables
- Richer list editing in metadata dialogs