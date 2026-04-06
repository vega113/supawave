# Supawave Host Resource Optimization

Automation for applying the production resource tuning from `deploy/production/` to the Supawave host (94 GiB RAM, 18 cores). Scripts are idempotent and capture backups for rollback.

## Prerequisites

- Run directly on the Supawave host with sudo/root access.
- Docker and Docker Compose installed and running.
- `curl` and `fallocate` available on the host (`mongosh` is only required inside the Mongo container).

## Scripts

- `provision.sh` — Orchestrates pre-flight validation, sysctl tuning, PAM limits, swap setup, and post-flight checks.
- `apply-sysctl.sh` — Installs `deploy/production/sysctl-tuning.conf` to `/etc/sysctl.d/99-wave.conf` and validates key tunables.
- `apply-limits.sh` — Installs `deploy/production/limits.conf.prod` as `/etc/security/limits.d/99-wave.conf`, ensures PAM limits are active, and verifies entries.
- `setup-swap.sh` — Creates and activates a 32 GiB `/swapfile`, persists it in `/etc/fstab`, and sets `vm.swappiness=10`.
- `validate.sh` — Pre/post validation of sudo, disk space, docker/mongo, swap, sysctl values, ulimits, and container health. Writes reports under `/var/log/wave-supawave/`.
- `rollback.sh` — Restores `/etc/sysctl.conf`, `/etc/security/limits.conf`, PAM session files, and `/etc/fstab` from backups, disables swap, and removes `/swapfile`.
- `install-grafana-alloy.sh` — Installs Grafana Alloy with Grafana Cloud onboarding variables.
- `configure-grafana-alloy.sh` — Writes `/etc/alloy/config.alloy` for metrics+logs remote write and restarts `alloy.service`.

Backups and snapshots are stored under `/var/backups/wave-supawave` (override with `BACKUP_DIR`). When running via `sudo`, use `sudo --preserve-env=BACKUP_DIR,SWAP_SIZE_GB ./provision.sh` to pass environment overrides through.

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

### Grafana Alloy Setup

```bash
cd deploy/supawave-host
sudo env \
  GCLOUD_HOSTED_METRICS_URL='https://.../api/prom/push' \
  GCLOUD_HOSTED_METRICS_ID='123456' \
  GCLOUD_HOSTED_LOGS_URL='https://.../loki/api/v1/push' \
  GCLOUD_HOSTED_LOGS_ID='123456' \
  GCLOUD_RW_API_KEY='glc_***' \
  ./install-grafana-alloy.sh

sudo env \
  GCLOUD_HOSTED_METRICS_URL='https://.../api/prom/push' \
  GCLOUD_HOSTED_METRICS_ID='123456' \
  GCLOUD_HOSTED_LOGS_URL='https://.../loki/api/v1/push' \
  GCLOUD_HOSTED_LOGS_ID='123456' \
  GCLOUD_RW_API_KEY='glc_***' \
  GCLOUD_SCRAPE_INTERVAL='60s' \
  WAVE_LOG_PATH='/home/deploy/supawave/shared/logs/wave*.log' \
  ./configure-grafana-alloy.sh
```

`WAVE_LOG_PATH` defaults to `/home/*/supawave/shared/logs/wave*.log` and controls which application log files Alloy tails for Loki ingestion.

## Troubleshooting

- Validation reports: `/var/log/wave-supawave/validate-*.log`
- Backups: `/var/backups/wave-supawave`
- If PAM limits appear inactive, start a new login shell (`su - $USER`) or re-SSH into the host.
- After rollback, run `sysctl --system` if custom sysctl files remain.
