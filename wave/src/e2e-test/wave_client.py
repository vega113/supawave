"""
HTTP + WebSocket client for interacting with the Apache Wave server in E2E tests.
"""

import asyncio
import json
from http.cookies import SimpleCookie

import requests
import websockets


# ---------------------------------------------------------------------------
# WebSocket wrapper
# ---------------------------------------------------------------------------

class WaveWebSocket:
    """Thin async wrapper around a ``websockets`` connection.

    Messages are framed as JSON envelopes::

        {"messageType": "<name>", "sequenceNumber": <int>, "message": {...}}
    """

    def __init__(self, ws, seq: int = 0):
        self.ws = ws
        self.seq = seq

    async def send(self, msg_type: str, payload: dict):
        """Send an envelope with the given *msg_type* and *payload*."""
        msg = {
            "messageType": msg_type,
            "sequenceNumber": self.seq,
            "message": payload,
        }
        self.seq += 1
        await self.ws.send(json.dumps(msg))

    async def recv(self, timeout: float = 10) -> dict:
        """Receive and parse a single JSON envelope."""
        raw = await asyncio.wait_for(self.ws.recv(), timeout=timeout)
        return json.loads(raw)

    async def recv_until(self, msg_type: str, timeout: float = 30) -> dict:
        """Receive messages until one with *messageType* == *msg_type* arrives."""
        loop = asyncio.get_event_loop()
        deadline = loop.time() + timeout
        while True:
            remaining = deadline - loop.time()
            if remaining <= 0:
                raise TimeoutError(f"Timed out waiting for {msg_type}")
            msg = await self.recv(timeout=remaining)
            if msg.get("messageType") == msg_type:
                return msg

    async def close(self):
        """Gracefully close the underlying connection."""
        await self.ws.close()


# ---------------------------------------------------------------------------
# Wave protocol helpers (numeric-string-key format)
# ---------------------------------------------------------------------------

def make_hashed_version(version: int, history_hash: str) -> dict:
    """Build a ``ProtocolHashedVersion`` payload.

    ``history_hash`` must be an uppercase-hex-encoded string (or empty
    string for version 0).
    """
    return {"1": version, "2": history_hash}


def make_open_request(participant: str, wave_id: str) -> dict:
    """Build a ``ProtocolOpenRequest`` payload.

    Fields: 1=participant_id, 2=wave_id, 3=wavelet_id_prefix[], 4=known_wavelet[].
    """
    return {
        "1": participant,
        "2": wave_id,
        "3": [],
        "4": [],
    }


def make_add_participant_delta(
    author: str,
    new_participant: str,
    version: int,
    history_hash: str,
) -> dict:
    """Build a ``ProtocolWaveletDelta`` with a single *add_participant* op."""
    return {
        "1": make_hashed_version(version, history_hash),
        "2": author,
        "3": [{"1": new_participant}],   # operations[]: add_participant
        "4": [],                          # address_path
    }


def make_blip_delta(
    author: str,
    blip_id: str,
    text: str,
    version: int,
    history_hash: str,
) -> dict:
    """Build a ``ProtocolWaveletDelta`` with a *mutate_document* op.

    Creates (or overwrites) a document with ``<body><line/>text</body>``.
    """
    return {
        "1": make_hashed_version(version, history_hash),
        "2": author,
        "3": [{
            "3": {                               # mutate_document
                "1": blip_id,                    # document_id
                "2": {                           # document_operation
                    "1": [                       # components[]
                        {"3": {"1": "body", "2": []}},    # element_start <body>
                        {"3": {"1": "line", "2": []}},    # element_start <line>
                        {"4": True},                       # element_end </line>
                        {"2": text},                       # characters
                        {"4": True},                       # element_end </body>
                    ],
                },
            },
        }],
        "4": [],
    }


def make_submit_request(
    wavelet_name: str,
    delta: dict,
    channel_id: str | None = None,
) -> dict:
    """Build a ``ProtocolSubmitRequest`` payload.

    Fields: 1=wavelet_name, 2=delta, 3=channel_id (optional).
    """
    msg: dict = {"1": wavelet_name, "2": delta}
    if channel_id:
        msg["3"] = channel_id
    return msg


class WaveServerClient:
    """Thin wrapper around the Wave server HTTP API used by E2E tests."""

    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()

    # ------------------------------------------------------------------
    # Health
    # ------------------------------------------------------------------

    def health_check(self) -> bool:
        """GET /healthz -- return True when the server reports 200."""
        try:
            resp = self.session.get(f"{self.base_url}/healthz", timeout=5)
            return resp.status_code == 200
        except requests.RequestException:
            return False

    # ------------------------------------------------------------------
    # Registration
    # ------------------------------------------------------------------

    def register_user(self, username: str, password: str) -> dict:
        """POST /auth/register with form-encoded address + password.

        Returns dict with *status_code* and *success* (bool).
        The server returns 200 on success and 403 on failure (duplicate
        account, registration disabled, etc.).
        """
        resp = self.session.post(
            f"{self.base_url}/auth/register",
            data={
                "address": username,
                "password": password,
            },
            allow_redirects=True,
            timeout=10,
        )
        return {
            "status_code": resp.status_code,
            "success": resp.status_code == 200,
        }

    # ------------------------------------------------------------------
    # Login
    # ------------------------------------------------------------------

    def login(self, username: str, password: str, domain: str = "local.net") -> dict:
        """POST /auth/signin with form-encoded address + password.

        Uses a *fresh* requests session so that cookies from a previous
        login do not leak into this request.  ``allow_redirects`` is
        disabled so we can inspect the Set-Cookie header directly.

        Returns dict with *status_code*, *jsessionid*, *jwt*, and
        *success* (bool).
        """
        # Use a throwaway session so that an existing JSESSIONID cookie
        # from a prior login does not get sent along.
        fresh = requests.Session()
        resp = fresh.post(
            f"{self.base_url}/auth/signin",
            data={
                "address": f"{username}@{domain}",
                "password": password,
            },
            allow_redirects=False,
            timeout=10,
        )

        jsessionid = None
        jwt = None

        # Extract cookies from the response
        for cookie in resp.cookies:
            if cookie.name == "JSESSIONID":
                jsessionid = cookie.value
            elif cookie.name == "wave-session-jwt":
                jwt = cookie.value

        # Fallback: parse Set-Cookie header with http.cookies.SimpleCookie
        # to correctly handle commas in Expires values.
        if jsessionid is None or jwt is None:
            raw_headers = (
                resp.raw.headers.getlist("Set-Cookie")
                if hasattr(resp.raw.headers, "getlist")
                else [resp.headers.get("Set-Cookie", "")]
            )
            for hdr in raw_headers:
                cookie = SimpleCookie()
                try:
                    cookie.load(hdr)
                except Exception:
                    continue
                if jsessionid is None and "JSESSIONID" in cookie:
                    jsessionid = cookie["JSESSIONID"].value
                if jwt is None and "wave-session-jwt" in cookie:
                    jwt = cookie["wave-session-jwt"].value

        return {
            "status_code": resp.status_code,
            "jsessionid": jsessionid,
            "jwt": jwt,
            "success": resp.status_code in (200, 302, 303),
        }

    # ------------------------------------------------------------------
    # WebSocket
    # ------------------------------------------------------------------

    async def ws_connect(self, jsessionid: str) -> WaveWebSocket:
        """Open a WebSocket to ``/socket`` authenticated via JSESSIONID cookie."""
        ws_url = self.base_url.replace("http://", "ws://").replace("https://", "wss://")
        ws = await websockets.connect(
            f"{ws_url}/socket",
            additional_headers={"Cookie": f"JSESSIONID={jsessionid}"},
        )
        return WaveWebSocket(ws)

    async def ws_authenticate(self, wave_ws: WaveWebSocket, token: str):
        """Send ``ProtocolAuthenticate`` and (optionally) wait for the result.

        On the Jakarta / Jetty path the server already knows the user from
        the JSESSIONID cookie sent during the upgrade, so this is a
        belt-and-suspenders step.  The server replies with
        ``ProtocolAuthenticationResult``.
        """
        await wave_ws.send("ProtocolAuthenticate", {"1": token})

    # ------------------------------------------------------------------
    # Search
    # ------------------------------------------------------------------

    def search(self, jsessionid: str, query: str = "in:inbox") -> dict:
        """GET /search/?query=...&index=0&numResults=10 with JSESSIONID cookie.

        Returns the parsed JSON body on success, or a dict with the raw
        status code and text on failure.
        """
        cookies = {"JSESSIONID": jsessionid}
        resp = self.session.get(
            f"{self.base_url}/search/",
            params={"query": query, "index": 0, "numResults": 10},
            cookies=cookies,
            timeout=10,
        )
        try:
            return resp.json()
        except ValueError:
            return {
                "status_code": resp.status_code,
                "text": resp.text,
            }
