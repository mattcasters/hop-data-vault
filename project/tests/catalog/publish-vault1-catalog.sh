#!/usr/bin/env bash
# Smoke test: publish vault1.hdv target tables to the vault-catalog file catalog.
# Exercises the same catalog path as ActionDataVaultUpdate with "Update data catalog" enabled.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_HOME="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROJECT_HOME="${REPO_HOME}/project"
CP_FILE="${REPO_HOME}/target/catalog-publish-smoke-cp.txt"

echo "== Data Catalog publish smoke test (vault1) =="
echo "Project home: ${PROJECT_HOME}"

cd "${REPO_HOME}"
mvn -q compile dependency:build-classpath -Dmdep.outputFile="${CP_FILE}"
java -cp "target/classes:$(cat "${CP_FILE}")" \
  org.apache.hop.datavault.catalog.smoke.DvCatalogPublishSmoke "${PROJECT_HOME}"

echo "Publish smoke test completed successfully."