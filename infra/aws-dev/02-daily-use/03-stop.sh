#!/usr/bin/env bash
# PURPOSE: Stop your containers and EC2 for the night without deleting stored data.
# RUN ON: Your laptop after stopping local apps and closing the tunnel with Ctrl+C.
# COMMAND: bash infra/aws-dev/02-daily-use/03-stop.sh
# WHAT IT DOES: Calls daily.sh stop to stop Compose cleanly, stop EC2 and wait.
# EDITS REQUIRED HERE: None. Set AWS_PROFILE in your terminal; renew login if needed.
# FAILURE: If remote container shutdown fails, EC2 is not silently stopped anyway.
# COST: Compute stops; retained EBS storage remains billable. Automatic public IPv4 is released.
# LIMIT: Manages the existing Compose stack, not a future Minikube installation.

set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$BASE/daily.sh" stop "$@"
