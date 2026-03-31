#!/usr/bin/env bash
# =============================================================================
# run-perf-tests.sh — Run Gatling performance tests against a Wave server.
#
# Usage:
#   bash scripts/run-perf-tests.sh                    # uses http://localhost:9898
#   WAVE_PERF_BASE_URL=http://localhost:9901 bash scripts/run-perf-tests.sh
#   bash scripts/run-perf-tests.sh --skip-seed         # skip data seeding
#   bash scripts/run-perf-tests.sh --simulation Search # run only SearchLoadSimulation
#
# Prerequisites:
#   - A running Wave server (sbt run)
#   - SBT installed
# =============================================================================
set -euo pipefail

BASE_URL="${WAVE_PERF_BASE_URL:-http://localhost:9898}"
SKIP_SEED=false
SIMULATION=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-seed) SKIP_SEED=true; shift ;;
    --simulation) SIMULATION="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "============================================"
echo " Wave Performance Tests"
echo " Server: $BASE_URL"
echo "============================================"
echo ""

# Check server is up
echo -n "Checking server health... "
if ! curl -sf "$BASE_URL/healthz" > /dev/null 2>&1; then
  echo "FAILED"
  echo "Server at $BASE_URL is not responding."
  echo "Start it with: sbt run"
  exit 1
fi
echo "OK"
echo ""

# Seed test data (20 waves × 10 blips × ~500 chars)
if [ "$SKIP_SEED" = false ]; then
  echo "--- Seeding test data ---"
  WAVE_PERF_BASE_URL="$BASE_URL" sbt --batch \
    'GatlingTest / runMain org.waveprotocol.wave.perf.WaveDataSeeder 20 10'
  echo ""
fi

# Run simulations
echo "--- Running Gatling simulations ---"
echo ""

if [ -n "$SIMULATION" ]; then
  WAVE_PERF_BASE_URL="$BASE_URL" sbt --batch \
    "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner ${SIMULATION}"
else
  for sim in SearchLoadSimulation WaveOpenSimulation FullJourneySimulation; do
    echo ">>> $sim"
    WAVE_PERF_BASE_URL="$BASE_URL" sbt --batch \
      "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner ${sim}" || true
    echo ""
  done
fi

echo ""
echo "============================================"
echo " Results: target/gatling/"
echo "============================================"
