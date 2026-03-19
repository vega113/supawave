# Supawave Deployment Plan

Task epic: `incubator-wave-deployment`
Branch: `deploy-supawave-contabo`
Worktree: `/Users/vega/devroot/worktrees/incubator-wave/deploy-supawave-contabo`
Target host: `ubuntu@86.48.3.138`
Canonical hostname: `wave.supawave.ai`
Redirect hostnames: `supawave.ai`, `www.supawave.ai`

## Goal
Automate deployment of Wave to the Contabo host from GitHub Actions and wire DNS for `supawave.ai` through Cloudflare so the app is reachable on a stable public domain.

## Chosen topology
- Deployment shape: Docker Compose on the Contabo host.
- Reverse proxy: Caddy on the host side of the Compose stack.
- Wave runtime: Wave container bound behind Caddy, not exposed directly on the public interface.
- Delivery path: GitHub Actions builds and ships the deployment payload over SSH, then the host runs a versioned deploy script.
- DNS: Cloudflare manages `wave.supawave.ai` as the canonical app hostname. The apex and `www` hostnames redirect to it once zone control is confirmed.

## Why this shape
- The host already has Docker.
- Docker Compose gives the narrowest reliable rollback path.
- It avoids host-level Java/process drift and keeps deploy/restart semantics simple.
- Caddy keeps TLS and reverse-proxy configuration simpler than bootstrapping nginx by hand.

## Current known constraints
- The Contabo host is reachable over SSH and already has Docker.
- nginx and cloudflared are not installed.
- `systemctl is-system-running` reports `degraded`, so the host bootstrap must avoid relying on an unknown broken service.
- The current Cloudflare token context does not return a `supawave.ai` zone, so DNS automation must explicitly verify whether the zone is missing, lives in another account, or needs onboarding before record creation.

## Responsibility split
- GitHub Actions:
  - checkout
  - build the deployment artifact/image
  - establish SSH auth from GitHub Secrets
  - copy the deployment bundle to the host
  - invoke the remote deploy script
  - run remote smoke checks and fail fast on unsuccessful rollout
- Host-side deploy scripts:
  - prepare release directories
  - render env/runtime config from host-local secrets
  - run `docker compose pull/build && docker compose up -d`
  - switch the active release pointer if needed
  - expose a deterministic rollback command
- Cloudflare automation:
  - verify zone ownership/access
  - create/update DNS records for the canonical and redirect hostnames
  - keep proxying/TLS assumptions documented

## Secret management
- GitHub Secrets:
  - deploy SSH private key
  - deploy host/user/path values
  - optional container registry credentials if needed
- Host-local secrets:
  - Wave runtime secrets and application config stay on the host, not in git
  - store them in a dedicated env file under the deploy root with restricted permissions
- TLS:
  - terminate HTTPS at Caddy with normal public certificates for the canonical hostname
  - if Cloudflare proxying is enabled, document the chosen SSL mode explicitly

## Rollback strategy
- Keep releases under versioned directories on the host.
- Keep a `current` symlink for the active deployment.
- The deploy script must preserve the previous release id.
- On failed post-deploy smoke checks, switch `current` back to the previous release and restart the compose stack.
- Document the manual rollback command and expected operator flow.

## Immediate checks
- Verify current software on the server: Docker, Compose plugin, open ports, filesystem layout.
- Verify whether Caddy is already packaged/available on Ubuntu 25.10 or should run as a container in Compose.
- Verify Cloudflare zone ownership and whether `supawave.ai` exists in the current account/token context.
- Decide whether the Wave data path should be host-mounted volumes or image-local for the first deploy.
- Decide which Wave config values must be injected from host-local secrets for first boot.

## Deliverables
- New deployment epic + tasks in Beads.
- Repo-owned deployment assets:
  - GitHub Actions workflow
  - host deploy/bootstrap scripts
  - compose/reverse-proxy config
  - deployment docs
- Cloudflare DNS automation or a documented blocker if zone ownership is not available in the current account.

## Post-deploy verification
- Confirm the Wave app answers on the canonical hostname with HTTP 200 or expected redirect/auth entrypoint behavior.
- Confirm the reverse proxy presents valid TLS for the canonical hostname.
- Confirm the Wave process/container is healthy after rollout.
- Run a minimal login/smoke flow or server probe script from GitHub Actions or the host.
- Record the deployed release id in logs/output for rollback traceability.
