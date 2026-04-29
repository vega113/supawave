// G-PORT-8 (#1117) — controller tests. The controller is loaded via
// `start()` after each test sets up its DOM + a fetch stub; auto-start
// is disabled by setting window.__G_PORT_8_DISABLE_AUTOSTART before
// the import.

import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/wavy-wave-nav-row.js";
import "../src/elements/wavy-version-history.js";

window.__G_PORT_8_DISABLE_AUTOSTART = true;
const controllerModule = await import(
  "../src/controllers/wave-action-bar-controller.js"
);

function installFetchStub(handler) {
  const original = window.fetch;
  const calls = [];
  window.fetch = async (url, init) => {
    calls.push({ url: String(url), init: init || {} });
    return await handler(url, init);
  };
  return {
    calls,
    restore: () => {
      window.fetch = original;
    }
  };
}

function okResponse() {
  return new Response("", { status: 200 });
}

describe("wave-action-bar-controller (G-PORT-8)", () => {
  let stub;

  beforeEach(() => {
    controllerModule.stop();
  });

  afterEach(() => {
    if (stub) stub.restore();
    stub = null;
    delete window.__G_PORT_8_FOLDER_TIMEOUT_MS;
    controllerModule.stop();
  });

  it("start() binds an existing <wavy-wave-nav-row> and stamps data-action-bar-bound", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+stub"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    // Microtask drain is enough for the synchronous bind path.
    await Promise.resolve();
    expect(row.hasAttribute("data-action-bar-bound")).to.be.true;
  });

  it("dispatches wavy-search-refresh-requested alongside completed so the rail re-queries", async () => {
    stub = installFetchStub(async () => okResponse());
    const wrapper = await fixture(
      html`<div>
        <wavy-search-rail></wavy-search-rail>
        <wavy-wave-nav-row source-wave-id="w+r"></wavy-wave-nav-row>
      </div>`
    );
    const rail = wrapper.querySelector("wavy-search-rail");
    const row = wrapper.querySelector("wavy-wave-nav-row");
    controllerModule.start();
    await Promise.resolve();
    let refreshSeen = null;
    rail.addEventListener(
      "wavy-search-refresh-requested",
      (e) => (refreshSeen = e.detail),
      { once: true }
    );
    const completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        () => resolve(true),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+r" }
      })
    );
    await completed;
    expect(refreshSeen).to.exist;
    expect(refreshSeen.reason).to.equal("folder-action");
    expect(refreshSeen.operation).to.equal("pin");
  });

  it("resets optimistic folder state when the same nav row switches waves", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+a"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    let completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+a" }
      })
    );
    await completed;
    expect(row.hasAttribute("pinned")).to.be.true;

    row.setAttribute("source-wave-id", "w+b");
    await new Promise((r) => setTimeout(r, 0));
    expect(row.hasAttribute("pinned")).to.be.false;
    completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+b" }
      })
    );
    const detail = await completed;
    expect(detail.operation).to.equal("pin");
    const url = new URL(stub.calls[1].url, window.location.origin);
    expect(url.searchParams.get("operation")).to.equal("pin");
  });

  it("times out hung folder requests so busy state clears", async () => {
    window.__G_PORT_8_FOLDER_TIMEOUT_MS = 20;
    stub = installFetchStub(
      async (_url, init) =>
        await new Promise((_resolve, reject) => {
          init.signal.addEventListener("abort", () =>
            reject(new DOMException("Aborted", "AbortError"))
          );
        })
    );
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+hang"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    const failed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-failed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+hang" }
      })
    );
    const detail = await failed;
    expect(detail.waveId).to.equal("w+hang");
    expect(detail.status).to.equal(0);
    expect(detail.error).to.be.a("string");
    expect(row.hasAttribute("data-folder-busy")).to.be.false;
  });

  it("does not let an in-flight request for one wave block a new wave", async () => {
    let resolveFirst;
    stub = installFetchStub(async (_url) => {
      if (stub.calls.length === 1) {
        return await new Promise((resolve) => {
          resolveFirst = () => resolve(okResponse());
        });
      }
      return okResponse();
    });
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+slow"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+slow" }
      })
    );
    await Promise.resolve();
    expect(row.getAttribute("data-folder-busy-wave-id")).to.equal("w+slow");

    const completedSlow = new Promise((resolve) =>
      document.addEventListener("wavy-folder-action-completed", (e) => {
        if (e.detail.waveId === "w+slow") resolve(e.detail);
      })
    );
    const completedFast = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => {
          if (e.detail.waveId === "w+fast") resolve(e.detail);
        }
      )
    );
    row.setAttribute("source-wave-id", "w+fast");
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+fast" }
      })
    );
    const detail = await completedFast;
    expect(detail.operation).to.equal("pin");
    expect(stub.calls).to.have.lengthOf(2);

    resolveFirst();
    const slowDetail = await completedSlow;
    expect(slowDetail.operation).to.equal("pin");
    expect(row.hasAttribute("data-folder-busy")).to.be.false;
  });

  it("emits failure for the original wave after a row is reused", async () => {
    let resolveFirst;
    stub = installFetchStub(
      async () =>
        await new Promise((resolve) => {
          resolveFirst = () => resolve(new Response("nope", { status: 500 }));
        })
    );
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+old"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    const failed = new Promise((resolve) =>
      document.addEventListener("wavy-folder-action-failed", (e) => {
        if (e.detail.waveId === "w+old") resolve(e.detail);
      })
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-archive-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+old" }
      })
    );
    await Promise.resolve();
    expect(row.hasAttribute("archived")).to.be.true;

    row.setAttribute("source-wave-id", "w+new");
    await new Promise((r) => setTimeout(r, 0));
    row.setAttribute("archived", "");

    resolveFirst();
    const detail = await failed;
    expect(detail.status).to.equal(500);
    expect(row.hasAttribute("archived")).to.be.true;
  });

  it("archive click POSTs /folder?operation=move&folder=archive&waveId=…", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+x"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();
    const completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-archive-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+x" }
      })
    );
    const detail = await completed;
    expect(stub.calls).to.have.lengthOf(1);
    expect(stub.calls[0].init.method).to.equal("POST");
    const url = new URL(stub.calls[0].url, window.location.origin);
    expect(url.pathname).to.equal("/folder/");
    expect(url.searchParams.get("operation")).to.equal("move");
    expect(url.searchParams.get("folder")).to.equal("archive");
    expect(url.searchParams.get("waveId")).to.equal("w+x");
    expect(detail).to.deep.equal({
      waveId: "w+x",
      operation: "move",
      folder: "archive"
    });
    // Optimistic flip stuck.
    expect(row.hasAttribute("archived")).to.be.true;
  });

  it("archive click rolls back the optimistic flip on non-200", async () => {
    stub = installFetchStub(
      async () => new Response("nope", { status: 500 })
    );
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+y"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();
    const failed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-failed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-archive-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+y" }
      })
    );
    const detail = await failed;
    expect(detail.status).to.equal(500);
    // Rolled back.
    expect(row.hasAttribute("archived")).to.be.false;
  });

  it("archive click on an already-archived row moves to inbox", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row archived source-wave-id="w+z"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();
    const completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-archive-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+z" }
      })
    );
    const detail = await completed;
    expect(detail.folder).to.equal("inbox");
    const url = new URL(stub.calls[0].url, window.location.origin);
    expect(url.searchParams.get("folder")).to.equal("inbox");
    expect(row.hasAttribute("archived")).to.be.false;
  });

  it("pin click POSTs /folder?operation=pin&waveId=…", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+p"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();
    const completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+p" }
      })
    );
    const detail = await completed;
    expect(detail.operation).to.equal("pin");
    expect(row.hasAttribute("pinned")).to.be.true;
    const url = new URL(stub.calls[0].url, window.location.origin);
    expect(url.searchParams.get("operation")).to.equal("pin");
  });

  it("pin click on a pinned row sends unpin", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row pinned source-wave-id="w+u"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();
    const completed = new Promise((resolve) =>
      document.addEventListener(
        "wavy-folder-action-completed",
        (e) => resolve(e.detail),
        { once: true }
      )
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+u" }
      })
    );
    const detail = await completed;
    expect(detail.operation).to.equal("unpin");
    expect(row.hasAttribute("pinned")).to.be.false;
  });

  it("missing waveId is a no-op (no fetch dispatched)", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(html`<wavy-wave-nav-row></wavy-wave-nav-row>`);
    controllerModule.start();
    await Promise.resolve();
    row.dispatchEvent(
      new CustomEvent("wave-nav-archive-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: {}
      })
    );
    // Yield once.
    await new Promise((r) => setTimeout(r, 10));
    expect(stub.calls).to.have.lengthOf(0);
  });

  it("version-history click flips the document overlay's open property", async () => {
    const wrapper = await fixture(
      html`<div>
        <wavy-wave-nav-row source-wave-id="w+v"></wavy-wave-nav-row>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    // Append the overlay to document.body so document.querySelector
    // finds it (the controller searches the owner document).
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      expect(overlay.open).to.be.false;
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "w+v" }
        })
      );
      // Reactive update microtask.
      await overlay.updateComplete;
      expect(overlay.open).to.be.true;
      expect(overlay.hasAttribute("hidden")).to.be.false;
    } finally {
      overlay.remove();
    }
  });

  it("MutationObserver picks up nav-rows added after start()", async () => {
    stub = installFetchStub(async () => okResponse());
    controllerModule.start();
    const row = document.createElement("wavy-wave-nav-row");
    row.setAttribute("source-wave-id", "w+late");
    document.body.appendChild(row);
    try {
      // MutationObserver runs at end of microtask checkpoint.
      await new Promise((r) => setTimeout(r, 0));
      const completed = new Promise((resolve) =>
        document.addEventListener(
          "wavy-folder-action-completed",
          (e) => resolve(e.detail),
          { once: true }
        )
      );
      row.dispatchEvent(
        new CustomEvent("wave-nav-pin-toggle-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "w+late" }
        })
      );
      const detail = await completed;
      expect(detail.operation).to.equal("pin");
    } finally {
      row.remove();
    }
  });

  it("MutationObserver unbinds nav-rows removed from the DOM", async () => {
    stub = installFetchStub(async () => okResponse());
    controllerModule.start();
    const row = document.createElement("wavy-wave-nav-row");
    row.setAttribute("source-wave-id", "w+gone");
    document.body.appendChild(row);
    await new Promise((r) => setTimeout(r, 0));
    expect(row.hasAttribute("data-action-bar-bound")).to.be.true;

    row.remove();
    await new Promise((r) => setTimeout(r, 0));
    expect(row.hasAttribute("data-action-bar-bound")).to.be.false;

    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+gone" }
      })
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(stub.calls).to.have.lengthOf(0);
  });

  it("hydrateFromDigest seeds pinned from a matching search-rail card on initial bind", async () => {
    const wrapper = await fixture(
      html`<div>
        <wavy-search-rail-card data-wave-id="w+hydpin" pinned></wavy-search-rail-card>
        <wavy-wave-nav-row source-wave-id="w+hydpin"></wavy-wave-nav-row>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    controllerModule.start();
    await Promise.resolve();
    expect(row.hasAttribute("pinned"), "pinned hydrated from digest card").to.be.true;
  });

  it("hydrateFromDigest seeds pinned when a row switches to a pinned wave — first click sends unpin", async () => {
    stub = installFetchStub(async () => okResponse());
    const card = document.createElement("wavy-search-rail-card");
    card.setAttribute("data-wave-id", "w+switch-pin");
    card.setAttribute("pinned", "");
    document.body.appendChild(card);
    try {
      const row = await fixture(
        html`<wavy-wave-nav-row source-wave-id="w+other2"></wavy-wave-nav-row>`
      );
      controllerModule.start();
      await Promise.resolve();
      expect(row.hasAttribute("pinned")).to.be.false;

      row.setAttribute("source-wave-id", "w+switch-pin");
      await new Promise((r) => setTimeout(r, 0));
      expect(
        row.hasAttribute("pinned"),
        "pin state hydrated after wave switch"
      ).to.be.true;

      const completed = new Promise((resolve) =>
        document.addEventListener(
          "wavy-folder-action-completed",
          (e) => resolve(e.detail),
          { once: true }
        )
      );
      row.dispatchEvent(
        new CustomEvent("wave-nav-pin-toggle-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "w+switch-pin" }
        })
      );
      const detail = await completed;
      expect(detail.operation, "first click must unpin an already-pinned wave").to.equal("unpin");
      const url = new URL(stub.calls[0].url, window.location.origin);
      expect(url.searchParams.get("operation")).to.equal("unpin");
    } finally {
      card.remove();
    }
  });

  it("hydrateFromDigest seeds archived via rail data-active-folder when card lacks archived attr", async () => {
    stub = installFetchStub(async () => okResponse());
    const rail = document.createElement("wavy-search-rail");
    rail.setAttribute("data-active-folder", "archive");
    document.body.appendChild(rail);
    try {
      const wrapper = await fixture(
        html`<div>
          <wavy-search-rail-card data-wave-id="w+arc-rail"></wavy-search-rail-card>
          <wavy-wave-nav-row source-wave-id="w+arc-rail"></wavy-wave-nav-row>
        </div>`
      );
      const row = wrapper.querySelector("wavy-wave-nav-row");
      controllerModule.start();
      await Promise.resolve();
      expect(
        row.hasAttribute("archived"),
        "archived hydrated from rail active-folder=archive"
      ).to.be.true;

      // First click on an archived row must send restore-to-inbox, not re-archive.
      const completed = new Promise((resolve) =>
        document.addEventListener(
          "wavy-folder-action-completed",
          (e) => resolve(e.detail),
          { once: true }
        )
      );
      row.dispatchEvent(
        new CustomEvent("wave-nav-archive-toggle-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "w+arc-rail" }
        })
      );
      const detail = await completed;
      expect(detail.folder, "first click must restore to inbox").to.equal("inbox");
      const url = new URL(stub.calls[0].url, window.location.origin);
      expect(url.searchParams.get("folder")).to.equal("inbox");
    } finally {
      rail.remove();
      stub.restore();
    }
  });

  it("hydrateFromDigest seeds archived via rail active-folder when no digest card is present", async () => {
    stub = installFetchStub(async () => okResponse());
    const rail = document.createElement("wavy-search-rail");
    rail.setAttribute("data-active-folder", "archive");
    document.body.appendChild(rail);
    try {
      const row = await fixture(
        html`<wavy-wave-nav-row source-wave-id="w+nocard-arc"></wavy-wave-nav-row>`
      );
      controllerModule.start();
      await Promise.resolve();
      expect(
        row.hasAttribute("archived"),
        "archived hydrated from rail active-folder when no card present"
      ).to.be.true;

      const completed = new Promise((resolve) =>
        document.addEventListener(
          "wavy-folder-action-completed",
          (e) => resolve(e.detail),
          { once: true }
        )
      );
      row.dispatchEvent(
        new CustomEvent("wave-nav-archive-toggle-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "w+nocard-arc" }
        })
      );
      const detail = await completed;
      expect(detail.folder, "first click must restore to inbox, not re-archive").to.equal("inbox");
    } finally {
      rail.remove();
      stub.restore();
    }
  });

  it("binding is idempotent — repeated scans do not double-fire", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+id"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();
    // Stop + start again should not double-bind (Set-based guard).
    controllerModule.stop();
    controllerModule.start();
    await Promise.resolve();
    let fireCount = 0;
    document.addEventListener(
      "wavy-folder-action-completed",
      () => fireCount++
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+id" }
      })
    );
    await new Promise((r) => setTimeout(r, 10));
    expect(fireCount, "completed event must fire exactly once").to.equal(1);
  });
});
