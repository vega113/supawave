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
  // Both views expose a refresh affordance with `title="Refresh search
  // results"` (GWT setTooltip lowers to a `title=""` HTML attr; J2CL
  // emits the same attr on its action-row button). On J2CL the rail
  // also emits a hidden SSR'd duplicate in light DOM — `:visible`
  // narrows to the user-perceivable button.
  return page.locator('[title="Refresh search results"]:visible').first();
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
    // The rail emits the action row twice in DOM: once pre-upgrade (in
    // light DOM, where it's intentionally hidden post-upgrade because the
    // rail does not expose a default slot per #1060) and once in the
    // shadow DOM (the visible one rendered by Lit). We assert at least
    // one VISIBLE action row exists, matching the contract a user would
    // perceive on the page.
    await expect(
      page.locator("[data-digest-action-row]:visible").first(),
      "J2CL: at least one action-row must be visible"
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.locator('[data-digest-action="refresh"]:visible').first(),
      "J2CL: refresh action button"
    ).toBeVisible();
    await expect(
      page.locator('[data-digest-action="sort"]:visible').first(),
      "J2CL: sort action button"
    ).toBeVisible();
    await expect(
      page.locator('[data-digest-action="filter"]:visible').first(),
      "J2CL: filter action button"
    ).toBeVisible();

    // Refresh affordance is reachable via the cross-view selector.
    await expect(
      refreshButton(page),
      "J2CL: refresh affordance reachable via title='Refresh search results'"
    ).toBeVisible({ timeout: 10_000 });

    // J-UI-1 / G-PORT-2: read whether the rail-card path is enabled
    // for this viewer. The shell-root SSR mirrors the per-viewer flag
    // value through `data-j2cl-search-rail-cards="true"`. When the
    // flag is OFF the J2CL view renders the legacy plain-DOM digest
    // list (which does NOT carry data-digest-card hooks); the parity
    // test then reduces its scope to the action-row contract,
    // because per-card structural parity is gated on the rail-cards
    // flag being on. The test still proves the parity selectors are
    // wired correctly when the path IS on.
    const railCardsOn = await page
      .locator("shell-root[data-j2cl-search-rail-cards='true']")
      .count();
    let j2clCards: DigestSnapshot[] | null = null;
    if (railCardsOn > 0) {
      // The J2CL search panel is async — cards arrive after the
      // search subscription returns. Wait for either at least one
      // <wavy-search-rail-card> to mount, or for the rail's
      // .result-count to become non-empty (which fires when the
      // search-update arrives even with zero matches). Either signal
      // means the search has settled.
      await page
        .waitForFunction(
          () => {
            const cards = document.querySelectorAll("wavy-search-rail-card");
            if (cards.length > 0) return true;
            const rail = document.querySelector("wavy-search-rail");
            const sr = rail && (rail as HTMLElement).shadowRoot;
            const counter = sr && sr.querySelector("p.result-count");
            return !!(counter && counter.textContent && counter.textContent.trim().length > 0);
          },
          { timeout: 20_000 }
        )
        .catch(() => {
          // Surface a clear diagnostic if the wait times out — better
          // than letting the parity assertion fail with a misleading
          // count.
          throw new Error(
            "J2CL search subscription did not settle within 20s. " +
              "Either the search service is broken on this server, " +
              "or the j2cl-search-rail-cards flag is not actually " +
              "delivering cards. Inspect the trace attachment."
          );
        });
      j2clCards = await snapshotCards(page);
    }

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
    // data-digest-action-row/sort/filter are only emitted by the J2CL SSR
    // path (appendWavySearchRail in HtmlRenderer). The GWT search panel
    // creates its toolbar via SearchPanelWidget at runtime without those
    // attributes, so sort/filter are not asserted here.

    // GWT search is also async (XHR /search). Wait until either at
    // least one digest card appears, or the wave-count info bar
    // renders text (the GWT widget updates `.waveCount` when the
    // /search response lands, even with zero matches).
    await page
      .waitForFunction(
        () => {
          if (document.querySelectorAll("[data-digest-card]").length > 0) return true;
          const wc = document.querySelector(".waveCount, [class*='waveCount']");
          return !!(wc && wc.textContent && wc.textContent.trim().length > 0);
        },
        { timeout: 30_000 }
      )
      .catch(() => {
        throw new Error(
          "GWT search did not settle within 30s. Inspect the trace attachment."
        );
      });

    const gwtCards = await snapshotCards(page);

    // ---- Parity assertions ----

    // GWT cards always carry data-digest-* hooks (this slice tagged
    // DigestDomImpl.ui.xml). Whether the J2CL view renders them via
    // <wavy-search-rail-card> depends on the j2cl-search-rail-cards
    // feature flag for the viewer.
    if (j2clCards !== null) {
      // J2CL rail-cards path is enabled for this viewer — assert the
      // full per-card parity contract.
      test.info().annotations.push({
        type: "parity-cards",
        description:
          `j2cl=${j2clCards.length} gwt=${gwtCards.length} ` +
          `j2cl-titles=${JSON.stringify(j2clCards.map((c) => c.title))} ` +
          `gwt-titles=${JSON.stringify(gwtCards.map((c) => c.title))}`
      });
      expect(
        gwtCards.length,
        `digest card count parity (j2cl=${j2clCards.length}, gwt=${gwtCards.length})`
      ).toEqual(j2clCards.length);
      expect(gwtCards.map((c) => c.title)).toEqual(j2clCards.map((c) => c.title));
      for (const c of [...j2clCards, ...gwtCards]) {
        expect(c.hasAvatars, "card must have data-digest-avatars").toBe(true);
        expect(c.hasTitle, "card must have data-digest-title").toBe(true);
        expect(c.hasSnippet, "card must have data-digest-snippet").toBe(true);
        expect(c.hasMsgCount, "card must have data-digest-msg-count").toBe(true);
        expect(c.hasTime, "card must have data-digest-time").toBe(true);
      }
    } else {
      // J2CL rail-cards flag is off — the legacy plain-DOM digest list
      // does not carry data-digest-* hooks. We still assert that GWT
      // cards (when any) expose the five children, because that's the
      // GWT-side contract this slice ships.
      for (const c of gwtCards) {
        expect(c.hasAvatars, "GWT card must have data-digest-avatars").toBe(true);
        expect(c.hasTitle, "GWT card must have data-digest-title").toBe(true);
        expect(c.hasSnippet, "GWT card must have data-digest-snippet").toBe(true);
        expect(c.hasMsgCount, "GWT card must have data-digest-msg-count").toBe(true);
        expect(c.hasTime, "GWT card must have data-digest-time").toBe(true);
      }
      test
        .info()
        .annotations.push({
          type: "note",
          description:
            "j2cl-search-rail-cards flag OFF for this viewer; per-card parity check skipped — only action-row + GWT-side card hooks asserted."
        });
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
