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
  WAVE_LOG_PATH='/home/deploy/supawave/shared/logs/wave-json*.log' \
  ./configure-grafana-alloy.sh
```

`WAVE_LOG_PATH` defaults to `/home/*/supawave/shared/logs/wave-json*.log` and controls which JSON-structured application log files Alloy tails for Loki ingestion. Only `wave-json*.log` files contain structured JSON; tailing `wave*.log` would mix plain-text and JSON lines, breaking Alloy's `stage.json` parsing.

### Loki Query Contract

- Recommended service selector: `{service_name="supawave"}`
- Low-level fallback selector: `{job="supawave/wave"}`
- Alloy promotes these labels from each JSON log line: `level`, `logger`, `thread`
- Fields such as `participantId` and `waveId` remain in the JSON payload; inspect or filter them with `| json` in Grafana/Loki instead of expecting them as labels

## Troubleshooting

- Validation reports: `/var/log/wave-supawave/validate-*.log`
- Backups: `/var/backups/wave-supawave`
- If PAM limits appear inactive, start a new login shell (`su - $USER`) or re-SSH into the host.
- After rollback, run `sysctl --system` if custom sysctl files remain.
- Confirm the live Alloy tail path and parser: `sudo grep -nE '__path__\\s*=|job\\s*=|format\\s*=|level\\s*=|logger\\s*=|thread\\s*=' /etc/alloy/config.alloy`
- Confirm Wave is still writing structured JSON: `tail -5 /home/ubuntu/supawave/shared/logs/wave-json.log | jq -c '{timestamp,level,logger_name,thread_name,message}'`
- Check Alloy shipping errors: `sudo journalctl -u alloy --since "15 minutes ago" --no-pager | grep -iE 'error|401|invalid token|loki|prometheus'`
- Check Alloy write counters: `curl -fsS http://127.0.0.1:12345/metrics | grep -E 'loki_write_sent_entries_total|loki_write_dropped_entries_total|prometheus_remote_storage_samples_failed_total'`
- Check that the SupaWave file tailer is active: `curl -fsS http://127.0.0.1:12345/metrics | grep 'loki_source_file_files_active_total{component_id="loki.source.file.supawave_logs"'`
- If Wave JSON logs exist, `/etc/alloy/config.alloy` still points at `wave-json*.log`, and Alloy logs show repeated `401 Unauthorized` / `authentication error: invalid token`, the remaining failure is Grafana Cloud authentication, not Wave log emission or Alloy file parsing.
- Refresh and redeploy these values when that happens: `GRAFANA_ALLOY_ENABLE=true`, `GCLOUD_HOSTED_METRICS_URL`, `GCLOUD_HOSTED_METRICS_ID`, `GCLOUD_HOSTED_LOGS_URL`, `GCLOUD_HOSTED_LOGS_ID`, and `GCLOUD_RW_API_KEY`.
- Healthy post-fix signals: `loki_source_file_files_active_total{component_id="loki.source.file.supawave_logs"}` is `>= 1`, `loki_write_sent_entries_total` rises above zero, `loki_write_dropped_entries_total{reason="ingester_error"}` stops increasing, `prometheus_remote_storage_samples_failed_total` stays at zero, and `{service_name="supawave"}` starts returning log lines in Grafana.

### Recommended Alerts

Create these Grafana-managed alerts against the Alloy metrics stream and Loki logs after rollout:

- SupaWave log tailer inactive:
  `max_over_time(loki_source_file_files_active_total{component_id="loki.source.file.supawave_logs"}[10m]) < 1`
- Loki write failures:
  `increase(loki_write_dropped_entries_total{component_id="loki.write.grafana_cloud_loki",reason="ingester_error"}[10m]) > 0`
- Prometheus remote-write failures:
  `increase(prometheus_remote_storage_samples_failed_total{component_id="prometheus.remote_write.metrics_service"}[10m]) > 0`
- SupaWave application error logs:
  `sum(count_over_time({service_name="supawave",level="ERROR"}[5m])) > 0`
