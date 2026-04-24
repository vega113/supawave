package org.waveprotocol.box.server.authentication.oauth;

public class SocialAuthException extends Exception {
  public SocialAuthException(String message) {
    super(message);
  }

  public SocialAuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
