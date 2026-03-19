#!/usr/bin/env bash
#
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

zone_name="${ZONE_NAME:-supawave.ai}"
origin_ip="${ORIGIN_IP:-86.48.3.138}"
api_base="https://api.cloudflare.com/client/v4"

require_env() {
  : "${CLOUDFLARE_API_TOKEN:?CLOUDFLARE_API_TOKEN is required}"
  : "${CLOUDFLARE_API_ACCOUNT_ID:?CLOUDFLARE_API_ACCOUNT_ID is required}"
}

cf_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  if [[ -n "$body" ]]; then
    curl -fsS -X "$method" \
      -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
      -H "Content-Type: application/json" \
      --data "$body" \
      "${api_base}/${path}"
  else
    curl -fsS \
      -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
      -H "Content-Type: application/json" \
      "${api_base}/${path}"
  fi
}

create_dns_record() {
  local zone_id="$1"
  local record_type="$2"
  local record_name="$3"
  local record_content="$4"
  local record_proxied="${5:-false}"

  local existing
  existing="$(
    cf_request "GET" "zones/${zone_id}/dns_records?type=${record_type}&name=${record_name}&per_page=1"
  )"

  local record_id
  record_id="$(jq -r '.result[0].id // empty' <<<"$existing")"

  local payload
  payload="$(
    jq -nc \
      --arg type "$record_type" \
      --arg name "$record_name" \
      --arg content "$record_content" \
      --argjson proxied "$record_proxied" \
      '{type: $type, name: $name, content: $content, ttl: 1, proxied: $proxied}'
  )"

  if [[ -n "$record_id" ]]; then
    cf_request "PUT" "zones/${zone_id}/dns_records/${record_id}" "$payload" >/dev/null
    printf 'updated %s %s -> %s (proxied=%s)\n' \
      "$record_type" "$record_name" "$record_content" "$record_proxied"
  else
    cf_request "POST" "zones/${zone_id}/dns_records" "$payload" >/dev/null
    printf 'created %s %s -> %s (proxied=%s)\n' \
      "$record_type" "$record_name" "$record_content" "$record_proxied"
  fi
}

print_blocked_state() {
  cat <<EOF
No accessible Cloudflare zone named ${zone_name} was returned by the current token.

API evidence:
  GET ${api_base}/zones?name=${zone_name}&per_page=50

Current account context:
  CLOUDFLARE_API_ACCOUNT_ID=${CLOUDFLARE_API_ACCOUNT_ID}

Once the zone exists in this account, apply the minimal DNS setup:
  1. Create an A record for ${zone_name} -> ${origin_ip}
  2. Optionally create a CNAME for www.${zone_name} -> ${zone_name}

If the zone is not yet onboarded, create or import it first:
  curl -sS -X POST "${api_base}/zones" \
    -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{"name":"'"${zone_name}"'","account":{"id":"'"${CLOUDFLARE_API_ACCOUNT_ID}"'"},"jump_start":false}'

Then rerun this script with --apply.
EOF
}

print_plan() {
  local zone_id="$1"

  cat <<EOF
Cloudflare zone found for ${zone_name}: ${zone_id}

Planned records:
  A    ${zone_name} -> ${origin_ip} (proxied=false, ttl=auto)
EOF
}

main() {
  require_env

  local zone_response
  zone_response="$(cf_request "GET" "zones?name=${zone_name}&per_page=1")"

  local zone_count
  zone_count="$(jq -r '.result | length' <<<"$zone_response")"

  if [[ "$zone_count" -eq 0 ]]; then
    print_blocked_state
    exit 2
  fi

  local zone_id
  zone_id="$(jq -r '.result[0].id' <<<"$zone_response")"

  if [[ "${1:-}" != "--apply" ]]; then
    print_plan "$zone_id"
    exit 0
  fi

  create_dns_record "$zone_id" "A" "$zone_name" "$origin_ip" false
}

main "$@"
