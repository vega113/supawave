# Contabo Overlay

This document is a provider-specific overlay for a generic Linux host deployment.
Read these first:
- [linux-host.md](linux-host.md)
- [caddy.md](caddy.md)

Contabo-specific assumptions in the current project history:
- host user: `ubuntu`
- deploy root example: `~/supawave`
- canonical hostname: `supawave.ai`
- legacy redirect hostname: `wave.supawave.ai`
- redirect hostname: `www.supawave.ai`

The canonical deploy assets are now provider-neutral and live under `deploy/caddy/`.
Do not treat `deploy/contabo/` as the source of truth for new work.

## GitHub Actions Host Key Pinning

Both Contabo deployment workflows require a pinned SSH host key fingerprint before
opening an SSH connection:

- `.github/workflows/deploy-contabo.yml`
- `.github/workflows/rollback-contabo.yml`

Configure `CONTABO_HOST_FINGERPRINT` as a repository variable (recommended) or a
repository secret. Use the `SHA256:...` fingerprint for the trusted Contabo SSH
host key, not a value captured from a failed workflow run. Verify it from a
trusted channel first, then set it with:

```bash
gh variable set CONTABO_HOST_FINGERPRINT --repo vega113/supawave --body 'SHA256:...'
```

To store it as a secret instead (not visible in workflow logs):

```bash
gh secret set CONTABO_HOST_FINGERPRINT --repo vega113/supawave --body 'SHA256:...'
```

If this value is missing or does not match the key returned by the host, deploy
and rollback workflows fail before uploading or executing the deployment bundle.
