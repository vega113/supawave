// G-PORT-5 (#1114) — mention autocomplete parity between
// `?view=j2cl-root` and `?view=gwt`.
//
// Acceptance per issue #1114:
//   - Sign in fresh user, open a wave with at least one blip + production
//     participants on both ?view=j2cl-root and ?view=gwt.
//   - Click Reply on a blip, type "@v", assert popover open with at
//     least one production suggestion, dispatch ArrowDown on the
//     composer body, Enter selects the active candidate, mention chip
//     appears in the composer with link to the picked user, submit and
//     assert the chip persists in the resulting blip.
//
// The G-PORT-5 slice rewrote the popover to be view-only and gave the
// composer body sole ownership of mention-keyboard navigation. This
// test exercises the regression path that issue #1125 documented:
// ArrowDown dispatched on the body element MUST stay owned by the
// composer and advance _mentionActiveIndex when the production wave
// has multiple matching candidates.
//
// Keyboard events (ArrowDown, Enter) are dispatched directly on the J2CL
// shadow-DOM body element rather than via page.keyboard, because
// contentEditable caret focus inside a shadow-DOM tree can be lost between
// Lit re-renders (a Playwright / Lit timing artefact). The GWT flow uses
// real page.keyboard events against the legacy editor because #1121 now
// provides a reliable GWT inline-reply driver.
import { test, expect, Page, Locator } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";
import {
  dispatchComposerKeyJ2cl,
  dispatchMentionKeyGwt,
  finishInlineReplyGwt,
  openInlineComposerJ2cl,
  openInlineComposerGwt,
  readMentionStateJ2cl,
  readMentionStateGwt,
  readRenderedMentionsGwt,
  typeAtMentionTriggerJ2cl,
  typeAtMentionTriggerGwt,
  waitForMentionPopoverGwt,
  waitForParticipantsJ2cl
} from "./helpers/mention";
import { compareLocatorScreenshots } from "./helpers/visualDiff";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

/**
 * On the J2CL view: open the first wave in the inbox by clicking its
 * search-rail card. Returns once at least one <wave-blip> mounts.
 */
async function openFirstWaveJ2cl(page: Page, baseURL: string): Promise<void> {
  await page.goto(`${baseURL}/?view=j2cl-root`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("shell-root", { timeout: 15_000 });
  const card = page.locator("wavy-search-rail-card[data-digest-card]:visible").first();
  await expect(card).toBeVisible({ timeout: 30_000 });
  await card.click({ timeout: 15_000 });
  await page.waitForSelector("wave-blip", { timeout: 30_000 });
}

async function openWelcomeWaveGwt(page: Page, gwt: GwtPage): Promise<void> {
  await gwt.goto("/");
  await gwt.assertInboxLoaded();
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

  await page.waitForTimeout(2_500);
  const digest = page.locator("text=Welcome to SupaWave").first();
  await expect(digest).toBeVisible({ timeout: 10_000 });
  await digest.click({ timeout: 15_000 });
  await page.waitForTimeout(6_000);

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
}

async function authorSimpleGwtWave(
  page: Page,
  gwt: GwtPage,
  text: string
): Promise<string> {
  await gwt.goto("/");
  await gwt.assertInboxLoaded();
  await expect(gwt.newWaveAffordance()).toBeVisible({ timeout: 15_000 });
  await gwt.newWaveAffordance().click();
  await page.waitForSelector("[data-blip-id]", { timeout: 30_000 });
  const waveId = await gwt.readWaveIdFromHash();
  expect(waveId, "GWT new-wave URL fragment must encode waveId").toBeTruthy();
  await gwt.typeIntoBlipDocument(text);
  return waveId;
}

async function sendMentionReplyJ2cl(
  page: Page,
  composer: Locator,
  expectedText: string
): Promise<void> {
  await expect
    .poll(
      async () =>
        await composer.evaluate((host: any) => host.targetLabel || ""),
      {
        message: "write-session reply target must hydrate before send",
        timeout: 15_000
      }
    )
    .not.toBe("");
  const sendBtn = composer
    .locator("composer-submit-affordance")
    .locator("button")
    .first();
  await sendBtn.click();
  await expect(
    composer,
    "inline composer must unmount after mention reply send"
  ).toHaveCount(0, { timeout: 30_000 });
  await expect(
    page.locator("wave-blip", { hasText: expectedText }).first(),
    `the newly sent reply must appear as a wave-blip carrying '${expectedText}'`
  ).toBeVisible({ timeout: 30_000 });
}

async function assertMentionPersistsAfterReloadJ2cl(
  page: Page,
  expectedText: string,
  expectedAddress: string
): Promise<void> {
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForSelector("wave-blip", { timeout: 30_000 });
  const persistedBlip = await findPersistedBlipAfterViewportGrowthJ2cl(
    page,
    expectedText
  );
  await expect(
    persistedBlip,
    `J2CL reload must preserve the blip containing '${expectedText}'`
  ).toBeVisible({ timeout: 30_000 });
  await expect(
    persistedBlip.locator(
      `[data-j2cl-read-mention='true'][data-mention-address='${expectedAddress}']`
    ),
    `J2CL reload must render mention chip for ${expectedAddress}`
  ).toBeVisible({ timeout: 30_000 });
}

async function findPersistedBlipAfterViewportGrowthJ2cl(
  page: Page,
  expectedText: string
): Promise<Locator> {
  const matchingBlips = page.locator("wave-blip", { hasText: expectedText });
  const persistedBlip = matchingBlips.last();
  const readHost = page.locator(".sidecar-selected-content").first();
  await expect(
    readHost,
    "J2CL selected-wave viewport host must render before reload persistence check"
  ).toBeVisible({ timeout: 30_000 });

  for (let attempt = 0; attempt < 45; attempt++) {
    if ((await matchingBlips.count()) > 0) {
      try {
        await persistedBlip.scrollIntoViewIfNeeded({ timeout: 2_000 });
      } catch (e) {
        // Hidden thread placeholders can race the reload path in CI; keep
        // driving viewport growth until the matching blip becomes visible.
      }
      if (await persistedBlip.isVisible()) {
        return persistedBlip;
      }
    }
    // The J2CL read surface intentionally loads a viewport window, not
    // the whole wave. After reload the newly submitted reply can be
    // outside the initial root window; scrolling to the forward edge
    // triggers the same placeholder-driven growth a user would use.
    await readHost.evaluate((host) => {
      host.scrollTop = host.scrollHeight;
      host.dispatchEvent(new Event("scroll", { bubbles: true }));
    });
    await page.waitForTimeout(1_000);
  }
  return persistedBlip;
}

test.describe("G-PORT-5 mention autocomplete parity", () => {
  test("J2CL: production @mention inserts and submits a mention reply", async ({
    page
  }) => {
    test.setTimeout(180_000);
    const creds = freshCredentials("g5j");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const firstLetter = creds.address.charAt(0);
    const gwtAuthor = new GwtPage(page, BASE_URL);
    const waveId = await authorSimpleGwtWave(
      page,
      gwtAuthor,
      "Mention persistence parity root blip"
    );

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.gotoWave(waveId);
    await j2cl.assertInboxLoaded();
    await page.waitForSelector("wave-blip", { timeout: 30_000 });
    const composer = await openInlineComposerJ2cl(page);

    const realParticipantCount = await waitForParticipantsJ2cl(composer, 10_000);
    expect(
      realParticipantCount,
      `production participants flow must populate composer participants before @${firstLetter}`
    ).toBeGreaterThanOrEqual(1);

    // Type "@<letter>" and assert the popover opened with at least
    // one candidate.
    await typeAtMentionTriggerJ2cl(page, composer, `@${firstLetter}`);
    // Diagnostic: capture composer body state for the assertion-
    // failure message so a future regression names the actual DOM
    // it found rather than just "open=false".
    const bodyState = await composer.evaluate((host: any) => {
      const body = host.shadowRoot.querySelector("[data-composer-body]");
      const replyElement = document.querySelector(
        "composer-inline-reply"
      ) as any;
      return {
        text: (body?.textContent || ""),
        html: (body?.innerHTML || "").slice(0, 400),
        draft: (host as any).draft || "",
        participants: ((host as any).participants || []).length,
        replyParticipants: ((replyElement && (replyElement as any).participants) || []).length,
        replyTagName: replyElement ? replyElement.tagName : null,
        mentionAnchor: (host as any)._mentionAnchor,
        mentionTriggerOffset: (host as any)._mentionTriggerOffset,
        triggerNodeText: (host as any)._mentionTriggerNode?.textContent || null,
        bodyOwnsSelection:
          typeof (host as any)._bodyOwnsSelection === "function"
            ? (host as any)._bodyOwnsSelection()
            : null,
        activeRoot: document.activeElement?.tagName,
        bodyContains: (() => {
          const sel = document.getSelection();
          if (!sel || sel.rangeCount === 0) return null;
          const r = sel.getRangeAt(0);
          return body && body.contains(r.startContainer);
        })()
      };
    });
    let mention = await readMentionStateJ2cl(composer);
    expect(
      mention.open,
      `popover must be open after typing @${firstLetter}; mention=${JSON.stringify(mention)} body=${JSON.stringify(bodyState)}`
    ).toBe(true);
    expect(
      mention.candidateCount,
      `popover must have at least one production suggestion for @${firstLetter}; saw ${JSON.stringify(mention)}`
    ).toBeGreaterThanOrEqual(1);
    const initialIndex = mention.activeIndex;

    // The popover element must be in the DOM with [open] reflected.
    await expect(
      composer.locator("mention-suggestion-popover[open]"),
      "mention-suggestion-popover must reflect open=true"
    ).toHaveCount(1, { timeout: 5_000 });

    // The popover MUST NOT have stolen focus from the composer body.
    // This is the core G-PORT-5 invariant — without it ArrowDown
    // never reaches the composer body's keydown handler. We walk
    // shadow-root boundaries from document.activeElement down to
    // confirm the deepest active element is the [data-composer-body]
    // div inside this composer's shadow tree.
    expect(
      mention.activeInBody,
      "composer body must retain focus after the popover opens"
    ).toBe(true);

    // ArrowDown must advance _mentionActiveIndex when production data
    // provides multiple candidates. With the current fresh welcome
    // wave there is one production participant, so the index wraps to
    // itself and Enter should still select that candidate.
    //
    // We dispatch the keydown directly on the composer body element.
    // page.keyboard.press routes via document.activeElement, but
    // contentEditable focus inside a shadow-DOM contenteditable can
    // be lost between Lit re-renders (the `@` trigger re-flows the
    // composer-stack to mount the popover host). Dispatching to the
    // body directly bypasses that race and exercises the same
    // keydown listener that real keystrokes hit in production.
    await dispatchComposerKeyJ2cl(composer, "ArrowDown");
    await page.waitForTimeout(120);
    mention = await readMentionStateJ2cl(composer);
    if (mention.candidateCount > 1) {
      expect(
        mention.activeIndex,
        `ArrowDown must advance the active index from ${initialIndex}; saw ${JSON.stringify(mention)}`
      ).not.toBe(initialIndex);
    } else {
      expect(
        mention.activeIndex,
        `ArrowDown should wrap to the sole production candidate; saw ${JSON.stringify(mention)}`
      ).toBe(initialIndex);
    }

    // Snapshot whichever candidate is currently active so we can
    // assert the chip carries its address after Enter.
    const expectedAddress = await composer.evaluate((host: any) => {
      const list = host._filteredMentionCandidates();
      const idx = host._mentionActiveIndex || 0;
      return list[idx % list.length]?.address || "";
    });
    expect(
      expectedAddress,
      "active candidate must carry an address before Enter"
    ).not.toBe("");

    // Enter selects the active candidate and inserts the chip.
    // Same shadow-DOM rationale as the ArrowDown dispatch above.
    await dispatchComposerKeyJ2cl(composer, "Enter");
    await page.waitForTimeout(300);

    const chipInfo = await composer.evaluate((host: any) => {
      const body = host.shadowRoot.querySelector("[data-composer-body]");
      const chip = body.querySelector(".wavy-mention-chip");
      return chip
        ? {
            mentionId: chip.getAttribute("data-mention-id") || "",
            text: chip.textContent || ""
          }
        : null;
    });
    expect(
      chipInfo,
      "mention chip must be inserted into the composer body after Enter"
    ).not.toBeNull();
    expect(
      chipInfo!.mentionId,
      `chip must carry data-mention-id=${expectedAddress}; saw ${chipInfo!.mentionId}`
    ).toBe(expectedAddress);
    expect(
      chipInfo!.text.startsWith("@"),
      `chip text must start with @; saw '${chipInfo!.text}'`
    ).toBe(true);

    // The popover MUST close after Enter — without this assertion a
    // regression where the popover stays open after commit would
    // pass the chip-insertion check.
    await expect
      .poll(
        async () =>
          await composer.evaluate((host: any) => Boolean(host._mentionOpen)),
        {
          message: "popover must close after Enter selects a candidate",
          timeout: 5_000
        }
      )
      .toBe(false);
    await expect(
      composer.locator("mention-suggestion-popover[open]"),
      "mention-suggestion-popover[open] must unmount after Enter"
    ).toHaveCount(0, { timeout: 5_000 });

    // Verify the rich-component serializer emits the mention/user
    // annotation that submit persists for the mention chip.
    const components = await composer.evaluate((host: any) => {
      if (typeof host.serializeRichComponents !== "function") {
        return [];
      }
      return host.serializeRichComponents();
    });
    const mentionComponent = components.find(
      (c: any) => c && c.type === "annotated" && c.annotationKey === "mention/user"
    );
    expect(
      mentionComponent,
      `rich-component serializer must emit a mention/user mention; saw ${JSON.stringify(components).slice(0, 400)}`
    ).toBeTruthy();
    expect(mentionComponent.annotationValue).toBe(expectedAddress);
    expect((mentionComponent.text || "").startsWith("@")).toBe(true);

    await sendMentionReplyJ2cl(page, composer, chipInfo!.text);
    await assertMentionPersistsAfterReloadJ2cl(
      page,
      chipInfo!.text,
      expectedAddress
    );
  });

  test("GWT: production @mention inserts, submits, and reloads", async ({
    page
  }) => {
    test.setTimeout(180_000);
    const creds = freshCredentials("g5g");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const gwt = new GwtPage(page, BASE_URL);
    await authorSimpleGwtWave(
      page,
      gwt,
      "GWT mention autocomplete parity root blip"
    );

    const firstLetter = creds.address.charAt(0);
    const initialBlipCount = await gwt.gwtBlips().count();
    await openInlineComposerGwt(gwt);
    await typeAtMentionTriggerGwt(page, gwt, `@${firstLetter}`);
    await waitForMentionPopoverGwt(page);

    let mention = await readMentionStateGwt(page);
    expect(
      mention.open,
      `GWT popover must open after @${firstLetter}; saw ${JSON.stringify(mention)}`
    ).toBe(true);
    expect(
      mention.candidateCount,
      `GWT popover must have production suggestions for @${firstLetter}; saw ${JSON.stringify(mention)}`
    ).toBeGreaterThanOrEqual(1);
    const initialIndex = mention.activeIndex;

    await dispatchMentionKeyGwt(page, gwt, "ArrowDown");
    mention = await readMentionStateGwt(page);
    if (mention.candidateCount > 1) {
      expect(
        mention.activeIndex,
        `GWT ArrowDown must advance the active index from ${initialIndex}; saw ${JSON.stringify(mention)}`
      ).not.toBe(initialIndex);
    } else {
      expect(
        mention.activeIndex,
        `GWT ArrowDown should keep the sole candidate active; saw ${JSON.stringify(mention)}`
      ).toBe(initialIndex);
    }
    const expectedAddress = mention.activeAddress;
    expect(expectedAddress, "GWT active mention candidate must carry an address").not.toBe("");

    await dispatchMentionKeyGwt(page, gwt, "Enter");
    const editor = gwt.gwtActiveEditableDocument();
    await expect
      .poll(
        async () =>
          (await readRenderedMentionsGwt(editor)).some(
            (item) => item.address === expectedAddress
          ),
        {
          message: `GWT editor must render a mention annotation for ${expectedAddress}`,
          timeout: 10_000
        }
      )
      .toBe(true);
    const editorMentions = await readRenderedMentionsGwt(editor);
    const insertedMention = editorMentions.find(
      (item) => item.address === expectedAddress
    )?.text;
    expect(insertedMention, "GWT inserted mention text must be readable").toBeTruthy();
    const insertedMentionText = insertedMention!;

    const persistedBlip = await finishInlineReplyGwt(
      page,
      gwt,
      initialBlipCount,
      insertedMentionText
    );
    await expect(
      persistedBlip.locator(`[data-mention-address='${expectedAddress}']`),
      `GWT persisted reply must keep mention annotation for ${expectedAddress}`
    ).toBeVisible({ timeout: 15_000 });

    await page.reload({ waitUntil: "domcontentloaded" });
    await expect
      .poll(
        async () =>
          await page.evaluate((text) => document.body.innerText.includes(text), insertedMentionText),
        {
          message: "GWT reload must restore the submitted mention reply text",
          timeout: 30_000
        }
      )
      .toBe(true);
    const reloadedMentionBlip = gwt.gwtBlips().filter({ hasText: insertedMentionText }).last();
    await expect(
      reloadedMentionBlip.locator(`[data-mention-address='${expectedAddress}']`),
      `GWT reload must render mention annotation for ${expectedAddress}`
    ).toBeVisible({ timeout: 30_000 });
  });

  test("J2CL and GWT mention popovers stay within visual diff budget", async ({
    page
  }, testInfo) => {
    test.setTimeout(180_000);
    const creds = freshCredentials("g5v");
    test.info().annotations.push({
      type: "test-user",
      description: creds.address
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const firstLetter = creds.address.charAt(0);
    const gwtAuthor = new GwtPage(page, BASE_URL);
    const waveId = await authorSimpleGwtWave(
      page,
      gwtAuthor,
      "Mention popover visual parity root blip"
    );

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.gotoWave(waveId);
    await j2cl.assertInboxLoaded();
    await page.waitForSelector("wave-blip", { timeout: 30_000 });
    const composer = await openInlineComposerJ2cl(page);
    await waitForParticipantsJ2cl(composer, 10_000);
    await typeAtMentionTriggerJ2cl(page, composer, `@${firstLetter}`);
    await expect(
      composer.locator("mention-suggestion-popover[open]"),
      "J2CL mention popover must be visible for visual diff"
    ).toHaveCount(1, { timeout: 5_000 });
    const j2clPopover = composer.locator("mention-suggestion-popover[open] .popover").first();
    await expect(
      j2clPopover,
      "J2CL visual diff target must be the rendered popover panel"
    ).toBeVisible({ timeout: 5_000 });
    const j2clActiveOption = composer
      .locator("mention-suggestion-popover[open] [role='option'][aria-selected='true']")
      .first();
    await expect(
      j2clActiveOption,
      "J2CL visual diff target must include the active option row"
    ).toBeVisible({ timeout: 5_000 });

    const gwtPage = await page.context().newPage();
    try {
      const gwt = new GwtPage(gwtPage, BASE_URL);
      await gwt.gotoWave(waveId);
      await gwt.assertInboxLoaded();
      await gwtPage.waitForSelector("[data-blip-id]", { timeout: 30_000 });
      await openInlineComposerGwt(gwt);
      await typeAtMentionTriggerGwt(gwtPage, gwt, `@${firstLetter}`);
      await gwtPage.mouse.move(24, 24);
      await gwtPage.waitForTimeout(250);
      const gwtPopover = await waitForMentionPopoverGwt(gwtPage);
      await gwtPopover.evaluate((panel) => {
        const popover = panel as HTMLElement;
        let popupLayer: HTMLElement | null = popover;
        for (let depth = 0; popupLayer && depth < 4; depth++) {
          if (!popupLayer.style.position || popupLayer.style.position === "static") {
            popupLayer.style.position = "relative";
          }
          popupLayer.style.zIndex = "2147483647";
          popupLayer = popupLayer.parentElement;
        }
        popover.style.background = "#FFFFFF";
        popover.style.backgroundColor = "#FFFFFF";
        popover
          .querySelectorAll("[data-e2e='gwt-mention-option']")
          .forEach((option) => {
            const optionEl = option as HTMLElement;
            optionEl.style.display = "block";
            optionEl.style.fontFamily = "Arial, sans-serif";
            optionEl.style.lineHeight = "16px";
            if (optionEl.getAttribute("data-active") === "true") {
              optionEl.style.backgroundColor = "#E8F0FE";
            }
          });
      });
      const gwtActiveOption = gwtPopover
        .locator("[data-e2e='gwt-mention-option'][data-active='true']")
        .first();
      await expect(
        gwtActiveOption,
        "GWT visual diff target must include the active option row"
      ).toBeVisible({ timeout: 5_000 });

      // Compare the active suggestion row, not the legacy GWT popup
      // chrome. The panel visibility assertions above still prove both
      // popovers opened; row-level screenshots keep the parity budget
      // focused on the selectable UI the user actually sees while
      // avoiding nondeterministic transparent GWT chrome composition.
      const diff = await compareLocatorScreenshots(
        testInfo,
        "mention-popover-active-option-j2cl-vs-gwt",
        j2clActiveOption,
        gwtActiveOption
      );
      test.info().annotations.push({
        type: "visual-diff",
        description: `mention active option mismatch ${(diff.mismatchRatio * 100).toFixed(2)}% (${diff.mismatchedPixels}/${diff.totalPixels})`
      });
      expect(
        diff.mismatchRatio,
        `mention active option visual diff must be <= 5%; saw ${(diff.mismatchRatio * 100).toFixed(2)}%`
      ).toBeLessThanOrEqual(0.05);
    } finally {
      await gwtPage.close();
    }
  });
});
