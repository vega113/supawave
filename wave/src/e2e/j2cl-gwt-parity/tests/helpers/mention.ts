import { expect, Locator, Page } from "@playwright/test";

export type MentionStateJ2cl = {
  open: boolean;
  activeIndex: number;
  candidateCount: number;
  activeInBody: boolean;
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
