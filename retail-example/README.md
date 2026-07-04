# Retail Example Hop Project

End-to-end retail data vault example demonstrating initial and incremental loads across Data Vault, Business Vault, and dimensional models.

![Retail 360 Data Vault model](../docs/images/data-vault-model-retail-example.png)

## Documentation

- [docs/getting-started-retail.adoc](../docs/getting-started-retail.adoc) — primary tutorial
- [docs/feature-overview.md](../docs/feature-overview.md) — all plugin capabilities
- [docs/README.md](../docs/README.md) — full documentation index

DV source catalog entries use namespace **`hop/retail-example/sources`**.

## Prerequisites

- Docker with Compose v2 (used by `../scripts/run-hop.sh`)
- Local PostgreSQL on port **54320** — start with `../scripts/run-postgres.sh`
- Python 3 (stdlib only) for data generation scripts

After upgrading to the two-database layout, reset the local Postgres volume once:

```sh
./scripts/run-postgres.sh reset
./scripts/run-postgres.sh up
```

## Database layout

Retail uses **two PostgreSQL databases** on the same local instance (port 54320):

| Database | Hop connection | Role |
|----------|----------------|------|
| `test_source` | **CRM** | E2E landing tables (source system) |
| `test_edw` | **Vault** | Data Vault, Business Vault, dimensional marts, load control, DM staging views |

Environment variables in `environments/local-docker-postgres.json`:

- `DB_SOURCE_NAME` → CRM (`test_source`)
- `DB_TARGET_NAME` → Vault (`test_edw`)

DV record sources are **database-backed** catalog entries (`physicalTable` on CRM). CSV files under `files/` are still generated for debugging; `load-e2e-sources-to-crm.hpl` loads them into CRM before each DV update.

## Project layout

```
retail-example/
├── project-config.json
├── environments/local-docker-postgres.json
├── metadata/                  # CRM, Vault, local-catalog, run configurations
├── catalog-data/              # E2E-* DV source catalog entries (DATABASE → CRM)
├── pipelines/                 # create-source-tables, load-e2e-sources-to-crm
├── files/                     # Generated CSV source files
├── models/
│   ├── retail-360.hdv         # Data Vault model (target: Vault)
│   ├── retail-360.hbv         # Business Vault model (target: Vault)
│   └── retail-warehouse.hdm   # Dimensional model (target: Vault)
├── sql/                       # drop-source / drop-target, load control, staging views
├── execution-maps/
│   ├── update-retail-dv-bv-dm.hem
│   └── simulate-6-months.hem
└── workflows/
    ├── run-retail-initial.hwf
    ├── run-retail-update.hwf
    └── simulate-6-months.hwf
```

Shared Python helpers live in `../scripts/end-to-end/` (repo root, not inside this project).

## Run instructions

From the repository root:

```sh
# Start PostgreSQL (once)
./scripts/run-postgres.sh up

# Initial load: drop CRM + EDW, create sources, generate data, load CRM, DV + BV + DM
./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf

# Incremental load: read control, generate update wave, load CRM, DV + BV + DM, advance control
./scripts/run-hop.sh retail-example workflows/run-retail-update.hwf
```

## Data generation

CSV files are written to `files/` by `../scripts/end-to-end/generate-retail-data.py`:

| Mode | Description |
|------|-------------|
| `initial` | Full snapshot wave (`*_initial.csv`) |
| `update` | Incremental wave for the current `retail_load_control.progress_date` |

Default scale: 10,000 customers, 1,000 products, 100,000 orders.

Each data generation run also regenerates `pipelines/load-e2e-sources-to-crm.hpl` with filenames for the current wave.