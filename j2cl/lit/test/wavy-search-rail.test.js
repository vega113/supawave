import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-search-rail.js";

const waitForMicrotasks = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("<wavy-search-rail>", () => {
  it("registers the F-2.S3 search-rail element", () => {
    expect(customElements.get("wavy-search-rail")).to.exist;
  });

  it("defaults to query=in:inbox and active folder=inbox", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    expect(el.query).to.equal("in:inbox");
    expect(el.activeFolder).to.equal("inbox");
    const inbox = el.renderRoot.querySelector('[data-folder-id="inbox"]');
    expect(inbox.getAttribute("aria-current")).to.equal("page");
  });

  it("renders all six saved-search folders with canonical query strings (B.5–B.10)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const folders = Array.from(el.renderRoot.querySelectorAll("button.folder"));
    const map = new Map(folders.map((b) => [b.dataset.folderId, b.dataset.query]));
    expect(map.get("inbox")).to.equal("in:inbox");
    expect(map.get("mentions")).to.equal("mentions:me");
    expect(map.get("tasks")).to.equal("tasks:me");
    expect(map.get("public")).to.equal("with:@");
    expect(map.get("archive")).to.equal("in:archive");
    expect(map.get("pinned")).to.equal("in:pinned");
    const labels = folders.map((b) => b.querySelector(".label").textContent.trim());
    expect(labels).to.deep.equal(["Inbox", "Mentions", "Tasks", "Public", "Archive", "Pinned"]);
  });

  it("Enter in the query box emits wavy-search-submit (B.1)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const input = el.renderRoot.querySelector("input.query");
    input.value = "in:archive tag:work";
    setTimeout(() => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    }, 0);
    const evt = await oneEvent(el, "wavy-search-submit");
    expect(evt.detail.query).to.equal("in:archive tag:work");
  });

  it("renders waveform glyph next to the query input (B.1)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    expect(el.renderRoot.querySelector(".waveform svg")).to.exist;
  });

  it("help-trigger click emits wavy-search-help-toggle (B.2; modal not owned by rail)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const help = el.renderRoot.querySelector(".help-trigger");
    expect(help).to.exist;
    expect(help.getAttribute("aria-haspopup")).to.equal("dialog");
    expect(help.getAttribute("aria-controls")).to.equal("wavy-search-help");
    setTimeout(() => help.click(), 0);
    const evt = await oneEvent(el, "wavy-search-help-toggle");
    expect(evt).to.exist;
    // The rail does NOT own a child <wavy-search-help> instance — the
    // singleton lives at document level.
    expect(el.renderRoot.querySelector("wavy-search-help")).to.be.null;
  });

  it("New Wave button click emits wavy-new-wave-requested and carries aria-keyshortcuts (B.3)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const newWave = el.renderRoot.querySelector(".new-wave");
    expect(newWave).to.exist;
    expect(newWave.getAttribute("aria-keyshortcuts")).to.equal(
      "Shift+Meta+O Shift+Control+O"
    );
    expect(newWave.dataset.shortcut).to.equal("Shift+Cmd+O");
    setTimeout(() => newWave.click(), 0);
    const evt = await oneEvent(el, "wavy-new-wave-requested");
    expect(evt).to.exist;
    expect(evt.detail.source).to.equal("button");
  });

  it("Manage saved searches click emits wavy-manage-saved-searches-requested (B.4)", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response("[]", {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      const manage = el.renderRoot.querySelector(".manage-saved");
      setTimeout(() => manage.click(), 0);
      const evt = await oneEvent(el, "wavy-manage-saved-searches-requested");
      expect(evt).to.exist;
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("Manage saved searches opens a dialog populated from /searches", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      expect(url).to.equal("/searches");
      return new Response(
        JSON.stringify([{ name: "Mine", query: "creator:me", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await waitForMicrotasks();
      await el.updateComplete;
      const dialog = el.renderRoot.querySelector('[role="dialog"]');
      expect(dialog, "saved-search dialog opens").to.exist;
      expect(dialog.textContent).to.contain("Manage saved searches");
      expect(dialog.querySelector('input[aria-label="Saved search name"]').value).to.equal("Mine");
      expect(dialog.querySelector('input[aria-label="Saved search query"]').value).to.equal("creator:me");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("surfaces malformed saved-search rows from /searches instead of dropping them", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response(
      JSON.stringify([{ name: "", query: "tag:needs-name", pinned: true }]),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await waitForMicrotasks();
      await el.updateComplete;
      const dialog = el.renderRoot.querySelector('[role="dialog"]');
      expect(dialog).to.exist;
      expect(dialog.querySelector(".saved-error").textContent).to.contain(
        "need both a name and a query"
      );
      expect(dialog.querySelector('input[aria-label="Saved search name"]').value).to.equal("");
      expect(dialog.querySelector('input[aria-label="Saved search query"]').value).to.equal("tag:needs-name");
      expect(dialog.querySelector(".saved-row .saved-button").getAttribute("aria-label")).to.equal(
        "Apply tag:needs-name"
      );
      expect(
        Array.from(dialog.querySelectorAll(".saved-row .saved-button"))
          .at(-1)
          .getAttribute("aria-label")
      ).to.equal("Remove tag:needs-name");
      expect(el.renderRoot.querySelector(".custom-search")).to.be.null;
      el.renderRoot.querySelector('button[aria-label="Close saved searches"]').click();
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await el.updateComplete;
      const reopened = el.renderRoot.querySelector('[role="dialog"]');
      expect(reopened.querySelector(".saved-error").textContent).to.contain(
        "need both a name and a query"
      );
      expect(reopened.querySelector('input[aria-label="Saved search query"]').value).to.equal("tag:needs-name");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("renders pinned saved searches as quick-access buttons that apply queries", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    el.savedSearches = [
      { name: "Bugs", query: "tag:bug", pinned: true },
      { name: "Hidden", query: "tag:hidden", pinned: false }
    ];
    await el.updateComplete;
    const buttons = Array.from(el.renderRoot.querySelectorAll(".custom-search"));
    expect(buttons.map((button) => button.dataset.savedSearchName)).to.deep.equal(["Bugs"]);
    expect(buttons[0].getAttribute("aria-label")).to.equal("Apply saved search Bugs (tag:bug)");
    setTimeout(() => buttons[0].click(), 0);
    const evt = await oneEvent(el, "wavy-saved-search-selected");
    expect(evt.detail.folderId).to.equal("");
    expect(evt.detail.kind).to.equal("custom");
    expect(evt.detail.label).to.equal("Bugs");
    expect(evt.detail.query).to.equal("tag:bug");
    expect(evt.detail.savedSearchName).to.equal("Bugs");
  });

  it("keeps focus on pinned saved search buttons and suppresses same-query apply", async () => {
    const el = await fixture(html`<wavy-search-rail query="tag:bug"></wavy-search-rail>`);
    await el.updateComplete;
    el.savedSearches = [{ name: "Bugs", query: "tag:bug", pinned: true }];
    await el.updateComplete;
    let selectedCount = 0;
    el.addEventListener("wavy-saved-search-selected", () => {
      selectedCount += 1;
    });
    const button = el.renderRoot.querySelector(".custom-search");
    button.click();
    await waitForMicrotasks();
    await el.updateComplete;
    expect(selectedCount).to.equal(0);
    expect(el.renderRoot.activeElement).to.equal(button);
  });

  it("autoloads pinned saved searches when requested by SSR", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response(
      JSON.stringify([{ name: "Auto", query: "tag:auto", pinned: true }]),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
    const el = await fixture(html`<wavy-search-rail autoload-saved-searches></wavy-search-rail>`);
    try {
      await waitForMicrotasks();
      await el.updateComplete;
      const button = el.renderRoot.querySelector(".custom-search");
      expect(button, "autoloaded pinned saved search renders").to.exist;
      expect(button.dataset.savedSearchName).to.equal("Auto");
      expect(button.dataset.query).to.equal("tag:auto");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("does not autoload saved searches without the SSR opt-in attribute", async () => {
    const originalFetch = globalThis.fetch;
    let calls = 0;
    globalThis.fetch = async () => {
      calls += 1;
      return new Response("[]", {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    };
    try {
      await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await waitForMicrotasks();
      expect(calls).to.equal(0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("treats unauthorized saved-search autoload as an empty list", async () => {
    const originalFetch = globalThis.fetch;
    let calls = 0;
    globalThis.fetch = async () => {
      calls += 1;
      return new Response("", { status: 401 });
    };
    const el = await fixture(html`<wavy-search-rail autoload-saved-searches></wavy-search-rail>`);
    try {
      await waitForMicrotasks();
      await el.updateComplete;
      expect(el.savedSearches).to.deep.equal([]);
      expect(el.savedSearchesError).to.equal("");
      expect(el._savedSearchesLoaded).to.equal(false);
      await el._loadSavedSearches();
      expect(calls).to.equal(2);
      expect(el.renderRoot.querySelector(".custom-search")).to.be.null;
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("shows a sign-in message when saved-search management is unauthorized", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response("", { status: 401 });
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await waitForMicrotasks();
      await el.updateComplete;
      const dialog = el.renderRoot.querySelector('[role="dialog"]');
      expect(dialog).to.exist;
      expect(dialog.querySelector(".saved-error").textContent).to.equal(
        "Sign in to manage saved searches."
      );
      expect(dialog.querySelector(".saved-empty")).to.be.null;
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("does not wipe a cached saved-search list after a later unauthorized reload", async () => {
    const originalFetch = globalThis.fetch;
    const responses = [
      new Response(
        JSON.stringify([{ name: "Cached", query: "tag:cached", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      ),
      new Response("", { status: 401 })
    ];
    globalThis.fetch = async () => responses.shift();
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el._loadSavedSearches();
      expect(el.savedSearches[0].name).to.equal("Cached");
      el.savedSearchesOpen = true;
      await el._loadSavedSearches(true);
      expect(el.savedSearches[0].name).to.equal("Cached");
      expect(el.savedSearchesError).to.equal("Sign in to refresh saved searches.");
      expect(el._savedSearchesLoaded).to.equal(true);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("keeps malformed saved-search warnings after an unauthorized forced reload", async () => {
    const originalFetch = globalThis.fetch;
    const responses = [
      new Response(
        JSON.stringify([{ name: "", query: "tag:needs-name", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      ),
      new Response("", { status: 401 })
    ];
    globalThis.fetch = async () => responses.shift();
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el._loadSavedSearches();
      expect(el.savedSearchesError).to.contain("need both a name and a query");
      await el._loadSavedSearches(true);
      expect(el.savedSearchesError).to.contain("need both a name and a query");
      expect(el.savedSearchDrafts[0].query).to.equal("tag:needs-name");
      expect(el._savedSearchesLoaded).to.equal(true);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("surfaces a friendly saved-search load failure message", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response("", { status: 500 });
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await waitForMicrotasks();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".saved-error").textContent).to.equal(
        "Unable to load saved searches."
      );
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("saves an added current search through /searches", async () => {
    const originalFetch = globalThis.fetch;
    const posts = [];
    globalThis.fetch = async (url, options = {}) => {
      if (!options.method || options.method === "GET") {
        return new Response("[]", {
          status: 200,
          headers: { "Content-Type": "application/json" }
        });
      }
      posts.push({ url, options });
      return new Response("", { status: 200 });
    };
    const el = await fixture(html`<wavy-search-rail query="tag:work"></wavy-search-rail>`);
    try {
      await el.updateComplete;
      const manage = el.renderRoot.querySelector(".manage-saved");
      manage.click();
      await waitForMicrotasks();
      await el.updateComplete;
      await waitForMicrotasks();
      await el.updateComplete;
      el.renderRoot.querySelector(".saved-footer .saved-button").click();
      await el.updateComplete;
      el.renderRoot.querySelector(".saved-button.primary").click();
      await waitForMicrotasks();
      expect(posts.length).to.equal(1);
      expect(posts[0].url).to.equal("/searches");
      expect(posts[0].options.method).to.equal("POST");
      expect(JSON.parse(posts[0].options.body)).to.deep.equal([
        { name: "Saved search 1", query: "tag:work", pinned: true }
      ]);
      await el.updateComplete;
      await waitForMicrotasks();
      expect(el.renderRoot.activeElement).to.equal(manage);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("applying a dirty saved-search draft does not persist it implicitly", async () => {
    const originalFetch = globalThis.fetch;
    const posts = [];
    globalThis.fetch = async (url, options = {}) => {
      if (options.method === "POST") {
        posts.push({ url, options });
        return new Response("", { status: 200 });
      }
      return new Response(
        JSON.stringify([{ name: "Draft", query: "tag:old", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      const manage = el.renderRoot.querySelector(".manage-saved");
      manage.click();
      await waitForMicrotasks();
      await el.updateComplete;
      const queryInput = el.renderRoot.querySelector('input[aria-label="Saved search query"]');
      queryInput.value = "tag:new";
      queryInput.dispatchEvent(new Event("input", { bubbles: true }));
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".saved-hint").textContent).to.contain(
        "discards unsaved edits"
      );
      expect(el.renderRoot.querySelector(".saved-hint").getAttribute("role")).to.equal("status");
      setTimeout(() => el.renderRoot.querySelector(".saved-row .saved-button").click(), 0);
      const evt = await oneEvent(el, "wavy-saved-search-selected");
      expect(evt.detail.query).to.equal("tag:new");
      expect(posts.length).to.equal(0);
      await el.updateComplete;
      await waitForMicrotasks();
      expect(el.renderRoot.activeElement).to.equal(manage);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("shows validation instead of dropping invalid saved-search rows", async () => {
    const originalFetch = globalThis.fetch;
    let postCount = 0;
    globalThis.fetch = async (_url, options = {}) => {
      if (options.method === "POST") {
        postCount += 1;
        return new Response("", { status: 200 });
      }
      return new Response(
        JSON.stringify([{ name: "Broken", query: "tag:work", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await waitForMicrotasks();
      await el.updateComplete;
      const nameInput = el.renderRoot.querySelector('input[aria-label="Saved search name"]');
      nameInput.value = "";
      nameInput.dispatchEvent(new Event("input", { bubbles: true }));
      el.renderRoot.querySelector(".saved-button.primary").click();
      await waitForMicrotasks();
      await el.updateComplete;
      expect(postCount).to.equal(0);
      expect(el.renderRoot.querySelector(".saved-error").textContent).to.contain(
        "needs both a name and a query"
      );
      expect(el.renderRoot.querySelector(".saved-error").getAttribute("role")).to.equal("alert");
      expect(el.renderRoot.querySelector('[role="dialog"]')).to.exist;
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("dedupes concurrent saved-search loads", async () => {
    const originalFetch = globalThis.fetch;
    let calls = 0;
    let resolveFetch;
    const responseReady = new Promise((resolve) => {
      resolveFetch = resolve;
    });
    globalThis.fetch = async () => {
      calls += 1;
      await responseReady;
      return new Response("[]", {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      const first = el._loadSavedSearches();
      const second = el._loadSavedSearches();
      expect(calls).to.equal(1);
      resolveFetch();
      await Promise.all([first, second]);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("shares saved-search load failures across concurrent callers without rejecting", async () => {
    const originalFetch = globalThis.fetch;
    let calls = 0;
    globalThis.fetch = async () => {
      calls += 1;
      return new Response("", { status: 500 });
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      const first = el._loadSavedSearches();
      const second = el._loadSavedSearches();
      await Promise.all([first, second]);
      expect(calls).to.equal(1);
      expect(el.savedSearchesError).to.equal("Unable to load saved searches.");
      expect(el.savedSearchesLoading).to.equal(false);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("keeps a forced saved-search reload in flight when an older load finishes", async () => {
    const originalFetch = globalThis.fetch;
    let calls = 0;
    let resolveFirst;
    let resolveSecond;
    const firstReady = new Promise((resolve) => {
      resolveFirst = resolve;
    });
    const secondReady = new Promise((resolve) => {
      resolveSecond = resolve;
    });
    globalThis.fetch = async () => {
      calls += 1;
      if (calls === 1) {
        await firstReady;
        return new Response(
          JSON.stringify([{ name: "Old", query: "tag:old", pinned: true }]),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
      await secondReady;
      return new Response(
        JSON.stringify([{ name: "Fresh", query: "tag:fresh", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      const first = el._loadSavedSearches();
      const second = el._loadSavedSearches(true);
      expect(calls).to.equal(2);
      resolveFirst();
      await first;
      expect(el.savedSearchesLoading).to.equal(true);
      const third = el._loadSavedSearches();
      expect(calls).to.equal(2);
      resolveSecond();
      await Promise.all([second, third]);
      expect(el.savedSearches[0].name).to.equal("Fresh");
      expect(el.savedSearchesLoading).to.equal(false);
      expect(el._savedSearchesLoadPromise).to.equal(null);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("does not let an older saved-search load overwrite a newer forced reload", async () => {
    const originalFetch = globalThis.fetch;
    let calls = 0;
    let resolveFirst;
    let resolveSecond;
    const firstReady = new Promise((resolve) => {
      resolveFirst = resolve;
    });
    const secondReady = new Promise((resolve) => {
      resolveSecond = resolve;
    });
    globalThis.fetch = async () => {
      calls += 1;
      if (calls === 1) {
        await firstReady;
        return new Response(
          JSON.stringify([{ name: "Old", query: "tag:old", pinned: true }]),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
      await secondReady;
      return new Response(
        JSON.stringify([{ name: "Fresh", query: "tag:fresh", pinned: true }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      const first = el._loadSavedSearches();
      const second = el._loadSavedSearches(true);
      resolveSecond();
      await second;
      expect(el.savedSearches[0].name).to.equal("Fresh");
      expect(el.savedSearchesLoading).to.equal(false);
      resolveFirst();
      await first;
      expect(el.savedSearches[0].name).to.equal("Fresh");
      expect(calls).to.equal(2);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("disables adding saved searches until the dialog load settles", async () => {
    const originalFetch = globalThis.fetch;
    let resolveFetch;
    const responseReady = new Promise((resolve) => {
      resolveFetch = resolve;
    });
    globalThis.fetch = async () => {
      await responseReady;
      return new Response("[]", {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    };
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector(".manage-saved").click();
      await el.updateComplete;
      const add = el.renderRoot.querySelector(".saved-footer .saved-button");
      expect(add.disabled).to.equal(true);
      resolveFetch();
      await waitForMicrotasks();
      await el.updateComplete;
      await waitForMicrotasks();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".saved-footer .saved-button").disabled).to.equal(false);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("clears stale saved-search errors on cached reopen", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    el.savedSearches = [{ name: "Cached", query: "tag:cached", pinned: true }];
    el.savedSearchesError = "Previous failure";
    el._savedSearchesLoaded = true;
    await el._loadSavedSearches();
    expect(el.savedSearchesError).to.equal("");
    expect(el.savedSearchDrafts[0].name).to.equal("Cached");
  });

  it("does not overwrite dirty drafts on cached saved-search reload", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    el.savedSearches = [{ name: "Cached", query: "tag:cached", pinned: true }];
    el.savedSearchDrafts = [{ name: "Dirty", query: "tag:dirty", pinned: true }];
    el.savedSearchesDirty = true;
    el._savedSearchesLoaded = true;
    await el._loadSavedSearches();
    expect(el.savedSearchDrafts[0].name).to.equal("Dirty");
  });

  it("opening saved-search management closes the sort menu", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response("[]", {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      el.renderRoot.querySelector('[data-digest-action="sort"]').click();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.exist;
      el.renderRoot.querySelector(".manage-saved").click();
      await waitForMicrotasks();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;
      expect(el.renderRoot.querySelector('[role="dialog"]')).to.exist;
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("close button restores focus and discards dirty saved-search drafts", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response(
      JSON.stringify([{ name: "Close me", query: "tag:close", pinned: true }]),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    try {
      await el.updateComplete;
      const manage = el.renderRoot.querySelector(".manage-saved");
      manage.click();
      await waitForMicrotasks();
      await el.updateComplete;
      await waitForMicrotasks();
      await el.updateComplete;
      const nameInput = el.renderRoot.querySelector('input[aria-label="Saved search name"]');
      nameInput.value = "Unsaved";
      nameInput.dispatchEvent(new Event("input", { bubbles: true }));
      await el.updateComplete;
      el.renderRoot.querySelector('button[aria-label="Close saved searches"]').click();
      await el.updateComplete;
      await waitForMicrotasks();
      expect(el.renderRoot.activeElement).to.equal(manage);
      expect(el.savedSearchesDirty).to.equal(false);
      expect(el.savedSearchDrafts[0].name).to.equal("Close me");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("Refresh click emits wavy-search-refresh-requested (B.11)", async () => {
    // G-PORT-2 (#1111): Refresh moved into the panel-level action row
    // alongside Sort and Filter, accessed via `[data-digest-action]`.
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const refresh = el.renderRoot.querySelector('[data-digest-action="refresh"]');
    expect(refresh, "refresh button must mount in the action row").to.exist;
    setTimeout(() => refresh.click(), 0);
    const evt = await oneEvent(el, "wavy-search-refresh-requested");
    expect(evt).to.exist;
  });

  it("clicking a folder flips aria-current and emits wavy-saved-search-selected", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const archive = el.renderRoot.querySelector('[data-folder-id="archive"]');
    setTimeout(() => archive.click(), 0);
    const evt = await oneEvent(el, "wavy-saved-search-selected");
    expect(evt.detail.folderId).to.equal("archive");
    expect(evt.detail.query).to.equal("in:archive");
    await el.updateComplete;
    expect(archive.getAttribute("aria-current")).to.equal("page");
    expect(
      el.renderRoot
        .querySelector('[data-folder-id="inbox"]')
        .getAttribute("aria-current")
    ).to.equal("false");
  });

  it("setting query=in:archive ... derives Archive as active folder (programmatic)", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    el.query = "in:archive orderby:datedesc";
    await el.updateComplete;
    expect(el.activeFolder).to.equal("archive");
    expect(
      el.renderRoot
        .querySelector('[data-folder-id="archive"]')
        .getAttribute("aria-current")
    ).to.equal("page");
  });

  it("custom query that doesn't match a folder leaves no aria-current", async () => {
    const el = await fixture(
      html`<wavy-search-rail query="title:meeting"></wavy-search-rail>`
    );
    await el.updateComplete;
    expect(el.activeFolder).to.equal("");
    const folders = el.renderRoot.querySelectorAll("button.folder");
    folders.forEach((b) => expect(b.getAttribute("aria-current")).to.equal("false"));
  });

  it("Mentions red dot is hidden by default and revealed when mentions-unread > 0", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const dot = el.renderRoot
      .querySelector('[data-folder-id="mentions"]')
      .querySelector(".mentions-dot");
    expect(dot).to.exist;
    expect(dot.hasAttribute("hidden")).to.be.true;
    el.mentionsUnread = 3;
    await el.updateComplete;
    expect(dot.hasAttribute("hidden")).to.be.false;
  });

  it("Mentions dot uses the GWT unread red, not the task/toolbar palettes", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const dot = el.renderRoot
      .querySelector('[data-folder-id="mentions"]')
      .querySelector(".mentions-dot");
    expect(getComputedStyle(dot).backgroundColor).to.equal("rgb(229, 62, 62)");
  });

  it("Tasks amber chip is hidden by default and revealed when tasks-pending > 0", async () => {
    const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
    await el.updateComplete;
    const chip = el.renderRoot
      .querySelector('[data-folder-id="tasks"]')
      .querySelector(".tasks-chip");
    expect(chip).to.exist;
    expect(chip.hasAttribute("hidden")).to.be.true;
    el.tasksPending = 7;
    await el.updateComplete;
    expect(chip.hasAttribute("hidden")).to.be.false;
    expect(chip.textContent.trim()).to.equal("7");
  });

  it("Tasks chip uses --wavy-signal-amber", async () => {
    const cssText = WavySearchRail_styleText();
    expect(cssText).to.include("var(--wavy-signal-amber");
  });

  it("G-PORT-9: stretches the rail to the GWT viewport-height panel", async () => {
    const cssText = WavySearchRail_styleText();
    expect(cssText).to.include("min-height: var(--wavy-rail-min-height");
    expect(cssText).to.include("calc(100vh - 90px)");
  });

  it("result-count <p> is aria-live polite (B.12)", async () => {
    const el = await fixture(
      html`<wavy-search-rail result-count="133 waves"></wavy-search-rail>`
    );
    await el.updateComplete;
    const p = el.renderRoot.querySelector("p.result-count");
    expect(p).to.exist;
    expect(p.getAttribute("aria-live")).to.equal("polite");
    expect(p.textContent.trim()).to.equal("133 waves");
  });

  it("does NOT expose a default slot for SSR fallback children (#1060)", async () => {
    // F-2 follow-up (#1060): the previous default slot projected the
    // SSR'd light DOM under the rendered shadow chrome and painted the
    // rail twice. The shadow-DOM render is now self-contained; light
    // DOM children supplied for SSR fallback have no slot to project
    // into and are visually hidden after upgrade.
    const el = await fixture(html`
      <wavy-search-rail>
        <div data-stub-card="1">card</div>
      </wavy-search-rail>
    `);
    await el.updateComplete;
    const defaultSlot = Array.from(
      el.renderRoot.querySelectorAll("slot")
    ).find((s) => !s.hasAttribute("name"));
    expect(defaultSlot, "no default <slot> in shadow DOM").to.not.exist;
    const stub = el.querySelector('[data-stub-card="1"]');
    expect(stub, "light child stays in light DOM").to.exist;
    expect(stub.assignedSlot, "light child must not project anywhere")
      .to.equal(null);
  });

  // F-4 (#1039 / R-4.7) — filter chip strip.
  describe("filter chip strip (F-4 / R-4.7)", () => {
    it("renders three filter chips inside <details data-j2cl-filter-strip>", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      expect(strip, "filter strip must mount").to.exist;
      const chips = Array.from(strip.querySelectorAll("button.filter-chip"));
      expect(chips.map((c) => c.dataset.filterId)).to.deep.equal([
        "unread",
        "attachments",
        "from-me"
      ]);
    });

    it("clicking a chip composes the token into the query and emits submit", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="unread"]');
      setTimeout(() => chip.click(), 0);
      const submit = await oneEvent(el, "wavy-search-submit");
      expect(submit.detail.query).to.equal("in:inbox is:unread");
      await el.updateComplete;
      expect(chip.getAttribute("aria-pressed")).to.equal("true");
    });

    it("toggling the chip off removes the token (case-insensitive)", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="IS:UNREAD foo"></wavy-search-rail>`
      );
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="unread"]');
      expect(chip.getAttribute("aria-pressed")).to.equal("true");
      setTimeout(() => chip.click(), 0);
      const submit = await oneEvent(el, "wavy-search-submit");
      // Removal must drop ALL case-insensitive matches and keep user tokens
      expect(submit.detail.query).to.equal("foo");
    });

    it("emits wavy-search-filter-toggled with active flag and filterId", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="attachments"]');
      setTimeout(() => chip.click(), 0);
      const evt = await oneEvent(el, "wavy-search-filter-toggled");
      expect(evt.detail.filterId).to.equal("attachments");
      expect(evt.detail.token).to.equal("has:attachment");
      expect(evt.detail.active).to.equal(true);
    });

    it("preserves user-typed tokens when adding a filter", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="from:bob in:inbox"></wavy-search-rail>`
      );
      await el.updateComplete;
      const strip = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      const chip = strip.querySelector('[data-filter-id="unread"]');
      setTimeout(() => chip.click(), 0);
      const submit = await oneEvent(el, "wavy-search-submit");
      expect(submit.detail.query).to.equal("from:bob in:inbox is:unread");
    });

    it("does not match substring tokens (is:unread does not collide with is:unread-foo)", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="is:unread-foo bar"></wavy-search-rail>`
      );
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="unread"]');
      // Token equality is exact, so the chip should NOT be active here.
      expect(chip.getAttribute("aria-pressed")).to.equal("false");
    });
  });

  // J-UI-2 (#1080 / R-4.5): folder click must carry the user-visible
  // label so the J2CL controller can announce navigation via aria-live
  // without re-deriving the label from the folderId.
  describe("J-UI-2 navigation announce + focus (#1080)", () => {
    it("wavy-saved-search-selected detail carries folder label", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const archive = el.renderRoot.querySelector(
        '[data-folder-id="archive"]'
      );
      setTimeout(() => archive.click(), 0);
      const evt = await oneEvent(el, "wavy-saved-search-selected");
      expect(evt.detail.folderId).to.equal("archive");
      expect(evt.detail.label).to.equal("Archive");
      expect(evt.detail.query).to.equal("in:archive");
    });

    it("wavy-search-filter-toggled detail carries filter label", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="from-me"]');
      setTimeout(() => chip.click(), 0);
      const evt = await oneEvent(el, "wavy-search-filter-toggled");
      expect(evt.detail.filterId).to.equal("from-me");
      expect(evt.detail.label).to.equal("From me");
      expect(evt.detail.active).to.equal(true);
    });

    it("focusActiveFolder() moves focus to the aria-current=page button", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      el.query = "in:archive";
      await el.updateComplete;
      el.focusActiveFolder();
      const archive = el.renderRoot.querySelector(
        '[data-folder-id="archive"]'
      );
      expect(el.renderRoot.activeElement || el.shadowRoot.activeElement).to.equal(
        archive
      );
    });

    it("focusActiveFolder() is a no-op when no folder is active", async () => {
      const el = await fixture(
        html`<wavy-search-rail query="title:meeting"></wavy-search-rail>`
      );
      await el.updateComplete;
      // No throw, no focus change.
      expect(() => el.focusActiveFolder()).to.not.throw();
    });

    it("each of the six folders emits its canonical query+label pair", async () => {
      const expected = [
        { id: "inbox", label: "Inbox", query: "in:inbox" },
        { id: "mentions", label: "Mentions", query: "mentions:me" },
        { id: "tasks", label: "Tasks", query: "tasks:me" },
        { id: "public", label: "Public", query: "with:@" },
        { id: "archive", label: "Archive", query: "in:archive" },
        { id: "pinned", label: "Pinned", query: "in:pinned" }
      ];
      for (const folder of expected) {
        const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
        await el.updateComplete;
        const button = el.renderRoot.querySelector(
          `[data-folder-id="${folder.id}"]`
        );
        setTimeout(() => button.click(), 0);
        const evt = await oneEvent(el, "wavy-saved-search-selected");
        expect(evt.detail).to.deep.equal({
          folderId: folder.id,
          label: folder.label,
          query: folder.query
        });
      }
    });

    it("each of the three chips composes with in:inbox and emits label", async () => {
      const expected = [
        { id: "unread", label: "Unread only", token: "is:unread" },
        { id: "attachments", label: "With attachments", token: "has:attachment" },
        { id: "from-me", label: "From me", token: "from:me" }
      ];
      for (const chip of expected) {
        const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
        await el.updateComplete;
        const button = el.renderRoot.querySelector(
          `[data-filter-id="${chip.id}"]`
        );
        setTimeout(() => button.click(), 0);
        const evt = await oneEvent(el, "wavy-search-filter-toggled");
        expect(evt.detail.filterId).to.equal(chip.id);
        expect(evt.detail.label).to.equal(chip.label);
        expect(evt.detail.token).to.equal(chip.token);
        expect(evt.detail.query).to.equal("in:inbox " + chip.token);
        expect(evt.detail.active).to.equal(true);
      }
    });

    it("chip toggle still emits BOTH wavy-search-filter-toggled and wavy-search-submit", async () => {
      // Defends against a refactor that splits the two events apart.
      // The Java view dedupes via chipDrivenSubmitPending; if either
      // event stops firing, the dedupe contract breaks silently.
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="unread"]');
      const seen = [];
      el.addEventListener("wavy-search-filter-toggled", (e) =>
        seen.push("toggled:" + e.detail.query)
      );
      el.addEventListener("wavy-search-submit", (e) =>
        seen.push("submit:" + e.detail.query)
      );
      chip.click();
      expect(seen).to.deep.equal([
        "toggled:in:inbox is:unread",
        "submit:in:inbox is:unread"
      ]);
    });

    it("filter-toggled is dispatched BEFORE wavy-search-submit (dedup order contract)", async () => {
      // J-UI-2 (#1080): the J2CL view's chipDrivenSubmitPending dedup
      // assumes filter-toggled lands first, sets the flag, and the
      // following submit listener sees and consumes the flag. If a
      // future refactor reverses this order the J2CL submit handler
      // would issue a duplicate backend search.
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const chip = el.renderRoot.querySelector('[data-filter-id="unread"]');
      const order = [];
      el.addEventListener("wavy-search-submit", () => order.push("submit"));
      el.addEventListener("wavy-search-filter-toggled", () =>
        order.push("toggled")
      );
      chip.click();
      // Re-assert order independent of registration order — the rail's
      // _toggleFilter must dispatch toggled first so the J2CL flag can
      // pre-arm before submit checks it.
      expect(order).to.deep.equal(["toggled", "submit"]);
    });
  });

  // G-PORT-2 (#1111): panel-level action row clones the GWT
  // SearchPresenter toolbar — refresh + sort + filter buttons in a
  // single visible row, each tagged with `data-digest-action="..."`
  // so the parity test resolves them on both views via one selector.
  describe("G-PORT-2 panel-level action row (#1111)", () => {
    it("renders an action row with refresh + sort + filter buttons", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const row = el.renderRoot.querySelector("[data-digest-action-row]");
      expect(row, "action row must mount").to.exist;
      const buttons = Array.from(row.querySelectorAll("button[data-digest-action]"));
      expect(buttons.map((b) => b.dataset.digestAction)).to.deep.equal([
        "refresh",
        "sort",
        "filter"
      ]);
    });

    it("each action button carries an aria-label for screen readers", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const refresh = el.renderRoot.querySelector('[data-digest-action="refresh"]');
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      const filter = el.renderRoot.querySelector('[data-digest-action="filter"]');
      expect(refresh.getAttribute("aria-label")).to.equal("Refresh search results");
      expect(sort.getAttribute("aria-label")).to.equal("Sort waves");
      expect(sort.getAttribute("aria-haspopup")).to.equal("menu");
      expect(sort.getAttribute("aria-controls")).to.equal("wavy-search-sort-menu");
      expect(filter.getAttribute("aria-label")).to.equal("Filter waves");
    });

    it("Sort button opens options and applying one submits an orderby query", async () => {
      const el = await fixture(html`<wavy-search-rail query="in:inbox orderby:datedesc"></wavy-search-rail>`);
      await el.updateComplete;
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      sort.click();
      await el.updateComplete;
      const createdOldest = el.renderRoot.querySelector('[data-sort-token="orderby:createdasc"]');
      expect(createdOldest, "created oldest sort option mounts").to.exist;
      setTimeout(() => createdOldest.click(), 0);
      const evt = await oneEvent(el, "wavy-search-submit");
      expect(evt.detail.query).to.equal("in:inbox orderby:createdasc");
      await el.updateComplete;
      await waitForMicrotasks();
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;
      expect(el.renderRoot.activeElement).to.equal(sort);
    });

    it("applying the default sort writes an explicit orderby token", async () => {
      const el = await fixture(html`<wavy-search-rail query="in:inbox orderby:dateasc"></wavy-search-rail>`);
      await el.updateComplete;
      el.renderRoot.querySelector('[data-digest-action="sort"]').click();
      await el.updateComplete;
      const newestActivity = el.renderRoot.querySelector('[data-sort-token="orderby:datedesc"]');
      expect(newestActivity.getAttribute("aria-checked")).to.equal("false");
      expect(newestActivity.hasAttribute("aria-current")).to.equal(false);
      setTimeout(() => newestActivity.click(), 0);
      const evt = await oneEvent(el, "wavy-search-submit");
      expect(evt.detail.query).to.equal("in:inbox orderby:datedesc");
    });

    it("default sort option shows aria-checked=true when query has no orderby token", async () => {
      const el = await fixture(html`<wavy-search-rail query="in:inbox"></wavy-search-rail>`);
      await el.updateComplete;
      el.renderRoot.querySelector('[data-digest-action="sort"]').click();
      await el.updateComplete;
      const newestActivity = el.renderRoot.querySelector('[data-sort-token="orderby:datedesc"]');
      const oldest = el.renderRoot.querySelector('[data-sort-token="orderby:createdasc"]');
      expect(newestActivity.getAttribute("aria-checked")).to.equal("true");
      expect(oldest.getAttribute("aria-checked")).to.equal("false");
    });

    it("choosing the implicit default sort with no orderby closes without re-submitting", async () => {
      const el = await fixture(html`<wavy-search-rail query="in:inbox"></wavy-search-rail>`);
      await el.updateComplete;
      let submitCount = 0;
      let sortRequestedCount = 0;
      el.addEventListener("wavy-search-submit", () => {
        submitCount += 1;
      });
      el.addEventListener("wavy-search-sort-requested", () => {
        sortRequestedCount += 1;
      });
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      sort.click();
      await el.updateComplete;
      const newestActivity = el.renderRoot.querySelector('[data-sort-token="orderby:datedesc"]');
      newestActivity.click();
      await waitForMicrotasks();
      await el.updateComplete;
      expect(el.query).to.equal("in:inbox");
      expect(submitCount).to.equal(0);
      expect(sortRequestedCount).to.equal(0);
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;
      expect(el.renderRoot.activeElement).to.equal(sort);
    });

    it("focuses the explicitly checked sort option when reopening the menu", async () => {
      const el = await fixture(html`<wavy-search-rail query="in:inbox orderby:createdasc"></wavy-search-rail>`);
      await el.updateComplete;
      el.renderRoot.querySelector('[data-digest-action="sort"]').click();
      await el.updateComplete;
      await waitForMicrotasks();
      const selected = el.renderRoot.querySelector('[data-sort-token="orderby:createdasc"]');
      const unchecked = el.renderRoot.querySelector('[data-sort-token="orderby:datedesc"]');
      expect(selected.getAttribute("aria-checked")).to.equal("true");
      expect(unchecked.getAttribute("aria-checked")).to.equal("false");
      expect(el.renderRoot.activeElement).to.equal(selected);
    });

    it("choosing the current sort closes the menu without re-submitting", async () => {
      const el = await fixture(html`<wavy-search-rail query="in:inbox orderby:datedesc"></wavy-search-rail>`);
      await el.updateComplete;
      let submitCount = 0;
      let sortRequestedCount = 0;
      el.addEventListener("wavy-search-submit", () => {
        submitCount += 1;
      });
      el.addEventListener("wavy-search-sort-requested", () => {
        sortRequestedCount += 1;
      });
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      sort.click();
      await el.updateComplete;
      el.renderRoot.querySelector('[data-sort-token="orderby:datedesc"]').click();
      await waitForMicrotasks();
      await el.updateComplete;
      expect(submitCount).to.equal(0);
      expect(sortRequestedCount).to.equal(0);
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;
      expect(el.renderRoot.activeElement).to.equal(sort);
    });

    it("Escape and outside click close the sort menu", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      sort.click();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.exist;
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;

      sort.click();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.exist;
      document.body.click();
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;
    });

    it("focus leaving the sort menu closes it", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const sort = el.renderRoot.querySelector('[data-digest-action="sort"]');
      sort.click();
      await el.updateComplete;
      const menu = el.renderRoot.querySelector(".sort-menu");
      const filter = el.renderRoot.querySelector('[data-digest-action="filter"]');
      menu.dispatchEvent(new FocusEvent("focusout", {
        bubbles: true,
        composed: true,
        relatedTarget: filter
      }));
      await el.updateComplete;
      expect(el.renderRoot.querySelector(".sort-menu")).to.be.null;
    });

    it("Arrow keys move through sort options", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      el.renderRoot.querySelector('[data-digest-action="sort"]').click();
      await el.updateComplete;
      const menu = el.renderRoot.querySelector(".sort-menu");
      const options = Array.from(menu.querySelectorAll(".sort-option"));
      options[0].focus();
      menu.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
      expect(el.renderRoot.activeElement).to.equal(options[1]);
      menu.dispatchEvent(new KeyboardEvent("keydown", { key: "End", bubbles: true }));
      expect(el.renderRoot.activeElement).to.equal(options[options.length - 1]);
    });

    it("Filter button click toggles the chip strip open and emits an event", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const filterBtn = el.renderRoot.querySelector('[data-digest-action="filter"]');
      const details = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      expect(details.hasAttribute("open"), "starts collapsed").to.equal(false);

      // Use a click and capture the synchronous event without race risk:
      // wavy-search-filter-toggle-requested is fired synchronously inside
      // the handler, so attach the listener first and then click.
      let toggleEvt = null;
      el.addEventListener("wavy-search-filter-toggle-requested", (e) => {
        toggleEvt = e;
      });
      filterBtn.click();
      await el.updateComplete;
      expect(toggleEvt, "filter-toggle-requested fires").to.exist;
      expect(toggleEvt.detail.open).to.equal(true);
      expect(details.hasAttribute("open"), "details opens").to.equal(true);
      expect(filterBtn.getAttribute("aria-pressed")).to.equal("true");
      expect(filterBtn.getAttribute("aria-expanded")).to.equal("true");

      // Second click closes.
      filterBtn.click();
      await el.updateComplete;
      expect(details.hasAttribute("open")).to.equal(false);
      expect(filterBtn.getAttribute("aria-pressed")).to.equal("false");
    });

    it("filter button aria-controls points at the filter strip id", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const filterBtn = el.renderRoot.querySelector('[data-digest-action="filter"]');
      const details = el.renderRoot.querySelector("details[data-j2cl-filter-strip]");
      expect(filterBtn.getAttribute("aria-controls")).to.equal(details.id);
    });
  });

  // J-UI-1 (#1079): the rail must expose a `cards` slot so the J2CL
  // search panel can project <wavy-search-rail-card> children inside the
  // shadow DOM. Without the slot the children would be hidden post-upgrade.
  describe("J-UI-1 cards slot (#1079)", () => {
    it("declares a <slot name=\"cards\"> in the shadow DOM", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const slot = el.renderRoot.querySelector('slot[name="cards"]');
      expect(slot, "rail must expose a cards slot for digest projection").to.exist;
    });

    it("accepts <wavy-search-rail-card> light-DOM children projected into the cards slot", async () => {
      const el = await fixture(html`
        <wavy-search-rail>
          <wavy-search-rail-card slot="cards" data-wave-id="w+a"></wavy-search-rail-card>
          <wavy-search-rail-card slot="cards" data-wave-id="w+b"></wavy-search-rail-card>
        </wavy-search-rail>
      `);
      await el.updateComplete;
      const slot = el.renderRoot.querySelector('slot[name="cards"]');
      const assigned = slot.assignedElements();
      expect(assigned.map((n) => n.dataset.waveId)).to.deep.equal(["w+a", "w+b"]);
    });

    it("preserves the saved-search list above the cards slot and filter strip below it", async () => {
      const el = await fixture(html`<wavy-search-rail></wavy-search-rail>`);
      await el.updateComplete;
      const folders = el.renderRoot.querySelector("ul.folders");
      const slot = el.renderRoot.querySelector('slot[name="cards"]');
      const filters = el.renderRoot.querySelector("details.filters");
      expect(folders, "saved-search list mounts").to.exist;
      expect(slot, "cards slot mounts").to.exist;
      expect(filters, "filter strip mounts").to.exist;
      // Document order: folders, then cards slot, then filter strip.
      expect(folders.compareDocumentPosition(slot) & Node.DOCUMENT_POSITION_FOLLOWING).to.be.greaterThan(0);
      expect(slot.compareDocumentPosition(filters) & Node.DOCUMENT_POSITION_FOLLOWING).to.be.greaterThan(0);
    });
  });
});

// Helper: read the element's static stylesheet text so we can assert
// the wavy token names actually appear (defends against silent renames).
function WavySearchRail_styleText() {
  const cls = customElements.get("wavy-search-rail");
  const styles = cls.styles;
  const arr = Array.isArray(styles) ? styles : [styles];
  return arr.map((s) => (s && s.cssText) || "").join("\n");
}
