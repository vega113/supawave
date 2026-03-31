package org.waveprotocol.wave.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.time.Duration;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Measures wave fetch latency (GET /fetch/{domain}/{waveId}) under load.
 *
 * Prerequisites: run the seed script first to create test waves.
 * Run: WAVE_PERF_BASE_URL=http://localhost:9898 sbt "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner WaveOpenSimulation"
 *
 * Thresholds:
 *   - P95 < 3000ms (baseline single-user)
 *   - P99 < 5000ms
 */
public class WaveOpenSimulation extends Simulation {

    ScenarioBuilder waveOpenScenario = scenario("Wave open load")
            .exec(WaveAuth.AUTHENTICATE)
            .pause(Duration.ofMillis(500))
            // Discover wave IDs via search
            .exec(http("Search for waves")
                    .get("/search/")
                    .queryParam("query", "in:inbox")
                    .queryParam("index", "0")
                    .queryParam("numResults", "20")
                    .check(status().is(200))
                    .check(jsonPath("$.3[*].3").findAll().optional().saveAs("waveIds"))
            )
            .pause(Duration.ofMillis(300))
            // Fetch waves in a loop
            .repeat(10, "fetchIdx").on(
                    exec(session -> {
                        List<String> waveIds = session.getList("waveIds");
                        if (waveIds == null || waveIds.isEmpty()) {
                            return session.set("currentWaveId", "local.net/w+dummy");
                        }
                        int idx = (int) session.getLong("fetchIdx") % waveIds.size();
                        return session.set("currentWaveId", waveIds.get(idx));
                    })
                    .exec(http("Fetch wave")
                            .get("/fetch/#{currentWaveId}")
                            .check(status().is(200))
                            .check(jsonPath("$").exists())
                    )
                    .pause(Duration.ofMillis(200), Duration.ofMillis(500))
            );

    {
        setUp(
                waveOpenScenario.injectOpen(atOnceUsers(1))
        ).protocols(WaveProtocol.HTTP_PROTOCOL)
                .assertions(
                        global().responseTime().percentile(95.0).lt(3000),
                        global().responseTime().percentile(99.0).lt(5000),
                        global().successfulRequests().percent().gt(95.0)
                );
    }
}
