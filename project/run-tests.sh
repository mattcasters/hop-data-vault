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

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

WORKFLOW_ARG="${1:-tests/run-tests.hwf}"
HOP_FILE_PATH="$(workflow_arg_to_container_path "${WORKFLOW_ARG}")"
COLLECT_METRICS="${COLLECT_METRICS:-Y}"

export METRICS_FOLDER="${METRICS_FOLDER:-/project/metrics/local}"
mkdir -p "${SCRIPT_DIR}/metrics/local"

echo "=== Running ${WORKFLOW_ARG} in Hop docker container ==="
echo "Environment: ${HOP_ENVIRONMENT_NAME:-local-docker-postgres} (${LOCAL_POSTGRES_ENV_FILE})"
echo "Database: ${LOCAL_POSTGRES_USER}@${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}/${LOCAL_POSTGRES_DB}"
require_local_postgres

EXIT_CODE=0
run_hop_docker_short_lived "${HOP_COMPOSE_FILE}" "${HOP_FILE_PATH}" "${METRICS_FOLDER}" \
  || EXIT_CODE=$?

reclaim_metrics_tree_ownership

if [ "${COLLECT_METRICS}" = "Y" ]; then
  collect_metrics_overview || EXIT_CODE=1
  print_metrics_overview_table
fi

exit "${EXIT_CODE}"