package org.waveprotocol.wave.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * End-to-end journey: login → search inbox → open wave → search again.
 * Measures the full user experience latency in a single-user baseline.
 *
 * Prerequisites: run the seed script first to create test waves.
 * Run: WAVE_PERF_BASE_URL=http://localhost:9898 sbt "gatlingTest:testOnly *FullJourneySimulation"
 *
 * Thresholds:
 *   - Mean response time < 2000ms across all requests
 *   - No request should exceed 10s (catches the reported 12-13s regression)
 */
public class FullJourneySimulation extends Simulation {

    ScenarioBuilder fullJourney = scenario("Full user journey")
            .exec(WaveAuth.AUTHENTICATE)
            .pause(Duration.ofMillis(300))
            // Step 1: Search inbox
            .exec(http("Search inbox")
                    .get("/search/")
                    .queryParam("query", "in:inbox")
                    .queryParam("index", "0")
                    .queryParam("numResults", "20")
                    .check(status().is(200))
                    .check(jsonPath("$.3[0].3").optional().saveAs("firstWaveId"))
            )
            .pause(Duration.ofMillis(500))
            // Step 2: Open first wave
            .exec(session -> {
                String waveId = session.getString("firstWaveId");
                if (waveId == null || waveId.isEmpty()) {
                    return session.set("waveToOpen", "local.net/w+dummy");
                }
                return session.set("waveToOpen", waveId);
            })
            .exec(http("Open wave")
                    .get("/fetch/#{waveToOpen}")
                    .check(status().is(200))
                    .check(jsonPath("$").exists())
            )
            .pause(Duration.ofMillis(500))
            // Step 3: Search again
            .exec(http("Search again")
                    .get("/search/")
                    .queryParam("query", "in:inbox")
                    .queryParam("index", "0")
                    .queryParam("numResults", "10")
                    .check(status().is(200))
            );

    {
        setUp(
                fullJourney.injectOpen(atOnceUsers(1))
        ).protocols(WaveProtocol.HTTP_PROTOCOL)
                .assertions(
                        global().responseTime().mean().lt(2000),
                        global().responseTime().max().lt(10000),
                        global().successfulRequests().percent().gt(95.0)
                );
    }
}
