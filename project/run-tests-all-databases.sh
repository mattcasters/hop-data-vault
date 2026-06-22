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
#

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "${SCRIPT_DIR}/docker/hop-docker-lib.sh"

RDBMS_METADATA_DIR="${SCRIPT_DIR}/metadata/rdbms"
BACKUP_DIR=""

DATABASES="${1:-postgres mysql singlestore}"
FAIL=0
ACTIVE_COMPOSE=""

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

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
  if [ -n "${ACTIVE_COMPOSE}" ]; then
    docker compose -f "${ACTIVE_COMPOSE}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  restore_rdbms_connections
}

trap cleanup INT TERM EXIT

ensure_hop_image "${HOP_COMPOSE_FILE}"
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

  echo "Clearing previous metrics JSON in metrics/${db}/"
  clear_metrics_json_files_for_db "${db}"
  export METRICS_FOLDER="/project/metrics/${db}"

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