import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


def run_deploy_script(
    repo_root: Path,
    deploy_script: Path,
    docker_script: str,
    *,
    command: str = "deploy",
    active_slot: str = "blue",
    previous_slot: str | None = None,
    application_conf: str | None = None,
    release_files: dict[str, dict[str, str]] | None = None,
) -> subprocess.CompletedProcess[str]:
  bash_path = find_bash()
  if bash_path is None:
    raise unittest.SkipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

  with tempfile.TemporaryDirectory(prefix="deploy-script-") as tmp_dir:
    temp_dir = Path(tmp_dir)
    fake_bin = temp_dir / "bin"
    fake_bin.mkdir(parents=True)
    deploy_root = temp_dir / "deploy-root"
    (deploy_root / "shared").mkdir(parents=True)
    (deploy_root / "shared" / "active-slot").write_text(f"{active_slot}\n", encoding="utf-8")
    if previous_slot is not None:
      (deploy_root / "shared" / "previous-slot").write_text(f"{previous_slot}\n", encoding="utf-8")
    (deploy_root / "releases" / "green").mkdir(parents=True)
    if application_conf is not None:
      (deploy_root / "releases" / "green" / "application.conf").write_text(
          application_conf,
          encoding="utf-8",
      )
    for slot, files in (release_files or {}).items():
      slot_dir = deploy_root / "releases" / slot
      slot_dir.mkdir(parents=True, exist_ok=True)
      for relative_path, content in files.items():
        file_path = slot_dir / relative_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content, encoding="utf-8")

    write_executable(fake_bin / "docker", docker_script)
    write_executable(
        fake_bin / "curl",
        """#!/usr/bin/env bash
set -euo pipefail
for arg in "$@"; do
  case "$arg" in
    http://*/healthz|https://*/healthz|http://*/|https://*/)
      exit 0
      ;;
  esac
done
exit 1
""",
    )
    write_executable(
        fake_bin / "systemctl",
        "#!/usr/bin/env bash\nset -euo pipefail\nexit 0\n",
    )
    write_executable(
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
        [bash_path, str(deploy_script), command],
        cwd=repo_root,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )


def write_executable(path: Path, content: str) -> None:
  path.write_text(textwrap.dedent(content), encoding="utf-8")
  path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def find_bash() -> str | None:
  for candidate in (
      os.environ.get("TEST_BASH"),
      "/opt/homebrew/bin/bash",
      "/usr/local/bin/bash",
      "/bin/bash",
  ):
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
