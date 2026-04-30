import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-version-history.js";

const SAMPLE_VERSIONS = [
  { index: 0, label: "v0", timestamp: "2026-04-26T10:00:00Z" },
  { index: 1, label: "v1", timestamp: "2026-04-26T10:05:00Z" },
  { index: 2, label: "v2", timestamp: "2026-04-26T10:15:00Z" },
  { index: 3, label: "v3", timestamp: "2026-04-26T10:30:00Z" }
];

describe("<wavy-version-history>", () => {
  it("defines the wavy-version-history custom element", () => {
    expect(customElements.get("wavy-version-history")).to.exist;
  });

  it("default state: open=false, host hidden, aria-hidden=true", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    expect(el.open).to.equal(false);
    expect(el.hasAttribute("hidden")).to.equal(true);
    expect(el.getAttribute("aria-hidden")).to.equal("true");
  });

  it("open_() removes hidden, sets role=dialog + aria-modal=true + aria-label=Version history", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    el.open_();
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.equal(false);
    expect(el.getAttribute("role")).to.equal("dialog");
    expect(el.getAttribute("aria-modal")).to.equal("true");
    expect(el.getAttribute("aria-label")).to.equal("Version history");
  });

  it("renders a slider with aria-label=Version history time slider", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    await el.updateComplete;
    const slider = el.renderRoot.querySelector("input[type='range']");
    expect(slider).to.exist;
    expect(slider.getAttribute("aria-label")).to.equal("Version history time slider");
  });

  it("slider input updates value and emits wavy-version-changed", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    await el.updateComplete;
    const slider = el.renderRoot.querySelector("input[type='range']");
    slider.value = "2";
    setTimeout(() => slider.dispatchEvent(new Event("input", { bubbles: true })));
    const ev = await oneEvent(el, "wavy-version-changed");
    expect(ev.detail.index).to.equal(2);
    expect(ev.detail.version).to.deep.equal(SAMPLE_VERSIONS[2]);
    expect(el.value).to.equal(2);
  });

  it("aria-valuemin=0 and aria-valuemax reflects versions.length-1", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    await el.updateComplete;
    const slider = el.renderRoot.querySelector("input[type='range']");
    expect(slider.getAttribute("aria-valuemin")).to.equal("0");
    expect(slider.getAttribute("aria-valuemax")).to.equal("3");
  });

  it("Show changes toggle: default aria-pressed=false; click → emits wavy-show-changes-toggled", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    await el.updateComplete;
    const buttons = el.renderRoot.querySelectorAll(".toggles button");
    const showChanges = buttons[0];
    expect(showChanges.textContent.trim()).to.equal("Show changes");
    expect(showChanges.getAttribute("aria-pressed")).to.equal("false");
    setTimeout(() => showChanges.click());
    const ev = await oneEvent(el, "wavy-show-changes-toggled");
    expect(ev.detail).to.deep.equal({ showChanges: true });
    expect(el.showChanges).to.equal(true);
  });

  it("Text only toggle: default aria-pressed=false; click → emits wavy-text-only-toggled", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    await el.updateComplete;
    const buttons = el.renderRoot.querySelectorAll(".toggles button");
    const textOnly = buttons[1];
    expect(textOnly.textContent.trim()).to.equal("Text only");
    expect(textOnly.getAttribute("aria-pressed")).to.equal("false");
    setTimeout(() => textOnly.click());
    const ev = await oneEvent(el, "wavy-text-only-toggled");
    expect(ev.detail).to.deep.equal({ textOnly: true });
    expect(el.textOnly).to.equal(true);
  });

  it("Restore gate (default): button is disabled + aria-disabled=true; click is a no-op (no event)", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    await el.updateComplete;
    const restore = el.renderRoot.querySelector("button.restore");
    expect(restore.hasAttribute("disabled")).to.equal(true);
    expect(restore.getAttribute("aria-disabled")).to.equal("true");
    let fired = false;
    el.addEventListener("wavy-version-restore-confirmed", () => {
      fired = true;
    });
    restore.click();
    await el.updateComplete;
    expect(fired).to.equal(false);
    const hint = el.renderRoot.querySelector(".restore-hint");
    expect(hint).to.exist;
    expect(hint.textContent.trim()).to.match(/Preview only/);
  });

  it("Restore enabled: removes aria-disabled + disabled, hides hint, click opens confirm <dialog>", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    el.restoreEnabled = true;
    await el.updateComplete;
    const restore = el.renderRoot.querySelector("button.restore");
    expect(restore.hasAttribute("disabled")).to.equal(false);
    expect(restore.getAttribute("aria-disabled")).to.equal("false");
    expect(el.renderRoot.querySelector(".restore-hint")).to.equal(null);
    restore.click();
    await el.updateComplete;
    const dlg = el.renderRoot.querySelector("dialog.confirm");
    expect(dlg).to.exist;
    expect(dlg.hasAttribute("open")).to.equal(true);
  });

  it("Confirm dialog Restore button → emits wavy-version-restore-confirmed and closes dialog", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    el.restoreEnabled = true;
    el.value = 2;
    await el.updateComplete;
    el.renderRoot.querySelector("button.restore").click();
    await el.updateComplete;
    const dlg = el.renderRoot.querySelector("dialog.confirm");
    const inner = dlg.querySelectorAll("button");
    const innerRestore = inner[1];
    setTimeout(() => innerRestore.click());
    const ev = await oneEvent(el, "wavy-version-restore-confirmed");
    expect(ev.detail.index).to.equal(2);
    expect(ev.detail.version).to.deep.equal(SAMPLE_VERSIONS[2]);
    await el.updateComplete;
    expect(dlg.hasAttribute("open")).to.equal(false);
  });

  it("Confirm dialog Cancel button → closes dialog without emitting wavy-version-restore-confirmed", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    el.restoreEnabled = true;
    await el.updateComplete;
    el.renderRoot.querySelector("button.restore").click();
    await el.updateComplete;
    const dlg = el.renderRoot.querySelector("dialog.confirm");
    const inner = dlg.querySelectorAll("button");
    const cancel = inner[0];
    let fired = false;
    el.addEventListener("wavy-version-restore-confirmed", () => {
      fired = true;
    });
    cancel.click();
    await el.updateComplete;
    expect(dlg.hasAttribute("open")).to.equal(false);
    expect(fired).to.equal(false);
  });

  it("Exit × button → sets open=false, emits wavy-version-history-exited, host gets hidden again", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    await el.updateComplete;
    const exit = el.renderRoot.querySelector("button.exit");
    setTimeout(() => exit.click());
    const ev = await oneEvent(el, "wavy-version-history-exited");
    expect(ev).to.exist;
    await el.updateComplete;
    expect(el.open).to.equal(false);
    expect(el.hasAttribute("hidden")).to.equal(true);
  });

  it("Escape key on host while open → exits", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    await el.updateComplete;
    setTimeout(() =>
      el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }))
    );
    const ev = await oneEvent(el, "wavy-version-history-exited");
    expect(ev).to.exist;
    await el.updateComplete;
    expect(el.open).to.equal(false);
  });

  it("empty versions array: slider renders with max=0, sliding stays a no-op shape", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = [];
    await el.updateComplete;
    const slider = el.renderRoot.querySelector("input[type='range']");
    expect(slider).to.exist;
    expect(slider.getAttribute("aria-valuemax")).to.equal("0");
    // Programmatic input dispatch — version is null because versions[0] is undefined.
    slider.value = "0";
    setTimeout(() => slider.dispatchEvent(new Event("input", { bubbles: true })));
    const ev = await oneEvent(el, "wavy-version-changed");
    expect(ev.detail).to.deep.equal({ index: 0, version: null });
  });

  it("Escape inside the inline confirm dialog closes the dialog, not the overlay", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = SAMPLE_VERSIONS;
    el.restoreEnabled = true;
    await el.updateComplete;
    el.renderRoot.querySelector("button.restore").click();
    await el.updateComplete;
    const dlg = el.renderRoot.querySelector("dialog.confirm");
    expect(dlg.hasAttribute("open")).to.equal(true);
    let exited = false;
    el.addEventListener("wavy-version-history-exited", () => { exited = true; });
    // Dispatch Escape on the host — the host's keydown handler must
    // recognise the open dialog and bail out (let native dialog handle).
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await el.updateComplete;
    expect(exited).to.equal(false);
    expect(el.open).to.equal(true);
  });

  it("rejected versionLoader Promise is swallowed; overlay chrome still works", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    el.versionLoader = () => Promise.reject(new Error("nope"));
    el.open_();
    await el.updateComplete;
    // Wait a microtask so the Promise rejection settles + .catch fires.
    await Promise.resolve();
    expect(el.open).to.equal(true);
    // Toggles still operate post-rejection.
    el.renderRoot.querySelectorAll(".toggles button")[0].click();
    await el.updateComplete;
    expect(el.showChanges).to.equal(true);
  });

  it("versionLoader callback is invoked once on first open with (0, ∞) range", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    let callCount = 0;
    let observed = null;
    el.versionLoader = (start, end) => {
      callCount += 1;
      observed = [start, end];
      return Promise.resolve([]);
    };
    el.open_();
    await el.updateComplete;
    el.open_();
    await el.updateComplete;
    el.close_();
    await el.updateComplete;
    el.open_();
    await el.updateComplete;
    expect(callCount).to.equal(1);
    expect(observed[0]).to.equal(0);
    expect(observed[1]).to.equal(Number.POSITIVE_INFINITY);
  });

  it("shows loader progress and failed loader text without closing the overlay", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    let rejectLoader;
    el.versionLoader = () =>
      new Promise((_resolve, reject) => {
        rejectLoader = reject;
      });
    el.open_();
    await el.updateComplete;
    expect(el.renderRoot.querySelector(".history-status").textContent).to.match(/Loading/);

    rejectLoader(new Error("history unavailable"));
    await Promise.resolve();
    await el.updateComplete;
    expect(el.open).to.equal(true);
    expect(el.renderRoot.querySelector(".history-status").textContent).to.match(
      /history unavailable/
    );
  });

  it("ignores stale version-loader completions after rebinding to another wave", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    let resolveLoader;
    el.versionLoader = () =>
      new Promise((resolve) => {
        resolveLoader = resolve;
      });
    el.open_();
    await el.updateComplete;
    expect(el.loading).to.equal(true);

    el.resetForWave();
    await el.updateComplete;
    resolveLoader([{ index: 0, label: "stale", version: 9 }]);
    await Promise.resolve();
    await el.updateComplete;

    expect(el.loading).to.equal(false);
    expect(el.versions).to.deep.equal([]);
  });

  it("allows version history load retry after loader failure", async () => {
    const el = await fixture(html`<wavy-version-history></wavy-version-history>`);
    let calls = 0;
    el.versionLoader = () => {
      calls += 1;
      if (calls === 1) {
        return Promise.reject(new Error("transient history failure"));
      }
      return [{ index: 0, label: "v8", version: 8 }];
    };

    el.open_();
    await Promise.resolve();
    await el.updateComplete;
    expect(el.error).to.contain("transient history failure");

    el.close_();
    await el.updateComplete;
    el.open_();
    await el.updateComplete;

    expect(calls).to.equal(2);
    expect(el.error).to.equal("");
    expect(el.versions.map((v) => v.version)).to.deep.equal([8]);
  });

  it("renders current version label and read-only snapshot preview", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.versions = [
      { index: 0, label: "v3", timestamp: "2026-04-26T10:05:00Z", version: 3 },
      { index: 1, label: "v5", timestamp: "2026-04-26T10:15:00Z", version: 5 }
    ];
    el.value = 1;
    el.snapshot = {
      version: 5,
      participants: ["alice@example.com", "bob@example.com"],
      documents: [
        { id: "b+root", content: "Older title and root body" },
        { id: "b+child", content: "Nested historical reply" }
      ]
    };
    await el.updateComplete;

    expect(el.renderRoot.querySelector(".current-version-label").textContent).to.contain("v5");
    const preview = el.renderRoot.querySelector(".snapshot-preview");
    expect(preview).to.exist;
    expect(preview.textContent).to.contain("Version 5");
    expect(preview.textContent).to.contain("2 participants");
    expect(preview.textContent).to.contain("Older title and root body");
  });

  it("renders restore status text near the destructive action", async () => {
    const el = await fixture(html`<wavy-version-history open></wavy-version-history>`);
    el.restoreStatus = "Version restored. Refreshing wave.";
    await el.updateComplete;
    const status = el.renderRoot.querySelector(".restore-status");
    expect(status).to.exist;
    expect(status.textContent).to.contain("Version restored");
  });
});
