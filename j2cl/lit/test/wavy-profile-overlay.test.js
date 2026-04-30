import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-profile-overlay.js";

const PARTICIPANTS = [
  { id: "alice@example.com", displayName: "Alice" },
  { id: "bob@example.com", displayName: "Bob" },
  { id: "carol@example.com", displayName: "Carol" }
];

describe("<wavy-profile-overlay>", () => {
  it("defines the wavy-profile-overlay custom element", () => {
    expect(customElements.get("wavy-profile-overlay")).to.exist;
  });

  it("default state: open=false, host hidden + aria-hidden=true", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    expect(el.open).to.equal(false);
    expect(el.hasAttribute("hidden")).to.equal(true);
    expect(el.getAttribute("aria-hidden")).to.equal("true");
  });

  it("receiving wave-blip-profile-requested on document opens the overlay", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    await el.updateComplete;
    document.dispatchEvent(
      new CustomEvent("wave-blip-profile-requested", {
        bubbles: true,
        composed: true,
        detail: { blipId: "b1", authorId: "bob@example.com" }
      })
    );
    await el.updateComplete;
    expect(el.open).to.equal(true);
    expect(el.hasAttribute("hidden")).to.equal(false);
    expect(el.getAttribute("role")).to.equal("dialog");
    expect(el.getAttribute("aria-modal")).to.equal("true");
  });

  it("opening with a matching authorId sets index to that participant", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    await el.updateComplete;
    document.dispatchEvent(
      new CustomEvent("wave-blip-profile-requested", {
        bubbles: true,
        composed: true,
        detail: { authorId: "carol@example.com" }
      })
    );
    await el.updateComplete;
    expect(el.index).to.equal(2);
  });

  it("opening with an unknown authorId falls back to index 0", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 1;
    await el.updateComplete;
    document.dispatchEvent(
      new CustomEvent("wave-blip-profile-requested", {
        bubbles: true,
        composed: true,
        detail: { authorId: "ghost@example.com" }
      })
    );
    await el.updateComplete;
    expect(el.index).to.equal(0);
  });

  it("opening fires wavy-profile-overlay-opened with the trigger authorId", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    await el.updateComplete;
    setTimeout(() =>
      document.dispatchEvent(
        new CustomEvent("wave-blip-profile-requested", {
          bubbles: true,
          composed: true,
          detail: { authorId: "alice@example.com" }
        })
      )
    );
    const ev = await oneEvent(el, "wavy-profile-overlay-opened");
    expect(ev.detail).to.deep.equal({ authorId: "alice@example.com" });
  });

  it("Prev button disabled at index 0; enabled otherwise", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 0;
    await el.updateComplete;
    let prev = el.renderRoot.querySelector("button.nav.prev");
    expect(prev.hasAttribute("disabled")).to.equal(true);
    el.index = 1;
    await el.updateComplete;
    prev = el.renderRoot.querySelector("button.nav.prev");
    expect(prev.hasAttribute("disabled")).to.equal(false);
  });

  it("Next button disabled at index participants.length-1; enabled otherwise", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 2;
    await el.updateComplete;
    let next = el.renderRoot.querySelector("button.nav.next");
    expect(next.hasAttribute("disabled")).to.equal(true);
    el.index = 1;
    await el.updateComplete;
    next = el.renderRoot.querySelector("button.nav.next");
    expect(next.hasAttribute("disabled")).to.equal(false);
  });

  it("Click Next: index += 1, emits wavy-profile-participant-changed", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 0;
    await el.updateComplete;
    const next = el.renderRoot.querySelector("button.nav.next");
    setTimeout(() => next.click());
    const ev = await oneEvent(el, "wavy-profile-participant-changed");
    expect(ev.detail.index).to.equal(1);
    expect(ev.detail.participant).to.deep.equal(PARTICIPANTS[1]);
    expect(el.index).to.equal(1);
  });

  it("Click Prev: index -= 1, emits wavy-profile-participant-changed", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 2;
    await el.updateComplete;
    const prev = el.renderRoot.querySelector("button.nav.prev");
    setTimeout(() => prev.click());
    const ev = await oneEvent(el, "wavy-profile-participant-changed");
    expect(ev.detail.index).to.equal(1);
    expect(ev.detail.participant).to.deep.equal(PARTICIPANTS[1]);
    expect(el.index).to.equal(1);
  });

  it("ArrowRight key on host → Next when not at end", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 0;
    await el.updateComplete;
    setTimeout(() =>
      el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight" }))
    );
    const ev = await oneEvent(el, "wavy-profile-participant-changed");
    expect(ev.detail.index).to.equal(1);
  });

  it("ArrowLeft key on host → Prev when not at start", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    el.index = 2;
    await el.updateComplete;
    setTimeout(() =>
      el.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft" }))
    );
    const ev = await oneEvent(el, "wavy-profile-participant-changed");
    expect(ev.detail.index).to.equal(1);
  });

  it("Close × button → open=false, emits wavy-profile-overlay-closed", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    await el.updateComplete;
    const exit = el.renderRoot.querySelector("button.exit");
    setTimeout(() => exit.click());
    const ev = await oneEvent(el, "wavy-profile-overlay-closed");
    expect(ev).to.exist;
    await el.updateComplete;
    expect(el.open).to.equal(false);
    expect(el.hasAttribute("hidden")).to.equal(true);
  });

  it("Escape key while open → close + emits wavy-profile-overlay-closed", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    await el.updateComplete;
    setTimeout(() =>
      el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }))
    );
    const ev = await oneEvent(el, "wavy-profile-overlay-closed");
    expect(ev).to.exist;
    expect(el.open).to.equal(false);
  });

  it("renders the named slot 'actions' so future slices can fill L.2 / L.3", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    await el.updateComplete;
    const slot = el.renderRoot.querySelector("slot[name='actions']");
    expect(slot).to.exist;
  });

  it("shows Send Message for non-self profile and emits requested participant", async () => {
    const el = await fixture(
      html`<wavy-profile-overlay open current-user-id="alice@example.com"></wavy-profile-overlay>`
    );
    el.participants = PARTICIPANTS;
    el.index = 1;
    await el.updateComplete;

    const send = el.renderRoot.querySelector("[data-profile-send-message]");
    const edit = el.renderRoot.querySelector("[data-profile-edit]");
    expect(send).to.exist;
    expect(send.hasAttribute("hidden")).to.equal(false);
    expect(edit).to.exist;
    expect(edit.hasAttribute("hidden")).to.equal(true);

    setTimeout(() => send.click());
    const ev = await oneEvent(el, "wave-new-with-participants-requested");
    expect(ev.detail).to.deep.equal({
      participants: ["bob@example.com"],
      source: "profile-overlay"
    });
    expect(el.open).to.equal(false);
  });

  it("shows Edit Profile for current user and emits a cancelable edit event", async () => {
    const el = await fixture(
      html`<wavy-profile-overlay open current-user-id="alice@example.com"></wavy-profile-overlay>`
    );
    el.participants = PARTICIPANTS;
    await el.updateComplete;

    const send = el.renderRoot.querySelector("[data-profile-send-message]");
    const edit = el.renderRoot.querySelector("[data-profile-edit]");
    expect(send).to.exist;
    expect(send.hasAttribute("hidden")).to.equal(true);
    expect(edit).to.exist;
    expect(edit.hasAttribute("hidden")).to.equal(false);

    let observed = null;
    el.addEventListener("wavy-profile-edit-requested", (event) => {
      observed = event;
      event.preventDefault();
    });
    edit.click();

    expect(observed).to.exist;
    expect(observed.cancelable).to.equal(true);
    expect(observed.detail.url).to.equal("/userprofile/edit");
    expect(observed.detail.participant).to.deep.equal(PARTICIPANTS[0]);
  });

  it("participant isSelf gates Edit Profile without currentUserId", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = [
      { id: "viewer@example.com", displayName: "Viewer", isSelf: true },
      { id: "bob@example.com", displayName: "Bob" }
    ];
    await el.updateComplete;

    const send = el.renderRoot.querySelector("[data-profile-send-message]");
    const edit = el.renderRoot.querySelector("[data-profile-edit]");
    expect(send.hasAttribute("hidden")).to.equal(true);
    expect(edit.hasAttribute("hidden")).to.equal(false);
  });

  it("avatar event with empty participants list still opens; nav buttons disabled both ends", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    el.participants = [];
    await el.updateComplete;
    document.dispatchEvent(
      new CustomEvent("wave-blip-profile-requested", {
        bubbles: true,
        composed: true,
        detail: { authorId: "ghost@example.com" }
      })
    );
    await el.updateComplete;
    expect(el.open).to.equal(true);
    expect(el.index).to.equal(0);
    const prev = el.renderRoot.querySelector("button.nav.prev");
    const next = el.renderRoot.querySelector("button.nav.next");
    expect(prev.hasAttribute("disabled")).to.equal(true);
    expect(next.hasAttribute("disabled")).to.equal(true);
    const name = el.renderRoot.querySelector(".name");
    expect(name.textContent.trim()).to.equal("Unknown participant");
  });

  it("renders <img> avatar when participant has avatarUrl, otherwise initials", async () => {
    const el = await fixture(html`<wavy-profile-overlay open></wavy-profile-overlay>`);
    el.participants = [
      { id: "p@example.com", displayName: "Pat Quinn", avatarUrl: "/u/pat.png" },
      { id: "q@example.com", displayName: "Quincy" }
    ];
    el.index = 0;
    await el.updateComplete;
    const img = el.renderRoot.querySelector(".avatar img");
    expect(img).to.exist;
    expect(img.getAttribute("src")).to.equal("/u/pat.png");
    el.index = 1;
    await el.updateComplete;
    expect(el.renderRoot.querySelector(".avatar img")).to.equal(null);
    const initials = el.renderRoot.querySelector(".avatar").textContent.trim();
    expect(initials).to.equal("Q");
  });

  it("real keyboard path: host receives Escape after focus moves on open", async () => {
    const el = await fixture(html`<wavy-profile-overlay></wavy-profile-overlay>`);
    el.participants = PARTICIPANTS;
    await el.updateComplete;
    document.dispatchEvent(
      new CustomEvent("wave-blip-profile-requested", {
        bubbles: true,
        composed: true,
        detail: { authorId: "alice@example.com" }
      })
    );
    await el.updateComplete;
    // After open the host must be focusable so keydown fires from real keyboard.
    expect(el.getAttribute("tabindex")).to.equal("-1");
    // Wait one microtask for the auto-focus.
    await Promise.resolve();
    let closed = false;
    el.addEventListener("wavy-profile-overlay-closed", () => { closed = true; });
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await el.updateComplete;
    expect(closed).to.equal(true);
    expect(el.open).to.equal(false);
  });
});
