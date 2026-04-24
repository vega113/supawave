package org.waveprotocol.box.server.authentication.oauth;

import java.util.Map;

public interface SocialAuthHttpClient {
  String postForm(String url, Map<String, String> form, Map<String, String> headers)
      throws SocialAuthException;

  String get(String url, Map<String, String> headers) throws SocialAuthException;
}
