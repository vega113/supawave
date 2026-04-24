package org.waveprotocol.box.server.authentication.oauth;

import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class DefaultSocialAuthHttpClient implements SocialAuthHttpClient {
  private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
  private final int timeoutMillis;

  @Inject
  public DefaultSocialAuthHttpClient(SocialAuthConfig config) {
    this.timeoutMillis = config.timeoutSeconds() * 1000;
  }

  @Override
  public String postForm(String url, Map<String, String> form, Map<String, String> headers)
      throws SocialAuthException {
    String body = encodeForm(form);
    return request("POST", url, headers, body);
  }

  @Override
  public String get(String url, Map<String, String> headers) throws SocialAuthException {
    return request("GET", url, headers, null);
  }

  private String request(String method, String url, Map<String, String> headers, String body)
      throws SocialAuthException {
    HttpURLConnection connection = null;
    try {
      URI uri = URI.create(url);
      if (!"https".equalsIgnoreCase(uri.getScheme())) {
        throw new SocialAuthException("Provider request must use HTTPS");
      }
      connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(timeoutMillis);
      connection.setReadTimeout(timeoutMillis);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Accept", "application/json");
      if (headers != null) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
      }
      if (body != null) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
        try (OutputStream out = connection.getOutputStream()) {
          out.write(bytes);
        }
      }
      int status = connection.getResponseCode();
      if (status >= 300 && status < 400) {
        throw new SocialAuthException("Provider redirected unexpectedly");
      }
      InputStream stream = status >= 200 && status < 300
          ? connection.getInputStream()
          : connection.getErrorStream();
      String response;
      try (InputStream responseStream = stream) {
        response = readBounded(responseStream);
      }
      if (status < 200 || status >= 300) {
        throw new SocialAuthException("Provider request failed");
      }
      return response;
    } catch (SocialAuthException e) {
      throw e;
    } catch (Exception e) {
      throw new SocialAuthException("Provider request failed", e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String encodeForm(Map<String, String> form) {
    StringBuilder body = new StringBuilder();
    for (Map.Entry<String, String> entry : form.entrySet()) {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(urlEncode(entry.getKey())).append('=')
          .append(urlEncode(entry.getValue()));
    }
    return body.toString();
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String readBounded(InputStream input) throws Exception {
    if (input == null) {
      return "";
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) != -1) {
      total += read;
      if (total > MAX_RESPONSE_BYTES) {
        throw new SocialAuthException("Provider response too large");
      }
      output.write(buffer, 0, read);
    }
    return output.toString(StandardCharsets.UTF_8);
  }
}
