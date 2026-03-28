package org.waveprotocol.box.server.rpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Singleton
@SuppressWarnings("serial")
public final class ApiDocsServlet extends HttpServlet {
  private static final String DOCS_VERSION = "2026-03-27";
  private static final String DEFAULT_BASE_URL = "http://127.0.0.1:9898";
  private static final String API_DOCS_PATH = "/api-docs";
  private static final String OPENAPI_PATH = "/api/openapi.json";
  private static final String LLM_ALIAS_PATH = "/api/llm.txt";
  private static final String LLMS_INDEX_PATH = "/llms.txt";
  private static final String LLMS_FULL_PATH = "/llms-full.txt";
  private static final String CANONICAL_RPC_PATH = "/robot/dataapi/rpc";
  private static final String RPC_ALIAS_PATH = "/robot/dataapi";
  private static final String TOKEN_PATH = "/robot/dataapi/token";
  private static final Gson PRETTY_JSON =
      new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

  private static final List<OperationDoc> OPERATIONS = createOperations();
  private static final List<String> GROUP_ORDER =
      Collections.unmodifiableList(
          Arrays.asList(
              "Protocol and internal",
              "Wave and conversation",
              "Search, profile, and folders",
              "Export and import"));

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String servletPath = request.getServletPath();
    if (servletPath == null || servletPath.isEmpty()) {
      servletPath = request.getRequestURI();
    }

    String baseUrl = deriveBaseUrl(request);

    if (API_DOCS_PATH.equals(servletPath)) {
      writeResponse(response, "text/html;charset=utf-8", renderHtml(baseUrl));
      return;
    }
    if (OPENAPI_PATH.equals(servletPath)) {
      writeResponse(response, "application/json;charset=utf-8", renderOpenApiJson());
      return;
    }
    if (LLM_ALIAS_PATH.equals(servletPath) || LLMS_FULL_PATH.equals(servletPath)) {
      writeResponse(response, "text/plain;charset=utf-8", renderLlmFullText(baseUrl));
      return;
    }
    if (LLMS_INDEX_PATH.equals(servletPath)) {
      writeResponse(response, "text/plain;charset=utf-8", renderLlmIndexText(baseUrl));
      return;
    }

    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  private static String deriveBaseUrl(HttpServletRequest request) {
    String scheme = request.getHeader("X-Forwarded-Proto");
    if (scheme == null || scheme.isEmpty()) {
      scheme = request.getScheme();
    }
    String host = request.getHeader("X-Forwarded-Host");
    if (host != null && host.contains(",")) {
      host = host.substring(0, host.indexOf(',')).trim();
    }
    if (host == null || host.isEmpty()) {
      host = request.getHeader("Host");
    }
    if (host == null || host.isEmpty()) {
      return DEFAULT_BASE_URL;
    }
    return scheme + "://" + host;
  }

  private static void writeResponse(HttpServletResponse response, String contentType, String body)
      throws IOException {
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(contentType);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Pragma", "no-cache");
    response.setStatus(HttpServletResponse.SC_OK);
    try (PrintWriter writer = response.getWriter()) {
      writer.write(body);
    }
  }

  private static String renderHtml(String baseUrl) {
    StringBuilder html = new StringBuilder(128000);
    html.append("<!DOCTYPE html>\n");
    html.append("<html lang=\"en\">\n");
    html.append("<head>\n");
    html.append("  <meta charset=\"UTF-8\">\n");
    html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    html.append("  <title>SupaWave Data API</title>\n");
    html.append("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">\n");
    html.append("  <link rel=\"alternate icon\" href=\"/static/favicon.ico\">\n");
    html.append("  <style>\n");
    html.append("    :root {\n");
    html.append("      --wave-primary: #0077b6;\n");
    html.append("      --wave-accent: #00b4d8;\n");
    html.append("      --wave-light: #90e0ef;\n");
    html.append("      --wave-deep: #023e8a;\n");
    html.append("      --wave-foam: #f0f8fb;\n");
    html.append("      --wave-border: #d9eef4;\n");
    html.append("      --wave-text: #123047;\n");
    html.append("      --wave-muted: #4f7086;\n");
    html.append("      --wave-code: #041b2d;\n");
    html.append("      --wave-card: rgba(255, 255, 255, 0.92);\n");
    html.append("    }\n");
    html.append("    * { box-sizing: border-box; }\n");
    html.append("    body {\n");
    html.append("      margin: 0;\n");
    html.append("      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;\n");
    html.append("      color: var(--wave-text);\n");
    html.append("      background:\n");
    html.append("        radial-gradient(circle at top left, rgba(144, 224, 239, 0.75), transparent 38%),\n");
    html.append("        radial-gradient(circle at top right, rgba(0, 180, 216, 0.28), transparent 22%),\n");
    html.append("        linear-gradient(180deg, #e8f8fc 0%, #f6fbfd 22%, #ffffff 100%);\n");
    html.append("    }\n");
    html.append("    a { color: var(--wave-primary); }\n");
    html.append("    .hero {\n");
    html.append("      background: linear-gradient(135deg, rgba(2, 62, 138, 0.96), rgba(0, 119, 182, 0.95) 52%, rgba(0, 180, 216, 0.92));\n");
    html.append("      color: #fff;\n");
    html.append("      padding: 56px 24px 44px;\n");
    html.append("      position: relative;\n");
    html.append("      overflow: hidden;\n");
    html.append("    }\n");
    html.append("    .hero::after {\n");
    html.append("      content: '';\n");
    html.append("      position: absolute;\n");
    html.append("      inset: auto -120px -140px auto;\n");
    html.append("      width: 320px;\n");
    html.append("      height: 320px;\n");
    html.append("      border-radius: 50%;\n");
    html.append("      background: rgba(255, 255, 255, 0.09);\n");
    html.append("      filter: blur(0.5px);\n");
    html.append("    }\n");
    html.append("    .hero-inner {\n");
    html.append("      max-width: 1180px;\n");
    html.append("      margin: 0 auto;\n");
    html.append("      position: relative;\n");
    html.append("      z-index: 1;\n");
    html.append("    }\n");
    html.append("    .eyebrow {\n");
    html.append("      display: inline-block;\n");
    html.append("      padding: 6px 12px;\n");
    html.append("      border-radius: 999px;\n");
    html.append("      background: rgba(255, 255, 255, 0.14);\n");
    html.append("      letter-spacing: 0.08em;\n");
    html.append("      text-transform: uppercase;\n");
    html.append("      font-size: 12px;\n");
    html.append("      font-weight: 700;\n");
    html.append("    }\n");
    html.append("    h1 {\n");
    html.append("      margin: 18px 0 12px;\n");
    html.append("      font-size: clamp(34px, 5vw, 58px);\n");
    html.append("      line-height: 1.05;\n");
    html.append("      letter-spacing: -0.03em;\n");
    html.append("    }\n");
    html.append("    .hero p {\n");
    html.append("      max-width: 860px;\n");
    html.append("      margin: 0;\n");
    html.append("      font-size: 17px;\n");
    html.append("      line-height: 1.7;\n");
    html.append("      color: rgba(255, 255, 255, 0.9);\n");
    html.append("    }\n");
    html.append("    .hero-links {\n");
    html.append("      display: flex;\n");
    html.append("      flex-wrap: wrap;\n");
    html.append("      gap: 12px;\n");
    html.append("      margin-top: 24px;\n");
    html.append("    }\n");
    html.append("    .hero-links a {\n");
    html.append("      display: inline-flex;\n");
    html.append("      align-items: center;\n");
    html.append("      gap: 8px;\n");
    html.append("      padding: 11px 16px;\n");
    html.append("      border-radius: 999px;\n");
    html.append("      background: rgba(255, 255, 255, 0.14);\n");
    html.append("      color: #fff;\n");
    html.append("      text-decoration: none;\n");
    html.append("      font-weight: 600;\n");
    html.append("    }\n");
    html.append("    .hero-links a:hover { background: rgba(255, 255, 255, 0.22); }\n");
    html.append("    .layout {\n");
    html.append("      max-width: 1180px;\n");
    html.append("      margin: -28px auto 48px;\n");
    html.append("      padding: 0 24px 48px;\n");
    html.append("      display: grid;\n");
    html.append("      grid-template-columns: minmax(0, 240px) minmax(0, 1fr);\n");
    html.append("      gap: 24px;\n");
    html.append("      align-items: start;\n");
    html.append("    }\n");
    html.append("    .sidebar {\n");
    html.append("      position: sticky;\n");
    html.append("      top: 18px;\n");
    html.append("      background: var(--wave-card);\n");
    html.append("      backdrop-filter: blur(14px);\n");
    html.append("      border: 1px solid rgba(217, 238, 244, 0.92);\n");
    html.append("      border-radius: 20px;\n");
    html.append("      box-shadow: 0 24px 48px rgba(2, 62, 138, 0.08);\n");
    html.append("      padding: 20px 18px;\n");
    html.append("    }\n");
    html.append("    .sidebar h2 {\n");
    html.append("      margin: 0 0 14px;\n");
    html.append("      font-size: 13px;\n");
    html.append("      text-transform: uppercase;\n");
    html.append("      letter-spacing: 0.08em;\n");
    html.append("      color: var(--wave-muted);\n");
    html.append("    }\n");
    html.append("    .sidebar a {\n");
    html.append("      display: block;\n");
    html.append("      padding: 8px 10px;\n");
    html.append("      border-radius: 12px;\n");
    html.append("      color: var(--wave-text);\n");
    html.append("      text-decoration: none;\n");
    html.append("      font-size: 14px;\n");
    html.append("    }\n");
    html.append("    .sidebar a:hover {\n");
    html.append("      background: rgba(0, 180, 216, 0.08);\n");
    html.append("      color: var(--wave-deep);\n");
    html.append("    }\n");
    html.append("    .content {\n");
    html.append("      display: grid;\n");
    html.append("      gap: 22px;\n");
    html.append("    }\n");
    html.append("    section {\n");
    html.append("      background: var(--wave-card);\n");
    html.append("      backdrop-filter: blur(14px);\n");
    html.append("      border: 1px solid rgba(217, 238, 244, 0.92);\n");
    html.append("      border-radius: 24px;\n");
    html.append("      box-shadow: 0 28px 56px rgba(2, 62, 138, 0.08);\n");
    html.append("      padding: 28px;\n");
    html.append("    }\n");
    html.append("    section h2 {\n");
    html.append("      margin: 0 0 18px;\n");
    html.append("      font-size: 28px;\n");
    html.append("      letter-spacing: -0.02em;\n");
    html.append("    }\n");
    html.append("    section h3 {\n");
    html.append("      margin: 28px 0 12px;\n");
    html.append("      font-size: 19px;\n");
    html.append("    }\n");
    html.append("    section p,\n");
    html.append("    section li {\n");
    html.append("      color: var(--wave-text);\n");
    html.append("      line-height: 1.72;\n");
    html.append("      font-size: 15px;\n");
    html.append("    }\n");
    html.append("    ul { padding-left: 20px; margin: 0; }\n");
    html.append("    .note,\n");
    html.append("    .warning,\n");
    html.append("    .grid-card,\n");
    html.append("    .metric-card,\n");
    html.append("    .operation-card {\n");
    html.append("      border-radius: 18px;\n");
    html.append("      border: 1px solid var(--wave-border);\n");
    html.append("    }\n");
    html.append("    .warning {\n");
    html.append("      background: linear-gradient(135deg, rgba(255, 239, 211, 0.9), rgba(255, 248, 233, 0.96));\n");
    html.append("      padding: 16px 18px;\n");
    html.append("      color: #7a4a04;\n");
    html.append("      margin-top: 18px;\n");
    html.append("    }\n");
    html.append("    .note {\n");
    html.append("      background: linear-gradient(135deg, rgba(227, 247, 251, 0.92), rgba(244, 252, 254, 0.96));\n");
    html.append("      padding: 16px 18px;\n");
    html.append("      margin-top: 18px;\n");
    html.append("    }\n");
    html.append("    .metrics {\n");
    html.append("      display: grid;\n");
    html.append("      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\n");
    html.append("      gap: 16px;\n");
    html.append("      margin-top: 22px;\n");
    html.append("    }\n");
    html.append("    .metric-card {\n");
    html.append("      padding: 18px;\n");
    html.append("      background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(235, 250, 252, 0.98));\n");
    html.append("    }\n");
    html.append("    .metric-card strong {\n");
    html.append("      display: block;\n");
    html.append("      font-size: 28px;\n");
    html.append("      margin-bottom: 8px;\n");
    html.append("      color: var(--wave-deep);\n");
    html.append("    }\n");
    html.append("    .code-grid,\n");
    html.append("    .reference-grid {\n");
    html.append("      display: grid;\n");
    html.append("      gap: 16px;\n");
    html.append("    }\n");
    html.append("    .code-grid { grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); }\n");
    html.append("    .reference-grid { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }\n");
    html.append("    .grid-card {\n");
    html.append("      padding: 18px;\n");
    html.append("      background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 252, 254, 0.98));\n");
    html.append("    }\n");
    html.append("    .grid-card h3 {\n");
    html.append("      margin-top: 0;\n");
    html.append("    }\n");
    html.append("    .operation-group {\n");
    html.append("      display: grid;\n");
    html.append("      gap: 18px;\n");
    html.append("      margin-top: 16px;\n");
    html.append("    }\n");
    html.append("    .operation-card {\n");
    html.append("      padding: 22px;\n");
    html.append("      background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 252, 253, 0.98));\n");
    html.append("    }\n");
    html.append("    .operation-card h3 {\n");
    html.append("      margin: 0 0 10px;\n");
    html.append("      display: flex;\n");
    html.append("      align-items: center;\n");
    html.append("      justify-content: space-between;\n");
    html.append("      gap: 12px;\n");
    html.append("      flex-wrap: wrap;\n");
    html.append("    }\n");
    html.append("    .operation-card code {\n");
    html.append("      font-size: 14px;\n");
    html.append("    }\n");
    html.append("    .op-badges {\n");
    html.append("      display: flex;\n");
    html.append("      flex-wrap: wrap;\n");
    html.append("      gap: 10px;\n");
    html.append("      margin: 14px 0 16px;\n");
    html.append("    }\n");
    html.append("    .badge {\n");
    html.append("      display: inline-flex;\n");
    html.append("      align-items: center;\n");
    html.append("      gap: 8px;\n");
    html.append("      padding: 8px 12px;\n");
    html.append("      border-radius: 999px;\n");
    html.append("      background: rgba(0, 180, 216, 0.09);\n");
    html.append("      color: var(--wave-deep);\n");
    html.append("      font-size: 13px;\n");
    html.append("      font-weight: 600;\n");
    html.append("    }\n");
    html.append("    pre {\n");
    html.append("      margin: 0;\n");
    html.append("      padding: 16px 18px;\n");
    html.append("      border-radius: 18px;\n");
    html.append("      overflow-x: auto;\n");
    html.append("      background: linear-gradient(180deg, rgba(5, 27, 45, 0.98), rgba(4, 20, 34, 0.98));\n");
    html.append("      color: #d8f6ff;\n");
    html.append("      font-size: 13px;\n");
    html.append("      line-height: 1.56;\n");
    html.append("    }\n");
    html.append("    .pre-title {\n");
    html.append("      margin: 0 0 8px;\n");
    html.append("      font-size: 13px;\n");
    html.append("      font-weight: 700;\n");
    html.append("      letter-spacing: 0.04em;\n");
    html.append("      text-transform: uppercase;\n");
    html.append("      color: var(--wave-muted);\n");
    html.append("    }\n");
    html.append("    .inline-links {\n");
    html.append("      display: flex;\n");
    html.append("      flex-wrap: wrap;\n");
    html.append("      gap: 10px;\n");
    html.append("    }\n");
    html.append("    .inline-links a {\n");
    html.append("      text-decoration: none;\n");
    html.append("      padding: 8px 12px;\n");
    html.append("      border-radius: 999px;\n");
    html.append("      background: rgba(0, 180, 216, 0.08);\n");
    html.append("      font-weight: 600;\n");
    html.append("    }\n");
    html.append("    .tiny {\n");
    html.append("      font-size: 13px;\n");
    html.append("      color: var(--wave-muted);\n");
    html.append("    }\n");
    html.append("    @media (max-width: 980px) {\n");
    html.append("      .layout { grid-template-columns: 1fr; margin-top: 0; }\n");
    html.append("      .sidebar { position: static; }\n");
    html.append("    }\n");
    html.append("    @media (max-width: 640px) {\n");
    html.append("      .hero { padding: 42px 18px 30px; }\n");
    html.append("      .layout { padding: 0 18px 36px; }\n");
    html.append("      section { padding: 22px; }\n");
    html.append("      .code-grid { grid-template-columns: 1fr; }\n");
    html.append("    }\n");
    html.append("  </style>\n");
    html.append("</head>\n");
    html.append("<body>\n");
    html.append("  <header class=\"hero\">\n");
    html.append("    <div class=\"hero-inner\">\n");
    html.append("      <span class=\"eyebrow\">Self-hosted API docs</span>\n");
    html.append("      <h1>SupaWave Data API</h1>\n");
    html.append(
        "      <p>Wave's current Data/Robot API is a JWT-protected JSON-RPC transport. The canonical endpoint is <code>/robot/dataapi/rpc</code>; <code>/robot/dataapi</code> stays live as a backward-compatible alias. Responses are always returned as a JSON array in request order.</p>\n");
    html.append("      <div class=\"hero-links\">\n");
    html.append("        <a href=\"").append(OPENAPI_PATH).append("\">OpenAPI 3.0 JSON</a>\n");
    html.append("        <a href=\"").append(LLMS_INDEX_PATH).append("\">llms.txt index</a>\n");
    html.append("        <a href=\"").append(LLMS_FULL_PATH).append("\">LLM reference</a>\n");
    html.append("        <a href=\"#operations\">24 supported operations</a>\n");
    html.append("      </div>\n");
    html.append("    </div>\n");
    html.append("  </header>\n");
    html.append("  <div class=\"layout\">\n");
    html.append("    <aside class=\"sidebar\">\n");
    html.append("      <h2>Contents</h2>\n");
    html.append("      <a href=\"#overview\">Overview</a>\n");
    html.append("      <a href=\"#transport\">Transport model</a>\n");
    html.append("      <a href=\"#auth\">Authentication</a>\n");
    html.append("      <a href=\"#token\">Token walkthrough</a>\n");
    html.append("      <a href=\"#build-with-ai\">Build with AI</a>\n");
    html.append("      <a href=\"#walkthrough\">End-to-end example</a>\n");
    html.append("      <a href=\"#operations\">Operation reference</a>\n");
    html.append("      <a href=\"#errors\">Errors and status codes</a>\n");
    html.append("      <a href=\"#versioning\">Versioning</a>\n");
    html.append("      <a href=\"#legacy\">Legacy notes</a>\n");
    for (String group : GROUP_ORDER) {
      html.append("      <h2>").append(escape(group)).append("</h2>\n");
      for (OperationDoc operation : operationsForGroup(group)) {
        html.append("      <a href=\"#")
            .append(operationAnchor(operation.method))
            .append("\">")
            .append(escape(operation.method))
            .append("</a>\n");
      }
    }
    html.append("    </aside>\n");
    html.append("    <main class=\"content\">\n");
    html.append("      <section id=\"overview\">\n");
    html.append("        <h2>Overview</h2>\n");
    html.append(
        "        <p>This API lets authenticated users and robots create waves, fetch wave state, append or edit blips, manage participants, query search/profile data, and move snapshots, deltas, or attachments in and out of Wave. The server-side Jakarta Data API servlet is the runtime source of truth for the operation inventory published here.</p>\n");
    html.append("        <div class=\"metrics\">\n");
    html.append("          <div class=\"metric-card\"><strong>24</strong><span>Supported JSON-RPC methods</span></div>\n");
    html.append("          <div class=\"metric-card\"><strong>JWT</strong><span>Bearer-only authentication on the RPC servlet</span></div>\n");
    html.append("          <div class=\"metric-card\"><strong>2</strong><span>Live RPC routes: canonical + alias</span></div>\n");
    html.append("          <div class=\"metric-card\"><strong>v")
        .append(escape(DOCS_VERSION))
        .append("</strong><span>Docs version stamp</span></div>\n");
    html.append("        </div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"transport\">\n");
    html.append("        <h2>Transport model</h2>\n");
    html.append(
        "        <p>Every RPC call is an HTTP <code>POST</code> carrying a JSON object or an array of JSON objects. The request envelope is JSON-RPC-like and uses <code>id</code>, <code>method</code>, and <code>params</code>. The response is always a JSON array even when the request body contains a single object.</p>\n");
    html.append("        <div class=\"reference-grid\">\n");
    html.append("          <div class=\"grid-card\"><h3>Canonical path</h3><p><code>POST ")
        .append(escape(CANONICAL_RPC_PATH))
        .append("</code></p></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Alias path</h3><p><code>POST ")
        .append(escape(RPC_ALIAS_PATH))
        .append("</code></p></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Single request body</h3><pre>")
        .append(escape(json(singleRequestTemplate())))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Batch response shape</h3><pre>")
        .append(escape(json(batchResponseTemplate())))
        .append("</pre></div>\n");
    html.append("        </div>\n");
    html.append("        <div class=\"note\"><strong>Compatibility:</strong> <code>/robot/dataapi</code> is still active, but new tooling and documentation should use <code>/robot/dataapi/rpc</code> as the canonical path.</div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"auth\">\n");
    html.append("        <h2>Authentication</h2>\n");
    html.append(
        "        <p>The RPC servlet accepts only a bearer JWT in the exact header form <code>Authorization: Bearer &lt;token&gt;</code>. The live Jakarta implementation validates the token type <code>data-api-access</code>, audience <code>data-api</code>, and the participant address in the JWT <code>sub</code> claim. There is no cookie or query-parameter fallback for RPC requests.</p>\n");
    html.append("        <div class=\"code-grid\">\n");
    html.append("          <div class=\"grid-card\"><h3>Required header</h3><pre>Authorization: Bearer $TOKEN</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Token endpoint</h3><pre>GET  ")
        .append(escape(TOKEN_PATH))
        .append("\nPOST ")
        .append(escape(TOKEN_PATH))
        .append("</pre></div>\n");
    html.append("        </div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"token\">\n");
    html.append("        <h2>Token generation walkthrough</h2>\n");
    html.append("        <h3>Browser/session flow</h3>\n");
    html.append("        <ul>\n");
    html.append("          <li>Open <code>")
        .append(escape(baseUrl))
        .append(TOKEN_PATH)
        .append("</code> while logged in.</li>\n");
    html.append("          <li>Select a short-lived expiry such as <code>3600</code> seconds.</li>\n");
    html.append("          <li>Generate the token and copy it into your <code>Authorization</code> header.</li>\n");
    html.append("        </ul>\n");
    html.append("        <h3>Robot client_credentials flow</h3>\n");
    html.append("        <p class=\"tiny\">This is the curl example to use when you need a token from a robot account. The docs intentionally avoid <code>expiry=0</code> or any never-expiring example.</p>\n");
    html.append("        <pre>")
        .append(escape(tokenCurlExample(baseUrl)))
        .append("</pre>\n");
    html.append("        <div class=\"warning\"><strong>Use short-lived tokens.</strong> The live token endpoint still supports effectively long-lived tokens when <code>expiry &lt;= 0</code> or when a robot account is configured with a zero lifetime. That behavior is high-risk and not shown in any example here.</div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"build-with-ai\">\n");
    html.append("        <h2>Build with AI</h2>\n");
    html.append("        <p>Google AI Studio / Gemini works best when you give it a short-lived token, the machine-readable docs, and stable environment variable names. Generate a one-hour JWT, paste the prompt below into your preferred LLM, and replace the robot-specific placeholders once you mint the robot secret.</p>\n");
    html.append("        <pre>");
    html.append(escape("Google AI Studio / Gemini starter prompt\n\n"
        + "Build a SupaWave robot for me.\n"
        + "Use these environment variables exactly:\n"
        + "SUPAWAVE_BASE_URL=" + baseUrl + "\n"
        + "SUPAWAVE_API_DOCS_URL=" + baseUrl + "/api-docs\n"
        + "SUPAWAVE_LLM_DOCS_URL=" + baseUrl + "/api/llm.txt\n"
        + "SUPAWAVE_DATA_API_URL=" + baseUrl + CANONICAL_RPC_PATH + "\n"
        + "SUPAWAVE_DATA_API_TOKEN=<1 hour JWT>\n"
        + "SUPAWAVE_ROBOT_ID=<robot@domain>\n"
        + "SUPAWAVE_ROBOT_SECRET=<consumer secret>\n"
        + "SUPAWAVE_ROBOT_CALLBACK_URL=<deployment url>\n\n"
        + "Read the docs first, prefer minimal JSON payloads, explain security tradeoffs, and keep tokens short-lived."));
    html.append("</pre>\n");
    html.append("        <div class=\"reference-grid\">\n");
    html.append("          <div class=\"grid-card\"><h3>Recommended env vars</h3><pre>SUPAWAVE_BASE_URL\nSUPAWAVE_API_DOCS_URL\nSUPAWAVE_LLM_DOCS_URL\nSUPAWAVE_DATA_API_URL\nSUPAWAVE_DATA_API_TOKEN\nSUPAWAVE_ROBOT_ID\nSUPAWAVE_ROBOT_SECRET\nSUPAWAVE_ROBOT_CALLBACK_URL</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Minimal common operations</h3><pre>robot.createWavelet\nwavelet.appendBlip\nwavelet.addParticipant\nrobot.fetchWave\nrobot.search</pre></div>\n");
    html.append("        </div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"walkthrough\">\n");
    html.append("        <h2>Complete end-to-end example</h2>\n");
    html.append("        <p>The quickest safe path for a new integration is: get a short-lived token, create a wave, append a root-thread blip, and add a participant. These are separate calls so you can inspect the IDs returned by each step.</p>\n");
    html.append("        <div class=\"code-grid\">\n");
    html.append("          <div class=\"grid-card\"><h3>1. Get token</h3><pre>")
        .append(escape(tokenCurlExample(baseUrl)))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>2. Create wave</h3><pre>")
        .append(escape(curlForOperation(baseUrl, findOperation("robot.createWavelet"))))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>3. Append blip</h3><pre>")
        .append(escape(curlForOperation(baseUrl, findOperation("wavelet.appendBlip"))))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>4. Add participant</h3><pre>")
        .append(escape(curlForOperation(baseUrl, findOperation("wavelet.addParticipant"))))
        .append("</pre></div>\n");
    html.append("        </div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"operations\">\n");
    html.append("        <h2>Operation reference</h2>\n");
    html.append(
        "        <p>Every method below is currently registered in the live Jakarta Data API registry. Unsupported legacy enum values such as <code>wavelet.create</code> are intentionally excluded.</p>\n");
    for (String group : GROUP_ORDER) {
      html.append("        <h3>").append(escape(group)).append("</h3>\n");
      html.append("        <div class=\"operation-group\">\n");
      for (OperationDoc operation : operationsForGroup(group)) {
        html.append(renderOperationCard(baseUrl, operation));
      }
      html.append("        </div>\n");
    }
    html.append("      </section>\n");
    html.append("      <section id=\"errors\">\n");
    html.append("        <h2>Errors and status codes</h2>\n");
    html.append("        <div class=\"code-grid\">\n");
    html.append("          <div class=\"grid-card\"><h3>Invalid or missing JWT</h3><pre>")
        .append(escape("HTTP/1.1 401 Unauthorized"))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Malformed JSON body</h3><pre>")
        .append(escape("HTTP/1.1 400 Bad Request\nUnable to parse Json to list of OperationRequests"))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Per-operation failure</h3><pre>")
        .append(escape(json(list(orderedMap("id", "op-1", "error", orderedMap("message", "Invalid id"))))))
        .append("</pre></div>\n");
    html.append("          <div class=\"grid-card\"><h3>Token endpoint JSON error</h3><pre>")
        .append(escape(json(orderedMap("error", "invalid_client", "error_description", "Robot credentials were rejected"))))
        .append("</pre></div>\n");
    html.append("        </div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"versioning\">\n");
    html.append("        <h2>Versioning and compatibility</h2>\n");
    html.append("        <p>The runtime Data API is currently unversioned. This documentation set is version stamped at <code>")
        .append(escape(DOCS_VERSION))
        .append("</code> so clients can see which contract snapshot they were built against. Future breaking changes should introduce a versioned runtime path rather than silently changing semantics in place.</p>\n");
    html.append("        <div class=\"inline-links\"><a href=\"")
        .append(OPENAPI_PATH)
        .append("\">Machine-readable contract</a><a href=\"")
        .append(LLMS_INDEX_PATH)
        .append("\">llms.txt index</a><a href=\"")
        .append(LLMS_FULL_PATH)
        .append("\">LLM reference</a></div>\n");
    html.append("      </section>\n");
    html.append("      <section id=\"legacy\">\n");
    html.append("        <h2>Legacy and unsupported notes</h2>\n");
    html.append("        <ul>\n");
    html.append("          <li><code>robot.createWavelet</code> is the supported create method. <code>wavelet.create</code> is not part of the live Jakarta Data API registry.</li>\n");
    html.append("          <li><code>DataApiOAuthServlet</code> still exists in source, but it is not registered by the live Jakarta server and should not be treated as the current auth story.</li>\n");
    html.append("          <li>Public docs here are scoped to the 24 methods in the live Jakarta registry, not the full shared <code>OperationType</code> enum.</li>\n");
    html.append("        </ul>\n");
    html.append("      </section>\n");
    html.append("    </main>\n");
    html.append("  </div>\n");
    html.append("</body>\n");
    html.append("</html>\n");
    return html.toString();
  }

  private static String renderOperationCard(String baseUrl, OperationDoc operation) {
    StringBuilder html = new StringBuilder(8192);
    html.append("          <article class=\"operation-card\" id=\"")
        .append(operationAnchor(operation.method))
        .append("\">\n");
    html.append("            <h3><span><code>")
        .append(escape(operation.method))
        .append("</code></span><span class=\"tiny\">")
        .append(escape(operation.requestSchemaName))
        .append(" / ")
        .append(escape(operation.responseSchemaName))
        .append("</span></h3>\n");
    html.append("            <p>")
        .append(escape(operation.summary))
        .append("</p>\n");
    html.append("            <div class=\"op-badges\">\n");
    html.append("              <span class=\"badge\">Auth: bearer JWT</span>\n");
    html.append("              <span class=\"badge\">Required: ")
        .append(escape(joinOrNone(operation.requiredParams)))
        .append("</span>\n");
    html.append("              <span class=\"badge\">Optional: ")
        .append(escape(joinOrNone(operation.optionalParams)))
        .append("</span>\n");
    html.append("            </div>\n");
    html.append("            <div class=\"code-grid\">\n");
    html.append("              <div>\n");
    html.append("                <p class=\"pre-title\">Request body schema</p>\n");
    html.append("                <pre>")
        .append(escape(json(requestExampleEnvelope(operation))))
        .append("</pre>\n");
    html.append("              </div>\n");
    html.append("              <div>\n");
    html.append("                <p class=\"pre-title\">Response shape</p>\n");
    html.append("                <pre>")
        .append(escape(json(successExampleEnvelope(operation))))
        .append("</pre>\n");
    html.append("              </div>\n");
    html.append("            </div>\n");
    html.append("            <p class=\"pre-title\">curl example</p>\n");
    html.append("            <pre>")
        .append(escape(curlForOperation(baseUrl, operation)))
        .append("</pre>\n");
    if (!operation.notes.isEmpty()) {
      html.append("            <div class=\"note\"><strong>Notes:</strong> ")
          .append(escape(operation.notes))
          .append("</div>\n");
    }
    html.append("          </article>\n");
    return html.toString();
  }

  private static String renderOpenApiJson() {
    Map<String, Object> document = orderedMap();
    document.put("openapi", "3.0.3");
    document.put(
        "info",
        orderedMap(
            "title", "SupaWave Data API",
            "version", DOCS_VERSION,
            "description",
                "JWT-protected JSON-RPC contract for the live Jakarta Data/Robot API. "
                    + CANONICAL_RPC_PATH
                    + " is canonical and "
                    + RPC_ALIAS_PATH
                    + " remains a compatible alias."));
    document.put("servers", list(orderedMap("url", "/")));

    Map<String, Object> paths = orderedMap();
    paths.put(CANONICAL_RPC_PATH, rpcPathObject(true));
    paths.put(RPC_ALIAS_PATH, rpcPathObject(false));
    paths.put(TOKEN_PATH, tokenPathObject());
    document.put("paths", paths);

    Map<String, Object> components = orderedMap();
    components.put("securitySchemes", orderedMap("BearerAuth", bearerAuthScheme()));
    Map<String, Object> schemas = orderedMap();
    schemas.put("DataApiSingleRequest", singleRequestSchema());
    schemas.put("DataApiBatchRequest", batchRequestSchema());
    schemas.put("JsonRpcResponseBatch", batchResponseSchema());
    schemas.put("JsonRpcSuccessItem", successItemSchema());
    schemas.put("JsonRpcErrorItem", errorItemSchema());
    schemas.put("AccessTokenResponse", accessTokenSchema());
    schemas.put("TokenErrorResponse", tokenErrorSchema());
    schemas.put(
        "RobotDataApiTokenRequest",
        orderedMap(
            "type", "object",
            "required", list("grant_type", "client_id", "client_secret"),
            "properties",
                orderedMap(
                    "grant_type",
                        orderedMap("type", "string", "enum", list("client_credentials")),
                    "client_id", orderedMap("type", "string"),
                    "client_secret", orderedMap("type", "string"),
                    "expiry", orderedMap("type", "integer", "format", "int64", "example", 3600))));
    for (OperationDoc operation : OPERATIONS) {
      schemas.put(operation.requestSchemaName, requestSchema(operation));
      schemas.put(operation.responseSchemaName, responseSchema(operation));
    }
    components.put("schemas", schemas);
    document.put("components", components);
    return PRETTY_JSON.toJson(document);
  }

  private static Map<String, Object> rpcPathObject(boolean canonical) {
    Map<String, Object> post = orderedMap();
    post.put("summary", canonical ? "Canonical Data API JSON-RPC endpoint" : "Backward-compatible alias");
    post.put(
        "description",
        canonical
            ? "Primary JSON-RPC transport for the Data API."
            : "Live alias for the canonical Data API JSON-RPC transport.");
    post.put("security", list(orderedMap("BearerAuth", list())));
    post.put(
        "requestBody",
        orderedMap(
            "required", true,
            "content",
                orderedMap(
                    "application/json",
                    orderedMap(
                        "schema",
                            orderedMap(
                                "oneOf",
                                list(
                                    orderedMap("$ref", "#/components/schemas/DataApiSingleRequest"),
                                    orderedMap("$ref", "#/components/schemas/DataApiBatchRequest"))),
                        "examples",
                            orderedMap(
                                "createWavelet",
                                    orderedMap(
                                        "summary", "Create a new wave",
                                        "value", list(requestExampleEnvelope(findOperation("robot.createWavelet")))),
                                "appendBlip",
                                    orderedMap(
                                        "summary", "Append a root-thread blip",
                                        "value", list(requestExampleEnvelope(findOperation("wavelet.appendBlip")))))))));
    post.put(
        "responses",
        orderedMap(
            "200",
                orderedMap(
                    "description", "A JSON array in request order containing success and/or per-operation error items.",
                    "content",
                        orderedMap(
                            "application/json",
                            orderedMap("schema", orderedMap("$ref", "#/components/schemas/JsonRpcResponseBatch")))),
            "400", orderedMap("description", "Malformed JSON request body."),
            "401", orderedMap("description", "Missing or invalid bearer token.")));
    post.put("x-jsonrpc-transport", true);
    post.put("x-canonical-path", CANONICAL_RPC_PATH);
    post.put(
        "x-wave-supported-methods",
        methodNames());
    post.put(
        "x-wave-operation-schemas",
        schemaIndex());
    return orderedMap("post", post);
  }

  private static Map<String, Object> tokenPathObject() {
    Map<String, Object> get = orderedMap();
    get.put("summary", "Render token UI");
    get.put(
        "description",
        "Shows the browser/session token page for logged-in users or redirects to /auth/signin?r=/robot/dataapi/token.");
    get.put(
        "responses",
        orderedMap(
            "200",
                orderedMap(
                    "description", "HTML token UI",
                    "content", orderedMap("text/html", orderedMap("schema", orderedMap("type", "string")))),
            "302", orderedMap("description", "Redirect to sign-in for unauthenticated callers.")));

    Map<String, Object> post = orderedMap();
    post.put("summary", "Issue a Data API JWT");
    post.put(
        "description",
        "Supports browser-session issuance and robot client_credentials issuance. Prefer expiry=3600 or another short-lived value.");
    post.put(
        "requestBody",
        orderedMap(
            "required", true,
            "content",
                orderedMap(
                    "application/x-www-form-urlencoded",
                    orderedMap("schema", orderedMap("$ref", "#/components/schemas/RobotDataApiTokenRequest")))));
    post.put(
        "responses",
        orderedMap(
            "200",
                orderedMap(
                    "description", "Access token response",
                    "content",
                        orderedMap(
                            "application/json",
                            orderedMap("schema", orderedMap("$ref", "#/components/schemas/AccessTokenResponse")))),
            "400",
                orderedMap(
                    "description", "Token request validation error",
                    "content",
                        orderedMap(
                            "application/json",
                            orderedMap("schema", orderedMap("$ref", "#/components/schemas/TokenErrorResponse")))),
            "401",
                orderedMap(
                    "description", "Unauthenticated browser caller",
                    "content",
                        orderedMap(
                            "application/json",
                            orderedMap("schema", orderedMap("$ref", "#/components/schemas/TokenErrorResponse"))))));
    return orderedMap("get", get, "post", post);
  }

  private static Map<String, Object> bearerAuthScheme() {
    return orderedMap(
        "type", "http",
        "scheme", "bearer",
        "bearerFormat", "JWT",
        "description", "JWT type=data-api-access, audience=data-api");
  }

  private static Map<String, Object> singleRequestSchema() {
    List<Object> refs = new ArrayList<Object>();
    for (OperationDoc operation : OPERATIONS) {
      refs.add(orderedMap("$ref", "#/components/schemas/" + operation.requestSchemaName));
    }
    return orderedMap("oneOf", refs);
  }

  private static Map<String, Object> batchRequestSchema() {
    List<Object> refs = new ArrayList<Object>();
    for (OperationDoc operation : OPERATIONS) {
      refs.add(orderedMap("$ref", "#/components/schemas/" + operation.requestSchemaName));
    }
    return orderedMap("type", "array", "items", orderedMap("oneOf", refs));
  }

  private static Map<String, Object> batchResponseSchema() {
    return orderedMap(
        "type", "array",
        "items",
            orderedMap(
                "oneOf",
                list(
                    orderedMap("$ref", "#/components/schemas/JsonRpcSuccessItem"),
                    orderedMap("$ref", "#/components/schemas/JsonRpcErrorItem"))));
  }

  private static Map<String, Object> successItemSchema() {
    return orderedMap(
        "type", "object",
        "required", list("id", "data"),
        "properties",
            orderedMap(
                "id", orderedMap("type", "string"),
                "data", orderedMap("type", "object")));
  }

  private static Map<String, Object> errorItemSchema() {
    return orderedMap(
        "type", "object",
        "required", list("id", "error"),
        "properties",
            orderedMap(
                "id", orderedMap("type", "string"),
                "error",
                    orderedMap(
                        "type", "object",
                        "properties",
                            orderedMap(
                                "message", orderedMap("type", "string")))));
  }

  private static Map<String, Object> accessTokenSchema() {
    return orderedMap(
        "type", "object",
        "required", list("access_token", "token_type", "expires_in"),
        "properties",
            orderedMap(
                "access_token", orderedMap("type", "string"),
                "token_type", orderedMap("type", "string", "example", "bearer"),
                "expires_in", orderedMap("type", "integer", "format", "int64", "example", 3600)));
  }

  private static Map<String, Object> tokenErrorSchema() {
    return orderedMap(
        "type", "object",
        "required", list("error"),
        "properties",
            orderedMap(
                "error", orderedMap("type", "string"),
                "error_description", orderedMap("type", "string")));
  }

  private static Map<String, Object> requestSchema(OperationDoc operation) {
    Map<String, Object> schema =
        orderedMap(
            "type", "object",
            "required", list("id", "method", "params"),
            "properties",
                orderedMap(
                    "id", orderedMap("type", "string", "example", "op-1"),
                    "method",
                        orderedMap("type", "string", "enum", list(operation.method), "example", operation.method),
                    "params", inferObjectSchema(operation.paramsExample, operation.requiredParams)));
    schema.put("description", operation.summary);
    schema.put("x-jsonrpc-method", operation.method);
    schema.put("x-wave-operation-group", operation.group);
    schema.put("x-primary-example", requestExampleEnvelope(operation));
    return schema;
  }

  private static Map<String, Object> responseSchema(OperationDoc operation) {
    Map<String, Object> schema =
        orderedMap(
            "type", "object",
            "required", list("id", "data"),
            "properties",
                orderedMap(
                    "id", orderedMap("type", "string", "example", "op-1"),
                    "data", inferSchema(operation.responseDataExample)));
    schema.put("description", operation.summary);
    schema.put("x-jsonrpc-method", operation.method);
    schema.put("x-wave-operation-group", operation.group);
    schema.put("x-primary-example", successExampleEnvelope(operation).get(0));
    return schema;
  }

  private static Map<String, Object> inferSchema(Object value) {
    if (value instanceof Map) {
      return inferObjectSchema(castMap(value), Collections.<String>emptyList());
    }
    if (value instanceof List) {
      List<?> listValue = (List<?>) value;
      Object itemExample = listValue.isEmpty() ? "" : listValue.get(0);
      return orderedMap("type", "array", "items", inferSchema(itemExample));
    }
    if (value instanceof Boolean) {
      return orderedMap("type", "boolean", "example", value);
    }
    if (value instanceof Integer || value instanceof Long) {
      return orderedMap("type", "integer", "example", value);
    }
    if (value instanceof Number) {
      return orderedMap("type", "number", "example", value);
    }
    return orderedMap("type", "string", "example", value == null ? "" : value);
  }

  private static Map<String, Object> inferObjectSchema(
      Map<String, Object> example, List<String> requiredParams) {
    Map<String, Object> properties = orderedMap();
    for (Map.Entry<String, Object> entry : example.entrySet()) {
      properties.put(entry.getKey(), inferSchema(entry.getValue()));
    }
    Map<String, Object> schema = orderedMap("type", "object", "properties", properties);
    if (!requiredParams.isEmpty()) {
      schema.put("required", new ArrayList<Object>(requiredParams));
    }
    return schema;
  }

  private static String renderLlmIndexText(String baseUrl) {
    StringBuilder text = new StringBuilder(4096);
    text.append("# SupaWave API Docs\n\n");
    text.append("> Self-hosted SupaWave Data API documentation for humans, API clients, and LLM agents.\n\n");
    text.append("- HTML docs: ").append(baseUrl).append(API_DOCS_PATH).append('\n');
    text.append("- OpenAPI JSON: ").append(baseUrl).append(OPENAPI_PATH).append('\n');
    text.append("- Full LLM reference: ").append(baseUrl).append(LLMS_FULL_PATH).append('\n');
    text.append("- Legacy LLM alias: ").append(baseUrl).append(LLM_ALIAS_PATH).append('\n');
    text.append("- Token endpoint: ").append(baseUrl).append(TOKEN_PATH).append('\n');
    text.append("- Canonical RPC endpoint: ").append(baseUrl).append(CANONICAL_RPC_PATH).append('\n');
    return text.toString();
  }

  private static String renderLlmFullText(String baseUrl) {
    StringBuilder text = new StringBuilder(72000);
    text.append("SupaWave Data API LLM Reference\n");
    text.append("Docs version: ").append(DOCS_VERSION).append('\n');
    text.append("Index: ").append(baseUrl).append(LLMS_INDEX_PATH).append('\n');
    text.append("HTML docs: ").append(baseUrl).append(API_DOCS_PATH).append('\n');
    text.append("OpenAPI JSON: ").append(baseUrl).append(OPENAPI_PATH).append('\n');
    text.append("Canonical RPC path: ").append(CANONICAL_RPC_PATH).append('\n');
    text.append("Alias path: ").append(RPC_ALIAS_PATH).append('\n');
    text.append("Token endpoint: ").append(TOKEN_PATH).append('\n');
    text.append("Auth: Authorization: Bearer <token> (JWT type=data-api-access, audience=data-api)\n");
    text.append("Transport: HTTP POST JSON-RPC. Request body can be one object or an array. Response body is always an array in request order.\n\n");

    text.append("Token acquisition (client_credentials, short-lived example)\n");
    text.append(tokenCurlExample(baseUrl)).append("\n\n");

    text.append("Google AI Studio / Gemini starter prompt\n");
    text.append("Build a SupaWave robot for me.\n");
    text.append("Use these environment variables exactly:\n");
    text.append("SUPAWAVE_BASE_URL=").append(baseUrl).append('\n');
    text.append("SUPAWAVE_API_DOCS_URL=").append(baseUrl).append("/api-docs\n");
    text.append("SUPAWAVE_LLM_DOCS_URL=").append(baseUrl).append("/api/llm.txt\n");
    text.append("SUPAWAVE_DATA_API_URL=").append(baseUrl).append(CANONICAL_RPC_PATH).append('\n');
    text.append("SUPAWAVE_DATA_API_TOKEN=<1 hour JWT>\n");
    text.append("SUPAWAVE_ROBOT_ID=<robot@domain>\n");
    text.append("SUPAWAVE_ROBOT_SECRET=<consumer secret>\n");
    text.append("SUPAWAVE_ROBOT_CALLBACK_URL=<deployment url>\n\n");

    text.append("Minimal common operations\n");
    text.append("- robot.createWavelet\n");
    text.append("- wavelet.appendBlip\n");
    text.append("- wavelet.addParticipant\n");
    text.append("- robot.fetchWave\n");
    text.append("- robot.search\n\n");

    text.append("Request envelope\n");
    text.append(json(singleRequestTemplate())).append("\n\n");

    text.append("Batch response envelope\n");
    text.append(json(batchResponseTemplate())).append("\n\n");

    text.append("Supported methods\n");
    for (OperationDoc operation : OPERATIONS) {
      text.append("- ").append(operation.method).append('\n');
    }
    text.append('\n');

    for (String group : GROUP_ORDER) {
      text.append(group).append('\n');
      text.append(repeat("=", group.length())).append('\n');
      for (OperationDoc operation : operationsForGroup(group)) {
        text.append(operation.method).append('\n');
        text.append("Summary: ").append(operation.summary).append('\n');
        text.append("Required params: ").append(joinOrNone(operation.requiredParams)).append('\n');
        text.append("Optional params: ").append(joinOrNone(operation.optionalParams)).append('\n');
        text.append("Component schema: ")
            .append(operation.requestSchemaName)
            .append(" / ")
            .append(operation.responseSchemaName)
            .append('\n');
        text.append("Request skeleton:\n");
        text.append(json(requestExampleEnvelope(operation))).append('\n');
        text.append("Response skeleton:\n");
        text.append(json(successExampleEnvelope(operation))).append('\n');
        if (!operation.notes.isEmpty()) {
          text.append("Notes: ").append(operation.notes).append('\n');
        }
        text.append('\n');
      }
    }

    text.append("Short end-to-end example\n");
    text.append("1. Get token -> POST ").append(TOKEN_PATH).append(" with expiry=3600.\n");
    text.append("2. Create wave -> robot.createWavelet.\n");
    text.append("3. Append blip -> wavelet.appendBlip.\n");
    text.append("4. Add participant -> wavelet.addParticipant.\n\n");

    text.append("Error conventions\n");
    text.append("- 401 for missing or invalid bearer tokens.\n");
    text.append("- 400 for malformed JSON request bodies.\n");
    text.append("- 200 with per-operation error items for execution failures.\n");
    text.append("- Token endpoint errors return HTTP status plus JSON {error, error_description?}.\n\n");

    text.append("Compatibility notes\n");
    text.append("- Use ").append(CANONICAL_RPC_PATH).append(" for new integrations.\n");
    text.append("- ").append(RPC_ALIAS_PATH).append(" remains live for compatibility.\n");
    text.append("- ").append(LLMS_FULL_PATH).append(" is the canonical LLM-friendly reference path.\n");
    text.append("- ").append(LLM_ALIAS_PATH).append(" remains live as a backward-compatible alias.\n");
    text.append("- Do not advertise wavelet.create or DataApiOAuthServlet as the current public API.\n");
    return text.toString();
  }

  private static Map<String, Object> schemaIndex() {
    Map<String, Object> index = orderedMap();
    for (OperationDoc operation : OPERATIONS) {
      index.put(
          operation.method,
          orderedMap(
              "request", operation.requestSchemaName,
              "response", operation.responseSchemaName));
    }
    return index;
  }

  private static List<String> methodNames() {
    List<String> methods = new ArrayList<String>();
    for (OperationDoc operation : OPERATIONS) {
      methods.add(operation.method);
    }
    return methods;
  }

  private static String tokenCurlExample(String baseUrl) {
    return "curl -sS \\\n"
        + "  -X POST "
        + baseUrl
        + TOKEN_PATH
        + " \\\n"
        + "  -H 'Content-Type: application/x-www-form-urlencoded' \\\n"
        + "  --data-urlencode 'grant_type=client_credentials' \\\n"
        + "  --data-urlencode 'client_id=robot@example.com' \\\n"
        + "  --data-urlencode 'client_secret=replace-me' \\\n"
        + "  --data-urlencode 'expiry=3600'";
  }

  private static String curlForOperation(String baseUrl, OperationDoc operation) {
    return "curl -sS \\\n"
        + "  -X POST "
        + baseUrl
        + CANONICAL_RPC_PATH
        + " \\\n"
        + "  -H 'Authorization: Bearer $TOKEN' \\\n"
        + "  -H 'Content-Type: application/json' \\\n"
        + "  --data-binary '"
        + escapeSingleQuotes(PRETTY_JSON.toJson(list(requestExampleEnvelope(operation))))
        + "'";
  }

  private static Map<String, Object> singleRequestTemplate() {
    return orderedMap(
        "id", "op-1",
        "method", "robot.createWavelet",
        "params", orderedMap("waveletData", orderedMap("waveId", "example.com!TBD_wave_1")));
  }

  private static List<Map<String, Object>> batchResponseTemplate() {
    return list(
        orderedMap("id", "op-1", "data", orderedMap("waveId", "example.com!w+abc123")),
        orderedMap("id", "op-2", "error", orderedMap("message", "Invalid id")));
  }

  private static Map<String, Object> requestExampleEnvelope(OperationDoc operation) {
    return orderedMap("id", "op-1", "method", operation.method, "params", operation.paramsExample);
  }

  private static List<Map<String, Object>> successExampleEnvelope(OperationDoc operation) {
    return list(orderedMap("id", "op-1", "data", operation.responseDataExample));
  }

  private static String operationAnchor(String method) {
    String normalized = NON_ALNUM.matcher(method.toLowerCase(Locale.ROOT)).replaceAll("-");
    return "operation-" + normalized;
  }

  private static String escape(String value) {
    return HtmlRenderer.escapeHtml(value == null ? "" : value);
  }

  private static String json(Object value) {
    return PRETTY_JSON.toJson(value);
  }

  private static String escapeSingleQuotes(String value) {
    return value.replace("'", "'\"'\"'");
  }

  private static String repeat(String value, int count) {
    StringBuilder builder = new StringBuilder(value.length() * count);
    for (int i = 0; i < count; i++) {
      builder.append(value);
    }
    return builder.toString();
  }

  private static String joinOrNone(List<String> values) {
    return values.isEmpty() ? "none" : String.join(", ", values);
  }

  private static List<OperationDoc> operationsForGroup(String group) {
    List<OperationDoc> operations = new ArrayList<OperationDoc>();
    for (OperationDoc operation : OPERATIONS) {
      if (operation.group.equals(group)) {
        operations.add(operation);
      }
    }
    return operations;
  }

  private static OperationDoc findOperation(String method) {
    for (OperationDoc operation : OPERATIONS) {
      if (operation.method.equals(method)) {
        return operation;
      }
    }
    throw new IllegalArgumentException("Unknown method " + method);
  }

  private static List<OperationDoc> createOperations() {
    List<OperationDoc> operations = new ArrayList<OperationDoc>();
    operations.add(
        operation(
            "Protocol and internal",
            "robot.notify",
            "Negotiates protocol version and optionally communicates a capabilities hash.",
            list("protocolVersion"),
            list("capabilitiesHash"),
            orderedMap("protocolVersion", "0.22", "capabilitiesHash", "sha256:optional"),
            orderedMap(),
            "Internal compatibility helper. Successful responses are empty."));
    operations.add(
        operation(
            "Protocol and internal",
            "robot.notifyCapabilitiesHash",
            "Legacy no-op notification entry kept for compatibility with older clients.",
            list("capabilitiesHash"),
            list(),
            orderedMap("capabilitiesHash", "sha256:robot-capabilities"),
            orderedMap(),
            "Registered today but intended only for legacy support."));
    operations.add(
        operation(
            "Wave and conversation",
            "robot.createWavelet",
            "Creates a new conversational wave and returns the real wave, wavelet, and root blip IDs.",
            list("waveletData"),
            list("message"),
            orderedMap(
                "waveletData",
                    orderedMap(
                        "waveId", "example.com!TBD_wave_1",
                        "waveletId", "example.com!conv+root",
                        "rootBlipId", "TBD_blip_1",
                        "participants", list("alice@example.com", "bob@example.com")),
                "message", "created from the Data API"),
            orderedMap(
                "blipId", "b+root",
                "message", "created from the Data API",
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root"),
            "Use temporary IDs in the request. The success payload contains the real IDs."));
    operations.add(
        operation(
            "Wave and conversation",
            "robot.fetchWave",
            "Fetches wave state for a specific wavelet or returns visible wavelet IDs when returnWaveletIds=true.",
            list("waveId"),
            list("waveletId", "returnWaveletIds", "message"),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "returnWaveletIds", false,
                "message", "optional fetch tag"),
            orderedMap(
                "blipId", "b+root",
                "message", "optional fetch tag",
                "waveletData",
                    orderedMap(
                        "waveId", "example.com!w+abc123",
                        "waveletId", "example.com!conv+root",
                        "rootBlipId", "b+root",
                        "title", "Project kickoff"),
                "blips",
                    orderedMap(
                        "b+root",
                            orderedMap(
                                "blipId", "b+root",
                                "content", "\nWelcome to the wave",
                                "contributors", list("alice@example.com"))),
                "threads", orderedMap("thread+root", orderedMap("id", "thread+root", "blipIds", list("b+root")))),
            "When returnWaveletIds=true the server returns waveletIds instead of the waveletData/blips/threads bundle."));
    operations.add(
        operation(
            "Wave and conversation",
            "wavelet.appendBlip",
            "Appends a new blip to the root thread of the target conversation.",
            list("waveId", "waveletId", "blipData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipData", orderedMap("blipId", "TBD_blip_2", "content", "\nHello from the API")),
            orderedMap("blipId", "b+root", "newBlipId", "b+new"),
            "Returns a WaveletBlipCreatedEvent payload with the new blip ID."));
    operations.add(
        operation(
            "Wave and conversation",
            "wavelet.addParticipant",
            "Adds a participant to the target wavelet.",
            list("waveId", "waveletId", "participantId"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "participantId", "bob@example.com"),
            orderedMap(
                "blipId", "b+root",
                "participantsAdded", list("bob@example.com"),
                "participantsRemoved", list()),
            "Returns a WaveletParticipantsChangedEvent payload."));
    operations.add(
        operation(
            "Wave and conversation",
            "wavelet.removeParticipant",
            "Removes a participant from the target wavelet.",
            list("waveId", "waveletId", "participantId"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "participantId", "bob@example.com"),
            orderedMap(
                "blipId", "b+root",
                "participantsAdded", list(),
                "participantsRemoved", list("bob@example.com")),
            "Returns a WaveletParticipantsChangedEvent payload."));
    operations.add(
        operation(
            "Wave and conversation",
            "wavelet.setTitle",
            "Sets the visible title of the target wavelet.",
            list("waveId", "waveletId", "waveletTitle"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "waveletTitle", "Project kickoff"),
            orderedMap(),
            "Successful responses are empty."));
    operations.add(
        operation(
            "Wave and conversation",
            "blip.createChild",
            "Creates a reply-thread child blip under the specified parent blip.",
            list("waveId", "waveletId", "blipId", "blipData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+parent",
                "blipData", orderedMap("blipId", "TBD_child_1", "content", "\nChild reply")),
            orderedMap("blipId", "b+root", "newBlipId", "b+child"),
            "Returns a WaveletBlipCreatedEvent payload."));
    operations.add(
        operation(
            "Wave and conversation",
            "blip.continueThread",
            "Appends a new blip to the end of the current thread for the specified blip.",
            list("waveId", "waveletId", "blipId", "blipData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+parent",
                "blipData", orderedMap("blipId", "TBD_thread_1", "content", "\nThread continuation")),
            orderedMap("blipId", "b+root", "newBlipId", "b+thread"),
            "Returns a WaveletBlipCreatedEvent payload."));
    operations.add(
        operation(
            "Wave and conversation",
            "blip.delete",
            "Deletes the specified blip.",
            list("waveId", "waveletId", "blipId"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+old"),
            orderedMap(),
            "Successful responses are empty."));
    operations.add(
        operation(
            "Wave and conversation",
            "document.modify",
            "Applies a DocumentModifyAction to the target blip content.",
            list("waveId", "waveletId", "blipId", "modifyAction"),
            list("range", "index", "modifyQuery"),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+root",
                "modifyAction",
                    orderedMap(
                        "modifyHow", "REPLACE",
                        "values", list("Updated text"),
                        "annotationKey", "",
                        "elements", list(),
                        "bundledAnnotations", list(),
                        "useMarkup", false),
                "range", orderedMap("start", 1, "end", 8)),
            orderedMap(),
            "Use range, index, or modifyQuery to target the document region. Successful responses are empty."));
    operations.add(
        operation(
            "Wave and conversation",
            "document.appendMarkup",
            "Appends markup to the specified blip.",
            list("waveId", "waveletId", "blipId", "content"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+root",
                "content", "<p><b>Marked up text</b></p>"),
            orderedMap(),
            "The content value is parsed as XML/markup. Successful responses are empty."));
    operations.add(
        operation(
            "Wave and conversation",
            "document.appendInlineBlip",
            "Appends a new inline blip on a new line within the specified parent blip.",
            list("waveId", "waveletId", "blipId", "blipData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+root",
                "blipData", orderedMap("blipId", "TBD_inline_1", "content", "\nInline details")),
            orderedMap("blipId", "b+root", "newBlipId", "b+inline"),
            "Returns a WaveletBlipCreatedEvent payload."));
    operations.add(
        operation(
            "Wave and conversation",
            "document.insertInlineBlip",
            "Inserts an inline blip at the provided API text index.",
            list("waveId", "waveletId", "blipId", "index", "blipData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+root",
                "index", 12,
                "blipData", orderedMap("blipId", "TBD_inline_2", "content", "\nInline details")),
            orderedMap("blipId", "b+root", "newBlipId", "b+inline"),
            "The index must be greater than zero. Returns a WaveletBlipCreatedEvent payload."));
    operations.add(
        operation(
            "Wave and conversation",
            "document.insertInlineBlipAfterElement",
            "Inserts an inline blip immediately after the specified element.",
            list("waveId", "waveletId", "blipId", "element", "blipData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "blipId", "b+root",
                "element", orderedMap("type", "IMAGE", "properties", orderedMap("url", "https://example.test/image.png")),
                "blipData", orderedMap("blipId", "TBD_inline_3", "content", "\nElement follow-up")),
            orderedMap("blipId", "b+root", "newBlipId", "b+inline"),
            "The server resolves the actual element location before inserting the inline thread."));
    operations.add(
        operation(
            "Search, profile, and folders",
            "robot.search",
            "Executes a wave search for the authenticated participant.",
            list("query"),
            list("index", "numResults"),
            orderedMap("query", "in:inbox", "index", 0, "numResults", 10),
            orderedMap(
                "searchResults",
                    orderedMap(
                        "query", "in:inbox",
                        "digests",
                            list(
                                orderedMap(
                                    "title", "Project kickoff",
                                    "waveId", "wave://example.com/w+abc123",
                                    "snippet", "Welcome to the wave")))),
            "Index defaults to 0 and numResults defaults to 10 when omitted."));
    operations.add(
        operation(
            "Search, profile, and folders",
            "robot.fetchProfiles",
            "Fetches participant profile records for one or more addresses.",
            list("fetchProfilesRequest"),
            list(),
            orderedMap(
                "fetchProfilesRequest",
                    orderedMap(
                        "participantIds", list("alice@example.com", "bob@example.com"),
                        "language", "en")),
            orderedMap(
                "fetchProfilesResult",
                    orderedMap(
                        "profiles",
                            list(
                                orderedMap(
                                    "address", "alice@example.com",
                                    "name", "Alice Example",
                                    "imageUrl", "/static/images/unknown.jpg",
                                    "profileUrl", "")))),
            "The response wraps profiles inside fetchProfilesResult."));
    operations.add(
        operation(
            "Search, profile, and folders",
            "robot.folderAction",
            "Marks the wave or a specific blip as read or unread for the current user.",
            list("waveId", "waveletId", "modifyHow"),
            list("blipId"),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "modifyHow", "markAsRead",
                "blipId", "b+root"),
            orderedMap(),
            "Supported modifyHow values are markAsRead and markAsUnread."));
    operations.add(
        operation(
            "Export and import",
            "robot.exportSnapshot",
            "Exports a committed wavelet snapshot as raw JSON.",
            list("waveId", "waveletId"),
            list(),
            orderedMap("waveId", "example.com!w+abc123", "waveletId", "example.com!conv+root"),
            orderedMap("rawSnapshot", "{\"waveletId\":\"example.com!conv+root\",\"version\":42}"),
            "The rawSnapshot string contains serialized snapshot JSON."));
    operations.add(
        operation(
            "Export and import",
            "robot.exportDeltas",
            "Exports serialized deltas plus the target hashed version.",
            list("waveId", "waveletId", "fromVersion", "toVersion"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "fromVersion", "BASE64_HASHED_VERSION_START",
                "toVersion", "BASE64_HASHED_VERSION_END"),
            orderedMap(
                "rawDeltas", list("BASE64_DELTA_BYTES"),
                "targetVersion", "BASE64_HASHED_VERSION_TARGET"),
            "Both version parameters are required by the current service implementation."));
    operations.add(
        operation(
            "Export and import",
            "robot.exportAttachment",
            "Exports attachment bytes together with the stored file metadata.",
            list("attachmentId"),
            list(),
            orderedMap("attachmentId", "att+123"),
            orderedMap(
                "attachmentData",
                    orderedMap(
                        "fileName", "design.pdf",
                        "creator", "alice@example.com",
                        "data", "BASE64_ATTACHMENT_BYTES")),
            "The attachment data payload includes fileName, creator, and raw bytes."));
    operations.add(
        operation(
            "Export and import",
            "robot.importDeltas",
            "Imports serialized deltas into the target wavelet and reports the first imported version.",
            list("waveId", "waveletId", "rawDeltas"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "rawDeltas", list("BASE64_DELTA_BYTES")),
            orderedMap("importedFromVersion", 42),
            "The server can return an error if the current wavelet version changes during import."));
    operations.add(
        operation(
            "Export and import",
            "robot.importAttachment",
            "Stores attachment bytes in the target wavelet context.",
            list("waveId", "waveletId", "attachmentId", "attachmentData"),
            list(),
            orderedMap(
                "waveId", "example.com!w+abc123",
                "waveletId", "example.com!conv+root",
                "attachmentId", "att+123",
                "attachmentData",
                    orderedMap(
                        "fileName", "design.pdf",
                        "creator", "alice@example.com",
                        "data", "BASE64_ATTACHMENT_BYTES")),
            orderedMap(),
            "Successful responses are empty."));
    return Collections.unmodifiableList(operations);
  }

  private static OperationDoc operation(
      String group,
      String method,
      String summary,
      List<String> requiredParams,
      List<String> optionalParams,
      Map<String, Object> paramsExample,
      Map<String, Object> responseDataExample,
      String notes) {
    return new OperationDoc(
        group,
        method,
        summary,
        Collections.unmodifiableList(new ArrayList<String>(requiredParams)),
        Collections.unmodifiableList(new ArrayList<String>(optionalParams)),
        Collections.unmodifiableMap(new LinkedHashMap<String, Object>(paramsExample)),
        Collections.unmodifiableMap(new LinkedHashMap<String, Object>(responseDataExample)),
        schemaName(method, "Request"),
        schemaName(method, "Response"),
        notes);
  }

  private static String schemaName(String method, String suffix) {
    String[] parts = method.split("\\.");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    builder.append(suffix);
    return builder.toString();
  }

  private static <T> List<T> list(T... items) {
    return new ArrayList<T>(Arrays.asList(items));
  }

  private static Map<String, Object> orderedMap(Object... entries) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < entries.length; i += 2) {
      map.put((String) entries[i], entries[i + 1]);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static final class OperationDoc {
    private final String group;
    private final String method;
    private final String summary;
    private final List<String> requiredParams;
    private final List<String> optionalParams;
    private final Map<String, Object> paramsExample;
    private final Map<String, Object> responseDataExample;
    private final String requestSchemaName;
    private final String responseSchemaName;
    private final String notes;

    private OperationDoc(
        String group,
        String method,
        String summary,
        List<String> requiredParams,
        List<String> optionalParams,
        Map<String, Object> paramsExample,
        Map<String, Object> responseDataExample,
        String requestSchemaName,
        String responseSchemaName,
        String notes) {
      this.group = group;
      this.method = method;
      this.summary = summary;
      this.requiredParams = requiredParams;
      this.optionalParams = optionalParams;
      this.paramsExample = paramsExample;
      this.responseDataExample = responseDataExample;
      this.requestSchemaName = requestSchemaName;
      this.responseSchemaName = responseSchemaName;
      this.notes = notes;
    }
  }
}
