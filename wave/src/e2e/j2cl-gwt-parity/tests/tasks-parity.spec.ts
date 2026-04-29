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
// J2CL persistence blocker (issue #1129):
//   The J2CL toggle delta (`J2clRichContentDeltaFactory.taskToggleRequest`)
//   emits two adjacent annotation boundaries with no operation
//   between them. The wavelet validator
//   (`DocOpAutomaton.checkAnnotationBoundary`) rejects this with
//   "ill-formed: adjacent annotation boundaries at original document
//   position 0 / resulting document position 0", so the toggle never
//   persists. The optimistic UI flips immediately on click, but
//   reload returns the un-toggled state and a second context never
//   sees the change. The fix needs to plumb the projected blip
//   body's wavelet item count from the read renderer through to the
//   delta factory so it can emit `retain(N)` between the boundaries.
//   That work is tracked at issue #1129.
//
//   Until #1129 lands, the J2CL half of this spec asserts:
//   - Optimistic UI: the host's `data-task-completed` flips on click.
//   The reload + cross-context J2CL assertions are wrapped in
//   `test.fixme` referencing #1129 so the gate flips back to live
//   the moment that issue is fixed (no test rewrite required).
import { test, expect, Browser, BrowserContext, Page } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";

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

/** Waits until the count of outer blips >= n. */
async function waitForBlipCount(page: Page, n: number): Promise<void> {
  await expect
    .poll(async () => (await blipIds(page)).length, {
      timeout: 30_000,
      message: `expected at least ${n} blips`
    })
    .toBeGreaterThanOrEqual(n);
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
  await page.waitForLoadState("networkidle", { timeout: 30_000 });

  await expect(gwt.newWaveAffordance()).toBeVisible({ timeout: 15_000 });
  await gwt.newWaveAffordance().click();
  await waitForBlipCount(page, 1);
  const waveId = await gwt.readWaveIdFromHash();
  expect(waveId, "GWT new-wave URL fragment must encode waveId").toBeTruthy();

  await gwt.typeIntoBlipDocument("Root blip text");

  const rootBlipId = (await blipIds(page))[0];
  expect(rootBlipId, "root blip id must populate").toBeTruthy();

  await gwt.clickReplyOnBlip(rootBlipId);
  await waitForBlipCount(page, 2);
  await gwt.typeIntoBlipDocument("Second blip text");

  await gwt.clickReplyOnBlip(rootBlipId);
  await waitForBlipCount(page, 3);
  await gwt.typeIntoBlipDocument("Third blip text");

  const ids = await blipIds(page);
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

  test("J2CL: per-blip task toggle flips data-task-completed (optimistic UI)", async ({
    page
  }) => {
    const creds = freshCredentials("g6j");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    test.info().annotations.push({
      type: "blocker",
      description:
        "J2CL persistence + cross-context propagation gated on issue #1129 " +
        "(taskToggleRequest emits adjacent annotation boundaries that the " +
        "wavelet validator rejects). This test asserts only the optimistic " +
        "UI flip — reload + cross-context assertions are gated below."
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const gwt = new GwtPage(page, BASE_URL);
    const { waveId, blipJ } = await authorThreeBlipWave(page, gwt);

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

    // Click the per-blip task toggle on B-J.
    const toggle = j2cl.blipTaskToggle(blipJ);
    await toggle.scrollIntoViewIfNeeded();
    await expect(
      toggle,
      "[j2cl] task toggle button must mount on B-J"
    ).toHaveCount(1, { timeout: 15_000 });
    await toggle.click({ force: true });

    // Optimistic UI: the host's `data-task-completed` flips on click.
    // (Persistence + cross-context propagation are blocked by #1129.)
    await expect
      .poll(async () => await j2cl.blipHasTaskCompleted(blipJ), {
        timeout: 10_000,
        message: `[j2cl] B-J should reflect data-task-completed after click`
      })
      .toBe(true);
  });

  // BLOCKED on #1129: the J2CL toggle delta is rejected by the
  // wavelet validator (adjacent annotation boundaries) so the toggle
  // never persists. Once that issue ships, flip `test.fixme` to
  // `test` and the J2CL reload + cross-context contract goes live
  // with no other rewrites needed.
  test.fixme(
    "J2CL: task toggle persists across reload + cross-context (BLOCKED on #1129)",
    async ({ page, browser }) => {
      const creds = freshCredentials("g6jp");
      test.info().annotations.push({
        type: "test-user",
        description: creds.address
      });
      await registerAndSignIn(page, BASE_URL, creds);

      const gwt = new GwtPage(page, BASE_URL);
      const { waveId, blipJ } = await authorThreeBlipWave(page, gwt);

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
