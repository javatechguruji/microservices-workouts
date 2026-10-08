# 01b. Create your development SSO user

[Login guide](01-aws-cli-login.md) · [VM setup / daily use](02-aws-dev-vm.md)

First complete [01 — Prepare AWS access](01-aws-cli-login.md). Then run these steps
once from the same IntelliJ terminal. This creates an **Identity Center
user + permission set + account assignment**. AWS creates the IAM role automatically;
you do not create an IAM user, manually create a role, or generate access keys.
Keep this identity when terminating/recreating the development VM.

## 1. Check the existing Identity Center instance

Your account was inspected during this setup: an active organization instance
already exists in **us-east-1**. Use **us-east-1 for both Identity Center and EC2**. You do not need to enable another instance.
The enablement instructions below apply only when setting up a different account.

Use your current administrator/root browser login for this one-time setup:

1. Open **IAM Identity Center** in the AWS Console.
2. Select **us-east-1**.
3. If already enabled, confirm it is an **organization instance** and that the
   identity source is **Identity Center directory**. If it uses a corporate IdP,
   use your organization's user-provisioning process instead of this script.
4. If not enabled, enable an **organization instance** from the AWS Organizations
   management account. Follow the Console's organization creation steps if needed.
   An account instance is not sufficient for assigning AWS account access here.
5. Copy **Dashboard → Settings summary → AWS access portal URL** and note the region.

Do not run `aws sso-admin create-instance` for this purpose: that API creates an
account instance, not the organization instance required by this workflow.
[AWS API distinction](https://docs.aws.amazon.com/cli/latest/reference/sso-admin/create-instance.html).

Identity Center has no additional service fee. If you are on AWS's Free Plan,
creating an AWS Organization upgrades the account to the Paid Plan; review the
Console notice before enabling it. [AWS enablement instructions](https://docs.aws.amazon.com/singlesignon/latest/userguide/enable-identity-center.html).

## 2. Use the administrator session from guide 01

```bash
export AWS_PROFILE=workouts-console
aws sts get-caller-identity
```

If that session expired, renew it with
`aws login --profile workouts-console --region us-east-1`, then verify again.
Check that the account ID is your intended development account. This session is
only for creating/configuring the SSO identity; step 6 switches you to daily access.

## 3. Create the user and its account role through AWS CLI

Use these values for your development user (do not put a password here):

```bash
export WORKOUTS_SSO_REGION=us-east-1       # Where Identity Center is enabled.
export WORKOUTS_EC2_REGION=us-east-1       # Must match terraform.tfvars aws_region.
export WORKOUTS_SSO_EMAIL='javatechguruji@gmail.com'
export WORKOUTS_SSO_USERNAME='techguru-dev'

bash infra/aws-dev/00-aws-access/01-create-sso-user.sh
```

Script: [01-create-sso-user.sh](../../infra/aws-dev/00-aws-access/01-create-sso-user.sh).
Its [implementation](../../infra/aws-dev/00-aws-access/create-sso-user.py) invokes
`aws identitystore create-user`, `aws sso-admin create-permission-set`,
`put-inline-policy-to-permission-set`, `create-account-assignment` and
`provision-permission-set`, with status polling. Type your account ID when prompted.
It reuses the matching user and its own permission set on repeat runs; it refuses
to overwrite a permission set with the same name created outside this script.

The permission set is **WorkoutsDevInfra**, with a **one-hour role session**. It
allows selected EC2/VPC provisioning and cleanup actions in your EC2 region. It
includes resource descriptions and tagging, VM lifecycle, network setup, EBS and
legacy Elastic IP cleanup/migration (new deployments do not allocate an EIP). It does not grant IAM administration/PassRole, Organizations,
S3, EKS, billing access, or AdministratorAccess.

**Scope limitation:** allowed EC2 actions apply region-wide, not just to resources
tagged for this project. This is narrower than root, but is not a single-project
security boundary. Use a dedicated development account if other important EC2
resources share the account/region. Permission needs can change when new resource
types are added; resolve specific AccessDenied errors rather than switching back
to root for daily work. This policy has not been live-tested with your SSO role yet.

## 4. Set the new user's password and require MFA

The CLI creates the user **without a password**. It does not automatically complete
password setup. In the Console:

1. **IAM Identity Center → Users → techguru-dev → Reset password**.
2. Choose **Send an email to the user with instructions for resetting the password**.
3. Open the email and set the password. Use this new user, not your root password,
   when signing in to the access portal.
4. Under **Settings → Authentication → Multi-factor authentication**, configure
   MFA at every sign-in and require users without a device to register one at sign-in.
   This is an instance-wide policy; coordinate with the administrator if others use it.
5. Sign into your AWS access portal as `techguru-dev` and register a passkey/security
   key or authenticator app. Confirm the account lists **WorkoutsDevInfra**.

Password/MFA enrollment requires the user's browser interaction; the script does
not store a password or automate MFA enrollment.
[AWS CLI-created user/password behavior](https://docs.aws.amazon.com/singlesignon/latest/userguide/addusers.html),
[AWS MFA enforcement](https://docs.aws.amazon.com/singlesignon/latest/userguide/how-to-configure-mfa-device-enforcement.html).

## 5. Configure your SSO profile once

```bash
aws configure sso --profile workouts-dev
```

| Prompt | Value |
| --- | --- |
| SSO session name | `workouts-dev` |
| SSO start URL | Actual portal URL copied in step 1 |
| SSO region | `WORKOUTS_SSO_REGION` value from step 3 |
| SSO registration scopes | Accept `sso:account:access` |
| Browser login | `techguru-dev`, its new password and MFA |
| AWS account | Your existing development account |
| Role | `WorkoutsDevInfra` |
| CLI default client region | `us-east-1` |
| CLI output format | `json` |
| Optional AWS skills/MCP prompt | `n`; not needed for this setup |

The CLI session name/profile name and Identity Center username are different
labels: the CLI profile is `workouts-dev`; the person signing in is `techguru-dev`.

## 6. Verify and use SSO for all development commands

```bash
export AWS_PROFILE=workouts-dev
aws sso login --profile workouts-dev
aws sts get-caller-identity
```

Check the account ID matches your existing environment. The ARN should contain
`assumed-role/AWSReservedSSO_WorkoutsDevInfra_...`, **not** end in `:root`.
Read-only permission check:

```bash
aws ec2 describe-instances --region us-east-1 --max-results 5 --query 'Reservations[].Instances[].InstanceId'
```

If your VM currently exists in **us-east-1**, you can also run
`terraform -chdir=infra/aws-dev/terraform plan` to check state-read access. Do not
apply just to test the login. A successful plan does not prove all create/delete
permissions; those are checked when you perform the intended lifecycle actions.

Now use the existing [daily/termination/recreation scripts](02-aws-dev-vm.md).
No changes to existing AWS resources, Terraform state or SSH keys are needed merely
to switch profiles. When SSO expires, rerun `aws sso login --profile workouts-dev`.
The SSO credentials are managed directly by AWS CLI/SDKs; you do not need the
console-login credential-process profile for this SSO session.
