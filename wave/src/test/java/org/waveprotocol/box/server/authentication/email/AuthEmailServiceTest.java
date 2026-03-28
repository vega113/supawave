package org.waveprotocol.box.server.authentication.email;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public class AuthEmailServiceTest extends TestCase {
  private static final ParticipantId USER = ParticipantId.ofUnsafe("frodo@example.com");

  @Mock private AccountStore accountStore;
  @Mock private EmailTokenIssuer emailTokenIssuer;
  @Mock private MailProvider mailProvider;
  @Mock private HttpServletRequest req;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(req.getScheme()).thenReturn("https");
    when(req.getServerName()).thenReturn("wave.example.com");
    when(req.getServerPort()).thenReturn(443);
    when(req.getRemoteAddr()).thenReturn("198.51.100.11");
  }

  public void testConfirmationEmailsAreThrottledPerRecipient() throws Exception {
    Config config = createConfig(300, 5, 20, "https://wave.example.com");
    AuthEmailService service = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);

    HumanAccountDataImpl account = createAccount("frodo@example.com");
    when(emailTokenIssuer.issueEmailConfirmToken(USER)).thenReturn("confirm-token");

    AuthEmailService.DispatchResult first = service.sendConfirmationEmail(req, account);
    AuthEmailService.DispatchResult second = service.sendConfirmationEmail(req, account);

    assertEquals(AuthEmailService.DispatchResult.SENT, first);
    assertEquals(AuthEmailService.DispatchResult.THROTTLED, second);
    verify(mailProvider).sendEmail(eq("frodo@example.com"),
        eq("Confirm your Wave account"), contains("confirm-token"));
  }

  public void testConfirmationEmailsUseConfiguredPublicUrl() throws Exception {
    Config config = createConfig(300, 5, 20, "https://wave.example.com");
    AuthEmailService service = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);

    HumanAccountDataImpl account = createAccount("frodo@example.com");
    when(emailTokenIssuer.issueEmailConfirmToken(USER)).thenReturn("confirm-token");
    when(req.getScheme()).thenReturn("http");
    when(req.getServerName()).thenReturn("evil.example.net");
    when(req.getServerPort()).thenReturn(8080);

    AuthEmailService.DispatchResult result = service.sendConfirmationEmail(req, account);

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    assertEquals(AuthEmailService.DispatchResult.SENT, result);
    verify(mailProvider).sendEmail(eq("frodo@example.com"),
        eq("Confirm your Wave account"), bodyCaptor.capture());
    assertTrue(bodyCaptor.getValue().contains("https://wave.example.com/auth/confirm-email?token=confirm-token"));
    assertFalse(bodyCaptor.getValue().contains("evil.example.net"));
  }

  public void testConfirmationEmailsUsePublicFrontendAddressFallback() throws Exception {
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
        .put("core.auth_email_send_cooldown_seconds", 300)
        .put("core.auth_email_send_max_per_address_per_hour", 5)
        .put("core.auth_email_send_max_per_ip_per_hour", 20)
        .put("core.http_frontend_public_address", "wave.public.example")
        .put("security.enable_ssl", true)
        .build());
    AuthEmailService service = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);

    HumanAccountDataImpl account = createAccount("frodo@example.com");
    when(emailTokenIssuer.issueEmailConfirmToken(USER)).thenReturn("confirm-token");
    when(req.getScheme()).thenReturn("http");
    when(req.getServerName()).thenReturn("localhost");
    when(req.getServerPort()).thenReturn(9898);

    AuthEmailService.DispatchResult result = service.sendConfirmationEmail(req, account);

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    assertEquals(AuthEmailService.DispatchResult.SENT, result);
    verify(mailProvider).sendEmail(eq("frodo@example.com"),
        eq("Confirm your Wave account"), bodyCaptor.capture());
    assertTrue(bodyCaptor.getValue().contains("https://wave.public.example/auth/confirm-email?token=confirm-token"));
    assertFalse(bodyCaptor.getValue().contains("localhost:9898"));
  }

  public void testIpThrottleUsesRemoteAddressInsteadOfForwardedFor() throws Exception {
    Config config = createConfig(0, 5, 1, "https://wave.example.com");
    AuthEmailService service = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);

    HumanAccountDataImpl firstAccount = createAccount("frodo@example.com");
    HumanAccountDataImpl secondAccount = createAccount("sam@example.com");
    when(emailTokenIssuer.issueEmailConfirmToken(firstAccount.getId())).thenReturn("confirm-token-1");
    when(emailTokenIssuer.issueEmailConfirmToken(secondAccount.getId())).thenReturn("confirm-token-2");
    when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5", "203.0.113.6");

    AuthEmailService.DispatchResult first = service.sendConfirmationEmail(req, firstAccount);
    AuthEmailService.DispatchResult second = service.sendConfirmationEmail(req, secondAccount);

    assertEquals(AuthEmailService.DispatchResult.SENT, first);
    assertEquals(AuthEmailService.DispatchResult.THROTTLED, second);
    verify(mailProvider, times(1)).sendEmail(eq("frodo@example.com"),
        eq("Confirm your Wave account"), contains("confirm-token-1"));
  }

  public void testExpiredThrottleStateIsEvictedAcrossKeys() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC);
    Config config = createConfig(0, 5, 20, "https://wave.example.com");
    AuthEmailService service = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        clock,
        config);

    HumanAccountDataImpl firstAccount = createAccount("frodo@example.com");
    HumanAccountDataImpl secondAccount = createAccount("sam@example.com");
    when(emailTokenIssuer.issueEmailConfirmToken(firstAccount.getId())).thenReturn("confirm-token-1");
    when(emailTokenIssuer.issueEmailConfirmToken(secondAccount.getId())).thenReturn("confirm-token-2");
    when(req.getRemoteAddr()).thenReturn("198.51.100.11", "198.51.100.12");

    AuthEmailService.DispatchResult first = service.sendConfirmationEmail(req, firstAccount);
    clock.advanceSeconds(61 * 60);
    AuthEmailService.DispatchResult second = service.sendConfirmationEmail(req, secondAccount);

    assertEquals(AuthEmailService.DispatchResult.SENT, first);
    assertEquals(AuthEmailService.DispatchResult.SENT, second);
    assertEquals(1, mapSize(service, "addressDispatches"));
    assertEquals(1, mapSize(service, "ipDispatches"));
    assertEquals(1, mapSize(service, "addressCooldowns"));
  }

  private Config createConfig(int cooldownSeconds,
                              int maxPerAddressPerHour,
                              int maxPerIpPerHour,
                              String publicUrl) {
    return ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
        .put("core.auth_email_send_cooldown_seconds", cooldownSeconds)
        .put("core.auth_email_send_max_per_address_per_hour", maxPerAddressPerHour)
        .put("core.auth_email_send_max_per_ip_per_hour", maxPerIpPerHour)
        .put("core.public_url", publicUrl)
        .build());
  }

  private HumanAccountDataImpl createAccount(String address) {
    ParticipantId participantId = ParticipantId.ofUnsafe(address);
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(participantId, new PasswordDigest("password".toCharArray()));
    account.setEmail(address);
    return account;
  }

  private int mapSize(AuthEmailService service, String fieldName) throws Exception {
    Field field = AuthEmailService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    Map<?, ?> map = (Map<?, ?>) field.get(service);
    return map.size();
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private final ZoneId zoneId;

    private MutableClock(Instant instant, ZoneId zoneId) {
      this.instant = instant;
      this.zoneId = zoneId;
    }

    @Override
    public ZoneId getZone() {
      return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }
  }
}
