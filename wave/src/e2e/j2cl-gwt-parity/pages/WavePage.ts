// G-PORT-1 (#1110) — shared page-object base for the J2CL <-> GWT parity
// Playwright harness. Each later G-PORT slice extends the J2cl/Gwt
// subclasses with the surface it actually exercises (findWave,
// openWave, clickReply, typeAndSend, mentionsList, ...). G-PORT-1 keeps
// the surface narrow on purpose: only goto() + assertInboxLoaded(),
// because the smoke test only needs to prove both views bootstrap.
import { Page } from "@playwright/test";

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

  /** Convenience: returns the rendered HTML for source-text assertions. */
  protected async pageHtml(): Promise<string> {
    return await this.page.content();
  }
}
