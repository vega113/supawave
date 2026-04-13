#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH=${CONFIG_PATH:-/etc/alloy/config.alloy}
GCLOUD_HOSTED_METRICS_URL=${GCLOUD_HOSTED_METRICS_URL:-}
GCLOUD_HOSTED_METRICS_ID=${GCLOUD_HOSTED_METRICS_ID:-}
GCLOUD_HOSTED_LOGS_URL=${GCLOUD_HOSTED_LOGS_URL:-}
GCLOUD_HOSTED_LOGS_ID=${GCLOUD_HOSTED_LOGS_ID:-}
GCLOUD_RW_API_KEY=${GCLOUD_RW_API_KEY:-}
GCLOUD_SCRAPE_INTERVAL=${GCLOUD_SCRAPE_INTERVAL:-60s}
WAVE_METRICS_ADDRESS=${WAVE_METRICS_ADDRESS:-127.0.0.1:9898}
WAVE_LOG_PATH=${WAVE_LOG_PATH:-/home/*/supawave/shared/logs/wave-json*.log}
WAVE_TIMESTAMP_FORMAT=${WAVE_TIMESTAMP_FORMAT:-RFC3339Nano}

if [[ "$WAVE_TIMESTAMP_FORMAT" =~ [[:cntrl:]] ]]; then
  echo "Invalid WAVE_TIMESTAMP_FORMAT: contains control characters" >&2; exit 1
fi

case "$WAVE_TIMESTAMP_FORMAT" in
  RFC3339Nano|RFC3339|Unix|UnixMs|UnixUs|UnixNs) ;;
  *[\"\'\\$\`]*)
    echo "Invalid WAVE_TIMESTAMP_FORMAT: $WAVE_TIMESTAMP_FORMAT (contains unsafe characters)" >&2; exit 1 ;;
  "") echo "WAVE_TIMESTAMP_FORMAT must not be empty" >&2; exit 1 ;;
  *) ;;  # Allow custom Go time layouts (e.g. "2006-01-02T15:04:05Z07:00")
esac

required=(
  GCLOUD_HOSTED_METRICS_URL
  GCLOUD_HOSTED_METRICS_ID
  GCLOUD_HOSTED_LOGS_URL
  GCLOUD_HOSTED_LOGS_ID
  GCLOUD_RW_API_KEY
)

for key in "${required[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "Missing required environment variable: $key" >&2
    exit 1
  fi
done

echo "[grafana-alloy] Configuring Loki shipping for Wave JSON logs"
echo "[grafana-alloy]   metrics address: $WAVE_METRICS_ADDRESS"
echo "[grafana-alloy]   tail path: $WAVE_LOG_PATH"
echo "[grafana-alloy]   timestamp format: $WAVE_TIMESTAMP_FORMAT"

matched_wave_logs=()
while IFS= read -r matched_log; do
  matched_wave_logs+=("$matched_log")
done < <(compgen -G "$WAVE_LOG_PATH" || true)

if ((${#matched_wave_logs[@]} > 0)); then
  echo "[grafana-alloy]   currently matched files:"
  printf '  %s\n' "${matched_wave_logs[@]}"
else
  echo "[grafana-alloy]   warning: no files currently match $WAVE_LOG_PATH"
fi

sudo bash -c "cat > '$CONFIG_PATH' <<EOF
prometheus.exporter.self \"alloy_check\" { }

discovery.relabel \"alloy_check\" {
  targets = prometheus.exporter.self.alloy_check.targets

  rule {
    target_label = \"instance\"
    replacement  = constants.hostname
  }

  rule {
    target_label = \"alloy_hostname\"
    replacement  = constants.hostname
  }

  rule {
    target_label = \"job\"
    replacement  = \"integrations/alloy-check\"
  }
}

prometheus.scrape \"alloy_check\" {
  targets    = discovery.relabel.alloy_check.output
  forward_to = [prometheus.relabel.alloy_check.receiver]
  scrape_interval = \"$GCLOUD_SCRAPE_INTERVAL\"
}

prometheus.relabel \"alloy_check\" {
  forward_to = [prometheus.remote_write.metrics_service.receiver]

  rule {
    source_labels = [\"name\"]
    regex         = \"(prometheus_target_sync_length_seconds_sum|prometheus_target_scrapes_.|prometheus_target_interval.|prometheus_sd_discovered_targets|alloy_build.*|prometheus_remote_write_wal_samples_appended_total|process_start_time_seconds)\"
    action        = \"keep\"
  }
}

prometheus.remote_write \"metrics_service\" {
  endpoint {
    url = \"$GCLOUD_HOSTED_METRICS_URL\"

    basic_auth {
      username = \"$GCLOUD_HOSTED_METRICS_ID\"
      password = \"$GCLOUD_RW_API_KEY\"
    }
  }
}

prometheus.scrape \"supawave_app\" {
  targets = [{
    __address__ = \"$WAVE_METRICS_ADDRESS\",
    instance = constants.hostname,
    job = \"supawave/wave\",
  }]
  metrics_path = \"/metrics\"
  scrape_interval = \"$GCLOUD_SCRAPE_INTERVAL\"
  forward_to = [prometheus.remote_write.metrics_service.receiver]
}

loki.write \"grafana_cloud_loki\" {
  endpoint {
    url = \"$GCLOUD_HOSTED_LOGS_URL\"

    basic_auth {
      username = \"$GCLOUD_HOSTED_LOGS_ID\"
      password = \"$GCLOUD_RW_API_KEY\"
    }
  }
}

discovery.relabel \"integrations_node_exporter\" {
  targets = prometheus.exporter.unix.integrations_node_exporter.targets

  rule {
    target_label = \"instance\"
    replacement  = \"default\"
  }

  rule {
    target_label = \"job\"
    replacement = \"integrations/node_exporter\"
  }
}

prometheus.exporter.unix \"integrations_node_exporter\" {
  disable_collectors = [\"ipvs\", \"btrfs\", \"infiniband\", \"xfs\", \"zfs\"]

  filesystem {
    fs_types_exclude     = \"^(autofs|binfmt_misc|bpf|cgroup2?|configfs|debugfs|devpts|devtmpfs|tmpfs|fusectl|hugetlbfs|iso9660|mqueue|nsfs|overlay|proc|procfs|pstore|rpc_pipefs|securityfs|selinuxfs|squashfs|sysfs|tracefs)$\"
    mount_points_exclude = \"^/(dev|proc|run/credentials/.+|sys|var/lib/docker/.+)($|/)\"
    mount_timeout        = \"5s\"
  }

  netclass {
    ignored_devices = \"^(veth.|cali.|[a-f0-9]{15})\\$\"
  }

  netdev {
    device_exclude = \"^(veth.|cali.|[a-f0-9]{15})\\$\"
  }
}

prometheus.scrape \"integrations_node_exporter\" {
  targets    = discovery.relabel.integrations_node_exporter.output
  forward_to = [prometheus.relabel.integrations_node_exporter.receiver]
}

prometheus.relabel \"integrations_node_exporter\" {
  forward_to = [prometheus.remote_write.metrics_service.receiver]

  rule {
    source_labels = [\"name\"]
    regex         = \"node_scrape_collector_.+\"
    action        = \"drop\"
  }
}

loki.source.journal \"logs_integrations_integrations_node_exporter_journal_scrape\" {
  max_age       = \"24h0m0s\"
  relabel_rules = discovery.relabel.logs_integrations_integrations_node_exporter_journal_scrape.rules
  forward_to    = [loki.write.grafana_cloud_loki.receiver]
}

local.file_match \"logs_integrations_integrations_node_exporter_direct_scrape\" {
  path_targets = [{
    __address__ = \"localhost\",
    __path__    = \"/var/log/{syslog,messages,*.log}\",
    instance    = \"default\",
    job         = \"integrations/node_exporter\",
  }]
}

discovery.relabel \"logs_integrations_integrations_node_exporter_journal_scrape\" {
  targets = []

  rule {
    source_labels = [\"__journal__systemd_unit\"]
    target_label  = \"unit\"
  }

  rule {
    source_labels = [\"__journal__boot_id\"]
    target_label  = \"boot_id\"
  }

  rule {
    source_labels = [\"__journal__transport\"]
    target_label  = \"transport\"
  }

  rule {
    source_labels = [\"__journal_priority_keyword\"]
    target_label  = \"level\"
  }
}

loki.source.file \"logs_integrations_integrations_node_exporter_direct_scrape\" {
  targets    = local.file_match.logs_integrations_integrations_node_exporter_direct_scrape.targets
  forward_to = [loki.write.grafana_cloud_loki.receiver]
}

// ── SupaWave application logs ──────────────────────────────────────────
local.file_match \"supawave_logs\" {
  path_targets = [{
    __address__  = \"localhost\",
    __path__     = \"$WAVE_LOG_PATH\",
    job          = \"supawave/wave\",
    service_name = \"supawave\",
    instance     = constants.hostname,
  }]
}

loki.process \"supawave_logs\" {
  forward_to = [loki.write.grafana_cloud_loki.receiver]

  stage.json {
    expressions = {
      level   = \"level\",
      logger  = \"logger_name\",
      thread  = \"thread_name\",
      message = \"message\",
      participant = \"participantId\",
      wave    = \"waveId\",
      timestamp = \"\",
    }
  }

  stage.labels {
    values = {
      level       = \"\",
      logger      = \"\",
      thread      = \"\",
    }
  }

  stage.timestamp {
    source = \"timestamp\"
    format = \"$WAVE_TIMESTAMP_FORMAT\"
  }
}

loki.source.file \"supawave_logs\" {
  targets    = local.file_match.supawave_logs.targets
  forward_to = [loki.process.supawave_logs.receiver]
}
EOF"

sudo chown root:alloy "$CONFIG_PATH"
sudo chmod 0640 "$CONFIG_PATH"

sudo systemctl restart alloy.service
sudo systemctl status --no-pager alloy.service

if command -v logger >/dev/null 2>&1; then
  logger -t grafana-alloy-config -- \
    "Loki shipping enabled; tail path=$WAVE_LOG_PATH timestamp_format=$WAVE_TIMESTAMP_FORMAT" || true
fi
