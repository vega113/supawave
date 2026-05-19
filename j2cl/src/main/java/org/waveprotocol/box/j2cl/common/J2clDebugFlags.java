package org.waveprotocol.box.j2cl.common;

/**
 * Build-time flags controlling debug-only behavior in the J2CL client.
 *
 * <p>DEBUG_OVERLAY_ENABLED is read from the JVM system property
 * {@code j2cl.debug.overlay.enabled} at class-initialization time.
 * It is <em>not</em> a Java compile-time constant expression (JLS §15.29),
 * so the Closure compiler cannot perform dead-code elimination on branches
 * guarded by this field in ADVANCED_OPTIMIZATIONS mode.
 *
 * <p>For the production Maven profile the property is set to {@code false}
 * via {@code <properties>} so guarded code is skipped at runtime. A future
 * improvement could replace this with a build-generated class that assigns
 * a literal {@code false} so that Closure can DCE the guarded branches.
 *
 * <p>In dev and sandbox builds the property defaults to {@code true},
 * allowing the runtime "j2cl-debug-overlay" feature flag to work.
 */
public final class J2clDebugFlags {

    /**
     * When false, calls to mark elements as debug-only become no-ops.
     * Set via the Maven property {@code j2cl.debug.overlay.enabled}
     * (e.g. {@code -Dj2cl.debug.overlay.enabled=false}). Defaults to
     * {@code true} so dev builds retain the debug overlay.
     *
     * <p>Because the initializer is a method call this field is NOT a
     * compile-time constant; Closure ADVANCED_OPTIMIZATIONS cannot DCE
     * branches guarded by it — visibility is suppressed at runtime instead.
     */
    public static final boolean DEBUG_OVERLAY_ENABLED =
            Boolean.parseBoolean(System.getProperty("j2cl.debug.overlay.enabled", "true"));
}
