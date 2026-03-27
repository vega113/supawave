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

readonly REMOTE_FLAGS_URL="https://supawave.ai/admin/flags"
readonly LOCAL_FLAGS_URL="http://localhost:9898/admin/flags"
readonly SESSION_COOKIE_FILE="${HOME}/.wave-session"

BASE_URL="${REMOTE_FLAGS_URL}"
SESSION_COOKIE=""
RESPONSE_STATUS=""
RESPONSE_BODY=""

usage() {
  cat <<'EOF'
Usage:
  scripts/feature-flag.sh [--local] set <name> <description> [--allowed user1,user2] [--global]
  scripts/feature-flag.sh [--local] enable <name>
  scripts/feature-flag.sh [--local] disable <name>
  scripts/feature-flag.sh [--local] delete <name>
  scripts/feature-flag.sh [--local] list
  scripts/feature-flag.sh [--local] get <name>

Options:
  --local    Use http://localhost:9898/admin/flags instead of https://supawave.ai/admin/flags
  --allowed  Comma-separated participant addresses allowed when the flag is not global
  --global   Mark the flag enabled for everyone
EOF
}

fail() {
  printf 'Error: %s\n' "$1" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

load_session_cookie() {
  if [[ ! -f "${SESSION_COOKIE_FILE}" ]]; then
    fail "Session cookie file not found at ${SESSION_COOKIE_FILE}. Run scripts/save-wave-session.sh."
  fi

  SESSION_COOKIE="$(tr -d '\r\n' < "${SESSION_COOKIE_FILE}")"

  if [[ -z "${SESSION_COOKIE}" ]]; then
    fail "Session cookie file is empty: ${SESSION_COOKIE_FILE}"
  fi

  if [[ "${SESSION_COOKIE}" != *=* ]]; then
    fail "Session cookie must be saved as NAME=value, for example JSESSIONID=abc123"
  fi
}

perform_request() {
  local method url body temp_file curl_status

  method="$1"
  url="$2"
  body="${3-}"
  temp_file="$(mktemp)"

  if [[ -n "${body}" ]]; then
    curl_status="$(
      curl -sS \
        -X "${method}" \
        --cookie "${SESSION_COOKIE}" \
        -H 'Accept: application/json' \
        -H 'Content-Type: application/json' \
        --data "${body}" \
        -o "${temp_file}" \
        -w '%{http_code}' \
        "${url}"
    )" || {
      rm -f "${temp_file}"
      fail "Request failed for ${method} ${url}"
    }
  else
    curl_status="$(
      curl -sS \
        -X "${method}" \
        --cookie "${SESSION_COOKIE}" \
        -H 'Accept: application/json' \
        -o "${temp_file}" \
        -w '%{http_code}' \
        "${url}"
    )" || {
      rm -f "${temp_file}"
      fail "Request failed for ${method} ${url}"
    }
  fi

  RESPONSE_STATUS="${curl_status}"
  RESPONSE_BODY="$(cat "${temp_file}")"
  rm -f "${temp_file}"
}

extract_error_message() {
  local error_message

  error_message="$(printf '%s' "${RESPONSE_BODY}" | jq -r '.error // empty' 2>/dev/null || true)"

  if [[ -n "${error_message}" ]]; then
    printf '%s\n' "${error_message}"
  else
    printf 'HTTP %s\n' "${RESPONSE_STATUS}"
  fi
}

require_success() {
  local action error_message

  action="$1"

  if [[ ! "${RESPONSE_STATUS}" =~ ^2 ]]; then
    error_message="$(extract_error_message)"
    fail "${action} failed: ${error_message}"
  fi
}

uri_encode() {
  jq -rn --arg value "$1" '$value | @uri'
}

build_payload() {
  local name description enabled allowed_users

  name="$1"
  description="$2"
  enabled="$3"
  allowed_users="$4"

  jq -cn \
    --arg name "${name}" \
    --arg description "${description}" \
    --arg enabled "${enabled}" \
    --arg allowedUsers "${allowed_users}" \
    '{
      name: $name,
      description: $description,
      enabled: $enabled,
      allowedUsers: $allowedUsers
    }'
}

list_flags() {
  perform_request GET "${BASE_URL}"
  require_success "List flags"
  printf '%s\n' "${RESPONSE_BODY}" | jq '.flags'
}

fetch_flag_json() {
  local name flag_json

  name="$1"
  perform_request GET "${BASE_URL}"
  require_success "Get flag ${name}"

  flag_json="$(
    printf '%s' "${RESPONSE_BODY}" |
      jq -ce --arg name "${name}" '.flags | map(select(.name == $name)) | .[0] // empty'
  )" || true

  if [[ -z "${flag_json}" ]]; then
    fail "Flag \"${name}\" was not found"
  fi

  printf '%s\n' "${flag_json}"
}

save_flag() {
  local name description enabled allowed_users payload

  name="$1"
  description="$2"
  enabled="$3"
  allowed_users="$4"
  payload="$(build_payload "${name}" "${description}" "${enabled}" "${allowed_users}")"

  perform_request POST "${BASE_URL}" "${payload}"
  require_success "Save flag ${name}"
}

set_flag() {
  local name description enabled allowed_users

  name="${1-}"
  description="${2-}"
  enabled="false"
  allowed_users=""

  if [[ -z "${name}" || -z "${description}" ]]; then
    fail "Usage: scripts/feature-flag.sh set <name> <description> [--allowed user1,user2] [--global]"
  fi

  shift 2

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --allowed)
        if [[ $# -lt 2 ]]; then
          fail "Missing value after --allowed"
        fi
        allowed_users="$2"
        shift 2
        ;;
      --global)
        enabled="true"
        shift
        ;;
      *)
        fail "Unknown option for set: $1"
        ;;
    esac
  done

  save_flag "${name}" "${description}" "${enabled}" "${allowed_users}"
  printf 'Success: flag "%s" saved.\n' "${name}"
}

enable_flag() {
  local name flag_json description allowed_users

  name="${1-}"

  if [[ -z "${name}" ]]; then
    fail "Usage: scripts/feature-flag.sh enable <name>"
  fi

  flag_json="$(fetch_flag_json "${name}")"
  description="$(printf '%s' "${flag_json}" | jq -r '.description // ""')"
  allowed_users="$(printf '%s' "${flag_json}" | jq -r '.allowedUsers // ""')"

  save_flag "${name}" "${description}" "true" "${allowed_users}"
  printf 'Success: flag "%s" enabled globally.\n' "${name}"
}

disable_flag() {
  local name flag_json description allowed_users

  name="${1-}"

  if [[ -z "${name}" ]]; then
    fail "Usage: scripts/feature-flag.sh disable <name>"
  fi

  flag_json="$(fetch_flag_json "${name}")"
  description="$(printf '%s' "${flag_json}" | jq -r '.description // ""')"
  allowed_users="$(printf '%s' "${flag_json}" | jq -r '.allowedUsers // ""')"

  save_flag "${name}" "${description}" "false" "${allowed_users}"
  printf 'Success: flag "%s" disabled globally.\n' "${name}"
}

delete_flag() {
  local name encoded_name

  name="${1-}"

  if [[ -z "${name}" ]]; then
    fail "Usage: scripts/feature-flag.sh delete <name>"
  fi

  encoded_name="$(uri_encode "${name}")"
  perform_request DELETE "${BASE_URL}?name=${encoded_name}"
  require_success "Delete flag ${name}"
  printf 'Success: flag "%s" deleted.\n' "${name}"
}

get_flag() {
  local name

  name="${1-}"

  if [[ -z "${name}" ]]; then
    fail "Usage: scripts/feature-flag.sh get <name>"
  fi

  fetch_flag_json "${name}" | jq '.'
}

main() {
  local filtered_args command
  filtered_args=()

  require_command curl
  require_command jq

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --local)
        BASE_URL="${LOCAL_FLAGS_URL}"
        shift
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        filtered_args+=("$1")
        shift
        ;;
    esac
  done

  if [[ ${#filtered_args[@]} -eq 0 ]]; then
    usage
    exit 1
  fi

  load_session_cookie

  command="${filtered_args[0]}"

  case "${command}" in
    set)
      set_flag "${filtered_args[@]:1}"
      ;;
    enable)
      enable_flag "${filtered_args[@]:1}"
      ;;
    disable)
      disable_flag "${filtered_args[@]:1}"
      ;;
    delete)
      delete_flag "${filtered_args[@]:1}"
      ;;
    list)
      if [[ ${#filtered_args[@]} -ne 1 ]]; then
        fail "Usage: scripts/feature-flag.sh list"
      fi
      list_flags
      ;;
    get)
      get_flag "${filtered_args[@]:1}"
      ;;
    *)
      usage >&2
      fail "Unknown command: ${command}"
      ;;
  esac
}

main "$@"
