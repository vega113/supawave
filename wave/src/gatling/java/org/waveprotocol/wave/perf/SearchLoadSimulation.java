package org.waveprotocol.wave.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Measures GET /search/?query=in:inbox latency under load.
 *
 * Prerequisites: run the seed script first to create test waves.
 * Run: WAVE_PERF_BASE_URL=http://localhost:9898 sbt "gatlingTest:testOnly *SearchLoadSimulation"
 *
 * Thresholds:
 *   - P95 < 2000ms (baseline single-user)
 *   - P99 < 5000ms
 */
public class SearchLoadSimulation extends Simulation {

    ScenarioBuilder searchScenario = scenario("Search inbox load")
            .exec(WaveAuth.AUTHENTICATE)
            .pause(Duration.ofMillis(500))
            .repeat(10).on(
                    exec(http("Search inbox")
                            .get("/search/")
                            .queryParam("query", "in:inbox")
                            .queryParam("index", "0")
                            .queryParam("numResults", "30")
                            .check(status().is(200))
                            .check(jsonPath("$").exists())
                    ).pause(Duration.ofMillis(200), Duration.ofMillis(500))
            );

    {
        setUp(
                searchScenario.injectOpen(atOnceUsers(1))
        ).protocols(WaveProtocol.HTTP_PROTOCOL)
                .assertions(
                        global().responseTime().percentile(95.0).lt(2000),
                        global().responseTime().percentile(99.0).lt(5000),
                        global().successfulRequests().percent().gt(95.0)
                );
    }
}
