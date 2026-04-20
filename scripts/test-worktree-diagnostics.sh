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

url=""
for arg in "$@"; do
  url="${arg}"
done
status="000"
case "${url}" in
  */healthz) status="${TEST_HEALTH_STATUS-200}" ;;
  */readyz) status="${TEST_READY_STATUS-200}" ;;
  */\?view=landing) status="${TEST_LANDING_STATUS-200}" ;;
  */\?view=j2cl-root) status="${TEST_J2CL_ROOT_STATUS-200}" ;;
  */j2cl/index.html) status="${TEST_J2CL_INDEX_STATUS-200}" ;;
  */j2cl-search/sidecar/j2cl-sidecar.js) status="${TEST_SIDECAR_STATUS-200}" ;;
  */) status="${TEST_ROOT_STATUS-200}" ;;
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

printf '%s\n' "${TEST_SMOKE_OUTPUT:-ROOT_STATUS=200
ROOT_SHELL=present
HEALTH_STATUS=200
LANDING_STATUS=200
J2CL_ROOT_STATUS=200
J2CL_ROOT_SHELL=present
J2CL_INDEX_STATUS=200
SIDECAR_STATUS=200
WEBCLIENT_STATUS=404}"
exit "${TEST_SMOKE_EXIT:-0}"
EOF
  chmod +x "${repo_root}/scripts/wave-smoke.sh"
}

seed_runtime_artifacts() {
  local repo_root="$1"
  local evidence_date="$2"
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
  cat > "${repo_root}/journal/local-verification/${evidence_date}-issue-587-worktree-diagnostics-20260412.md" <<'EOF'
# Local Verification
- pending
EOF
}

run_bundle_shape_case() {
  local temp_dir repo_root bin_dir output_path output prior_date

  temp_dir="$(mktemp -d)"
  repo_root="${temp_dir}/repo"
  bin_dir="${temp_dir}/bin"
  output_path="${temp_dir}/bundle.md"
  prior_date="2026-04-11"
  mkdir -p "${repo_root}" "${bin_dir}"

  make_git_stub "${bin_dir}"
  make_curl_stub "${bin_dir}"
  make_fake_smoke_script "${repo_root}"
  seed_runtime_artifacts "${repo_root}" "${prior_date}"

  output="$(
    PATH="${bin_dir}:${PATH}" \
    TEST_REPO_ROOT="${repo_root}" \
    TEST_BRANCH="issue-587-worktree-diagnostics-20260412" \
    TEST_ROOT_STATUS=200 \
    TEST_HEALTH_STATUS=200 \
    TEST_READY_STATUS=200 \
    TEST_LANDING_STATUS=200 \
    TEST_J2CL_ROOT_STATUS=200 \
    TEST_J2CL_INDEX_STATUS=200 \
    TEST_SIDECAR_STATUS=200 \
    TEST_SMOKE_EXIT=0 \
    "${SCRIPT_PATH}" --port 9904 --lines 2 --output "${output_path}"
  )" || fail "expected diagnostics bundle command to succeed"

  assert_contains "${output}" "# Worktree Diagnostics Bundle"
  assert_contains "${output}" "Branch: issue-587-worktree-diagnostics-20260412"
  assert_contains "${output}" "Port: 9904"
  assert_contains "${output}" '`GET /healthz` -> `200`'
  assert_contains "${output}" '`GET /` -> `200`'
  assert_contains "${output}" '`GET /?view=landing` -> `200`'
  assert_contains "${output}" '`GET /?view=j2cl-root` -> `200`'
  assert_contains "${output}" '`GET /j2cl/index.html` -> `200`'
  assert_contains "${output}" '`GET /j2cl-search/sidecar/j2cl-sidecar.js` -> `200`'
  assert_contains "${output}" 'Smoke exit: `0`'
  assert_contains "${output}" 'ROOT_SHELL=present'
  assert_contains "${output}" 'WEBCLIENT_STATUS=404'
  assert_contains "${output}" "Runtime config: ${repo_root}/journal/runtime-config/issue-587-worktree-diagnostics-20260412-port-9904.application.conf"
  assert_contains "${output}" "Evidence file: ${repo_root}/journal/local-verification/${prior_date}-issue-587-worktree-diagnostics-20260412.md"
  assert_contains "${output}" "startup line 2"
  assert_contains "${output}" "server log final"
  assert_file_contains "${output_path}" "Evidence file: ${repo_root}/journal/local-verification/${prior_date}-issue-587-worktree-diagnostics-20260412.md"

  rm -rf "${temp_dir}"
}

run_missing_artifacts_case() {
  local temp_dir repo_root bin_dir output today missing_runtime missing_evidence

  temp_dir="$(mktemp -d)"
  repo_root="${temp_dir}/repo"
  bin_dir="${temp_dir}/bin"
  today="$(date +%F)"
  missing_runtime="${repo_root}/journal/runtime-config/issue-587-worktree-diagnostics-20260412-port-9904.application.conf"
  missing_evidence="${repo_root}/journal/local-verification/${today}-issue-587-worktree-diagnostics-20260412.md"
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
    TEST_LANDING_STATUS=000 \
    TEST_J2CL_ROOT_STATUS=000 \
    TEST_J2CL_INDEX_STATUS=000 \
    TEST_SIDECAR_STATUS=000 \
    TEST_SMOKE_EXIT=1 \
    TEST_SMOKE_OUTPUT=$'ROOT_STATUS=000\nUnexpected root status: 000' \
    "${SCRIPT_PATH}" --port 9904 --lines 5
  )" || fail "expected diagnostics bundle to stay usable when artifacts are missing"

  assert_contains "${output}" "Runtime config: (missing: ${missing_runtime})"
  assert_contains "${output}" "Evidence file: (missing: ${missing_evidence})"
  assert_contains "${output}" 'Smoke exit: `1`'
  assert_contains "${output}" "(missing:"

  rm -rf "${temp_dir}"
}

run_empty_probe_status_case() {
  local temp_dir repo_root bin_dir output

  temp_dir="$(mktemp -d)"
  repo_root="${temp_dir}/repo"
  bin_dir="${temp_dir}/bin"
  mkdir -p "${repo_root}" "${bin_dir}"

  make_git_stub "${bin_dir}"
  make_curl_stub "${bin_dir}"
  make_fake_smoke_script "${repo_root}"
  seed_runtime_artifacts "${repo_root}" "2026-04-12"

  output="$(
    PATH="${bin_dir}:${PATH}" \
    TEST_REPO_ROOT="${repo_root}" \
    TEST_BRANCH="issue-587-worktree-diagnostics-20260412" \
    TEST_ROOT_STATUS=302 \
    TEST_HEALTH_STATUS="" \
    TEST_READY_STATUS=200 \
    TEST_LANDING_STATUS=200 \
    TEST_J2CL_ROOT_STATUS=200 \
    TEST_J2CL_INDEX_STATUS=200 \
    TEST_SIDECAR_STATUS=200 \
    TEST_SMOKE_EXIT=0 \
    "${SCRIPT_PATH}" --port 9904 --lines 2
  )" || fail "expected diagnostics bundle to tolerate empty curl status output"

  assert_contains "${output}" '`GET /healthz` -> `000`'

  rm -rf "${temp_dir}"
}

run_missing_option_value_case() {
  local output

  if output="$("${SCRIPT_PATH}" --port 2>&1)"; then
    fail "expected --port without a value to fail"
  fi

  assert_contains "${output}" "--port requires a value"
}

main() {
  run_bundle_shape_case
  run_missing_artifacts_case
  run_empty_probe_status_case
  run_missing_option_value_case
  printf 'PASS: scripts/worktree-diagnostics.sh bundle contract\n'
}

main "$@"
