# Connect Codex to your ChatGPT Pro account

Checked: September 29, 2026.

## What I verified on this machine

The installed CLI is `codex-cli 0.159.2`. Running:

```bash
codex login status
```

returned:

```text
Logged in using ChatGPT
```

**Your local Codex CLI already uses ChatGPT authentication.** This output does not identify the account, verify an active Pro subscription, or prove that this desktop conversation uses the same account. I did not inspect credentials or change your sign-in.

ChatGPT Pro includes Codex access, subject to the plan's usage limits. See [official pricing and plan availability](https://learn.chatgpt.com/docs/pricing).

## 1. Confirm the account and subscription

1. Open the profile menu in the desktop app hosting this conversation and check the active account.
2. In ChatGPT, open Settings and find the account/subscription section. Confirm that the account has an active **Pro** plan; labels can vary by app version.
3. Compare the account in both places. If a workspace selector appears, check that you selected the intended workspace.

If the app uses your Pro account, no additional account configuration is needed. The CLI result above is supporting evidence for the CLI only.

## 2. Switch the desktop app or IDE to your Pro account

Only do this if the account is wrong or API-key authentication is selected:

1. Use the profile menu to log out.
2. On desktop, choose **Continue**; in the IDE extension, choose **Sign in with ChatGPT**.
3. Complete the browser flow using the account with Pro, then return to the app.

These sign-in methods are described in [OpenAI authentication guidance](https://learn.chatgpt.com/docs/auth).

## 3. Switch the CLI if necessary

Run these commands individually in Terminal:

```bash
codex logout
codex login
```

Complete the browser sign-in with your Pro account, then verify:

```bash
codex login status
```

Expected: `Logged in using ChatGPT`.

The CLI and IDE extension share cached credentials, so logging out can affect both. API-key sign-in uses separate Platform billing rather than included ChatGPT plan usage. [Authentication and billing behavior](https://learn.chatgpt.com/docs/auth)

## 4. If browser sign-in fails

Enable device-code login in ChatGPT security settings, then run:

```bash
codex login --device-auth
```

Follow the displayed link and code instructions. [Official device-code procedure](https://learn.chatgpt.com/docs/auth#login-on-headless-devices)

## Completion check

- The account shown in your app is the one with an active Pro subscription.
- The intended workspace is selected.
- If you use the CLI, `codex login status` reports ChatGPT authentication.
- A new Codex conversation works under that account.
