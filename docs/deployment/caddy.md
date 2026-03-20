# Caddy-Fronted Deployment

Caddy is a web server and reverse proxy. In this deployment flavor, Caddy owns the public edge and proxies traffic to Wave on an internal port.

## Why this is recommended

For many operators, Caddy-fronted deployment is simpler because it centralizes:
- certificate issuance and renewal
- HTTP to HTTPS redirects
- apex/www canonicalization
- reverse-proxy behavior

Wave remains a separate application service. Caddy is supported, but it is not embedded into the Wave process and not required for all deployments.

## Topology

- Caddy listens on `:80` and `:443`
- Wave listens on an internal port such as `127.0.0.1:9898`
- Caddy proxies requests to Wave and owns public TLS

## Canonical assets

Use the canonical provider-neutral assets under:
- `deploy/caddy/Caddyfile`
- `deploy/caddy/compose.yml`
- `deploy/caddy/application.conf`
- `deploy/caddy/deploy.sh`
- `deploy/caddy/deploy.env.example`

## Validation

Docker-based validation:
```bash
bash -n scripts/deployment/bootstrap-linux-host.sh
WAVE_IMAGE=ghcr.io/example/wave:test DEPLOY_ROOT=/tmp/supawave WAVE_INTERNAL_PORT=9898 CANONICAL_HOST=wave.example.test ROOT_HOST=example.test WWW_HOST=www.example.test docker compose -f deploy/caddy/compose.yml config
```

At runtime, confirm:
- Caddy serves HTTPS publicly
- Wave is reachable only on the internal port
- redirects point to the canonical host as intended

## Non-Docker path

Example systemd units live under:
- `deploy/systemd/wave.service`
- `deploy/systemd/caddy.service`

## Migration

From standalone to Caddy-fronted:
- move TLS ownership from Wave to Caddy
- move redirect handling to Caddy
- bind Wave to an internal port only
- keep a rollback path back to standalone

## DNS and proxy overlays

Cloudflare is optional. If used, treat it as a DNS/proxy overlay on top of this topology, not as a requirement.
See [cloudflare-supawave.md](cloudflare-supawave.md).
