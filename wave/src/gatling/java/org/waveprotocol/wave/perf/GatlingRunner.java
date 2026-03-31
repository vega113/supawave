package org.waveprotocol.wave.perf;

import io.gatling.app.Gatling;

/**
 * Programmatic Gatling runner — invokes simulations without requiring the SBT plugin.
 *
 * Usage:
 *   WAVE_PERF_BASE_URL=http://localhost:9898 sbt \
 *     "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner SearchLoadSimulation"
 *
 * If no simulation name is given, runs all simulations sequentially.
 */
public class GatlingRunner {

    private static final String[] ALL_SIMULATIONS = {
            "org.waveprotocol.wave.perf.SearchLoadSimulation",
            "org.waveprotocol.wave.perf.WaveOpenSimulation",
            "org.waveprotocol.wave.perf.FullJourneySimulation"
    };

    public static void main(String[] args) {
        if (args.length > 0) {
            String simName = args[0];
            if (!simName.contains(".")) {
                simName = "org.waveprotocol.wave.perf." + simName;
            }
            runSimulation(simName);
        } else {
            for (String sim : ALL_SIMULATIONS) {
                System.out.println("\n=== Running: " + sim + " ===\n");
                runSimulation(sim);
            }
        }
    }

    private static void runSimulation(String simulationClass) {
        // Set system properties for Gatling configuration
        System.setProperty("gatling.core.simulationClass", simulationClass);
        System.setProperty("gatling.core.directory.results", "target/gatling");

        try {
            // Use Gatling's args-based entry point
            Gatling.main(new String[0]);
        } catch (Exception e) {
            System.err.println("Simulation " + simulationClass + " failed: " + e.getMessage());
        } finally {
            System.clearProperty("gatling.core.simulationClass");
        }
    }
}
