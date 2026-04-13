import os
import shlex
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = REPO_ROOT / "scripts" / "docker-push-with-retry.sh"
DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "deploy-contabo.yml"


class DockerPushWithRetryTest(unittest.TestCase):
  def test_retries_unknown_blob_then_succeeds(self):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash to execute docker-push-with-retry.sh")

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

      env = self._build_mock_command_env(
          bash_path=bash_path,
          fake_bin=fake_bin,
      )

      result = subprocess.run(  # noqa: S603 - trusted subprocess invocation in test harness
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

  def test_fails_fast_on_non_transient_error(self):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash to execute docker-push-with-retry.sh")

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

echo "manifest unknown" >&2
exit 1
""",
      )

      result = self._run_script(
          bash_path=bash_path,
          fake_bin=fake_bin,
          image_ref="ghcr.io/example/wave:test",
      )

      self.assertNotEqual(0, result.returncode)
      self.assertEqual("1", attempt_file.read_text(encoding="utf-8"))
      self.assertIn("non-transient error", f"{result.stdout}\n{result.stderr}")

  def test_exits_non_zero_after_max_transient_attempts(self):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash to execute docker-push-with-retry.sh")

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

echo "unknown blob" >&2
exit 1
""",
      )
      self._write_executable(fake_bin / "sleep", "#!/usr/bin/env bash\nexit 0\n")

      result = self._run_script(
          bash_path=bash_path,
          fake_bin=fake_bin,
          image_ref="ghcr.io/example/wave:test",
          extra_env={"DOCKER_PUSH_MAX_ATTEMPTS": "3"},
      )

      self.assertNotEqual(0, result.returncode)
      self.assertEqual("3", attempt_file.read_text(encoding="utf-8"))
      self.assertIn("unknown blob", f"{result.stdout}\n{result.stderr}")
      self.assertIn("failed after 3 attempts", f"{result.stdout}\n{result.stderr}")

  def test_build_mock_command_env_prepends_fake_bin_and_resolved_bash_dir(self):
    fake_bin = Path("/tmp/fake-bin")
    bash_path = "/tmp/custom-bash/bash"

    with patch.dict(os.environ, {"PATH": "/usr/bin:/bin"}):
      env = self._build_mock_command_env(
          bash_path=bash_path,
          fake_bin=fake_bin,
      )

    self.assertEqual(
        f"{fake_bin}{os.pathsep}{Path(bash_path).parent}{os.pathsep}/usr/bin:/bin",
        env["PATH"],
    )

  def test_rejects_invalid_retry_configuration(self):
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash to execute docker-push-with-retry.sh")

    with tempfile.TemporaryDirectory(prefix="docker-push-retry-") as tmp_dir:
      temp_dir = Path(tmp_dir)
      fake_bin = temp_dir / "bin"
      fake_bin.mkdir(parents=True)
      self._write_executable(
          fake_bin / "docker",
          """#!/usr/bin/env bash
set -euo pipefail
echo "docker should not run for invalid retry config" >&2
exit 99
""",
      )

      cases = (
          (
              {"DOCKER_PUSH_MAX_ATTEMPTS": "0"},
              "DOCKER_PUSH_MAX_ATTEMPTS must be a positive integer",
          ),
          (
              {"DOCKER_PUSH_RETRY_DELAY_SECONDS": "nope"},
              "DOCKER_PUSH_RETRY_DELAY_SECONDS must be a non-negative integer",
          ),
      )
      for extra_env, expected_message in cases:
        with self.subTest(extra_env=extra_env):
          result = self._run_script(
              bash_path=bash_path,
              fake_bin=fake_bin,
              image_ref="ghcr.io/example/wave:test",
              extra_env=extra_env,
          )
          self.assertEqual(64, result.returncode)
          self.assertIn(expected_message, f"{result.stdout}\n{result.stderr}")

  def test_deploy_workflow_uses_retry_helper_for_registry_push(self):
    workflow = DEPLOY_WORKFLOW.read_text(encoding="utf-8")

    self.assertIn('docker build --tag "$IMAGE_REF" -f Dockerfile.prebuilt target/universal/stage', workflow)
    self.assertIn('bash scripts/docker-push-with-retry.sh "$IMAGE_REF"', workflow)

  @staticmethod
  def _write_executable(path: Path, content: str) -> None:
    path.write_text(textwrap.dedent(content), encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

  @staticmethod
  def _run_script(
      bash_path: str,
      fake_bin: Path,
      image_ref: str,
      extra_env: dict[str, str] | None = None,
  ) -> subprocess.CompletedProcess[str]:
    env = DockerPushWithRetryTest._build_mock_command_env(
        bash_path=bash_path,
        fake_bin=fake_bin,
    )
    if extra_env:
      env.update(extra_env)
    return subprocess.run(  # noqa: S603 - trusted subprocess invocation in test harness
        [bash_path, str(SCRIPT_PATH), image_ref],
        cwd=REPO_ROOT,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )

  @staticmethod
  def _build_mock_command_env(
      bash_path: str,
      fake_bin: Path,
  ) -> dict[str, str]:
    env = os.environ.copy()
    path_parts = [str(fake_bin), str(Path(bash_path).parent)]
    if current_path := env.get("PATH"):
      path_parts.append(current_path)
    env["PATH"] = os.pathsep.join(path_parts)
    return env

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
