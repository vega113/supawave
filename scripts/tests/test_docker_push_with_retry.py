import os
import shlex
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = REPO_ROOT / "scripts" / "docker-push-with-retry.sh"
DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "deploy-contabo.yml"


class DockerPushWithRetryTest(unittest.TestCase):
  def test_retries_unknown_blob_then_succeeds(self):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute docker-push-with-retry.sh")

    with tempfile.TemporaryDirectory(prefix="docker-push-retry-") as tmp_dir:
      temp_dir = Path(tmp_dir)
      fake_bin = temp_dir / "bin"
      fake_bin.mkdir(parents=True)
      attempt_file = temp_dir / "attempts.txt"
      quoted_attempt_file = shlex.quote(str(attempt_file))

      self._write_executable(
          fake_bin / "docker",
          f"""#!/usr/bin/env bash
set -euo pipefail
attempt_file={quoted_attempt_file}
attempts=0
if [[ -f "$attempt_file" ]]; then
  attempts=$(cat "$attempt_file")
fi
attempts=$((attempts + 1))
printf '%s' "$attempts" > "$attempt_file"

if [[ "${{1:-}}" != "push" ]]; then
  echo "unexpected docker invocation: $*" >&2
  exit 1
fi

if [[ "$attempts" -eq 1 ]]; then
  echo "unknown blob" >&2
  exit 1
fi

echo "digest: sha256:test size: 1234"
""",
      )
      self._write_executable(fake_bin / "sleep", "#!/usr/bin/env bash\nexit 0\n")

      env = os.environ.copy()
      env["PATH"] = f"{fake_bin}:{env['PATH']}"

      result = subprocess.run(
          [bash_path, str(SCRIPT_PATH), "ghcr.io/example/wave:test"],
          cwd=REPO_ROOT,
          env=env,
          capture_output=True,
          text=True,
          check=False,
      )

      self.assertEqual(
          0,
          result.returncode,
          msg=f"retry script failed unexpectedly:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
      )
      self.assertEqual("2", attempt_file.read_text(encoding="utf-8"))
      self.assertIn("unknown blob", f"{result.stdout}\n{result.stderr}")

  def test_deploy_workflow_uses_retry_helper_for_registry_push(self):
    workflow = DEPLOY_WORKFLOW.read_text(encoding="utf-8")

    self.assertIn('docker build --tag "$IMAGE_REF" -f Dockerfile.prebuilt target/universal/stage', workflow)
    self.assertIn('bash scripts/docker-push-with-retry.sh "$IMAGE_REF"', workflow)

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
      return str(path)
    return None


if __name__ == "__main__":
  unittest.main()
