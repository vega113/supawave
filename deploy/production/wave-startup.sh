#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# =============================================================================
# Wave Production Startup Script
# =============================================================================
#
# Prepares and launches a Wave server instance with optimized JVM settings for
# the supawave production host (94 GiB RAM, 18 AMD EPYC cores).
#
# Usage:
#   ./wave-startup.sh [blue|green]
#
# The optional argument selects a color slot for blue/green deployments.
# If omitted, defaults to "blue".
#
# Environment overrides (all optional):
#   WAVE_HEAP_MAX    - Maximum heap size       (default: 20G)
#   WAVE_HEAP_INIT   - Initial heap size       (default: 6G)
#   WAVE_GC_PAUSE    - Max GC pause target ms  (default: 200)
#   WAVE_HOME        - Wave installation root   (default: /opt/wave)
#   WAVE_LOG_DIR     - Log directory            (default: $WAVE_HOME/logs)
#   WAVE_EXTRA_OPTS  - Additional JAVA_OPTS appended after defaults
#
# WARNING: These defaults assume a 94 GiB host running two Wave instances.
#          For smaller hosts reduce WAVE_HEAP_MAX proportionally:
#            32 GiB host, single instance -> WAVE_HEAP_MAX=24G
#            32 GiB host, blue/green      -> WAVE_HEAP_MAX=12G
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
SLOT="${1:-blue}"
if [[ "$SLOT" != "blue" && "$SLOT" != "green" ]]; then
  echo "Usage: $0 [blue|green]" >&2
  exit 1
fi

WAVE_HOME="${WAVE_HOME:-/opt/wave}"
WAVE_LOG_DIR="${WAVE_LOG_DIR:-${WAVE_HOME}/logs}"
WAVE_HEAP_MAX="${WAVE_HEAP_MAX:-20G}"
WAVE_HEAP_INIT="${WAVE_HEAP_INIT:-6G}"
WAVE_GC_PAUSE="${WAVE_GC_PAUSE:-200}"
WAVE_EXTRA_OPTS="${WAVE_EXTRA_OPTS:-}"

# ---------------------------------------------------------------------------
# Ensure log directory exists
# ---------------------------------------------------------------------------
mkdir -p "$WAVE_LOG_DIR"

# ---------------------------------------------------------------------------
# Build JAVA_OPTS
# ---------------------------------------------------------------------------
# Heap sizing rationale (see RESOURCE_OPTIMIZATION_ANALYSIS.md):
#   -Xmx20G  : Each instance gets 20 GiB.  Two instances = 40 GiB, leaving
#               ~54 GiB for MongoDB (12 GiB limit), OS caches, and headroom.
#   -Xms6G   : Start small so the JVM does not pin 40 GiB on boot during a
#               blue/green switchover when both instances are briefly live.
#               G1 expands the heap transparently as load arrives.
#
# GC rationale:
#   G1GC is the recommended collector for heaps above 4 GiB.  It provides
#   region-based concurrent collection with a tunable pause-time target.
#   -XX:MaxGCPauseMillis=200  : Soft target; G1 will trade throughput to stay
#                                under 200 ms.  Suitable for an interactive
#                                collaboration server.
#   -XX:+ParallelRefProcEnabled : Process reference objects in parallel during
#                                  GC, reducing pause time on large heaps.
#   -XX:InitiatingHeapOccupancyPercent=35 : Start concurrent marking early.
#                                  At 20 GiB this triggers at ~7 GiB used,
#                                  giving G1 plenty of time to reclaim before
#                                  the heap fills.  Lower values trade CPU for
#                                  shorter pauses.
#
# Diagnostics:
#   HeapDumpOnOutOfMemoryError : Produces an hprof file for post-mortem
#                                 analysis if the JVM exhausts the heap.
#   GC logging (Xlog)          : Rotated log with timestamps for correlating
#                                 latency spikes with GC activity.
#   JMX (disabled by default)  : Uncomment the JMX block below if you need
#                                 remote monitoring via VisualVM / JConsole.
# ---------------------------------------------------------------------------

JAVA_OPTS=(
  # -- Heap ------------------------------------------------------------------
  "-Xmx${WAVE_HEAP_MAX}"
  "-Xms${WAVE_HEAP_INIT}"

  # -- Garbage Collector -----------------------------------------------------
  "-XX:+UseG1GC"
  "-XX:MaxGCPauseMillis=${WAVE_GC_PAUSE}"
  "-XX:+ParallelRefProcEnabled"
  "-XX:InitiatingHeapOccupancyPercent=35"

  # -- Crash Diagnostics -----------------------------------------------------
  "-XX:+HeapDumpOnOutOfMemoryError"
  # Use a directory, not a fixed filename, so repeated OOM events create
  # separate dump files (java_pid<PID>.hprof) instead of overwriting.
  "-XX:HeapDumpPath=${WAVE_LOG_DIR}/"
  "-XX:+ExitOnOutOfMemoryError"

  # -- GC Logging (Java 17+ unified logging) ---------------------------------
  "-Xlog:gc*:file=${WAVE_LOG_DIR}/gc-${SLOT}.log:time,uptime,level,tags:filecount=5,filesize=50m"

  # -- Large Pages (optional, requires OS configuration) ---------------------
  # Uncomment if transparent huge pages or explicit huge pages are enabled on
  # the host.  Reduces TLB misses and improves throughput for large heaps.
  # "-XX:+UseLargePages"

  # -- JMX Remote Monitoring (disabled by default) ---------------------------
  # Uncomment for remote JVM monitoring.  In production:
  #   1. JMX is bound to localhost only (local.only=true + hostname=127.0.0.1).
  #   2. Use an SSH tunnel for remote access: ssh -L 9010:localhost:9010 user@host
  #   3. The authenticate=false / ssl=false settings are acceptable ONLY because
  #      JMX is restricted to loopback.  SSH provides auth and encryption.
  #   4. If you need network-exposed JMX, enable authentication and TLS:
  #        -Dcom.sun.management.jmxremote.authenticate=true
  #        -Dcom.sun.management.jmxremote.password.file=/opt/wave/config/jmxremote.password
  #        -Dcom.sun.management.jmxremote.access.file=/opt/wave/config/jmxremote.access
  #        -Dcom.sun.management.jmxremote.ssl=true
  # "-Dcom.sun.management.jmxremote"
  # "-Dcom.sun.management.jmxremote.port=9010"
  # "-Dcom.sun.management.jmxremote.local.only=true"
  # "-Dcom.sun.management.jmxremote.authenticate=false"
  # "-Dcom.sun.management.jmxremote.ssl=false"
  # "-Dcom.sun.management.jmxremote.rmi.port=9010"
  # "-Djava.rmi.server.hostname=127.0.0.1"
)

# Append any extra options the operator provided.
if [[ -n "$WAVE_EXTRA_OPTS" ]]; then
  # Word-splitting is intentional here to allow multiple flags in one variable.
  # Disable globbing to prevent wildcard characters in flags (e.g. -D*) from
  # being expanded against the filesystem.
  # shellcheck disable=SC2206
  set -f
  JAVA_OPTS+=($WAVE_EXTRA_OPTS)
  set +f
fi

export JAVA_OPTS="${JAVA_OPTS[*]}"

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
echo "=== Wave Production Startup (${SLOT}) ==="
echo "  Heap max:       ${WAVE_HEAP_MAX}"
echo "  Heap init:      ${WAVE_HEAP_INIT}"
echo "  GC:             G1GC (pause target ${WAVE_GC_PAUSE} ms)"
echo "  Heap dump dir:  ${WAVE_LOG_DIR}/"
echo "  GC log:         ${WAVE_LOG_DIR}/gc-${SLOT}.log"
echo "  Log dir:        ${WAVE_LOG_DIR}"
# Redact any -D properties that might contain secrets (API keys, passwords).
JAVA_OPTS_DISPLAY=$(echo "$JAVA_OPTS" | sed 's/-D[^ ]*\(password\|secret\|key\|token\)[^ ]*/[REDACTED]/gi')
echo "  JAVA_OPTS:      ${JAVA_OPTS_DISPLAY}"

# Warn if file descriptor limit is below recommended threshold.
NOFILE_SOFT=$(ulimit -Sn 2>/dev/null || echo "unknown")
if [[ "$NOFILE_SOFT" != "unknown" && "$NOFILE_SOFT" != "unlimited" && "$NOFILE_SOFT" -lt 131072 ]]; then
  echo ""
  echo "  WARNING: soft nofile limit is ${NOFILE_SOFT} (recommended >= 131072)."
  echo "           Apply deploy/production/limits.conf.prod and reboot, or run:"
  echo "             ulimit -n 131072"
  echo ""
fi

# Warn if no swap is configured.
SWAP_TOTAL=$(awk '/SwapTotal/{print $2}' /proc/meminfo 2>/dev/null || echo "0")
if [[ "$SWAP_TOTAL" -eq 0 ]] 2>/dev/null; then
  echo ""
  echo "  WARNING: No swap space detected.  An OOM event will kill the process"
  echo "           immediately with no graceful degradation.  See"
  echo "           RESOURCE_OPTIMIZATION_ANALYSIS.md Phase 1.3 for swap setup."
  echo ""
fi

echo ""

# ---------------------------------------------------------------------------
# Launch Wave
# ---------------------------------------------------------------------------
# If running inside Docker, the entrypoint handles the actual java command.
# If running on bare metal, exec into the Wave run script.
# Verify log directory is writable before handing off to the JVM.
if [[ ! -w "$WAVE_LOG_DIR" ]]; then
  echo "ERROR: Log directory ${WAVE_LOG_DIR} is not writable." >&2
  echo "       GC logs and heap dumps will fail.  Fix permissions first." >&2
  exit 1
fi

if [[ -x "${WAVE_HOME}/bin/run-server.sh" ]]; then
  exec "${WAVE_HOME}/bin/run-server.sh"
elif [[ -x "${WAVE_HOME}/bin/wave" ]]; then
  exec "${WAVE_HOME}/bin/wave"
else
  echo "ERROR: Could not find executable Wave launcher in ${WAVE_HOME}/bin/" >&2
  echo "       Expected run-server.sh or wave binary with execute permission." >&2
  exit 1
fi
