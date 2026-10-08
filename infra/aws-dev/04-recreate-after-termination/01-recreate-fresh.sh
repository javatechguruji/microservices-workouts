#!/usr/bin/env bash
# PURPOSE: Rebuild an already deleted VM with fresh infrastructure and seed data.
# RUN ON: Your laptop, retaining the original Terraform state, settings and SSH key.
# COMMAND: bash infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh
# WHAT IT DOES: Calls setup-vm.sh to reconcile Terraform resources, create the missing
#   VM, install Docker and initialize the infrastructure. Review the Terraform plan.
# WHY A HELPER: Fresh recreation uses the same installer as first-day setup.
# EDITS REQUIRED HERE: None. Review terraform/terraform.tfvars and set AWS_PROFILE.
# DATA: Discards history lost with the terminated disk and recreates standard sample
#   customers/orders/payments. No database backup/restore is needed for this workflow.
# EFFECTS: Does not terminate a running VM. If the VM still exists, setup is reapplied.
#   New/recreated infrastructure incurs AWS charges.

set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$BASE/setup-vm.sh"  "$@"
