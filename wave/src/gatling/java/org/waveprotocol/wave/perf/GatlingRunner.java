package org.waveprotocol.wave.perf;

import io.gatling.app.Gatling;

/**
 * Programmatic Gatling runner — invokes a single simulation without requiring the SBT plugin.
 *
 * Usage (single simulation):
 *   WAVE_PERF_BASE_URL=http://localhost:9898 sbt \
 *     "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner SearchLoadSimulation"
 *
 * To run all simulations, use the shell script (each runs in its own JVM):
 *   bash scripts/run-perf-tests.sh
 *
 * Note: Gatling.main() cannot be called multiple times in the same JVM — the actor system
 * and resources are not reset between calls (Gatling issue #4013). Each simulation must
 * run in a separate JVM process.
 */
public class GatlingRunner {

    static final String[] ALL_SIMULATIONS = {
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
            if (!runSimulation(simName)) {
                System.exit(1);
            }
        } else {
            System.err.println("Error: a simulation name is required.");
            System.err.println("Available simulations:");
            for (String sim : ALL_SIMULATIONS) {
                System.err.println("  " + sim.substring(sim.lastIndexOf('.') + 1));
            }
            System.err.println("To run all simulations use: bash scripts/run-perf-tests.sh");
            System.exit(1);
        }
    }

    private static boolean runSimulation(String simulationClass) {
        // Set system properties for Gatling configuration
        System.setProperty("gatling.core.simulationClass", simulationClass);
        System.setProperty("gatling.core.directory.results", "target/gatling");

        try {
            // Use Gatling's args-based entry point
            Gatling.main(new String[0]);
            return true;
        } catch (Exception e) {
            System.err.println("Simulation " + simulationClass + " failed: " + e.getMessage());
            return false;
        } finally {
            System.clearProperty("gatling.core.simulationClass");
        }
    }
}
