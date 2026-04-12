import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SCRIPT = REPO_ROOT / "deploy" / "caddy" / "deploy.sh"
DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "deploy-contabo.yml"


class DeploySanityGateTest(unittest.TestCase):
  def test_deploy_fails_when_sanity_credentials_are_missing(self):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

    temp_dir = Path(tempfile.mkdtemp(prefix="deploy-sanity-gate-"))
    fake_bin = temp_dir / "bin"
    fake_bin.mkdir(parents=True)
    deploy_root = temp_dir / "deploy-root"
    (deploy_root / "shared").mkdir(parents=True)
    (deploy_root / "shared" / "active-slot").write_text("blue\n", encoding="utf-8")

    self._write_executable(
        fake_bin / "docker",
        """#!/usr/bin/env bash
set -euo pipefail
cmd="$*"
if [[ "$1" == "compose" && "$2" == "version" ]]; then
  exit 0
fi
case "$cmd" in
  *" ps caddy --format json"*)
    printf '{"Service":"caddy","State":"running"}\\n'
    ;;
  *" up -d wave-green"*)
    ;;
  *" stop wave-green"*)
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
    self._write_executable(
        fake_bin / "curl",
        """#!/usr/bin/env bash
set -euo pipefail
for arg in "$@"; do
  if [[ "$arg" == "http://localhost:9899/healthz" ]]; then
    exit 0
  fi
done
exit 1
""",
    )
    self._write_executable(
        fake_bin / "systemctl",
        "#!/usr/bin/env bash\nset -euo pipefail\nexit 0\n",
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

    result = subprocess.run(
        [bash_path, str(DEPLOY_SCRIPT), "deploy"],
        cwd=REPO_ROOT,
        env=env,
        capture_output=True,
        text=True,
        check=False,
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
      probe = subprocess.run(
          [str(path), "-c", 'printf "%s" "${BASH_VERSINFO[0]}"'],
          capture_output=True,
          text=True,
          check=False,
      )
      if probe.returncode != 0:
        continue
      version = probe.stdout.strip()
      if version.isdigit() and int(version) >= 4:
        return str(path)
    return None


if __name__ == "__main__":
  unittest.main()
