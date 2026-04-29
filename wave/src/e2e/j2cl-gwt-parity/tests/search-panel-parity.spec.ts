// G-PORT-2 (#1111) — search panel parity between ?view=j2cl-root and
// ?view=gwt.
//
// Asserts:
//   - both views render the search rail surface (action row + folder
//     list);
//   - both views expose a refresh affordance reachable via a single
//     selector (`title="Refresh search results"`) that exists on the
//     GWT toolbar (setTooltip ⇒ HTML `title="..."`) and the J2CL
//     action row (`<button data-digest-action="refresh"
//     title="Refresh search results">`);
//   - both views render the same set of digest cards in the same
//     order. A freshly registered user has 0 waves, so on each view
//     the count is 0 — the test still proves equality of the lists,
//     and the per-card structural assertions kick in once any test
//     fixture seeds at least one wave;
//   - each digest card on each view exposes `[data-digest-card]` plus
//     the five sub-selectors (`avatars`, `title`, `snippet`,
//     `msg-count`, `time`).
//
// Per the G-PORT-1 parity hard rule (issue #1110), this test does NOT
// skip an assertion to make the run pass. If a view legitimately
// fails to render the rail or expose the parity selectors the test
// fails until the underlying renderer is fixed.
import { test, expect, Locator, Page } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

interface DigestSnapshot {
  /** waveId / data-wave-id when present. */
  id: string | null;
  /** Title text. */
  title: string;
  /** Whether the six structural children exist. */
  hasAvatars: boolean;
  hasTitle: boolean;
  hasSnippet: boolean;
  hasMsgCount: boolean;
  hasTime: boolean;
}

/**
 * Reads every {@code [data-digest-card]} on the page and returns a
 * structural snapshot per card. Reads through the open shadow root of
 * the J2CL custom elements via Playwright's automatic shadow piercing
 * (the {@code locator(...)} selector pierces open shadow roots).
 */
async function snapshotCards(page: Page): Promise<DigestSnapshot[]> {
  // Wait briefly for the rail to settle. Both views ship the rail
  // pre-upgrade (server-rendered), so we don't need a long timeout —
  // a single attempt is enough.
  const cards = page.locator("[data-digest-card]");
  const count = await cards.count();
  const out: DigestSnapshot[] = [];
  for (let i = 0; i < count; i++) {
    const card = cards.nth(i);
    out.push({
      id: await card.getAttribute("data-wave-id"),
      title: ((await card.locator("[data-digest-title]").first().textContent()) || "").trim(),
      hasAvatars: (await card.locator("[data-digest-avatars]").count()) > 0,
      hasTitle: (await card.locator("[data-digest-title]").count()) > 0,
      hasSnippet: (await card.locator("[data-digest-snippet]").count()) > 0,
      hasMsgCount: (await card.locator("[data-digest-msg-count]").count()) > 0,
      hasTime: (await card.locator("[data-digest-time]").count()) > 0
    });
  }
  return out;
}

/**
 * Resolves the first refresh affordance by `title` attribute. GWT's
 * setTooltip lowers to `title=""` so the same selector matches both
 * views.
 */
function refreshButton(page: Page): Locator {
  return page.locator('[title="Refresh search results"]').first();
}

test.describe("G-PORT-2 search panel parity", () => {
  test("J2CL and GWT search rails render the same digest list and refresh affordance", async ({
    page
  }) => {
    test.setTimeout(90_000);
    const creds = freshCredentials("gp2");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    await registerAndSignIn(page, BASE_URL, creds);

    // ---- J2CL view ----
    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();

    // The rail must mount.
    await expect(
      page.locator("wavy-search-rail"),
      "J2CL: <wavy-search-rail> must mount"
    ).toHaveCount(1, { timeout: 15_000 });

    // The new action row must mount with refresh + sort + filter buttons.
    await expect(
      page.locator("[data-digest-action-row]").first(),
      "J2CL: action-row must mount"
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.locator('[data-digest-action="refresh"]').first(),
      "J2CL: refresh action button"
    ).toBeVisible();
    await expect(
      page.locator('[data-digest-action="sort"]').first(),
      "J2CL: sort action button"
    ).toBeVisible();
    await expect(
      page.locator('[data-digest-action="filter"]').first(),
      "J2CL: filter action button"
    ).toBeVisible();

    // Refresh affordance is reachable via the cross-view selector.
    await expect(
      refreshButton(page),
      "J2CL: refresh affordance reachable via title='Refresh search results'"
    ).toBeVisible({ timeout: 10_000 });

    const j2clCards = await snapshotCards(page);

    // ---- GWT view ----
    const gwt = new GwtPage(page, BASE_URL);
    await gwt.goto("/");
    await gwt.assertInboxLoaded();

    // GWT renders the search panel via SearchPanelWidget. Wait for the
    // rail toolbar to render — the GWT presenter creates the toolbar
    // on bootstrap. We use the shared refresh affordance as the
    // "rail is up" signal because it's what the parity contract
    // actually requires.
    await expect(
      refreshButton(page),
      "GWT: refresh affordance reachable via title='Refresh search results'"
    ).toBeVisible({ timeout: 30_000 });

    const gwtCards = await snapshotCards(page);

    // ---- Parity assertions ----

    // 1. The two views render the same number of digest cards.
    expect(
      gwtCards.length,
      `digest card count parity (j2cl=${j2clCards.length}, gwt=${gwtCards.length})`
    ).toEqual(j2clCards.length);

    // 2. Same titles in the same order.
    expect(gwtCards.map((c) => c.title)).toEqual(j2clCards.map((c) => c.title));

    // 3. Each card on each view exposes the five sub-children
    //    contract. (No-op for an empty list — but defensive against
    //    future fixture-seeded runs.)
    for (const c of [...j2clCards, ...gwtCards]) {
      expect(c.hasAvatars, "card must have data-digest-avatars").toBe(true);
      expect(c.hasTitle, "card must have data-digest-title").toBe(true);
      expect(c.hasSnippet, "card must have data-digest-snippet").toBe(true);
      expect(c.hasMsgCount, "card must have data-digest-msg-count").toBe(true);
      expect(c.hasTime, "card must have data-digest-time").toBe(true);
    }

    // 4. Visual diff: capture a rail screenshot from each view. We
    //    don't compare them at the pixel level (the two views
    //    legitimately differ in chrome — that's J-UI / V-* visual
    //    polish, not the rail-DOM contract this slice owns), but the
    //    screenshots are attached to the report so a reviewer can
    //    inspect side-by-side.
    //
    //    Re-load the J2CL view first to grab its screenshot, then
    //    re-load the GWT view for its screenshot.
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();
    await expect(page.locator("wavy-search-rail")).toHaveCount(1);
    const j2clRailShot = await page
      .locator("wavy-search-rail")
      .first()
      .screenshot();
    await test.info().attach("rail-j2cl.png", {
      body: j2clRailShot,
      contentType: "image/png"
    });

    await gwt.goto("/");
    await gwt.assertInboxLoaded();
    await expect(refreshButton(page)).toBeVisible({ timeout: 30_000 });
    // GWT's search panel container is `.search-panel` from SearchPanelWidget.css.
    // We screenshot the closest ancestor that contains the refresh button so the
    // test does not depend on a specific GWT internal class name.
    const gwtRail = page
      .locator('[title="Refresh search results"]')
      .first()
      .locator("xpath=ancestor::*[contains(@class, 'search-panel') or contains(@class, 'self')][1]");
    let gwtShot: Buffer;
    if ((await gwtRail.count()) > 0) {
      gwtShot = await gwtRail.first().screenshot();
    } else {
      // Fallback: screenshot the whole top-left quadrant where the rail lives.
      gwtShot = await page.screenshot({
        clip: { x: 0, y: 0, width: 360, height: 800 }
      });
    }
    await test.info().attach("rail-gwt.png", {
      body: gwtShot,
      contentType: "image/png"
    });
  });
});
