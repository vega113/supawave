package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

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
