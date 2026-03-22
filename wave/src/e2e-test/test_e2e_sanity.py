"""
E2E sanity tests -- Phases 2+3: registration, login, and WebSocket scenarios.
"""

import asyncio

import pytest

from wave_client import (
    make_add_participant_delta,
    make_blip_delta,
    make_open_request,
    make_submit_request,
)

# Module-level dict shared across tests to carry session state forward.
SESSION_INFO: dict = {}

# Domain used by the local dev server (from reference.conf).
DOMAIN = "local.net"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _alice(run_id: str) -> str:
    return f"alice_{run_id}"


def _bob(run_id: str) -> str:
    return f"bob_{run_id}"


def _addr(username: str) -> str:
    """Return the full wave address for a username."""
    return f"{username}@{DOMAIN}"


_PASSWORD = "Secret123!"


def _run(coro):
    """Run an async coroutine in a fresh or existing event loop."""
    try:
        loop = asyncio.get_event_loop()
        if loop.is_closed():
            raise RuntimeError("closed")
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    return loop.run_until_complete(coro)


# ---------------------------------------------------------------------------
# Scenario 1 -- health check
# ---------------------------------------------------------------------------

def test_01_health_check(client):
    assert client.health_check() is True


# ---------------------------------------------------------------------------
# Scenario 2 -- register first user (alice)
# ---------------------------------------------------------------------------

def test_02_register_user1(client, run_id):
    result = client.register_user(_alice(run_id), _PASSWORD)
    assert result["success"], f"Registration failed: {result}"
    assert result["status_code"] == 200


# ---------------------------------------------------------------------------
# Scenario 3 -- register second user (bob)
# ---------------------------------------------------------------------------

def test_03_register_user2(client, run_id):
    result = client.register_user(_bob(run_id), _PASSWORD)
    assert result["success"], f"Registration failed: {result}"
    assert result["status_code"] == 200


# ---------------------------------------------------------------------------
# Scenario 4 -- duplicate registration should fail
# ---------------------------------------------------------------------------

def test_04_duplicate_registration(client, run_id):
    result = client.register_user(_alice(run_id), _PASSWORD)
    assert not result["success"], "Duplicate registration should have failed"
    assert result["status_code"] == 403


# ---------------------------------------------------------------------------
# Scenario 5 -- login user 1 (alice)
# ---------------------------------------------------------------------------

def test_05_login_user1(client, run_id):
    result = client.login(_alice(run_id), _PASSWORD)
    assert result["success"], f"Login failed: {result}"
    assert result["jsessionid"] is not None, "JSESSIONID not set"
    SESSION_INFO["alice"] = result


# ---------------------------------------------------------------------------
# Scenario 6 -- login user 2 (bob)
# ---------------------------------------------------------------------------

def test_06_login_user2(client, run_id):
    result = client.login(_bob(run_id), _PASSWORD)
    assert result["success"], f"Login failed: {result}"
    assert result["jsessionid"] is not None, "JSESSIONID not set"
    SESSION_INFO["bob"] = result


# ===================================================================
# Phase 3 -- WebSocket scenarios
# ===================================================================


# ---------------------------------------------------------------------------
# Scenario 7 -- Alice connects via WebSocket and authenticates
# ---------------------------------------------------------------------------

def test_07_user1_ws_connect(client, run_id):
    """Alice opens a WebSocket and sends ProtocolAuthenticate."""
    session = SESSION_INFO["alice"]

    async def _test():
        ws = await client.ws_connect(session["jsessionid"])
        await client.ws_authenticate(ws, session["jsessionid"])
        # The server should reply with ProtocolAuthenticationResult
        resp = await ws.recv(timeout=10)
        assert resp["messageType"] == "ProtocolAuthenticationResult", (
            f"Expected ProtocolAuthenticationResult, got {resp}"
        )
        SESSION_INFO["alice_ws"] = ws

    _run(_test())


# ---------------------------------------------------------------------------
# Scenario 8 -- Alice creates a new wave
# ---------------------------------------------------------------------------

def test_08_user1_creates_wave(client, run_id):
    """Alice creates a wave by submitting an add_participant delta at v0."""
    wave_id = f"{DOMAIN}!w+e2e{run_id}"
    wavelet_name = f"{DOMAIN}!w+e2e{run_id}/{DOMAIN}!conv+root"
    alice_addr = _addr(_alice(run_id))
    ws = SESSION_INFO["alice_ws"]

    async def _test():
        delta = make_add_participant_delta(alice_addr, alice_addr, 0, "")
        submit = make_submit_request(wavelet_name, delta)
        await ws.send("ProtocolSubmitRequest", submit)

        resp = await ws.recv_until("ProtocolSubmitResponse", timeout=30)
        msg = resp["message"]
        ops = msg.get("1", 0)
        assert int(ops) > 0, f"Expected operations_applied > 0, got {resp}"

        SESSION_INFO["wave_id"] = wave_id
        SESSION_INFO["wavelet_name"] = wavelet_name
        # Stash the hashed version after application for the next delta.
        if "3" in msg:
            SESSION_INFO["last_version"] = msg["3"]

    _run(_test())


# ---------------------------------------------------------------------------
# Scenario 9 -- Alice opens the wave view
# ---------------------------------------------------------------------------

def test_09_user1_opens_wave(client, run_id):
    """Alice opens a view on the newly created wave and receives a snapshot."""
    wave_id = SESSION_INFO["wave_id"]
    alice_addr = _addr(_alice(run_id))
    ws = SESSION_INFO["alice_ws"]

    async def _test():
        open_req = make_open_request(alice_addr, wave_id)
        await ws.send("ProtocolOpenRequest", open_req)

        # The server streams ProtocolWaveletUpdate messages.
        # We expect at least: a channel_id update, a snapshot or delta update,
        # and a marker update.  Collect until we see the marker (field 6).
        updates = []
        deadline = asyncio.get_event_loop().time() + 30
        got_marker = False
        channel_id = None

        while not got_marker:
            remaining = deadline - asyncio.get_event_loop().time()
            if remaining <= 0:
                break
            msg = await ws.recv(timeout=remaining)
            if msg.get("messageType") != "ProtocolWaveletUpdate":
                continue
            updates.append(msg)
            inner = msg.get("message", {})
            # Channel ID is sent in the first update
            if "7" in inner and channel_id is None:
                channel_id = inner["7"]
            # Marker indicates end of initial snapshot set
            if inner.get("6") is True:
                got_marker = True

        assert len(updates) > 0, "Expected at least one ProtocolWaveletUpdate"
        assert got_marker, f"Never received marker update, got {len(updates)} updates"
        SESSION_INFO["channel_id"] = channel_id

    _run(_test())


# ---------------------------------------------------------------------------
# Scenario 10 -- Alice adds Bob as a participant
# ---------------------------------------------------------------------------

def test_10_user1_adds_user2(client, run_id):
    """Alice adds Bob to the wavelet."""
    wavelet_name = SESSION_INFO["wavelet_name"]
    alice_addr = _addr(_alice(run_id))
    bob_addr = _addr(_bob(run_id))
    ws = SESSION_INFO["alice_ws"]
    last_ver = SESSION_INFO.get("last_version")

    async def _test():
        # Build version from last submit response
        version = int(last_ver["1"]) if last_ver else 1
        history_hash = last_ver["2"] if last_ver else ""

        delta = make_add_participant_delta(alice_addr, bob_addr, version, history_hash)
        submit = make_submit_request(
            wavelet_name, delta, channel_id=SESSION_INFO.get("channel_id"),
        )
        await ws.send("ProtocolSubmitRequest", submit)

        resp = await ws.recv_until("ProtocolSubmitResponse", timeout=30)
        msg = resp["message"]
        ops = msg.get("1", 0)
        assert int(ops) > 0, f"Expected operations_applied > 0, got {resp}"

        # Update version for next operation
        if "3" in msg:
            SESSION_INFO["last_version"] = msg["3"]

    _run(_test())


# ---------------------------------------------------------------------------
# Scenario 11 -- Alice writes a blip
# ---------------------------------------------------------------------------

def test_11_user1_writes_blip(client, run_id):
    """Alice submits a blip with text to the wavelet."""
    wavelet_name = SESSION_INFO["wavelet_name"]
    alice_addr = _addr(_alice(run_id))
    ws = SESSION_INFO["alice_ws"]
    last_ver = SESSION_INFO.get("last_version")

    async def _test():
        version = int(last_ver["1"]) if last_ver else 2
        history_hash = last_ver["2"] if last_ver else ""

        blip_id = f"b+e2e{run_id}"
        delta = make_blip_delta(
            alice_addr, blip_id, "Hello from E2E test!", version, history_hash,
        )
        submit = make_submit_request(
            wavelet_name, delta, channel_id=SESSION_INFO.get("channel_id"),
        )
        await ws.send("ProtocolSubmitRequest", submit)

        resp = await ws.recv_until("ProtocolSubmitResponse", timeout=30)
        msg = resp["message"]
        ops = msg.get("1", 0)
        assert int(ops) > 0, f"Expected operations_applied > 0, got {resp}"

        # Store for any future tests that may follow
        if "3" in msg:
            SESSION_INFO["last_version"] = msg["3"]

        # Clean up the WebSocket connection
        await ws.close()

    _run(_test())
