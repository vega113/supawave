# Caddy Deployment Assets

This directory contains the canonical provider-neutral deployment assets for the
Caddy-fronted Wave topology.

Use these files when:
- running Wave behind Caddy on a generic Linux host
- validating the canonical Docker-based deployment layout
- deriving provider-specific overlays from the generic deployment story

Files:
- `Caddyfile`: public TLS and reverse-proxy rules
- `compose.yml`: Docker Compose topology for `wave + caddy`
- `application.conf`: Wave runtime config for the Caddy-fronted example
- `deploy.sh`: host-side deploy and rollback helper
- `deploy.env.example`: example registry/deploy environment variables

Provider-specific examples should point back here instead of forking behavior
silently.
