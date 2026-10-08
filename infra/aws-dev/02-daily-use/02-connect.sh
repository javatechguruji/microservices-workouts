#!/usr/bin/env bash
# PURPOSE: Open the SSH tunnel between your laptop and the AWS infrastructure.
# WHY: Microservices keep using localhost; the tunnel forwards traffic to AWS.
#   The VM's current public IP is discovered automatically—no edits in each service.
# RUN ON: Your laptop after the VM and containers have started.
# COMMAND: bash infra/aws-dev/02-daily-use/02-connect.sh
# WHAT IT DOES: Calls daily.sh connect to refresh the VM address and forward local
#   database, Kafka, Keycloak and dashboard ports. Leave this terminal open.
# EDITS REQUIRED HERE: None. Set AWS_PROFILE in your terminal. Stop the old local
#   infrastructure first so its ports do not conflict with the tunnel.
# FINISH: Press Ctrl+C to close the tunnel. This does not stop EC2.
# LIMIT: This tunnel does not configure Minikube-to-IntelliJ HTTP traffic routing.

set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$BASE/daily.sh" connect "$@"
