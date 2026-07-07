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