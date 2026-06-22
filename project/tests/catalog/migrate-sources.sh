#!/usr/bin/env bash
# One-time migration of legacy data-vault-source Hop metadata into catalog-data.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_HOME="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROJECT_HOME="${REPO_HOME}/project"
CP_FILE="${REPO_HOME}/target/migrate-sources-cp.txt"

cd "${REPO_HOME}"
mvn -q compile dependency:build-classpath -Dmdep.outputFile="${CP_FILE}"
java -cp "target/classes:$(cat "${CP_FILE}")" \
  org.apache.hop.datavault.catalog.DvLegacySourceMigrator "${PROJECT_HOME}" local-catalog