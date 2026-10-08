#!/usr/bin/env bash
# PURPOSE: One-time creation of an Identity Center user and EC2/VPC permission set.
# BEFORE RUNNING: Read docs/infra-setup/01b-aws-sso-user-setup.md, including Console prerequisites.
# RUN ON: Your laptop, using your current administrator profile; not the new SSO profile.
# EDITS: Set the five environment variables shown below to your actual values.
#   export AWS_PROFILE=workouts-console
#   export WORKOUTS_SSO_REGION=us-east-1
#   export WORKOUTS_EC2_REGION=us-east-1
#   export WORKOUTS_SSO_EMAIL=javatechguruji@gmail.com
#   export WORKOUTS_SSO_USERNAME=techguru-dev
# ACTIONS: Creates/reuses a user, creates/updates WorkoutsDevInfra, assigns it to
#   this AWS account and waits for provisioning. AWS creates the IAM role automatically.
# PERMISSIONS: Selected EC2/VPC actions anywhere in WORKOUTS_EC2_REGION, not only
#   project-tagged resources. No IAM administration, PassRole, S3, EKS or billing access.
# FINISH IN CONSOLE: Set/reset the new user's password and require/register MFA.
# Does not create long-term access keys, enable Organizations, or change existing AWS login profiles.
set -euo pipefail
BASE=$(cd "$(dirname "$0")" && pwd)
exec python3 "$BASE/create-sso-user.py"
