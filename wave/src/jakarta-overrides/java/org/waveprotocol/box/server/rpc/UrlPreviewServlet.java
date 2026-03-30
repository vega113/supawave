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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    while (redirectCount <= MAX_REDIRECTS) {
      validateUrlForPreview(currentUrl);
      HttpURLConnection conn = openPreviewConnection(currentUrl);
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
              sb.append(buf, 0, read);
              total += read;
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

  private static HttpURLConnection openPreviewConnection(URL url) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(FETCH_TIMEOUT_MS);
    conn.setReadTimeout(FETCH_TIMEOUT_MS);
    conn.setInstanceFollowRedirects(false);
    conn.setRequestProperty("User-Agent", "WaveBot/1.0 (URL Preview)");
    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
    return conn;
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
    InetAddress[] resolvedAddresses = InetAddress.getAllByName(host);
    if (resolvedAddresses.length == 0) {
      throw new MalformedURLException("Unresolvable host");
    }
    for (InetAddress address : resolvedAddresses) {
      if (isBlockedAddress(address)) {
        throw new MalformedURLException("Blocked address");
      }
    }
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
