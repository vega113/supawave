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

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json" }
  });
}

async function waitFor(assertion, attempts = 20) {
  let lastError;
  for (let i = 0; i < attempts; i += 1) {
    try {
      assertion();
      return;
    } catch (err) {
      lastError = err;
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  }
  throw lastError;
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

    // Simulate J2clSelectedWaveController: render() sets source-wave-id, then
    // publishNavRowFolderState() publishes the new wave's folder state (w+b is
    // not pinned). Both happen synchronously before the MutationObserver fires.
    row.setAttribute("source-wave-id", "w+b");
    row.removeAttribute("pinned"); // model clears stale controller state for w+b
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
    // Simulate model publish: render() changes source-wave-id and
    // publishNavRowFolderState() clears pinned for w+fast (not pinned).
    row.setAttribute("source-wave-id", "w+fast");
    row.removeAttribute("pinned"); // model publishes pinned=false for w+fast
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
    stub = installFetchStub(async (url) => {
      const parsed = new URL(String(url), window.location.origin);
      if (parsed.pathname.endsWith("/api/info")) {
        return jsonResponse({ version: 1, canRestore: false });
      }
      if (parsed.pathname.endsWith("/api/history")) {
        return jsonResponse([]);
      }
      throw new Error(`unexpected fetch ${parsed.pathname}`);
    });
    const wrapper = await fixture(
      html`<div>
        <wavy-wave-nav-row source-wave-id="example.com/w+v"></wavy-wave-nav-row>
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
          detail: { sourceWaveId: "example.com/w+v" }
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

  it("version-history click wires /history loader and enables restore from canRestore", async () => {
    stub = installFetchStub(async (url) => {
      const parsed = new URL(String(url), window.location.origin);
      if (parsed.pathname.endsWith("/api/info")) {
        return jsonResponse({ version: 7, canRestore: true });
      }
      if (parsed.pathname.endsWith("/api/history")) {
        expect(parsed.searchParams.get("start")).to.equal("0");
        return jsonResponse([
          {
            appliedAt: 0,
            resultingVersion: 3,
            author: "alice@example.com",
            timestamp: 1770000010000,
            opCount: 2
          },
          {
            appliedAt: 3,
            resultingVersion: 7,
            author: "bob@example.com",
            timestamp: 1770000020000,
            opCount: 1
          }
        ]);
      }
      throw new Error(`unexpected fetch ${parsed.pathname}`);
    });
    const wrapper = await fixture(
      html`<div>
        <wavy-wave-nav-row source-wave-id="example.com/w+v"></wavy-wave-nav-row>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "example.com/w+v" }
        })
      );
      await overlay.updateComplete;
      await waitFor(() => expect(stub.calls).to.have.lengthOf(2));
      await waitFor(() => expect(overlay.versions).to.have.lengthOf(2));
      await overlay.updateComplete;

      expect(stub.calls).to.have.lengthOf(2);
      const first = new URL(stub.calls[0].url, window.location.origin);
      const second = new URL(stub.calls[1].url, window.location.origin);
      expect(first.pathname).to.equal(
        "/history/example.com/w%2Bv/example.com/conv%2Broot/api/info"
      );
      expect(second.pathname).to.equal(
        "/history/example.com/w%2Bv/example.com/conv%2Broot/api/history"
      );
      expect(overlay.restoreEnabled).to.equal(true);
      expect(overlay.versions.map((v) => v.version)).to.deep.equal([3, 7]);
      expect(overlay.versions[0].label).to.equal("v3");
    } finally {
      overlay.remove();
    }
  });

  it("version-history changed fetches snapshot preview for the selected historical version", async () => {
    let resolveSnapshot;
    stub = installFetchStub(async (url) => {
      const parsed = new URL(String(url), window.location.origin);
      if (parsed.pathname.endsWith("/api/info")) {
        return jsonResponse({ version: 7, canRestore: false });
      }
      if (parsed.pathname.endsWith("/api/history")) {
        return jsonResponse([{ appliedAt: 0, resultingVersion: 3, timestamp: 10, opCount: 1 }]);
      }
      if (parsed.pathname.endsWith("/api/snapshot")) {
        expect(parsed.searchParams.get("version")).to.equal("3");
        return new Promise((resolve) => {
          resolveSnapshot = () =>
            resolve(
              jsonResponse({
                version: 3,
                participants: ["alice@example.com"],
                documents: [{ id: "b+root", content: "Historical root" }]
              })
            );
        });
      }
      throw new Error(`unexpected fetch ${parsed.pathname}`);
    });
    const wrapper = await fixture(
      html`<div>
        <wavy-wave-nav-row source-wave-id="example.com/w+snap"></wavy-wave-nav-row>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "example.com/w+snap" }
        })
      );
      await overlay.updateComplete;
      await waitFor(() => expect(overlay.versions).to.have.lengthOf(1));
      await overlay.updateComplete;
      overlay.snapshot = {
        version: 2,
        documents: [{ id: "b+root", content: "Stale preview" }]
      };
      await overlay.updateComplete;

      overlay.dispatchEvent(
        new CustomEvent("wavy-version-changed", {
          bubbles: true,
          composed: true,
          detail: { index: 0, version: overlay.versions[0] }
        })
      );
      await waitFor(() => expect(resolveSnapshot).to.be.a("function"));
      await overlay.updateComplete;
      expect(overlay.snapshot).to.equal(null);

      resolveSnapshot();
      await waitFor(() => expect(overlay.snapshot).to.exist);
      await overlay.updateComplete;

      expect(overlay.snapshot.version).to.equal(3);
      expect(overlay.snapshot.documents[0].content).to.equal("Historical root");
    } finally {
      overlay.remove();
    }
  });

  it("keeps restore disabled when the server allows restore but no history entries load", async () => {
    stub = installFetchStub(async (url) => {
      const parsed = new URL(String(url), window.location.origin);
      if (parsed.pathname.endsWith("/api/info")) {
        return jsonResponse({ version: 0, canRestore: true });
      }
      if (parsed.pathname.endsWith("/api/history")) {
        return jsonResponse([]);
      }
      throw new Error(`unexpected fetch ${parsed.pathname}`);
    });
    const wrapper = await fixture(
      html`<div>
        <wavy-wave-nav-row source-wave-id="example.com/w+empty"></wavy-wave-nav-row>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "example.com/w+empty" }
        })
      );
      await waitFor(() => expect(stub.calls).to.have.lengthOf(2));
      await overlay.updateComplete;

      expect(overlay.versions).to.deep.equal([]);
      expect(overlay.restoreEnabled).to.equal(false);
      expect(overlay.renderRoot.querySelector("button.restore").hasAttribute("disabled")).to.equal(
        true
      );
    } finally {
      overlay.remove();
    }
  });

  it("restore confirmation POSTs existing API and requests selected-wave refresh", async () => {
    stub = installFetchStub(async (url, init) => {
      const parsed = new URL(String(url), window.location.origin);
      if (parsed.pathname.endsWith("/api/info")) {
        return jsonResponse({ version: 7, canRestore: true });
      }
      if (parsed.pathname.endsWith("/api/history")) {
        return jsonResponse([{ appliedAt: 0, resultingVersion: 3, timestamp: 10, opCount: 1 }]);
      }
      if (parsed.pathname.endsWith("/api/restore")) {
        expect(init.method).to.equal("POST");
        expect(parsed.searchParams.get("version")).to.equal("3");
        return jsonResponse({ ok: true, restoredToVersion: 3, opsApplied: 2 });
      }
      throw new Error(`unexpected fetch ${parsed.pathname}`);
    });
    const wrapper = await fixture(
      html`<div>
        <section class="sidecar-selected-card">
          <wavy-wave-nav-row source-wave-id="example.com/w+restore"></wavy-wave-nav-row>
        </section>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "example.com/w+restore" }
        })
      );
      await overlay.updateComplete;
      await waitFor(() => expect(overlay.versions).to.have.lengthOf(1));
      await overlay.updateComplete;

      const refreshed = new Promise((resolve) =>
        document.addEventListener(
          "wavy-selected-wave-refresh-requested",
          (e) => resolve(e.detail),
          { once: true }
        )
      );
      overlay.dispatchEvent(
        new CustomEvent("wavy-version-restore-confirmed", {
          bubbles: true,
          composed: true,
          detail: { index: 0, version: overlay.versions[0] }
        })
      );
      const detail = await refreshed;
      await overlay.updateComplete;

      expect(detail).to.deep.equal({
        waveId: "example.com/w+restore",
        reason: "version-restore",
        restoredToVersion: 3
      });
      expect(overlay.restoreStatus).to.match(/restored/i);
    } finally {
      overlay.remove();
    }
  });

  it("restore failure is visible and does not request selected-wave refresh", async () => {
    stub = installFetchStub(async (url) => {
      const parsed = new URL(String(url), window.location.origin);
      if (parsed.pathname.endsWith("/api/info")) {
        return jsonResponse({ version: 7, canRestore: true });
      }
      if (parsed.pathname.endsWith("/api/history")) {
        return jsonResponse([{ appliedAt: 0, resultingVersion: 3, timestamp: 10, opCount: 1 }]);
      }
      if (parsed.pathname.endsWith("/api/restore")) {
        return new Response("restore denied", { status: 403 });
      }
      throw new Error(`unexpected fetch ${parsed.pathname}`);
    });
    const wrapper = await fixture(
      html`<div>
        <section class="sidecar-selected-card">
          <wavy-wave-nav-row source-wave-id="example.com/w+denied"></wavy-wave-nav-row>
        </section>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "example.com/w+denied" }
        })
      );
      await waitFor(() => expect(overlay.versions).to.have.lengthOf(1));

      let refreshed = false;
      document.addEventListener(
        "wavy-selected-wave-refresh-requested",
        () => { refreshed = true; },
        { once: true }
      );
      overlay.dispatchEvent(
        new CustomEvent("wavy-version-restore-confirmed", {
          bubbles: true,
          composed: true,
          detail: { index: 0, version: overlay.versions[0] }
        })
      );
      await waitFor(() => expect(overlay.error).to.match(/restore denied/));
      expect(refreshed).to.equal(false);
      expect(overlay.restoreStatus).to.equal("");
    } finally {
      overlay.remove();
    }
  });

  it("malformed source-wave-id leaves version history inert and does not fetch", async () => {
    stub = installFetchStub(async () => {
      throw new Error("fetch should not be called");
    });
    const wrapper = await fixture(
      html`<div>
        <wavy-wave-nav-row source-wave-id="malformed"></wavy-wave-nav-row>
        <wavy-version-history hidden></wavy-version-history>
      </div>`
    );
    const row = wrapper.querySelector("wavy-wave-nav-row");
    const overlay = wrapper.querySelector("wavy-version-history");
    document.body.appendChild(overlay);
    try {
      controllerModule.start();
      await Promise.resolve();
      row.dispatchEvent(
        new CustomEvent("wave-nav-version-history-requested", {
          bubbles: true,
          composed: true,
          detail: { sourceWaveId: "malformed" }
        })
      );
      await overlay.updateComplete;
      await new Promise((r) => setTimeout(r, 0));
      expect(overlay.open).to.equal(false);
      expect(stub.calls).to.have.lengthOf(0);
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

  it("falls back to one-shot binding if MutationObserver construction fails", async () => {
    stub = installFetchStub(async () => okResponse());
    const originalMutationObserver = window.MutationObserver;
    window.MutationObserver = class {
      constructor() {
        throw new Error("observer unavailable");
      }
    };
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+fallback"></wavy-wave-nav-row>`
    );
    try {
      controllerModule.start();
      await Promise.resolve();
      expect(row.hasAttribute("data-action-bar-bound")).to.be.true;

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
          detail: { sourceWaveId: "w+fallback" }
        })
      );
      const detail = await completed;
      expect(detail.operation).to.equal("pin");
    } finally {
      controllerModule.stop();
      window.MutationObserver = originalMutationObserver;
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

  it("hydrateFromDigest seeds pinned via rail active-folder when no digest card is present", async () => {
    stub = installFetchStub(async () => okResponse());
    const rail = document.createElement("wavy-search-rail");
    rail.setAttribute("data-active-folder", "pinned");
    document.body.appendChild(rail);
    try {
      const row = await fixture(
        html`<wavy-wave-nav-row source-wave-id="w+nocard-pin"></wavy-wave-nav-row>`
      );
      controllerModule.start();
      await Promise.resolve();
      expect(
        row.hasAttribute("pinned"),
        "pinned hydrated from rail active-folder when no card present"
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
          detail: { sourceWaveId: "w+nocard-pin" }
        })
      );
      const detail = await completed;
      expect(detail.operation, "first click must unpin, not pin").to.equal("unpin");
    } finally {
      rail.remove();
      stub.restore();
    }
  });

  it("hydrateFromDigest leaves toggles unset when no digest card or active folder exists", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row source-wave-id="w+nocard-default"></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    expect(row.hasAttribute("pinned"), "no rail active-folder means no pinned fallback").to.be
      .false;
    expect(row.hasAttribute("archived"), "no rail active-folder means no archive fallback").to.be
      .false;
  });

  it("syncFolderStateForWave preserves model-published pinned state when no rail card exists", async () => {
    // Simulates J2clSelectedWaveController.publishNavRowFolderState() setting
    // marker+pinned synchronously in the same JS task as render() sets source-wave-id.
    // The MutationObserver fires after both, so syncFolderStateForWave must
    // preserve model-backed state instead of treating it as stale optimistic state.
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+modpin-a"
        data-folder-state-wave-id="w+modpin-a"
        pinned
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    // Simulate Java setting source-wave-id, ownership marker, and pinned in
    // one sync task: any order inside this task completes before
    // MutationObserver fires.
    row.setAttribute("source-wave-id", "w+modpin-b");
    row.setAttribute("data-folder-state-wave-id", "w+modpin-b");
    row.setAttribute("pinned", ""); // model published pinned=true for wave b
    await new Promise((r) => setTimeout(r, 0)); // let observer fire

    expect(
      row.hasAttribute("pinned"),
      "model-published pinned must survive wave-id switch with no rail card"
    ).to.be.true;
    expect(row.getAttribute("data-folder-state-wave-id")).to.equal("w+modpin-b");

    // First click must send unpin (wave is already pinned per model).
    const completed = new Promise((resolve) =>
      document.addEventListener("wavy-folder-action-completed", (e) => resolve(e.detail), {
        once: true
      })
    );
    row.dispatchEvent(
      new CustomEvent("wave-nav-pin-toggle-requested", {
        bubbles: true,
        composed: true,
        detail: { sourceWaveId: "w+modpin-b" }
      })
    );
    const detail = await completed;
    expect(detail.operation, "first click must unpin, not double-pin").to.equal("unpin");
  });

  it("syncFolderStateForWave does not restore stale pinned when model clears it on wave switch", async () => {
    stub = installFetchStub(async () => okResponse());
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+stale-a"
        data-folder-state-wave-id="w+stale-a"
        pinned
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    // Model switches wave and publishes cleared pinned state for wave-b.
    row.setAttribute("source-wave-id", "w+stale-b");
    row.setAttribute("data-folder-state-wave-id", "w+stale-b");
    row.removeAttribute("pinned"); // model published pinned=false for wave b
    await new Promise((r) => setTimeout(r, 0));

    expect(
      row.hasAttribute("pinned"),
      "stale pinned from previous wave must not be restored"
    ).to.be.false;
    expect(row.getAttribute("data-folder-state-wave-id")).to.equal("w+stale-b");
  });

  it("clears stale busy owner when Java stamps new marker before observer fires", async () => {
    // Wave A had an in-flight folder action; user switches to wave B.
    // Java stamps data-folder-state-wave-id=B synchronously, so the observer
    // sees current===waveId and must still clear the stale busy from A.
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+wave-a"
        data-folder-state-wave-id="w+wave-a"
        data-folder-busy
        data-folder-busy-wave-id="w+wave-a"
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    // Java switches selection and stamps the new marker before the observer fires.
    row.setAttribute("data-folder-state-wave-id", "w+wave-b");
    row.setAttribute("source-wave-id", "w+wave-b");
    await new Promise((r) => setTimeout(r, 0));

    expect(row.hasAttribute("data-folder-busy"), "stale busy from wave-a must be cleared").to.be.false;
    expect(row.hasAttribute("data-folder-busy-wave-id"), "stale busy-wave-id from wave-a must be cleared").to.be.false;
    expect(row.getAttribute("data-folder-state-wave-id")).to.equal("w+wave-b");
  });

  it("preserves model-published archived state when source wave changes before observer flush", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+old"
        data-folder-state-wave-id="w+old"
        archived
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    row.setAttribute("source-wave-id", "w+new");
    row.setAttribute("data-folder-state-wave-id", "w+new");
    row.setAttribute("archived", "");
    await new Promise((r) => setTimeout(r, 0));

    expect(row.hasAttribute("archived"), "model-published archived must survive observer sync")
      .to.be.true;
    expect(row.getAttribute("data-folder-state-wave-id")).to.equal("w+new");
  });

  it("does not hydrate stale digest state over a model-published cleared state", async () => {
    const card = document.createElement("wavy-search-rail-card");
    card.setAttribute("data-wave-id", "w+clear");
    card.setAttribute("pinned", "");
    document.body.appendChild(card);
    try {
      const row = await fixture(
        html`<wavy-wave-nav-row
          source-wave-id="w+clear"
          data-folder-state-wave-id="w+clear"
        ></wavy-wave-nav-row>`
      );
      controllerModule.start();
      await Promise.resolve();

      expect(row.hasAttribute("pinned"), "model-owned cleared state must win over stale digest")
        .to.be.false;
      expect(row.getAttribute("data-folder-state-wave-id")).to.equal("w+clear");
    } finally {
      card.remove();
    }
  });

  it("clears folder state and marker when source wave id is removed", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+old"
        data-folder-state-wave-id="w+old"
        pinned
        archived
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    row.removeAttribute("source-wave-id");
    await new Promise((r) => setTimeout(r, 0));

    expect(row.hasAttribute("pinned")).to.be.false;
    expect(row.hasAttribute("archived")).to.be.false;
    expect(row.hasAttribute("data-folder-state-wave-id")).to.be.false;
  });

  it("clears in-flight busy state when source wave id is removed after marker cleanup", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+old"
        data-folder-state-wave-id="w+old"
        data-folder-busy
        data-folder-busy-wave-id="w+old"
        pinned
        archived
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    // Java clears the ownership marker synchronously with deselect before the
    // source-wave-id MutationObserver callback runs. The controller still owns
    // the pending optimistic busy affordance and must clear it on no-source.
    row.removeAttribute("data-folder-state-wave-id");
    row.removeAttribute("source-wave-id");
    await new Promise((r) => setTimeout(r, 0));

    expect(row.hasAttribute("data-folder-busy")).to.be.false;
    expect(row.hasAttribute("data-folder-busy-wave-id")).to.be.false;
    expect(row.hasAttribute("pinned")).to.be.false;
    expect(row.hasAttribute("archived")).to.be.false;
    expect(row.hasAttribute("data-folder-state-wave-id")).to.be.false;
  });

  it("clears busy state for any previous owner when source wave id is removed", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+old"
        data-folder-busy
        data-folder-busy-wave-id="w+other"
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    row.removeAttribute("source-wave-id");
    await new Promise((r) => setTimeout(r, 0));

    expect(row.hasAttribute("data-folder-busy")).to.be.false;
    expect(row.hasAttribute("data-folder-busy-wave-id")).to.be.false;
  });

  it("clears stale optimistic folder state when ownership marker does not match source wave", async () => {
    const row = await fixture(
      html`<wavy-wave-nav-row
        source-wave-id="w+old"
        data-folder-state-wave-id="w+old"
        pinned
      ></wavy-wave-nav-row>`
    );
    controllerModule.start();
    await Promise.resolve();

    row.setAttribute("source-wave-id", "w+new");
    await new Promise((r) => setTimeout(r, 0));

    expect(row.getAttribute("data-folder-state-wave-id")).to.equal("w+new");
    expect(
      row.hasAttribute("pinned"),
      "stale optimistic pinned must clear when Java has not stamped model ownership"
    ).to.be.false;
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
