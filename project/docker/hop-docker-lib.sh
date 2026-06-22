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

# Shared helpers for running Apache Hop in the project Docker image.
# Source from run-tests.sh or run-tests-all-databases.sh after setting SCRIPT_DIR.

: "${SCRIPT_DIR:?SCRIPT_DIR must be set before sourcing hop-docker-lib.sh}"

DOCKER_DIR="${SCRIPT_DIR}/docker"
HOP_IMAGE_NAME="docker-hop:latest"
HOP_COMPOSE_FILE="${DOCKER_DIR}/compose.hop.yml"
METRICS_COMPOSE_FILE="${HOP_COMPOSE_FILE}"
METRICS_OVERVIEW_CSV="${SCRIPT_DIR}/metrics/metrics-overview.csv"
COLLECT_METRICS_PIPELINE="/project/tests/shared/collect-metrics-results.hpl"

workflow_arg_to_container_path() {
  workflow_arg="${1:-tests/run-tests.hwf}"
  case "${workflow_arg}" in
    /project/*)
      printf '%s\n' "${workflow_arg}"
      ;;
    /*)
      printf '/project%s\n' "${workflow_arg}"
      ;;
    *)
      printf '/project/%s\n' "${workflow_arg}"
      ;;
  esac
}

ensure_hop_image() {
  compose_file="${1:-${HOP_COMPOSE_FILE}}"
  if docker image inspect "${HOP_IMAGE_NAME}" >/dev/null 2>&1; then
    return 0
  fi
  echo "Building Hop docker image (${HOP_IMAGE_NAME})..."
  docker compose -f "${compose_file}" build hop
}

reclaim_rdbms_connection_ownership() {
  rdbms_metadata_dir="${SCRIPT_DIR}/metadata/rdbms"
  for conn in CRM.json Vault.json; do
    path="${rdbms_metadata_dir}/${conn}"
    if [ ! -e "${path}" ]; then
      continue
    fi
    chown "${HOST_UID}:${HOST_GID}" "${path}" 2>/dev/null \
      || sudo chown "${HOST_UID}:${HOST_GID}" "${path}" 2>/dev/null \
      || true
  done
}

clear_metrics_json_files_for_db() {
  db="${1:-}"
  if [ -z "${db}" ]; then
    return 0
  fi
  metrics_dir="${SCRIPT_DIR}/metrics/${db}"
  mkdir -p "${metrics_dir}"
  find "${metrics_dir}" -maxdepth 1 -name '*.json' -type f -delete 2>/dev/null || true
}

reclaim_metrics_folder_ownership() {
  db="${1:-}"
  compose_file="${2:-${HOP_COMPOSE_FILE}}"
  if [ -z "${db}" ]; then
    return 0
  fi
  metrics_dir="${SCRIPT_DIR}/metrics/${db}"
  if [ ! -d "${metrics_dir}" ]; then
    return 0
  fi
  docker compose -f "${compose_file}" run --rm --no-deps --entrypoint chown hop \
    -R "${HOST_UID}:${HOST_GID}" "/project/metrics/${db}" >/dev/null 2>&1 || true
  chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || true
}

reclaim_metrics_tree_ownership() {
  metrics_dir="${SCRIPT_DIR}/metrics"
  if [ ! -d "${metrics_dir}" ]; then
    return 0
  fi
  if [ -f "${HOP_COMPOSE_FILE}" ]; then
    docker compose -f "${HOP_COMPOSE_FILE}" run --rm --no-deps --entrypoint chown hop \
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
  rm -f "${METRICS_OVERVIEW_CSV}"
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
  overview_rows="$(wc -l < "${METRICS_OVERVIEW_CSV}" | tr -d ' ')"
  if [ "${overview_rows}" -le 1 ]; then
    echo "Metrics overview CSV has no data rows (only ${overview_rows} line(s))." >&2
    return 1
  fi
  echo "Wrote ${METRICS_OVERVIEW_CSV} (${overview_rows} lines)"
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

run_hop_docker_short_lived() {
  compose_file="${1:?compose file required}"
  hop_file_path="${2:?hop file path required}"
  metrics_folder="${3:-/project/metrics/local}"
  entrypoint_extension="${4:-}"

  ensure_hop_image "${compose_file}"

  set +e
  if [ -n "${entrypoint_extension}" ]; then
    docker compose -f "${compose_file}" run --rm \
      -e HOP_FILE_PATH="${hop_file_path}" \
      -e "HOP_RUN_PARAMETERS=METRICS_FOLDER=${metrics_folder}" \
      -e "HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH=${entrypoint_extension}" \
      hop
  else
    docker compose -f "${compose_file}" run --rm \
      -e HOP_FILE_PATH="${hop_file_path}" \
      -e "HOP_RUN_PARAMETERS=METRICS_FOLDER=${metrics_folder}" \
      -e HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH= \
      hop
  fi
  run_exit=$?
  set -e
  return "${run_exit}"
}