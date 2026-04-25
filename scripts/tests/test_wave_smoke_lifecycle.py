import os
import re
import signal
import shutil
import socket
import subprocess
import sys
import textwrap
import time
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "wave-smoke.sh"


# This script-level regression proves wave-smoke.sh preserves and stops a
# normal long-lived staged process. It does not reproduce ServerMain.run()
# returning early; the Java join behavior is covered by the SBT staged smoke
# verification because it requires the real native-packager launcher.


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def write_fake_install(tmp_path: Path, server_source: str) -> Path:
    install = tmp_path / "stage"
    bin_dir = install / "bin"
    bin_dir.mkdir(parents=True)
    server = install / "fake_wave_server.py"
    server.write_text(server_source)
    launcher = bin_dir / "wave"
    launcher.write_text(
        "#!/usr/bin/env bash\n"
        "set -euo pipefail\n"
        f"exec {sys.executable} {server}\n"
    )
    launcher.chmod(0o755)
    return install


def run_smoke(install: Path, port: int, command: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update(
        {
            "INSTALL_DIR": str(install),
            "PORT": str(port),
            "STOP_TIMEOUT": "5",
        }
    )
    args = ["bash", str(SCRIPT), command]
    try:
        return subprocess.run(
            args,
            cwd=ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=20,
            check=False,
        )
    except subprocess.TimeoutExpired:
        if command == "start":
            subprocess.run(
                ["bash", str(SCRIPT), "stop"],
                cwd=ROOT,
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=10,
                check=False,
            )
        raise


def assert_pid_alive(pid: int) -> None:
    os.kill(pid, 0)


def wait_for_pid_exit(pid: int, timeout_seconds: float = 7.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return
        time.sleep(0.2)
    raise AssertionError(f"PID {pid} did not exit within {timeout_seconds} seconds")


def port_accepts_connection(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.5):
            return True
    except OSError:
        return False


def has_pid_discovery_tool() -> bool:
    return any(shutil.which(tool) for tool in ("lsof", "ss", "fuser"))


def listener_pids(port: int) -> list[int]:
    if shutil.which("lsof"):
        completed = subprocess.run(
            ["lsof", f"-tiTCP:{port}", "-sTCP:LISTEN"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=5,
            check=False,
        )
        return sorted({int(line) for line in completed.stdout.splitlines() if line.strip()})
    if shutil.which("ss"):
        completed = subprocess.run(
            ["ss", "-tlnp", f"sport = :{port}"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=5,
            check=False,
        )
        return sorted({int(pid) for pid in re.findall(r"pid=(\d+)", completed.stdout)})
    if shutil.which("fuser"):
        completed = subprocess.run(
            ["fuser", f"{port}/tcp"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=5,
            check=False,
        )
        pids = {int(pid) for pid in re.findall(r"\b\d+\b", completed.stdout)}
        if not pids:
            for line in completed.stderr.splitlines():
                rhs = line.split(":", 1)[1] if ":" in line else line
                pids.update(int(pid) for pid in re.findall(r"\b\d+\b", rhs))
        return sorted(pids)
    return []


def assert_listener_pid(port: int, expected_pid: int) -> None:
    pids = listener_pids(port)
    if pids:
        assert pids == [expected_pid]


def read_pid(install: Path) -> int:
    return int((install / "wave_server.pid").read_text().strip())


def wait_for_port(port: int, timeout_seconds: float = 5.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if port_accepts_connection(port):
            return
        time.sleep(0.1)
    raise AssertionError(f"Port {port} did not start accepting connections")


def test_start_check_stop_keeps_fake_staged_server_alive(tmp_path: Path) -> None:
    port = free_port()
    install = write_fake_install(
        tmp_path,
        textwrap.dedent(
            """
            import http.server
            import os
            import signal
            import socketserver
            import sys
            from urllib.parse import urlparse

            port = int(os.environ["PORT"])

            class Handler(http.server.BaseHTTPRequestHandler):
                def do_GET(self):
                    body = b""
                    status = 200
                    parsed = urlparse(self.path)
                    route = parsed.path
                    query = parsed.query
                    if route == "/" and query == "":
                        body = b"<script src='webclient/webclient.nocache.js'></script>"
                    elif route == "/healthz":
                        body = b"ok"
                    elif route == "/" and query == "view=landing":
                        body = b"landing"
                    elif route == "/" and query == "view=j2cl-root":
                        body = b"<div data-j2cl-root-shell></div>"
                    elif route in (
                        "/j2cl/index.html",
                        "/j2cl-search/sidecar/j2cl-sidecar.js",
                        "/webclient/webclient.nocache.js",
                    ):
                        body = b"asset"
                    else:
                        status = 404
                    self.send_response(status)
                    self.end_headers()
                    self.wfile.write(body)

                def log_message(self, format, *args):
                    return

            class ReusableTCPServer(socketserver.TCPServer):
                allow_reuse_address = True

            signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
            with ReusableTCPServer(("127.0.0.1", port), Handler) as httpd:
                httpd.serve_forever()
            """
        ),
    )

    start = run_smoke(install, port, "start")
    try:
        assert start.returncode == 0, start.stdout + start.stderr
        assert "READY" in start.stdout
        pid_file = install / "wave_server.pid"
        assert pid_file.exists()
        recorded_pid = read_pid(install)
        assert_listener_pid(port, recorded_pid)
        assert_pid_alive(recorded_pid)

        time.sleep(1.0)
        check = run_smoke(install, port, "check")
        assert check.returncode == 0, check.stdout + check.stderr
        assert "ROOT_STATUS=200" in check.stdout
        assert "J2CL_ROOT_STATUS=200" in check.stdout
        assert "WEBCLIENT_STATUS=200" in check.stdout
        assert_pid_alive(recorded_pid)
        assert_listener_pid(port, recorded_pid)

        if not has_pid_discovery_tool():
            return

        duplicate_start = run_smoke(install, port, "start")
        assert duplicate_start.returncode == 0, duplicate_start.stdout + duplicate_start.stderr
        assert "READY" in duplicate_start.stdout
        replacement_pid = read_pid(install)
        assert replacement_pid != recorded_pid
        wait_for_pid_exit(recorded_pid)
        assert_pid_alive(replacement_pid)
        assert_listener_pid(port, replacement_pid)

        after_duplicate_check = run_smoke(install, port, "check")
        assert after_duplicate_check.returncode == 0, (
            after_duplicate_check.stdout + after_duplicate_check.stderr
        )

        os.kill(replacement_pid, signal.SIGTERM)
        wait_for_pid_exit(replacement_pid)
        assert not port_accepts_connection(port)
    finally:
        stop = run_smoke(install, port, "stop")
        assert stop.returncode == 0, stop.stdout + stop.stderr
        assert not (install / "wave_server.pid").exists()
        assert not port_accepts_connection(port)


def test_start_refuses_non_wave_process_on_port(tmp_path: Path) -> None:
    if not has_pid_discovery_tool():
        pytest.skip("port PID detection tool unavailable")

    port = free_port()
    install = write_fake_install(tmp_path, "raise SystemExit('should not launch')\n")
    listener = subprocess.Popen(
        [
            sys.executable,
            "-c",
            (
                "import socket, time\n"
                f"sock = socket.socket(); sock.bind(('127.0.0.1', {port})); "
                "sock.listen(); time.sleep(60)\n"
            ),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        wait_for_port(port)
        start = run_smoke(install, port, "start")
        assert start.returncode != 0
        assert "non-Wave process" in start.stderr
        assert listener.poll() is None
    finally:
        listener.terminate()
        try:
            listener.wait(timeout=5)
        except subprocess.TimeoutExpired:
            listener.kill()
            listener.wait(timeout=5)
