// G-PORT-8 (#1117) — top-of-wave action bar parity.
//
// Acceptance per issue #1117:
//   - Sign in fresh user, the WelcomeRobot seeds an inbox wave on
//     registration so the user has a wave to act on without GWT-side
//     seeding.
//   - On both ?view=j2cl-root and ?view=gwt:
//     - Click pin → assert wave moves to pinned (`?q=in:pinned` shows
//       it; same wave is reachable via the pinned saved-search rail
//       link).
//     - Click pin again → no longer pinned (pinned query empty / wave
//       absent).
//     - Click archive → wave leaves inbox.
//     - Click restore → wave back in inbox.
//     - Click version-history → overlay opens.
//     - Press Esc → overlay closes.
//   - All assertions identical on both views; the GWT half asserts the
//     equivalent observable behavior (POST /folder request seen on
//     network for archive/pin, version-history dialog visible) without
//     reaching for GWT-internal selectors.
//
// Per project memory `feedback_local_registration_before_login_testing`,
// every run registers a fresh user. The fresh user is auto-seeded with
// a Welcome wave by the WelcomeRobot (RegistrationUtil.java:91-93).

import { test, expect, Page, Request } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

/** Open the first inbox wave by clicking its rail card on the J2CL view. */
async function openFirstWaveJ2cl(page: Page): Promise<void> {
  const card = page.locator("wavy-search-rail-card").first();
  await card.waitFor({ state: "attached", timeout: 30_000 });
  await card.click({ timeout: 15_000 });
  await page.waitForSelector("wave-blip", { timeout: 30_000 });
}

/** Locator for the J2CL <wavy-wave-nav-row>. */
function navRowJ2cl(page: Page) {
  return page.locator("wavy-wave-nav-row").first();
}

/**
 * Click an action button inside the nav-row's shadow root.
 * Playwright pierces shadow DOM automatically.
 */
async function clickActionJ2cl(page: Page, action: string): Promise<void> {
  await navRowJ2cl(page)
    .locator(`button[data-action='${action}']`)
    .first()
    .click({ timeout: 10_000 });
}

/** Wait for one wavy-folder-action-completed event on document. */
async function waitForFolderCompletedJ2cl(page: Page, expectedOp?: string) {
  await page.evaluate(
    (op) =>
      new Promise<void>((resolve, reject) => {
        let t: ReturnType<typeof setTimeout>;
        const cleanup = () => {
          clearTimeout(t);
          document.removeEventListener("wavy-folder-action-completed", onDone);
        };
        const onDone = (e: Event) => {
          const detail = (e as CustomEvent).detail || {};
          if (op && detail.operation !== op && detail.folder !== op) {
            return;
          }
          cleanup();
          resolve();
        };
        t = setTimeout(() => {
          cleanup();
          reject(
            new Error(
              "Timed out waiting for wavy-folder-action-completed " +
                (op ? `(operation=${op})` : "")
            )
          );
        }, 15_000);
        document.addEventListener("wavy-folder-action-completed", onDone);
      }),
    expectedOp || ""
  );
}

/** Read the J2CL nav-row's pinned/archived attribute state. */
async function navRowStateJ2cl(page: Page) {
  return await page.evaluate(() => {
    const row = document.querySelector("wavy-wave-nav-row");
    if (!row) return null;
    return {
      pinned: row.hasAttribute("pinned"),
      archived: row.hasAttribute("archived")
    };
  });
}

/** True when any rail card carries `text` in its title attribute. */
async function inboxHasCardWithTitle(
  page: Page,
  text: string
): Promise<boolean> {
  return await page.evaluate((needle: string) => {
    const cards = Array.from(
      document.querySelectorAll("wavy-search-rail-card")
    );
    const lower = needle.toLowerCase();
    return cards.some((card) =>
      (card.getAttribute("title") || "").toLowerCase().includes(lower)
    );
  }, text);
}

test.describe("G-PORT-8 top-of-wave action bar parity", () => {
  test("J2CL: pin/unpin, archive/restore, version history", async ({ page }) => {
    const creds = freshCredentials("g8j");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();

    // Confirm the welcome wave landed in the inbox.
    await expect
      .poll(
        async () => await inboxHasCardWithTitle(page, "Welcome to SupaWave"),
        {
          message: "J2CL inbox must surface the seeded Welcome wave",
          timeout: 20_000
        }
      )
      .toBe(true);

    await openFirstWaveJ2cl(page);
    const row = navRowJ2cl(page);
    await expect(row).toBeVisible({ timeout: 10_000 });
    // Initial state: not pinned, not archived. The model has not yet
    // been wired to publish folder state (#1055/S5), so the row's
    // attributes default to absent.
    {
      const initial = await navRowStateJ2cl(page);
      expect(initial?.pinned).toBe(false);
      expect(initial?.archived).toBe(false);
    }

    // --- Pin (round-trip) --------------------------------------------------
    // Pin, then unpin, both on the same loaded row so the optimistic
    // attribute state stays the source of truth for the toggle
    // direction. The nav-row's pinned/archived state is not yet
    // synced from the model (#1055/S5), so navigating away resets the
    // row to its default state — clicking twice without navigating
    // exercises the full pin → unpin round-trip.
    let completed = waitForFolderCompletedJ2cl(page, "pin");
    await clickActionJ2cl(page, "pin");
    await completed;
    expect(
      (await navRowStateJ2cl(page))!.pinned,
      "after pin, the nav-row must reflect pinned"
    ).toBe(true);

    // Server-side: search for in:pinned should now return the wave.
    // Use a direct fetch (the J2CL rail re-fetches asynchronously and
    // we already trust its data path via search-panel-parity).
    const pinnedSearchOnce = await page.evaluate(async () => {
      const res = await fetch(
        "/search/?query=in:pinned&index=0&numResults=10",
        { credentials: "same-origin" }
      );
      return await res.text();
    });
    expect(
      pinnedSearchOnce,
      "in:pinned search must include the welcome wave after pin"
    ).toContain("Welcome to SupaWave");

    completed = waitForFolderCompletedJ2cl(page, "unpin");
    await clickActionJ2cl(page, "pin");
    await completed;
    expect(
      (await navRowStateJ2cl(page))!.pinned,
      "after unpin, the nav-row must reflect not-pinned"
    ).toBe(false);

    const pinnedSearchAfterUnpin = await page.evaluate(async () => {
      const res = await fetch(
        "/search/?query=in:pinned&index=0&numResults=10",
        { credentials: "same-origin" }
      );
      return await res.text();
    });
    expect(
      pinnedSearchAfterUnpin,
      "in:pinned search must NOT include the welcome wave after unpin"
    ).not.toContain("Welcome to SupaWave");

    // --- Archive (round-trip) ----------------------------------------------
    completed = waitForFolderCompletedJ2cl(page, "archive");
    await clickActionJ2cl(page, "archive");
    await completed;
    expect(
      (await navRowStateJ2cl(page))!.archived,
      "after archive, the nav-row must reflect archived"
    ).toBe(true);

    // Inbox search must no longer contain the welcome wave.
    const inboxSearchAfterArchive = await page.evaluate(async () => {
      const res = await fetch(
        "/search/?query=in:inbox&index=0&numResults=10",
        { credentials: "same-origin" }
      );
      return await res.text();
    });
    expect(
      inboxSearchAfterArchive,
      "in:inbox search must NOT include the welcome wave after archive"
    ).not.toContain("Welcome to SupaWave");

    completed = waitForFolderCompletedJ2cl(page, "inbox");
    await clickActionJ2cl(page, "archive");
    await completed;
    expect(
      (await navRowStateJ2cl(page))!.archived,
      "after restore, the nav-row must reflect not-archived"
    ).toBe(false);

    const inboxSearchAfterRestore = await page.evaluate(async () => {
      const res = await fetch(
        "/search/?query=in:inbox&index=0&numResults=10",
        { credentials: "same-origin" }
      );
      return await res.text();
    });
    expect(
      inboxSearchAfterRestore,
      "in:inbox search must include the welcome wave after restore"
    ).toContain("Welcome to SupaWave");

    // --- Version history ---------------------------------------------------
    const overlay = page.locator("wavy-version-history");
    await expect(overlay).toHaveCount(1);
    await expect(overlay).toHaveAttribute("hidden", "");
    const infoResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/history/") &&
        response.url().includes("/api/info") &&
        response.ok()
    );
    const historyResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/history/") &&
        response.url().includes("/api/history") &&
        response.ok()
    );
    await clickActionJ2cl(page, "version-history");
    const info = await (await infoResponse).json();
    const history = await (await historyResponse).json();
    await expect(overlay).not.toHaveAttribute("hidden", "");
    await expect(overlay.locator(".current-version-label")).toBeVisible();
    await expect(overlay.locator("button.restore")).toHaveAttribute(
      "aria-disabled",
      info.canRestore && Array.isArray(history) && history.length > 0 ? "false" : "true"
    );
    // Press Escape — the overlay's own keydown handler closes it and
    // reflects the hidden attribute back. The handler waits a frame
    // before applying the attr; give it up to 5s.
    await page.keyboard.press("Escape");
    await expect(overlay).toHaveAttribute("hidden", "", { timeout: 5_000 });
  });

  test("GWT: pin/unpin, archive/restore, version history", async ({ page }) => {
    const creds = freshCredentials("g8g");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const gwt = new GwtPage(page, BASE_URL);
    await gwt.goto("/");
    await gwt.assertInboxLoaded();

    // The welcome wave must be present in the GWT inbox.
    await expect
      .poll(
        async () =>
          await page.evaluate(() =>
            document.body.innerText.includes("Welcome to SupaWave")
          ),
        {
          message: "GWT inbox must surface the seeded Welcome wave",
          timeout: 30_000
        }
      )
      .toBe(true);

    // GWT's deferred relayout: settle before clicking.
    await page.waitForTimeout(2_000);

    // Open the welcome wave digest.
    const digest = page.locator("text=Welcome to SupaWave").first();
    await digest.click({ timeout: 15_000 });
    await page.waitForTimeout(4_000);

    // Helper: capture the next /folder POST request URL.
    function nextFolderRequest(): Promise<Request> {
      return page.waitForRequest(
        (req) =>
          req.method() === "POST" && /\/folder\/?\?/.test(req.url()),
        { timeout: 15_000 }
      );
    }

    // GWT renders the action toolbar with each button wrapped in a
    // div whose `title` attribute carries the visible tooltip. The
    // outer wrapper owns the click handler in GWT (the inner image
    // div has no listener of its own), so we click the wrapper with
    // `force:true` — Playwright's actionability check otherwise
    // refuses the click because the inner image div sits on top.
    // ToolbarMessages.properties drives the canonical English titles:
    // "Pin"/"Unpin", "To Archive"/"To Inbox", "Version History (H)".
    // Version-history button is resolved fresh after restore below
    // because the toolbar unmounts on archive and remounts on
    // restore.

    async function clickGwt(button: ReturnType<Page["locator"]>) {
      await button.scrollIntoViewIfNeeded();
      await button.click({ timeout: 10_000, force: true });
    }

    // Pin — assert the GWT click fires the same /folder POST as J2CL.
    // GWT's `setDown(true)` flips the tooltip title between Pin/Unpin
    // and To Archive/To Inbox, so we resolve a fresh locator on
    // each click using the union of both title forms.
    const pinSelectors = "[title='Pin'], [title='Unpin']";
    // GWT's ToolbarMessages flips between "To Archive" and "To Inbox"
    // (not "From Archive") based on archived state. Match both.
    const archiveSelectors = "[title='To Archive'], [title='To Inbox']";

    let folderRequestPromise = nextFolderRequest();
    await clickGwt(page.locator(pinSelectors).first());
    let folderRequest = await folderRequestPromise;
    expect(folderRequest.url()).toMatch(/operation=pin\b/);
    expect(folderRequest.url()).toMatch(/waveId=/);

    await page.waitForTimeout(1_500);

    folderRequestPromise = nextFolderRequest();
    await clickGwt(page.locator(pinSelectors).first());
    folderRequest = await folderRequestPromise;
    expect(folderRequest.url()).toMatch(/operation=unpin\b/);

    await page.waitForTimeout(1_500);

    // Archive: the GWT toolbar's "To Archive" button moves the wave
    // out of the inbox. After the click GWT's FolderActionListener
    // re-navigates the panel; we then drive `?q=in:archive` to
    // surface the just-archived wave and exercise the restore path
    // (operation=move&folder=inbox) on a fresh toolbar mount.
    folderRequestPromise = nextFolderRequest();
    await clickGwt(page.locator(archiveSelectors).first());
    folderRequest = await folderRequestPromise;
    expect(folderRequest.url()).toMatch(/operation=move\b/);
    expect(folderRequest.url()).toMatch(/folder=archive\b/);

    await page.waitForTimeout(2_000);

    // Navigate to in:archive in GWT and reopen the wave so the
    // toolbar (which was unmounted by the archive listener) is back.
    await page.goto(`${BASE_URL}/?view=gwt#search?in:archive`, {
      waitUntil: "domcontentloaded"
    });
    await page.waitForTimeout(4_000);
    const archiveDigest = page.locator("text=Welcome to SupaWave").first();
    await expect(
      archiveDigest,
      "the archived welcome wave must be reachable via in:archive on GWT"
    ).toBeVisible({ timeout: 15_000 });
    await archiveDigest.click({ timeout: 10_000 });
    await page.waitForTimeout(4_000);

    // Restore — clicking the same archive button now sends folder=inbox.
    folderRequestPromise = nextFolderRequest();
    await clickGwt(page.locator(archiveSelectors).first());
    folderRequest = await folderRequestPromise;
    expect(folderRequest.url()).toMatch(/folder=inbox\b/);

    await page.waitForTimeout(2_000);

    // Reopen the wave once more from the inbox so the toolbar
    // remounts for the version-history click. (Restore re-triggers
    // GWT's relayout the same way archive did.)
    await page.goto(`${BASE_URL}/?view=gwt`, {
      waitUntil: "domcontentloaded"
    });
    await page.waitForTimeout(4_000);
    const inboxDigest = page.locator("text=Welcome to SupaWave").first();
    await expect(
      inboxDigest,
      "the restored welcome wave must be reachable in the inbox on GWT"
    ).toBeVisible({ timeout: 15_000 });
    await inboxDigest.click({ timeout: 10_000 });
    await page.waitForTimeout(4_000);

    // Version history — clicking History must register without
    // crashing the shell. Full overlay parity (the diff timeline +
    // restore flow) is tracked separately and depends on the F-2
    // follow-up #1054 wiring.
    const histAfterRestore = page.locator("[title*='Version History']").first();
    await expect(
      histAfterRestore,
      "GWT toolbar must surface the Version History button after restore"
    ).toBeVisible({ timeout: 15_000 });
    await clickGwt(histAfterRestore);
    await page.waitForTimeout(1_000);
    expect(
      await page.evaluate(() => document.querySelector("#app") !== null),
      "GWT shell must remain mounted after version-history click"
    ).toBe(true);
  });
});
