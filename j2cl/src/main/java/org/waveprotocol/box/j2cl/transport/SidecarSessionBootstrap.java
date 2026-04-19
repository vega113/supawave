package org.waveprotocol.box.j2cl.transport;

import java.util.Map;

public final class SidecarSessionBootstrap {
  private final String address;

  public SidecarSessionBootstrap(String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  public static SidecarSessionBootstrap fromRootHtml(String html) {
    if (html == null) {
      throw new IllegalArgumentException("Root HTML must not be null");
    }
    int sessionMarker = html.indexOf("__session");
    if (sessionMarker < 0) {
      throw new IllegalArgumentException("Root page did not expose window.__session");
    }
    int assignIdx = sessionMarker + "__session".length();
    while (assignIdx < html.length() && Character.isWhitespace(html.charAt(assignIdx))) {
      assignIdx++;
    }
    if (assignIdx >= html.length() || html.charAt(assignIdx) != '=') {
      throw new IllegalArgumentException("Unable to locate __session assignment operator");
    }
    assignIdx++;
    while (assignIdx < html.length() && Character.isWhitespace(html.charAt(assignIdx))) {
      assignIdx++;
    }
    if (assignIdx >= html.length() || html.charAt(assignIdx) != '{') {
      throw new IllegalArgumentException("Unable to locate __session JSON object");
    }
    int objectStart = assignIdx;
    int objectEnd = findMatchingBrace(html, objectStart);
    Map<String, Object> session =
        SidecarTransportCodec.parseJsonObject(html.substring(objectStart, objectEnd + 1));
    Object addressValue = session.get("address");
    if (!(addressValue instanceof String)) {
      throw new IllegalArgumentException("Session bootstrap did not include an address");
    }
    String address = ((String) addressValue).trim();
    if ("null".equals(address) || address.isEmpty()) {
      throw new IllegalArgumentException("Session bootstrap did not include an address");
    }
    return new SidecarSessionBootstrap(address);
  }

  private static int findMatchingBrace(String html, int objectStart) {
    boolean inString = false;
    boolean escaped = false;
    int depth = 0;
    for (int i = objectStart; i < html.length(); i++) {
      char c = html.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"') {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    throw new IllegalArgumentException("Unterminated __session JSON object");
  }
}
