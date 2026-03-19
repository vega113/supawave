# Contabo Deployment

The Contabo deployment for Wave uses a Docker Compose stack on the host at
`ubuntu@86.48.3.138`.

## Topology

- `wave` runs from the repository `Dockerfile` image.
- `caddy` terminates public traffic and reverse proxies to the internal Wave
  container.
- Wave binds to `127.0.0.1:9898` on the host and is not exposed directly.
- Persistent Wave data lives under the deploy root, not in the release
  directory.
- GitHub Actions builds the image, ships a bundle over SSH, and runs the host
  deploy script.

## Release Layout

Default deploy root:

```text
~/supawave
```

The runtime tree is split into:

- `incoming/` for uploaded bundles
- `releases/<git-sha>/` for immutable release payloads
- `current` for the active release symlink
- `previous` for the prior release symlink
- `shared/` for persistent data and Caddy state

Wave data directories:

- `shared/accounts`
- `shared/attachments`
- `shared/certificates`
- `shared/deltas`
- `shared/indexes`
- `shared/sessions`

Caddy state directories:

- `shared/caddy-data`
- `shared/caddy-config`

## GitHub Secrets

Required:

- `CONTABO_SSH_HOST`
- `CONTABO_SSH_USER`
- `CONTABO_SSH_KEY`

Optional:

- `CONTABO_SSH_PORT`
- `CONTABO_DEPLOY_ROOT`

Defaults used by the workflow when optional secrets are omitted:

- SSH port `22`
- deploy root `/home/<ssh-user>/supawave`
- canonical hostname `wave.supawave.ai`
- redirect hostnames `supawave.ai` and `www.supawave.ai`

## Rollout Flow

1. GitHub Actions runs `:pst:build` and `:wave:smokeInstalled` as package
   preflight.
2. The workflow builds the container image with the repository `Dockerfile`.
3. The workflow creates a bundle containing:
   - `compose.yml`
   - `Caddyfile`
   - `application.conf`
   - `deploy.sh`
   - `image.tar.gz`
4. The bundle is copied to the Contabo host over SSH.
5. The host extracts the bundle into `releases/<git-sha>/`.
6. The release script loads the image, flips `current` to the new release, and
   runs `docker compose up -d`.
7. The release script smoke-checks:
   - `http://127.0.0.1:9898/readyz`
   - the canonical host through the local Caddy listener
   - the redirect hostnames through the same listener
8. On smoke failure, the script flips `current` back to `previous` and starts
   the last known-good release.

## First Boot Assumptions

- Docker and the Compose plugin are installed on the host.
- nginx and cloudflared are not required.
- The host user can create the deploy root under its home directory.
- Cloudflare DNS is handled separately and must point the public hostnames at
  the Contabo IP before TLS issuance or redirect checks can succeed.

## Rollback

To roll back manually, run the release script from the current release directory:

```bash
bash ~/supawave/current/deploy.sh rollback
```

The script restores the `previous` symlink, restarts the Compose stack, and
re-runs the same smoke checks.

## Notes

- This slice owns deployment automation only.
- DNS record creation for `supawave.ai` remains a separate Cloudflare task.
- The app config in `deploy/contabo/application.conf` only overrides the host
  binding and canonical domain.
