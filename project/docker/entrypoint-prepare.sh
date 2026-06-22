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

DB_TYPE="${DB_TYPE:-postgres}"
PROFILE_DIR="/project/metadata/rdbms/profiles/${DB_TYPE}"

if [ ! -d "${PROFILE_DIR}" ]; then
  echo "Missing RDBMS profile directory: ${PROFILE_DIR}" >&2
  exit 1
fi

rm -f /project/metadata/rdbms/CRM.json /project/metadata/rdbms/Vault.json
cp "${PROFILE_DIR}/CRM.json" /project/metadata/rdbms/CRM.json
cp "${PROFILE_DIR}/Vault.json" /project/metadata/rdbms/Vault.json

mkdir -p "/project/metrics/${DB_TYPE}" /project/vault-catalog

if [ -n "${HOST_UID:-}" ] && [ -n "${HOST_GID:-}" ]; then
  chown "${HOST_UID}:${HOST_GID}" /project/metadata/rdbms/CRM.json /project/metadata/rdbms/Vault.json
  chown "${HOST_UID}:${HOST_GID}" "/project/metrics/${DB_TYPE}" /project/vault-catalog
fi

echo "Activated CRM/Vault RDBMS profiles for ${DB_TYPE}"