package org.waveprotocol.wave.perf;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Authentication chain for Gatling simulations.
 *
 * Wave uses session-based auth: POST /auth/signin returns 302/303 with
 * Set-Cookie: JSESSIONID=...; wave-session-jwt=...
 * Gatling's cookie jar captures these automatically.
 */
final class WaveAuth {
    static final String DOMAIN = "local.net";
    static final String PASSWORD = "PerfTest123!";
    static final String TEST_USER = "perfuser";
    static final String TEST_ADDRESS = TEST_USER + "@" + DOMAIN;

    /** Register user (idempotent — 200 on success, 403 on duplicate, 302/303 redirects accepted). */
    static final ChainBuilder REGISTER = exec(
            http("Register user")
                    .post("/auth/register")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(StringBody("address=" + TEST_USER + "&password=" + PASSWORD))
                    .check(status().in(200, 302, 303, 403))
    );

    /** Login and capture JSESSIONID in the session cookie jar. */
    static final ChainBuilder LOGIN = exec(
            http("Login")
                    .post("/auth/signin")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(StringBody("address=" + TEST_ADDRESS + "&password=" + PASSWORD))
                    .check(status().in(302, 303))
    );

    /** Register + login chain. */
    static final ChainBuilder AUTHENTICATE = exec(REGISTER).exec(LOGIN);

    private WaveAuth() {}
}
