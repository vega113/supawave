package org.waveprotocol.box.server.authentication.email;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServletRequest;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.wave.util.logging.Log;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public final class AuthEmailService {

  public enum DispatchResult {
    SENT,
    THROTTLED,
    FAILED
  }

  private enum MessageKind {
    CONFIRMATION,
    MAGIC_LINK
  }

  private static final Log LOG = Log.get(AuthEmailService.class);
  private static final long HOURLY_WINDOW_MILLIS = 60L * 60L * 1000L;

  private final AccountStore accountStore;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailProvider mailProvider;
  private final Clock clock;
  private final long cooldownMillis;
  private final int maxPerAddressPerHour;
  private final int maxPerIpPerHour;
  private final String publicBaseUrl;
  private final Map<String, ArrayDeque<Long>> addressDispatches = new ConcurrentHashMap<>();
  private final Map<String, ArrayDeque<Long>> ipDispatches = new ConcurrentHashMap<>();
  private final Map<String, Long> addressCooldowns = new ConcurrentHashMap<>();

  @Inject
  public AuthEmailService(AccountStore accountStore,
                          EmailTokenIssuer emailTokenIssuer,
                          MailProvider mailProvider,
                          Clock clock,
                          Config config) {
    this.accountStore = accountStore;
    this.emailTokenIssuer = emailTokenIssuer;
    this.mailProvider = mailProvider;
    this.clock = clock;
    this.cooldownMillis = readCooldownMillis(config);
    this.maxPerAddressPerHour = readLimit(config, "core.auth_email_send_max_per_address_per_hour", 5);
    this.maxPerIpPerHour = readLimit(config, "core.auth_email_send_max_per_ip_per_hour", 20);
    this.publicBaseUrl = PublicBaseUrlResolver.resolve(config);
  }

  public DispatchResult sendConfirmationEmail(HttpServletRequest request, HumanAccountData account) {
    return send(request, account, MessageKind.CONFIRMATION);
  }

  public DispatchResult sendMagicLinkEmail(HttpServletRequest request, HumanAccountData account) {
    return send(request, account, MessageKind.MAGIC_LINK);
  }

  public boolean confirmEmailOwnership(HumanAccountData account) throws PersistenceException {
    boolean changed = !account.isEmailConfirmed();
    if (changed) {
      account.setEmailConfirmed(true);
      accountStore.putAccount(account);
    }
    return changed;
  }

  private DispatchResult send(HttpServletRequest request,
                              HumanAccountData account,
                              MessageKind kind) {
    String recipient = resolveRecipient(account);
    String addressKey = normalizeKey(recipient);
    String ipKey = resolveClientKey(request);
    if (!tryAcquire(addressKey, ipKey)) {
      LOG.info("Auth email throttled for " + addressKey + " from " + ipKey);
      return DispatchResult.THROTTLED;
    }

    try {
      String token = issueToken(account, kind);
      String path = buildPath(kind, token);
      String url = buildAbsoluteUrl(path);
      String subject = buildSubject(kind);
      String body = buildBody(account, url, kind);
      mailProvider.sendEmail(recipient, subject, body);
      return DispatchResult.SENT;
    } catch (MailException e) {
      LOG.severe("Failed to send auth email for " + account.getId().getAddress(), e);
      return DispatchResult.FAILED;
    }
  }

  private synchronized boolean tryAcquire(String addressKey, String ipKey) {
    long now = clock.millis();
    pruneExpiredState(now);

    Long lastSentAt = addressCooldowns.get(addressKey);
    if (lastSentAt != null) {
      return false;
    }

    ArrayDeque<Long> addressEvents =
        addressDispatches.computeIfAbsent(addressKey, ignored -> new ArrayDeque<>());
    ArrayDeque<Long> ipEvents =
        ipDispatches.computeIfAbsent(ipKey, ignored -> new ArrayDeque<>());

    prune(addressEvents, now);
    prune(ipEvents, now);

    if (addressEvents.size() >= maxPerAddressPerHour || ipEvents.size() >= maxPerIpPerHour) {
      return false;
    }

    addressEvents.addLast(now);
    ipEvents.addLast(now);
    addressCooldowns.put(addressKey, now);
    return true;
  }

  private void pruneExpiredState(long now) {
    pruneDispatchMap(addressDispatches, now);
    pruneDispatchMap(ipDispatches, now);
    pruneCooldowns(now);
  }

  private void pruneDispatchMap(Map<String, ArrayDeque<Long>> dispatches, long now) {
    dispatches.entrySet().removeIf(entry -> {
      prune(entry.getValue(), now);
      return entry.getValue().isEmpty();
    });
  }

  private void pruneCooldowns(long now) {
    addressCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);
  }

  private void prune(ArrayDeque<Long> events, long now) {
    long cutoff = now - HOURLY_WINDOW_MILLIS;
    while (!events.isEmpty() && events.peekFirst() <= cutoff) {
      events.removeFirst();
    }
  }

  private String resolveRecipient(HumanAccountData account) {
    String email = account.getEmail();
    if (email != null && !email.trim().isEmpty()) {
      return email.trim().toLowerCase(Locale.ROOT);
    }
    return account.getId().getAddress().toLowerCase(Locale.ROOT);
  }

  private String resolveClientKey(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr.trim();
  }

  private String issueToken(HumanAccountData account, MessageKind kind) {
    return switch (kind) {
      case CONFIRMATION -> emailTokenIssuer.issueEmailConfirmToken(account.getId());
      case MAGIC_LINK -> emailTokenIssuer.issueMagicLinkToken(account.getId());
    };
  }

  private String buildPath(MessageKind kind, String token) {
    return switch (kind) {
      case CONFIRMATION -> "/auth/confirm-email?token=" + token;
      case MAGIC_LINK -> "/auth/magic-link?token=" + token;
    };
  }

  private String buildSubject(MessageKind kind) {
    return switch (kind) {
      case CONFIRMATION -> "Confirm your Wave account";
      case MAGIC_LINK -> "Login Link - Wave";
    };
  }

  private String buildBody(HumanAccountData account, String url, MessageKind kind) {
    String address = HtmlRenderer.escapeHtml(account.getId().getAddress());
    String safeUrl = HtmlRenderer.escapeHtml(url);
    return switch (kind) {
      case CONFIRMATION -> "<html><body>"
          + "<h2>Confirm Your Account</h2>"
          + "<p>Welcome to Wave! Please confirm your account: <b>" + address + "</b></p>"
          + "<p>Click the link below to activate your account:</p>"
          + "<p><a href=\"" + safeUrl + "\">Confirm Email</a></p>"
          + "<p>If you did not register, you can safely ignore this email.</p>"
          + "<p>This link will expire in 24 hours.</p>"
          + "</body></html>";
      case MAGIC_LINK -> "<html><body>"
          + "<h2>Login Link</h2>"
          + "<p>A login link was requested for your Wave account: <b>" + address + "</b></p>"
          + "<p>Click the link below to sign in:</p>"
          + "<p><a href=\"" + safeUrl + "\">Sign In</a></p>"
          + "<p>If you did not request this, you can safely ignore this email.</p>"
          + "<p>This link will expire in 10 minutes.</p>"
          + "</body></html>";
    };
  }

  private String buildAbsoluteUrl(String path) {
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return publicBaseUrl + normalizedPath;
  }

  private String normalizeKey(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private long readCooldownMillis(Config config) {
    long seconds = config.hasPath("core.auth_email_send_cooldown_seconds")
        ? config.getLong("core.auth_email_send_cooldown_seconds") : 300L;
    return Math.max(0L, seconds) * 1000L;
  }

  private int readLimit(Config config, String path, int fallback) {
    int value = config.hasPath(path) ? config.getInt(path) : fallback;
    return Math.max(1, value);
  }

}
