# Satellite editor

A **Satellite** stores descriptive attributes that change over time for a **Hub** or **Link**. Loads are insert-only: new attribute values create new rows with a load timestamp.

## Default record source (required)

Select the **catalog DV_SOURCE** feed for this satellite. This is required even when the satellite reads the **same physical table** as its parent hub.

The dropdown lists **catalog entries** (logical feeds), not source columns. Configure the **value** written to the vault on the catalog source (source indicator or indicator field).

## General tab

- **Parent hub** or **Parent link** — Choose one, not both.
- **Default record source** — Catalog feed for this satellite's pipeline.
- **Driving key** / **Driving key source field** — For multi-active satellites (multiple current rows per parent key).

## Attributes tab

- List columns to historize, or leave empty to auto-include all non-key fields from the feed.
- **Include in CDC** — Uncheck columns that should not trigger new satellite rows when they change.
- **Load from source** — Populates attributes from the default record source.

## Status tracking tab

Optional status tracking satellite (STS) for full-snapshot feeds. Requires a record source and a resolved status table name.

## Multi-source hubs

When a hub has several record sources, use **one satellite per feed** (recommended), each with its own default record source, all pointing at the same parent hub.

Run **Check model** to catch a missing default record source or a feed not listed on the parent hub.