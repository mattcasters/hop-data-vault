#!/usr/bin/env bash
# Smoke test for the Data Catalog SPI (Phase 1).
# Verifies CRUD round-trip via RecordDefinitionRegistry against local-catalog metadata.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_HOME="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROJECT_HOME="${REPO_HOME}/project"
CP_FILE="${REPO_HOME}/target/catalog-smoke-cp.txt"

echo "== Data Catalog smoke test =="
echo "Project home: ${PROJECT_HOME}"

cd "${REPO_HOME}"
mvn -q compile dependency:build-classpath -Dmdep.outputFile="${CP_FILE}"
java -cp "target/classes:$(cat "${CP_FILE}")" \
  org.apache.hop.catalog.smoke.CatalogRegistrySmoke "${PROJECT_HOME}"

echo "Smoke test completed successfully."