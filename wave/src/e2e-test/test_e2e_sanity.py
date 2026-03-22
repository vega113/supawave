"""
E2E sanity tests -- Phase 2: registration and login scenarios.
"""

import pytest

# Module-level dict shared across tests to carry session state forward.
SESSION_INFO: dict = {}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _alice(run_id: str) -> str:
    return f"alice_{run_id}"


def _bob(run_id: str) -> str:
    return f"bob_{run_id}"


_PASSWORD = "Secret123!"


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
