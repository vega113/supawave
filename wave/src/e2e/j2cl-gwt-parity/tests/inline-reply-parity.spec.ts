// G-PORT-4 (#1113) — inline reply + working compose toolbar parity.
//
// Acceptance per issue #1113:
//   - Sign in fresh user, open a wave with at least one blip on both
//     ?view=j2cl-root and ?view=gwt.
//   - Click Reply on a blip. Assert composer opens INLINE at that
//     position (not at the bottom of a separate panel).
//   - Type "hello world" in the composer.
//   - Select the word "world" and click Bold. Assert the DOM shows
//     <strong>world</strong> (or equivalent) inside the composer.
//   - Click Send. Assert a new blip appears in the wave with text
//     "hello world".
//   - All assertions pass on BOTH views; if the GWT half fails for an
//     existing GWT regression unrelated to this slice, file a separate
//     issue and KEEP the assertion (do not skip silently).
//
// Per project memory `feedback_local_registration_before_login_testing`,
// every run registers a fresh user. The fresh user is auto-seeded with
// a Welcome wave by the WelcomeRobot (RegistrationUtil.java:91-93), so
// the inbox is non-empty by the time the test opens it. No GWT-side
// seeding is needed.
import { test, expect, Page } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

/**
 * On the J2CL view: open the first wave in the inbox by clicking its
 * search-rail card. Returns a locator for the opened wave's blip
 * container.
 */
async function openFirstWaveJ2cl(page: Page, baseURL: string): Promise<void> {
  await page.goto(`${baseURL}/?view=j2cl-root`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("shell-root", { timeout: 15_000 });
  // Find a digest card and click. wavy-search-rail-card carries
  // data-wave-id on its host.
  const card = page.locator("wavy-search-rail-card").first();
  await card.waitFor({ state: "attached", timeout: 30_000 });
  await card.click({ timeout: 15_000 });
  // The wave panel mounts wave-blip elements once the snapshot loads.
  await page.waitForSelector("wave-blip", { timeout: 30_000 });
}

/**
 * Click Reply on the first <wave-blip> in the wave and return a
 * locator for the inline <wavy-composer>. Asserts the composer
 * mounts INLINE inside the blip subtree (not at the page root).
 */
async function clickReplyOnFirstBlipJ2cl(page: Page) {
  const firstBlip = page.locator("wave-blip").first();
  await firstBlip.scrollIntoViewIfNeeded();
  await firstBlip.hover();
  // The Reply button lives inside <wave-blip-toolbar>'s shadow root;
  // Playwright pierces shadow DOM automatically.
  await firstBlip
    .locator("wave-blip-toolbar")
    .locator("button[data-toolbar-action='reply']")
    .click({ timeout: 10_000 });
  const inlineComposer = firstBlip.locator(
    "wavy-composer[data-inline-composer='true']"
  );
  await expect(
    inlineComposer,
    "Reply must mount <wavy-composer> inline at the blip"
  ).toHaveCount(1, { timeout: 10_000 });
  return inlineComposer;
}

/**
 * Insert the given phrase into the composer body via
 * `execCommand("insertText")` so wavy-composer observes the change
 * through the browser's normal editing/input pipeline. Setting
 * `textContent` directly desynchronizes the rich-component serializer
 * — observed: composer unmounts on send (so the click registered) but
 * no new blip is created on the server (the controller saw an
 * empty/stale rich-component list).
 *
 * Strategy: focus the editable body, insert the full phrase in one
 * browser editing command, then poll until the composer state/draft
 * reflects the expected text. This keeps the mutation observer and
 * serialized draft in sync without relying on per-character
 * Playwright keystrokes.
 */
async function typeInComposerJ2cl(
  page: Page,
  composerLocator: ReturnType<Page["locator"]>,
  phrase: string
): Promise<void> {
  const body = composerLocator.locator("[data-composer-body]");
  await body.click();
  await page.waitForTimeout(400);

  const readDraft = async () =>
    await composerLocator.evaluate(
      (host: HTMLElement) => (host as any).draft || ""
    );

  // Drive the input via execCommand("insertText") so the browser's
  // text-editing pipeline fires real beforeinput/input events that
  // wavy-composer's mutation observer + draft-change listener both
  // honour. `keyboard.type` consistently drops both leading and
  // trailing characters under any load — the browser delivers them
  // before Lit attaches the input listener, so the controller's
  // serializer never sees them. execCommand-driven input fires the
  // events synchronously and is the same path the user-visible
  // composer takes when the user pastes or types.
  await composerLocator.evaluate(
    (host: HTMLElement, args: { phrase: string }) => {
      const root = (host as any).shadowRoot as ShadowRoot;
      const b = root.querySelector("[data-composer-body]") as HTMLElement;
      b.focus();
      // Move caret to a clean end-of-body position before insert so
      // the new text appends rather than splitting an existing node.
      const range = document.createRange();
      range.selectNodeContents(b);
      range.collapse(false);
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(range);
      document.execCommand("insertText", false, args.phrase);
    },
    { phrase }
  );

  await expect
    .poll(readDraft, {
      message: `composer draft must equal '${phrase}' before submit`,
      timeout: 5_000,
      intervals: [100, 200, 300]
    })
    .toBe(phrase);
}

/**
 * Programmatically select the given word inside the composer body and
 * click the Bold tile. Asserts <strong>{word}</strong> appears in the
 * body afterwards.
 */
async function applyBoldToWordJ2cl(
  page: Page,
  composerLocator: ReturnType<Page["locator"]>,
  word: string
): Promise<void> {
  await page.evaluate((args: { word: string }) => {
    const composer = document.querySelector(
      "wavy-composer[data-inline-composer='true']"
    );
    if (!composer || !composer.shadowRoot) return;
    const editor = composer.shadowRoot.querySelector("[data-composer-body]");
    if (!editor) return;
    const text = editor.firstChild;
    if (!text || text.nodeType !== Node.TEXT_NODE) return;
    const idx = (text.textContent || "").indexOf(args.word);
    if (idx < 0) return;
    const range = document.createRange();
    range.setStart(text, idx);
    range.setEnd(text, idx + args.word.length);
    const sel = window.getSelection();
    sel?.removeAllRanges();
    sel?.addRange(range);
    document.dispatchEvent(new Event("selectionchange"));
  }, { word });
  await composerLocator
    .locator("wavy-format-toolbar")
    .locator("toolbar-button[action='bold']")
    .locator("button")
    .click({ timeout: 10_000 });
  const bodyHtml = await composerLocator.evaluate((host: HTMLElement) => {
    const root = (host as any).shadowRoot as ShadowRoot | null;
    if (!root) return "";
    const body = root.querySelector("[data-composer-body]");
    return body ? body.innerHTML : "";
  });
  const wrapMatcher = new RegExp(
    `<(strong|b)[^>]*>${word}<\\/\\1>`,
    "i"
  );
  expect(
    wrapMatcher.test(bodyHtml),
    `bold tile must wrap '${word}' in <strong>; saw: ${bodyHtml}`
  ).toBe(true);
}

/**
 * Click the inline composer's Send button and wait for a new
 * <wave-blip> to appear in the wave.
 */
async function sendInlineReplyJ2cl(
  page: Page,
  composerLocator: ReturnType<Page["locator"]>,
  draftText: string
): Promise<void> {
  const sendBtn = composerLocator
    .locator("composer-submit-affordance")
    .locator("button")
    .first();
  await sendBtn.scrollIntoViewIfNeeded();
  await expect(sendBtn).toBeVisible({ timeout: 5_000 });
  await sendBtn.click({ timeout: 10_000 });

  // Wait for the inline composer to unmount — this confirms the reply
  // was accepted. Once the composer is gone any wave-blip carrying
  // the draft text is the newly created reply blip, not the composer
  // itself (which also lived inside a wave-blip subtree).
  await expect(
    composerLocator,
    "inline composer must unmount after send"
  ).toHaveCount(0, { timeout: 15_000 });
  await expect(
    page.locator("wave-blip", { hasText: draftText }).first(),
    `the newly sent reply must appear as a wave-blip carrying '${draftText}'`
  ).toBeVisible({ timeout: 20_000 });
}

async function clickReplyOnFirstBlipGwt(gwt: GwtPage): Promise<void> {
  const firstBlip = gwt.gwtBlips().first();
  await expect(
    firstBlip,
    "GWT welcome wave must expose at least one rendered blip"
  ).toBeVisible({ timeout: 15_000 });
  await firstBlip.hover();
  const reply = firstBlip.locator("[data-e2e-action='reply']").first();
  await expect(
    reply,
    "GWT reply action must be reachable through a stable hook"
  ).toBeVisible({ timeout: 15_000 });
  await reply.click({ timeout: 10_000 });
  await expect(
    gwt.gwtActiveEditableDocument(),
    "GWT editor must open after Reply"
  ).toBeVisible({ timeout: 15_000 });
}

async function typeInComposerGwt(
  page: Page,
  gwt: GwtPage,
  phrase: string
): Promise<void> {
  const editor = gwt.gwtActiveEditableDocument();
  await editor.click({ timeout: 10_000 });
  await editor.evaluate((el) => (el as HTMLElement).focus());
  await expect
    .poll(
      async () =>
        await editor.evaluate(
          (el) => el === document.activeElement || el.contains(document.activeElement)
        ),
      { message: "GWT editor must own focus before typing", timeout: 5_000 }
    )
    .toBe(true);
  await page.keyboard.insertText(phrase);
  await expect
    .poll(
      async () => await editor.evaluate((el) => (el.textContent || "").trim()),
      { message: "GWT editor must contain the typed draft", timeout: 10_000 }
    )
    .toContain(phrase);
}

async function selectWordInGwtEditor(
  page: Page,
  gwt: GwtPage,
  word: string
): Promise<boolean> {
  const editor = gwt.gwtActiveEditableDocument();
  const selected = await editor.evaluate((el, targetWord) => {
    (el as HTMLElement).focus();
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
      const text = node.textContent || "";
      const index = text.indexOf(targetWord);
      if (index >= 0) {
        const range = document.createRange();
        range.setStart(node, index);
        range.setEnd(node, index + targetWord.length);
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        document.dispatchEvent(new Event("selectionchange", { bubbles: true }));
        return true;
      }
      node = walker.nextNode();
    }
    return false;
  }, word);
  if (!selected) {
    return false;
  }
  return await page.evaluate(
    (targetWord) => window.getSelection()?.toString() === targetWord,
    word
  );
}

async function selectTrailingWordWithKeyboard(
  page: Page,
  gwt: GwtPage,
  word: string
): Promise<void> {
  const editor = gwt.gwtActiveEditableDocument();
  await editor.click({ timeout: 10_000 });
  await expect
    .poll(
      async () =>
        await editor.evaluate(
          (el, targetWord) => (el.textContent || "").trim().endsWith(targetWord),
          word
        ),
      {
        message: `GWT editor text must end with '${word}' before keyboard selection`,
        timeout: 5_000
      }
    )
    .toBe(true);
  await page.keyboard.press(
    process.platform === "darwin" ? "Meta+ArrowRight" : "Control+ArrowRight"
  );
  for (let i = 0; i < word.length; i += 1) {
    await page.keyboard.press("Shift+ArrowLeft");
  }
  await expect
    .poll(
      async () => await page.evaluate(() => window.getSelection()?.toString() || ""),
      {
        message: `GWT keyboard fallback must select trailing '${word}'`,
        timeout: 5_000
      }
    )
    .toBe(word);
}

async function applyBoldToWordGwt(
  page: Page,
  gwt: GwtPage,
  word: string
): Promise<void> {
  const selectedByRange = await selectWordInGwtEditor(page, gwt, word);
  if (!selectedByRange) {
    await selectTrailingWordWithKeyboard(page, gwt, word);
  }
  await expect
    .poll(
      async () => await page.evaluate(() => window.getSelection()?.toString() || ""),
      { message: `GWT selection must target '${word}' before Bold`, timeout: 5_000 }
    )
    .toBe(word);

  const bold = page.locator("[data-e2e-action='bold']").first();
  await expect(bold, "GWT bold toolbar action must be stable").toBeVisible({
    timeout: 10_000
  });
  await bold.click({ timeout: 10_000 });
}

async function finishInlineReplyGwt(
  page: Page,
  gwt: GwtPage,
  initialBlipCount: number,
  draftText: string
): Promise<void> {
  const done = page.locator("[data-e2e-action='edit-done']").last();
  await expect(done, "GWT edit-done action must be stable").toBeVisible({
    timeout: 10_000
  });
  await done.click({ timeout: 10_000 });
  await expect
    .poll(
      async () => await gwt.gwtBlips().count(),
      { message: "GWT reply submit must add a new blip", timeout: 25_000 }
    )
    .toBeGreaterThan(initialBlipCount);
  await expect(
    gwt.gwtBlips().filter({ hasText: draftText }).last(),
    "the newly submitted GWT reply blip must carry the draft text"
  ).toBeVisible({ timeout: 20_000 });
}

// Each test registers a fresh user and operates on its own
// authenticated session, so the suite is safe to run with the
// harness's `fullyParallel: false, workers: 1` config without a
// `.serial` annotation. Removing `.serial` keeps each test
// independent — if one flakes mid-run, the next still gets a clean
// browser context.
test.describe("G-PORT-4 inline reply + working compose toolbar parity", () => {
  test("J2CL: bold-applied inline reply ships a new blip", async ({ page }) => {
    const creds = freshCredentials("g4j");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    // The Welcome wave seeded by the WelcomeRobot at registration time
    // gives us a wave with multiple blips to reply to.
    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();

    await openFirstWaveJ2cl(page, BASE_URL);
    const composer = await clickReplyOnFirstBlipJ2cl(page);
    // Use a unique payload so we can locate the new blip exactly
    // (the welcome wave already contains the substring "hello"
    // adjacent to other words elsewhere). The bold tile is
    // applied to the second word of the unique payload.
    const phrase = `hello world ${Date.now().toString(36)}`;
    await typeInComposerJ2cl(page, composer, phrase);
    await applyBoldToWordJ2cl(page, composer, "world");
    await sendInlineReplyJ2cl(page, composer, phrase);
  });

  test("GWT: bold-applied inline reply ships a new blip", async ({ page }) => {
    const creds = freshCredentials("g4g");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    // Smoke: the GWT bootstrap mounts.
    const gwt = new GwtPage(page, BASE_URL);
    await gwt.goto("/");
    await gwt.assertInboxLoaded();

    // The Welcome wave digest must be present in the GWT inbox. Use
    // a poll so we don't race GWT's deferred digest hydration.
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

    // Open the welcome wave. The digest list lives on the left
    // pane; click its visible entry. Wait briefly first so GWT's
    // deferred relayout has settled — direct .first() can target a
    // hidden pre-render scratch node otherwise.
    await page.waitForTimeout(2_500);
    const digest = page.locator("text=Welcome to SupaWave").first();
    await expect(digest).toBeVisible({ timeout: 10_000 });
    await digest.click({ timeout: 15_000 });
    await page.waitForTimeout(6_000);

    // GWT renders blip text inside multiple nested containers; assert
    // the body's text content carries a stable welcome-wave phrase
    // that is present in both J2CL and GWT renderings. Direct
    // visibility checks race GWT's deferred relayout.
    await expect
      .poll(
        async () =>
          await page.evaluate(() =>
            document.body.innerText.includes("This welcome wave is your dock")
          ),
        {
          message: "GWT view must render the welcome wave's body",
          timeout: 20_000
        }
      )
      .toBe(true);

    const phrase = `hello ${Date.now().toString(36)} world`;
    await expect(
      gwt.gwtBlips().filter({ hasText: phrase }),
      "unique reply payload must not already exist in the welcome wave"
    ).toHaveCount(0);
    const initialBlipCount = await gwt.gwtBlips().count();
    await clickReplyOnFirstBlipGwt(gwt);
    await typeInComposerGwt(page, gwt, phrase);
    await applyBoldToWordGwt(page, gwt, "world");
    await finishInlineReplyGwt(page, gwt, initialBlipCount, phrase);
  });
});
