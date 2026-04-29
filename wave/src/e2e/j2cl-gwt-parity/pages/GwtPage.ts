// G-PORT-1 (#1110) — page object for the /?view=gwt surface.
// Markers come straight from the server-side parity contract in
// J2clStageOneReadSurfaceParityTest.java:337-351:
//   - webclient/webclient.nocache.js script reference present
//   - id="app" host div present
//   - <shell-root> absent
//
// G-PORT-3 (#1112): adds compose / send selectors so the parity test
// can author a wave with multiple blips on the GWT view. The J2CL
// root shell's compose surface is mounted in a hidden
// .sidecar-search-card legacy wrapper today (see
// j2cl/lit/src/design/wavy-thread-collapse.css:90 — `display: none
// !important`), so the GWT view is the only path that can drive a
// real compose flow from the inbox in a Playwright test until the
// J2CL composer ships its visible surface (out of scope for G-PORT-3).
//
// GWT compose surface (per BlipViewBuilder + BlipMetaViewBuilder):
//   - "New Wave" button: <div title^="New Wave"> in the inbox toolbar.
//     Clicking it creates a new wave and opens its first blip in edit
//     mode, with the URL fragment routed to #domain/<wave-id>.
//   - The blip content body is a `[kind="document"]` div ancestor
//     hosting a contenteditable. Click into the document container,
//     type plain text, then press Escape to commit (keyboard hint:
//     "Shift+Enter to finish, Esc to exit").
//   - Per-blip Reply menu: `[data-option="reply"]` inside the meta.
//     Clicking it spawns a new inline reply blip in edit mode.
import { expect, Locator, Page } from "@playwright/test";
import { WavePage } from "./WavePage";

export class GwtPage extends WavePage {
  viewQuery(): string {
    return "view=gwt";
  }

  async assertInboxLoaded(): Promise<void> {
    // GWT bootstrap is server-rendered HTML, so we read the source.
    const html = await this.pageHtml();

    expect(
      html.includes("webclient/webclient.nocache.js"),
      "GWT view should reference the GWT webclient bundle"
    ).toBe(true);

    await expect(
      this.page.locator("#app"),
      'GWT view should mount into <div id="app">'
    ).toHaveCount(1, { timeout: 15_000 });

    await expect(
      this.page.locator("shell-root"),
      "GWT view should not render the J2CL <shell-root>"
    ).toHaveCount(0);
  }

  override newWaveAffordance(): Locator {
    // GWT toolbar button: <div title="New Wave (Shift+Cmd+O) (Shift+Ctrl/Cmd+O)">.
    return this.page.locator('div[title^="New Wave"]').first();
  }

  /**
   * G-PORT-3: clicks into the document container of a blip and types
   * `text`, then presses Escape to commit (mirrors GWT's "Esc to exit"
   * editor convention).
   */
  async typeIntoBlipDocument(text: string): Promise<void> {
    // The GWT document container has kind="document" and the
    // .SWCAW class (compiled CSS class).
    const editable = this.page.locator(".SWCAW [contenteditable]").last();
    await editable.click({ force: true });
    await expect(editable).toBeVisible({ timeout: 5_000 });
    await this.page.keyboard.type(text, { delay: 10 });
    await this.page.keyboard.press("Escape");
    // Wait for edit mode to exit: contenteditable elements disappear.
    await expect(this.page.locator(".SWCAW [contenteditable]")).toHaveCount(0, {
      timeout: 5_000
    });
  }

  /**
   * G-PORT-3: clicks the Reply menu option on the blip with the given
   * model id (data-blip-id). Spawns an inline reply blip in edit mode.
   */
  async clickReplyOnBlip(blipId: string): Promise<void> {
    const blip = this.page.locator(`[data-blip-id="${blipId}"]`).first();
    await blip.hover();
    const reply = blip.locator('[data-option="reply"]').first();
    await expect(reply).toBeVisible({ timeout: 5_000 });
    await reply.click();
  }

  /**
   * G-PORT-3: returns the domain-qualified wave id encoded in the URL
   * fragment after a wave is selected/created. Format:
   * `#<domain>/<waveId>` → returns `<domain>/<waveId>` so the J2CL
   * sidecar route codec accepts it as a `?wave=` value (its
   * isValidWaveId requires the `<domain>/w+...` shape).
   */
  async readWaveIdFromHash(): Promise<string> {
    const url = new URL(this.page.url());
    const trimmed = url.hash.replace(/^#\/?/, "");
    const match = /^([^/]+\/w\+[^/]+)/.exec(trimmed);
    if (!match) {
      throw new Error(
        `readWaveIdFromHash: no waveId in URL hash: ${this.page.url()}`
      );
    }
    return match[1];
  }
}
