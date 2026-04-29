// G-PORT-1 (#1110) — page object for the /?view=j2cl-root surface.
// Markers chosen to match the server-side parity contract asserted in
// J2clStageOneReadSurfaceParityTest and the existing screenshot harness
// in scripts/screenshot-v-5.mjs:
//   - <shell-root> present
//   - <shell-root-signed-out> absent
//   - GWT bootstrap (webclient/webclient.nocache.js) absent
//
// G-PORT-3 (#1112): adds compose / send selectors for the parity test.
// Note: wave-reading-parity.spec.ts authors waves on the GWT view
// (J2CL composer is currently hidden). These selectors remain here for
// future slices when the J2CL composer ships its visible surface.
//   - wavy-search-rail >> button.new-wave (per source line 461)
//   - wavy-composer >> [data-composer-body] (the contenteditable body)
//   - composer-submit-affordance >> button[aria-label="<label>"] —
//     <wavy-composer> renders this with label "Create wave" /
//     "Send reply" / "Save" depending on mode.
//
// G-PORT-6 (#1115): adds task-affordance helpers for the tasks parity test.
//   - blipTaskToggle(blipId)        — locates the per-blip task button
//   - blipHasTaskCompleted(blipId)  — presence check (Lit reflects
//     `taskCompleted: true` as the bare attribute, never `="true"`).
import { expect, Locator } from "@playwright/test";
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

  override newWaveAffordance(): Locator {
    // Two <wavy-search-rail> instances may live in the DOM at once
    // (the inbox rail + a hidden sidecar copy used by the search
    // panel). Either's "New Wave" button dispatches
    // `wavy-new-wave-requested` which the root shell controller
    // listens to on document.body. We target the visible one by
    // using the role+name accessor — Playwright's `getByRole`
    // automatically scopes to the visible button.
    return this.page.getByRole("button", { name: "New Wave" }).first();
  }

  override composerBody(): Locator {
    // J-UI-3 / J-UI-5: J2clComposeSurfaceView builds the create form
    // as a <textarea aria-label="New wave content"> inside a
    // .j2cl-compose-create-form. (When the j2cl-inline-rich-composer
    // feature flag is on, the body becomes the contenteditable
    // <wavy-composer>'s [data-composer-body] — same locator works
    // because the textarea is also accessible via aria-label.)
    return this.page
      .locator(
        'textarea[aria-label="New wave content"], wavy-composer [data-composer-body]'
      )
      .first();
  }

  /**
   * G-PORT-3 (#1112): inline reply textarea (legacy
   * <composer-inline-reply>) once a wave is selected. When the
   * j2cl-inline-rich-composer flag is on, the inline composer is a
   * <wavy-composer> with [data-composer-body] instead.
   */
  inlineReplyBody(): Locator {
    return this.page
      .locator(
        'composer-inline-reply textarea[aria-label="Reply"], wavy-composer [data-composer-body]'
      )
      .first();
  }

  override composerSubmit(label: string): Locator {
    // composer-submit-affordance renders <button aria-label=${label}>.
    return this.page
      .locator(`composer-submit-affordance button[aria-label="${label}"]`)
      .first();
  }

  /**
   * G-PORT-3 (#1112): root-of-wave reply trigger ("Click here to reply"
   * affordance at the bottom of the read surface, F-3.S1).
   */
  rootReplyTrigger(): Locator {
    return this.page.locator("button[data-wave-root-reply-trigger]");
  }

  /** G-PORT-3 (#1112): per-blip reply button inside a blip's toolbar. */
  blipReplyButton(blipId: string): Locator {
    return this.page.locator(
      `wave-blip[data-blip-id="${blipId}"] wave-blip-toolbar button[data-toolbar-action="reply"]`
    );
  }

  /**
   * G-PORT-6 (#1115): per-blip task toggle button inside the
   * <wavy-task-affordance> custom element. Playwright pierces shadow
   * DOM automatically so the descendant selector lands on the actual
   * <button data-task-toggle-trigger="true"> rendered in the
   * affordance's renderRoot.
   */
  blipTaskToggle(blipId: string): Locator {
    return this.page.locator(
      `wave-blip[data-blip-id="${blipId}"] wavy-task-affordance ` +
        `button[data-task-toggle-trigger="true"]`
    );
  }

  /**
   * G-PORT-6 (#1115): returns whether the outer <wave-blip> host
   * carries the `data-task-completed` attribute. Lit's Boolean
   * reflection emits the attribute as presence-only (no `="true"`
   * value), so all assertions go through `hasAttribute(...)`.
   */
  async blipHasTaskCompleted(blipId: string): Promise<boolean> {
    return await this.page.evaluate((id: string) => {
      const el = document.querySelector(
        `wave-blip[data-blip-id="${(window as any).CSS.escape(id)}"]`
      );
      return el ? el.hasAttribute("data-task-completed") : false;
    }, blipId);
  }
}
