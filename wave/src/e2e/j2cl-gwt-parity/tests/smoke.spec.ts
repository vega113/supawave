// G-PORT-1 (#1110) — smoke test for the J2CL <-> GWT parity harness.
// One test per run that:
//   1. Registers a fresh user (per memory:
//      feedback_local_registration_before_login_testing).
//   2. Signs in.
//   3. Asserts the J2CL shell renders at /?view=j2cl-root.
//   4. Asserts the GWT shell renders at /?view=gwt.
//
// Hard rule from issue #1110 / task brief: do NOT skip an assertion to
// make the smoke pass. If the view is genuinely broken, file a blocker
// comment on #1110 and let the test fail until fixed.
import { test, expect } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

test.describe("G-PORT-1 parity harness smoke", () => {
  test("both views bootstrap for a freshly registered user", async ({ page }) => {
    const creds = freshCredentials();
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });

    await registerAndSignIn(page, BASE_URL, creds);

    // J2CL view must render the signed-in shell.
    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();

    // GWT view must render the GWT bootstrap and host div.
    const gwt = new GwtPage(page, BASE_URL);
    await gwt.goto("/");
    await gwt.assertInboxLoaded();

    // Sanity: the auth cookie persists across both view switches, i.e.
    // we are still signed in after toggling views (no auth bounce).
    expect(page.url()).not.toMatch(/\/auth\/signin/);
  });
});
