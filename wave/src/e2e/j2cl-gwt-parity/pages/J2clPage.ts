// G-PORT-1 (#1110) — page object for the /?view=j2cl-root surface.
// Markers chosen to match the server-side parity contract asserted in
// J2clStageOneReadSurfaceParityTest and the existing screenshot harness
// in scripts/screenshot-v-5.mjs:
//   - <shell-root> present
//   - <shell-root-signed-out> absent
//   - GWT bootstrap (webclient/webclient.nocache.js) absent
import { expect } from "@playwright/test";
import { WavePage } from "./WavePage";

export class J2clPage extends WavePage {
  viewQuery(): string {
    return "view=j2cl-root";
  }

  async assertInboxLoaded(): Promise<void> {
    await expect(
      this.page.locator('shell-root[data-j2cl-root-shell="true"]'),
      "J2CL view should render the signed-in J2CL shell"
    ).toHaveCount(1, { timeout: 15_000 });

    await expect(
      this.page.locator("shell-root-signed-out"),
      "J2CL view should not render the signed-out shell after sign-in"
    ).toHaveCount(0);

    const html = await this.pageHtml();
    expect(
      html.includes("webclient/webclient.nocache.js"),
      "J2CL view should not load the GWT webclient bundle"
    ).toBe(false);
  }
}
