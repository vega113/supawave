import { Locator, Page } from "@playwright/test";

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
      const text = `${item?.address || ""}${item?.displayName || ""}`.trim();
      return text.length > 0;
    });
    const source = `${participant?.address || participant?.displayName || ""}`.trim();
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
        cancelable: true
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
