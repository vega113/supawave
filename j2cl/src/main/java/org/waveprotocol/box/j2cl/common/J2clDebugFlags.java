package org.waveprotocol.box.j2cl.common;

/**
 * Build-time flags controlling debug-only behavior in the J2CL client.
 *
 * The value of DEBUG_OVERLAY_ENABLED is a compile-time constant.
 * In production builds (j2clProductionBuild profile) it is forced to false
 * so that the Closure compiler (ADVANCED_OPTIMIZATIONS) can eliminate all
 * debug tagging and the associated strings/attributes.
 *
 * In dev and sandbox builds it remains true, allowing the existing runtime
 * "j2cl-debug-overlay" feature flag (controlled server-side) to continue working.
 */
public final class J2clDebugFlags {

    /**
     * When false, calls to mark elements as debug-only become no-ops and
     * the compiler can dead-code-eliminate the tagging logic.
     *
     * The value can be forced at build time by passing
     * -Dj2cl.debug.overlay.enabled=false on the Maven command line
     * for the production profile. This makes the constant false at
     * compile time for ADVANCED_OPTIMIZATIONS, enabling DCE.
     */
    public static final boolean DEBUG_OVERLAY_ENABLED =
            Boolean.parseBoolean(System.getProperty("j2cl.debug.overlay.enabled", "true"));
}
