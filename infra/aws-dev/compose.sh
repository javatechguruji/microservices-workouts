#!/usr/bin/env bash
# PURPOSE: Run Docker Compose using the VM's generated, loopback-only configuration.
# RUN ON: The AWS VM, usually with sudo; installer/daily scripts call this for you.
# WHAT IT DOES: Locates compose.generated.json and forwards your Compose arguments.
# EDITS REQUIRED HERE: None. The generated file is created by setup.sh; do not edit
#   it manually. Repository docker-compose.yml is the source configuration.
# EXAMPLE ON VM: sudo bash infra/aws-dev/compose.sh logs --tail=100 keycloak
# NOTE: Arguments such as down -v can delete data; normal daily scripts use start/stop.

set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
[[ -f "$ROOT/infra/aws-dev/compose.generated.json" ]] || { echo 'Run setup.sh first.'; exit 1; }
exec docker compose --project-directory "$ROOT" -f "$ROOT/infra/aws-dev/compose.generated.json" "$@"
