import { expect, Locator, Page } from "@playwright/test";
import {
  GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR,
  GwtPage
} from "../../pages/GwtPage";

export type MentionStateJ2cl = {
  open: boolean;
  activeIndex: number;
  candidateCount: number;
  activeInBody: boolean;
};

export type MentionStateGwt = {
  open: boolean;
  activeIndex: number;
  candidateCount: number;
  activeText: string;
  activeAddress: string;
};

export async function waitForParticipantsJ2cl(
  composer: Locator,
  timeoutMs: number
): Promise<number> {
  const deadline = Date.now() + timeoutMs;
  let best = 0;
  while (Date.now() < deadline) {
    const count = await composer.evaluate((host: any) =>
      Array.isArray(host.participants) ? host.participants.length : 0
    );
    if (count > best) best = count;
    if (best >= 1) return best;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return best;
}

export async function readMentionTriggerLetterJ2cl(
  composer: Locator
): Promise<string> {
  return await composer.evaluate((host: any) => {
    const participants = Array.isArray(host.participants) ? host.participants : [];
    const participant = participants.find((item: any) => {
      const address = `${item?.address || ""}`.trim();
      return address.length > 0;
    });
    const source = `${participant?.address || ""}`.trim();
    return source.charAt(0).toLowerCase();
  });
}

export async function typeAtMentionTriggerJ2cl(
  page: Page,
  composer: Locator,
  literal: string
): Promise<void> {
  const body = composer.locator("[data-composer-body]");
  await body.click();
  await page.waitForTimeout(400);
  await composer.evaluate(
    (host: any, text: string) => {
      const b = host.shadowRoot?.querySelector("[data-composer-body]");
      if (!b) {
        throw new Error("typeAtMentionTriggerJ2cl: no [data-composer-body]");
      }
      b.focus();
      const node = document.createTextNode(text);
      b.appendChild(node);
      const end = document.createRange();
      end.setStart(node, text.length);
      end.setEnd(node, text.length);
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(end);
      b.dispatchEvent(new InputEvent("input", { bubbles: true }));
    },
    literal
  );
  await page.waitForTimeout(250);
}

export async function dispatchComposerKeyJ2cl(
  composer: Locator,
  key: string
): Promise<void> {
  await composer.evaluate((host: any, keyName: string) => {
    const body = host.shadowRoot?.querySelector("[data-composer-body]");
    if (!body) {
      throw new Error("dispatchComposerKeyJ2cl: no [data-composer-body]");
    }
    body.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: keyName,
        bubbles: true,
        cancelable: true,
        composed: true
      })
    );
  }, key);
}

export async function readMentionStateJ2cl(
  composer: Locator
): Promise<MentionStateJ2cl> {
  return await composer.evaluate((host: any) => {
    const body = host.shadowRoot?.querySelector("[data-composer-body]");
    let active: any = document.activeElement;
    while (active && active.shadowRoot && active.shadowRoot.activeElement) {
      active = active.shadowRoot.activeElement;
    }
    const candidates =
      typeof host._filteredMentionCandidates === "function"
        ? host._filteredMentionCandidates()
        : [];
    return {
      open: Boolean(host._mentionOpen),
      activeIndex: Number(host._mentionActiveIndex || 0),
      candidateCount: candidates.length,
      activeInBody: active === body
    };
  });
}

export async function waitForMentionPopoverGwt(page: Page): Promise<Locator> {
  const popover = page.locator("[data-e2e='gwt-mention-popover']:visible").last();
  await expect(popover, "GWT mention popover must open").toBeVisible({
    timeout: 10_000
  });
  return popover;
}

export async function readMentionStateGwt(page: Page): Promise<MentionStateGwt> {
  const popover = page.locator("[data-e2e='gwt-mention-popover']:visible").last();
  const open = await popover.count().then((count) => count > 0);
  if (!open) {
    return {
      open: false,
      activeIndex: -1,
      candidateCount: 0,
      activeText: "",
      activeAddress: ""
    };
  }
  const options = popover.locator("[data-e2e='gwt-mention-option']");
  const candidateCount = await options.count();
  const activeOptions = popover.locator(
    "[data-e2e='gwt-mention-option'][data-active='true']"
  );
  const activeCount = await activeOptions.count();
  const active = activeCount > 0 ? activeOptions.first() : options.first();
  const activeText = candidateCount > 0 ? (await active.textContent()) || "" : "";
  const activeAddress =
    candidateCount > 0 ? (await active.getAttribute("data-mention-address")) || "" : "";
  const activeIndex =
    candidateCount > 0
      ? await options.evaluateAll((nodes) =>
          nodes.findIndex((node) => node.getAttribute("data-active") === "true")
        )
      : -1;
  return {
    open,
    activeIndex,
    candidateCount,
    activeText: activeText.trim(),
    activeAddress
  };
}

export async function typeAtMentionTriggerGwt(
  page: Page,
  gwt: GwtPage,
  literal: string
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
      { message: "GWT editor must own focus before typing a mention trigger", timeout: 5_000 }
    )
    .toBe(true);
  await page.keyboard.type(literal, { delay: 20 });
  await page.waitForTimeout(350);
}

export async function dispatchMentionKeyGwt(page: Page, key: string): Promise<void> {
  await page.keyboard.press(key);
  await page.waitForTimeout(150);
}

export async function readRenderedMentionsGwt(scope: Locator): Promise<
  Array<{ address: string; text: string }>
> {
  return await scope.locator("[data-mention-address]").evaluateAll((nodes) =>
    nodes.map((node) => ({
      address: node.getAttribute("data-mention-address") || "",
      text: (node.textContent || "").trim()
    }))
  );
}

/**
 * Click Reply on the first <wave-blip> and return the inline composer
 * locator after it mounts. The retry loop handles the same transient
 * wave-blip replacement during snapshot hydration that the G-PORT-5
 * mention-autocomplete parity test already protects against.
 */
export async function openInlineComposerJ2cl(page: Page): Promise<Locator> {
  await page.waitForTimeout(1_500);
  const firstBlip = page.locator("wave-blip").first();
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      await firstBlip.scrollIntoViewIfNeeded({ timeout: 5_000 });
      await firstBlip.hover({ timeout: 5_000 });
      await firstBlip
        .locator("wave-blip-toolbar")
        .locator("button[data-toolbar-action='reply']")
        .click({ timeout: 10_000 });
      break;
    } catch (e) {
      if (attempt === 3) throw e;
      await page.waitForTimeout(800);
    }
  }
  const inlineComposer = firstBlip.locator(
    "wavy-composer[data-inline-composer='true']"
  );
  await expect(
    inlineComposer,
    "Reply must mount <wavy-composer> inline at the blip"
  ).toHaveCount(1, { timeout: 10_000 });
  return inlineComposer;
}

export async function openInlineComposerGwt(gwt: GwtPage): Promise<void> {
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

export async function finishInlineReplyGwt(
  page: Page,
  gwt: GwtPage,
  initialBlipCount: number,
  draftText: string
): Promise<Locator> {
  await expect(
    gwt.gwtActiveEditableDocument(),
    "GWT draft must expose an active editor before submit"
  ).toBeVisible({ timeout: 5_000 });
  const done = page.locator("[data-e2e-action='edit-done']").last();
  await expect(done, "GWT edit-done action must be stable").toBeVisible({
    timeout: 10_000
  });
  await done.click({ timeout: 10_000 });
  await expect(
    page.locator("[data-e2e-action='edit-done']"),
    "GWT edit-done chrome must close after submit"
  ).toHaveCount(0, { timeout: 15_000 });
  await expect
    .poll(
      async () => await gwt.gwtBlips().count(),
      { message: "GWT reply submit must add a new blip", timeout: 25_000 }
    )
    .toBeGreaterThan(initialBlipCount);
  const persistedBlip = gwt.gwtBlips().filter({ hasText: draftText }).last();
  await expect(
    persistedBlip,
    "the newly submitted GWT reply blip must carry the mention text"
  ).toBeVisible({ timeout: 20_000 });
  await expect(
    persistedBlip.locator(GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR),
    "the submitted GWT reply blip must not contain an open editor"
  ).toHaveCount(0, { timeout: 5_000 });
  return persistedBlip;
}
