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

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SCRIPT_PATH="${ROOT_DIR}/scripts/feature-flag.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  local haystack needle

  haystack="$1"
  needle="$2"

  if [[ "${haystack}" != *"${needle}"* ]]; then
    fail "expected output to contain: ${needle}"
  fi
}

make_jq_stub() {
  local stub_path

  stub_path="$1/jq"
  cat > "${stub_path}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1-}" == "-r" ]]; then
  sed -n 's/.*"error":"\([^"]*\)".*/\1/p'
  exit 0
fi
printf '{}\n'
EOF
  chmod +x "${stub_path}"
}

make_curl_stub() {
  local stub_path

  stub_path="$1/curl"
  cat > "${stub_path}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
body_file=""
header_file=""
http_code="401"
response_body='{"error":"Not authenticated"}'
response_headers="${TEST_RESPONSE_HEADERS-HTTP/1.1 401 Unauthorized}"
args=("$@")
index=0
while [[ ${index} -lt ${#args[@]} ]]; do
  case "${args[${index}]}" in
    -o)
      index=$((index + 1))
      body_file="${args[${index}]}"
      ;;
    -D)
      index=$((index + 1))
      header_file="${args[${index}]}"
      ;;
    -w)
      index=$((index + 1))
      ;;
  esac
  index=$((index + 1))
done
if [[ -n "${body_file}" ]]; then
  printf '%s' "${response_body}" > "${body_file}"
fi
if [[ -n "${header_file}" ]]; then
  printf '%s\n' "${response_headers}" > "${header_file}"
fi
printf '%s' "${http_code}"
EOF
  chmod +x "${stub_path}"
}

run_case() {
  local cookie_value response_headers temp_dir home_dir bin_dir output status

  cookie_value="$1"
  response_headers="$2"
  temp_dir="$(mktemp -d)"
  home_dir="${temp_dir}/home"
  bin_dir="${temp_dir}/bin"
  mkdir -p "${home_dir}" "${bin_dir}"
  printf '%s\n' "${cookie_value}" > "${home_dir}/.wave-session"
  make_curl_stub "${bin_dir}"
  make_jq_stub "${bin_dir}"
  output="$(
    HOME="${home_dir}" \
    PATH="${bin_dir}:${PATH}" \
    TEST_RESPONSE_HEADERS="${response_headers}" \
    "${SCRIPT_PATH}" list 2>&1
  )" && status=0 || status=$?
  rm -rf "${temp_dir}"
  if [[ ${status} -eq 0 ]]; then
    fail "expected script failure for cookie ${cookie_value}"
  fi
  printf '%s' "${output}"
}

run_stale_jwt_case() {
  local output

  output="$(
    run_case \
      'JSESSIONID=active; wave-session-jwt=stale' \
      $'HTTP/1.1 401 Unauthorized\nSet-Cookie: wave-session-jwt=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax'
  )"
  assert_contains "${output}" "stored wave-session-jwt cookie is expired or invalid"
  assert_contains "${output}" "Sign in again in your browser"
  assert_contains "${output}" "scripts/save-wave-session.sh"
}

run_missing_jwt_case() {
  local output

  output="$(run_case 'JSESSIONID=active' 'HTTP/1.1 401 Unauthorized')"
  assert_contains "${output}" "saved session cookie is missing wave-session-jwt"
  assert_contains "${output}" "Save the full Cookie header"
}

main() {
  run_stale_jwt_case
  run_missing_jwt_case
  printf 'PASS: scripts/feature-flag.sh auth diagnostics\n'
}

main "$@"
