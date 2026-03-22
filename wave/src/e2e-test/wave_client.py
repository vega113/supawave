"""
HTTP client for interacting with the Apache Wave server in E2E tests.
"""

from http.cookies import SimpleCookie

import requests


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
