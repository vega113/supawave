# Contabo Overlay

This document is a provider-specific overlay for a generic Linux host deployment.
Read these first:
- [linux-host.md](linux-host.md)
- [caddy.md](caddy.md)

Contabo-specific assumptions in the current project history:
- host user: `ubuntu`
- deploy root example: `~/supawave`
- current example hostnames used during setup: `wave.supawave.ai`, `supawave.ai`, `www.supawave.ai`

The canonical deploy assets are now provider-neutral and live under `deploy/caddy/`.
Do not treat `deploy/contabo/` as the source of truth for new work.
