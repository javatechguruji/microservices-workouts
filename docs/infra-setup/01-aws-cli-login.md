# 01. Prepare AWS access — first-time setup

[Reading order](README.md#aws-setup-reading-order) · [Next: 01b SSO setup](01b-aws-sso-user-setup.md)

Follow one path: **prepare your CLI → create your development SSO login → create
the VM → use the daily scripts**. Run all commands in your IntelliJ terminal on
your Mac, from the repository root. Use **us-east-1** throughout.

This document prepares the initial administrator login needed to create your
development user. It does not create a VM. After the next guide, all development
commands use one profile: **workouts-dev**.

## 1. Select the current AWS CLI

```bash
export PATH="/opt/homebrew/bin:$PATH"
rehash
which aws
aws --version
```

The browser-login command below requires AWS CLI **2.32.0 or newer**. On your Mac,
use the Homebrew executable at `/opt/homebrew/bin/aws`, rather than the older copy
at `/usr/local/bin/aws`. If missing, run `brew install awscli`; if outdated, run
`brew update` then `brew upgrade awscli`.

If new terminals select the old copy, add `export PATH="/opt/homebrew/bin:$PATH"`
once to `~/.zshrc`, open a new terminal and check again.

## 2. Sign in as the account administrator — one time

Creating your development user requires existing administrative access. This
initial login is named `workouts-console`; use it for account setup only.

```bash
aws login --profile workouts-console --region us-east-1
```

In the browser:

1. Select your intended AWS account and existing administrator session.
2. If your only existing login is root, choose **Root or IAM user → Root user**
   and enter the account email/password in the AWS page.
3. Complete MFA, approve the CLI login request, then return to IntelliJ.

Do not create root access keys or put credentials in repository files.
[AWS browser-login instructions](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html).

If asked **Configure AWS skills and the AWS MCP server for your AI coding
agent(s)? [y/n/never]**, enter **n**. That integration is optional and is not
required for this setup.

## 3. Verify the account

```bash
export AWS_PROFILE=workouts-console
aws sts get-caller-identity
```

Check that `Account` is your intended development account. If using root for this
initial setup, the ARN ends in `:root`. No infrastructure is created by this check.

## 4. Next: create your development login

Keep this terminal open and follow **[01b — Create your development SSO user](01b-aws-sso-user-setup.md)**.
It takes you through these steps, in order:

1. Find the Identity Center access portal.
2. Run the script to create `techguru-dev` and assign its development permissions.
3. Set the user's password and MFA.
4. Configure and verify the `workouts-dev` CLI profile.

Only after finishing 01b, proceed to **[02 — VM setup and daily use](02-aws-dev-vm.md)**.
You do not need a `workouts-terraform` profile for this path.

Already completed SSO setup? Skip the initial administrator login. Use:

```bash
export AWS_PROFILE=workouts-dev
aws sso login --profile workouts-dev
aws sts get-caller-identity
```

Then continue with guide 02. You do not recreate the user or reconfigure SSO each day.

## Troubleshooting only

| Problem | Action |
| --- | --- |
| `aws login` is not recognized | Repeat step 1 and select the newer CLI. |
| Initial administrator session expired | Repeat step 2, then verify the account. |
| SSO portal rejects root credentials | In guide 01b, sign in as `techguru-dev` with its password/MFA. |
| `workouts-dev` profile not found | Complete guide 01b before starting the VM setup. |
| Wrong account shown | Correct the login before running any provisioning script. |
