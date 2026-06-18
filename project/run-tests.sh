#!/bin/sh
set -e

WORKFLOW_FILE="${1:-tests/run-tests.hwf}"

cd /home/matt/git/mattcasters/hop/assemblies/client/target/hop
sh hop run -j hop-data-vault -f "$WORKFLOW_FILE" -r local