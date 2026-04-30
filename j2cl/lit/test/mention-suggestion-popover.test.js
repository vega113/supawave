// G-PORT-5 (#1114) — mention popover is view-only.
//
// Per the G-PORT-5 plan §4: the composer body is the SOLE owner of
// keyboard navigation while the popover is open. The popover never
// listens for keystrokes and never steals focus from the composer body.
// These tests reflect the view-only contract: render the listbox,
// reflect host-supplied activeIndex as the visual highlight, route
// click activation back to the host, and never trap or hijack
// keystrokes / focus.
import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/mention-suggestion-popover.js";

describe("<mention-suggestion-popover>", () => {
  const candidates = [
    { address: "alice@example.com", displayName: "Alice Adams" },
    { address: "bob@example.com", displayName: "Bob Brown" }
  ];

  it("renders listbox options and announces candidate count", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);

    const listbox = el.renderRoot.querySelector("[role='listbox']");
    const options = el.renderRoot.querySelectorAll("[role='option']");
    expect(listbox.getAttribute("tabindex")).to.equal("-1");
    expect(listbox.getAttribute("aria-activedescendant")).to.equal(options[0].id);
    expect(options[0].getAttribute("aria-selected")).to.equal("true");
    expect(options[0].textContent.trim()).to.equal("@alice@example.com");
    expect(options[1].dataset.address).to.equal("bob@example.com");
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.include(
      "2 mention suggestions"
    );
  });

  it("does NOT steal focus into the listbox when opened", async () => {
    // G-PORT-5: this is the regression that issue #1125 documented.
    // If the popover focuses its listbox on open, document.activeElement
    // moves away from the composer body and the composer dismisses on
    // the next selectionchange. The view-only popover must leave focus
    // wherever it was before.
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    await el.updateComplete;
    expect(el.shadowRoot.activeElement).to.equal(null);
  });

  it("does NOT consume keydown events itself", async () => {
    // G-PORT-5: every key the user presses while the popover is open
    // must reach the composer body's keydown listener. The popover
    // must not preventDefault or otherwise eat the event.
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    for (const key of ["ArrowDown", "ArrowUp", "Enter", "Tab", "Escape"]) {
      const event = new KeyboardEvent("keydown", {
        key,
        bubbles: true,
        cancelable: true
      });
      el.dispatchEvent(event);
      expect(event.defaultPrevented, `${key} must NOT be prevented`).to.equal(false);
    }
  });

  it("reflects host-supplied activeIndex as the visual highlight", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${candidates}
        .activeIndex=${0}
        open
      ></mention-suggestion-popover>
    `);
    let options = el.renderRoot.querySelectorAll("[role='option']");
    expect(options[0].getAttribute("aria-selected")).to.equal("true");
    expect(options[1].getAttribute("aria-selected")).to.equal("false");

    el.activeIndex = 1;
    await el.updateComplete;
    options = el.renderRoot.querySelectorAll("[role='option']");
    expect(options[0].getAttribute("aria-selected")).to.equal("false");
    expect(options[1].getAttribute("aria-selected")).to.equal("true");
    expect(el.renderRoot.querySelector("[role='listbox']").getAttribute("aria-activedescendant")).to.equal(
      options[1].id
    );
  });

  it("G-PORT-9: pins option height to the GWT active-row height", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const option = el.renderRoot.querySelector("[role='option']");
    expect(getComputedStyle(option).height).to.equal("28px");
    expect(getComputedStyle(option).maxHeight).to.equal("28px");
    expect(getComputedStyle(option).overflow).to.equal("hidden");
  });

  it("emits mention-select on click", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "mention-select");

    el.renderRoot.querySelectorAll("[role='option']")[1].click();

    expect((await eventPromise).detail).to.deep.equal({
      address: "bob@example.com",
      displayName: "Bob Brown"
    });
  });

  it("preventDefaults mousedown on options to avoid focus theft", async () => {
    // G-PORT-5: a focusable option element would steal focus from
    // the composer body on mousedown and trip the composer's blur-
    // dismiss path before the click landed. preventDefault keeps
    // document.activeElement on the composer body.
    const el = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const option = el.renderRoot.querySelectorAll("[role='option']")[0];
    const event = new MouseEvent("mousedown", { bubbles: true, cancelable: true });
    option.dispatchEvent(event);
    expect(event.defaultPrevented).to.equal(true);
  });

  it("renders the empty-state placeholder when no candidates match", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${[]}
        focus-target-id="composer-caret"
        open
      ></mention-suggestion-popover>
    `);

    expect(el.renderRoot.textContent).to.include("No mention matches");
    expect(el.renderRoot.querySelector("[role='listbox']").hasAttribute("aria-activedescendant")).to.equal(
      false
    );
    expect(el.renderRoot.querySelector("[aria-live='polite']").textContent).to.include(
      "No mention suggestions"
    );
  });

  it("emits overlay-close when the host calls close()", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${candidates}
        focus-target-id="composer-caret"
        open
      ></mention-suggestion-popover>
    `);
    const eventPromise = oneEvent(el, "overlay-close");

    el.close("escape");

    expect((await eventPromise).detail).to.deep.equal({
      reason: "escape",
      focusTargetId: "composer-caret"
    });
    expect(el.open).to.equal(false);
  });

  it("keeps option ids unique across popover instances", async () => {
    const first = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);
    const second = await fixture(html`
      <mention-suggestion-popover .candidates=${candidates} open></mention-suggestion-popover>
    `);

    expect(first.renderRoot.querySelector("[role='option']").id).to.not.equal(
      second.renderRoot.querySelector("[role='option']").id
    );
  });

  it("clamps active descendant when candidates shrink", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${candidates}
        active-index="5"
        open
      ></mention-suggestion-popover>
    `);

    el.candidates = [{ address: "solo@example.com", displayName: "Solo" }];
    await el.updateComplete;
    expect(el.renderRoot.querySelector("[role='listbox']").getAttribute("aria-activedescendant")).to.equal(
      el.renderRoot.querySelector("[role='option']").id
    );
  });

  it("wraps active selection for negative active indexes below the candidate count", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${candidates}
        active-index="-3"
        open
      ></mention-suggestion-popover>
    `);
    const options = el.renderRoot.querySelectorAll("[role='option']");

    expect(el.renderRoot.querySelector("[role='listbox']").getAttribute("aria-activedescendant")).to.equal(
      options[1].id
    );
  });

  it("falls back to the first option for non-finite active indexes", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${candidates}
        .activeIndex=${Number.NaN}
        open
      ></mention-suggestion-popover>
    `);
    const options = el.renderRoot.querySelectorAll("[role='option']");

    expect(el.renderRoot.querySelector("[role='listbox']").getAttribute("aria-activedescendant")).to.equal(
      options[0].id
    );
  });

  it("filters malformed candidate entries before rendering", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${[null, "bad", { address: "valid@example.com" }]}
        open
      ></mention-suggestion-popover>
    `);

    const options = el.renderRoot.querySelectorAll("[role='option']");
    expect(options.length).to.equal(1);
    expect(options[0].dataset.address).to.equal("valid@example.com");
  });

  it("filters candidate entries without usable addresses before rendering", async () => {
    const el = await fixture(html`
      <mention-suggestion-popover
        .candidates=${[
          { displayName: "Missing" },
          { address: "   ", displayName: "Blank" },
          { address: " valid@example.com ", displayName: "Valid" }
        ]}
        open
      ></mention-suggestion-popover>
    `);

    const options = el.renderRoot.querySelectorAll("[role='option']");
    expect(options.length).to.equal(1);
    expect(options[0].dataset.address).to.equal("valid@example.com");
  });
});
