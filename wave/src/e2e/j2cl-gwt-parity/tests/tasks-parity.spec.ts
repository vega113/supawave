// G-PORT-6 (#1115) — tasks + done state parity test for the J2CL <-> GWT
// parity harness.
//
// Acceptance per issue #1115 + the G-PORT roadmap (slice §3 G-PORT-6):
//   - Sign in fresh user. Open a wave with at least 2 blips on both
//     ?view=j2cl-root and ?view=gwt.
//   - Click the task icon on a blip. Assert the blip becomes a task
//     (DOM marker / metadata indicator).
//   - Click done. Assert done state visible (strikethrough /
//     checkbox-ish indicator).
//   - Reload the page. Assert the done state is preserved.
//   - Open the same wave from a second browser context. Assert done
//     state visible there too.
//   - All assertions on both views.
//
// Surface notes:
//   J2CL models tasks as a per-blip annotation toggled via the
//   <wavy-task-affordance> button (J-UI-6 / F-3.S2). The
//   <wave-blip> host carries `data-task-completed` (Lit Boolean
//   reflection — presence-only, never `="true"`) when the
//   annotation is set.
//
//   GWT models tasks as inline <check> content elements (a checkbox
//   doodad in the blip's document). Inserting one is gated behind
//   "Insert task" on the format toolbar, which immediately opens
//   TaskMetadataPopup — that popup must be dismissed before the
//   inline checkbox can be clicked. Toggling the checkbox makes the
//   CheckBox renderer add the `task-completed` class to the
//   surrounding paragraph (CheckBox.java:107, 175, 201) — that's
//   the strikethrough indicator the acceptance refers to.
//
// We deliberately use *different target blips* for the two halves
// (B-J for J2CL's per-blip annotation, B-G for GWT's inline check)
// so the two task models don't co-exist on a single document.
//
// Issue #1129 regression coverage:
//   J2CL task toggles are annotation writes. The delta must retain the
//   backing blip body's wavelet item count between annotation open and
//   close boundaries so the server accepts the mutation. This file now
//   keeps both the immediate optimistic UI assertion and the authoritative
//   reload + cross-context persistence contract live.
import { test, expect, Browser, BrowserContext, Page } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";
import { insertOpenTaskOnGwt } from "./helpers/gwt-task";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

/**
 * Returns the data-blip-id of every OUTER blip element (J2CL
 * <wave-blip> or GWT <div class="blip" kind="b">), in DOM order.
 * Filtered to OUTER hosts because the J2CL renderer applies
 * data-blip-id to several descendants.
 */
async function blipIds(page: Page): Promise<string[]> {
  return await page
    .locator("wave-blip[data-blip-id], [kind='b'][data-blip-id]")
    .evaluateAll(els =>
      els
        .map(el => el.getAttribute("data-blip-id") || "")
        .filter(id => id.length > 0)
    );
}

/**
 * Returns only outer blip ids whose chrome and body are populated.
 * GWT can insert placeholder blips that have data-blip-id before
 * author/time metadata and visible body text are ready; those must not
 * be indexed as B-G/B-J targets.
 */
async function populatedBlipIds(page: Page): Promise<string[]> {
  return await page
    .locator("wave-blip[data-blip-id], [kind='b'][data-blip-id]")
    .evaluateAll((els) => {
      const out: string[] = [];
      for (const el of els) {
        const id = el.getAttribute("data-blip-id");
        if (!id) continue;
        const author =
          el.getAttribute("data-blip-author") ||
          el
            .querySelector("[data-blip-author]")
            ?.getAttribute("data-blip-author") ||
          "";
        const time =
          el.getAttribute("data-blip-time") ||
          el
            .querySelector("[data-blip-time]")
            ?.getAttribute("data-blip-time") ||
          "";
        const bodyText = (el.textContent || "").trim();
        if (author && time && bodyText) out.push(id);
      }
      return out;
    });
}

/** Waits until the count of outer blips >= n. */
async function waitForBlipCount(page: Page, n: number): Promise<void> {
  await expect
    .poll(async () => (await blipIds(page)).length, {
      timeout: 30_000,
      message: `expected at least ${n} blips`
    })
    .toBeGreaterThanOrEqual(n);
}

/** Waits until at least n non-empty outer blip ids are populated. */
async function waitForPopulatedBlipIds(
  page: Page,
  n: number
): Promise<string[]> {
  let latest: string[] = [];
  await expect
    .poll(
      async () => {
        latest = await populatedBlipIds(page);
        return latest.length;
      },
      {
        timeout: 30_000,
        message: `expected at least ${n} populated blip ids`
      }
    )
    .toBeGreaterThanOrEqual(n);
  return latest;
}

/**
 * Authors a 3-blip wave on the GWT view (root + 2 replies) and
 * returns identifiers for the spec's two halves:
 *   - waveId: domain-qualified id encoded in the URL fragment.
 *   - blipG:  the second blip — used by the GWT half.
 *   - blipJ:  the third blip — used by the J2CL half.
 *
 * Authoring runs on GWT because the J2CL inbox compose surface is
 * still gated off in some configurations; G-PORT-3 settled on
 * authoring on GWT and asserting on both views.
 */
async function authorThreeBlipWave(
  page: Page,
  gwt: GwtPage
): Promise<{ waveId: string; blipG: string; blipJ: string }> {
  await gwt.goto("/");
  await gwt.assertInboxLoaded();

  await expect(gwt.newWaveAffordance()).toBeVisible({ timeout: 15_000 });
  await gwt.newWaveAffordance().click();
  await waitForBlipCount(page, 1);
  const waveId = await gwt.readWaveIdFromHash();
  expect(waveId, "GWT new-wave URL fragment must encode waveId").toBeTruthy();

  await gwt.typeIntoBlipDocument("Root blip text");

  const rootBlipId = (await waitForPopulatedBlipIds(page, 1))[0];
  expect(rootBlipId, "root blip id must populate").toBeTruthy();

  await gwt.clickReplyOnBlip(rootBlipId);
  await waitForBlipCount(page, 2);
  await gwt.typeIntoBlipDocument("Second blip text");

  await gwt.clickReplyOnBlip(rootBlipId);
  await waitForBlipCount(page, 3);
  await gwt.typeIntoBlipDocument("Third blip text");

  const ids = await waitForPopulatedBlipIds(page, 3);
  expect(
    ids.length,
    `wave should have at least 3 blips after authoring; saw ${ids.length}`
  ).toBeGreaterThanOrEqual(3);

  return { waveId, blipG: ids[1], blipJ: ids[2] };
}

/**
 * Boots a second BrowserContext that shares the first context's
 * cookies + localStorage so the second-context navigations land on
 * the same authenticated session. Mirrors Playwright's documented
 * storageState pattern.
 */
async function spawnSecondContext(
  browser: Browser,
  primary: BrowserContext
): Promise<BrowserContext> {
  const storage = await primary.storageState();
  return await browser.newContext({ storageState: storage });
}

test.describe("G-PORT-6 tasks + done state parity", () => {
  test.setTimeout(300_000); // GWT compose + cross-view assertions + 2nd context.

  test("GWT: insert-task + done + reload + cross-context", async ({
    page,
    browser
  }) => {
    const creds = freshCredentials("g6g");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const gwt = new GwtPage(page, BASE_URL);
    const { waveId, blipG } = await authorThreeBlipWave(page, gwt);

    // Re-enter edit mode on B-G via its per-blip Edit menu so the
    // wave-level format toolbar (with "Insert task") is mounted.
    // Clicking the document body alone does not start an EditSession
    // — that's wired through ActionsImpl on the Edit menu option.
    await gwt.clickEditOnBlip(blipG);
    await expect(
      page.locator('div[title^="Insert task"]').first(),
      "[gwt] format toolbar Insert task button must mount in edit mode"
    ).toBeVisible({ timeout: 15_000 });

    await gwt.clickInsertTask();
    // The TaskMetadataPopup is open on top of the page; dismiss it
    // before reaching for the freshly inserted checkbox.
    await gwt.dismissTaskMetadataPopup();

    // Commit the edit (Escape) so the doodad is persisted before we
    // toggle it and so the format toolbar gets out of the way.
    await page.keyboard.press("Escape");
    await page.waitForTimeout(500);

    await expect
      .poll(
        async () =>
          await page
            .locator(`[data-blip-id="${blipG}"] input[type="checkbox"]`)
            .count(),
        {
          timeout: 15_000,
          message: `[gwt] B-G should contain an inline checkbox after Insert task`
        }
      )
      .toBeGreaterThanOrEqual(1);

    // Click the inline checkbox to flip the task to "done".
    await gwt.clickFirstTaskCheckboxInBlip(blipG);
    await expect
      .poll(async () => await gwt.blipHasCheckedTask(blipG), {
        timeout: 10_000,
        message: `[gwt] B-G inline checkbox should be checked after click`
      })
      .toBe(true);
    await expect
      .poll(async () => await gwt.blipHasTaskCompletedParagraph(blipG), {
        timeout: 10_000,
        message: `[gwt] B-G paragraph should carry .task-completed`
      })
      .toBe(true);

    // Reload — done state must be preserved.
    await page.reload({ waitUntil: "domcontentloaded" });
    await gwt.assertInboxLoaded();
    await expect
      .poll(
        async () =>
          await page
            .locator(`[data-blip-id="${blipG}"] input[type="checkbox"]`)
            .count(),
        {
          timeout: 60_000,
          message: `[gwt after reload] B-G inline checkbox must remount`
        }
      )
      .toBeGreaterThanOrEqual(1);
    await expect
      .poll(async () => await gwt.blipHasCheckedTask(blipG), {
        timeout: 30_000,
        message: `[gwt after reload] B-G inline checkbox must stay :checked`
      })
      .toBe(true);
    await expect
      .poll(async () => await gwt.blipHasTaskCompletedParagraph(blipG), {
        timeout: 30_000,
        message: `[gwt after reload] B-G paragraph must keep .task-completed`
      })
      .toBe(true);

    // Cross-context — second BrowserContext signed in as the same
    // user must observe the done state.
    const secondContext = await spawnSecondContext(browser, page.context());
    try {
      const secondPage = await secondContext.newPage();
      const gwtCross = new GwtPage(secondPage, BASE_URL);
      await gwtCross.gotoWave(waveId);
      await gwtCross.assertInboxLoaded();
      await expect
        .poll(
          async () =>
            await secondPage
              .locator(`[data-blip-id="${blipG}"] input[type="checkbox"]`)
              .count(),
          {
            timeout: 60_000,
            message: `[gwt cross-context] B-G inline checkbox must mount`
          }
        )
        .toBeGreaterThanOrEqual(1);
      await expect
        .poll(async () => await gwtCross.blipHasCheckedTask(blipG), {
          timeout: 30_000,
          message: `[gwt cross-context] B-G inline checkbox must be :checked`
        })
        .toBe(true);
      await expect
        .poll(
          async () => await gwtCross.blipHasTaskCompletedParagraph(blipG),
          {
            timeout: 30_000,
            message: `[gwt cross-context] B-G paragraph must carry .task-completed`
          }
        )
        .toBe(true);
    } finally {
      await secondContext.close();
    }
  });

  test("J2CL: existing task toggle flips data-task-completed (optimistic UI)", async ({
    page
  }) => {
    const creds = freshCredentials("g6j");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    test.info().annotations.push({
      type: "scope",
      description:
        "Immediate J2CL optimistic UI assertion; reload + cross-context " +
        "persistence is covered by the following live regression test."
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const gwt = new GwtPage(page, BASE_URL);
    const { waveId, blipJ } = await authorThreeBlipWave(page, gwt);
    await insertOpenTaskOnGwt(page, gwt, blipJ);

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.gotoWave(waveId);
    await j2cl.assertInboxLoaded();

    await expect
      .poll(async () => await page.locator("wave-blip").count(), {
        timeout: 60_000,
        message: "[j2cl] expected at least 1 <wave-blip> after navigation"
      })
      .toBeGreaterThanOrEqual(1);
    await expect
      .poll(
        async () =>
          await page
            .locator(`wave-blip[data-blip-id="${blipJ}"][data-wave-id]`)
            .count(),
        {
          timeout: 30_000,
          message: `[j2cl] wave-blip[data-wave-id] must mount on B-J before toggle`
        }
      )
      .toBeGreaterThanOrEqual(1);

    // Give the compose-surface controller a beat after the read
    // surface mounts so its acceptNextWriteSession callback runs and
    // installs writeSession for this wave.
    await page.waitForTimeout(2_000);

    // Click the per-blip task toggle on B-J. The toggle is expected only
    // because the GWT side created a real task on this blip first; normal
    // non-task blips should not show a generic task affordance.
    const toggle = j2cl.blipTaskToggle(blipJ);
    await toggle.scrollIntoViewIfNeeded();
    await expect(
      toggle,
      "[j2cl] task toggle button must mount on B-J"
    ).toHaveCount(1, { timeout: 15_000 });
    await toggle.click({ force: true });

    // Optimistic UI: the host's `data-task-completed` flips on click.
    await expect
      .poll(async () => await j2cl.blipHasTaskCompleted(blipJ), {
        timeout: 10_000,
        message: `[j2cl] B-J should reflect data-task-completed after click`
      })
      .toBe(true);
  });

  test(
    "J2CL: task toggle persists across reload + cross-context",
    async ({ page, browser }) => {
      const creds = freshCredentials("g6jp");
      test.info().annotations.push({
        type: "test-user",
        description: creds.address
      });
      await registerAndSignIn(page, BASE_URL, creds);

      const gwt = new GwtPage(page, BASE_URL);
      const { waveId, blipJ } = await authorThreeBlipWave(page, gwt);
      await insertOpenTaskOnGwt(page, gwt, blipJ);

      const j2cl = new J2clPage(page, BASE_URL);
      await j2cl.gotoWave(waveId);
      await j2cl.assertInboxLoaded();

      await expect
        .poll(
          async () =>
            await page
              .locator(`wave-blip[data-blip-id="${blipJ}"][data-wave-id]`)
              .count(),
          { timeout: 30_000 }
        )
        .toBeGreaterThanOrEqual(1);
      await page.waitForTimeout(2_000);

      const host = page.locator(`wave-blip[data-blip-id="${blipJ}"]`).first();
      await expect(host).toHaveAttribute("data-blip-doc-size", /^[1-9]\d*$/);
      const toggle = j2cl.blipTaskToggle(blipJ);
      await toggle.scrollIntoViewIfNeeded();
      await toggle.click({ force: true });
      await expect
        .poll(async () => await j2cl.blipHasTaskCompleted(blipJ), {
          timeout: 10_000
        })
        .toBe(true);
      // Wait for the toggle delta to roundtrip through the server.
      await page.waitForTimeout(5_000);

      // Reload — done state must be preserved.
      await page.reload({ waitUntil: "domcontentloaded" });
      await j2cl.assertInboxLoaded();
      await expect
        .poll(
          async () =>
            await page.locator(`wave-blip[data-blip-id="${blipJ}"]`).count(),
          { timeout: 60_000 }
        )
        .toBeGreaterThanOrEqual(1);
      await expect
        .poll(async () => await j2cl.blipHasTaskCompleted(blipJ), {
          timeout: 30_000,
          message: `[j2cl after reload] B-J data-task-completed must be preserved`
        })
        .toBe(true);

      // Cross-context — second BrowserContext signed in as the same
      // user must observe the done state on the J2CL view.
      const secondContext = await spawnSecondContext(browser, page.context());
      try {
        const secondPage = await secondContext.newPage();
        const j2clCross = new J2clPage(secondPage, BASE_URL);
        await j2clCross.gotoWave(waveId);
        await j2clCross.assertInboxLoaded();
        await expect
          .poll(
            async () =>
              await secondPage
                .locator(`wave-blip[data-blip-id="${blipJ}"]`)
                .count(),
            { timeout: 60_000 }
          )
          .toBeGreaterThanOrEqual(1);
        await expect
          .poll(async () => await j2clCross.blipHasTaskCompleted(blipJ), {
            timeout: 30_000,
            message: `[j2cl cross-context] B-J data-task-completed must be visible`
          })
          .toBe(true);
      } finally {
        await secondContext.close();
      }
    }
  );
});
