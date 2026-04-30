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

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.waveprotocol.box.common.SessionConstants;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.box.server.util.RandomBase64Generator;
import org.waveprotocol.box.server.util.UrlParameters;
import org.waveprotocol.wave.common.bootstrap.FlagConstants;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

@SuppressWarnings("serial")
@Singleton
public class WaveClientServlet extends HttpServlet {
  private static final Log LOG = Log.get(WaveClientServlet.class);
  private static final HashMap<String, String> FLAG_MAP = Maps.newHashMap();
  private static final String VIEW_LANDING = "landing";
  private static final String VIEW_J2CL_ROOT = "j2cl-root";

  static {
    for (int i = 0; i < FlagConstants.__NAME_MAPPING__.length; i += 2) {
      FLAG_MAP.put(FlagConstants.__NAME_MAPPING__[i], FlagConstants.__NAME_MAPPING__[i + 1]);
    }
  }

  private static volatile boolean loggedClientFlagsOnce = false;

  private final String domain;
  private final String analyticsAccount;
  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final boolean hasExplicitWebsocketPresentedAddress;
  private final String websocketPresentedAddress;
  private final Config config;
  private final String buildCommit;
  private final long serverBuildTime;
  private final String currentReleaseId;
  private final boolean prerenderingEnabled;
  private final WavePreRenderer wavePreRenderer;
  private final J2clSelectedWaveSnapshotRenderer j2clSelectedWaveSnapshotRenderer;
  private final FeatureFlagService featureFlagService;

  static boolean supportsMentionSearch(Config config) {
    return !"solr".equals(config.getString("core.search_type"));
  }

  @Inject
  public WaveClientServlet(
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      Config config,
      SessionManager sessionManager,
      AccountStore accountStore,
      VersionServlet versionServlet,
      WavePreRenderer wavePreRenderer,
      J2clSelectedWaveSnapshotRenderer j2clSelectedWaveSnapshotRenderer,
      FeatureFlagService featureFlagService) {
    List<String> httpAddresses = config.getStringList("core.http_frontend_addresses");
    String websocketAddress = config.getString("core.http_websocket_public_address");
    String configuredWebsocketPresentedAddress =
        config.getString("core.http_websocket_presented_address");
    this.domain = domain;
    String websocketAddress1 =
        StringUtils.isEmpty(websocketAddress) ? httpAddresses.get(0) : websocketAddress;
    this.hasExplicitWebsocketPresentedAddress =
        StringUtils.isNotEmpty(configuredWebsocketPresentedAddress);
    this.websocketPresentedAddress =
        hasExplicitWebsocketPresentedAddress
            ? configuredWebsocketPresentedAddress
            : websocketAddress1;
    this.analyticsAccount = config.getString("administration.analytics_account");
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.config = config;
    this.buildCommit = versionServlet.getBuildCommit();
    this.serverBuildTime = versionServlet.getBuildTime();
    this.currentReleaseId = versionServlet.getCurrentReleaseId();
    this.prerenderingEnabled = config.hasPath("core.enable_prerendering")
        && config.getBoolean("core.enable_prerendering");
    this.wavePreRenderer = wavePreRenderer;
    this.j2clSelectedWaveSnapshotRenderer = j2clSelectedWaveSnapshotRenderer;
    this.featureFlagService = featureFlagService;
  }

  WaveClientServlet(
      String domain,
      Config config,
      SessionManager sessionManager,
      AccountStore accountStore,
      VersionServlet versionServlet,
      WavePreRenderer wavePreRenderer,
      FeatureFlagService featureFlagService) {
    this(
        domain,
        config,
        sessionManager,
        accountStore,
        versionServlet,
        wavePreRenderer,
        null,
        featureFlagService);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    WebSession session = WebSessions.from(request, false);
    ParticipantId id = sessionManager.getLoggedInUser(session);
    String requestedView = resolveRequestedView(request);
    boolean j2clRootBootstrapEnabled =
        featureFlagService.isEnabled("j2cl-root-bootstrap", id != null ? id.getAddress() : null);

    String contextRoot = request.getContextPath();
    String requestUri = request.getRequestURI();
    boolean isContextRoot = requestUri != null
        && (requestUri.equals(contextRoot)
            || requestUri.equals(contextRoot + "/")
            || requestUri.equals("/"));
    if (VIEW_LANDING.equals(requestedView)
        || (id == null
            && StringUtils.isEmpty(requestedView)
            && StringUtils.isEmpty(request.getQueryString())
            && isContextRoot)) {
      // Signed-out visitors hitting "/" with no view/query land on the public
      // marketing page rather than an empty Wave shell. Explicit ?view=landing
      // continues to render the landing page for any user.
      response.setContentType("text/html");
      response.setCharacterEncoding("UTF-8");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().write(HtmlRenderer.renderLandingPage(domain, analyticsAccount));
      return;
    }

    if (VIEW_J2CL_ROOT.equals(requestedView)
        || (StringUtils.isEmpty(requestedView) && j2clRootBootstrapEnabled)) {
      // F-2 slice 6 (#1058, Part B): signed-in read-surface preview
      // route reachable at ?view=j2cl-root&q=read-surface-preview. The
      // route is server-only — no WaveletProvider lookup, no live
      // session — so reviewers and design contributors always see the
      // same fixture content. Signed-out viewers fall through to the
      // regular shell so the sign-in flow handles them.
      if ("read-surface-preview".equals(request.getParameter("q")) && id != null) {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Vary", "Cookie");
        response.setStatus(HttpServletResponse.SC_OK);
        try (var w = response.getWriter()) {
          w.write(HtmlRenderer.renderJ2clReadSurfacePreviewPage(
              request.getContextPath(),
              buildCommit,
              serverBuildTime,
              currentReleaseId,
              id.getAddress()));
        } catch (IOException e) {
          LOG.warning("Failed to render J2CL read-surface preview page", e);
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return;
      }
      // F-0 (#1035): admin-or-owner gated design preview sub-branch.
      // Reachable at ?view=j2cl-root&q=design-preview per issue body
      // §Verification. A non-admin requesting q=design-preview falls
      // through to the regular root shell with no error / no leak.
      if ("design-preview".equals(request.getParameter("q")) && id != null) {
        boolean isAdminOrOwner = false;
        try {
          AccountData acct = accountStore.getAccount(id);
          if (acct != null && acct.isHuman()) {
            String role = acct.asHuman().getRole();
            isAdminOrOwner =
                HumanAccountData.ROLE_ADMIN.equals(role)
                    || HumanAccountData.ROLE_OWNER.equals(role);
          }
        } catch (Exception e) {
          LOG.warning("Failed to look up role for design-preview gate " + id.getAddress(), e);
        }
        if (isAdminOrOwner) {
          response.setContentType("text/html");
          response.setCharacterEncoding("UTF-8");
          response.setHeader("Cache-Control", "private, no-store");
          response.setHeader("Vary", "Cookie");
          response.setStatus(HttpServletResponse.SC_OK);
          try (var w = response.getWriter()) {
            w.write(HtmlRenderer.renderJ2clDesignPreviewPage(
                request.getContextPath(),
                buildCommit,
                serverBuildTime,
                currentReleaseId)); // codeql[java/xss]
          } catch (IOException e) {
            LOG.warning("Failed to render J2CL design preview page", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          }
          return;
        }
        // Fall through: non-admin reaches the regular root shell.
      }
      String rootShellReturnTarget = buildJ2clRootShellReturnTarget(request);
      J2clSelectedWaveSnapshotRenderer.SnapshotResult snapshotResult =
          j2clSelectedWaveSnapshotRenderer == null
              ? J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave()
              : j2clSelectedWaveSnapshotRenderer.renderRequestedWave(request.getParameter("wave"), id);
      // J-UI-1 (#1079): per-viewer flag gating for the new rail-card path.
      boolean railCardsEnabled =
          featureFlagService.isEnabled(
              "j2cl-search-rail-cards", id != null ? id.getAddress() : null);
      // J-UI-8 (#1086): per-viewer locale lookup so <html lang> reflects
      // the user preference (R-6.1). The matrix only requires the AT
      // signal — full SSR string localization is out of scope and tracked
      // separately. Account lookup failures fall through to "en" via the
      // sanitiser in HtmlRenderer.
      String viewerLocale = null;
      if (id != null) {
        try {
          AccountData acct = accountStore.getAccount(id);
          if (acct != null && acct.isHuman()) {
            viewerLocale = acct.asHuman().getLocale();
          }
        } catch (Exception e) {
          LOG.warning("Failed to look up locale for j2cl-root SSR " + id.getAddress(), e);
        }
      }
      // J-UI-8 (#1086): per-viewer flag gating for the noscript banner.
      boolean serverFirstPaintEnabled =
          featureFlagService.isEnabled(
              "j2cl-server-first-paint", id != null ? id.getAddress() : null);
      // J-UI-5 (#1083): per-viewer flag gating for the inline rich-text
      // composer + selection-driven format toolbar.
      boolean inlineRichComposerEnabled =
          featureFlagService.isEnabled(
              "j2cl-inline-rich-composer", id != null ? id.getAddress() : null);
      // V-2 (#1100): per-viewer flag gating for the developer-strings
      // overlay. When off, the body omits the j2cl-debug-overlay-on
      // class so sidecar.css hides eyebrow/status/detail and Lit
      // composers render without the reply-target paragraph.
      boolean debugOverlayEnabled =
          featureFlagService.isEnabled(
              "j2cl-debug-overlay", id != null ? id.getAddress() : null);
      response.setContentType("text/html");
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Cache-Control", "private, no-store");
      response.setHeader("Vary", "Cookie");
      response.setStatus(HttpServletResponse.SC_OK);
      try (var w = response.getWriter()) {
        // HtmlRenderer normalizes the route target to a same-origin path and escapes it with
        // StringEscapeUtils.escapeHtml4 before threading it into the shell HTML.
        w.write(HtmlRenderer.renderJ2clRootShellPage(
            getSessionJson(session),
            analyticsAccount,
            buildCommit,
            serverBuildTime,
            currentReleaseId,
            rootShellReturnTarget,
            snapshotResult,
            railCardsEnabled,
            viewerLocale,
            serverFirstPaintEnabled,
            inlineRichComposerEnabled,
            debugOverlayEnabled)); // codeql[java/xss]
      } catch (IOException e) {
        LOG.warning("Failed to render J2CL root shell page", e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
      return;
    }

    AccountData account = id != null ? sessionManager.getLoggedInAccount(session) : null;
    if (account != null && account.isHuman()) {
      String locale = account.asHuman().getLocale();
      if (locale != null) {
        String requestLocale = UrlParameters.getParameters(request.getQueryString()).get("locale");
        if (requestLocale == null) {
          response.sendRedirect(UrlParameters.addParameter(
              buildLocalRedirectTarget(request), "locale", locale)); // codeql[java/unvalidated-url-redirection]
          return;
        }
      }
    }

    response.setContentType("text/html");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "private, no-store");
    response.setHeader("Vary", "Cookie");
    response.setStatus(HttpServletResponse.SC_OK);
    try (var w = response.getWriter()) {
      String username = null;
      String userDomain = null;
      String userRole = null;
      if (id != null) {
        username = id.getAddress().split("@")[0];
        userDomain = id.getDomain();
        userRole = HumanAccountData.ROLE_USER;
        try {
          AccountData acctData = accountStore.getAccount(id);
          if (acctData != null && acctData.isHuman()) {
            userRole = acctData.asHuman().getRole();
          }
        } catch (Exception e) {
          LOG.warning("Failed to look up role for " + id.getAddress(), e);
        }
      }
      // renderTopBar accepts a null username and emits the signed-out auth shell.
      String topBarHtml = HtmlRenderer.renderTopBar(username, userDomain, userRole);

      // Keep the legacy rollback path on the existing skeleton load until the
      // server-side pre-rendered fragment has an explicit sanitization boundary.
      w.write(HtmlRenderer.renderWaveClientPage(
          getSessionJson(session),
          getClientFlags(request),
          resolveWebsocketAddressForPage(request, false),
          topBarHtml,
          analyticsAccount,
          buildCommit,
          serverBuildTime,
          currentReleaseId,
          null)); // codeql[java/xss]
    } catch (IOException e) {
      LOG.warning("Failed to render WaveClient page", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  JSONObject getClientFlags(HttpServletRequest request) {
    JSONObject ret = new JSONObject();

    Enumeration<?> iter = request.getParameterNames();
    while (iter.hasMoreElements()) {
      String name = (String) iter.nextElement();
      String value = request.getParameter(name);
      applyRequestFlagValue(ret, name, value);
    }

    applySystemPropertyClientFlags(ret);
    applyConfiguredClientFlags(ret);

    if (!loggedClientFlagsOnce) {
      loggedClientFlagsOnce = true;
      try {
        LOG.info("WaveClient flags: " + ret.toString());
      } catch (Throwable ignore) {
      }
    }
    return ret;
  }

  private void applyRequestFlagValue(JSONObject ret, String name, String value) {
    if (!FLAG_MAP.containsKey(name)) {
      return;
    }
    try {
      putInferredValue(ret, name, value);
    } catch (NumberFormatException ex) {
      LOG.warning("Ignoring flag [" + name + "]: " + ex.getClass().getSimpleName());
    } catch (JSONException ex) {
      LOG.warning("Failed to encode flag [" + name + "]");
    }
  }

  private void applySystemPropertyClientFlags(JSONObject ret) {
    try {
      String sys = System.getProperty("wave.clientFlags");
      if (sys == null || sys.trim().isEmpty()) {
        return;
      }
      String[] pairs = sys.split(",");
      for (String pair : pairs) {
        String p = pair.trim();
        if (p.isEmpty()) {
          continue;
        }
        int eq = p.indexOf('=');
        String name = (eq > 0) ? p.substring(0, eq).trim() : p;
        String value = (eq > 0) ? p.substring(eq + 1).trim() : "true";
        applyClientFlagValue(ret, name, value);
      }
    } catch (Exception ignored) {
    }
  }

  private void applyConfiguredClientFlags(JSONObject ret) {
    try {
      if (config == null) {
        return;
      }
      for (String name : FLAG_MAP.keySet()) {
        String path = "client.flags.defaults." + name;
        if (!config.hasPath(path) || ret.has(FLAG_MAP.get(name))) {
          continue;
        }
        try {
          putConfiguredValue(ret, name, path);
        } catch (Exception ignored) {
        }
      }
      applyDerivedFragmentDefaults(ret);
      applyCsvDefaults(ret);
    } catch (Exception ignored) {
    }
  }

  private void applyDerivedFragmentDefaults(JSONObject ret) {
    applyStringFlagDefault(ret, "fragmentFetchMode", "server.fragments.transport");
    applyBooleanFlagDefault(ret, "forceClientFragments", "wave.fragments.forceClientApplier");
  }

  private void applyCsvDefaults(JSONObject ret) {
    if (!config.hasPath("client.flags.defaults")
        || !config.getValue("client.flags.defaults").valueType().name().equals("STRING")) {
      return;
    }
    String defaults = config.getString("client.flags.defaults");
    if (defaults == null || defaults.trim().isEmpty()) {
      return;
    }
    String[] pairs = defaults.split(",");
    for (String pair : pairs) {
      String p = pair.trim();
      if (p.isEmpty()) {
        continue;
      }
      int eq = p.indexOf('=');
      String name = (eq > 0) ? p.substring(0, eq).trim() : p;
      String value = (eq > 0) ? p.substring(eq + 1).trim() : "true";
      applyClientFlagValue(ret, name, value);
    }
  }

  private void applyStringFlagDefault(JSONObject ret, String flagName, String configPath) {
    if (ret.has(FLAG_MAP.get(flagName)) || config == null || !config.hasPath(configPath)) {
      return;
    }
    String value = config.getString(configPath);
    if (value == null || value.trim().isEmpty()) {
      return;
    }
    try {
      ret.put(FLAG_MAP.get(flagName), value.trim().toLowerCase());
    } catch (JSONException ignored) {
    }
  }

  private void applyBooleanFlagDefault(JSONObject ret, String flagName, String configPath) {
    if (ret.has(FLAG_MAP.get(flagName)) || config == null || !config.hasPath(configPath)) {
      return;
    }
    try {
      ret.put(FLAG_MAP.get(flagName), config.getBoolean(configPath));
    } catch (JSONException ignored) {
    }
  }

  private void applyClientFlagValue(JSONObject ret, String name, String value) {
    if (!FLAG_MAP.containsKey(name) || ret.has(FLAG_MAP.get(name))) {
      return;
    }
    try {
      putInferredValue(ret, name, value);
    } catch (Exception ignored) {
    }
  }

  private void putConfiguredValue(JSONObject ret, String name, String path)
      throws JSONException {
    ConfigValue value = config.getValue(path);
    ConfigValueType valueType = value.valueType();
    Object unwrapped = value.unwrapped();

    if (valueType == ConfigValueType.STRING) {
      ret.put(FLAG_MAP.get(name), String.valueOf(unwrapped));
      return;
    }

    if (valueType == ConfigValueType.BOOLEAN) {
      ret.put(FLAG_MAP.get(name), (Boolean) unwrapped);
      return;
    }

    if (valueType == ConfigValueType.NUMBER) {
      ret.put(FLAG_MAP.get(name), unwrapped);
    }
  }

  private void putInferredValue(JSONObject ret, String name, String value)
      throws JSONException {
    if (value == null) {
      ret.put(FLAG_MAP.get(name), JSONObject.NULL);
      return;
    }

    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      ret.put(FLAG_MAP.get(name), trimmed);
      return;
    }

    if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
      ret.put(FLAG_MAP.get(name), Boolean.parseBoolean(trimmed));
      return;
    }

    if (trimmed.matches("-?\\d+")) {
      try {
        ret.put(FLAG_MAP.get(name), Integer.parseInt(trimmed));
      } catch (NumberFormatException ignored) {
        ret.put(FLAG_MAP.get(name), Long.parseLong(trimmed));
      }
      return;
    }

    if (trimmed.matches("-?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][+-]?\\d+)?")) {
      ret.put(FLAG_MAP.get(name), Double.parseDouble(trimmed));
      return;
    }

    ret.put(FLAG_MAP.get(name), trimmed);
  }

  /**
   * Build the {@link SessionConstants}-shaped session payload for the current
   * request. Package-private so that sibling servlets (e.g. {@link
   * J2clBootstrapServlet}) can reuse the exact same role/feature/ID-seed logic
   * rather than duplicating it. The returned object is fresh per call; the
   * {@link SessionConstants#ID_SEED} seed is regenerated on every invocation.
   */
  JSONObject buildSessionJson(WebSession session) {
    return getSessionJson(session);
  }

  /**
   * Return the same presented WebSocket address this servlet would resolve for
   * the J2CL root shell. Exposed for sibling servlets that need to mirror the
   * address into a JSON contract without re-reading config.
   */
  String presentedWebsocketAddress(HttpServletRequest request) {
    return resolveWebsocketAddressForPage(request, true);
  }

  private JSONObject getSessionJson(WebSession session) {
    try {
      ParticipantId user = sessionManager.getLoggedInUser(session);
      String address = (user != null) ? user.getAddress() : null;
      String sessionId = (new RandomBase64Generator()).next(10);

      // Look up the user's role so the client knows if this is an admin
      String userRole = HumanAccountData.ROLE_USER;
      if (user != null) {
        try {
          AccountData acctData = accountStore.getAccount(user);
          if (acctData != null && acctData.isHuman()) {
            userRole = acctData.asHuman().getRole();
          }
        } catch (Exception e) {
          LOG.warning("Failed to look up role for session JSON: " + address, e);
        }
      }

      JSONObject json = new JSONObject()
          .put(SessionConstants.DOMAIN, domain)
          .putOpt(SessionConstants.ADDRESS, address)
          .putOpt(SessionConstants.ID_SEED, sessionId)
          .put(SessionConstants.ROLE, userRole);
      // Add enabled feature flags for this user
      if (address != null) {
        List<String> enabledFlags =
            new ArrayList<>(featureFlagService.getEnabledFlagNames(address));
        if (supportsMentionSearch(config)) {
          if (!enabledFlags.contains("mentions-search")) {
            enabledFlags.add("mentions-search");
          }
        }
        json.put("features", new JSONArray(enabledFlags));
      }
      return json;
    } catch (JSONException e) {
      LOG.severe("Failed to create session JSON");
      return new JSONObject();
    }
  }

  @SuppressWarnings("java/xss")
  private String buildJ2clRootShellReturnTarget(HttpServletRequest request) {
    String requestUri = StringUtils.defaultIfBlank(request.getRequestURI(), "/");
    StringBuilder returnTarget = new StringBuilder(requestUri).append("?view=")
        .append(VIEW_J2CL_ROOT);
    // q and wave only flow into the shell as URL-encoded route components here. HtmlRenderer
    // later normalizes the target to a same-origin path and HTML-escapes it before rendering.
    String query = request.getParameter("q");
    if (query != null && !query.isEmpty()) {
      returnTarget.append("&q=").append(encodeReturnTargetComponent(query)); // codeql[java/xss]
    }
    String wave = request.getParameter("wave");
    if (wave != null && !wave.isEmpty()) {
      returnTarget.append("&wave=").append(encodeReturnTargetComponent(wave)); // codeql[java/xss]
    }
    return returnTarget.toString();
  }

  private static String encodeReturnTargetComponent(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String buildLocalRedirectTarget(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String safeRequestUri =
        requestUri != null && requestUri.startsWith("/") && !requestUri.startsWith("//")
            ? requestUri
            : "/";
    String queryString = request.getQueryString();
    if (queryString == null || queryString.isEmpty()) {
      return safeRequestUri;
    }
    return safeRequestUri + "?" + queryString;
  }

  @SuppressWarnings("java/xss")
  private String resolveWebsocketAddressForPage(
      HttpServletRequest request, boolean servingJ2clRootShell) {
    if (hasExplicitWebsocketPresentedAddress) {
      return websocketPresentedAddress;
    }
    if (!servingJ2clRootShell) {
      return websocketPresentedAddress;
    }
    String host = resolvePresentedHostHeader(request);
    return StringUtils.isBlank(host) ? websocketPresentedAddress : host;
  }

  private String resolvePresentedHostHeader(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String forwardedHost = normalizeSingleHostHeaderValue(request.getHeader("X-Forwarded-Host"));
    if (StringUtils.isNotBlank(forwardedHost) && isTrustedPublicHost(forwardedHost)) {
      return forwardedHost;
    }
    String host = normalizeSingleHostHeaderValue(request.getHeader("Host"));
    if (StringUtils.isNotBlank(host) && isTrustedPublicHost(host)) {
      return host;
    }
    return null;
  }

  private boolean isTrustedPublicHost(String host) {
    if (StringUtils.isBlank(host)) {
      return false;
    }
    // Extract the host label, handling bracketed IPv6 (e.g. [::1]:9898 → [::1]).
    String normalizedHost;
    if (host.startsWith("[")) {
      int end = host.indexOf(']');
      normalizedHost = end >= 0 ? host.substring(0, end + 1) : "";
    } else {
      normalizedHost = StringUtils.substringBefore(host, ":").trim();
    }
    if (normalizedHost.isEmpty()) {
      return false;
    }
    String lowerHost = normalizedHost.toLowerCase();
    String lowerDomain = domain.toLowerCase();
    return lowerHost.equals(lowerDomain)
        || lowerHost.endsWith("." + lowerDomain)
        || lowerHost.equals("localhost")
        || lowerHost.equals("[::1]")
        || normalizedHost.equals("127.0.0.1");
  }

  private static String normalizeSingleHostHeaderValue(String headerValue) {
    if (StringUtils.isBlank(headerValue)) {
      return null;
    }
    String normalized = StringUtils.substringBefore(headerValue, ",").trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.matches("^(?:\\[[0-9A-Fa-f:.]+\\]|[A-Za-z0-9.-]+)(?::\\d{1,5})?$")
        ? normalized
        : null;
  }

  private String resolveRequestedView(HttpServletRequest request) {
    String[] values = request.getParameterValues("view");
    if (values == null || values.length == 0) {
      return request.getParameter("view");
    }
    for (String value : values) {
      if (VIEW_J2CL_ROOT.equals(value)) {
        return VIEW_J2CL_ROOT;
      }
    }
    for (String value : values) {
      if (VIEW_LANDING.equals(value)) {
        return VIEW_LANDING;
      }
    }
    return values[0];
  }
}
