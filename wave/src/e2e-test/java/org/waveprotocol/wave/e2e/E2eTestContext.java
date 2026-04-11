package org.waveprotocol.wave.e2e;

import com.google.gson.JsonObject;
import java.util.UUID;

/**
 * Shared static state for the ordered E2E test suite.
 * All fields are mutated in test order by WaveE2eTest.
 * RUN_ID is a short unique suffix preventing cross-run collisions.
 */
class E2eTestContext {

    /** 8-char hex suffix unique per JVM run — appended to usernames and wave local IDs. */
    static final String RUN_ID =
            UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    static String aliceJsessionid;
    static String bobJsessionid;
    static String aliceJwt;
    static String bobJwt;

    /** Wave ID in "domain!wave_local_id" format (e.g. local.net!w+e2eabc12345). */
    static String waveId;
    /** Modern wave ID in "domain/wave_local_id" format (e.g. local.net/w+e2eabc12345). */
    static String modernWaveId;
    /** Wavelet name: "domain/wave_local_id/~/wavelet_local_id". */
    static String waveletName;
    /** Channel ID returned during ProtocolOpenRequest drain phase. */
    static String channelId;
    /**
     * Last hashed_version_after_application from ProtocolSubmitResponse (field "3").
     * Stored as JsonObject {"1": version, "2": historyHash} to avoid Double coercion.
     */
    static JsonObject lastVersion;

    static String blipId;
    static String replyBlipId;
    static String contentToken;
    static String freshTag;

    static WaveWebSocketClient aliceWs;
    static WaveWebSocketClient bobWs;
}
