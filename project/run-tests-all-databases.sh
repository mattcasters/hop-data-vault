#!/bin/sh
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/docker"
RDBMS_METADATA_DIR="${SCRIPT_DIR}/metadata/rdbms"
BACKUP_DIR=""

DATABASES="${1:-postgres mysql singlestore}"
FAIL=0

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

reclaim_rdbms_connection_ownership() {
  for conn in CRM.json Vault.json; do
    path="${RDBMS_METADATA_DIR}/${conn}"
    if [ ! -e "${path}" ]; then
      continue
    fi
    chown "${HOST_UID}:${HOST_GID}" "${path}" 2>/dev/null \
      || sudo chown "${HOST_UID}:${HOST_GID}" "${path}" 2>/dev/null \
      || true
  done
}

METRICS_COMPOSE_FILE="${DOCKER_DIR}/compose.postgres.yml"
METRICS_OVERVIEW_CSV="${SCRIPT_DIR}/metrics/metrics-overview.csv"
COLLECT_METRICS_PIPELINE="/project/tests/shared/collect-metrics-results.hpl"

reclaim_metrics_folder_ownership() {
  db="${1:-}"
  compose_file="${2:-}"
  if [ -z "${db}" ]; then
    return 0
  fi
  metrics_dir="${SCRIPT_DIR}/metrics/${db}"
  if [ ! -d "${metrics_dir}" ]; then
    return 0
  fi
  if [ -n "${compose_file}" ]; then
    docker compose -f "${compose_file}" run --rm --no-deps --entrypoint chown hop \
      -R "${HOST_UID}:${HOST_GID}" "/project/metrics/${db}" >/dev/null 2>&1 || true
  fi
  chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || true
}

reclaim_metrics_tree_ownership() {
  metrics_dir="${SCRIPT_DIR}/metrics"
  if [ ! -d "${metrics_dir}" ]; then
    return 0
  fi
  if [ -f "${METRICS_COMPOSE_FILE}" ]; then
    docker compose -f "${METRICS_COMPOSE_FILE}" run --rm --no-deps --entrypoint chown hop \
      -R "${HOST_UID}:${HOST_GID}" /project/metrics >/dev/null 2>&1 || true
  fi
  chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || true
}

collect_metrics_overview() {
  if ! find "${SCRIPT_DIR}/metrics" -name '*.json' -print -quit 2>/dev/null | grep -q .; then
    echo "No metrics JSON files found; skipping metrics overview collection."
    return 0
  fi
  if [ ! -f "${METRICS_COMPOSE_FILE}" ]; then
    echo "Missing compose file for metrics collection: ${METRICS_COMPOSE_FILE}" >&2
    return 1
  fi

  echo "=== Collecting metrics overview ==="
  set +e
  docker compose -f "${METRICS_COMPOSE_FILE}" run --rm --no-deps \
    -e HOP_FILE_PATH="${COLLECT_METRICS_PIPELINE}" \
    -e HOP_RUN_PARAMETERS= \
    -e HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH= \
    hop
  COLLECT_EXIT=$?
  set -e
  reclaim_metrics_tree_ownership

  if [ "${COLLECT_EXIT}" -ne 0 ]; then
    echo "FAILED: metrics overview collection (exit code ${COLLECT_EXIT})" >&2
    return "${COLLECT_EXIT}"
  fi
  if [ ! -f "${METRICS_OVERVIEW_CSV}" ]; then
    echo "Metrics overview pipeline finished but ${METRICS_OVERVIEW_CSV} was not created." >&2
    return 1
  fi
  echo "Wrote ${METRICS_OVERVIEW_CSV}"
  return 0
}

print_metrics_overview_table() {
  if [ ! -f "${METRICS_OVERVIEW_CSV}" ]; then
    return 0
  fi

  echo ""
  echo "=== Metrics overview ==="
  python3 - "${METRICS_OVERVIEW_CSV}" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
rows = list(csv.reader(path.open(encoding="utf-8")))
if not rows:
    sys.exit(0)

widths = [max(len(row[i]) for row in rows) for i in range(len(rows[0]))]
for row in rows:
    print("  ".join(row[i].ljust(widths[i]) for i in range(len(row))))

data_rows = max(len(rows) - 1, 0)
print("")
print(f"Rows: {data_rows}  File: {path}")
PY
}

backup_rdbms_connections() {
  if [ -n "${BACKUP_DIR}" ]; then
    return 0
  fi
  for conn in CRM.json Vault.json; do
    if [ ! -f "${RDBMS_METADATA_DIR}/${conn}" ]; then
      echo "Missing RDBMS connection metadata: ${RDBMS_METADATA_DIR}/${conn}" >&2
      exit 1
    fi
  done
  BACKUP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hop-data-vault-rdbms-backup.XXXXXX")"
  cp "${RDBMS_METADATA_DIR}/CRM.json" "${BACKUP_DIR}/CRM.json"
  cp "${RDBMS_METADATA_DIR}/Vault.json" "${BACKUP_DIR}/Vault.json"
  echo "Backed up local CRM/Vault connections to ${BACKUP_DIR}"
}

restore_rdbms_connections() {
  if [ -z "${BACKUP_DIR}" ] || [ ! -d "${BACKUP_DIR}" ]; then
    return 0
  fi
  reclaim_rdbms_connection_ownership
  cp "${BACKUP_DIR}/CRM.json" "${RDBMS_METADATA_DIR}/CRM.json"
  cp "${BACKUP_DIR}/Vault.json" "${RDBMS_METADATA_DIR}/Vault.json"
  rm -rf "${BACKUP_DIR}"
  BACKUP_DIR=""
  echo "Restored local CRM/Vault connections"
}

cleanup() {
  if [ -n "${ACTIVE_COMPOSE:-}" ]; then
    docker compose -f "${ACTIVE_COMPOSE}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  restore_rdbms_connections
}

trap cleanup INT TERM EXIT

backup_rdbms_connections

for db in ${DATABASES}; do
  COMPOSE_FILE="${DOCKER_DIR}/compose.${db}.yml"
  if [ ! -f "${COMPOSE_FILE}" ]; then
    echo "Unknown database profile '${db}' (missing ${COMPOSE_FILE})" >&2
    FAIL=1
    continue
  fi

  echo "=== Running test suite against ${db} ==="
  ACTIVE_COMPOSE="${COMPOSE_FILE}"

  mkdir -p "${SCRIPT_DIR}/metrics/${db}"
  export METRICS_FOLDER="/project/metrics/${db}"

  docker compose -f "${COMPOSE_FILE}" build hop
  set +e
  docker compose -f "${COMPOSE_FILE}" up --abort-on-container-exit --exit-code-from hop hop
  EXIT_CODE=$?
  set -e

  docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans
  reclaim_rdbms_connection_ownership
  reclaim_metrics_folder_ownership "${db}" "${COMPOSE_FILE}"

  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo "FAILED: ${db} (exit code ${EXIT_CODE})" >&2
    FAIL=1
  else
    echo "PASSED: ${db}"
  fi
done

ACTIVE_COMPOSE=""
restore_rdbms_connections
trap - INT TERM EXIT

collect_metrics_overview || FAIL=1
print_metrics_overview_table

if [ "${FAIL}" -ne 0 ]; then
  echo "One or more database test runs failed." >&2
  exit 1
fi

echo "All database test runs passed."