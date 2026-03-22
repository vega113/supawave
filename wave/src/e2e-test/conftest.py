"""
Pytest fixtures for Apache Wave E2E tests.

If the environment variable WAVE_E2E_BASE_URL is set the tests will run
against that URL (the caller is responsible for starting the server).
Otherwise the fixture builds the distribution with Gradle, starts the
server locally, waits for /healthz, and tears it down after the session.
"""

import os
import signal
import subprocess
import time

import pytest
import requests

from wave_client import WaveServerClient

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
_DEFAULT_PORT = 9898
_DEFAULT_BASE_URL = f"http://localhost:{_DEFAULT_PORT}"
_HEALTHZ_TIMEOUT = 90  # seconds


def _wait_for_healthz(base_url: str, timeout: int = _HEALTHZ_TIMEOUT) -> None:
    """Block until GET /healthz returns 200 or *timeout* seconds elapse."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            resp = requests.get(f"{base_url}/healthz", timeout=2)
            if resp.status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(1)
    raise RuntimeError(
        f"Wave server at {base_url} did not become healthy within {timeout}s"
    )


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def server():
    """Yield the base URL of a running Wave server.

    When WAVE_E2E_BASE_URL is set the fixture simply returns it.  Otherwise
    it builds via Gradle, starts the server process, waits for health, and
    stops it on teardown.
    """
    env_url = os.environ.get("WAVE_E2E_BASE_URL")
    if env_url:
        _wait_for_healthz(env_url)
        yield env_url
        return

    # -- Build the distribution ------------------------------------------------
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    gradlew = os.path.join(repo_root, "gradlew")

    print("\n[conftest] Building distribution with :wave:installDist ...")
    build = subprocess.run(
        [gradlew, ":wave:installDist"],
        cwd=repo_root,
        capture_output=True,
        text=True,
        timeout=600,
    )
    if build.returncode != 0:
        raise RuntimeError(
            f"Gradle build failed (rc={build.returncode}):\n{build.stderr[-2000:]}"
        )

    # -- Start the server ------------------------------------------------------
    wave_bin = os.path.join(repo_root, "wave", "build", "install", "wave", "bin", "wave")
    if not os.path.isfile(wave_bin):
        raise FileNotFoundError(f"Wave start script not found: {wave_bin}")

    print(f"[conftest] Starting Wave server from {wave_bin} ...")
    popen_kwargs = dict(
        cwd=os.path.join(repo_root, "wave", "build", "install", "wave"),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if os.name == "posix":
        popen_kwargs["preexec_fn"] = os.setsid

    proc = subprocess.Popen([wave_bin], **popen_kwargs)

    def _terminate(proc):
        """Terminate the server process (and its children on POSIX)."""
        if os.name == "posix":
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        else:
            proc.terminate()

    def _force_kill(proc):
        """Force-kill the server process (and its children on POSIX)."""
        if os.name == "posix":
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        else:
            proc.kill()

    try:
        _wait_for_healthz(_DEFAULT_BASE_URL)
        print("[conftest] Wave server is healthy.")
        yield _DEFAULT_BASE_URL
    finally:
        print("[conftest] Stopping Wave server ...")
        try:
            _terminate(proc)
            proc.wait(timeout=15)
        except Exception:
            _force_kill(proc)
            proc.wait(timeout=5)


@pytest.fixture(scope="session")
def client(server):
    """Return a :class:`WaveServerClient` pointed at the running server."""
    return WaveServerClient(server)


@pytest.fixture(scope="session")
def run_id():
    """Return a short unique suffix for this test run.

    Append this to usernames to avoid collisions across runs.
    """
    return str(int(time.time()))
