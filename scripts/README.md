# Shared scripts

Docker runners and retail data generators used by `integration-tests/` and `retail-example/`.

## Runners

```bash
./scripts/run-postgres.sh up
./scripts/run-hop.sh <hop-project-dir> <workflow>
```

| Project folder | Example |
|----------------|---------|
| `integration-tests` | `./scripts/run-hop.sh integration-tests tests/run-tests.hwf` |
| `retail-example` | `./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf` |

Thin wrappers also exist in `integration-tests/run-tests.sh` and `integration-tests/run-postgres.sh`.

## Retail data generators

`scripts/end-to-end/` — Python helpers invoked from `retail-example` workflows:

- `generate-retail-data.py` — initial snapshot or period update CSVs
- `activate-source-wave.py` — switch catalog file masks
- `generate-catalog-sources.py` — (re)create E2E catalog JSON entries