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
