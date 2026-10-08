#!/usr/bin/env bash
# PURPOSE: Shared implementation for start, connect, stop and status commands.
# RUN ON: Your laptop; normally use the numbered scripts in 02-daily-use instead.
# WHAT IT DOES: Reads Terraform VM ID/region, queries AWS, refreshes SSH configuration,
#   and performs only the selected action. No Terraform apply or package installation.
# EDITS REQUIRED HERE: None. Set AWS_PROFILE in your terminal. If your public IP
#   changes, update terraform/terraform.tfvars ssh_cidr and apply that firewall change.
# OPTIONS: start | connect | stop | status
# DATA: Start/stop preserve stored volumes. No destroy/termination operation exists.
# LIMIT: Controls EC2 and Compose only; Minikube lifecycle is not implemented.

set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TF="$ROOT/infra/aws-dev/terraform"
CONFIG="$ROOT/infra/aws-dev/ssh.generated.conf"
KEY="$ROOT/infra/aws-dev/.ssh/id_ed25519"
ACTION=${1:-help}
case "$ACTION" in start|connect|stop|status) ;; *)
  echo 'Usage: bash infra/aws-dev/daily.sh {start|connect|stop|status}'
  exit 1
esac
for tool in aws terraform ssh; do
  command -v "$tool" >/dev/null || { echo "Missing command: $tool"; exit 1; }
done
# Read the existing deployment; never create or destroy resources during daily use.
INSTANCE=$(terraform -chdir="$TF" output -raw instance_id)
REGION=$(terraform -chdir="$TF" output -raw aws_region)
[[ -n "$INSTANCE" && -n "$REGION" ]] || { echo 'Run one-time setup first.'; exit 1; }
aws_ec2() { aws ec2 "$@" --region "$REGION"; }
STATE=$(aws_ec2 describe-instances --instance-ids "$INSTANCE" \
  --query 'Reservations[0].Instances[0].State.Name' --output text)
if [[ "$ACTION" == status ]]; then
  printf '%s: %s (%s)\n' "$INSTANCE" "$STATE" "$REGION"
  exit 0
fi
# Always query AWS: the automatic public IPv4 changes after stop/start.
refresh_ssh() {
  [[ -f "$KEY" ]] || { echo 'Restore your original private SSH key first.'; return 1; }
  local ip
  ip=$(aws_ec2 describe-instances --instance-ids "$INSTANCE" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
  [[ -n "$ip" && "$ip" != None && "$ip" != null ]] || { echo 'VM has no public IP.'; return 1; }
  umask 077
  cat > "$CONFIG" <<CONFIG
Host workouts-dev
    HostName $ip
    HostKeyAlias $INSTANCE
    User ubuntu
    IdentityFile "${KEY/#$HOME\//%d/}"
    IdentitiesOnly yes
    StrictHostKeyChecking accept-new
    ServerAliveInterval 30
    ServerAliveCountMax 3
CONFIG
}
wait_ssh() {
  local attempt
  for attempt in {1..60}; do
    if ssh -F "$CONFIG" -o BatchMode=yes -o ConnectTimeout=5 workouts-dev true; then return 0; fi
    sleep 5
  done
  echo 'SSH unavailable. Check your login, key and ssh_cidr setting.' >&2
  return 1
}
case "$ACTION" in
  start)
    case "$STATE" in
      stopping) aws_ec2 wait instance-stopped --instance-ids "$INSTANCE"; STATE=stopped ;;
      stopped|pending|running) ;;
      *) echo "Cannot start VM in state $STATE. See the guide's recreation steps."; exit 1 ;;
    esac
    if [[ "$STATE" == stopped ]]; then
      aws_ec2 start-instances --instance-ids "$INSTANCE" >/dev/null
    fi
    aws_ec2 wait instance-running --instance-ids "$INSTANCE"
    aws_ec2 wait instance-status-ok --instance-ids "$INSTANCE"
    refresh_ssh
    wait_ssh
    ssh -F "$CONFIG" workouts-dev 'cd ~/microservices-workouts && sudo bash infra/aws-dev/compose.sh start'
    echo 'Containers started; allow time for readiness. Next: bash infra/aws-dev/daily.sh connect'
    ;;
  connect)
    [[ "$STATE" == running ]] || { echo 'Run daily.sh start first.'; exit 1; }
    refresh_ssh
    echo 'Leave this terminal open. Ctrl+C closes the tunnel.'
    exec bash "$ROOT/infra/aws-dev/tunnel.sh" workouts-dev "$CONFIG"
    ;;
  stop)
    case "$STATE" in
      stopped) echo 'VM is already stopped.'; exit 0 ;;
      stopping) aws_ec2 wait instance-stopped --instance-ids "$INSTANCE"; exit 0 ;;
      running) ;;
      *) echo "Cannot stop cleanly in state $STATE. Wait for startup or inspect AWS."; exit 1 ;;
    esac
    refresh_ssh
    # Fail rather than silently skip a clean shutdown if SSH/Compose is unavailable.
    ssh -F "$CONFIG" -o BatchMode=yes -o ConnectTimeout=10 workouts-dev \
      'cd ~/microservices-workouts && sudo bash infra/aws-dev/compose.sh stop'
    aws_ec2 stop-instances --instance-ids "$INSTANCE" >/dev/null
    aws_ec2 wait instance-stopped --instance-ids "$INSTANCE"
    echo 'VM stopped; data retained. EBS storage charges continue; automatic public IPv4 is released.'
    echo 'Any legacy Elastic IP remains billable until released; see the migration steps in guide 02.'
    ;;
esac
