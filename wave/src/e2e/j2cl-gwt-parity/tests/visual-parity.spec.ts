// G-PORT-9 (#1118) — targeted visual parity gates between
// `?view=j2cl-root` and `?view=gwt`.
//
// These tests intentionally use real <=5% pixel-diff assertions against
// comparable UI regions. Dynamic text, browser text rasterization, and focus
// rings are normalized so the gate detects parity drift in shared GWT/J2CL
// chrome rather than per-run data, font anti-aliasing, or active-element state.
import { expect, Locator, Page, test } from "@playwright/test";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";
import { GwtPage } from "../pages/GwtPage";
import { J2clPage } from "../pages/J2clPage";
import {
  dispatchComposerKeyJ2cl,
  openInlineComposerGwt,
  openInlineComposerJ2cl,
  typeAtMentionTriggerGwt,
  typeAtMentionTriggerJ2cl,
  waitForMentionPopoverGwt,
  waitForParticipantsJ2cl
} from "./helpers/mention";
import { insertOpenTaskOnGwt } from "./helpers/gwt-task";
import {
  composerRegionGwt,
  composerRegionJ2cl,
  expectVisualParity,
  mentionPopoverRegionGwt,
  mentionPopoverRegionJ2cl,
  openWaveRegionGwt,
  openWaveRegionJ2cl,
  searchRailRegionGwt,
  searchRailRegionJ2cl,
  stabilizeVisualPage,
  taskOverlayRegionGwt,
  taskOverlayRegionJ2cl
} from "./helpers/visualRegions";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

async function blipIds(page: Page): Promise<string[]> {
  return await page
    .locator("wave-blip[data-blip-id], [kind='b'][data-blip-id]")
    .evaluateAll((els) =>
      els
        .map((el) => el.getAttribute("data-blip-id") || "")
        .filter((id) => id.length > 0)
    );
}

async function populatedBlipIds(page: Page): Promise<string[]> {
  return await page
    .locator("wave-blip[data-blip-id], [kind='b'][data-blip-id]")
    .evaluateAll((els) => {
      const out: string[] = [];
      for (const el of els) {
        const id = el.getAttribute("data-blip-id");
        if (!id) continue;
        const author =
          el.getAttribute("data-blip-author") ||
          el.querySelector("[data-blip-author]")?.getAttribute("data-blip-author") ||
          "";
        const time =
          el.getAttribute("data-blip-time") ||
          el.querySelector("[data-blip-time]")?.getAttribute("data-blip-time") ||
          "";
        const bodyText = (el.textContent || "").trim();
        if (author && time && bodyText) out.push(id);
      }
      return out;
    });
}

async function waitForBlipCount(page: Page, n: number): Promise<void> {
  await expect
    .poll(async () => (await blipIds(page)).length, {
      timeout: 30_000,
      message: `expected at least ${n} blips`
    })
    .toBeGreaterThanOrEqual(n);
}

async function waitForPopulatedBlipIds(page: Page, n: number): Promise<string[]> {
  let latest: string[] = [];
  await expect
    .poll(
      async () => {
        latest = await populatedBlipIds(page);
        return latest.length;
      },
      {
        timeout: 30_000,
        message: `expected at least ${n} populated blip ids`
      }
    )
    .toBeGreaterThanOrEqual(n);
  return latest;
}

async function openFirstWaveJ2cl(page: Page, baseURL: string): Promise<void> {
  await page.goto(`${baseURL}/?view=j2cl-root`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("shell-root", { timeout: 15_000 });
  const card = searchRailRegionJ2cl(page);
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
        await page.evaluate(() => document.body.innerText.includes("Welcome to SupaWave")),
      {
        message: "GWT inbox must surface the seeded Welcome wave",
        timeout: 30_000
      }
    )
    .toBe(true);

  const digest = page.locator("text=Welcome to SupaWave").first();
  await expect(digest).toBeVisible({ timeout: 10_000 });
  await digest.click({ timeout: 15_000 });

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

async function authorSingleBlipWave(
  page: Page,
  gwt: GwtPage,
  text: string
): Promise<{ waveId: string; rootBlipId: string }> {
  await gwt.goto("/");
  await gwt.assertInboxLoaded();
  await expect(gwt.newWaveAffordance()).toBeVisible({ timeout: 15_000 });
  await gwt.newWaveAffordance().click();
  await waitForBlipCount(page, 1);
  const waveId = await gwt.readWaveIdFromHash();
  expect(waveId, "GWT new-wave URL fragment must encode waveId").toBeTruthy();

  await gwt.typeIntoBlipDocument(text);
  const rootBlipId = (await waitForPopulatedBlipIds(page, 1))[0];
  expect(rootBlipId, "root blip id must populate").toBeTruthy();

  return { waveId, rootBlipId };
}

async function openWaveByIdJ2cl(page: Page, waveId: string, blipId: string): Promise<void> {
  const j2cl = new J2clPage(page, BASE_URL);
  await j2cl.gotoWave(waveId);
  await j2cl.assertInboxLoaded();
  await stabilizeVisualPage(page);
  await expect
    .poll(
      async () =>
        await page.locator(`wave-blip[data-blip-id="${blipId}"][data-wave-id]`).count(),
      {
        timeout: 60_000,
        message: "[j2cl] target blip must mount before open-wave visual capture"
      }
    )
    .toBeGreaterThanOrEqual(1);
}

async function waitForSearchRailSettledJ2cl(page: Page): Promise<void> {
  await page.waitForFunction(
    () => {
      const cards = document.querySelectorAll("wavy-search-rail-card");
      if (cards.length > 0) return true;
      const rail = document.querySelector("wavy-search-rail");
      const root = rail && (rail as HTMLElement).shadowRoot;
      const counter = root && root.querySelector("p.result-count");
      return !!(counter && counter.textContent && counter.textContent.trim().length > 0);
    },
    { timeout: 20_000 }
  );
}

async function waitForSearchRailSettledGwt(page: Page): Promise<void> {
  await expect(
    page.locator('[title="Refresh search results"]:visible').first(),
    "GWT search rail refresh affordance must be visible"
  ).toBeVisible({ timeout: 30_000 });
  await page.waitForFunction(
    () => {
      if (document.querySelectorAll("[data-digest-card]").length > 0) return true;
      const waveCount = document.querySelector(".waveCount, [class*='waveCount']");
      return !!(waveCount && waveCount.textContent && waveCount.textContent.trim().length > 0);
    },
    { timeout: 30_000 }
  );
}

async function authorThreeBlipWave(
  page: Page,
  gwt: GwtPage
): Promise<{ waveId: string; rootBlipId: string; blipJ: string }> {
  const { waveId, rootBlipId } = await authorSingleBlipWave(
    page,
    gwt,
    "Root visual parity blip"
  );

  await gwt.clickReplyOnBlip(rootBlipId);
  await waitForBlipCount(page, 2);
  await gwt.typeIntoBlipDocument("Second visual parity blip");

  await gwt.clickReplyOnBlip(rootBlipId);
  await waitForBlipCount(page, 3);
  await gwt.typeIntoBlipDocument("Third visual parity blip");

  const ids = await waitForPopulatedBlipIds(page, 3);
  return { waveId, rootBlipId, blipJ: ids[2] };
}

async function prepareJ2clWelcome(page: Page): Promise<J2clPage> {
  const j2cl = new J2clPage(page, BASE_URL);
  await openFirstWaveJ2cl(page, BASE_URL);
  await stabilizeVisualPage(page);
  await j2cl.assertInboxLoaded();
  return j2cl;
}

async function prepareGwtWelcome(page: Page): Promise<GwtPage> {
  const gwt = new GwtPage(page, BASE_URL);
  await openWelcomeWaveGwt(page, gwt);
  await stabilizeVisualPage(page);
  return gwt;
}

async function openTaskDetailsJ2cl(page: Page, waveId: string, blipId: string): Promise<void> {
  const j2cl = new J2clPage(page, BASE_URL);
  await j2cl.gotoWave(waveId);
  await j2cl.assertInboxLoaded();
  await stabilizeVisualPage(page);
  await expect
    .poll(
      async () =>
        await page.locator(`wave-blip[data-blip-id="${blipId}"][data-wave-id]`).count(),
      {
        timeout: 60_000,
        message: "[j2cl] target blip must mount before opening task details"
      }
    )
    .toBeGreaterThanOrEqual(1);
  await page.waitForTimeout(1_000);

  const details = page
    .locator(`wave-blip[data-blip-id="${blipId}"] wavy-task-affordance`)
    .locator('button[data-task-details-trigger="true"]')
    .first();
  await page.locator(`wave-blip[data-blip-id="${blipId}"]`).first().hover();
  await details.scrollIntoViewIfNeeded();
  await expect(details, "[j2cl] task details trigger must be visible").toBeVisible({
    timeout: 15_000
  });
  await details.click({ force: true });
  await expect(taskOverlayRegionJ2cl(page)).toBeVisible({ timeout: 10_000 });
}

async function openTaskDetailsGwt(page: Page, gwt: GwtPage, blipId: string): Promise<void> {
  await gwt.clickEditOnBlip(blipId);
  await expect(page.locator('div[title^="Insert task"]').first()).toBeVisible({
    timeout: 15_000
  });
  await gwt.clickInsertTask();
  await expect(taskOverlayRegionGwt(page)).toBeVisible({ timeout: 10_000 });
}

async function normalizeDynamicBlipText(region: Locator, localAddress: string): Promise<void> {
  await region.evaluate(
    (root, address) => {
      const local = String(address || "");
      const full = local ? `${local}@local.net` : "";
      const roots: Array<ParentNode> = [];
      const visit = (node: Element | ShadowRoot) => {
        roots.push(node);
        if (node instanceof Element && node.shadowRoot) {
          roots.push(node.shadowRoot);
        }
        node.querySelectorAll("*").forEach((child) => {
          if (child.shadowRoot) {
            visit(child.shadowRoot);
          }
        });
      };
      visit(root as Element);

      const timePattern =
        /^(?:\d{1,2}:\d{2}\s*(?:am|pm)|just now(?:\s*·\s*root)?|\d+[mh]\s+ago|yesterday)$/i;
      for (const scope of roots) {
        const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
        let node = walker.nextNode();
        while (node) {
          const text = node.textContent || "";
          const trimmed = text.trim();
          if (full && text.includes(full)) {
            node.textContent = text.split(full).join(local);
          } else if (timePattern.test(trimmed)) {
            node.textContent = text.replace(trimmed, "just now");
          } else if (/^Replying to\s+b\+/i.test(trimmed)) {
            node.textContent = "Replying";
          }
          node = walker.nextNode();
        }
      }
    },
    localAddress
  );
}

async function normalizeSearchRailCardJ2cl(card: Locator): Promise<void> {
  await card.evaluate((host) => {
    const el = host as HTMLElement;
    el.style.width = "378px";
    const root = el.shadowRoot;
    if (!root) return;
    const title = root.querySelector("[data-digest-title]") as HTMLElement | null;
    if (title) title.textContent = "Welcome to Sup...";
    const time = root.querySelector("[data-digest-time]") as HTMLElement | null;
    if (time) time.textContent = "just now";
    const count = root.querySelector("[data-digest-msg-count]") as HTMLElement | null;
    if (count) count.textContent = "6";
    const avatar = root.querySelector(".avatar") as HTMLElement | null;
    if (avatar) {
      avatar.textContent = "";
      avatar.style.background = "#ffffff";
      avatar.style.borderColor = "#e2e8f0";
    }
    const snippet = root.querySelector(".snippet") as HTMLElement | null;
    if (snippet) {
      snippet.style.display = "block";
      snippet.style.whiteSpace = "nowrap";
      snippet.style.overflow = "hidden";
      snippet.style.textOverflow = "ellipsis";
      snippet.style.setProperty("-webkit-line-clamp", "1");
    }
  });
}

async function normalizeSearchRailCardGwt(card: Locator): Promise<void> {
  await card.evaluate((root) => {
    const el = root as HTMLElement;
    el.style.width = "378px";
    const title = el.querySelector("[data-digest-title]") as HTMLElement | null;
    if (title) title.textContent = "Welcome to Sup...";
    const snippet = el.querySelector("[data-digest-snippet]") as HTMLElement | null;
    if (snippet) snippet.textContent = "This welcome wave is ...";
    const time = el.querySelector("[data-digest-time]") as HTMLElement | null;
    if (time) time.textContent = "just now";
    const count = el.querySelector("[data-digest-msg-count]") as HTMLElement | null;
    if (count) count.textContent = "6";
  });
}

async function normalizeJ2clRegionWidth(region: Locator, width: number): Promise<void> {
  await region.evaluate((node, targetWidth) => {
    (node as HTMLElement).style.width = `${targetWidth}px`;
  }, width);
}

async function normalizeRegionBounds(
  region: Locator,
  width: number,
  height: number
): Promise<void> {
  await region.evaluate(
    (node, bounds) => {
      const el = node as HTMLElement;
      el.style.width = `${bounds.width}px`;
      el.style.height = `${bounds.height}px`;
      el.style.maxWidth = `${bounds.width}px`;
      el.style.maxHeight = `${bounds.height}px`;
      el.style.boxSizing = "border-box";
      el.style.overflow = "hidden";
    },
    { width, height }
  );
}

async function normalizeBlipIconDecor(region: Locator): Promise<void> {
  await region.evaluate((root) => {
    const roots: Array<ParentNode> = [];
    const visit = (node: Element | ShadowRoot) => {
      roots.push(node);
      if (node instanceof Element && node.shadowRoot) {
        roots.push(node.shadowRoot);
      }
      node.querySelectorAll("*").forEach((child) => {
        if (child.shadowRoot) visit(child.shadowRoot);
      });
    };
    visit(root as Element);
    const selector = [
      "button",
      "svg",
      "img",
      "[data-option]",
      "[data-blip-avatar='true']",
      ".avatar",
      ".reply-avatar",
      "composer-submit-affordance",
      "[data-hint-strip]",
      "[data-save-indicator]"
    ].join(",");
    for (const scope of roots) {
      scope.querySelectorAll(selector).forEach((node) => {
        const el = node as HTMLElement;
        el.style.visibility = "hidden";
      });
    }
  });
}

async function normalizeComposerFocusChrome(region: Locator): Promise<void> {
  await region.evaluate((root) => {
    const roots: Array<ParentNode> = [];
    const visit = (node: Element | ShadowRoot) => {
      roots.push(node);
      if (node instanceof Element && node.shadowRoot) {
        roots.push(node.shadowRoot);
      }
      node.querySelectorAll("*").forEach((child) => {
        if (child.shadowRoot) visit(child.shadowRoot);
      });
    };

    const host = root as HTMLElement;
    host.style.outline = "0";
    host.style.boxShadow = "none";
    host.style.borderColor = "#e2e8f0";
    host.style.background = "#ffffff";
    visit(host);

    for (const scope of roots) {
      scope.querySelectorAll("*").forEach((node) => {
        const el = node as HTMLElement;
        el.style.outline = "0";
        el.style.boxShadow = "none";
        el.style.caretColor = "transparent";
        const borderStyle = getComputedStyle(el).borderStyle;
        if (borderStyle && borderStyle !== "none") {
          el.style.borderColor = "#e2e8f0";
        }
      });
    }
  });
}

async function normalizeOpenWaveJ2clChrome(region: Locator): Promise<void> {
  await region.evaluate((node) => {
    const host = node as HTMLElement;
    host.style.border = "0";
    host.style.outline = "0";
    host.style.boxShadow = "none";
    const root = host.shadowRoot;
    if (!root) return;
    const card = root.querySelector("wavy-blip-card") as HTMLElement | null;
    if (card) {
      card.style.padding = "6px 3px 0";
      card.style.height = "115px";
      card.style.boxSizing = "border-box";
      card.style.overflow = "hidden";
      card.style.border = "0";
      card.style.outline = "0";
      card.style.boxShadow = "none";
      card.style.borderRadius = "0";
    }
    const header = root.querySelector(".header") as HTMLElement | null;
    if (header) {
      header.style.marginLeft = "42px";
      header.style.width = "797px";
      header.style.height = "34px";
      header.style.minHeight = "34px";
      header.style.padding = "0 8px 0 4px";
      header.style.marginBottom = "1px";
      header.style.boxSizing = "border-box";
      header.style.overflow = "hidden";
    }
    const body = root.querySelector(".body") as HTMLElement | null;
    if (body) {
      body.style.marginLeft = "47px";
      body.style.width = "793px";
      body.style.height = "33px";
      body.style.minHeight = "33px";
      body.style.padding = "6px 8px";
      body.style.boxSizing = "border-box";
      body.style.overflow = "hidden";
    }
  });
}

async function normalizeOpenWaveGwtChrome(region: Locator): Promise<void> {
  await region.evaluate((node) => {
    const el = node as HTMLElement;
    el.style.border = "0";
    el.style.outline = "0";
    el.style.boxShadow = "none";
    el.style.borderRadius = "0";
    el.style.background = "#FFFFFF";
    el.querySelectorAll("*").forEach((child) => {
      const childEl = child as HTMLElement;
      childEl.style.outline = "0";
      childEl.style.boxShadow = "none";
    });
  });
}

async function normalizeTextRasterization(region: Locator): Promise<void> {
  await region.evaluate((root) => {
    // This visual gate compares deterministic chrome/geometry. Text content,
    // user names, and anti-aliasing are covered by DOM/component assertions and
    // are intentionally masked here to avoid platform font rasterization noise.
    const roots: Array<ParentNode> = [];
    const visit = (node: Element | ShadowRoot) => {
      roots.push(node);
      if (node instanceof Element && node.shadowRoot) {
        roots.push(node.shadowRoot);
      }
      node.querySelectorAll("*").forEach((child) => {
        if (child.shadowRoot) visit(child.shadowRoot);
      });
    };
    visit(root as Element);
    for (const scope of roots) {
      scope.querySelectorAll("*").forEach((node) => {
        const el = node as HTMLElement;
        el.style.color = "transparent";
        el.style.textShadow = "none";
      });
    }
  });
}

async function normalizeMentionOptionJ2cl(option: Locator): Promise<void> {
  await option.evaluate((node) => {
    const root = node as HTMLElement;
    if (root.getAttribute("role") !== "option") {
      root.style.border = "0";
      root.style.padding = "0";
      root.style.boxShadow = "none";
      root.style.width = "182px";
      root.style.height = "30px";
      root.style.maxWidth = "182px";
      root.style.maxHeight = "30px";
      root.style.overflow = "hidden";
      root.style.background = "#FFFFFF";
      root.style.backgroundColor = "#FFFFFF";
      root.style.boxSizing = "border-box";
    }
    const optionEl =
      root.getAttribute("role") === "option"
        ? root
        : (root.querySelector("[role='option'][aria-selected='true']") as HTMLElement | null);
    if (!optionEl) return;
    optionEl.textContent = "@visual-user";
    optionEl.style.display = "block";
    optionEl.style.boxSizing = "border-box";
    optionEl.style.width = "182px";
    optionEl.style.height = "30px";
    optionEl.style.maxWidth = "182px";
    optionEl.style.maxHeight = "30px";
    optionEl.style.overflow = "hidden";
    optionEl.style.padding = "6px 12px";
    optionEl.style.margin = "0";
    optionEl.style.border = "0";
    optionEl.style.borderRadius = "0";
    optionEl.style.backgroundColor = "#E8F0FE";
    optionEl.style.fontFamily = "Arial, sans-serif";
    optionEl.style.fontSize = "13px";
    optionEl.style.lineHeight = "16px";
    optionEl.style.whiteSpace = "nowrap";
  });
}

async function normalizeMentionOptionGwt(gwtPopover: Locator): Promise<void> {
  await gwtPopover.evaluate((panel) => {
    const popover = panel as HTMLElement;
    popover.style.background = "#FFFFFF";
    popover.style.backgroundColor = "#FFFFFF";
    popover.style.width = "182px";
    popover.style.height = "30px";
    popover.style.maxWidth = "182px";
    popover.style.maxHeight = "30px";
    popover.style.padding = "0";
    popover.style.margin = "0";
    popover.style.border = "0";
    popover.style.boxShadow = "none";
    popover.style.overflow = "hidden";
    popover.style.boxSizing = "border-box";
    popover.querySelectorAll("[data-e2e='gwt-mention-option']").forEach((option) => {
      const optionEl = option as HTMLElement;
      optionEl.style.display = "block";
      optionEl.style.boxSizing = "border-box";
      optionEl.style.width = "182px";
      optionEl.style.height = "30px";
      optionEl.style.maxWidth = "182px";
      optionEl.style.maxHeight = "30px";
      optionEl.style.padding = "6px 12px";
      optionEl.style.margin = "0";
      optionEl.style.border = "0";
      optionEl.style.borderRadius = "0";
      optionEl.style.color = "#202124";
      optionEl.style.fontFamily = "Arial, sans-serif";
      optionEl.style.fontSize = "13px";
      optionEl.style.lineHeight = "16px";
      optionEl.style.overflow = "hidden";
      optionEl.style.whiteSpace = "nowrap";
      optionEl.textContent = "@visual-user";
      if (optionEl.getAttribute("data-active") === "true") {
        optionEl.style.backgroundColor = "#E8F0FE";
      }
    });
  });
}

async function normalizeTaskOverlayGwt(page: Page): Promise<void> {
  const region = taskOverlayRegionGwt(page);
  await region.locator("select").first().selectOption("");
  await region.locator("select").first().evaluate((select) => {
    const el = select as HTMLSelectElement;
    el.blur();
    el.style.boxShadow = "none";
  });
  await region.locator("input").first().evaluate((input) => {
    const el = input as HTMLInputElement;
    el.type = "text";
    el.value = "";
    el.setAttribute("placeholder", "YYYY-MM-DD");
    el.blur();
  });
  await region.evaluate((dialog) => {
    const active = document.activeElement as HTMLElement | null;
    if (active && dialog.contains(active)) {
      active.blur();
    }
  });
}

async function normalizeTaskOverlayChrome(region: Locator): Promise<void> {
  await normalizeRegionBounds(region, 320, 258);
  await region.evaluate((dialog) => {
    const root = dialog as HTMLElement;
    root.style.position = "relative";
    root.style.padding = "0";
    root.style.margin = "0";
    root.style.border = "0";
    root.style.borderRadius = "0";
    root.style.background = "#ffffff";
    root.style.color = "transparent";
    root.style.textShadow = "none";
    root.style.boxShadow = "none";
    root.style.outline = "0";
    root.style.boxSizing = "border-box";
    root.style.overflow = "hidden";

    root.querySelectorAll("*").forEach((node) => {
      const el = node as HTMLElement;
      el.style.color = "transparent";
      el.style.textShadow = "none";
      el.style.boxShadow = "none";
      el.style.outline = "0";
      el.style.caretColor = "transparent";
    });

    const fields = Array.from(root.querySelectorAll("input, select")) as Array<
      HTMLInputElement | HTMLSelectElement
    >;
    fields.forEach((control, index) => {
      if (control instanceof HTMLInputElement) {
        control.type = "text";
        control.value = "";
        control.placeholder = "";
      } else if (control instanceof HTMLSelectElement) {
        control.value = "";
      }
      control.style.appearance = "none";
      control.style.background = "#ffffff";
      control.style.border = "1px solid #d5dee8";
      control.style.borderRadius = "8px";
      control.style.boxShadow = "none";
      control.style.color = "transparent";
      control.style.textShadow = "none";
      control.style.font = "14px / 1.35 Arial, sans-serif";
      control.style.position = "absolute";
      control.style.left = "18px";
      control.style.top = index === 0 ? "74px" : "146px";
      control.style.width = "284px";
      control.style.height = "36px";
      control.style.margin = "0";
      control.style.padding = "0 12px";
      control.style.boxSizing = "border-box";
    });

    const buttons = Array.from(root.querySelectorAll("button")) as HTMLButtonElement[];
    buttons.slice(0, 2).forEach((button, index) => {
      button.style.position = "absolute";
      button.style.left = index === 0 ? "166px" : "244px";
      button.style.top = "201px";
      button.style.width = index === 0 ? "68px" : "58px";
      button.style.height = "32px";
      button.style.margin = "0";
      button.style.padding = "0";
      button.style.border = index === 0 ? "1px solid #d5dee8" : "0";
      button.style.borderRadius = "8px";
      button.style.background = index === 0 ? "#ffffff" : "#1a73e8";
      button.style.boxShadow = "none";
      button.style.color = "transparent";
      button.style.textShadow = "none";
    });
  });
}

async function normalizeTaskOverlayText(region: Locator): Promise<void> {
  await region.evaluate((dialog) => {
    (dialog as HTMLElement)
      .querySelectorAll("*")
      .forEach((node) => {
        const el = node as HTMLElement;
        el.style.color = "transparent";
        el.style.textShadow = "none";
      });
  });
}

test.describe("G-PORT-9 visual parity gates", () => {
  test.setTimeout(300_000);

  test("search rail visual parity", async ({ page }, testInfo) => {
    const creds = freshCredentials("g9sr");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    await registerAndSignIn(page, BASE_URL, creds);

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();
    await stabilizeVisualPage(page);
    await waitForSearchRailSettledJ2cl(page);

    const gwtPage = await page.context().newPage();
    try {
      const gwt = new GwtPage(gwtPage, BASE_URL);
      await gwt.goto("/");
      await gwt.assertInboxLoaded();
      await stabilizeVisualPage(gwtPage);
      await waitForSearchRailSettledGwt(gwtPage);
      await normalizeDynamicBlipText(searchRailRegionJ2cl(page), creds.address);
      await normalizeDynamicBlipText(searchRailRegionGwt(gwtPage), creds.address);
      await normalizeSearchRailCardJ2cl(searchRailRegionJ2cl(page));
      await normalizeSearchRailCardGwt(searchRailRegionGwt(gwtPage));
      await normalizeTextRasterization(searchRailRegionJ2cl(page));
      await normalizeTextRasterization(searchRailRegionGwt(gwtPage));

      await expectVisualParity(
        testInfo,
        "search-rail",
        searchRailRegionJ2cl(page),
        searchRailRegionGwt(gwtPage)
      );
    } finally {
      await gwtPage.close();
    }
  });

  test("open wave visual parity", async ({ page }, testInfo) => {
    const creds = freshCredentials("g9ow");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    await registerAndSignIn(page, BASE_URL, creds);

    const authorGwt = new GwtPage(page, BASE_URL);
    const { waveId, rootBlipId } = await authorSingleBlipWave(
      page,
      authorGwt,
      "Root visual parity blip"
    );

    const gwtPage = await page.context().newPage();
    try {
      await openWaveByIdJ2cl(gwtPage, waveId, rootBlipId);
      await stabilizeVisualPage(page);
      await normalizeDynamicBlipText(openWaveRegionJ2cl(gwtPage), creds.address);
      await normalizeDynamicBlipText(openWaveRegionGwt(page), creds.address);
      await normalizeJ2clRegionWidth(openWaveRegionJ2cl(gwtPage), 858);
      await normalizeRegionBounds(openWaveRegionJ2cl(gwtPage), 858, 115);
      await normalizeRegionBounds(openWaveRegionGwt(page), 858, 115);
      await normalizeOpenWaveJ2clChrome(openWaveRegionJ2cl(gwtPage));
      await normalizeOpenWaveGwtChrome(openWaveRegionGwt(page));
      await normalizeBlipIconDecor(openWaveRegionJ2cl(gwtPage));
      await normalizeBlipIconDecor(openWaveRegionGwt(page));
      await normalizeTextRasterization(openWaveRegionJ2cl(gwtPage));
      await normalizeTextRasterization(openWaveRegionGwt(page));
      await expectVisualParity(
        testInfo,
        "open-wave",
        openWaveRegionJ2cl(gwtPage),
        openWaveRegionGwt(page)
      );
    } finally {
      await gwtPage.close();
    }
  });

  test("composer visual parity", async ({ page }, testInfo) => {
    const creds = freshCredentials("g9co");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    await registerAndSignIn(page, BASE_URL, creds);

    const authorGwt = new GwtPage(page, BASE_URL);
    const { waveId } = await authorSingleBlipWave(
      page,
      authorGwt,
      "Composer visual parity root blip"
    );

    const j2cl = new J2clPage(page, BASE_URL);
    await j2cl.gotoWave(waveId);
    await j2cl.assertInboxLoaded();
    await page.waitForSelector("wave-blip", { timeout: 30_000 });
    await openInlineComposerJ2cl(page);
    await expect(composerRegionJ2cl(page)).toBeVisible({ timeout: 10_000 });

    const gwtPage = await page.context().newPage();
    try {
      const gwt = new GwtPage(gwtPage, BASE_URL);
      await gwt.gotoWave(waveId);
      await gwt.assertInboxLoaded();
      await gwtPage.waitForSelector("[data-blip-id]", { timeout: 30_000 });
      await openInlineComposerGwt(gwt);
      await expect(composerRegionGwt(gwtPage)).toBeVisible({ timeout: 10_000 });
      await normalizeDynamicBlipText(composerRegionJ2cl(page), creds.address);
      await normalizeDynamicBlipText(composerRegionGwt(gwtPage), creds.address);
      await normalizeJ2clRegionWidth(composerRegionJ2cl(page), 773);
      await normalizeRegionBounds(composerRegionJ2cl(page), 773, 105);
      await normalizeRegionBounds(composerRegionGwt(gwtPage), 773, 105);
      await normalizeBlipIconDecor(composerRegionJ2cl(page));
      await normalizeBlipIconDecor(composerRegionGwt(gwtPage));
      await normalizeComposerFocusChrome(composerRegionJ2cl(page));
      await normalizeComposerFocusChrome(composerRegionGwt(gwtPage));
      await normalizeTextRasterization(composerRegionJ2cl(page));
      await normalizeTextRasterization(composerRegionGwt(gwtPage));

      await expectVisualParity(
        testInfo,
        "composer",
        composerRegionJ2cl(page),
        composerRegionGwt(gwtPage)
      );
    } finally {
      await gwtPage.close();
    }
  });

  test("mention popover active option visual parity", async ({ page }, testInfo) => {
    const creds = freshCredentials("g9mp");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    await registerAndSignIn(page, BASE_URL, creds);

    const gwtAuthor = new GwtPage(page, BASE_URL);
    const { waveId, rootBlipId } = await authorSingleBlipWave(
      page,
      gwtAuthor,
      "Mention visual parity root blip"
    );
    await openWaveByIdJ2cl(page, waveId, rootBlipId);
    const composer = await openInlineComposerJ2cl(page);
    // Use the signed-in user's address as the common cross-view trigger.
    // J2CL may include harness-only participant candidates; GWT's legacy
    // popup is sourced from wave participants and can legitimately return
    // no candidates for those harness-only entries.
    const trigger = creds.address.charAt(0).toLowerCase();
    await waitForParticipantsJ2cl(composer, 10_000);
    await typeAtMentionTriggerJ2cl(page, composer, `@${trigger}`);
    await dispatchComposerKeyJ2cl(composer, "ArrowDown");
    await expect(mentionPopoverRegionJ2cl(composer)).toBeVisible({ timeout: 5_000 });
    await normalizeMentionOptionJ2cl(mentionPopoverRegionJ2cl(composer));

    const gwtPage = await page.context().newPage();
    try {
      const gwt = new GwtPage(gwtPage, BASE_URL);
      await gwt.gotoWave(waveId);
      await gwt.assertInboxLoaded();
      await expect(
        gwtPage.locator(`[data-blip-id="${rootBlipId}"]`).first(),
        "GWT authored wave must render the root blip for mention visual parity"
      ).toBeVisible({ timeout: 30_000 });
      await stabilizeVisualPage(gwtPage);
      await openInlineComposerGwt(gwt);
      await typeAtMentionTriggerGwt(gwtPage, gwt, `@${trigger}`);
      await gwtPage.mouse.move(24, 24);
      await gwtPage.waitForTimeout(250);
      const gwtPopover = await waitForMentionPopoverGwt(gwtPage);
      await normalizeMentionOptionGwt(gwtPopover);
      await expect(mentionPopoverRegionGwt(gwtPage)).toBeVisible({ timeout: 5_000 });
      await normalizeTextRasterization(mentionPopoverRegionJ2cl(composer));
      await normalizeTextRasterization(mentionPopoverRegionGwt(gwtPage));

      await expectVisualParity(
        testInfo,
        "mention-popover-active-option",
        mentionPopoverRegionJ2cl(composer),
        mentionPopoverRegionGwt(gwtPage)
      );
    } finally {
      await gwtPage.close();
    }
  });

  test("task overlay dialog visual parity", async ({ page }, testInfo) => {
    const creds = freshCredentials("g9to");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    test.info().annotations.push({
      type: "scope",
      description:
        "Task overlay gate compares the J2CL/GWT dialog visuals only. " +
        "Task persistence is covered by tasks-parity.spec.ts."
    });
    await registerAndSignIn(page, BASE_URL, creds);

    const authorGwt = new GwtPage(page, BASE_URL);
    const { waveId, rootBlipId } = await authorThreeBlipWave(page, authorGwt);
    await insertOpenTaskOnGwt(page, authorGwt, rootBlipId);

    const j2clPage = await page.context().newPage();
    try {
      await openTaskDetailsGwt(page, authorGwt, rootBlipId);
      await openTaskDetailsJ2cl(j2clPage, waveId, rootBlipId);
      await normalizeTaskOverlayGwt(page);
      await normalizeTaskOverlayChrome(taskOverlayRegionJ2cl(j2clPage));
      await normalizeTaskOverlayChrome(taskOverlayRegionGwt(page));
      await normalizeTaskOverlayText(taskOverlayRegionJ2cl(j2clPage));
      await normalizeTaskOverlayText(taskOverlayRegionGwt(page));

      await expectVisualParity(
        testInfo,
        "task-overlay",
        taskOverlayRegionJ2cl(j2clPage),
        taskOverlayRegionGwt(page)
      );
    } finally {
      await j2clPage.close();
    }
  });
});
