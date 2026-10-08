#!/usr/bin/env bash
# PURPOSE: Your single first-day command to create the VM and install infrastructure.
# RUN ON: Your laptop, from the repository root.
# COMMAND: bash infra/aws-dev/01-first-day/01-create-and-install.sh
#
# BEFORE RUNNING — PREREQUISITES AND READING ORDER:
# All document/file paths below are relative to the repository root.
#
# 1. Read docs/infra-setup/01-aws-cli-login.md
#    Title: 01. Prepare AWS access — first-time setup
#    Complete the initial administrator login, then follow
#    docs/infra-setup/01b-aws-sso-user-setup.md to create your development SSO login.
#    Covers choosing AWS CLI 2.32.0+, browser sign-in/MFA, the optional MCP prompt,
#    password/MFA, profile creation and region configuration (us-east-1).
#    You need an AWS account and permission to create EC2/network/storage resources.
#    You do NOT need to create a VM, VPC, subnet or SSH key manually in AWS Console.
#
# 2. Read docs/infra-setup/02-aws-dev-vm.md
#    Title: 02. AWS development environment: simple steps
#    Complete section 1, steps A-C BEFORE running this script (this script is step D).
#    A: Install Terraform, AWS CLI, Python 3 and ensure OpenSSH is available.
#    B: Complete login using guide 01 and select the correct AWS profile.
#    C: Copy infra/aws-dev/terraform/terraform.tfvars.example to
#       infra/aws-dev/terraform/terraform.tfvars ONLY if it does not already exist.
#       Edit aws_region, name, ssh_cidr, instance_type, disk_gib and cpu_credits.
#       Find your current public IPv4: curl -4 https://checkip.amazonaws.com
#       Example syntax: ssh_cidr = "YOUR_PUBLIC_IPV4/32" (replace the placeholder).
#
# 3. In the SAME terminal, select and verify the profile you actually configured:
#      aws configure list-profiles
#      export AWS_PROFILE=workouts-dev
#      aws sso login --profile workouts-dev
#      aws sts get-caller-identity
#    Use workouts-dev for all VM lifecycle scripts after completing SSO setup.
#    Check the reported Account is the one where you want to create this VM.
#    If login expired or region is missing, follow guide 01 before continuing.
#
# 4. Run this script from the repository root using the COMMAND above.
#    Review Terraform's plan and type yes. Leave the terminal open until you see
#    "Everything is ready!" and the shell prompt returns; setup includes verification.
#
# DAILY USE AFTER SETUP: Follow guide 02, section 2 (start and connect).
# AFTER TERMINATION: Use infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh.
#
# WHAT IT DOES: Calls ../setup-vm.sh, which creates AWS resources with Terraform,
#   uploads configuration, installs Docker, and initializes databases, Kafka,
#   Keycloak users/clients, dashboards and sample customers/orders/payments. Review Terraform's plan before typing yes.
# WHY IT CALLS A HELPER: First-day setup and fresh recovery share the same operations.
#   Keeping them in setup-vm.sh avoids two copies of the installation logic.
#   You do NOT need to run setup-vm.sh separately afterward.
#
# EDITS REQUIRED HERE: None.
#   Copy terraform/terraform.tfvars.example to terraform/terraform.tfvars and edit
#   your region, public IP /32, instance size, disk size and CPU-credit preference.
#   In your terminal, set AWS_PROFILE to your configured AWS profile name.
#   Do not put AWS credentials into scripts.
# NOTE: Creates billable resources. Installs infrastructure, not Minikube/apps.
#   Your workflow recreates sample data; no database backup or restore is required.

set -euo pipefail
BASE=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$BASE/setup-vm.sh"  "$@"
