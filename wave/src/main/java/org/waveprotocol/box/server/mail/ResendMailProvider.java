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
package org.waveprotocol.box.server.mail;

import com.typesafe.config.Config;
import org.waveprotocol.wave.util.logging.Log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link MailProvider} implementation that sends email via the
 * <a href="https://resend.com/">Resend</a> API.
 *
 * <p>Requires {@code core.resend_api_key} and {@code core.email_from_address}
 * to be set in the server configuration.
 */
public class ResendMailProvider implements MailProvider {

  private static final Log LOG = Log.get(ResendMailProvider.class);
  private static final String RESEND_API_URL = "https://api.resend.com/emails";

  private final String apiKey;
  private final String fromAddress;
  private final HttpClient httpClient;

  public ResendMailProvider(Config config) {
    this.apiKey = config.getString("core.resend_api_key");
    this.fromAddress = config.getString("core.email_from_address");
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalStateException(
          "core.resend_api_key must be configured when using ResendMailProvider");
    }
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  @Override
  public void sendEmail(String to, String subject, String htmlBody) throws MailException {
    // Build JSON payload manually to avoid extra dependencies.
    String jsonPayload = "{\"from\":" + jsonString(fromAddress)
        + ",\"to\":[" + jsonString(to) + "]"
        + ",\"subject\":" + jsonString(subject)
        + ",\"html\":" + jsonString(htmlBody) + "}";

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(RESEND_API_URL))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
        .timeout(Duration.ofSeconds(30))
        .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warning("Resend API returned status " + response.statusCode()
            + ": " + response.body());
        throw new MailException(
            "Resend API returned status " + response.statusCode());
      }
      LOG.info("Email sent via Resend to " + to + " (subject: " + subject + ")");
    } catch (MailException e) {
      throw e;
    } catch (Exception e) {
      throw new MailException("Failed to send email via Resend", e);
    }
  }

  /** Escapes a string for inclusion in a JSON value. */
  private static String jsonString(String s) {
    if (s == null) return "null";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n");  break;
        case '\r': sb.append("\\r");  break;
        case '\t': sb.append("\\t");  break;
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
}
