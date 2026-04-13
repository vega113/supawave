import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SCRIPT = REPO_ROOT / "deploy" / "caddy" / "deploy.sh"


class MongoMigrationBootstrapTest(unittest.TestCase):
  def test_deploy_fails_when_migration_success_log_is_missing(self):
    result = self._run_deploy(log_output="wave started without migration marker\n")

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_reaches_sanity_gate_when_migration_success_log_is_present(self):
    result = self._run_deploy(log_output="Completed Mongock Mongo schema migrations\n")

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)
    self.assertNotIn("did not report Mongo migration completion", combined)

  def test_deploy_checks_unquoted_hocon_mongo_values(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  mongodb_driver: v4
  account_store_type = mongodb
  delta_store_type = file
}
""",
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def _run_deploy(
      self,
      log_output: str,
      application_conf: str = """\
core {
  mongodb_driver = "v4"
  account_store_type = "mongodb"
  delta_store_type = "mongodb"
}
""",
  ) -> subprocess.CompletedProcess[str]:
    bash_path = self._bash_path()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

    with tempfile.TemporaryDirectory(prefix="mongo-migration-bootstrap-") as tmp_dir:
      temp_dir = Path(tmp_dir)
      fake_bin = temp_dir / "bin"
      fake_bin.mkdir(parents=True)
      deploy_root = temp_dir / "deploy-root"
      (deploy_root / "shared").mkdir(parents=True)
      (deploy_root / "shared" / "active-slot").write_text("blue\n", encoding="utf-8")
      (deploy_root / "releases" / "green").mkdir(parents=True)
      (deploy_root / "releases" / "green" / "application.conf").write_text(
          textwrap.dedent(application_conf),
          encoding="utf-8",
      )

      self._write_executable(
          fake_bin / "docker",
          f"""#!/usr/bin/env bash
set -euo pipefail
cmd="$*"
if [[ "$1" == "compose" && "$2" == "version" ]]; then
  exit 0
fi
case "$cmd" in
  *" ps caddy --format json"*)
    printf '{{"Service":"caddy","State":"running"}}\\n'
    ;;
  *" up -d wave-green"*)
    ;;
  *" stop wave-green"*)
    ;;
  "pull ghcr.io/example/wave:test"|*" pull ghcr.io/example/wave:test"*)
    ;;
  *" logs --no-color wave-green"*)
    printf '%s' {log_output!r}
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

      return subprocess.run(
          [bash_path, str(DEPLOY_SCRIPT), "deploy"],
          cwd=REPO_ROOT,
          env=env,
          capture_output=True,
          text=True,
          check=False,
      )

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
