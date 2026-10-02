# Codex account setup

This developer-tool guide is separate from e-commerce authentication. It does not
verify your current app account or subscription. Earlier CLI observations are not
proof that a desktop session uses the same account.

## Check the intended account

Open the app's profile menu and confirm the account/workspace. Check your plan in
ChatGPT's account settings. For the CLI, run:

```sh
codex login status
```

This reports CLI authentication status, not proof of the desktop app's active plan.

## Change sign-in only if necessary

For the CLI:

```sh
codex logout
codex login
```

Complete browser login with your intended ChatGPT account. The desktop signed-out
screen uses **Continue**; the IDE extension uses **Sign in with ChatGPT**. API-key
sign-in uses separate Platform usage billing. CLI and IDE share cached credentials.
See [official authentication guidance](https://learn.chatgpt.com/docs/auth).

## Browser callback unavailable

Enable device-code login in the applicable account/workspace settings, then run:

```sh
codex login --device-auth
```

Follow the displayed link/code. See [official device login instructions](https://learn.chatgpt.com/docs/auth#login-on-headless-devices).

No account was switched or credentials inspected while revising this guide.
