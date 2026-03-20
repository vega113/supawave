# Cloudflare Overlay For supawave.ai

This document is an optional DNS/proxy overlay.
Read [linux-host.md](linux-host.md) first, then choose a deployment flavor from [standalone.md](standalone.md) or [caddy.md](caddy.md).

Cloudflare is optional. It is not part of the canonical Linux-host baseline.

Current project-specific notes for `supawave.ai`:
- DNS records were created for `supawave.ai`, `wave.supawave.ai`, and `www.supawave.ai`
- public traffic may be proxied through Cloudflare, but Wave and Caddy remain deployable without Cloudflare

Use this overlay only for Cloudflare-specific DNS/proxy behavior.
