# Supawave Host Resource Optimization

Automation for applying the production resource tuning from `deploy/production/` to the Supawave host (94 GiB RAM, 18 cores). Scripts are idempotent and capture backups for rollback.

## Prerequisites

- Run directly on the Supawave host with sudo/root access.
- Docker and Docker Compose installed and running.
- `mongosh`, `curl`, and `fallocate` available on the host.

## Scripts

- `provision.sh` — Orchestrates pre-flight validation, sysctl tuning, PAM limits, swap setup, and post-flight checks.
- `apply-sysctl.sh` — Installs `deploy/production/sysctl-tuning.conf` to `/etc/sysctl.d/99-wave.conf` and validates key tunables.
- `apply-limits.sh` — Merges `deploy/production/limits.conf.prod` into `/etc/security/limits.conf`, ensures PAM limits are active, and verifies entries.
- `setup-swap.sh` — Creates and activates a 32 GiB `/swapfile`, persists it in `/etc/fstab`, and sets `vm.swappiness=10`.
- `validate.sh` — Pre/post validation of sudo, disk space, docker/mongo, swap, sysctl values, ulimits, and container health. Writes reports under `/var/log/wave-supawave/`.
- `rollback.sh` — Restores `/etc/sysctl.conf`, `/etc/security/limits.conf`, PAM session files, and `/etc/fstab` from backups, disables swap, and removes `/swapfile`.

Backups and snapshots are stored under `/var/backups/wave-supawave` (override with `BACKUP_DIR`).

## CI Integration

The GitHub Actions workflow `.github/workflows/deploy-contabo.yml` now uploads this directory with each deploy and runs `provision.sh` on the host (guarded to the `deploy` action). Manual runs remain supported for iterative validation or rollback.

## Quick Start

```bash
cd deploy/supawave-host
sudo ./provision.sh
```

This captures a rollback snapshot, runs pre-flight checks, applies sysctl, limits, and swap, then runs post-flight validation.

## Targeted Usage

- Pre-flight only: `sudo ./validate.sh pre`
- Post-flight only: `sudo ./validate.sh post`
- Apply sysctl: `sudo ./apply-sysctl.sh`
- Apply limits: `sudo ./apply-limits.sh`
- Setup swap: `sudo ./setup-swap.sh`
- Rollback everything: `sudo ./rollback.sh`

## Troubleshooting

- Validation reports: `/var/log/wave-supawave/validate-*.log`
- Backups: `/var/backups/wave-supawave`
- If PAM limits appear inactive, start a new login shell (`su - $USER`) or re-SSH into the host.
- After rollback, run `sysctl --system` if custom sysctl files remain.
