package org.waveprotocol.box.server.account;

import java.util.Locale;
import java.util.Objects;

/** Provider identity linked to a human Wave account. */
public final class SocialIdentity {
  private final String provider;
  private final String subject;
  private final String email;
  private final String displayName;
  private final long linkedAtMillis;

  public SocialIdentity(String provider, String subject, String email, String displayName,
      long linkedAtMillis) {
    this.provider = requireText(provider, "provider").toLowerCase(Locale.ROOT);
    this.subject = requireText(subject, "subject");
    this.email = normalizeOptional(email);
    this.displayName = normalizeOptional(displayName);
    this.linkedAtMillis = linkedAtMillis;
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

  public long getLinkedAtMillis() {
    return linkedAtMillis;
  }

  public boolean matches(String provider, String subject) {
    return this.provider.equals(requireText(provider, "provider").toLowerCase(Locale.ROOT))
        && this.subject.equals(requireText(subject, "subject"));
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SocialIdentity)) {
      return false;
    }
    SocialIdentity other = (SocialIdentity) obj;
    return linkedAtMillis == other.linkedAtMillis
        && Objects.equals(provider, other.provider)
        && Objects.equals(subject, other.subject)
        && Objects.equals(email, other.email)
        && Objects.equals(displayName, other.displayName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provider, subject, email, displayName, linkedAtMillis);
  }
}
