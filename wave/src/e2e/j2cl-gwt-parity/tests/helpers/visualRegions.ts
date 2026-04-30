import { expect, Locator, Page, TestInfo } from "@playwright/test";
import { GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR } from "../../pages/GwtPage";
import { compareLocatorScreenshots } from "./visualDiff";

export async function stabilizeVisualPage(page: Page): Promise<void> {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        animation-duration: 0.001ms !important;
        animation-delay: 0ms !important;
        transition-duration: 0.001ms !important;
        transition-delay: 0ms !important;
        caret-color: transparent !important;
      }

      div[style*="toast-wave-gradient"] {
        display: none !important;
      }
    `
  });
}

export async function expectVisualParity(
  testInfo: TestInfo,
  name: string,
  j2cl: Locator,
  gwt: Locator,
  maxRatio = 0.05
): Promise<void> {
  await expect(j2cl, `${name}: J2CL region must be visible`).toBeVisible();
  await expect(gwt, `${name}: GWT region must be visible`).toBeVisible();
  await j2cl.scrollIntoViewIfNeeded();
  await gwt.scrollIntoViewIfNeeded();
  const diff = await compareLocatorScreenshots(testInfo, name, j2cl, gwt);
  testInfo.annotations.push({
    type: "visual-diff",
    description:
      `${name} mismatch ${(diff.mismatchRatio * 100).toFixed(2)}% ` +
      `(${diff.mismatchedPixels}/${diff.totalPixels})`
  });
  expect(
    diff.mismatchRatio,
    `${name} visual diff must be <= ${(maxRatio * 100).toFixed(0)}%; ` +
      `saw ${(diff.mismatchRatio * 100).toFixed(2)}%`
  ).toBeLessThanOrEqual(maxRatio);
}

export function searchRailRegionJ2cl(page: Page): Locator {
  // J2CL renderer: HtmlRenderer.appendWavySearchRail + <wavy-search-rail>.
  // Capture the visible digest row because it is the common search-rail
  // content shared by both GWT and J2CL while the surrounding app-shell
  // shortcuts intentionally differ between views.
  return page.locator("wavy-search-rail-card[data-digest-card]:visible").first();
}

export function searchRailRegionGwt(page: Page): Locator {
  // GWT renderer: DigestDomImpl row inside SearchPanelWidget.
  return page.locator("[data-digest-card]:visible").first();
}

export function openWaveRegionJ2cl(page: Page): Locator {
  // J2CL renderer: HtmlRenderer sidecar selected-wave host + <wave-blip>.
  return page.locator("wave-blip[data-blip-id]:visible").first();
}

export function openWaveRegionGwt(page: Page): Locator {
  // GWT renderer: FullStructure/BlipViewBuilder emits outer [kind='b'] blips.
  return page.locator("[kind='b'][data-blip-id]:visible").first();
}

export function composerRegionJ2cl(page: Page): Locator {
  // J2CL renderer: <wave-blip> mounts inline <wavy-composer>.
  return page
    .locator("wavy-composer[data-inline-composer='true']:visible, composer-inline-reply:visible")
    .first();
}

export function composerRegionGwt(page: Page): Locator {
  // GWT renderer: EditSession activates a [kind='document'] editor in the blip.
  return page
    .locator(
      [
        `[kind="document"]:is(${GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR})`,
        `[kind="document"] :is(${GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR})`
      ].join(", ")
    )
    .last()
    .locator("xpath=ancestor::*[@kind='b' or contains(@class, 'blip')][1]");
}

export function mentionPopoverRegionJ2cl(composer: Locator): Locator {
  // J2CL renderer: <mention-suggestion-popover> inside <wavy-composer>.
  return composer
    .locator("mention-suggestion-popover[open] .popover")
    .first();
}

export function mentionPopoverRegionGwt(page: Page): Locator {
  // GWT renderer: MentionPopupWidget tags active rows with data-e2e hooks.
  return page
    .locator("[data-e2e='gwt-mention-popover']:visible")
    .last();
}

export function taskOverlayRegionJ2cl(page: Page): Locator {
  // J2CL renderer: <task-metadata-popover> dialog inside <wavy-task-affordance>.
  return page.locator("task-metadata-popover[open] .dialog").first();
}

export function taskOverlayRegionGwt(page: Page): Locator {
  // GWT renderer: TaskMetadataPopup inside DesktopUniversalPopup chrome.
  return page
    .getByText("Task details", { exact: true })
    .last()
    .locator("xpath=ancestor::*[.//select and .//*[normalize-space()='Due date']][1]");
}
