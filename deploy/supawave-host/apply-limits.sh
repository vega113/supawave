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

render_limits_block() {
  local source_conf=$1
  local block
  block=$(awk 'NF && $1 !~ /^#/ {print}' "$source_conf")
  printf "# BEGIN wave-supawave limits\n%s\n# END wave-supawave limits\n" "$block"
}

remove_existing_block() {
  local target=$1
  local temp=$2
  if grep -q "# BEGIN wave-supawave limits" "$target"; then
    awk '
      /# BEGIN wave-supawave limits/ {skip=1}
      skip && /# END wave-supawave limits/ {skip=0; next}
      !skip {print}
    ' "$target" >"$temp"
  else
    cat "$target" >"$temp"
  fi
}

ensure_pam_limits() {
  local pam_file=$1
  if ! grep -Eq '^[[:space:]]*session[[:space:]]+required[[:space:]]+pam_limits.so' "$pam_file"; then
    echo "session required pam_limits.so" >>"$pam_file"
    log "Appended pam_limits.so to $pam_file"
  fi
}

validate_limits() {
  local limit_file=$1
  local expected=(
    "wave soft nofile 131072"
    "wave hard nofile 262144"
    "wave soft nproc 65536"
    "wave hard nproc 65536"
    "root soft nofile 262144"
    "root hard nofile 262144"
    "root soft nproc 65536"
    "root hard nproc 65536"
    "* soft nofile 65536"
    "* hard nofile 131072"
    "* soft nproc 32768"
    "* hard nproc 65536"
  )
  local line
  for line in "${expected[@]}"; do
    if ! grep -q -F "$line" "$limit_file"; then
      log "FAIL: missing limits line: $line"
      exit 1
    fi
  done
  log "limits config contains expected entries"
}

main() {
  require_root "$@"

  local script_dir
  script_dir=$(cd "$(dirname "$0")" && pwd)
  local repo_root
  repo_root=$(cd "$script_dir/../.." && pwd)

  local source_conf="$repo_root/deploy/production/limits.conf.prod"
  local limits_dir="/etc/security/limits.d"
  local target_conf="$limits_dir/99-wave.conf"
  local backup_dir="${BACKUP_DIR:-/var/backups/wave-supawave}"

  if [[ ! -f "$source_conf" ]]; then
    log "Missing source limits config: $source_conf"
    exit 1
  fi

  mkdir -p "$limits_dir"
  touch "$target_conf"
  backup_file "$target_conf" "99-wave.conf.orig" "$backup_dir"
  backup_file "/etc/pam.d/common-session" "common-session.orig" "$backup_dir"
  backup_file "/etc/pam.d/common-session-noninteractive" "common-session-noninteractive.orig" "$backup_dir"

  local temp
  temp=$(mktemp)
  remove_existing_block "$target_conf" "$temp"
  render_limits_block "$source_conf" >>"$temp"
  install -m 644 "$temp" "$target_conf"
  rm -f "$temp"
  log "Updated $target_conf"

  ensure_pam_limits "/etc/pam.d/common-session"
  ensure_pam_limits "/etc/pam.d/common-session-noninteractive"

  validate_limits "$target_conf"

  local hard_nofile
  hard_nofile=$(ulimit -Hn 2>/dev/null || true)
  if [[ -n "$hard_nofile" && "$hard_nofile" =~ ^[0-9]+$ ]]; then
    if (( hard_nofile < 262144 )); then
      log "WARN: current hard nofile is $hard_nofile; open a new login session to pick up limits"
    else
      log "Limit check: hard nofile $hard_nofile"
    fi
  else
    log "Limit check: hard nofile ${hard_nofile:-unknown}"
  fi
}

main "$@"
