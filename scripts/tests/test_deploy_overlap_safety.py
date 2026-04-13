import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SCRIPT = REPO_ROOT / "deploy" / "caddy" / "deploy.sh"
BUILD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "build.yml"


class DeployOverlapSafetyTest(unittest.TestCase):
  def test_deploy_stops_old_slot_immediately_after_swap(self):
    result, temp_dir = self._run_script(
        command="deploy",
        active_slot="blue",
        previous_slot=None,
        running_services=["caddy", "wave-blue"],
    )

    self.assertEqual(
        0,
        result.returncode,
        msg=f"deploy failed unexpectedly:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
    )
    ops = (temp_dir / "ops.log").read_text(encoding="utf-8")
    self.assertIn("stop wave-blue", ops)
    self.assertNotIn("systemd-run", ops)

  def test_rollback_stops_replaced_slot_immediately_after_swap(self):
    result, temp_dir = self._run_script(
        command="rollback",
        active_slot="green",
        previous_slot="blue",
        running_services=["caddy", "wave-blue", "wave-green"],
    )

    self.assertEqual(
        0,
        result.returncode,
        msg=f"rollback failed unexpectedly:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
    )
    ops = (temp_dir / "ops.log").read_text(encoding="utf-8")
    self.assertIn("stop wave-green", ops)
    self.assertNotIn("systemd-run", ops)

  def test_build_workflow_runs_overlap_safety_regression(self):
    workflow = BUILD_WORKFLOW.read_text(encoding="utf-8")
    self.assertIn("scripts.tests.test_deploy_overlap_safety", workflow)

  def test_deploy_fails_closed_when_old_slot_status_cannot_be_verified(self):
    result, _ = self._run_script(
        command="deploy",
        active_slot="blue",
        previous_slot=None,
        running_services=["caddy", "wave-blue"],
        stop_fail_services=["wave-blue"],
        ps_error_services=["wave-blue"],
    )

    self.assertNotEqual(0, result.returncode)
    self.assertIn(
        "unable to verify whether replaced slot wave-blue is still running",
        result.stderr,
    )

  def test_deploy_reverts_swap_when_replaced_slot_stays_running(self):
    result, temp_dir = self._run_script(
        command="deploy",
        active_slot="blue",
        previous_slot=None,
        running_services=["caddy", "wave-blue"],
        stop_fail_services=["wave-blue"],
    )

    self.assertNotEqual(0, result.returncode)
    self.assertIn("replaced slot wave-blue is still running", result.stderr)
    self.assertEqual("blue", (temp_dir / "deploy-root" / "shared" / "active-slot").read_text(encoding="utf-8").strip())
    self.assertIn(
        "# active: blue",
        (temp_dir / "deploy-root" / "shared" / "upstream.caddy").read_text(encoding="utf-8"),
    )
    ops = (temp_dir / "ops.log").read_text(encoding="utf-8")
    self.assertIn("stop wave-green", ops)

  def test_rollback_reverts_swap_when_replaced_slot_stays_running(self):
    result, temp_dir = self._run_script(
        command="rollback",
        active_slot="green",
        previous_slot="blue",
        running_services=["caddy", "wave-blue", "wave-green"],
        stop_fail_services=["wave-green"],
    )

    self.assertNotEqual(0, result.returncode)
    self.assertIn("replaced slot wave-green is still running", result.stderr)
    shared_dir = temp_dir / "deploy-root" / "shared"
    self.assertEqual("green", (shared_dir / "active-slot").read_text(encoding="utf-8").strip())
    self.assertEqual("blue", (shared_dir / "previous-slot").read_text(encoding="utf-8").strip())
    self.assertIn(
        "# active: green",
        (shared_dir / "upstream.caddy").read_text(encoding="utf-8"),
    )
    ops = (temp_dir / "ops.log").read_text(encoding="utf-8")
    self.assertIn("stop wave-blue", ops)

  def test_revert_swap_keeps_new_slot_running_when_routing_restore_fails(self):
    result, temp_dir = self._run_script(
        command="deploy",
        active_slot="blue",
        previous_slot=None,
        running_services=["caddy", "wave-blue"],
        stop_fail_services=["wave-blue"],
        reload_fail_on_calls=[2],
    )

    self.assertNotEqual(0, result.returncode)
    self.assertIn(
        "failed to reload Caddy while reverting swap to blue",
        result.stderr,
    )
    ops = (temp_dir / "ops.log").read_text(encoding="utf-8")
    self.assertNotIn("stop wave-green", ops)

  def _run_script(
      self,
      command: str,
      active_slot: str,
      previous_slot: str | None,
      running_services: list[str],
      stop_fail_services: list[str] | None = None,
      ps_error_services: list[str] | None = None,
      reload_fail_on_calls: list[int] | None = None,
  ):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

    stop_fail_services = stop_fail_services or []
    ps_error_services = ps_error_services or []
    reload_fail_on_calls = reload_fail_on_calls or []
    temp_dir = Path(tempfile.mkdtemp(prefix="deploy-overlap-safety-"))
    fake_bin = temp_dir / "bin"
    fake_bin.mkdir(parents=True)
    deploy_root = temp_dir / "deploy-root"
    (deploy_root / "shared").mkdir(parents=True)
    (deploy_root / "shared" / "active-slot").write_text(f"{active_slot}\n", encoding="utf-8")
    if previous_slot is not None:
      (deploy_root / "shared" / "previous-slot").write_text(f"{previous_slot}\n", encoding="utf-8")
    ops_log = temp_dir / "ops.log"
    reload_count = temp_dir / "reload-count"
    stop_fail_checks = "\n".join(
        f'if [[ "$cmd" == *" stop {service}"* ]]; then exit 1; fi'
        for service in stop_fail_services
    )
    ps_error_checks = "\n".join(
        f'if [[ "$cmd" == *" ps {service} --format json"* ]]; then '
        f'echo "docker ps failed for {service}" >&2; exit 2; fi'
        for service in ps_error_services
    )
    running_checks = "\n".join(
        f'if [[ "$cmd" == *" ps {service} --format json"* ]]; then '
        f'printf \'{{"Service":"{service}","State":"running"}}\\n\'; exit 0; fi'
        for service in running_services
    )

    self._write_executable(
        fake_bin / "docker",
        f"""#!/usr/bin/env bash
set -euo pipefail
	printf '%s\\n' "$*" >> "{ops_log}"
	cmd="$*"
	{stop_fail_checks}
	{ps_error_checks}
if [[ "$cmd" == *"image inspect --format"* && "$cmd" == *"ghcr.io/example/wave:test"* ]]; then
  printf 'true\\n'
  exit 0
fi
	case "$cmd" in
  "compose version")
    exit 0
    ;;
  "pull ghcr.io/example/wave:test")
    exit 0
    ;;
  *" ps caddy --format json"*)
    printf '{{"Service":"caddy","State":"running"}}\\n'
    exit 0
    ;;
  *" ps -q wave-blue"*)
    printf 'wave-blue-id\\n'
    exit 0
    ;;
  *" ps -q wave-green"*)
    printf 'wave-green-id\\n'
    exit 0
    ;;
  *" up -d wave-green"*)
    exit 0
    ;;
  *" up -d wave-blue"*)
    exit 0
    ;;
  *"inspect --format "*wave-blue-id*)
    printf '2026-04-13T11:00:00Z\\n'
    exit 0
    ;;
  *"inspect --format "*wave-green-id*)
    printf '2026-04-13T11:00:00Z\\n'
    exit 0
    ;;
  *" logs --no-color "*wave-blue*)
    printf 'Completed Mongock Mongo schema migrations\n'
    exit 0
    ;;
  *" logs --no-color "*wave-green*)
    printf 'Completed Mongock Mongo schema migrations\n'
    exit 0
    ;;
  *" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile"*)
    count=0
    if [[ -f "{reload_count}" ]]; then
      count=$(<"{reload_count}")
    fi
    count=$((count + 1))
    printf '%s\n' "$count" > "{reload_count}"
    case "$count" in
{textwrap.indent("".join(f"      {call}) exit 1 ;;\n" for call in reload_fail_on_calls), "    ")}
    esac
    exit 0
    ;;
  *" stop wave-blue"*)
    exit 0
    ;;
  *" stop wave-green"*)
    exit 0
    ;;
  *" kill wave-blue"*)
    exit 0
    ;;
  *" kill wave-green"*)
    exit 0
    ;;
  "run --rm --network host "*|*"run --rm --network host "*)
    exit 0
    ;;
esac
{running_checks}
echo "unexpected docker invocation: $cmd" >&2
exit 1
""",
    )
    self._write_executable(
        fake_bin / "curl",
        """#!/usr/bin/env bash
set -euo pipefail
args="$*"
out=""
for ((i=1;i<=$#;i++)); do
  if [ "${!i}" = "-w" ]; then
    j=$((i+1))
    out="${!j}"
  fi
done
if [[ "$args" == *"http://localhost:9899/healthz"* ]]; then
  if [ -n "$out" ]; then
    printf '%s' "${out//%{http_code}/200}"
  fi
  exit 0
fi
if [[ "$args" == *"http://localhost:9898/healthz"* ]]; then
  if [ -n "$out" ]; then
    printf '%s' "${out//%{http_code}/200}"
  fi
  exit 0
fi
if [[ "$args" == *"https://supawave.ai/healthz"* ]]; then
  if [ -n "$out" ]; then
    printf '%s' "${out//%{http_code}/200}"
  fi
  exit 0
fi
if [[ "$args" == *"https://wave.supawave.ai/"* ]]; then
  if [ -n "$out" ]; then
    printf '%s' "${out//%{http_code}/301}"
  fi
  exit 0
fi
exit 1
""",
    )
    self._write_executable(
        fake_bin / "systemctl",
        "#!/usr/bin/env bash\nset -euo pipefail\nexit 0\n",
    )
    self._write_executable(
        fake_bin / "systemd-run",
        "#!/usr/bin/env bash\nset -euo pipefail\necho unexpected systemd-run \"$*\" >&2\nexit 1\n",
    )
    self._write_executable(
        fake_bin / "flock",
        "#!/usr/bin/env bash\nset -euo pipefail\nexit 0\n",
    )

    env = os.environ.copy()
    env["PATH"] = f"{fake_bin}:{env['PATH']}"
    env["DEPLOY_ROOT"] = str(deploy_root)
    env["WAVE_IMAGE"] = "ghcr.io/example/wave:test"
    env["CANONICAL_HOST"] = "supawave.ai"
    env["ROOT_HOST"] = "wave.supawave.ai"
    env["WWW_HOST"] = "www.supawave.ai"
    env["SANITY_ADDRESS"] = "testregister"
    env["SANITY_PASSWORD"] = "testregister"

    result = subprocess.run(
        [bash_path, str(DEPLOY_SCRIPT), command],
        cwd=REPO_ROOT,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    return result, temp_dir

  @staticmethod
  def _write_executable(path: Path, content: str) -> None:
    path.write_text(textwrap.dedent(content), encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

  @staticmethod
  def _bash_path() -> str | None:
    for candidate in (os.environ.get("TEST_BASH"), "/opt/homebrew/bin/bash", "/usr/local/bin/bash", "/bin/bash"):
      if not candidate:
        continue
      path = Path(candidate)
      if not path.is_file() or not os.access(path, os.X_OK):
        continue
      version = subprocess.run(
          [str(path), "-c", 'printf "%s" "${BASH_VERSINFO[0]}"'],
          capture_output=True,
          text=True,
          check=True,
      ).stdout.strip()
      if version.isdigit() and int(version) >= 4:
        return str(path)
    return None


if __name__ == "__main__":
  unittest.main()
