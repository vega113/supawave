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

ensure_disk_space() {
  local path=$1
  local required_mb=$2
  local free_mb
  free_mb=$(df -Pm "$path" | awk 'NR==2 {print $4}')
  if [[ "$free_mb" -lt "$required_mb" ]]; then
    log "FAIL: insufficient disk at $path (${free_mb}MB free, need ${required_mb}MB)"
    exit 1
  fi
}

activate_swapfile() {
  local swap_path=$1
  mkswap "$swap_path" >/dev/null
  swapon "$swap_path"
}

ensure_fstab_entry() {
  local swap_path=$1
  if ! grep -Eq "^[[:space:]]*${swap_path}[[:space:]]+none[[:space:]]+swap[[:space:]]" /etc/fstab; then
    echo "$swap_path none swap sw 0 0" >>/etc/fstab
    log "Added $swap_path to /etc/fstab"
  fi
}

validate_swap() {
  local swap_path=$1
  local expected_bytes=$2
  local size
  size=$(swapon --show=NAME,SIZE --bytes --noheadings 2>/dev/null | awk '$1 == "'"$swap_path"'" {print $2}')
  if [[ -z "$size" ]]; then
    log "FAIL: $swap_path not active"
    exit 1
  fi
  if [[ "$size" -lt "$expected_bytes" ]]; then
    log "FAIL: $swap_path size ${size}B below expected ${expected_bytes}B"
    exit 1
  fi
  log "Swap active at $swap_path (${size} bytes)"
}

main() {
  require_root "$@"

  local swap_path="/swapfile"
  local swap_size_gb="${SWAP_SIZE_GB:-32}"
  local swap_bytes=$((swap_size_gb * 1024 * 1024 * 1024))
  local backup_dir="${BACKUP_DIR:-/var/backups/wave-supawave}"
  local required_mb=$((swap_size_gb * 1024 + 1024))

  backup_file "/etc/fstab" "fstab.orig" "$backup_dir"
  backup_file "/etc/sysctl.d/99-wave.conf" "99-wave.conf.orig" "$backup_dir"
  ensure_disk_space "$(dirname "$swap_path")" "$required_mb"

  if swapon --show=NAME --noheadings 2>/dev/null | grep -q "^$swap_path$"; then
    local current_bytes
    current_bytes=$(swapon --show=NAME,SIZE --bytes --noheadings 2>/dev/null | awk '$1 == "'"$swap_path"'" {print $2}')
    if [[ -n "$current_bytes" && "$current_bytes" -ge "$swap_bytes" ]]; then
      log "$swap_path already active (${current_bytes} bytes)"
    else
      swapoff "$swap_path"
      fallocate -l "${swap_size_gb}G" "$swap_path"
      chmod 600 "$swap_path"
      activate_swapfile "$swap_path"
      log "Recreated $swap_path to ${swap_size_gb}G"
    fi
  else
    fallocate -l "${swap_size_gb}G" "$swap_path"
    chmod 600 "$swap_path"
    activate_swapfile "$swap_path"
    log "Created $swap_path (${swap_size_gb}G)"
  fi

  ensure_fstab_entry "$swap_path"
  if [[ -f /etc/sysctl.d/99-wave.conf ]]; then
    if ! grep -Eq '^[[:space:]]*vm.swappiness[[:space:]]*=' /etc/sysctl.d/99-wave.conf; then
      echo "vm.swappiness = 10" >>/etc/sysctl.d/99-wave.conf
      log "Persisted vm.swappiness=10 in /etc/sysctl.d/99-wave.conf"
    fi
  else
    echo "vm.swappiness = 10" >/etc/sysctl.d/99-wave.conf
    log "Created /etc/sysctl.d/99-wave.conf with vm.swappiness=10"
  fi
  sysctl -w vm.swappiness=10 >/dev/null

  validate_swap "$swap_path" "$swap_bytes"
  log "Swap setup complete (swappiness=$(cat /proc/sys/vm/swappiness))"
}

main "$@"
