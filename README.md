# Hop Data Vault 2.0 Metadata Plugin

This plugin provides Hop Metadata types for capturing **Data Vault 2.0** logical models and the associated physical/update strategy configuration.

## Provided Metadata Types

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

- **Data Vault Hub** (`data-vault-hub`)
  - Extends `DvTableBase` / implements `IDvTable`
  - Table name, description, record source, configuration reference (from base)
  - List of Business Keys (composite supported)

- **Data Vault Link** (`data-vault-link`)
  - Extends `DvTableBase` / implements `IDvTable`
  - Participating hubs (by metadata name)
  - Driving keys (for role-playing / same hub multiple times)
  - Optional link satellite flag

- **Data Vault Satellite** (`data-vault-satellite`)
  - Extends `DvTableBase` / implements `IDvTable`
  - Attach to Hub **or** Link (via metadata reference)
  - List of attributes (with include-in-hashdiff flag)
  - Multi-active satellite support + driving key

A common abstract base `DvTableBase` and `IDvTable` interface were introduced so that generic code can treat Hubs, Links and Satellites uniformly.

`IDvTable` now extends Hop's `IGuiPosition`, `IBaseMeta` and `IHasName` so the DV table objects (Hubs, Links, Satellites) can be used directly as draggable/positionable nodes in a visual Data Vault modeling canvas (just like `TransformMeta` and `ActionMeta` are used in pipelines and workflows). The base class contains the `Point location` (persisted via `@HopMetadataProperty(inline=true)`) and `selected` flag plus all the required method implementations.

- **Data Vault Model** (`data-vault-model`)
  - Groups hubs + links + satellites for one EDW / subject area
  - References a default Data Vault Configuration (separate for strategy reuse)

All model objects can reference a Configuration (via `storeWithName`). Individual objects can override the default.

## @HopMetadataProperty + @GuiWidgetElement

The POJOs use standard Hop annotations so they:
- Serialize cleanly to JSON in the project `metadata/` folder
- Appear in the Metadata perspective
- Get auto-generated (or easily customized) dialogs via the widget annotations

Lists of sub-POJOs (Business Keys, Satellite Attributes) and reference lists are supported via `@HopMetadataProperty`. Because `GuiElementType` does not yet expose a `LIST` widget type, the collection fields currently rely on the metadata framework / future editor enhancements for rich editing (the scalar fields get nice widgets immediately).

## Building

```bash
mvn clean package
```

Artifacts:
- `target/hop-datavault-0.0.1-SNAPSHOT.jar`
- `target/hop-datavault-0.0.1-SNAPSHOT.zip` (ready-to-unzip plugin layout)

## Installation (external plugin)

1. Unzip the assembly zip into your Hop installation, or manually copy the jar to:
   ```
   $HOP_HOME/plugins/misc/datavault/hop-datavault-0.0.1-SNAPSHOT.jar
   ```
2. (Re)start Hop GUI.
3. The new metadata types should appear in the Metadata perspective under "Data Vault".

## Usage

1. Create a **Data Vault Configuration** first (recommended).
2. Create Hubs, Links, Satellites.
3. Create a **Data Vault Model** that references them + the configuration.
4. Later plugins / transforms / generators can read these via `IHopMetadataProvider` and the standard metadata input transform.

## Common Data Vault 2.0 options included (research-backed)

- Hashing: MD5 / SHA1 / SHA256 / SHA512 (AutomateDV, Scalefree, DV literature)
- Binary vs String hash keys (widely recommended to use BINARY)
- Trimming + casing normalization (standard best practice)
- Delimiter + null placeholder (AutomateDV `concat_string` / `null_placeholder_string`)
- Unknown record handling (very common "ghost record" / "unknown" hub pattern)
- Column naming conventions (LDTS / LOAD_DATE, RSRC, HASHDIFF, HK suffixes)
- Hashdiff vs end-dating satellite patterns

## Next steps / ideas for a full plugin

- Pipeline transforms or actions that generate staging → DV loading pipelines from a model + config
- DDL generation transform using the model
- Visual modeler perspective (see Hop issue #7077)
- Support for PIT tables, bridges, reference tables, etc.

This code was generated to be simple, clean POJOs following Hop metadata conventions as closely as possible.
