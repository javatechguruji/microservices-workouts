#!/usr/bin/env bash
# PURPOSE: Cleanly terminate the development VM before testing fresh recreation.
# RUN ON: Your laptop, after stopping local Java/React apps and closing the SSH tunnel.
# COMMAND: bash infra/aws-dev/03-terminate/01-terminate.sh
# WHAT IT DOES: Reads the VM ID/region from Terraform, shows your AWS identity,
#   asks you to type the VM ID, stops Compose/EC2 cleanly, terminates EC2, waits, then releases any recorded legacy Elastic IP.
# EDITS REQUIRED HERE: None. Set AWS_PROFILE to your working AWS profile first.
# DATA: Permanently deletes the VM/root disk and its Docker data. No backup is taken.
#   The network, local Terraform state and SSH key remain; the automatic public IPv4 is released with the VM.
# COST: Verified VM/disk/IP deletion ends their ongoing charges. Legacy EIPs are also checked.
# SAFETY: Aborts if clean shutdown fails. Terraform prevent_destroy does not block
#   this explicit AWS CLI termination. This is not the normal daily stop command.
# NEXT: bash infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh

# VERIFY WITHOUT DELETING: append --verify-only to print the current cleanup report.
# RETRIES: Completed steps are skipped; remaining disks/IP are checked and retried.
# The private cleanup-record.json preserves resource IDs; it contains no database data.
set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec python3 "$BASE/termination.py" "$@"
