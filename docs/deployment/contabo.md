# Contabo Deployment

The Contabo deployment for Wave uses a Docker Compose stack on the host at
`ubuntu@86.48.3.138`.

## Topology

- `wave` runs from the repository `Dockerfile` image.
- `mongo` runs as an internal sidecar on the compose network and stores the
  Wave persistence data for supported stores. Wave waits for its healthcheck,
  not just container start.
- `caddy` terminates public traffic and reverse proxies to the internal Wave
  container.
- Wave binds to `127.0.0.1:9898` on the host and is not exposed directly.
- Persistent Wave data lives under the deploy root, not in the release
  directory.
- GitHub Actions builds and pushes the image to `ghcr.io`, ships a small
  release bundle over SSH, and runs the host deploy script.

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
- `shared/mongo/db`
- `shared/indexes`
- `shared/sessions`

Caddy state directories:

- `shared/caddy-data`
- `shared/caddy-config`

Deploy credential file:

- `shared/deploy.env`

Recommended contents of `shared/deploy.env` on the Contabo host:

```bash
GHCR_USERNAME=<github-username>
GHCR_TOKEN=<ghcr-read-token>
GHCR_REGISTRY_HOST=ghcr.io
```

## GitHub Secrets

Required:

- `CONTABO_SSH_HOST`
- `CONTABO_SSH_USER`
- `CONTABO_SSH_KEY`

Optional:

- `CONTABO_SSH_PORT`
- `CONTABO_DEPLOY_ROOT`

GitHub Actions also needs:

- `packages: write` permission so it can push the image to `ghcr.io`

Defaults used by the workflow when optional secrets are omitted:

- SSH port `22`
- deploy root `/home/<ssh-user>/supawave`
- canonical hostname `wave.supawave.ai`
- redirect hostnames `supawave.ai` and `www.supawave.ai`

## Rollout Flow

1. GitHub Actions runs `:pst:build` and `:wave:smokeInstalled` as package
   preflight.
2. The workflow builds the container image with the repository `Dockerfile`.
3. The workflow logs in to `ghcr.io` and pushes the image tagged by commit SHA.
4. The workflow creates a bundle containing:
   - `compose.yml`
   - `Caddyfile`
   - `application.conf`
   - `deploy.sh`
5. The bundle is copied to the Contabo host over SSH.
6. The host extracts the bundle into `releases/<git-sha>/`.
7. The release script logs in to `ghcr.io` if `shared/deploy.env` provides
   credentials, pulls the target image, flips `current` to the new release, and
   runs `docker compose up -d`.
   - The compose stack now waits for `mongo` to pass its ping healthcheck
     before starting Wave.
8. The release script smoke-checks:
   - `http://127.0.0.1:9898/readyz`
   - the canonical host through the local Caddy listener
   - the redirect hostnames through the same listener
9. On smoke failure, the script flips `current` back to `previous` and starts
   the last known-good release.

## First Boot Assumptions

- Docker and the Compose plugin are installed on the host.
- `shared/deploy.env` exists if the GHCR package is private.
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
- The app config in `deploy/contabo/application.conf` now also pins the
  supported persistence stores to MongoDB v4. Sessions and search remain on
  their existing config paths; this deployment slice does not claim those are
  solved.
- Mongo authentication and backup strategy remain explicit follow-up work; this
  slice only changes the intended store topology and startup gating.
