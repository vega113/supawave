package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HtmlRendererRobotRegistrationTest {
  @Test
  public void registrationPageExplainsBotSuffixAndOptionalCallbackUrl() {
    String html = HtmlRenderer.renderRobotRegistrationPage("example.com", "", "",
        "registration-xsrf");

    assertTrue(html.contains("must end with -bot"));
    assertTrue(html.contains("optional"));
    assertTrue(html.contains("later"));
    assertTrue(html.contains("Current API Token Secret"));
    assertTrue(html.contains("name=\"token\""));
  }

  @Test
  public void successPageExplainsCallbackUrlCanBeAddedLater() {
    String html = HtmlRenderer.renderRobotRegistrationSuccessPage(
        "helper-bot@example.com", "secret-token", "");

    assertTrue(html.contains("callback URL"));
    assertTrue(html.contains("later"));
  }
}
