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

snapshot_files() {
  local backup_dir=$1
  local snapshot_dir="$backup_dir/snapshot-$(date +%Y%m%d%H%M%S)"
  mkdir -p "$snapshot_dir"
  local paths=("/etc/sysctl.conf" "/etc/sysctl.d/99-wave.conf" "/etc/security/limits.conf" "/etc/pam.d/common-session" "/etc/pam.d/common-session-noninteractive" "/etc/fstab")
  local path
  for path in "${paths[@]}"; do
    if [[ -f "$path" ]]; then
      cp "$path" "$snapshot_dir/$(basename "$path")"
    fi
  done
  log "Captured rollback snapshot at $snapshot_dir"
}

main() {
  require_root "$@"

  local script_dir
  script_dir=$(cd "$(dirname "$0")" && pwd)
  local backup_dir="${BACKUP_DIR:-/var/backups/wave-supawave}"

  snapshot_files "$backup_dir"

  log "Pre-flight validation"
  "$script_dir/validate.sh" pre

  log "Applying sysctl tuning"
  "$script_dir/apply-sysctl.sh"

  log "Applying PAM limits"
  "$script_dir/apply-limits.sh"

  log "Setting up swap"
  "$script_dir/setup-swap.sh"

  log "Post-flight validation"
  "$script_dir/validate.sh" post

  log "Provisioning complete"
}

main "$@"
