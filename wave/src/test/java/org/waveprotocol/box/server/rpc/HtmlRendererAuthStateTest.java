package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import java.util.List;

public final class HtmlRendererAuthStateTest {
  @Test
  public void registeredSignInPageShowsSuccessBanner() {
    String html = HtmlRenderer.renderAuthenticationPage(
        "example.com",
        "Account created! Sign in to get started.",
        AuthenticationServlet.RESPONSE_STATUS_SUCCESS,
        false,
        "",
        true,
        true);

    assertTrue(html.contains("Account created"));
    assertTrue(html.contains("id=\"successBanner\""));
    assertTrue(html.contains("role=\"status\""));
    assertTrue(html.contains("aria-live=\"polite\""));
    assertTrue(html.contains("Forgot password?"));
    assertTrue(html.contains("Login with email link"));
  }

  @Test
  public void signInPageOmitsSocialButtonsByDefault() {
    String html = HtmlRenderer.renderAuthenticationPage(
        "example.com",
        "",
        AuthenticationServlet.RESPONSE_STATUS_NONE,
        false,
        "",
        true,
        false);

    assertFalse(html.contains("/auth/social/google"));
    assertFalse(html.contains("/auth/social/github"));
  }

  @Test
  public void signInPageShowsConfiguredSocialButtons() {
    String html = HtmlRenderer.renderAuthenticationPage(
        "example.com",
        "",
        AuthenticationServlet.RESPONSE_STATUS_NONE,
        false,
        "",
        true,
        false,
        List.of(
            new HtmlRenderer.SocialProviderLink("Google", "/auth/social/google"),
            new HtmlRenderer.SocialProviderLink("GitHub", "/auth/social/github")));

    assertTrue(html.contains("Continue with Google"));
    assertTrue(html.contains("href=\"/auth/social/google\""));
    assertTrue(html.contains("Continue with GitHub"));
    assertTrue(html.contains("href=\"/auth/social/github\""));
  }

  @Test
  public void signInPageFiltersBlankSocialButtons() {
    String html = HtmlRenderer.renderAuthenticationPage(
        "example.com",
        "",
        AuthenticationServlet.RESPONSE_STATUS_NONE,
        false,
        "",
        true,
        false,
        List.of(
            new HtmlRenderer.SocialProviderLink("", "/auth/social/google"),
            new HtmlRenderer.SocialProviderLink("GitHub", "")));

    assertFalse(html.contains("<div class=\"social-auth-options\""));
    assertFalse(html.contains("Continue with"));
  }

  @Test
  public void socialUsernamePageRequiresSupaWaveUsername() {
    String html = HtmlRenderer.renderSocialUsernamePage(
        "example.com",
        "Google",
        "social@example.com",
        "",
        AuthenticationServlet.RESPONSE_STATUS_NONE,
        "csrf-123",
        "");

    assertTrue(html.contains("Choose your SupaWave username"));
    assertTrue(html.contains("social@example.com"));
    assertTrue(html.contains("name=\"address\""));
    assertTrue(html.contains("name=\"csrf\" value=\"csrf-123\""));
  }

  @Test
  public void socialFailurePageHonorsEmailAuthFlags() {
    String html = HtmlRenderer.renderSocialFailurePage(
        "example.com",
        "Social sign-in could not be completed.",
        "",
        false,
        false,
        true);

    assertTrue(html.contains("Social sign-in could not be completed."));
    assertFalse(html.contains("Forgot password?"));
    assertTrue(html.contains("Login with email link"));
  }

  @Test
  public void socialFailurePageHonorsDisabledLoginPage() {
    String html = HtmlRenderer.renderSocialFailurePage(
        "example.com",
        "Social sign-in could not be completed.",
        "",
        true,
        true,
        true);

    assertTrue(html.contains("HTTP authentication disabled by administrator."));
    assertFalse(html.contains("id=\"wiab_loginform\""));
    assertFalse(html.contains("Forgot password?"));
    assertFalse(html.contains("Login with email link"));
  }

  @Test
  public void checkEmailPageShowsInboxGuidance() {
    String html = HtmlRenderer.renderCheckEmailPage(
        "example.com",
        "");

    assertTrue(html.contains("Check your inbox"));
    assertTrue(html.contains("Activate your account"));
    assertTrue(html.contains("Already confirmed?"));
  }

  @Test
  public void activationRequiredSignInPageShowsActionRequiredMessage() {
    String html = HtmlRenderer.renderActivationRequiredAuthenticationPage(
        "example.com",
        "We sent a fresh activation email.",
        "",
        false,
        true,
        true);

    assertTrue(html.contains("Check your inbox"));
    assertTrue(html.contains("We sent a fresh activation email."));
    assertTrue(html.contains("Forgot password?"));
    assertTrue(html.contains("Login with email link"));
  }

  @Test
  public void activationRequiredSignInPageHidesLoginFormWhenDisabled() {
    String html = HtmlRenderer.renderActivationRequiredAuthenticationPage(
        "example.com",
        "We sent a fresh activation email.",
        "",
        true,
        true,
        true);

    assertTrue(html.contains("HTTP authentication disabled by administrator."));
    assertFalse(html.contains("id=\"wiab_loginform\""));
    assertFalse(html.contains("Forgot password?"));
    assertFalse(html.contains("Login with email link"));
  }

  @Test
  public void emailConfirmationPageSuccessFeelsReadyToProceed() {
    String html = HtmlRenderer.renderEmailConfirmationPage(
        "example.com",
        "Email confirmed successfully! You can now sign in.",
        AuthenticationServlet.RESPONSE_STATUS_SUCCESS,
        "");

    assertTrue(html.contains("Ready to sign in"));
    assertTrue(html.contains("Go to Sign In"));
  }
}
