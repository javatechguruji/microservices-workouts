#!/usr/bin/env bash
# PURPOSE: Resume your existing VM and start its existing infrastructure containers.
# RUN ON: Your laptop at the start of a workday; first-day setup must already exist.
# COMMAND: bash infra/aws-dev/02-daily-use/01-start.sh
# WHAT IT DOES: Calls daily.sh start to read the VM ID from Terraform state, start
#   EC2 if needed, wait for readiness, refresh SSH configuration and start Compose.
# EDITS REQUIRED HERE: None. Set AWS_PROFILE in your terminal; renew AWS login if needed.
# NEXT: Run 02-connect.sh. This script does not reinstall services or reset data.
# WHY A HELPER: daily.sh shares AWS/SSH handling across all daily commands.

set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$BASE/daily.sh" start "$@"
