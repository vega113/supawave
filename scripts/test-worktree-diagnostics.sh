#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SCRIPT_PATH="${ROOT_DIR}/scripts/worktree-diagnostics.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  local haystack="$1"
  local needle="$2"

  if [[ "${haystack}" != *"${needle}"* ]]; then
    fail "expected output to contain: ${needle}"
  fi
}

assert_file_contains() {
  local path="$1"
  local needle="$2"

  [[ -f "${path}" ]] || fail "expected file to exist: ${path}"
  local content
  content="$(cat "${path}")"
  assert_contains "${content}" "${needle}"
}

make_git_stub() {
  local stub_path="$1/git"
  cat > "${stub_path}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1-} ${2-}" in
  "rev-parse --show-toplevel")
    printf '%s\n' "${TEST_REPO_ROOT}"
    ;;
  "branch --show-current")
    printf '%s\n' "${TEST_BRANCH}"
    ;;
  *)
    printf 'unsupported git invocation: %s\n' "$*" >&2
    exit 1
    ;;
esac
EOF
  chmod +x "${stub_path}"
}

make_curl_stub() {
  local stub_path="$1/curl"
  cat > "${stub_path}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

url="${*: -1}"
status="000"
case "${url}" in
  */healthz) status="${TEST_HEALTH_STATUS-200}" ;;
  */readyz) status="${TEST_READY_STATUS-200}" ;;
  */webclient/webclient.nocache.js) status="${TEST_WEBCLIENT_STATUS-200}" ;;
  */) status="${TEST_ROOT_STATUS-302}" ;;
esac
printf '%s' "${status}"
EOF
  chmod +x "${stub_path}"
}

make_fake_smoke_script() {
  local repo_root="$1"
  mkdir -p "${repo_root}/scripts"
  cat > "${repo_root}/scripts/wave-smoke.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1-}" != "check" ]]; then
  printf 'unsupported smoke invocation: %s\n' "$*" >&2
  exit 1
fi

printf '%s\n' "${TEST_SMOKE_OUTPUT:-ROOT_STATUS=302
HEALTH_STATUS=200
WEBCLIENT_STATUS=200}"
exit "${TEST_SMOKE_EXIT:-0}"
EOF
  chmod +x "${repo_root}/scripts/wave-smoke.sh"
}

seed_runtime_artifacts() {
  local repo_root="$1"
  mkdir -p \
    "${repo_root}/target/universal/stage" \
    "${repo_root}/journal/runtime-config" \
    "${repo_root}/journal/local-verification"
  cat > "${repo_root}/target/universal/stage/wave_server.out" <<'EOF'
startup line 1
startup line 2
startup final line
EOF
  cat > "${repo_root}/target/universal/stage/wiab-server.log" <<'EOF'
server log 1
server log 2
server log final
EOF
  cat > "${repo_root}/journal/runtime-config/issue-587-worktree-diagnostics-20260412-port-9904.application.conf" <<'EOF'
http_frontend_addresses = ["127.0.0.1:9904"]
EOF
  cat > "${repo_root}/journal/local-verification/2026-04-12-issue-587-worktree-diagnostics.md" <<'EOF'
# Local Verification
- pending
EOF
}

run_bundle_shape_case() {
  local temp_dir repo_root bin_dir output_path output

  temp_dir="$(mktemp -d)"
  repo_root="${temp_dir}/repo"
  bin_dir="${temp_dir}/bin"
  output_path="${temp_dir}/bundle.md"
  mkdir -p "${repo_root}" "${bin_dir}"

  make_git_stub "${bin_dir}"
  make_curl_stub "${bin_dir}"
  make_fake_smoke_script "${repo_root}"
  seed_runtime_artifacts "${repo_root}"

  output="$(
    PATH="${bin_dir}:${PATH}" \
    TEST_REPO_ROOT="${repo_root}" \
    TEST_BRANCH="issue-587-worktree-diagnostics-20260412" \
    TEST_ROOT_STATUS=302 \
    TEST_HEALTH_STATUS=200 \
    TEST_READY_STATUS=200 \
    TEST_WEBCLIENT_STATUS=200 \
    TEST_SMOKE_EXIT=0 \
    "${SCRIPT_PATH}" --port 9904 --lines 2 --output "${output_path}"
  )" || fail "expected diagnostics bundle command to succeed"

  assert_contains "${output}" "# Worktree Diagnostics Bundle"
  assert_contains "${output}" "Branch: issue-587-worktree-diagnostics-20260412"
  assert_contains "${output}" "Port: 9904"
  assert_contains "${output}" '`GET /healthz` -> `200`'
  assert_contains "${output}" 'Smoke exit: `0`'
  assert_contains "${output}" "startup line 2"
  assert_contains "${output}" "server log final"
  assert_file_contains "${output_path}" "Runtime config:"
  assert_file_contains "${output_path}" "Evidence file:"

  rm -rf "${temp_dir}"
}

run_missing_artifacts_case() {
  local temp_dir repo_root bin_dir output

  temp_dir="$(mktemp -d)"
  repo_root="${temp_dir}/repo"
  bin_dir="${temp_dir}/bin"
  mkdir -p "${repo_root}" "${bin_dir}" "${repo_root}/scripts"

  make_git_stub "${bin_dir}"
  make_curl_stub "${bin_dir}"
  make_fake_smoke_script "${repo_root}"

  output="$(
    PATH="${bin_dir}:${PATH}" \
    TEST_REPO_ROOT="${repo_root}" \
    TEST_BRANCH="issue-587-worktree-diagnostics-20260412" \
    TEST_ROOT_STATUS=000 \
    TEST_HEALTH_STATUS=000 \
    TEST_READY_STATUS=000 \
    TEST_WEBCLIENT_STATUS=000 \
    TEST_SMOKE_EXIT=1 \
    TEST_SMOKE_OUTPUT=$'ROOT_STATUS=000\nUnexpected root status: 000' \
    "${SCRIPT_PATH}" --port 9904 --lines 5
  )" || fail "expected diagnostics bundle to stay usable when artifacts are missing"

  assert_contains "${output}" "Runtime config: missing"
  assert_contains "${output}" "Evidence file: missing"
  assert_contains "${output}" 'Smoke exit: `1`'
  assert_contains "${output}" "(missing:"

  rm -rf "${temp_dir}"
}

main() {
  run_bundle_shape_case
  run_missing_artifacts_case
  printf 'PASS: scripts/worktree-diagnostics.sh bundle contract\n'
}

main "$@"
