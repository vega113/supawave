/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Servlet that fetches URL metadata (Open Graph, title, description, image)
 * for generating link preview cards in the Wave client.
 *
 * <p>GET /url-preview?url=&lt;encoded-url&gt; returns JSON:
 * <pre>{"title":"...","description":"...","imageUrl":"...","siteName":"..."}</pre>
 */
@SuppressWarnings("serial")
@Singleton
public class UrlPreviewServlet extends HttpServlet {

  public static final String URL_PREVIEW_URL = "/url-preview";

  private static final Log LOG = Log.get(UrlPreviewServlet.class);

  private static final int FETCH_TIMEOUT_MS = 5000;
  private static final int MAX_BODY_BYTES = 512 * 1024; // 512 KB max HTML to read
  private static final int CACHE_MAX_SIZE = 500;
  private static final int MAX_REDIRECTS = 5;

  private static final Pattern OG_TITLE = Pattern.compile(
      "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_TITLE_ALT = Pattern.compile(
      "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:title[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_DESC = Pattern.compile(
      "<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_DESC_ALT = Pattern.compile(
      "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:description[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_IMAGE = Pattern.compile(
      "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_IMAGE_ALT = Pattern.compile(
      "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_SITE = Pattern.compile(
      "<meta[^>]+property=[\"']og:site_name[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern OG_SITE_ALT = Pattern.compile(
      "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:site_name[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern HTML_TITLE = Pattern.compile(
      "<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
  private static final Pattern META_DESC = Pattern.compile(
      "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern META_DESC_ALT = Pattern.compile(
      "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+name=[\"']description[\"']", Pattern.CASE_INSENSITIVE);

  private final SessionManager sessionManager;

  /** Simple in-memory cache: url -> json result string. */
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  private static final class CacheEntry {
    final String json;
    final long timestamp;
    CacheEntry(String json) {
      this.json = json;
      this.timestamp = System.currentTimeMillis();
    }
    boolean isExpired() {
      return System.currentTimeMillis() - timestamp > 3600_000; // 1 hour
    }
  }

  @Inject
  public UrlPreviewServlet(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Require authentication
    WebSession ws = WebSessions.from(request, false);
    ParticipantId user = sessionManager.getLoggedInUser(ws);
    if (user == null) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String targetUrl = request.getParameter("url");
    if (targetUrl == null || targetUrl.isEmpty()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'url' parameter");
      return;
    }

    if (targetUrl.length() > 2048) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "URL too long");
      return;
    }
    try {
      validateUrlForPreview(URI.create(targetUrl).toURL());
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or disallowed URL");
      return;
    }

    // Check cache
    CacheEntry cached = cache.get(targetUrl);
    if (cached != null && !cached.isExpired()) {
      writeJsonResponse(response, cached.json);
      return;
    }

    // Fetch and parse
    try {
      String html = fetchUrl(targetUrl);
      String json = parseMetadata(html, targetUrl);

      // Cache the result (with size limit)
      if (cache.size() < CACHE_MAX_SIZE) {
        cache.put(targetUrl, new CacheEntry(json));
      }

      writeJsonResponse(response, json);
    } catch (Exception e) {
      LOG.warning("Failed to fetch URL preview for: " + maskUrl(targetUrl), e);
      // Return empty metadata rather than an error
      writeJsonResponse(response, "{\"title\":\"\",\"description\":\"\",\"imageUrl\":\"\",\"siteName\":\"\"}");
    }
  }

  private void writeJsonResponse(HttpServletResponse response, String json) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json; charset=utf-8");
    response.setHeader("Cache-Control", "public, max-age=3600");
    try (var w = response.getWriter()) {
      w.write(json);
      w.flush();
    }
  }

  private static String fetchUrl(String targetUrl) throws IOException {
    URL currentUrl = URI.create(targetUrl).toURL();
    int redirectCount = 0;
    // Use <= so that we follow up to MAX_REDIRECTS redirect hops and then request
    // the terminal URL.  With <, a chain of exactly MAX_REDIRECTS redirects would
    // never have its destination fetched (the loop exits after the last hop is
    // recorded, before the destination is requested).
    while (redirectCount <= MAX_REDIRECTS) {
      validateUrlForPreview(currentUrl);
      HttpURLConnection conn = openSsrfSafeConnection(currentUrl);
      try {
        int status = conn.getResponseCode();
        if (isRedirectStatus(status)) {
          String location = conn.getHeaderField("Location");
          if (location == null || location.isBlank()) {
            throw new IOException("Redirect without location for " + currentUrl);
          }
          currentUrl = currentUrl.toURI().resolve(location).toURL();
          redirectCount += 1;
        } else if (status == HttpURLConnection.HTTP_OK) {
          try (InputStream is = conn.getInputStream();
               BufferedReader reader = new BufferedReader(
                   new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            int total = 0;
            while ((read = reader.read(buf)) != -1 && total < MAX_BODY_BYTES) {
              int toAppend = Math.min(read, MAX_BODY_BYTES - total);
              sb.append(buf, 0, toAppend);
              total += toAppend;
            }
            return sb.toString();
          }
        } else {
          throw new IOException("HTTP " + status + " for " + currentUrl);
        }
      } catch (Exception e) {
        if (e instanceof IOException) {
          throw (IOException) e;
        }
        throw new IOException("Failed to fetch " + currentUrl, e);
      } finally {
        conn.disconnect();
      }
    }
    throw new IOException("Too many redirects for " + targetUrl);
  }

  /**
   * Opens an HTTP(S) connection to the given URL with SSRF protection at the socket layer.
   *
   * <p>We resolve DNS ourselves, validate every resolved IP against {@link #isBlockedAddress},
   * then force the connection to use that validated IP. This eliminates the DNS rebinding
   * TOCTOU gap where {@code HttpURLConnection} could re-resolve the hostname to a different
   * (malicious) IP after our validation check.
   *
   * <p>For HTTP: we rewrite the URL to use the validated IP and set the Host header explicitly.
   * For HTTPS: we additionally install an {@link SSLSocketFactory} that performs TLS handshake
   * with the original hostname for proper SNI and certificate verification, and set a
   * {@link javax.net.ssl.HostnameVerifier} that checks the original hostname.
   */
  private static HttpURLConnection openSsrfSafeConnection(URL url) throws IOException {
    String originalHost = url.getHost();
    int port = url.getPort();
    boolean isHttps = "https".equalsIgnoreCase(url.getProtocol());
    int effectivePort = port != -1 ? port : (isHttps ? 443 : 80);

    // Resolve and validate: pick the first non-blocked address
    InetAddress validatedAddress = resolveAndValidate(originalHost);

    // Rewrite URL to use the validated IP directly, preventing the JVM from re-resolving DNS
    String ipLiteral = validatedAddress.getHostAddress();
    if (validatedAddress instanceof Inet6Address) {
      ipLiteral = "[" + ipLiteral + "]";
    }
    String rewrittenSpec = url.getProtocol() + "://" + ipLiteral
        + (port != -1 ? ":" + port : "")
        + (url.getPath() != null ? url.getPath() : "")
        + (url.getQuery() != null ? "?" + url.getQuery() : "");
    URL ipUrl = URI.create(rewrittenSpec).toURL();

    HttpURLConnection conn = (HttpURLConnection) ipUrl.openConnection();

    if (isHttps && conn instanceof HttpsURLConnection) {
      HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
      httpsConn.setSSLSocketFactory(
          new SsrfSafeSSLSocketFactory(originalHost, effectivePort, validatedAddress));
      // Certificate hostname verification is enforced at the TLS layer via
      // SSLParameters.setEndpointIdentificationAlgorithm("HTTPS") in SsrfSafeSSLSocketFactory.
      // This performs RFC 2818 hostname verification during handshake against the original hostname.
      // The HostnameVerifier returns true because the IP-rewritten URL hostname won't match
      // the cert, but the SSLSocket-level endpoint identification already verified it.
      httpsConn.setHostnameVerifier((hostname, session) -> true);
    }

    conn.setRequestMethod("GET");
    conn.setConnectTimeout(FETCH_TIMEOUT_MS);
    conn.setReadTimeout(FETCH_TIMEOUT_MS);
    conn.setInstanceFollowRedirects(false);
    // Attempt to set the Host header to the original hostname.
    // Note: HttpURLConnection treats "Host" as a restricted header and may not honor this
    // in standard JVM settings. For HTTPS, SNI provides the primary routing mechanism.
    // For HTTP, multi-tenant hosts sharing an IP may fail to route correctly; this is an
    // acceptable trade-off for IP-pinning SSRF protection.
    // Use scheme-aware default port logic: 80 for HTTP, 443 for HTTPS.
    int defaultPort = isHttps ? 443 : 80;
    String hostHeader = port != -1 && port != defaultPort
        ? originalHost + ":" + port
        : originalHost;
    try {
      conn.setRequestProperty("Host", hostHeader);
    } catch (IllegalArgumentException e) {
      // Host header is restricted in this JVM; log and continue.
      // HTTPS will route via SNI; HTTP routing may fail for some multi-tenant hosts.
      LOG.warning("Could not set Host header (restricted by JVM): " + e.getMessage());
    }
    conn.setRequestProperty("User-Agent", "WaveBot/1.0 (URL Preview)");
    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
    return conn;
  }

  /**
   * Resolves the hostname and returns the first non-blocked address.
   * Throws if all resolved addresses are blocked or the host is unresolvable.
   */
  private static InetAddress resolveAndValidate(String host) throws IOException {
    InetAddress[] addresses = InetAddress.getAllByName(host);
    if (addresses.length == 0) {
      throw new IOException("Unresolvable host: " + host);
    }
    for (InetAddress addr : addresses) {
      if (!isBlockedAddress(addr)) {
        return addr;
      }
    }
    throw new IOException("All resolved addresses for host are blocked: " + host);
  }

  /**
   * An {@link SSLSocketFactory} that connects to a pre-validated IP address while performing
   * TLS with the original hostname for proper SNI and certificate verification.
   *
   * <p>This closes the DNS rebinding TOCTOU gap for HTTPS connections: the socket connects
   * to the IP we already validated, so a second DNS lookup cannot redirect to an internal host.
   */
  private static final class SsrfSafeSSLSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    private final String originalHost;
    private final int port;
    private final InetAddress validatedAddress;

    SsrfSafeSSLSocketFactory(String originalHost, int port, InetAddress validatedAddress)
        throws IOException {
      // Use platform default SSL socket factory to allow TLS version negotiation
      // (TLSv1.2, TLSv1.3, etc.) with the server, rather than forcing TLSv1.3.
      this.delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
      this.originalHost = originalHost;
      this.port = port;
      this.validatedAddress = validatedAddress;
    }

    /**
     * Intercepts socket creation by host/port. Instead of letting the JVM resolve the hostname,
     * we connect a plain socket to the validated IP and then layer TLS on top with the original
     * hostname for SNI.
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException {
      return createValidatedSslSocket();
    }

    /**
     * Creates an SSL socket with local address binding.
     * Uses the pre-validated IP, ignoring the host parameter.
     */
    @Override
    public Socket createSocket(String host, int port,
        InetAddress localHost, int localPort) throws IOException {
      return createValidatedSslSocket();
    }

    /**
     * Creates an SSL socket from an InetAddress. Uses the pre-validated IP.
     */
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return createValidatedSslSocket();
    }

    /**
     * Creates an SSL socket from an InetAddress with local address binding.
     * Uses the pre-validated IP, ignoring the host parameter.
     */
    @Override
    public Socket createSocket(InetAddress host, int port,
        InetAddress localHost, int localPort) throws IOException {
      return createValidatedSslSocket();
    }

    /**
     * Layers TLS over an existing socket using the original hostname for SNI.
     * The socket is already connected to the validated IP, so we only layer TLS.
     * Defensive check: verify socket is connected to the validated IP.
     */
    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
        throws IOException {
      // Defensive check: if the socket is already connected, verify it's to our validated IP.
      // This prevents bypass of IP-pinning if HttpsURLConnection were to pass a socket
      // connected to a different (malicious) address.
      if (s.isConnected()) {
        InetAddress remoteAddr = s.getInetAddress();
        if (remoteAddr != null && !remoteAddr.equals(validatedAddress)) {
          throw new IOException("Socket connected to unexpected address: " + remoteAddr +
              " (expected " + validatedAddress + ")");
        }
      }
      // Layer TLS over an existing socket, using original hostname for SNI
      SSLSocket sslSocket = (SSLSocket) delegate.createSocket(s, originalHost, port, autoClose);
      SSLParameters params = sslSocket.getSSLParameters();
      // Enforce RFC 2818 hostname verification at TLS level
      params.setEndpointIdentificationAlgorithm("HTTPS");
      // Set SNI only if originalHost is not a numeric IP literal
      if (!isNumericIpLiteral(originalHost)) {
        params.setServerNames(List.of(new SNIHostName(originalHost)));
      }
      sslSocket.setSSLParameters(params);
      return sslSocket;
    }

    private SSLSocket createValidatedSslSocket() throws IOException {
      // Connect a plain TCP socket to the validated IP
      Socket rawSocket = new Socket();
      try {
        rawSocket.connect(new InetSocketAddress(validatedAddress, port), FETCH_TIMEOUT_MS);
        // Layer TLS on top with original hostname for SNI and certificate verification
        SSLSocket sslSocket = (SSLSocket) delegate.createSocket(
            rawSocket, originalHost, port, true);
        SSLParameters params = sslSocket.getSSLParameters();
        // Enforce RFC 2818 hostname verification at TLS level
        params.setEndpointIdentificationAlgorithm("HTTPS");
        // Set SNI only if originalHost is not a numeric IP literal
        if (!isNumericIpLiteral(originalHost)) {
          params.setServerNames(
              List.of(new SNIHostName(originalHost)));
        }
        sslSocket.setSSLParameters(params);
        return sslSocket;
      } catch (Exception e) {
        // Catch Exception (not just IOException) to also handle IllegalArgumentException
        // from SNIHostName for edge-case hostnames, preventing rawSocket leaks.
        try {
          rawSocket.close();
        } catch (IOException closeEx) {
          e.addSuppressed(closeEx);
        }
        if (e instanceof IOException) {
          throw (IOException) e;
        }
        throw new IOException("SSL socket creation failed", e);
      }
    }

    /**
     * Returns the default cipher suites from the underlying delegate factory.
     */
    @Override
    public String[] getDefaultCipherSuites() {
      return delegate.getDefaultCipherSuites();
    }

    /**
     * Returns the supported cipher suites from the underlying delegate factory.
     */
    @Override
    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }

    /**
     * Detects if the given string is a numeric IP literal (IPv4 or IPv6).
     * Returns true for "192.168.1.1", "[::1]", etc.
     */
    private static boolean isNumericIpLiteral(String host) {
      if (host == null || host.isEmpty()) {
        return false;
      }
      // URL.getHost() returns bracketed IPv6 like "[2001:db8::1]" — strip brackets for parsing
      String stripped = host;
      if (host.startsWith("[") && host.endsWith("]")) {
        stripped = host.substring(1, host.length() - 1);
        // Bracketed hosts are always IPv6 literals
        return true;
      }
      // Check for IPv4 literal: all digits and dots
      if (stripped.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
        return true;
      }
      // Check for un-bracketed IPv6 literal (contains colons)
      if (stripped.contains(":")) {
        return true;
      }
      return false;
    }
  }

  private static boolean isRedirectStatus(int status) {
    return status == HttpURLConnection.HTTP_MOVED_PERM
        || status == HttpURLConnection.HTTP_MOVED_TEMP
        || status == HttpURLConnection.HTTP_SEE_OTHER
        || status == 307
        || status == 308;
  }

  private static void validateUrlForPreview(URL url) throws IOException {
    String protocol = url.getProtocol();
    if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
      throw new MalformedURLException("Unsupported protocol");
    }
    if (url.getUserInfo() != null) {
      throw new MalformedURLException("User info is not allowed");
    }
    String host = url.getHost();
    if (host == null || host.isBlank()) {
      throw new MalformedURLException("Host is required");
    }
    if (isBlockedHostName(host)) {
      throw new MalformedURLException("Blocked host");
    }
    // Note: The authoritative SSRF protection is enforced at connect time by
    // openSsrfSafeConnection(), which resolves DNS once, validates the IP,
    // and pins the connection to that validated address, closing the DNS rebinding
    // TOCTOU gap (see GitHub issue #511). Higher-level hostname validation here
    // provides fail-fast defense-in-depth, but the connection-layer pinning is the
    // actual protection.
  }

  private static boolean isBlockedHostName(String host) {
    String normalized = host.toLowerCase();
    return "localhost".equals(normalized)
        || normalized.endsWith(".localhost")
        || "metadata".equals(normalized)
        || "metadata.google.internal".equals(normalized);
  }

  private static boolean isBlockedAddress(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    if (address instanceof Inet4Address) {
      byte[] bytes = ((Inet4Address) address).getAddress();
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      boolean isCarrierGradeNat = first == 100 && second >= 64 && second <= 127;
      boolean isBenchmarkingRange = first == 198 && (second == 18 || second == 19);
      return isCarrierGradeNat || isBenchmarkingRange;
    }
    if (address instanceof Inet6Address) {
      byte[] bytes = ((Inet6Address) address).getAddress();
      int first = Byte.toUnsignedInt(bytes[0]);
      return (first & 0xFE) == 0xFC;
    }
    return false;
  }

  private static String parseMetadata(String html, String targetUrl) {
    String title = extractFirst(html, OG_TITLE, OG_TITLE_ALT);
    if (title.isEmpty()) {
      title = extractFirst(html, HTML_TITLE);
    }
    String description = extractFirst(html, OG_DESC, OG_DESC_ALT);
    if (description.isEmpty()) {
      description = extractFirst(html, META_DESC, META_DESC_ALT);
    }
    String imageUrl = extractFirst(html, OG_IMAGE, OG_IMAGE_ALT);
    String siteName = extractFirst(html, OG_SITE, OG_SITE_ALT);

    return "{\"title\":" + jsonString(title)
        + ",\"description\":" + jsonString(description)
        + ",\"imageUrl\":" + jsonString(imageUrl)
        + ",\"siteName\":" + jsonString(siteName) + "}";
  }

  @SafeVarargs
  private static String extractFirst(String html, Pattern... patterns) {
    for (Pattern p : patterns) {
      Matcher m = p.matcher(html);
      if (m.find()) {
        return m.group(1).trim();
      }
    }
    return "";
  }

  /** Produces a JSON-safe quoted string. */
  private static String jsonString(String s) {
    if (s == null || s.isEmpty()) return "\"\"";
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private static String maskUrl(String url) {
    if (url == null) return "null";
    if (url.length() <= 20) return "***";
    return url.substring(0, 15) + "***";
  }
}
