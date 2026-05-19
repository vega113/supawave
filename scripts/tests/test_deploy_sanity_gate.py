import unittest
from pathlib import Path

from scripts.tests.deploy_harness import find_bash, run_deploy_script


REPO_ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SCRIPT = REPO_ROOT / "deploy" / "caddy" / "deploy.sh"
CONTABO_DEPLOY_SCRIPT = REPO_ROOT / "deploy" / "contabo" / "deploy.sh"
DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "deploy-contabo.yml"


class DeploySanityGateTest(unittest.TestCase):
  def test_deploy_script_allows_longer_search_warmup_window(self):
    for script_path in (DEPLOY_SCRIPT, CONTABO_DEPLOY_SCRIPT):
      deploy_script = script_path.read_text(encoding="utf-8")

      self.assertIn(
          'local sanity_search_deadline_seconds="${SANITY_SEARCH_DEADLINE_SECONDS:-120}"',
          deploy_script,
      )
      self.assertIn(
          'local sanity_search_request_timeout_seconds="${SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS:-15}"',
          deploy_script,
      )

  def test_deploy_script_caps_search_requests_to_remaining_deadline(self):
    for script_path in (DEPLOY_SCRIPT, CONTABO_DEPLOY_SCRIPT):
      deploy_script = script_path.read_text(encoding="utf-8")

      self.assertIn('remaining_time=$(( DEADLINE - $(date +%s) ))', deploy_script)
      self.assertIn('if [ "$remaining_time" -le 0 ]; then', deploy_script)
      self.assertIn('request_timeout="$SEARCH_REQUEST_TIMEOUT_SECONDS"', deploy_script)
      self.assertIn('if [ "$request_timeout" -gt "$remaining_time" ]; then', deploy_script)
      self.assertIn('request_timeout="$remaining_time"', deploy_script)
      self.assertIn('--max-time "$request_timeout"', deploy_script)

  def test_deploy_fails_when_sanity_credentials_are_missing(self):
    bash_path = find_bash()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

    result = run_deploy_script(
        REPO_ROOT,
        DEPLOY_SCRIPT,
        """#!/usr/bin/env bash
set -euo pipefail
cmd="$*"
if [[ "$1" == "compose" && "$2" == "version" ]]; then
  exit 0
fi
if [[ "$cmd" == *"image inspect --format"* && "$cmd" == *"ghcr.io/example/wave:test"* ]]; then
  printf 'true\n'
  exit 0
fi
case "$cmd" in
  *" ps caddy --format json"*)
    printf '{"Service":"caddy","State":"running"}\\n'
    ;;
  *" ps -q wave-green"*)
    printf 'wave-green-id\\n'
    ;;
  *" up -d wave-green"*)
    ;;
  *" exec -T caddy caddy reload"*)
    ;;
  *" stop wave-blue"*)
    ;;
  *" stop wave-green"*)
    ;;
  *"inspect --format {{.State.StartedAt}} wave-green-id"*)
    printf '2026-04-13T11:00:00Z\\n'
    ;;
  *" logs --no-color --since 2026-04-13T11:00:00Z wave-green"*)
    printf 'Completed Mongock Mongo schema migrations\n'
    ;;
  "pull ghcr.io/example/wave:test"|*" pull ghcr.io/example/wave:test"*)
    ;;
  *)
    echo "unexpected docker invocation: $cmd" >&2
    exit 1
    ;;
esac
""",
    )

    self.assertNotEqual(
        0,
        result.returncode,
        msg=f"deploy unexpectedly succeeded:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
    )
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)
    self.assertNotIn("skipping sanity check", combined.lower())

  def test_deploy_sanity_retries_transient_apk_failures(self):
    bash_path = find_bash()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

    result = run_deploy_script(
        REPO_ROOT,
        DEPLOY_SCRIPT,
        r"""#!/usr/bin/env bash
set -euo pipefail
cmd="$*"
if [[ "$1" == "compose" && "$2" == "version" ]]; then
  exit 0
fi
if [[ "$cmd" == *"image inspect --format"* && "$cmd" == *"ghcr.io/example/wave:test"* ]]; then
  printf 'true\n'
  exit 0
fi
if [[ "$1" == "run" && "$cmd" == *"alpine:3.20 sh -c"* ]]; then
  script="${@: -1}"
  container_bin=$(mktemp -d)
  export CONTAINER_BIN="$container_bin"
  export APK_ATTEMPTS="$container_bin/apk-attempts"
  cat > "$container_bin/curl" <<'CURL_STUB'
#!/bin/sh
echo "curl should be replaced by apk install" >&2
exit 127
CURL_STUB
  chmod +x "$container_bin/curl"
  cat > "$container_bin/apk" <<'APK'
#!/bin/bash
set -euo pipefail
attempt=0
if [[ -f "$APK_ATTEMPTS" ]]; then
  attempt=$(/bin/cat "$APK_ATTEMPTS")
fi
attempt=$((attempt + 1))
printf '%s' "$attempt" > "$APK_ATTEMPTS"
if [[ "$attempt" -lt 3 ]]; then
  echo "temporary error (try again later)" >&2
  exit 1
fi
/bin/cat > "$CONTAINER_BIN/curl" <<'CURL'
#!/bin/bash
set -euo pipefail
cookie=""
previous=""
for arg in "$@"; do
  if [[ "$previous" == "-c" ]]; then
    cookie="$arg"
  fi
  previous="$arg"
done
url="${@: -1}"
case "$url" in
  */auth/signin)
    if [[ -n "$cookie" ]]; then
      printf 'localhost\tFALSE\t/\tFALSE\t0\tJSESSIONID\ttest\n' > "$cookie"
    fi
    printf '200'
    ;;
  */search/*)
    printf '{"3":[{"3":"wave!example"}]}'
    ;;
  */fetch/*)
    printf '{"1":true}\n200'
    ;;
  *)
    printf '200'
    ;;
esac
CURL
/bin/chmod +x "$CONTAINER_BIN/curl"
/bin/cat > "$CONTAINER_BIN/jq" <<'JQ'
#!/bin/bash
set -euo pipefail
filter="${@: -1}"
input=$(cat)
case "$filter" in
  *'["3"][0]["3"]'*)
    printf 'wave!example\n'
    ;;
  'has("1")')
    printf 'true\n'
    ;;
  *)
    printf '%s\n' "$input"
    ;;
esac
JQ
/bin/chmod +x "$CONTAINER_BIN/jq"
/bin/cat > "$CONTAINER_BIN/grep" <<'GREP'
#!/bin/sh
exec /usr/bin/grep "$@"
GREP
/bin/chmod +x "$CONTAINER_BIN/grep"
/bin/cat > "$CONTAINER_BIN/sed" <<'SED'
#!/bin/sh
exec /usr/bin/sed "$@"
SED
/bin/chmod +x "$CONTAINER_BIN/sed"
/bin/cat > "$CONTAINER_BIN/tail" <<'TAIL'
#!/bin/sh
exec /usr/bin/tail "$@"
TAIL
/bin/chmod +x "$CONTAINER_BIN/tail"
/bin/cat > "$CONTAINER_BIN/cat" <<'CAT'
#!/bin/sh
exec /bin/cat "$@"
CAT
/bin/chmod +x "$CONTAINER_BIN/cat"
APK
  chmod +x "$container_bin/apk"
  cat > "$container_bin/date" <<'DATE'
#!/bin/sh
exec /bin/date "$@"
DATE
  chmod +x "$container_bin/date"
  cat > "$container_bin/sleep" <<'SLEEP'
#!/bin/sh
exit 0
SLEEP
  chmod +x "$container_bin/sleep"
  PATH="$container_bin" \
    INTERNAL_PORT="${INTERNAL_PORT:-9898}" \
    SANITY_ADDR="${SANITY_ADDR:-test@example.com}" \
    SANITY_PASS="${SANITY_PASS:-password}" \
    SANITY_SEARCH_DEADLINE_SECONDS="${SANITY_SEARCH_DEADLINE_SECONDS:-120}" \
    SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS="${SANITY_SEARCH_REQUEST_TIMEOUT_SECONDS:-15}" \
    /bin/sh -c "$script"
  attempts=$(cat "$APK_ATTEMPTS")
  if [[ "$attempts" != "3" ]]; then
    echo "expected apk to be attempted 3 times, got $attempts" >&2
    exit 1
  fi
  exit 0
fi
case "$cmd" in
  *" ps caddy --format json"*)
    printf '{"Service":"caddy","State":"running"}\n'
    ;;
  *" ps -q wave-green"*)
    printf 'wave-green-id\n'
    ;;
  *" up -d wave-green"*)
    ;;
  *" exec -T caddy caddy reload"*)
    ;;
  *" stop wave-blue"*)
    ;;
  *" stop wave-green"*)
    ;;
  *"inspect --format {{.State.StartedAt}} wave-green-id"*)
    printf '2026-04-13T11:00:00Z\n'
    ;;
  *" logs --no-color --since 2026-04-13T11:00:00Z wave-green"*)
    printf 'Completed Mongock Mongo schema migrations\n'
    ;;
  "pull ghcr.io/example/wave:test"|*" pull ghcr.io/example/wave:test"*)
    ;;
  *)
    echo "unexpected docker invocation: $cmd" >&2
    exit 1
    ;;
esac
""",
        env_overrides={
            "SANITY_ADDRESS": "test@example.com",
            "SANITY_PASSWORD": "password",
        },
    )

    self.assertEqual(
        0,
        result.returncode,
        msg=f"deploy should retry transient apk failures:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
    )
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("ALL CHECKS PASSED", combined)

  def test_deploy_sanity_scripts_retry_apk_dependency_install(self):
    for script_path in (DEPLOY_SCRIPT, CONTABO_DEPLOY_SCRIPT):
      deploy_script = script_path.read_text(encoding="utf-8")

      self.assertIn("install_sanity_tools_with_retry()", deploy_script)
      self.assertIn("apk add --no-cache curl jq", deploy_script)
      self.assertIn("for attempt in 1 2 3; do", deploy_script)

  def test_deploy_workflow_validates_sanity_credentials_before_remote_steps(self):
    workflow = DEPLOY_WORKFLOW.read_text(encoding="utf-8")

    self.assertIn("name: Validate deploy sanity credentials", workflow)
    self.assertIn("if: github.event_name == 'push' || github.event.inputs.action != 'status'", workflow)
    self.assertIn('SANITY_ADDRESS: ${{ secrets.SANITY_ADDRESS }}', workflow)
    self.assertIn('SANITY_PASSWORD: ${{ secrets.SANITY_PASSWORD }}', workflow)
    self.assertIn(': "${SANITY_ADDRESS:?Set repo secret SANITY_ADDRESS', workflow)
    self.assertIn(': "${SANITY_PASSWORD:?Set repo secret SANITY_PASSWORD', workflow)

    validate_idx = workflow.index("name: Validate deploy sanity credentials")
    remote_idx = workflow.index("name: Upload bundle")
    self.assertLess(validate_idx, remote_idx)

  def test_deploy_workflow_records_notification_failures(self):
    workflow = DEPLOY_WORKFLOW.read_text(encoding="utf-8")

    self.assertIn("id: notify_success", workflow)
    self.assertIn("id: notify_failure", workflow)
    self.assertIn("failure() && steps.deploy.outcome != 'success'", workflow)
    self.assertIn("name: Close deploy-notification-failure issues on notification success", workflow)
    self.assertIn("name: Create GitHub issue on deploy notification failure", workflow)
    self.assertIn("continue-on-error: true", workflow)
    self.assertIn("deploy-notification-failure", workflow)
    self.assertIn("Check the notification step log for the Resend HTTP error", workflow)

  def test_build_workflow_runs_deploy_notification_tests(self):
    workflow = (REPO_ROOT / ".github" / "workflows" / "build.yml").read_text(
        encoding="utf-8"
    )

    self.assertIn("python3 -m unittest scripts.tests.test_send_resend_email -v", workflow)

if __name__ == "__main__":
  unittest.main()
