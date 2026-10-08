#!/usr/bin/env bash
# PURPOSE: Install Docker Engine, Compose and prerequisites on Ubuntu 24.04 AMD64.
# RUN ON: The AWS VM as root; setup-vm.sh calls this automatically over SSH.
# WHAT IT DOES: Checks the OS, configures Docker's apt repository, installs packages,
#   and enables/starts the Docker service. Does not install the infrastructure stack.
# EDITS REQUIRED HERE: None for the documented Ubuntu VM. VM settings belong in
#   terraform/terraform.tfvars. Do not run this Linux installer on your Mac.
# WHEN: First installation or an intentional upgrade, not every morning.

set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo bash infra/aws-dev/install-docker.sh'; exit 1; }
source /etc/os-release
[[ "$ID" == ubuntu && "$VERSION_ID" == 24.04 && $(uname -m) == x86_64 ]] || {
  echo 'This installer supports Ubuntu 24.04 x86_64.'; exit 1;
}
apt-get update
apt-get install -y ca-certificates curl python3
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
cat > /etc/apt/sources.list.d/docker.sources <<REPO
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
REPO
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
# Keep sudo explicit; membership in the docker group is equivalent to root access.
docker compose version
