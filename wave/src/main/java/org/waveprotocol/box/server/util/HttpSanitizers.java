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
package org.waveprotocol.box.server.util;

/**
 * Small helpers to sanitize values used in HTTP response headers to prevent
 * header injection / response splitting.
 */
public final class HttpSanitizers {
  private HttpSanitizers() {}

  /**
   * Removes CR/LF and other ASCII control characters (except TAB) that could
   * break a header line. Returns null if input is null.
   */
  public static String stripHeaderBreakingChars(String value) {
    if (value == null) return null;
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      // allow HT (0x09), visible chars 0x20..0x7E, and extended Unicode;
      // drop CR (0x0D), LF (0x0A) and other C0 controls
      if ((c >= 0x20 && c != 0x7F) || c == '\t' || c > 0x7F) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Validates that a redirect target is a safe relative path for this origin.
   * - must start with a single '/'
   * - must not start with '//'
   * - must not contain CR/LF or backslashes
   */
  public static boolean isSafeRelativeRedirect(String path) {
    if (path == null) return false;
    if (!path.startsWith("/")) return false;
    if (path.startsWith("//")) return false; // protocol-relative external URL
    if (path.indexOf('\\') >= 0) return false;
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '\r' || c == '\n') return false;
    }
    return true;
  }

  /**
   * Percent-encode per RFC 5987 for HTTP header parameters (UTF-8).
   * Allowed: a-z A-Z 0-9 - . _ ~
   */
  public static String rfc5987Encode(String value) {
    if (value == null) return "";
    StringBuilder sb = new StringBuilder(value.length() + 16);
    byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (byte b : bytes) {
      int c = b & 0xFF;
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
          c == '-' || c == '.' || c == '_' || c == '~') {
        sb.append((char) c);
      } else {
        sb.append('%');
        String hex = Integer.toHexString(c).toUpperCase();
        if (hex.length() == 1) sb.append('0');
        sb.append(hex);
      }
    }
    return sb.toString();
  }

  /**
   * Builds a safe Content-Disposition header value for attachment downloads
   * including both filename (ASCII fallback) and filename* (RFC 5987) forms.
   */
  public static String buildContentDispositionAttachment(String filename) {
    String base = sanitizeFileName(filename);
    // Limit sizes to keep headers reasonable; preserve extension when truncating
    base = safeTruncatePreservingExtension(base, 120); // generous bound for encoded form
    // ASCII fallback for quoted filename=, with its own tighter bound
    String ascii = toAsciiFallback(base);
    ascii = safeTruncatePreservingExtension(ascii, 80);
    String encoded = rfc5987Encode(base);
    return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
  }

  // --- internals ---

  /** Best-effort filesystem-agnostic file name sanitizer (header-safe). */
  private static String sanitizeFileName(String value) {
    String v = stripHeaderBreakingChars(value);
    if (v == null) v = "download";
    // Remove quotes/semicolons and TAB; collapse to spaces
    v = v.replace('"', '_').replace(';', '_').replace('\t', ' ');
    // Remove path and Windows-reserved characters: \ / : * ? " < > |
    StringBuilder sb = new StringBuilder(v.length());
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
        sb.append('_');
        continue;
      }
      // Drop Unicode format/control characters (e.g., bidi overrides) to avoid UI spoofing
      int type = Character.getType(c);
      if (type == Character.FORMAT || type == Character.CONTROL) {
        continue;
      }
      sb.append(c);
    }
    v = sb.toString().trim();
    // Remove leading/trailing dots and shrink sequences of spaces
    while (v.startsWith(".")) v = v.substring(1);
    while (v.endsWith(".")) v = v.substring(0, v.length() - 1);
    v = v.replaceAll("\\s+", " ");
    if (v.isEmpty() || v.equals(".") || v.equals("..")) v = "download";
    return v;
  }

  private static String toAsciiFallback(String base) {
    StringBuilder ascii = new StringBuilder(base.length());
    for (int i = 0; i < base.length(); i++) {
      char c = base.charAt(i);
      if (c <= 0x7F) {
        // ensure quotes are not present in ascii fallback
        ascii.append(c == '"' ? '_' : c);
      } else {
        ascii.append('_');
      }
    }
    String out = ascii.toString().trim();
    if (out.isEmpty()) out = "download";
    return out;
  }

  /**
   * Truncate to at most maxLen characters, preserving extension when present.
   * Adds an ellipsis if truncated.
   */
  private static String safeTruncatePreservingExtension(String name, int maxLen) {
    if (name == null) return "download";
    if (name.length() <= maxLen) return name;
    int dot = name.lastIndexOf('.');
    String base = name;
    String ext = "";
    if (dot > 0 && dot < name.length() - 1 && (name.length() - dot) <= 16) {
      base = name.substring(0, dot);
      ext = name.substring(dot); // includes dot
    }
    int room = Math.max(0, maxLen - ext.length());
    if (room <= 3) {
      // not enough space for ellipsis + extension; fallback hard cut
      return name.substring(0, maxLen);
    }
    String head = base.substring(0, Math.min(base.length(), room - 3));
    return head + "..." + ext;
  }

  // -----------------------------
  // Optional hash-first builder
  // -----------------------------

  /** Safe extension allowlist (lowercase, leading dot). */
  private static final java.util.Set<String> SAFE_EXTS = java.util.Set.of(
      ".txt", ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".csv", ".json", ".xml", ".zip");

  /** Returns lowercase extension (including dot) from a sanitized name, or empty if none. */
  private static String extractSafeExt(String sanitized) {
    int dot = sanitized.lastIndexOf('.');
    if (dot > 0 && dot < sanitized.length() - 1) {
      String ext = sanitized.substring(dot).toLowerCase(java.util.Locale.ROOT);
      if (SAFE_EXTS.contains(ext) && ext.length() <= 10) return ext;
    }
    return "";
  }

  private static String sha256Hex(String input, int hexLen) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(dig.length * 2);
      for (byte b : dig) {
        String h = Integer.toHexString(b & 0xFF);
        if (h.length() == 1) sb.append('0');
        sb.append(h);
      }
      String hex = sb.toString();
      int n = Math.max(8, Math.min(hexLen, hex.length()));
      return hex.substring(0, n);
    } catch (Exception e) {
      // Fallback to a simple, deterministic label on failure
      return "deadbeef";
    }
  }

  /**
   * Build a Content-Disposition header using a hash-first filename to avoid
   * leaking user-provided names. Preserves a safe extension where available.
   * Example: attachment; filename="file-1a2b3c4d5e6f7a8b.pdf"; filename*=UTF-8''file-1a2b....pdf
   */
  public static String buildHashedContentDispositionAttachment(String originalName) {
    String sanitized = sanitizeFileName(originalName);
    String ext = extractSafeExt(sanitized);
    String label = "file-" + sha256Hex(sanitized, 32);
    String full = label + ext;
    String ascii = toAsciiFallback(full);
    String encoded = rfc5987Encode(full);
    return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
  }
}
