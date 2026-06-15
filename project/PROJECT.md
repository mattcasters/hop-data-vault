# Sample Hop Data Vault Project

> **Prerequisites**
>
> - Configure the two database connections **`CRM`** and **`Vault`** in project metadata (`metadata/rdbms/CRM.json` and `metadata/rdbms/Vault.json`).
> - Testing has only been done with **PostgreSQL**.
> - Install the **hop-datavault** plugin (0.0.7-SNAPSHOT) in your Hop 2.18.0 environment.

This folder is a sample Hop project demonstrating the Data Vault 2.0 plugin: model-driven DDL, pipeline generation, initial and incremental loads, and golden-dataset unit tests.

## Project layout

```
project/
├── project-config.json          # Hop project settings (metadata, datasets, unit tests)
├── metadata/                    # RDBMS, DV config/sources, datasets, unit-test definitions
├── datasets/                    # Golden + source CSVs referenced by Hop datasets
├── files/
│   ├── basic/                   # CRM sample CSVs for vault1 (customer, order, product)
│   └── multi-active-satellite/  # customer_phone source CSVs
├── images/                      # Screenshots (model canvas, workflow runs)
└── tests/
    ├── basic/                   # vault1 model, loads, validation pipelines, update-vault1.hwf
    └── satellite-multi-active/  # customer-phone model, loads, update-customer-phone.hwf, run-tests.hwf
```

| Path | Purpose |
|------|---------|
| `metadata/data-vault-configuration/` | Shared hash / naming / satellite strategy (`vault-config`) |
| `metadata/data-vault-source/` | Logical record sources (with optional `group`) |
| `metadata/data-vault-source-database/` | CRM table + field mappings per source |
| `metadata/dataset/` | Hop dataset definitions pointing at `datasets/*.csv` |
| `metadata/unit-test/` | Pipeline unit test metadata |
| `tests/basic/vault1.hdv` | Classic hub / link / satellite model (customer, order, product) |
| `tests/satellite-multi-active/customer-phone.hdv` | Hub + multi-active satellite (`phone_type` driving key) |

`project-config.json` sets `metadataBaseFolder`, `dataSetsCsvFolder`, and `unitTestsBasePath` relative to `${PROJECT_HOME}`.

## Quick start: run all tests

Open this `project/` folder as a Hop project (so `${PROJECT_HOME}` resolves correctly), then run:

**`tests/satellite-multi-active/run-tests.hwf`**

This orchestrator executes, in order:

1. **`tests/basic/update-vault1.hwf`** — full vault1 initial + incremental load with validation
2. **`tests/satellite-multi-active/update-customer-phone.hwf`** — multi-active satellite initial + incremental load with validation

Both must succeed for the run to complete.

You can also run either child workflow on its own if you only need one scenario.

---

## Test suite: `tests/basic/` (vault1)

### Sample model

- **Model file:** `tests/basic/vault1.hdv`
- Open in Hop GUI to view the visual layout of hubs, links, and satellites.

![vault1 model](images/vault1.jpg)

### Workflow: `tests/basic/update-vault1.hwf`

End-to-end demonstration of the `vault1.hdv` model — **initial load** and **incremental update** in one run:

1. **Create CRM tables** — `customer`, `product`, `order` on the CRM connection.
2. **load1** — `tests/basic/load1.hpl` loads `files/basic/*_load1.csv` into CRM.
3. **Drop Vault tables** — clean slate for hub, satellite, and link tables.
4. **update vault1.hdv** — Data Vault Update action against `${PROJECT_HOME}/tests/basic/vault1.hdv`:
   - Generates update pipelines per table / record source (`CRM-customer`, `CRM-order`, `CRM-product`)
   - Creates or alters vault tables and performs insert-only loads (hash keys, satellite change detection)
5. **Test after initial load** — unit tests:
   - validate-hub-customer UNIT
   - validate-hub-order UNIT
   - validate-hub-product UNIT
   - validate-sat-customer UNIT
   - validate-sat-order UNIT
   - validate-sat-product UNIT
6. **load2** — `tests/basic/load2.hpl` loads `files/basic/*_load2.csv` (incremental batch).
7. **update vault1.hdv** (second run) — applies deltas only.
8. **Test after updates** — re-runs validation for the post-load2 state.

![Successful run of update-vault1.hwf](images/update-vault1-workflow.jpg)

Golden datasets live under `datasets/` (e.g. `hub-customer-golden`, `hub-customer-golden-load2`, `lnk-customer-order-golden-load2`). Validation pipelines are in `tests/basic/validate-*.hpl`.

---

## Test suite: `tests/satellite-multi-active/` (customer phone)

### Sample model

- **Model file:** `tests/satellite-multi-active/customer-phone.hdv`
- **Hub:** `hub_customer_phone` (business key `customer_id`)
- **Satellite:** `sat_customer_phone` with **driving key** `phone_type` / source field `phone_type`, attribute `phone_number`
- **Record source:** `CRM-customer-phone` → `customer_phone` table

Multi-active behavior: one satellite row per customer **and** phone type (e.g. MOBILE and HOME for the same customer).

### Workflow: `tests/satellite-multi-active/update-customer-phone.hwf`

1. **Create customer_phone table** on CRM.
2. **Drop Vault tables** — `hub_customer_phone`, `sat_customer_phone`.
3. **load-customer-phone1** — `load-customer-phone1.hpl` from `files/multi-active-satellite/customer_phone_load1.csv`.
4. **update customer-phone.hdv** — first Data Vault Update (DDL enabled, model checks on).
5. **Test after initial** — validate-hub-customer-phone UNIT, validate-sat-customer-phone UNIT.
6. **load-customer-phone2** — incremental batch from `customer_phone_load2.csv`.
7. **update customer-phone.hdv** — second update (delta load).
8. **Test after initial 2** — validate-hub-customer-phone2 UNIT (hub golden state after load2).

Golden datasets: `hub-customer-phone-golden1/2`, `sat-customer-phone-golden1/2` in `datasets/`.

### Orchestrator: `tests/satellite-multi-active/run-tests.hwf`

Chains `update-vault1.hwf` then `update-customer-phone.hwf`. Use this as the **single entry point** to exercise the full sample project.

---

## Notes

- Link tables (`lnk_*`) are created and loaded in the vault1 flow; dedicated link unit tests exist (`validate-lnk-customer-order UNIT`) and can be added to workflow test stages as needed.
- The Data Vault Update action supports **`recordSourceGroup`** on record sources tagged with **`group`** in metadata — useful for partial scheduled loads (not exercised in these sample workflows; all groups are empty).
- All connections, run configurations, sources, and unit tests are under `metadata/`.
- Source CSVs under `files/` are inputs to load pipelines; `datasets/` holds expected outputs for Hop unit tests.