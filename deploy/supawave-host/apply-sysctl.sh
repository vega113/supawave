#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

require_root() {
  if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
    exec sudo -- "$0" "$@"
  fi
}

log() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$1"
}

backup_file() {
  local src=$1
  local name=$2
  local backup_dir=$3
  if [[ -f "$src" ]]; then
    mkdir -p "$backup_dir"
    local fixed="$backup_dir/$name"
    if [[ ! -f "$fixed" ]]; then
      cp "$src" "$fixed"
    fi
    cp "$src" "$backup_dir/${name}.bak.$(date +%Y%m%d%H%M%S)"
  fi
}

validate_sysctl() {
  local key=$1
  local expected=$2
  local current
  current=$(sysctl -n "$key" 2>/dev/null || true)
  if [[ "$current" != "$expected" ]]; then
    log "FAIL: $key expected $expected but found ${current:-unset}"
    exit 1
  fi
}

main() {
  require_root "$@"

  local script_dir
  script_dir=$(cd "$(dirname "$0")" && pwd)
  local repo_root
  repo_root=$(cd "$script_dir/../.." && pwd)

  local co_located="$script_dir/sysctl-tuning.conf"
  local repo_path="$repo_root/deploy/production/sysctl-tuning.conf"
  local source_conf=""
  if [[ -f "$co_located" ]]; then
    source_conf="$co_located"
  elif [[ -f "$repo_path" ]]; then
    source_conf="$repo_path"
  else
    log "Missing source sysctl config (checked $co_located and $repo_path)"
    exit 1
  fi
  local target_conf="/etc/sysctl.d/99-wave.conf"
  local backup_dir="${BACKUP_DIR:-/var/backups/wave-supawave}"

  backup_file "/etc/sysctl.conf" "sysctl.conf.orig" "$backup_dir"
  backup_file "$target_conf" "sysctl-99-wave.conf.orig" "$backup_dir"

  if [[ -f "$target_conf" ]] && cmp -s "$source_conf" "$target_conf"; then
    log "sysctl config already matches target, skipping copy"
  else
    install -m 644 "$source_conf" "$target_conf"
    log "Installed $target_conf"
  fi

  sysctl --system >/dev/null
  log "Reloaded sysctl settings"

  validate_sysctl "fs.file-max" "2097152"
  validate_sysctl "fs.nr_open" "1048576"
  validate_sysctl "vm.swappiness" "10"
  validate_sysctl "net.core.somaxconn" "65535"
  log "sysctl validation succeeded"
}

main "$@"
