#!/usr/bin/env bash
# PURPOSE: Forward your laptop's localhost ports to infrastructure on the AWS VM.
# RUN ON: Your laptop; normally 02-daily-use/02-connect.sh calls this automatically.
# WHAT IT DOES: Opens an SSH session with local port forwarding and connection
#   keepalives. Runs in the foreground; Ctrl+C disconnects without stopping the VM.
# EDITS REQUIRED HERE: None. The daily helper refreshes ssh.generated.conf for you.
# OPTIONS: First argument is SSH host alias; optional second is SSH configuration path.
# LIMIT: Local ports must be free. This does not route remote HTTP calls to IntelliJ.

set -euo pipefail
HOST=${1:-workouts-dev}
args=()
# Optional generated SSH config from setup-vm.sh.
if [[ -n "${2:-}" ]]; then args+=(-F "$2"); fi
for port in 5432 6379 8180 9092 8089 4317 4318 3000 9090 9093 3100 3200 13133; do
  args+=(-L "127.0.0.1:$port:127.0.0.1:$port")
done
exec ssh -N -T -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 "${args[@]}" "$HOST"
