# 02. AWS development environment: simple steps

[Reading order](README.md#aws-setup-reading-order) · [01 Login](01-aws-cli-login.md) · [02 Setup / daily use](02-aws-dev-vm.md)

**Run these commands on your laptop, from the repository root.**
All installation and daily-use scripts are in [infra/aws-dev](../../infra/aws-dev).

Open the linked script for the situation you are in. The helper scripts in the
parent folder are called automatically; you do not need to run those separately.

| Folder / situation | Scripts to run in order |
| --- | --- |
| **01-first-day** — new environment | [01 Create VM and install](../../infra/aws-dev/01-first-day/01-create-and-install.sh) |
| **02-daily-use** — normal workday | [01 Start/resume](../../infra/aws-dev/02-daily-use/01-start.sh) → [02 Connect](../../infra/aws-dev/02-daily-use/02-connect.sh) → work → [03 Stop](../../infra/aws-dev/02-daily-use/03-stop.sh) |
| **02-daily-use** — check anytime | [04 Status](../../infra/aws-dev/02-daily-use/04-status.sh) |
| **03-terminate** — intentionally delete VM/data | [01 Clean termination](../../infra/aws-dev/03-terminate/01-terminate.sh) |
| **04-recreate-after-termination** — deleted VM, fresh data | [01 Recreate and install](../../infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh) |

**Your chosen workflow is disposable:** recreate the VM and reload sample data.
No database backup or restore is needed. The recreation script does not terminate a running VM.

## 1. One-time setup

Already have the older deployment in another region? Complete the
[one-time region change](#moving-the-existing-deployment-to-us-east-1-once) before creating the new VM.

**A. Install the laptop tools if missing.** On macOS:

```bash
brew tap hashicorp/tap
brew install hashicorp/tap/terraform awscli python
```

**B. Complete account access once, in order.**

1. [01 — Prepare AWS access](01-aws-cli-login.md): initial administrator login.
2. [01b — Create your development SSO user](01b-aws-sso-user-setup.md): user,
   password/MFA, profile configuration and verification.

After completing both guides, use only your development profile for the VM scripts:

```bash
export AWS_PROFILE=workouts-dev
aws sso login --profile workouts-dev
aws sts get-caller-identity
```

Keep this terminal open and continue below. Repeat login when the session expires;
do not repeat user creation each day.

**C. Edit your settings once.** Each setting has a one-line comment.

```bash
test -f infra/aws-dev/terraform/terraform.tfvars || cp infra/aws-dev/terraform/terraform.tfvars.example infra/aws-dev/terraform/terraform.tfvars
curl -4 https://checkip.amazonaws.com
nano infra/aws-dev/terraform/terraform.tfvars
```

These settings are one-time setup. Keep this file after stop or termination. Only
update `ssh_cidr` if your laptop/home/VPN public IP changes; the VM IP is unrelated.

Settings template: [terraform.tfvars.example](../../infra/aws-dev/terraform/terraform.tfvars.example).

Set `ssh_cidr` to your public IP followed by `/32`. Keep `aws_region = "us-east-1"` and choose the VM size.
The current default is `t3.large` for infrastructure only. For your planned
Minikube plus Java services, budget 16 GiB initially, for example `t3.xlarge`,
and measure actual usage.

**D. Run [01-create-and-install.sh](../../infra/aws-dev/01-first-day/01-create-and-install.sh).**

```bash
bash infra/aws-dev/01-first-day/01-create-and-install.sh
```

Review Terraform's plan and type `yes`. The script automatically:

- Creates the VM, network, firewall, SSH key registration and an automatically assigned public IPv4.
- Installs Docker and starts the infrastructure Compose stack.
- Creates application tables and inserts sample customers, orders, payments and catalog data.
- Configures Keycloak users/clients, Kafka topics and dashboards.
- Verifies user logins, machine clients, sample records and infrastructure readiness.
- Prints **Everything is ready!** only after those checks pass.

You do not separately run `install-docker.sh`, `setup.sh` or the Keycloak scripts.
Save the generated console passwords privately:

```bash
ssh -F infra/aws-dev/ssh.generated.conf workouts-dev 'sudo cat ~/microservices-workouts/infra/aws-dev/admin.env'
```

The installer includes these repeatable fixtures:

| Data | Included |
| --- | --- |
| Keycloak users | `customer1`, `customer2`, `admin1`, `othercustomer`; each initial password matches the username |
| Keycloak clients | Nine service clients plus the `security-demo-ui` browser client |
| Customer profiles | Four, matching the users and their tenants |
| Orders and payments | Three delivered sample orders with successful payments and order items |
| Products / discounts / ratings | 10 products, 5 discounts, 6 rating summaries |
| Inventory | 10 stock rows and committed reservations for the sample orders |
| Customer history / notifications | Purchase categories and delivered-order inbox entries |

Orders belong to `customer1` and `customer2` in tenant `demo`. `othercustomer`
belongs to tenant `other`. Sample orders are historical fixtures, so they do not
queue payments or publish synthetic Kafka events when applications start.
Repeating setup does not duplicate records or deduct inventory again. Existing
human passwords and application records are preserved; verification reports an
error if demo passwords were changed instead of silently resetting them.
Sample rows are installed even before the Java services have been started.
The final readiness message covers infrastructure and demo data, not application
HTTP flows or the still-pending Minikube deployment.

Stop your old laptop infrastructure once with `docker compose stop` before
opening the AWS tunnel. Keep your local Terraform state, `.ssh` folder and settings;
they are needed to manage and reconnect to this VM.

## 2. Every day: start and connect

Run [01-start.sh](../../infra/aws-dev/02-daily-use/01-start.sh), then
[02-connect.sh](../../infra/aws-dev/02-daily-use/02-connect.sh).
In a new laptop terminal, select your AWS profile. See
[SSO login steps](01b-aws-sso-user-setup.md#6-verify-and-use-sso-for-all-development-commands) if needed:

```bash
export AWS_PROFILE=workouts-dev
aws sso login --profile workouts-dev

bash infra/aws-dev/02-daily-use/01-start.sh
bash infra/aws-dev/02-daily-use/02-connect.sh
```

[01-start.sh](../../infra/aws-dev/02-daily-use/01-start.sh) resumes EC2 if stopped, waits for it, refreshes the SSH address and starts
the existing containers. It does not reinstall anything or reset data.
[02-connect.sh](../../infra/aws-dev/02-daily-use/02-connect.sh) opens the tunnel; **leave that terminal open**.

Allow services time to start, then use your applications. For the currently
implemented local setup, Java services use their `local` profile and unchanged
`localhost` infrastructure addresses. Keycloak: http://localhost:8180/admin/.
Grafana: http://localhost:3000.

**No Terraform apply, copying files or installation is needed each morning.**
If the VM is already running, you can simply run `02-connect.sh`.

## 3. Every day: finish and stop

1. Stop your locally running Java services and React.
2. Press **Ctrl+C** in the tunnel terminal.
3. In that terminal, run [03-stop.sh](../../infra/aws-dev/02-daily-use/03-stop.sh):

   ```bash
   bash infra/aws-dev/02-daily-use/03-stop.sh
   ```

The helper stops the infrastructure containers cleanly, stops EC2 and waits for
it to stop. Your databases, users and messages remain on disk for tomorrow.
If SSH fails, fix connectivity and retry; the helper will not silently skip the
clean container shutdown.

**Stop is not delete.** Compute charges stop and AWS releases the automatic
public IPv4, ending its IP charge. EBS storage remains billable and preserves your
data. Next start gets a new public IP; start/connect query AWS and update SSH
configuration automatically. Local applications keep their `localhost` settings.
You do not need to terminate/reinstall each day. Any legacy Elastic IP must first
be released using the one-time migration steps below. Public IPv4 is billable
while the VM runs; it is not free.
See [AWS EBS billing](https://repost.aws/knowledge-center/ebs-charge-stopped-instance)
and [public IPv4 pricing](https://aws.amazon.com/vpc/pricing/).

## 4. Intentionally terminate the VM

For normal evenings, use the daily **stop** script. To test a completely fresh
installation, stop local apps and close your tunnel, then run:

```bash
export AWS_PROFILE=workouts-dev
bash infra/aws-dev/03-terminate/01-terminate.sh
```

The [termination script](../../infra/aws-dev/03-terminate/01-terminate.sh) shows the
AWS identity and VM ID. Type that exact instance ID to confirm. It cleanly stops
containers and EC2, terminates the VM, waits until termination finishes, and AWS releases its automatic public IPv4.
It aborts if clean shutdown fails. **All data on the VM's root disk is deleted;
no backup is taken.** The network and local Terraform state/key remain for reuse.
For older deployments, the script also releases any recorded legacy Elastic IP. If release fails, the
script reports failure; rerun it to finish cleanup even if the VM is already gone.
It refuses to release an IP that has been moved to another resource.

After cleanup, the script prints a service-by-service report:

```text
SUCCESS | EC2 i-... | TERMINATED — no ongoing instance compute charge
SUCCESS | Root EBS vol-... | DELETED — no ongoing volume storage charge
SUCCESS | Automatic public IPv4 | RELEASED with VM — no ongoing automatic IP charge
RETAINED | VPC, subnet, internet gateway, routing, security group, key registration
         No standalone hourly charge in this configuration
Cleanup verified! No ongoing EC2, root EBS or public IPv4 charges for these verified resources.
```

The report checks actual AWS resource status, including root disk deletion. If an
original root disk remains available after termination, the script deletes that
specific disk; it never deletes a disk attached to another resource.
If a step fails, it prints **INCOMPLETE/UNVERIFIED**, exits with an error, and does
not claim successful cleanup. Fix the error and rerun the same script: it skips
completed deletions and retries remaining work. The ignored local
`infra/aws-dev/cleanup-record.json` preserves the exact IDs for retry; it is not a
database backup. Keep it with your Terraform state until cleanup is verified.

To check cleanup again **without deleting anything**:

```bash
bash infra/aws-dev/03-terminate/01-terminate.sh --verify-only
```

This report covers only this deployment's EC2, root disks, automatic IPv4 and any recorded legacy Elastic IP. It does
not audit other resources, manually added disks/snapshots, billing commitments or
previously incurred charges. Existing billed usage does not disappear after cleanup.

This explicitly calls the AWS API; Terraform's `prevent_destroy` only protects
Terraform operations and does not block this command.

## 5. Recreate after termination

A terminated VM cannot be restarted. The script creates a **new VM**, installs
Docker and all infrastructure, creates Keycloak users/clients, reloads sample data,
and verifies the result. No database backup is required for your chosen workflow.
Your previous experiments are discarded; the standard demo records return.

Keep your local Terraform state, settings and SSH key. Then run:

```bash
export AWS_PROFILE=workouts-dev
bash infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh
```

Review Terraform's plan and type `yes`. It recreates the deleted VM with an automatic public IPv4
and updates SSH configuration automatically. Local microservices keep their
`localhost` settings. Wait for **Everything is ready!**, then:

```bash
bash infra/aws-dev/02-daily-use/02-connect.sh
```

The same command works after an intentional full Terraform teardown. Normal
Terraform destruction is guarded by `prevent_destroy`; see the detailed reference
only if you deliberately want to remove resources. For ordinary evenings, use
**stop**, which keeps your existing data. Do not delete the local Terraform state
or SSH key when deleting the VM. The new console admin passwords are generated
on the new VM; retrieve them using the command in section 1.

## 6. Your Minikube + one-local-service plan

The intended arrangement is:

- **AWS:** infrastructure plus Minikube running the microservices.
- **IntelliJ:** only the service you are editing.
- **Debug session:** route calls to the local service and pause its remote copy
  when necessary, including competing Kafka consumers/background jobs.

**The scripts currently install infrastructure only.** They do not yet install
Minikube, deploy the Java services, or route cluster requests to IntelliJ. That
setup needs a separate implementation before you can use the full hybrid workflow.
The current `daily.sh` manages EC2 and Compose only; Minikube start/stop will need
to be added with that implementation. EKS is not installed inside this VM.

## Only when needed

| Situation | Action |
| --- | --- |
| AWS login expired | Renew your AWS login; do not recreate the VM |
| Your home/VPN public IP changed | Update `ssh_cidr` in `terraform.tfvars`, then run `terraform -chdir=infra/aws-dev/terraform apply` |
| Software/configuration update | Deliberately rerun setup; it pulls images, reapplies configuration and inserts missing demo data |

**Daily routine: start → connect → work → stop. Reinstall only for a new VM or an intentional update.**

## Moving the existing deployment to us-east-1 (once)

Use **us-east-1 everywhere for new setup**: Identity Center, AWS CLI and Terraform.
The local configuration has been updated. Existing resources do not move when a
region setting changes. Your existing state/cleanup record still identifies the
previous deployment in **us-east-2**; preserve those records until cleanup finishes.
The setup script stops if the configured region differs from the recorded deployment.

Before creating the new environment:

1. Use your original AWS profile with cleanup permissions in the previous region.
   Complete this before changing an existing SSO permission set to the new region.
2. Run the usual [termination script](../../infra/aws-dev/03-terminate/01-terminate.sh)
   and confirm its VM, disk and Elastic IP cleanup checks pass.
3. Remove the retained old network and key-pair registration with Terraform:

   ```bash
   terraform -chdir=infra/aws-dev/terraform init
   terraform -chdir=infra/aws-dev/terraform destroy -var='aws_region=us-east-2'
   ```

   Review the resources before typing `yes`. This command targets the old region
   deliberately. Do not delete or edit the state to bypass cleanup. If destruction
   fails, resolve the error and rerun it before proceeding.
4. Configure your CLI profiles for the new region:

   ```bash
   aws configure set region us-east-1 --profile workouts-console
   # Run this for your SSO profile if you have configured it:
   aws configure set region us-east-1 --profile workouts-dev
   export AWS_REGION=us-east-1
   export AWS_DEFAULT_REGION=us-east-1
   ```

5. Follow [the SSO guide](01b-aws-sso-user-setup.md) to create/update its regional
   permissions, using `WORKOUTS_EC2_REGION=us-east-1`. If the SSO profile already
   exists, log in again after provisioning completes.
6. Run [recreate fresh](../../infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh).
   Terraform now creates the network and VM in **us-east-1**, then installs the
   software and sample data. Keep your local SSH key. No data migration is performed.


## Existing VM: switch from Elastic IP once

New deployments already use automatic public IPv4. For an existing deployment,
first check `terraform -chdir=infra/aws-dev/terraform output -raw aws_region`.
If it differs from `terraform.tfvars`, complete the region-change procedure above
instead. Do not apply a region change against existing resources.

For a VM already in the configured region, close the SSH tunnel, then run:

```bash
export AWS_PROFILE=workouts-dev
aws sso login --profile workouts-dev
terraform -chdir=infra/aws-dev/terraform init
terraform -chdir=infra/aws-dev/terraform plan
terraform -chdir=infra/aws-dev/terraform apply
bash infra/aws-dev/02-daily-use/01-start.sh
bash infra/aws-dev/02-daily-use/02-connect.sh
```

Review the plan: it should remove `aws_eip.dev`, not replace your VM or delete its
disk. Resolve unrelated changes before approving. The instance already has
`associate_public_ip_address = true`. Terraform releases the old managed EIP;
the new automatic address is discovered by start/connect. This interrupts existing
connections but preserves the VM data. No manual public-IP edits are needed.
If the EIP was already released by the termination script, recreation simply uses
the new configuration. Do not manually edit state or the cleanup record.

After this one-time change, use **start/connect in the morning and stop at night**.
Terraform's `public_ip` output is only a cached snapshot; use start/connect to
refresh SSH after every stop/start.
