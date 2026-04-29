// G-PORT-1 (#1110) — page object for the /?view=gwt surface.
// Markers come straight from the server-side parity contract in
// J2clStageOneReadSurfaceParityTest.java:337-351:
//   - webclient/webclient.nocache.js script reference present
//   - id="app" host div present
//   - <shell-root> absent
import { expect } from "@playwright/test";
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
}
