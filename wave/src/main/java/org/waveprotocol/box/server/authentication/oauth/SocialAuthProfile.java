package org.waveprotocol.box.server.authentication.oauth;

public final class SocialAuthProfile {
  private final String provider;
  private final String subject;
  private final String email;
  private final String displayName;
  private final boolean emailVerified;

  public SocialAuthProfile(String provider, String subject, String email, String displayName,
      boolean emailVerified) {
    this.provider = provider;
    this.subject = subject;
    this.email = email;
    this.displayName = displayName;
    this.emailVerified = emailVerified;
  }

  public String getProvider() {
    return provider;
  }

  public String getSubject() {
    return subject;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }
}
