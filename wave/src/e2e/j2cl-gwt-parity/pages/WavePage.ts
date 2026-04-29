// G-PORT-1 (#1110) — shared page-object base for the J2CL <-> GWT parity
// Playwright harness. Each later G-PORT slice extends the J2cl/Gwt
// subclasses with the surface it actually exercises (findWave,
// openWave, clickReply, typeAndSend, mentionsList, ...). G-PORT-1 keeps
// the surface narrow on purpose: only goto() + assertInboxLoaded(),
// because the smoke test only needs to prove both views bootstrap.
//
// G-PORT-3 (#1112) extends the base with the surface needed by
// `wave-reading-parity.spec.ts`:
//   - gotoWave(waveId) — switches the same browser session to a wave URL
//     while preserving the view query.
//   - newWaveAffordance(), composerBody(), composerSubmit(label) — the
//     "create / send" surface. Implemented on whichever subclass supports
//     compose interactions (currently GwtPage, because the J2CL composer
//     is hidden). Subclasses that do not support compose throw a clear
//     diagnostic. The parity test authors content via the GWT view, then
//     asserts the rendered chrome on both views.
import { Locator, Page } from "@playwright/test";

export abstract class WavePage {
  constructor(readonly page: Page, readonly baseURL: string) {}

  /** "view=j2cl-root" or "view=gwt". Subclass-defined. */
  abstract viewQuery(): string;

  /** Asserts the post-login shell rendered for this view. */
  abstract assertInboxLoaded(): Promise<void>;

  /**
   * Navigates to `path` (default "/") with this view's `?view=...` query
   * appended. Uses `domcontentloaded` so we don't depend on networkidle,
   * which is flaky against the live update channel — assertInboxLoaded()
   * does the real readiness check.
   */
  async goto(path: string = "/"): Promise<void> {
    const sep = path.includes("?") ? "&" : "?";
    const target = `${this.baseURL}${path}${sep}${this.viewQuery()}`;
    await this.page.goto(target, { waitUntil: "domcontentloaded" });
  }

  /**
   * G-PORT-3 (#1112): navigate to the wave-detail URL while preserving
   * the active view. The server reads the {@code wave} query param
   * (see WaveServlet); the {@code view} query is the J2CL/GWT switch.
   */
  async gotoWave(waveId: string): Promise<void> {
    await this.goto(`/?wave=${encodeURIComponent(waveId)}`);
  }

  /**
   * G-PORT-3 (#1112): the inbox "New Wave" affordance. Subclasses that
   * support compose/create-wave interactions should implement this.
   */
  newWaveAffordance(): Locator {
    throw new Error(
      `${this.constructor.name}.newWaveAffordance() is not implemented. ` +
        `Extend this page object to provide the compose/create-wave ` +
        `affordance for the current view.`
    );
  }

  /** G-PORT-3 (#1112): the composer's contenteditable body. */
  composerBody(): Locator {
    throw new Error(
      `${this.constructor.name}.composerBody() is not implemented.`
    );
  }

  /**
   * G-PORT-3 (#1112): the composer submit button for the given mode
   * label ("Create wave", "Send reply", "Save").
   */
  composerSubmit(_label: string): Locator {
    throw new Error(
      `${this.constructor.name}.composerSubmit() is not implemented.`
    );
  }

  /** Convenience: returns the rendered HTML for source-text assertions. */
  protected async pageHtml(): Promise<string> {
    return await this.page.content();
  }
}
