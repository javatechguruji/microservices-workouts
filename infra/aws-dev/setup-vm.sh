#!/usr/bin/env bash
# PURPOSE: Create the AWS development VM and install the shared infrastructure.
# RUN ON: Your laptop, from the repository root, for first setup or recreation.
# NORMAL ENTRY POINT: bash infra/aws-dev/01-first-day/01-create-and-install.sh
#
# WHAT THIS SCRIPT DOES:
#   1. Checks your tools and AWS identity; creates/reuses your local SSH key.
#   2. Runs Terraform init, validate and apply (you review the plan and type yes).
#   3. Waits for EC2, creates SSH configuration and waits for Ubuntu initialization.
#   4. Uploads Compose/configuration files without uploading private keys or state.
#   5. Calls install-docker.sh and setup.sh on the VM to install/start infrastructure,
#      create databases/topics, seed sample customers/orders, and verify users/clients/data.
# OPTION: --vm-only stops after the VM/SSH setup; it does not upload/install the stack.
#
# DO YOU NEED TO EDIT THIS SCRIPT? No, for the documented workflow.
#   Edit infra/aws-dev/terraform/terraform.tfvars instead: region, ssh_cidr, VM size,
#   disk size and CPU-credit mode. Copy terraform.tfvars.example first.
#   Select credentials in your terminal: export AWS_PROFILE=your-profile-name
#   Never put AWS passwords or access keys in this script.
#
# WHY ANOTHER SCRIPT CALLS THIS ONE:
#   The numbered scripts are easy-to-find entry points for first-day/recovery tasks.
#   They reuse this implementation so fixes apply to both workflows consistently.
#   Run ONE entry point; do not run this again after create-and-install finishes.
#
# EFFECTS: Creates billable AWS resources and pulls container images. Existing volumes
#   are reused; setup reconciles configuration. It does not restore deleted data.
#   It installs infrastructure only, not Minikube or your Java applications.

set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TF="$ROOT/infra/aws-dev/terraform"
KEY="$ROOT/infra/aws-dev/.ssh/id_ed25519"
MODE=${1:-all}
[[ $# -le 1 && ( "$MODE" == all || "$MODE" == --vm-only ) ]] || {
  echo 'Usage: bash infra/aws-dev/setup-vm.sh [--vm-only]'; exit 1;
}
for tool in terraform aws ssh ssh-keygen tar python3; do
  command -v "$tool" >/dev/null || { echo "Install $tool first; see 02-aws-dev-vm.md."; exit 1; }
done
[[ -f "$TF/terraform.tfvars" ]] || {
  echo 'Copy terraform/terraform.tfvars.example to terraform/terraform.tfvars and edit your values first.'; exit 1;
}
# Confirm which account your AWS_PROFILE/environment selects, before creating anything.
aws sts get-caller-identity
umask 077
mkdir -p "$(dirname "$KEY")"
# Never silently replace a missing key for an existing Terraform-managed VM.
if [[ ! -f "$KEY" ]]; then
  if [[ -f "$TF/terraform.tfstate" || -f "$KEY.pub" ]]; then
    echo 'Existing state/public key found but private key is missing. Restore your private-key backup.'; exit 1
  fi
  # Local development SSH key; the private key is never uploaded or stored in Terraform state.
  ssh-keygen -t ed25519 -N '' -f "$KEY" -C workouts-dev
fi
[[ -f "$KEY.pub" ]] || ssh-keygen -y -f "$KEY" > "$KEY.pub"
terraform -chdir="$TF" init
terraform -chdir="$TF" validate
# A region change must not orphan the resources recorded in the existing state.
DEPLOYED_REGION=$(terraform -chdir="$TF" output -json | python3 -c 'import json,sys; print(json.load(sys.stdin).get("aws_region", {}).get("value", ""))')
REQUESTED_REGION=$(printf 'var.aws_region\n' | terraform -chdir="$TF" console)
REQUESTED_REGION=${REQUESTED_REGION#\"}
REQUESTED_REGION=${REQUESTED_REGION%\"}
if [[ -n "$DEPLOYED_REGION" && "$DEPLOYED_REGION" != "$REQUESTED_REGION" ]]; then
  echo "Existing deployment region: $DEPLOYED_REGION; requested region: $REQUESTED_REGION."
  echo 'Setup stopped. Follow the region-change steps in docs/infra-setup/02-aws-dev-vm.md.'
  echo 'Keep the existing Terraform state and cleanup record until the old resources are removed.'
  exit 1
fi
# Terraform shows the resource changes and asks for yes before applying them.
terraform -chdir="$TF" apply
INSTANCE=$(terraform -chdir="$TF" output -raw instance_id)
REGION=$(terraform -chdir="$TF" output -raw aws_region)
# Wait for AWS health checks before attempting SSH.
aws ec2 wait instance-status-ok --region "$REGION" --instance-ids "$INSTANCE"
# Query the live address instead of relying on a cached Terraform output.
IP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
[[ -n "$IP" && "$IP" != None && "$IP" != null ]] || { echo 'VM has no public IPv4 address.'; exit 1; }
SSH_CONFIG="$ROOT/infra/aws-dev/ssh.generated.conf"
# A separate SSH config avoids editing your personal ~/.ssh/config.
cat > "$SSH_CONFIG" <<CONFIG
Host workouts-dev
    HostName $IP
    HostKeyAlias $INSTANCE
    User ubuntu
    IdentityFile "${KEY/#$HOME\//%d/}"
    IdentitiesOnly yes
    StrictHostKeyChecking accept-new
    ServerAliveInterval 30
    ServerAliveCountMax 3
CONFIG
# Retry connection while sshd finishes starting; changed host keys remain rejected.
ready=false
for attempt in {1..60}; do
  if ssh -F "$SSH_CONFIG" -o BatchMode=yes -o ConnectTimeout=5 workouts-dev true; then
    ready=true; break
  fi
  sleep 5
done
[[ "$ready" == true ]] || { echo 'SSH unavailable. Check ssh_cidr and instance console logs.'; exit 1; }
# Wait for Ubuntu's initial package/cloud-init work to finish before using apt.
ssh -F "$SSH_CONFIG" workouts-dev 'sudo cloud-init status --wait'
if [[ "$MODE" != --vm-only ]]; then
  # Upload only stack source files; exclude generated configuration, state and private keys.
  archive=$(mktemp -t workouts-upload.XXXXXX)
  trap 'rm -f "$archive"' EXIT
  python3 - "$ROOT" "$archive" <<'PY'
import pathlib, sys, tarfile
root = pathlib.Path(sys.argv[1])
with tarfile.open(sys.argv[2], 'w:gz') as archive:
    archive.add(root / 'docker-compose.yml', arcname='docker-compose.yml')
    archive.add(root / 'docker', arcname='docker')
    # Reuse application-owned schema/seed SQL without uploading builds or source code.
    for path in sorted(root.glob('*-service/src/main/resources/schema.sql')):
        archive.add(path, arcname=str(path.relative_to(root)))
    for path in (root / 'infra/aws-dev').iterdir():
        if path.is_file() and path.suffix in ('.sh', '.py'):
            archive.add(path, arcname=str(path.relative_to(root)))
PY
  ssh -F "$SSH_CONFIG" workouts-dev 'mkdir -p ~/microservices-workouts && tar -xzf - -C ~/microservices-workouts' < "$archive"
  # Reuse the existing install/initialization scripts, including Keycloak and database seeds.
  ssh -F "$SSH_CONFIG" workouts-dev 'cd ~/microservices-workouts && sudo bash infra/aws-dev/install-docker.sh && sudo bash infra/aws-dev/setup.sh'
fi
printf '\nVM ready. Connect with: ssh -F "%s" workouts-dev\n' "$SSH_CONFIG"
printf 'Tunnel: bash infra/aws-dev/tunnel.sh workouts-dev "%s"\n' "$SSH_CONFIG"

if [[ "$MODE" != --vm-only ]]; then
  echo 'Everything is ready! Infrastructure, Keycloak users/clients and sample database data verified.'
fi
