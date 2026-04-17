Status: Canonical
Owner: Project Maintainers
Updated: 2026-04-17
Review cadence: quarterly

# Deployment Guide

Apache Wave supports two first-class deployment flavors:
- standalone: Wave terminates TLS directly
- caddy-fronted: Caddy terminates TLS and reverse proxies to Wave

Read in this order:
1. `linux-host.md` for the generic Linux host baseline and prerequisites
2. `standalone.md` if you want a minimal direct-TLS deployment
3. `caddy.md` if you want the recommended reverse-proxy deployment
4. `../../deploy/mongo/README.md` for Mongo auth, backup, restore, and durability guidance
5. optional overlays such as `contabo.md` and `cloudflare-supawave.md` only after reading the generic docs
6. `email-deliverability-supawave.md` if you are diagnosing Resend or DNS-related inbox-placement issues for `supawave.ai`

Provider-specific notes are overlays, not the canonical deployment story.
