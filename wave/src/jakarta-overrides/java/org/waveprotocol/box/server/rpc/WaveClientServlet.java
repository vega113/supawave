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
    this.featureFlagService = featureFlagService;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    WebSession session = WebSessions.from(request, false);
    ParticipantId id = sessionManager.getLoggedInUser(session);
    String requestedView = resolveRequestedView(request);
    boolean j2clRootBootstrapEnabled =
        featureFlagService.isEnabled("j2cl-root-bootstrap", id != null ? id.getAddress() : null);

    if (VIEW_LANDING.equals(requestedView)) {
      response.setContentType("text/html");
      response.setCharacterEncoding("UTF-8");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().write(HtmlRenderer.renderLandingPage(domain, analyticsAccount));
      return;
    }

    if (VIEW_J2CL_ROOT.equals(requestedView)
        || (StringUtils.isEmpty(requestedView) && j2clRootBootstrapEnabled)) {
      String rootShellReturnTarget = buildJ2clRootShellReturnTarget(request);
      response.setContentType("text/html");
      response.setCharacterEncoding("UTF-8");
      response.setStatus(HttpServletResponse.SC_OK);
      try (var w = response.getWriter()) {
        // HtmlRenderer normalizes the route target to a same-origin path and escapes it with
        // StringEscapeUtils.escapeHtml4 before threading it into the shell HTML.
        w.write(HtmlRenderer.renderJ2clRootShellPage( // codeql[java/xss]
            getSessionJson(session),
            analyticsAccount,
            buildCommit,
            serverBuildTime,
            currentReleaseId,
            rootShellReturnTarget,
            resolveWebsocketAddressForPage(request)));
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
          response.sendRedirect(UrlParameters.addParameter( // codeql[java/url-redirection]
              buildLocalRedirectTarget(request), "locale", locale));
          return;
        }
      }
    }

    response.setContentType("text/html");
    response.setCharacterEncoding("UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);
    try (var w = response.getWriter()) {
      String topBarHtml = "";
      if (id != null) {
        String username = id.getAddress().split("@")[0];
        String userDomain = id.getDomain();
        String userRole = HumanAccountData.ROLE_USER;
        try {
          AccountData acctData = accountStore.getAccount(id);
          if (acctData != null && acctData.isHuman()) {
            userRole = acctData.asHuman().getRole();
          }
        } catch (Exception e) {
          LOG.warning("Failed to look up role for " + id.getAddress(), e);
        }
        topBarHtml = HtmlRenderer.renderTopBar(username, userDomain, userRole);
      }

      // Keep the legacy rollback path on the existing skeleton load until the
      // server-side pre-rendered fragment has an explicit sanitization boundary.
      w.write(HtmlRenderer.renderWaveClientPage( // codeql[java/xss]
          getSessionJson(session),
          getClientFlags(request),
          resolveWebsocketAddressForPage(request),
          topBarHtml,
          analyticsAccount,
          buildCommit,
          serverBuildTime,
          currentReleaseId,
          null));
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

  private String resolveWebsocketAddressForPage(HttpServletRequest request) {
    return websocketPresentedAddress;
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
