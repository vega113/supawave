// G-PORT-7 (#1116): integration test for the shell-root keydown
// dispatcher. Mounts a <shell-root> alongside three <wave-blip> hosts
// and a closeable <wavy-confirm-dialog> mock so we exercise the end-
// to-end window keydown -> dispatcher flow.
import { fixture, expect, html } from "@open-wc/testing";
import "../../src/elements/shell-root.js";
import "../../src/elements/wave-blip.js";
import { isMacPlatform } from "../../src/shortcuts/keybindings.js";

// Stand-in confirm dialog so we don't pull in the F-3.S4 one + its
// styling deps; the dispatcher only cares about open/close.
class MockConfirm extends HTMLElement {
  constructor() {
    super();
    this._open = false;
  }
  get open() { return this._open; }
  set open(v) {
    this._open = !!v;
    if (v) this.setAttribute("open", ""); else this.removeAttribute("open");
  }
  close() { this.open = false; }
}
if (!customElements.get("wavy-confirm-dialog")) {
  customElements.define("wavy-confirm-dialog", MockConfirm);
}

function fireKey(key, mods = {}) {
  const evt = new KeyboardEvent("keydown", {
    key,
    code:
      key === "j"
        ? "KeyJ"
        : key === "k"
        ? "KeyK"
        : key === "o" || key === "O"
        ? "KeyO"
        : key,
    bubbles: true,
    cancelable: true,
    composed: true,
    shiftKey: !!mods.shiftKey,
    ctrlKey: !!mods.ctrlKey,
    metaKey: !!mods.metaKey,
    altKey: !!mods.altKey
  });
  window.dispatchEvent(evt);
  return evt;
}

describe("<shell-root> shell-level keydown dispatcher", () => {
  let host;
  let confirm;
  let blips;

  beforeEach(async () => {
    host = await fixture(html`
      <div>
        <shell-root></shell-root>
        <wavy-confirm-dialog></wavy-confirm-dialog>
        <wave-blip data-blip-id="b1" data-wave-id="w" author-name="A"></wave-blip>
        <wave-blip data-blip-id="b2" data-wave-id="w" author-name="B"></wave-blip>
        <wave-blip data-blip-id="b3" data-wave-id="w" author-name="C"></wave-blip>
      </div>
    `);
    confirm = host.querySelector("wavy-confirm-dialog");
    blips = Array.from(host.querySelectorAll("wave-blip"));
  });

  afterEach(() => {
    // Force shell-root to remove its window listener.
    const shell = host.querySelector("shell-root");
    if (shell && shell.parentNode) shell.parentNode.removeChild(shell);
  });

  it("j focuses the first blip, second j moves to the second blip", () => {
    fireKey("j");
    expect(blips[0].hasAttribute("focused")).to.equal(true);
    fireKey("j");
    expect(blips[0].hasAttribute("focused")).to.equal(false);
    expect(blips[1].hasAttribute("focused")).to.equal(true);
  });

  it("k goes back", () => {
    fireKey("j"); // b1
    fireKey("j"); // b2
    fireKey("k"); // b1
    expect(blips[0].hasAttribute("focused")).to.equal(true);
  });

  it("Shift+Cmd+O dispatches wavy-new-wave-requested on document.body", () => {
    let sawCount = 0;
    let source = "";
    const handler = (event) => {
      sawCount += 1;
      source = event.detail && event.detail.source;
    };
    document.body.addEventListener("wavy-new-wave-requested", handler);
    try {
      fireKey("o", { shiftKey: true, metaKey: true });
      // The matcher only fires this on Mac when metaKey is set. Some
      // headless test browsers report a non-Mac platform; in that case
      // re-fire with ctrlKey while the listener is still attached so
      // this test can observe the event on either platform.
      if (sawCount === 0) {
        fireKey("o", { shiftKey: true, ctrlKey: true });
      }
    } finally {
      document.body.removeEventListener("wavy-new-wave-requested", handler);
    }
    expect(sawCount).to.equal(1);
    expect(source).to.equal("keyboard-shortcut");
  });

  it("Shift+Cmd/Ctrl+O inside editable targets suppresses browser default without opening New Wave", () => {
    let sawCount = 0;
    const handler = () => { sawCount += 1; };
    const input = document.createElement("input");
    const composerBody = document.createElement("div");
    composerBody.contentEditable = "true";
    const events = [];
    document.body.appendChild(input);
    document.body.appendChild(composerBody);
    document.body.addEventListener("wavy-new-wave-requested", handler);
    const fireFrom = (target, mods) => {
      target.focus();
      const evt = new KeyboardEvent("keydown", {
        key: "o",
        code: "KeyO",
        bubbles: true,
        cancelable: true,
        composed: true,
        shiftKey: true,
        metaKey: !!mods.metaKey,
        ctrlKey: !!mods.ctrlKey
      });
      target.dispatchEvent(evt);
      events.push(evt);
    };
    const primaryMod = isMacPlatform() ? { metaKey: true } : { ctrlKey: true };
    try {
      fireFrom(input, primaryMod);
      fireFrom(composerBody, primaryMod);
    } finally {
      document.body.removeEventListener("wavy-new-wave-requested", handler);
      input.remove();
      composerBody.remove();
    }
    expect(sawCount).to.equal(0);
    expect(events.every((evt) => evt.defaultPrevented)).to.equal(true);
  });

  it("Esc closes an open dialog before dropping blip focus", () => {
    fireKey("j"); // focus b1
    confirm.open = true;
    fireKey("Escape");
    expect(confirm.open).to.equal(false);
    // First Esc closed dialog only — blip stays focused.
    expect(blips[0].hasAttribute("focused")).to.equal(true);
    // Second Esc drops blip focus.
    fireKey("Escape");
    expect(blips[0].hasAttribute("focused")).to.equal(false);
  });

  it("j is ignored while a modal dialog is open", () => {
    fireKey("j"); // b1
    confirm.open = true;
    fireKey("j");
    // No advance — b1 stays focused, dialog stays open.
    expect(blips[0].hasAttribute("focused")).to.equal(true);
    expect(confirm.open).to.equal(true);
  });

  it("j inside an <input> falls through to the input", () => {
    const input = document.createElement("input");
    document.body.appendChild(input);
    try {
      input.focus();
      const evt = new KeyboardEvent("keydown", {
        key: "j",
        code: "KeyJ",
        bubbles: true,
        cancelable: true,
        composed: true
      });
      input.dispatchEvent(evt);
      // No blip should have been focused.
      expect(blips.every((b) => !b.hasAttribute("focused"))).to.equal(true);
      // Default not prevented, so the input would have received the j.
      expect(evt.defaultPrevented).to.equal(false);
    } finally {
      input.remove();
    }
  });

  it("Esc fires even from inside an input (global)", () => {
    const input = document.createElement("input");
    document.body.appendChild(input);
    try {
      input.focus();
      confirm.open = true;
      const evt = new KeyboardEvent("keydown", {
        key: "Escape",
        bubbles: true,
        cancelable: true,
        composed: true
      });
      input.dispatchEvent(evt);
      expect(confirm.open).to.equal(false);
    } finally {
      input.remove();
    }
  });
});
