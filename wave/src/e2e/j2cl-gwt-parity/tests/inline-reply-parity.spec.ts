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
import {
  GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR,
  GwtPage
} from "../pages/GwtPage";
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

async function authorSimpleGwtWave(page: Page, baseURL: string): Promise<string> {
  const gwt = new GwtPage(page, baseURL);
  await gwt.goto("/");
  await gwt.assertInboxLoaded();
  await expect(gwt.newWaveAffordance()).toBeVisible({ timeout: 15_000 });
  await gwt.newWaveAffordance().click();
  await page.waitForSelector("[kind='b'][data-blip-id]", { timeout: 30_000 });
  const waveId = await gwt.readWaveIdFromHash();
  await gwt.typeIntoBlipDocument("Root blip text for inline reply parity");
  return waveId;
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
  // Playwright pierces shadow DOM automatically. Scope through the
  // outer card first: a root blip can contain inline child wave-blip
  // elements, and a broad descendant toolbar selector matches every
  // nested reply button in strict mode before the compose path runs.
  await firstBlip
    .locator("wavy-blip-card")
    .first()
    .locator("wave-blip-toolbar")
    .first()
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
  // GWT's legacy editor updates its persistent model from keyboard events.
  // insertText mutates the DOM text but can leave the editor model empty,
  // which makes toolbar selection/annotation commands no-op.
  await page.keyboard.type(phrase, { delay: 10 });
  await expect
    .poll(
      async () => await editor.evaluate((el) => (el.textContent || "").trim()),
      { message: "GWT editor must contain the typed draft", timeout: 10_000 }
    )
    .toContain(phrase);
}

async function selectTrailingWordWithGwtWebDriver(
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
        message: `GWT editor text must end with '${word}' before webdriver selection`,
        timeout: 5_000
      }
    )
    .toBe(true);
  await editor.evaluate((el: HTMLElement, targetWord: string) => {
    const win = window as typeof window & {
      webdriverEditorGetContent?: (editorDiv: Element) => string;
      webdriverEditorSetSelection?: (editorDiv: Element, start: number, end: number) => void;
    };
    if (!win.webdriverEditorGetContent || !win.webdriverEditorSetSelection) {
      throw new Error("GWT editor webdriver selection hooks are unavailable");
    }
    let editorDiv: HTMLElement | null = el;
    let content = "";
    while (editorDiv) {
      // The GWT webdriver hook expects the content-owning editor div,
      // which may be an ancestor of the editable document element.
      content = win.webdriverEditorGetContent(editorDiv) || "";
      if (
        !content.startsWith("Error in webdriverEditorGetContent") &&
        content.includes(targetWord)
      ) {
        break;
      }
      editorDiv = editorDiv.parentElement;
    }
    if (
      !editorDiv ||
      content.startsWith("Error in webdriverEditorGetContent") ||
      !content.includes(targetWord)
    ) {
      throw new Error(`GWT editor model does not contain '${targetWord}': ${content}`);
    }

    const searchUpperBound = content.length + 10;
    let firstSelectionError = "";
    for (let start = searchUpperBound; start >= 0; start -= 1) {
      for (let extraEnd = 0; extraEnd <= 4; extraEnd += 1) {
        const end = start + targetWord.length + extraEnd;
        try {
          win.webdriverEditorSetSelection(editorDiv, start, end);
        } catch (error) {
          if (!firstSelectionError) {
            firstSelectionError = error instanceof Error ? error.message : String(error);
          }
          continue;
        }
        if (window.getSelection()?.toString() === targetWord) {
          return;
        }
      }
    }
    throw new Error(
      `GWT webdriver selection could not target '${targetWord}' in content ${content}` +
        (firstSelectionError ? `; first setSelection error: ${firstSelectionError}` : "")
    );
  }, word);
  await expect
    .poll(
      async () => await editor.evaluate(() => window.getSelection()?.toString() || ""),
      {
        message: `GWT webdriver helper must select trailing '${word}'`,
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
  // GWT's editor selection controller reads the editor's native selection
  // through its SelectionHelper. Direct DOM ranges can select browser text
  // while bypassing the legacy editor model, so use the existing GWT webdriver
  // selection hook to target the same SelectionHelper state the toolbar uses.
  await selectTrailingWordWithGwtWebDriver(gwt, word);
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
  const editor = gwt.gwtActiveEditableDocument();
  await expect
    .poll(
      async () =>
        await editor.evaluate((el: HTMLElement, targetWord: string) => {
          const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
          let node = walker.nextNode();
          while (node) {
            const text = node.textContent || "";
            if (text.includes(targetWord)) {
              let current = node.parentElement;
              while (current && current !== el) {
                const tagName = current.tagName.toLowerCase();
                const inlineFontWeight = current.style.fontWeight;
                const numericInlineWeight = Number(inlineFontWeight);
                if (
                  tagName === "b" ||
                  tagName === "strong" ||
                  inlineFontWeight === "bold" ||
                  inlineFontWeight === "bolder" ||
                  numericInlineWeight >= 700
                ) {
                  return true;
                }
                current = current.parentElement;
              }
            }
            node = walker.nextNode();
          }
          return false;
        }, word),
      {
        message: `GWT bold toolbar action must apply formatting to '${word}'`,
        timeout: 5_000
      }
    )
    .toBe(true);
}

async function finishInlineReplyGwt(
  page: Page,
  gwt: GwtPage,
  initialBlipCount: number,
  draftText: string
): Promise<void> {
  await expect(
    gwt.gwtActiveEditableDocument(),
    "GWT draft must expose an active editor before submit"
  ).toBeVisible({ timeout: 5_000 });
  const done = page.locator("[data-e2e-action='edit-done']").last();
  await expect(done, "GWT edit-done action must be stable").toBeVisible({
    timeout: 10_000
  });
  await done.click({ timeout: 10_000 });
  // Wait for the editor chrome to close — edit-done disappearing confirms
  // the submit handler ran (not just that a draft blip existed beforehand).
  await expect(
    page.locator("[data-e2e-action='edit-done']"),
    "GWT edit-done chrome must close after submit"
  ).toHaveCount(0, { timeout: 15_000 });
  // Confirm a new, non-editable blip was added.
  await expect
    .poll(
      async () => await gwt.gwtBlips().count(),
      { message: "GWT reply submit must add a new blip", timeout: 25_000 }
    )
    .toBeGreaterThan(initialBlipCount);
  // The persisted blip must be visible and must not contain an open editor,
  // distinguishing it from a pre-submit draft.
  const persistedBlip = gwt.gwtBlips().filter({ hasText: draftText }).last();
  await expect(
    persistedBlip,
    "the newly submitted GWT reply blip must carry the draft text"
  ).toBeVisible({ timeout: 20_000 });
  await expect(
    // GWT keeps editabledocmarker on read-only documents, so only active editability signals count.
    persistedBlip.locator(GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR),
    "the submitted GWT reply blip must not contain an open editor (must be persisted, not a draft)"
  ).toHaveCount(0, { timeout: 5_000 });
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

    // Author a simple owned wave first. The welcome wave is public/help
    // content and can be read-only or large enough to make root-reply
    // targeting ambiguous; this parity gate needs the J2CL inline reply
    // path, not welcome-wave seeding behavior.
    const waveId = await authorSimpleGwtWave(page, BASE_URL);

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.gotoWave(waveId);
    await j2cl.assertInboxLoaded();
    await page.waitForSelector("wave-blip", { timeout: 30_000 });
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
    await page.waitForTimeout(2_500); // empirically calibrated: GWT deferred relayout before digest list renders
    const digest = page.locator("text=Welcome to SupaWave").first();
    await expect(digest).toBeVisible({ timeout: 10_000 });
    await digest.click({ timeout: 15_000 });
    await page.waitForTimeout(6_000); // empirically calibrated: GWT deferred wave-open and blip render after click

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
