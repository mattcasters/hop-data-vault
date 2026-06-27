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
# Run hop svg inside the project Docker image (apache/hop + hop-datavault plugin).
# Host paths in -f/-o/-s/-t/--project-home are translated to /workspace/... in the container.
#
# Examples (from project/):
#   ./run-svg.sh -f tests/multi-satellite-bv/customer-360.hdv \
#                -o ../docs/images/data-vault-model-customer-360.svg --no-notes
#
#   HOP_COMMAND_PARAMETERS="-f tests/multi-satellite-bv/customer-360.hbv \
#     -o ../docs/images/business-vault-model-customer-360.svg --no-notes" ./run-svg.sh
#
#   ./run-svg.sh -s tests/multi-satellite-bv -t ../docs/images/generated -r --no-notes
#
# Optional: HOP_IMAGE_VERSION=2.18.1 to rebuild against a specific apache/hop tag.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "${SCRIPT_DIR}/docker/hop-docker-lib.sh"

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

HOP_COMMAND="${HOP_COMMAND:-svg}"

if [ "$#" -gt 0 ]; then
  set -- "$@"
elif [ -n "${HOP_COMMAND_PARAMETERS:-}" ]; then
  # shellcheck disable=SC2086
  set -- ${HOP_COMMAND_PARAMETERS}
else
  echo "Usage: $0 [hop svg options]" >&2
  echo "  or:  HOP_COMMAND_PARAMETERS=\"-f <input> -o <output> [--no-notes]\" $0" >&2
  echo "" >&2
  echo "Path options (-f, -o, -s, -t, --project-home) may be host paths relative to the" >&2
  echo "current directory, paths under project/, or paths under the repository root." >&2
  exit 1
fi

HOST_PARAMS="$*"
OUTPUT_PATHS="$(collect_svg_output_paths "$@")"
CONTAINER_PARAMS="$(translate_svg_command_parameters "$@")" || exit 1

echo "=== Running hop ${HOP_COMMAND} in Docker (${HOP_IMAGE_NAME}) ==="
echo "Host parameters:      ${HOST_PARAMS}"
echo "Container parameters: ${CONTAINER_PARAMS}"

EXIT_CODE=0
run_hop_docker_command "${HOP_SVG_COMPOSE_FILE}" "${HOP_COMMAND}" "${CONTAINER_PARAMS}" \
  || EXIT_CODE=$?

# Restore ownership on written outputs (container runs as root).
for output_path in ${OUTPUT_PATHS}; do
  reclaim_path_ownership "${output_path}"
done

exit "${EXIT_CODE}"