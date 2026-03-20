# Linux Host Baseline

This project now treats deployment as provider-neutral. The canonical target is a generic systemd-based x86_64 Linux host, with Ubuntu LTS used as the reference environment for package names and examples.

## Baseline assumptions

- public DNS points at the host
- ports `80` and `443` are reachable from the internet
- systemd is available for non-Docker service management
- Java 17 or later is available for standalone Wave deployments
- Docker Engine and `docker compose` are available for the Docker/Caddy path
- common bootstrap tools such as `curl`, `tar`, and `openssl` are available

Other Linux distributions may work if they provide equivalent packages and runtime behavior.

## Bootstrap

Reference script:
- `scripts/deployment/bootstrap-linux-host.sh`

Dry run:
```bash
scripts/deployment/bootstrap-linux-host.sh --dry-run
```

The script covers only host prerequisites and common packages. It does not deploy Wave itself.

## Choose a deployment flavor

- Use [standalone.md](standalone.md) for direct Wave TLS termination.
- Use [caddy.md](caddy.md) for the recommended `wave + caddy` topology.

## Optional overlays

These are optional and should not be treated as the source of truth:
- [contabo.md](contabo.md)
- [cloudflare-supawave.md](cloudflare-supawave.md)
