#!/usr/bin/env bash
# PURPOSE: Check whether your existing EC2 VM is running, stopped or terminated.
# RUN ON: Your laptop whenever you want to inspect the VM state.
# COMMAND: bash infra/aws-dev/02-daily-use/04-status.sh
# WHAT IT DOES: Calls daily.sh status; reads Terraform outputs and queries AWS.
# EDITS REQUIRED HERE: None. Set AWS_PROFILE in your terminal.
# EFFECTS: Read-only. Does not start, stop or install anything; not a service health check.

set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$BASE/daily.sh" status "$@"
