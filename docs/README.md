<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Hop Data Vault documentation index

Documentation by audience.

## Managers and architects

| Document | Description |
|----------|-------------|
| [presentations/hop-data-vault-overview.md](presentations/hop-data-vault-overview.md) | High-level slide deck: goals, architecture, hybrid warehouses, roadmap |

## Modelers (zero to hero)

| Document | Description |
|----------|-------------|
| [getting-started-modeler.adoc](getting-started-modeler.adoc) | Hands-on tutorial: DV → BV SCD2 → external tables using `project/` |

## Data Vault reference (AsciiDoc)

| Document | Description |
|----------|-------------|
| [datavault-plugin.adoc](datavault-plugin.adoc) | Plugin overview, visual editor, workflows |
| [datavault-configuration.adoc](datavault-configuration.adoc) | Embedded `.hdv` configuration |
| [datavault-source.adoc](datavault-source.adoc) | Record sources |
| [datavault-source-database.adoc](datavault-source-database.adoc) | Database source tab |
| [dv-hub.adoc](dv-hub.adoc) | Hub metadata |
| [dv-link.adoc](dv-link.adoc) | Link metadata |
| [dv-satellite.adoc](dv-satellite.adoc) | Satellite metadata |
| [dv-integration-modes.adoc](dv-integration-modes.adoc) | Hop managed / external / custom pipelines |
| [datavault-update-action.adoc](datavault-update-action.adoc) | Data Vault Update workflow action |

## Business Vault reference (AsciiDoc)

| Document | Description |
|----------|-------------|
| [business-vault-overview.adoc](business-vault-overview.adoc) | `.hbv` modeler and table types |
| [business-vault-scd2.adoc](business-vault-scd2.adoc) | SCD2 generation, multi-satellite mappings |
| [business-vault-configuration.adoc](business-vault-configuration.adoc) | Embedded `.hbv` configuration |
| [business-vault-update-action.adoc](business-vault-update-action.adoc) | Business Vault Update workflow action |

## AI and advanced topics

| Document | Description |
|----------|-------------|
| [ai-advisory.md](ai-advisory.md) | AI Help / advisor setup and usage |
| [performance-tuning.md](performance-tuning.md) | Sort memory and pipeline tuning notes |

## Command-line tools

### `hop svg`

Export pipelines (`.hpl`), workflows (`.hwf`), Data Vault models (`.hdv`), Business Vault models (`.hbv`), and dimensional models (`.hdm`) to SVG.

**Docker (no local Hop install):** from `project/`, use `run-svg.sh` (same `docker-hop:latest` image as the test runner):

```bash
cd project
./run-svg.sh -f tests/multi-satellite-bv/customer-360.hdv \
             -o ../docs/images/customer-360.svg --no-notes
```

**Local Hop** with the plugin installed:

```bash
hop svg -f project/tests/multi-satellite-bv/customer-360.hdv \
        -o docs/images/customer-360-generated.svg --no-notes
hop svg -s project/tests/multi-satellite-bv -t /tmp/svg-out -r
hop svg -f project/tests/basic/load1.hpl -o /tmp/load1.svg
```

Options: `--no-notes`, `--magnification`, `--show-hash-keys` (`.hdv` only), `--project-home`.

## Sample project

| Document | Description |
|----------|-------------|
| [../project/PROJECT.md](../project/PROJECT.md) | Test suites, Docker runner, prerequisites |

## Design notes (in progress)

Internal planning documents for features not yet fully productized:

| Document | Topic |
|----------|-------|
| [bv-field-dictionary-plan.md](bv-field-dictionary-plan.md) | BV field dictionary |
| [dimensional-modeler-plan.md](dimensional-modeler-plan.md) | Dimensional modeler |
| [marquez-lineage-plan.md](marquez-lineage-plan.md) | Lineage export |

Screenshots: [images/](images/) — includes Customer 360 DV/BV canvases and SCD2 table dialog tabs