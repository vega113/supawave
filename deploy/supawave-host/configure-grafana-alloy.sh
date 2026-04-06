#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH=${CONFIG_PATH:-/etc/alloy/config.alloy}
GCLOUD_HOSTED_METRICS_URL=${GCLOUD_HOSTED_METRICS_URL:-}
GCLOUD_HOSTED_METRICS_ID=${GCLOUD_HOSTED_METRICS_ID:-}
GCLOUD_HOSTED_LOGS_URL=${GCLOUD_HOSTED_LOGS_URL:-}
GCLOUD_HOSTED_LOGS_ID=${GCLOUD_HOSTED_LOGS_ID:-}
GCLOUD_RW_API_KEY=${GCLOUD_RW_API_KEY:-}
GCLOUD_SCRAPE_INTERVAL=${GCLOUD_SCRAPE_INTERVAL:-60s}
WAVE_LOG_PATH=${WAVE_LOG_PATH:-/home/*/supawave/shared/logs/wave*.log}

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
    address = \"localhost\",
    path    = \"/var/log/{syslog,messages,*.log}\",
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
    address  = \"localhost\",
    path     = \"$WAVE_LOG_PATH\",
    job      = \"supawave/wave\",
    instance = constants.hostname,
  }]
}

loki.process \"supawave_logs\" {
  forward_to = [loki.write.grafana_cloud_loki.receiver]

  stage.regex {
    expression = \"^(?P<timestamp>\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{3}[^\\\\s]+)\\\\s+\\\\[(?P<thread>[^\\\\]]+)\\\\]\\\\s+(?P<level>\\\\w+)\\\\s+(?P<logger>\\\\S+)\\\\s+-\\\\s+(?P<message>.*)\"
  }

  stage.labels {
    values = {
      level  = \"\",
      logger = \"\",
      thread = \"\",
    }
  }

  stage.timestamp {
    source = \"timestamp\"
    format = \"2006-01-02T15:04:05.000Z07:00\"
  }

  stage.label_drop {
    values = [\"timestamp\"]
  }
}

loki.source.file \"supawave_logs\" {
  targets    = local.file_match.supawave_logs.targets
  forward_to = [loki.process.supawave_logs.receiver]
}
EOF"

sudo chmod 600 "$CONFIG_PATH"

sudo systemctl restart alloy.service
sudo systemctl status --no-pager alloy.service
