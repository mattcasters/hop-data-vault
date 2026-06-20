# Issue #27 — Source-to-target data type validation

See the implementation plan in the session plan file. Summary of the approved approach:

- **When:** During model `check()` on each table — sources are already known on `DvHub` (multiple), `DvSatellite` (one), and `DvLink` (multiple). Not deferred to `generateUpdatePipelines()`.
- **What:** Validate Hub business keys, Satellite attributes, and Link hub-key mappings against source field types.
- **How:** New `DvFieldMappingValidationSupport` compares source `IValueMeta` to target metadata.
- **Modes:**
  - **Fast:** stored `SourceField` metadata only (no DB round-trip)
  - **Detailed:** live schema from database (parquet/avro/iceberg later)
- **Defaults:** GUI "Check model" always uses detailed checking. Data Vault Update action gets a "Detailed data type checking" checkbox (default enabled).

GitHub: https://github.com/mattcasters/hop-data-vault/issues/27