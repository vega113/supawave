import os
import shlex
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEPLOY_BUNDLE_DIR = REPO_ROOT / "deploy/caddy"
SETUP_SWAP_SCRIPT = REPO_ROOT / "deploy/supawave-host/setup-swap.sh"
VALIDATE_SCRIPT = REPO_ROOT / "deploy/supawave-host/validate.sh"
NEAR_NOMINAL_SWAP_BYTES = (32 * 1024 * 1024 * 1024) - 4096


def write_executable(path: Path, content: str) -> None:
    path.write_text(textwrap.dedent(content), encoding="utf-8")
    path.chmod(0o755)


class DeployRecoveryTest(unittest.TestCase):
    def make_temp_dir(self, prefix: str) -> Path:
        temp_dir = Path(tempfile.mkdtemp(prefix=prefix))
        self.addCleanup(shutil.rmtree, temp_dir, ignore_errors=True)
        return temp_dir

    def build_env(self, bin_dir: Path, extra_env: dict[str, str]) -> dict[str, str]:
        env = os.environ.copy()
        env["PATH"] = f"{bin_dir}:{env['PATH']}"
        env.update(extra_env)
        return env

    def checked_replace(
        self,
        text: str,
        old: str,
        new: str,
        description: str,
        count: int = -1,
    ) -> str:
        patched_text = text.replace(old, new, count)
        if patched_text == text:
            raise AssertionError(
                f"Failed to patch {description}: expected to replace {old!r}, but no changes were made"
            )
        return patched_text

    def write_testable_deploy_bundle(self, temp_dir: Path) -> Path:
        bundle_dir = temp_dir / "deploy-bundle"
        bundle_dir.mkdir()

        for name in ("compose.yml", "Caddyfile", "application.conf"):
            shutil.copy(DEPLOY_BUNDLE_DIR / name, bundle_dir / name)

        deploy_script = (DEPLOY_BUNDLE_DIR / "deploy.sh").read_text(encoding="utf-8")
        overrides = textwrap.dedent(
            """
            acquire_lock() { :; }
            release_lock() { :; }
            pull_image() { :; }
            wait_for_slot_health() { return 0; }
            sanity_check_slot() { return 0; }
            generate_upstream() { :; }
            reload_caddy() { :; }
            post_swap_smoke() { return 0; }
            schedule_cooldown() { :; }
            """
        ).strip()
        marker = "# ---------------------------------------------------------------------------\n# Main\n# ---------------------------------------------------------------------------"
        deploy_script = self.checked_replace(
            deploy_script,
            marker,
            f"{overrides}\n\n{marker}",
            "deploy script main marker",
            1,
        )

        deploy_path = bundle_dir / "deploy.sh"
        deploy_path.write_text(deploy_script, encoding="utf-8")
        deploy_path.chmod(0o755)
        return deploy_path

    def write_testable_setup_swap_script(self, temp_dir: Path) -> Path:
        script_text = SETUP_SWAP_SCRIPT.read_text(encoding="utf-8")
        script_text = self.checked_replace(
            script_text,
            textwrap.dedent(
                """
                require_root() {
                  if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
                    exec sudo -- "$0" "$@"
                  fi
                }
                """
            ).strip(),
            textwrap.dedent(
                """
                require_root() {
                  :
                }
                """
            ).strip(),
            "setup-swap.sh require_root",
            1,
        )
        script_text = self.checked_replace(
            script_text,
            "/etc/fstab",
            "${TEST_ETC_FSTAB:-/etc/fstab}",
            "setup-swap.sh /etc/fstab",
        )
        script_text = self.checked_replace(
            script_text,
            "/etc/sysctl.d/99-wave.conf",
            "${TEST_SYSCTL_CONF:-/etc/sysctl.d/99-wave.conf}",
            "setup-swap.sh /etc/sysctl.d/99-wave.conf",
        )
        script_text = self.checked_replace(
            script_text,
            "/proc/sys/vm/swappiness",
            "${TEST_PROC_SWAPPINESS:-/proc/sys/vm/swappiness}",
            "setup-swap.sh /proc/sys/vm/swappiness",
        )
        script_text = self.checked_replace(
            script_text,
            'local swap_path="/swapfile"',
            'local swap_path="${TEST_SWAP_PATH:-/swapfile}"',
            "setup-swap.sh swap_path",
            1,
        )

        script_path = temp_dir / "setup-swap.sh"
        script_path.write_text(script_text, encoding="utf-8")
        script_path.chmod(0o755)
        return script_path

    def write_testable_validate_script(self, temp_dir: Path) -> Path:
        script_text = VALIDATE_SCRIPT.read_text(encoding="utf-8")
        script_text = self.checked_replace(
            script_text,
            textwrap.dedent(
                """
                require_root() {
                  if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
                    exec sudo -- "$0" "$@"
                  fi
                }
                """
            ).strip(),
            textwrap.dedent(
                """
                require_root() {
                  :
                }
                """
            ).strip(),
            "validate.sh require_root",
            1,
        )
        script_text = self.checked_replace(
            script_text,
            'local swap_path="/swapfile"',
            'local swap_path="${TEST_SWAP_PATH:-/swapfile}"',
            "validate.sh swap_path",
            1,
        )
        script_text = self.checked_replace(
            script_text,
            "/var/log/wave-supawave",
            "${TEST_LOG_DIR:-/var/log/wave-supawave}",
            "validate.sh log dir",
        )

        script_path = temp_dir / "validate.sh"
        script_path.write_text(script_text, encoding="utf-8")
        script_path.chmod(0o755)
        return script_path

    def write_migration_mocks(
        self,
        bin_dir: Path,
        docker_log: Path,
        compose_container_id: str | None = "legacy-wave-container",
        compose_container_image: str | None = "ghcr.io/example/wave:legacy",
        fallback_container_name: str | None = None,
        fallback_container_image: str | None = None,
    ) -> None:
        docker_log_path = shlex.quote(str(docker_log))
        write_executable(
            bin_dir / "docker",
            f"""
            #!/usr/bin/env bash
            set -euo pipefail
            compose_container_id={shlex.quote(compose_container_id or "")}
            compose_container_image={shlex.quote(compose_container_image or "")}
            fallback_container_name={shlex.quote(fallback_container_name or "")}
            fallback_container_image={shlex.quote(fallback_container_image or "")}
            printf '%s\n' "$*" >> {docker_log_path}

            if [[ "${{1:-}}" == "compose" && "${{2:-}}" == "version" ]]; then
              exit 0
            fi

            if [[ "${{1:-}}" == "compose" && "$*" == *" images wave --format "* ]]; then
              echo "format value \"{{{{.Repository}}}}:{{{{.Tag}}}}\" could not be parsed: parsing failed" >&2
              exit 1
            fi

            if [[ "${{1:-}}" == "compose" && "$*" == *" ps -aq wave"* ]]; then
              if [[ -n "$compose_container_id" ]]; then
                printf '%s\n' "$compose_container_id"
              fi
              exit 0
            fi

            if [[ "${{1:-}}" == "inspect" && "${{2:-}}" == "--format" ]]; then
              if [[ -n "$compose_container_id" && "${{4:-}}" == "$compose_container_id" && -n "$compose_container_image" ]]; then
                printf '%s\n' "$compose_container_image"
                exit 0
              fi

              if [[ -n "$fallback_container_name" && "${{4:-}}" == "$fallback_container_name" ]]; then
                if [[ -n "$fallback_container_image" ]]; then
                  printf '%s\n' "$fallback_container_image"
                fi
                exit 0
              fi

              exit 0
            fi

            if [[ "${{1:-}}" == "compose" ]]; then
              exit 0
            fi

            exit 0
            """,
        )

    def write_swap_mocks(self, bin_dir: Path, command_log: Path, swap_path: Path) -> None:
        command_log_path = shlex.quote(str(command_log))
        quoted_swap_path = shlex.quote(str(swap_path))
        write_executable(
            bin_dir / "swapon",
            f"""
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'swapon %s\n' "$*" >> {command_log_path}

            if [[ "${{1:-}}" == "--show=NAME" && "${{2:-}}" == "--noheadings" ]]; then
              printf '%s\n' {quoted_swap_path}
              exit 0
            fi

            if [[ "${{1:-}}" == "--show=NAME,SIZE" && "${{2:-}}" == "--bytes" && "${{3:-}}" == "--noheadings" ]]; then
              printf '%s %s\n' {quoted_swap_path} {NEAR_NOMINAL_SWAP_BYTES}
              exit 0
            fi

            exit 0
            """,
        )
        write_executable(
            bin_dir / "swapoff",
            f"""
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'swapoff %s\n' "$*" >> {command_log_path}
            exit 0
            """,
        )
        write_executable(
            bin_dir / "fallocate",
            f"""
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'fallocate %s\n' "$*" >> {command_log_path}
            if [[ "${{1:-}}" == "-l" && -n "${{3:-}}" ]]; then
              mkdir -p "$(dirname "$3")"
              : > "$3"
            fi
            exit 0
            """,
        )
        write_executable(
            bin_dir / "mkswap",
            f"""
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'mkswap %s\n' "$*" >> {command_log_path}
            exit 0
            """,
        )
        write_executable(
            bin_dir / "sysctl",
            f"""
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'sysctl %s\n' "$*" >> {command_log_path}

            if [[ "${{1:-}}" == "-w" ]]; then
              exit 0
            fi

            if [[ "${{1:-}}" == "-n" ]]; then
              case "${{2:-}}" in
                fs.file-max) echo 2097152 ;;
                fs.nr_open) echo 1048576 ;;
                vm.swappiness) echo 10 ;;
                net.core.somaxconn) echo 65535 ;;
                *) echo 0 ;;
              esac
              exit 0
            fi

            exit 0
            """,
        )
        write_executable(
            bin_dir / "df",
            """
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'Filesystem 1048576-blocks Used Available Capacity Mounted on\n'
            printf '/dev/mock 1048576 1024 1047552 1%% /\n'
            """,
        )
        write_executable(
            bin_dir / "docker",
            """
            #!/usr/bin/env bash
            set -euo pipefail
            if [[ "${1:-}" == "ps" ]]; then
              exit 0
            fi
            exit 0
            """,
        )
        write_executable(
            bin_dir / "curl",
            """
            #!/usr/bin/env bash
            set -euo pipefail
            printf '200'
            """,
        )

    def test_deploy_migration_uses_compose_v2_safe_legacy_probe(self) -> None:
        temp_dir = self.make_temp_dir("deploy-recovery-migration-")
        deploy_root = temp_dir / "supawave"
        legacy_dir = deploy_root / "current"
        legacy_dir.mkdir(parents=True)
        (legacy_dir / "compose.yml").write_text("services:\n  wave:\n    image: ghcr.io/example/wave:legacy\n", encoding="utf-8")

        bin_dir = temp_dir / "bin"
        bin_dir.mkdir()
        docker_log = temp_dir / "docker.log"
        self.write_migration_mocks(bin_dir, docker_log)
        deploy_script = self.write_testable_deploy_bundle(temp_dir)

        result = subprocess.run(
            [str(deploy_script), "deploy"],
            capture_output=True,
            text=True,
            env=self.build_env(
                bin_dir,
                {
                    "DEPLOY_ROOT": str(deploy_root),
                    "WAVE_IMAGE": "ghcr.io/example/wave:target",
                },
            ),
        )

        self.assertEqual(0, result.returncode, msg=f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
        self.assertIn("Deploying to green slot", result.stdout)
        self.assertIn("Inspecting legacy compose file", result.stdout)
        self.assertIn("Discovering legacy wave image", result.stdout)
        self.assertIn("Stopping legacy wave container", result.stdout)
        self.assertIn("Starting blue slot and caddy", result.stdout)

        docker_commands = docker_log.read_text(encoding="utf-8")
        self.assertIn("ps -aq wave", docker_commands)
        self.assertIn("inspect --format {{.Config.Image}} legacy-wave-container", docker_commands)
        self.assertIn("rm -f legacy-wave-container", docker_commands)
        self.assertNotIn("stop wave", docker_commands)
        self.assertNotIn("rm -f wave", docker_commands)
        self.assertEqual(
            "ghcr.io/example/wave:legacy",
            (deploy_root / "releases/blue/image-ref").read_text(encoding="utf-8").strip(),
        )
        self.assertEqual(
            "ghcr.io/example/wave:target",
            (deploy_root / "releases/green/image-ref").read_text(encoding="utf-8").strip(),
        )
        self.assertEqual(
            "green",
            (deploy_root / "shared/active-slot").read_text(encoding="utf-8").strip(),
        )
        self.assertTrue((deploy_root / "shared/indexes/green/lucene9").is_dir())

    def test_deploy_migration_uses_target_image_fallback_when_legacy_image_unresolved(self) -> None:
        temp_dir = self.make_temp_dir("deploy-recovery-migration-fallback-")
        deploy_root = temp_dir / "supawave"
        legacy_dir = deploy_root / "current"
        legacy_dir.mkdir(parents=True)
        (legacy_dir / "compose.yml").write_text("services:\n  wave:\n    image: ghcr.io/example/wave:legacy\n", encoding="utf-8")

        bin_dir = temp_dir / "bin"
        bin_dir.mkdir()
        docker_log = temp_dir / "docker.log"
        self.write_migration_mocks(
            bin_dir,
            docker_log,
            compose_container_id=None,
            compose_container_image=None,
            fallback_container_name="supawave-wave-1",
            fallback_container_image=None,
        )
        deploy_script = self.write_testable_deploy_bundle(temp_dir)

        result = subprocess.run(
            [str(deploy_script), "deploy"],
            capture_output=True,
            text=True,
            env=self.build_env(
                bin_dir,
                {
                    "DEPLOY_ROOT": str(deploy_root),
                    "WAVE_IMAGE": "ghcr.io/example/wave:target",
                },
            ),
        )

        self.assertEqual(0, result.returncode, msg=f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
        self.assertIn("Legacy wave image unresolved; using target image fallback", result.stdout)
        self.assertIn("Falling back to legacy container name supawave-wave-1", result.stdout)

        docker_commands = docker_log.read_text(encoding="utf-8")
        self.assertIn("ps -aq wave", docker_commands)
        self.assertIn("inspect --format {{.Config.Image}} supawave-wave-1", docker_commands)
        self.assertIn("rm -f supawave-wave-1", docker_commands)
        self.assertEqual(
            "ghcr.io/example/wave:target",
            (deploy_root / "releases/blue/image-ref").read_text(encoding="utf-8").strip(),
        )
        self.assertEqual(
            "ghcr.io/example/wave:target",
            (deploy_root / "releases/green/image-ref").read_text(encoding="utf-8").strip(),
        )
        self.assertEqual(
            "green",
            (deploy_root / "shared/active-slot").read_text(encoding="utf-8").strip(),
        )
        self.assertTrue((deploy_root / "shared/indexes/green/lucene9").is_dir())

    def test_swap_tolerance_accepts_near_nominal_size(self) -> None:
        with self.subTest("setup-swap keeps an already-active near-nominal swapfile"):
            temp_dir = self.make_temp_dir("deploy-recovery-setup-swap-")
            bin_dir = temp_dir / "bin"
            bin_dir.mkdir()
            command_log = temp_dir / "commands.log"
            swap_path = temp_dir / "swapfile"
            swap_path.write_text("", encoding="utf-8")
            proc_swappiness = temp_dir / "proc" / "swappiness"
            proc_swappiness.parent.mkdir(parents=True)
            proc_swappiness.write_text("10\n", encoding="utf-8")
            etc_dir = temp_dir / "etc"
            etc_dir.mkdir()
            fstab_path = etc_dir / "fstab"
            fstab_path.write_text("", encoding="utf-8")
            sysctl_conf = etc_dir / "99-wave.conf"
            setup_script = self.write_testable_setup_swap_script(temp_dir)
            self.write_swap_mocks(bin_dir, command_log, swap_path)

            result = subprocess.run(
                [str(setup_script)],
                capture_output=True,
                text=True,
                env=self.build_env(
                    bin_dir,
                    {
                        "BACKUP_DIR": str(temp_dir / "backups"),
                        "SWAP_SIZE_GB": "32",
                        "TEST_SWAP_PATH": str(swap_path),
                        "TEST_ETC_FSTAB": str(fstab_path),
                        "TEST_SYSCTL_CONF": str(sysctl_conf),
                        "TEST_PROC_SWAPPINESS": str(proc_swappiness),
                    },
                ),
            )

            self.assertEqual(0, result.returncode, msg=f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
            self.assertIn("already active", result.stdout)
            self.assertIn("Swap setup complete", result.stdout)

            executed_commands = command_log.read_text(encoding="utf-8")
            self.assertNotIn("swapoff", executed_commands)
            self.assertNotIn("fallocate", executed_commands)
            self.assertNotIn("mkswap", executed_commands)

        with self.subTest("validate post accepts the same near-nominal swapfile"):
            temp_dir = self.make_temp_dir("deploy-recovery-validate-swap-")
            bin_dir = temp_dir / "bin"
            bin_dir.mkdir()
            command_log = temp_dir / "commands.log"
            swap_path = temp_dir / "swapfile"
            swap_path.write_text("", encoding="utf-8")
            log_dir = temp_dir / "logs"
            validate_script = self.write_testable_validate_script(temp_dir)
            self.write_swap_mocks(bin_dir, command_log, swap_path)

            result = subprocess.run(
                [str(validate_script), "post"],
                capture_output=True,
                text=True,
                env=self.build_env(
                    bin_dir,
                    {
                        "SWAP_SIZE_GB": "32",
                        "TEST_SWAP_PATH": str(swap_path),
                        "TEST_LOG_DIR": str(log_dir),
                    },
                ),
            )

            self.assertEqual(0, result.returncode, msg=f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
            self.assertIn("Running post-flight checks", result.stdout)
            self.assertIn("OK: swapfile active", result.stdout)
            self.assertIn("Validation completed", result.stdout)

        with self.subTest("setup-swap rejects invalid swap sizes"):
            temp_dir = self.make_temp_dir("deploy-recovery-invalid-swap-")
            setup_script = self.write_testable_setup_swap_script(temp_dir)

            result = subprocess.run(
                [str(setup_script)],
                capture_output=True,
                text=True,
                env=self.build_env(
                    temp_dir / "bin",
                    {
                        "SWAP_SIZE_GB": "0",
                    },
                ),
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("SWAP_SIZE_GB must be a positive integer", result.stdout + result.stderr)

        with self.subTest("validate rejects invalid swap sizes"):
            temp_dir = self.make_temp_dir("deploy-recovery-invalid-swap-validate-")
            log_dir = temp_dir / "logs"
            validate_script = self.write_testable_validate_script(temp_dir)

            result = subprocess.run(
                [str(validate_script), "post"],
                capture_output=True,
                text=True,
                env=self.build_env(
                    temp_dir / "bin",
                    {
                        "SWAP_SIZE_GB": "0",
                        "TEST_LOG_DIR": str(log_dir),
                    },
                ),
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("SWAP_SIZE_GB must be a positive integer", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
