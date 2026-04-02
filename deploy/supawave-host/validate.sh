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
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$1" | tee -a "$REPORT_FILE"
}

check_command() {
  local name=$1
  if command -v "$name" >/dev/null 2>&1; then
    return 0
  fi
  log "FAIL: missing command $name"
  return 1
}

check_sudo() {
  if [[ ${EUID:-$(id -u)} -eq 0 ]]; then
    return 0
  fi
  if sudo -n true 2>/dev/null; then
    return 0
  fi
  log "FAIL: sudo not available without prompt"
  return 1
}

check_disk() {
  local path=$1
  local required_mb=$2
  local free_mb
  free_mb=$(df -Pm "$path" | awk 'NR==2 {print $4}')
  if [[ "$free_mb" -lt "$required_mb" ]]; then
    log "FAIL: ${free_mb}MB free on $path (need ${required_mb}MB)"
    return 1
  fi
  return 0
}

check_docker() {
  if ! check_command docker; then
    return 1
  fi
  if docker info >/dev/null 2>&1; then
    return 0
  fi
  log "FAIL: docker daemon not reachable"
  return 1
}

check_mongo() {
  if docker ps --format '{{.Names}}' | grep -q '^supawave-mongo-1$'; then
    if docker exec supawave-mongo-1 mongosh --quiet --eval 'db.runCommand({ping:1}).ok' >/dev/null 2>&1; then
      return 0
    fi
    log "FAIL: supawave-mongo-1 ping failed"
    return 1
  fi
  log "WARN: supawave-mongo-1 container not running"
  return 0
}

check_swap() {
  local swap_path="/swapfile"
  local swap_size_gb=${SWAP_SIZE_GB:-32}
  local expected_bytes=$((swap_size_gb * 1024 * 1024 * 1024))
  local size
  size=$(swapon --show=NAME,SIZE --bytes --noheadings 2>/dev/null | awk '$1 == "'"$swap_path"'" {print $2}')
  if [[ -z "$size" ]]; then
    log "FAIL: swapfile not active"
    return 1
  fi
  if (( size < expected_bytes - 4096 )); then
    log "FAIL: swapfile size ${size} below expected ${expected_bytes}"
    return 1
  fi
  log "OK: swapfile active (${size} bytes)"
  return 0
}

check_sysctl() {
  local key=$1
  local expected=$2
  local current
  current=$(sysctl -n "$key" 2>/dev/null || true)
  if [[ "$current" != "$expected" ]]; then
    log "FAIL: sysctl $key expected $expected found ${current:-unset}"
    return 1
  fi
  return 0
}

check_ulimits() {
  local hard_nofile
  hard_nofile=$(ulimit -Hn 2>/dev/null || true)
  local hard_nproc
  hard_nproc=$(ulimit -Hu 2>/dev/null || true)
  if [[ -z "$hard_nofile" || ( "$hard_nofile" != "unlimited" && "$hard_nofile" =~ ^[0-9]+$ && "$hard_nofile" -lt 262144 ) ]]; then
    log "WARN: hard nofile is ${hard_nofile:-unset}; open a new login session to pick up PAM limits"
  fi
  if [[ -z "$hard_nproc" || ( "$hard_nproc" != "unlimited" && "$hard_nproc" =~ ^[0-9]+$ && "$hard_nproc" -lt 65536 ) ]]; then
    log "WARN: hard nproc is ${hard_nproc:-unset}; open a new login session to pick up PAM limits"
  fi
}

check_services() {
  local expected=("supawave-wave-blue-1" "supawave-mongo-1" "supawave-caddy-1")
  local missing=0
  local name
  for name in "${expected[@]}"; do
    if ! docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
      log "WARN: container ${name} not running"
      missing=1
    fi
  done
  return "$missing"
}

check_health() {
  local code
  code=$(curl -fsSI -w "%{http_code}" -o /dev/null --max-time 3 -H "Host: supawave.ai" http://127.0.0.1/ 2>/dev/null || true)
  if [[ "$code" == "200" || "$code" == "301" || "$code" == "302" ]]; then
    return 0
  fi
  log "WARN: local HTTP probe returned ${code:-none}"
  return 0
}

run_checks() {
  local mode=$1
  local failures=0

  if [[ "$mode" == "pre" || "$mode" == "all" ]]; then
    log "Running pre-flight checks"
    local swap_size_gb=${SWAP_SIZE_GB:-32}
    local disk_required_mb=$(((swap_size_gb + 1) * 1024))
    check_sudo || failures=$((failures + 1))
    command -v ssh >/dev/null 2>&1 || log "WARN: ssh not found (not required for provisioning)"
    command -v java >/dev/null 2>&1 || log "WARN: java not found (not required if Wave runs in Docker)"
    check_docker || failures=$((failures + 1))
    check_disk "/" "$disk_required_mb" || failures=$((failures + 1))
    check_mongo || failures=$((failures + 1))
  fi

  if [[ "$mode" == "post" || "$mode" == "all" ]]; then
    log "Running post-flight checks"
    check_swap || failures=$((failures + 1))
    check_sysctl "fs.file-max" "2097152" || failures=$((failures + 1))
    check_sysctl "fs.nr_open" "1048576" || failures=$((failures + 1))
    check_sysctl "vm.swappiness" "10" || failures=$((failures + 1))
    check_sysctl "net.core.somaxconn" "65535" || failures=$((failures + 1))
    check_ulimits
    check_services || true
    check_health || true
  fi

  if [[ "$failures" -ne 0 ]]; then
    log "Validation failed with ${failures} blocking issue(s)"
    return 1
  fi
  log "Validation completed"
  return 0
}

main() {
  require_root "$@"
  local mode=${1:-all}
  case "$mode" in
    pre|post|all) ;;
    *) echo "Usage: $0 [pre|post|all]" >&2; exit 1 ;;
  esac

  mkdir -p /var/log/wave-supawave
  export REPORT_FILE="/var/log/wave-supawave/validate-$(date +%Y%m%d%H%M%S).log"
  touch "$REPORT_FILE"

  run_checks "$mode"
}

main "$@"
