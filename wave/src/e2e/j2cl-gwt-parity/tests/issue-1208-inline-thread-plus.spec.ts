import { test, expect, Page } from "@playwright/test";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";
import { GwtPage } from "../pages/GwtPage";
import { J2clPage } from "../pages/J2clPage";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

async function blipIds(page: Page): Promise<string[]> {
  return await page
    .locator("wave-blip[data-blip-id], [kind='b'][data-blip-id]")
    .evaluateAll(els =>
      els
        .map(el => el.getAttribute("data-blip-id") || "")
        .filter(id => id.length > 0)
    );
}

async function waitForBlipCount(page: Page, n: number): Promise<void> {
  await expect
    .poll(async () => (await blipIds(page)).length, {
      timeout: 30_000,
      message: `expected at least ${n} blips`
    })
    .toBeGreaterThanOrEqual(n);
}

test.describe("Issue #1208 J2CL inline reply affordance", () => {
  test.setTimeout(180_000);

  test("parent-owned inline replies use the parent chevron without a gutter plus", async ({
    page
  }) => {
    const creds = freshCredentials("i1208");
    await registerAndSignIn(page, BASE_URL, creds);

    const gwt = new GwtPage(page, BASE_URL);
    await gwt.goto("/");
    await gwt.assertInboxLoaded();
    await page.waitForLoadState("networkidle", { timeout: 30_000 });

    await expect(gwt.newWaveAffordance()).toBeVisible({ timeout: 15_000 });
    await gwt.newWaveAffordance().click();
    await waitForBlipCount(page, 1);
    const waveId = await gwt.readWaveIdFromHash();
    await gwt.typeIntoBlipDocument("Issue 1208 root");

    const rootBlipId = (await blipIds(page))[0];
    await gwt.clickReplyOnBlip(rootBlipId);
    await waitForBlipCount(page, 2);
    await gwt.typeIntoBlipDocument("Issue 1208 inline parent");

    const replyParentBlipId = (await blipIds(page))[1];
    await gwt.clickReplyOnBlip(replyParentBlipId);
    await waitForBlipCount(page, 3);
    await gwt.typeIntoBlipDocument("Issue 1208 nested reply");

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.gotoWave(waveId);
    await j2cl.assertInboxLoaded();

    const inlineThread = page.locator(
      `.inline-thread[data-parent-blip-id="${replyParentBlipId}"]`
    );
    await expect
      .poll(async () => await inlineThread.count(), {
        timeout: 60_000,
        message: "expected at least one parent-owned inline thread"
      })
      .toBeGreaterThanOrEqual(1);
    await expect(inlineThread.locator(".j2cl-read-thread-toggle")).toHaveCount(
      0
    );

    const chevron = page
      .locator(
        `wave-blip[data-blip-id="${replyParentBlipId}"] [data-thread-chevron="true"]`
      )
      .first();
    await expect(chevron).toBeVisible({ timeout: 10_000 });

    await chevron.click();
    await expect
      .poll(
        async () =>
          await inlineThread.evaluateAll(els =>
            els.every(
              el => el.getAttribute("data-j2cl-thread-collapsed") === "true"
            )
          ),
        {
          timeout: 10_000,
          message: "parent chevron should collapse every inline thread"
        }
      )
      .toBe(true);
  });
});
