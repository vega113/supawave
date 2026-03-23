"""
Tests for the sbt-verify job added to .github/workflows/build.yml.

These tests validate the static YAML structure and configuration values of
the new CI job without requiring a live GitHub Actions runner.
"""

import os
import re

import pytest
import yaml

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

_WORKFLOW_PATH = os.path.join(
    os.path.dirname(__file__), "..", "workflows", "build.yml"
)

_SBT_INSTALL_KEYSERVER_URL = (
    "https://keyserver.ubuntu.com/pks/lookup"
    "?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823"
)
_SBT_APT_REPO = "https://repo.scala-sbt.org/scalasbt/debian"
_SBT_KEYRING = "/etc/apt/keyrings/sbt-archive-keyring.gpg"
_SBT_LIST = "/etc/apt/sources.list.d/sbt.list"
_SBT_COMPILE_CMD = "sbt --batch pst/compile wave/compile"


@pytest.fixture(scope="module")
def workflow():
    """Load and return the parsed build.yml workflow document."""
    with open(_WORKFLOW_PATH) as fh:
        return yaml.safe_load(fh)


@pytest.fixture(scope="module")
def sbt_job(workflow):
    """Return the sbt-verify job definition."""
    return workflow["jobs"]["sbt-verify"]


@pytest.fixture(scope="module")
def sbt_steps(sbt_job):
    """Return the list of steps for the sbt-verify job."""
    return sbt_job["steps"]


# ---------------------------------------------------------------------------
# Job-level configuration
# ---------------------------------------------------------------------------


def test_sbt_verify_job_exists(workflow):
    """The sbt-verify job must be present in the workflow."""
    assert "sbt-verify" in workflow["jobs"]


def test_sbt_verify_job_name(sbt_job):
    """Job display name must be 'SBT Build Verification'."""
    assert sbt_job["name"] == "SBT Build Verification"


def test_sbt_verify_runs_on(sbt_job):
    """Job must target ubuntu-latest runners."""
    assert sbt_job["runs-on"] == "ubuntu-latest"


def test_sbt_verify_timeout(sbt_job):
    """Job timeout must be exactly 30 minutes."""
    assert sbt_job["timeout-minutes"] == 30


def test_sbt_verify_timeout_is_not_excessive(sbt_job):
    """Job timeout must not exceed 60 minutes (sanity upper bound)."""
    assert sbt_job["timeout-minutes"] <= 60


def test_sbt_verify_step_count(sbt_steps):
    """sbt-verify must define exactly four steps."""
    assert len(sbt_steps) == 4


# ---------------------------------------------------------------------------
# Step 0 – Checkout
# ---------------------------------------------------------------------------


def test_checkout_step_name(sbt_steps):
    """First step must be named 'Checkout'."""
    assert sbt_steps[0]["name"] == "Checkout"


def test_checkout_step_action(sbt_steps):
    """Checkout step must use actions/checkout@v4."""
    assert sbt_steps[0]["uses"] == "actions/checkout@v4"


# ---------------------------------------------------------------------------
# Step 1 – Set up JDK 17
# ---------------------------------------------------------------------------


def test_java_setup_step_name(sbt_steps):
    """Second step must be named 'Set up JDK 17'."""
    assert sbt_steps[1]["name"] == "Set up JDK 17"


def test_java_setup_action(sbt_steps):
    """Java setup step must use actions/setup-java@v4."""
    assert sbt_steps[1]["uses"] == "actions/setup-java@v4"


def test_java_setup_distribution(sbt_steps):
    """Java distribution must be 'temurin'."""
    assert sbt_steps[1]["with"]["distribution"] == "temurin"


def test_java_setup_version(sbt_steps):
    """Java version must be '17'."""
    assert str(sbt_steps[1]["with"]["java-version"]) == "17"


def test_java_setup_cache_type(sbt_steps):
    """Java setup must cache 'sbt' (not 'gradle')."""
    assert sbt_steps[1]["with"]["cache"] == "sbt"


def test_java_setup_cache_not_gradle(sbt_steps):
    """Cache type must not be 'gradle' (sbt project uses sbt cache)."""
    assert sbt_steps[1]["with"]["cache"] != "gradle"


# ---------------------------------------------------------------------------
# Step 2 – Install sbt
# ---------------------------------------------------------------------------


def test_install_sbt_step_name(sbt_steps):
    """Third step must be named 'Install sbt'."""
    assert sbt_steps[2]["name"] == "Install sbt"


def test_install_sbt_step_is_run(sbt_steps):
    """Install sbt step must use a 'run' command block (not 'uses')."""
    assert "run" in sbt_steps[2]
    assert "uses" not in sbt_steps[2]


def test_install_sbt_creates_keyrings_dir(sbt_steps):
    """Install script must create /etc/apt/keyrings with mode 0755."""
    run = sbt_steps[2]["run"]
    assert "install -d -m 0755 /etc/apt/keyrings" in run


def test_install_sbt_curl_uses_fail_flag(sbt_steps):
    """curl invocation must include --fail so non-2xx responses abort the build."""
    run = sbt_steps[2]["run"]
    assert "--fail" in run


def test_install_sbt_curl_uses_show_error_flag(sbt_steps):
    """curl invocation must include --show-error for diagnostic output."""
    run = sbt_steps[2]["run"]
    assert "--show-error" in run


def test_install_sbt_curl_uses_location_flag(sbt_steps):
    """curl invocation must include --location to follow redirects."""
    run = sbt_steps[2]["run"]
    assert "--location" in run


def test_install_sbt_curl_retry_count(sbt_steps):
    """curl must be configured to retry 5 times for transient network errors."""
    run = sbt_steps[2]["run"]
    assert "--retry 5" in run


def test_install_sbt_gpg_key_url(sbt_steps):
    """Install script must fetch the correct SBT GPG key from keyserver.ubuntu.com."""
    run = sbt_steps[2]["run"]
    assert "keyserver.ubuntu.com" in run
    assert "0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" in run


def test_install_sbt_gpg_fingerprint(sbt_steps):
    """The exact SBT signing key fingerprint must be used."""
    run = sbt_steps[2]["run"]
    assert "2EE0EA64E40A89B84B2DF73499E82A75642AC823" in run


def test_install_sbt_keyring_destination(sbt_steps):
    """GPG key must be written to the correct keyring path."""
    run = sbt_steps[2]["run"]
    assert _SBT_KEYRING in run


def test_install_sbt_apt_repo_url(sbt_steps):
    """Apt repo must point to the official Scala SBT debian repository."""
    run = sbt_steps[2]["run"]
    assert _SBT_APT_REPO in run


def test_install_sbt_apt_repo_signed_by(sbt_steps):
    """Apt sources entry must reference the keyring via signed-by."""
    run = sbt_steps[2]["run"]
    assert f"signed-by={_SBT_KEYRING}" in run


def test_install_sbt_apt_sources_list_path(sbt_steps):
    """Apt sources list file must be placed at the expected path."""
    run = sbt_steps[2]["run"]
    assert _SBT_LIST in run


def test_install_sbt_apt_get_update(sbt_steps):
    """Install script must run apt-get update before installing."""
    run = sbt_steps[2]["run"]
    assert "apt-get update" in run


def test_install_sbt_apt_get_update_quiet(sbt_steps):
    """apt-get update must use quiet flag (-qq) to reduce log noise."""
    run = sbt_steps[2]["run"]
    assert "apt-get update -qq" in run


def test_install_sbt_apt_get_install(sbt_steps):
    """Install script must install the 'sbt' package via apt-get."""
    run = sbt_steps[2]["run"]
    assert "apt-get install" in run
    assert "sbt" in run


def test_install_sbt_apt_get_install_flags(sbt_steps):
    """apt-get install must use -y (non-interactive) and -qq (quiet) flags."""
    run = sbt_steps[2]["run"]
    assert "apt-get install -y -qq sbt" in run


def test_install_sbt_uses_sudo(sbt_steps):
    """Privileged operations in the install step must use sudo."""
    run = sbt_steps[2]["run"]
    assert "sudo" in run


def test_install_sbt_repo_distribution(sbt_steps):
    """Apt repo entry must specify the 'all' distribution."""
    run = sbt_steps[2]["run"]
    assert "all main" in run


# ---------------------------------------------------------------------------
# Step 3 – SBT compile verification
# ---------------------------------------------------------------------------


def test_sbt_compile_step_name(sbt_steps):
    """Fourth step must be named 'SBT compile verification'."""
    assert sbt_steps[3]["name"] == "SBT compile verification"


def test_sbt_compile_step_is_run(sbt_steps):
    """SBT compile step must use 'run' (not 'uses')."""
    assert "run" in sbt_steps[3]
    assert "uses" not in sbt_steps[3]


def test_sbt_compile_command(sbt_steps):
    """SBT compile step must run the exact expected command."""
    assert sbt_steps[3]["run"].strip() == _SBT_COMPILE_CMD


def test_sbt_compile_uses_batch_flag(sbt_steps):
    """sbt must be invoked with --batch to prevent interactive prompts in CI."""
    assert "--batch" in sbt_steps[3]["run"]


def test_sbt_compile_includes_pst_module(sbt_steps):
    """Compile command must include the pst sub-project."""
    assert "pst/compile" in sbt_steps[3]["run"]


def test_sbt_compile_includes_wave_module(sbt_steps):
    """Compile command must include the wave sub-project."""
    assert "wave/compile" in sbt_steps[3]["run"]


def test_sbt_compile_pst_before_wave(sbt_steps):
    """pst/compile must appear before wave/compile in the command."""
    cmd = sbt_steps[3]["run"]
    assert cmd.index("pst/compile") < cmd.index("wave/compile")


# ---------------------------------------------------------------------------
# Regression – existing jobs remain unchanged
# ---------------------------------------------------------------------------


def test_existing_jobs_still_present(workflow):
    """Pre-existing jobs must not have been removed by this PR."""
    jobs = workflow["jobs"]
    for expected_job in ("server-jdk17", "server-jakarta", "client-gwt"):
        assert expected_job in jobs, f"Job '{expected_job}' was unexpectedly removed"


def test_sbt_verify_is_independent_job(workflow):
    """sbt-verify must not declare any 'needs' dependencies on other jobs."""
    sbt_job = workflow["jobs"]["sbt-verify"]
    assert "needs" not in sbt_job


def test_workflow_trigger_on_pull_request(workflow):
    """Workflow must trigger on pull_request events (covering sbt-verify).

    PyYAML parses the bare YAML key ``on`` as the Python boolean ``True``,
    so we access ``workflow[True]`` rather than ``workflow["on"]``.
    """
    # PyYAML parses bare `on:` as True (YAML 1.1 boolean synonym)
    triggers = workflow.get(True) or workflow.get("on", {})
    assert "pull_request" in triggers


def test_sbt_verify_no_continue_on_error(sbt_steps):
    """No step in sbt-verify should silently swallow failures via continue-on-error."""
    for step in sbt_steps:
        assert step.get("continue-on-error") is not True, (
            f"Step '{step.get('name')}' has continue-on-error: true"
        )