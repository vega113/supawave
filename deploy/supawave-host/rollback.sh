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

restore_file() {
  local target=$1
  local name=$2
  local backup_dir=$3
  local backup="$backup_dir/$name"
  if [[ -f "$backup" ]]; then
    cp "$backup" "$target"
    log "Restored $target from $backup"
    return 0
  fi
  log "WARN: no backup found for $target"
  return 1
}

remove_wave_fstab_entry() {
  if [[ -f /etc/fstab ]]; then
    if awk '$1 == "/swapfile" && $3 == "swap" { found = 1 } END { exit !found }' /etc/fstab; then
      local tmp
      tmp=$(mktemp)
      awk '!($1 == "/swapfile" && $3 == "swap")' /etc/fstab > "$tmp"
      cat "$tmp" > /etc/fstab
      rm -f "$tmp"
      log "Removed swapfile entries from /etc/fstab"
    fi
  fi
}

main() {
  require_root "$@"

  local backup_dir="${BACKUP_DIR:-/var/backups/wave-supawave}"

  if swapon --show=NAME --noheadings 2>/dev/null | grep -q '^/swapfile$'; then
    swapoff /swapfile || true
    log "Disabled swapfile"
  fi

  restore_file "/etc/sysctl.conf" "sysctl.conf.orig" "$backup_dir" || true
  restore_file "/etc/sysctl.d/99-wave.conf" "sysctl-99-wave.conf.orig" "$backup_dir" || rm -f /etc/sysctl.d/99-wave.conf
  restore_file "/etc/security/limits.d/99-wave.conf" "limits-99-wave.conf.orig" "$backup_dir" || rm -f /etc/security/limits.d/99-wave.conf
  restore_file "/etc/pam.d/common-session" "common-session.orig" "$backup_dir" || true
  restore_file "/etc/pam.d/common-session-noninteractive" "common-session-noninteractive.orig" "$backup_dir" || true
  restore_file "/etc/fstab" "fstab.orig" "$backup_dir" || true
  remove_wave_fstab_entry

  if [[ -f /swapfile ]]; then
    rm -f /swapfile
    log "Removed /swapfile"
  fi

  sysctl --system >/dev/null || true
  log "Rollback complete; review limits and start new sessions to reapply PAM settings"
}

main "$@"
