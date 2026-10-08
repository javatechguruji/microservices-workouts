#!/usr/bin/env bash
# PURPOSE: Initialize and verify the shared infrastructure on the AWS VM.
# RUN ON: The VM as root; setup-vm.sh runs this automatically after Docker installation.
# WHAT IT DOES: Generates/reuses admin passwords, prepares private Compose settings,
#   creates volumes/databases, pulls images, starts services, reconciles Keycloak
#   users/clients, creates Kafka topics and checks readiness.
# EDITS REQUIRED HERE: None for normal fresh setup. For a backup restore, supply the
#   existing console passwords in admin.env as described in the restore guide.
# DATA: Reuses existing volumes and human passwords but reapplies seeded client
#   configuration. Inserts repeatable demo history; does not restore backups.
# WHEN: Initial setup or deliberate updates; use daily start/stop for normal work.
# LIMIT: Does not install Minikube or deploy Java services.

set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo bash infra/aws-dev/setup.sh'; exit 1; }
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"
command -v python3 >/dev/null
docker info >/dev/null
docker compose version
umask 077
SECRETS="$ROOT/infra/aws-dev/admin.env"
if [[ ! -f "$SECRETS" ]]; then
  python3 - <<'PY' > "$SECRETS"
import secrets
for name in ('KEYCLOAK_ADMIN_PASSWORD', 'GRAFANA_ADMIN_PASSWORD'):
    print(name + '=' + secrets.token_hex(24))
PY
fi
set -a
source "$SECRETS"
set +a
python3 infra/aws-dev/render-compose.py
compose() { bash infra/aws-dev/compose.sh "$@"; }
retry() {
  local label=$1 attempt output
  local attempts=${READY_ATTEMPTS:-90}
  shift
  echo "Waiting for $label..."
  for ((attempt=1; attempt<=attempts; attempt++)); do
    if output=$("$@" 2>&1); then
      echo "$label: ready"
      return 0
    fi
    if (( attempt == 1 || attempt % 6 == 0 )); then
      echo "$label: still starting (check $attempt/$attempts)."
    fi
    if (( attempt < attempts )); then sleep 5; fi
  done
  echo "Timed out waiting for $label. Last check: $output" >&2
  compose ps -a >&2 || true
  echo 'Inspect service logs with: sudo bash infra/aws-dev/compose.sh logs --tail=100 SERVICE' >&2
  return 1
}
docker volume create workouts-postgres-data >/dev/null
docker volume create workouts-kafka-data >/dev/null
compose config --quiet
compose pull
compose up -d postgres
retry PostgreSQL docker exec postgres pg_isready -U postgres -d postgres
python3 docker/postgres/ensure-databases.py
compose up -d
READY_ATTEMPTS=180 retry "Keycloak realm import (first startup can take several minutes)" curl --connect-timeout 3 --max-time 10 -fsS -o /dev/null http://localhost:8180/realms/ecommerce/.well-known/openid-configuration
python3 docker/keycloak/configure-security.py
python3 docker/keycloak/verify-clients.py
python3 docker/keycloak/verify-learning-users.py
python3 docker/postgres/seed-demo-data.py
retry Kafka docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
for topic in payment-completed commerce-order-events; do
  docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 \
    --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1
done
retry Redis docker exec redis redis-cli ping
for url in http://localhost:3000/api/health http://localhost:9090/-/ready \
  http://localhost:9093/-/ready http://localhost:3100/ready \
  http://localhost:3200/ready http://localhost:13133/ http://localhost:8089/; do
  retry "$url" curl --connect-timeout 3 --max-time 10 -fsS -o /dev/null "$url"
done
compose ps
echo 'Scope: infrastructure and demo data; Java applications/Minikube are not deployed by this installer.'
echo 'Admin passwords: sudo cat infra/aws-dev/admin.env. Start the laptop tunnel next.'
echo 'Everything is ready! Infrastructure, Keycloak users/clients and sample database data verified.'
