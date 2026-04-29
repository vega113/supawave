// G-PORT-1 (#1110) — fresh test-user fixture for the parity harness.
//
// Per project memory `feedback_local_registration_before_login_testing`:
// never assume a user like `vega` exists locally. Every run registers a
// fresh account and signs in.
//
// Server flow (UserRegistrationServlet.java:120-135 +
// AuthenticationServlet):
//   POST /auth/register on direct success ⇒ 302 to
//     /auth/signin?registered=1
//   POST /auth/register with email confirmation enabled ⇒ 302 to
//     /auth/register?check-email=1   (test bails — needs out-of-band
//                                     email verification)
//   POST /auth/signin on success ⇒ 302 to / (or the saved entry URL)
//
// We therefore drive register and sign-in as two explicit steps.
import { Page, expect } from "@playwright/test";
import { randomBytes } from "node:crypto";

export interface TestCredentials {
  address: string;
  email: string;
  password: string;
}

export function freshCredentials(prefix: string = "qag1"): TestCredentials {
  const stamp = Date.now().toString(36) + randomBytes(2).toString("hex");
  const address = `${prefix}${stamp}`;
  return {
    address,
    email: `${address}@local.net`,
    password: `Pass${stamp}!`
  };
}

async function fillById(page: Page, id: string, value: string): Promise<void> {
  await page.locator(`#${id}`).fill(value);
}

/**
 * Drives `page` through `/auth/register` and then `/auth/signin`.
 * Throws a clear diagnostic if the local server has email confirmation
 * enabled (we cannot confirm the account out-of-band from a smoke test).
 */
export async function registerAndSignIn(
  page: Page,
  baseURL: string,
  creds: TestCredentials
): Promise<void> {
  // Step 1: register.
  await page.goto(`${baseURL}/auth/register`, { waitUntil: "domcontentloaded" });
  await fillById(page, "address", creds.address);
  await fillById(page, "email", creds.email);
  await fillById(page, "password", creds.password);
  await fillById(page, "verifypass", creds.password);
  await Promise.all([
    page.waitForURL(/\/auth\/(signin|register\?check-email)/, { waitUntil: "domcontentloaded" }),
    page.locator("input.btn-primary").click()
  ]);

  const afterRegisterUrl = page.url();

  if (/[?&]check-email=1\b/.test(afterRegisterUrl)) {
    throw new Error(
      "registerAndSignIn: server returned /auth/register?check-email=1 — " +
        "the local server has email confirmation enabled. Disable " +
        "`core.email_confirmation_enabled` in application.conf or seed a " +
        "verified user before running this test."
    );
  }

  if (/\/auth\/register\b/.test(afterRegisterUrl) && !/registered=1/.test(afterRegisterUrl)) {
    // Server re-rendered the register form with an error. Surface the
    // visible error message so debugging the smoke test is fast.
    const errorText = (await page.locator("body").innerText()).slice(0, 600);
    throw new Error(
      `registerAndSignIn: registration did not redirect away from /auth/register.\n` +
        `URL: ${afterRegisterUrl}\nPage text:\n${errorText}`
    );
  }

  // Step 2: sign in. The server PRGs to /auth/signin?registered=1 on
  // direct success; we explicitly re-fill the form so the test does
  // not depend on an autofilled address.
  if (!/\/auth\/signin\b/.test(page.url())) {
    await page.goto(`${baseURL}/auth/signin`, { waitUntil: "domcontentloaded" });
  }
  await fillById(page, "address", creds.address);
  await fillById(page, "password", creds.password);
  await Promise.all([
    page.waitForURL(url => !/\/auth\/signin/.test(url.toString()), { waitUntil: "domcontentloaded" }),
    page
      .locator('input.btn-primary, button[type="submit"]')
      .first()
      .click()
  ]);

  const afterSignInUrl = page.url();
  expect(
    /\/auth\/signin/.test(afterSignInUrl),
    `registerAndSignIn: sign-in did not redirect away from /auth/signin (still at ${afterSignInUrl})`
  ).toBe(false);
}
