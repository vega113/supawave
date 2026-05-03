import { expect, Page } from "@playwright/test";
import { GwtPage } from "../../pages/GwtPage";

/**
 * Creates an open task on an existing GWT blip and leaves the checkbox
 * unchecked. J2CL intentionally no longer mounts a generic Task toggle on
 * every normal blip; it should expose task controls only after the GWT task
 * doodad/annotation exists.
 */
export async function insertOpenTaskOnGwt(
  page: Page,
  gwt: GwtPage,
  blipId: string
): Promise<void> {
  await gwt.clickEditOnBlip(blipId);
  await expect(
    page.locator('div[title^="Insert task"]').first(),
    "[gwt] format toolbar Insert task button must mount in edit mode"
  ).toBeVisible({ timeout: 15_000 });
  await gwt.clickInsertTask();
  await gwt.dismissTaskMetadataPopup();
  await page.keyboard.press("Escape");
  await expect
    .poll(
      async () =>
        await page
          .locator(`[data-blip-id="${blipId}"] input[type="checkbox"]`)
          .count(),
      {
        timeout: 15_000,
        message: `[gwt] ${blipId} should contain an inline checkbox after Insert task`
      }
    )
    .toBeGreaterThanOrEqual(1);
  await expect
    .poll(async () => await gwt.blipHasCheckedTask(blipId), {
      timeout: 10_000,
      message: `[gwt] ${blipId} inline checkbox should start unchecked`
    })
    .toBe(false);
}
