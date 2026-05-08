import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "deploy-contabo.yml"
ROLLBACK_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "rollback-contabo.yml"
TRUST_SCRIPT = REPO_ROOT / "scripts" / "deployment" / "trust-ssh-host-key.sh"

FINGERPRINT_ENV = (
    "CONTABO_HOST_FINGERPRINT: ${{ vars.CONTABO_HOST_FINGERPRINT || "
    "secrets.CONTABO_HOST_FINGERPRINT }}"
)


class ContaboHostKeyWorkflowTest(unittest.TestCase):
  def test_deploy_and_rollback_require_pinned_host_fingerprint(self):
    for workflow_path in (DEPLOY_WORKFLOW, ROLLBACK_WORKFLOW):
      workflow = workflow_path.read_text(encoding="utf-8")

      self.assertIn(FINGERPRINT_ENV, workflow)
      self.assertIn("scripts/deployment/trust-ssh-host-key.sh", workflow)
      self.assertIn('"$CONTABO_HOST_FINGERPRINT"', workflow)

  def test_deploy_trusts_pinned_host_key_before_uploading_bundle(self):
    workflow = DEPLOY_WORKFLOW.read_text(encoding="utf-8")

    trust_idx = workflow.index("name: Trust Contabo host key")
    upload_idx = workflow.index("name: Upload bundle")
    self.assertLess(trust_idx, upload_idx)
    self.assertIn("CONTABO_HOST_FINGERPRINT", workflow[trust_idx:upload_idx])

  def test_shared_trust_script_rejects_missing_fingerprint_without_network(self):
    with tempfile.TemporaryDirectory() as temp_dir:
      root = Path(temp_dir)
      fake_bin = root / "bin"
      fake_bin.mkdir()
      sentinel = root / "ssh_keyscan_called"
      write_executable(
          fake_bin / "ssh-keyscan",
          f"""\
          #!/usr/bin/env bash
          touch {sentinel}
          exit 0
          """,
      )
      env = os.environ.copy()
      env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"

      result = subprocess.run(
          [str(TRUST_SCRIPT), "contabo.example", "22", ""],
          cwd=REPO_ROOT,
          env=env,
          text=True,
          capture_output=True,
          check=False,
      )

      self.assertNotEqual(0, result.returncode)
      self.assertIn("CONTABO_HOST_FINGERPRINT must be set", result.stderr)
      self.assertFalse(sentinel.exists(), "ssh-keyscan must not be called when fingerprint is missing")

  def test_shared_trust_script_accepts_matching_scanned_fingerprint(self):
    result, known_hosts = run_trust_script_with_fake_ssh_key_tools(
        expected="SHA256:expected",
        actual="SHA256:expected",
    )

    self.assertEqual(
        0,
        result.returncode,
        msg=f"STDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
    )
    self.assertIn("ssh-ed25519 AAAATESTKEY", known_hosts)

  def test_shared_trust_script_rejects_mismatched_scanned_fingerprint(self):
    result, _home = run_trust_script_with_fake_ssh_key_tools(
        expected="SHA256:expected",
        actual="SHA256:actual",
    )

    self.assertNotEqual(0, result.returncode)
    self.assertIn("SSH host key fingerprint mismatch", result.stderr)
    self.assertIn("Expected: SHA256:expected", result.stderr)
    self.assertIn("SHA256:actual", result.stderr)

  def test_build_workflow_runs_host_key_workflow_tests(self):
    workflow = (REPO_ROOT / ".github" / "workflows" / "build.yml").read_text(
        encoding="utf-8"
    )

    self.assertIn(
        "python3 -m unittest scripts.tests.test_contabo_host_key_workflows -v",
        workflow,
    )


def run_trust_script_with_fake_ssh_key_tools(expected: str, actual: str):
  with tempfile.TemporaryDirectory() as temp_dir:
    root = Path(temp_dir)
    fake_bin = root / "bin"
    home = root / "home"
    runner_temp = root / "runner"
    fake_bin.mkdir()
    home.mkdir()
    runner_temp.mkdir()

    write_executable(
        fake_bin / "ssh-keyscan",
        """\
        #!/usr/bin/env bash
        set -euo pipefail
        printf '# contabo.example:22 SSH-2.0-OpenSSH_test\\n'
        printf 'contabo.example ssh-ed25519 AAAATESTKEY\\n'
        """,
    )
    write_executable(
        fake_bin / "ssh-keygen",
        f"""\
        #!/usr/bin/env bash
        set -euo pipefail
        printf '256 {actual} contabo.example (ED25519)\\n'
        """,
    )

    env = os.environ.copy()
    env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
    env["HOME"] = str(home)
    env["RUNNER_TEMP"] = str(runner_temp)

    result = subprocess.run(
        [str(TRUST_SCRIPT), "contabo.example", "22", expected],
        cwd=REPO_ROOT,
        env=env,
        text=True,
        capture_output=True,
        check=False,
    )
    known_hosts_path = home / ".ssh" / "known_hosts"
    known_hosts = (
        known_hosts_path.read_text(encoding="utf-8")
        if known_hosts_path.exists()
        else ""
    )
    return result, known_hosts


def write_executable(path: Path, content: str):
  path.write_text(textwrap.dedent(content), encoding="utf-8")
  path.chmod(0o755)
