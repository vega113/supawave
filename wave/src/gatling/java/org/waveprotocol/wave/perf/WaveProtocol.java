package org.waveprotocol.wave.perf;

import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Shared HTTP protocol configuration for all Wave Gatling simulations.
 */
final class WaveProtocol {
    static final String BASE_URL = System.getenv().getOrDefault(
            "WAVE_PERF_BASE_URL", "http://localhost:9898");

    static final HttpProtocolBuilder HTTP_PROTOCOL = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .disableFollowRedirect();

    private WaveProtocol() {}
}
