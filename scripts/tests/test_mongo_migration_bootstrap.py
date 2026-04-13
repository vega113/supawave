import subprocess
import textwrap
import unittest
from pathlib import Path

from scripts.tests.deploy_harness import find_bash, run_deploy_script


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

  def test_deploy_checks_case_insensitive_mongo_values(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  mongodb_driver = "V4"
  account_store_type = "MongoDB"
  delta_store_type = file
}
""",
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_checks_comma_terminated_hocon_mongo_values(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  mongodb_driver = "v4",
  account_store_type = "mongodb",
  delta_store_type = file
}
""",
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_checks_dotted_core_hocon_mongo_values(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core.mongodb_driver = "v4"
core.account_store_type = "mongodb"
core.delta_store_type = file
""",
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_checks_compact_hocon_object_mongo_values(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf='core {mongodb_driver="v4",account_store_type="mongodb",delta_store_type=file}\n',
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_ignores_commented_mongo_config_lines(self):
    """Commented-out config examples must not trigger the migration gate."""
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  # mongodb_driver = "v4"
  # account_store_type = "mongodb"
  delta_store_type = file
}
""",
    )

    # Gate should NOT have fired; deploy proceeds to sanity env-var check.
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertNotIn("did not report Mongo migration completion", combined)
    # Without Mongo gate the deploy reaches the sanity env-var guard.
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_ignores_inline_commented_mongo_config_values(self):
    """Inline HOCON comments must not trigger the migration gate."""
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  some_other_setting = file // account_store_type = "mongodb"
  another_setting = memory # mongodb_driver = "v4"
  delta_store_type = file
}
""",
    )

    combined = f"{result.stdout}\n{result.stderr}"
    self.assertNotIn("did not report Mongo migration completion", combined)
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_skips_marker_gate_for_legacy_images_without_marker_support(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        wave_image="ghcr.io/example/wave:legacy",
        marker_supported=False,
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertNotIn("did not report Mongo migration completion", combined)
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_ignores_partial_value_matches_for_migration_gate(self):
    """Regex must not match extended values like 'v42' or 'mongodbx'."""
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  mongodb_driver = "v42"
  account_store_type = "mongodbx"
  delta_store_type = file
}
""",
    )

    # Gate should NOT have fired (values are not exact); deploy proceeds to
    # sanity env-var check.
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertNotIn("did not report Mongo migration completion", combined)
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_ignores_partial_key_matches_for_migration_gate(self):
    """Regex must not match suffix/prefixed store keys like legacy_account_store_type."""
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
core {
  mongodb_driver = "v4"
  legacy_account_store_type = "mongodb"
  delta_store_type = file
}
""",
    )

    combined = f"{result.stdout}\n{result.stderr}"
    self.assertNotIn("did not report Mongo migration completion", combined)
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_ignores_non_core_hocon_mongo_blocks(self):
    """Only core-scoped Mongo settings should arm the migration gate."""
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        application_conf="""\
legacy {
  mongodb_driver = "v4"
  account_store_type = "mongodb"
}
core {
  mongodb_driver = "v2"
  account_store_type = "file"
  delta_store_type = file
}
""",
    )

    combined = f"{result.stdout}\n{result.stderr}"
    self.assertNotIn("did not report Mongo migration completion", combined)
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_deploy_ignores_stale_migration_marker_from_previous_startup(self):
    result = self._run_deploy(
        log_output="wave started without migration marker\n",
        full_log_output=(
            "Completed Mongock Mongo schema migrations\n"
            "wave started without migration marker\n"
        ),
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("did not report Mongo migration completion", combined)
    self.assertNotIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)

  def test_rollback_allows_legacy_previous_slot_without_migration_marker_support(self):
    result = run_deploy_script(
        REPO_ROOT,
        DEPLOY_SCRIPT,
        textwrap.dedent(
            """#!/usr/bin/env bash
set -euo pipefail
cmd="$*"
if [[ "$1" == "compose" && "$2" == "version" ]]; then
  exit 0
fi
case "$cmd" in
  *" ps wave-blue --format json"*)
    exit 0
    ;;
  *" up -d wave-blue"*)
    ;;
  *" stop wave-green"*)
    ;;
  *)
    echo "unexpected docker invocation: $cmd" >&2
    exit 1
    ;;
esac
""",
        ),
        command="rollback",
        active_slot="green",
        previous_slot="blue",
        release_files={
            "blue": {
                "application.conf": textwrap.dedent(
                    """\
core {
  mongodb_driver = "v4"
  account_store_type = "mongodb"
  delta_store_type = "mongodb"
}
"""
                ),
                "image-ref": "ghcr.io/example/wave:old\n",
            },
        },
    )

    self.assertNotEqual(0, result.returncode)
    combined = f"{result.stdout}\n{result.stderr}"
    self.assertIn("SANITY_ADDRESS and SANITY_PASSWORD must both be set", combined)
    self.assertNotIn("did not report Mongo migration completion", combined)

  def _run_deploy(
      self,
      log_output: str,
      full_log_output: str | None = None,
      wave_image: str = "ghcr.io/example/wave:test",
      marker_supported: bool = True,
      application_conf: str = """\
core {
  mongodb_driver = "v4"
  account_store_type = "mongodb"
  delta_store_type = "mongodb"
}
""",
  ) -> subprocess.CompletedProcess[str]:
    bash_path = find_bash()
    if bash_path is None:
      self.skipTest("requires bash >= 4 to execute deploy/caddy/deploy.sh")

    full_log_output = full_log_output if full_log_output is not None else log_output

    return run_deploy_script(
        REPO_ROOT,
        DEPLOY_SCRIPT,
        textwrap.dedent(
            f"""#!/usr/bin/env bash
set -euo pipefail
cmd="$*"
marker_supported={"1" if marker_supported else "0"}
if [[ "$1" == "compose" && "$2" == "version" ]]; then
  exit 0
fi
if [[ "$cmd" == *"image inspect --format"* && "$cmd" == *"{wave_image}"* ]]; then
  if [[ "$marker_supported" == "1" ]]; then
    printf 'true\\n'
  fi
  exit 0
fi
case "$cmd" in
  *" ps caddy --format json"*)
    printf '{{"Service":"caddy","State":"running"}}\\n'
    ;;
  *" ps -q wave-green"*)
    printf 'wave-green-id\\n'
    ;;
  *" up -d wave-green"*)
    ;;
  *" stop wave-green"*)
    ;;
  "pull {wave_image}"|*" pull {wave_image}"*)
    ;;
  *"inspect --format {{{{.State.StartedAt}}}} wave-green-id"*)
    printf '2026-04-13T11:00:00Z\\n'
    ;;
  *" logs --no-color --since 2026-04-13T11:00:00Z wave-green"*)
    printf '%s' {log_output!r}
    ;;
  *" logs --no-color wave-green"*)
    printf '%s' {full_log_output!r}
    ;;
  *)
    echo "unexpected docker invocation: $cmd" >&2
    exit 1
    ;;
esac
""",
        ),
        application_conf=textwrap.dedent(application_conf),
        wave_image=wave_image,
    )


if __name__ == "__main__":
  unittest.main()
