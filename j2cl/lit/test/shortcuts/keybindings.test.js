// G-PORT-7 (#1116): unit tests for the pure keybindings matcher.
import { expect } from "@open-wc/testing";
import {
  KEY_ACTION,
  isEditableTarget,
  isMacPlatform,
  matchShortcut
} from "../../src/shortcuts/keybindings.js";

function fakeEvent(key, mods = {}, target = null) {
  return {
    key,
    shiftKey: !!mods.shiftKey,
    ctrlKey: !!mods.ctrlKey,
    metaKey: !!mods.metaKey,
    altKey: !!mods.altKey,
    repeat: !!mods.repeat,
    target,
    composedPath: () => (target ? [target] : [])
  };
}

describe("matchShortcut", () => {
  it("returns null for unrelated keys", () => {
    expect(matchShortcut(fakeEvent("a"), { isMac: true })).to.equal(null);
    expect(matchShortcut(fakeEvent("Enter"), { isMac: true })).to.equal(null);
  });

  it("matches j -> BLIP_FOCUS_NEXT (not global)", () => {
    const m = matchShortcut(fakeEvent("j"), { isMac: true });
    expect(m).to.deep.equal({
      action: KEY_ACTION.BLIP_FOCUS_NEXT,
      global: false
    });
  });

  it("matches k -> BLIP_FOCUS_PREV (not global)", () => {
    const m = matchShortcut(fakeEvent("k"), { isMac: false });
    expect(m).to.deep.equal({
      action: KEY_ACTION.BLIP_FOCUS_PREV,
      global: false
    });
  });

  it("matches uppercase J / K", () => {
    expect(matchShortcut(fakeEvent("J"), { isMac: true }).action).to.equal(
      KEY_ACTION.BLIP_FOCUS_NEXT
    );
    expect(matchShortcut(fakeEvent("K"), { isMac: true }).action).to.equal(
      KEY_ACTION.BLIP_FOCUS_PREV
    );
  });

  it("rejects j / k with any modifier", () => {
    expect(matchShortcut(fakeEvent("j", { shiftKey: true }), { isMac: true })).to.equal(null);
    expect(matchShortcut(fakeEvent("j", { ctrlKey: true }), { isMac: true })).to.equal(null);
    expect(matchShortcut(fakeEvent("j", { metaKey: true }), { isMac: true })).to.equal(null);
    expect(matchShortcut(fakeEvent("j", { altKey: true }), { isMac: true })).to.equal(null);
  });

  it("matches Escape -> CLOSE_TOPMOST (global)", () => {
    expect(matchShortcut(fakeEvent("Escape"), { isMac: true })).to.deep.equal({
      action: KEY_ACTION.CLOSE_TOPMOST,
      global: true
    });
    expect(matchShortcut(fakeEvent("Esc"), { isMac: true })).to.deep.equal({
      action: KEY_ACTION.CLOSE_TOPMOST,
      global: true
    });
  });

  it("rejects modified Esc combinations", () => {
    expect(matchShortcut(fakeEvent("Escape", { shiftKey: true }), { isMac: true })).to.equal(null);
    expect(matchShortcut(fakeEvent("Escape", { ctrlKey: true }), { isMac: true })).to.equal(null);
  });

  it("matches Shift+Cmd+O on Mac -> OPEN_NEW_WAVE (global)", () => {
    expect(
      matchShortcut(fakeEvent("o", { shiftKey: true, metaKey: true }), { isMac: true })
    ).to.deep.equal({ action: KEY_ACTION.OPEN_NEW_WAVE, global: true });
    expect(
      matchShortcut(fakeEvent("O", { shiftKey: true, metaKey: true }), { isMac: true })
    ).to.deep.equal({ action: KEY_ACTION.OPEN_NEW_WAVE, global: true });
  });

  it("matches Shift+Ctrl+O on non-Mac -> OPEN_NEW_WAVE (global)", () => {
    expect(
      matchShortcut(fakeEvent("o", { shiftKey: true, ctrlKey: true }), { isMac: false })
    ).to.deep.equal({ action: KEY_ACTION.OPEN_NEW_WAVE, global: true });
  });

  it("does not match Shift+Cmd+O on non-Mac (wrong primary modifier)", () => {
    expect(
      matchShortcut(fakeEvent("o", { shiftKey: true, metaKey: true }), { isMac: false })
    ).to.equal(null);
  });

  it("does not match Shift+Ctrl+O on Mac (wrong primary modifier)", () => {
    expect(
      matchShortcut(fakeEvent("o", { shiftKey: true, ctrlKey: true }), { isMac: true })
    ).to.equal(null);
  });

  it("does not match Cmd+O without Shift", () => {
    expect(
      matchShortcut(fakeEvent("o", { metaKey: true }), { isMac: true })
    ).to.equal(null);
  });

  it("does not match repeated Escape or Shift+Cmd+O", () => {
    expect(
      matchShortcut(fakeEvent("Escape", { repeat: true }), { isMac: true })
    ).to.equal(null);
    expect(
      matchShortcut(fakeEvent("o", { shiftKey: true, metaKey: true, repeat: true }), { isMac: true })
    ).to.equal(null);
  });

  it("matches repeated j/k for continuous blip navigation", () => {
    expect(
      matchShortcut(fakeEvent("j", { repeat: true }), { isMac: true })
    ).to.deep.equal({ action: "BLIP_FOCUS_NEXT", global: false });
    expect(
      matchShortcut(fakeEvent("k", { repeat: true }), { isMac: true })
    ).to.deep.equal({ action: "BLIP_FOCUS_PREV", global: false });
  });
});

describe("isEditableTarget", () => {
  it("returns false for a div", () => {
    const div = document.createElement("div");
    expect(isEditableTarget({ target: div, composedPath: () => [div] })).to.equal(false);
  });

  it("returns true for an <input>", () => {
    const inp = document.createElement("input");
    expect(isEditableTarget({ target: inp, composedPath: () => [inp] })).to.equal(true);
  });

  it("returns true for a <textarea>", () => {
    const ta = document.createElement("textarea");
    expect(isEditableTarget({ target: ta, composedPath: () => [ta] })).to.equal(true);
  });

  it("returns true for contenteditable=true", () => {
    const div = document.createElement("div");
    div.setAttribute("contenteditable", "true");
    expect(isEditableTarget({ target: div, composedPath: () => [div] })).to.equal(true);
  });

  it("returns true for bare contenteditable (empty-string value)", () => {
    const div = document.createElement("div");
    div.setAttribute("contenteditable", "");
    expect(isEditableTarget({ target: div, composedPath: () => [div] })).to.equal(true);
  });

  it("returns false for contenteditable=false", () => {
    const div = document.createElement("div");
    div.setAttribute("contenteditable", "false");
    expect(isEditableTarget({ target: div, composedPath: () => [div] })).to.equal(false);
  });
});

describe("isMacPlatform", () => {
  it("detects MacIntel", () => {
    expect(isMacPlatform({ platform: "MacIntel" })).to.equal(true);
  });
  it("detects iPhone via platform", () => {
    expect(isMacPlatform({ platform: "iPhone" })).to.equal(true);
  });
  it("returns false for Win32", () => {
    expect(isMacPlatform({ platform: "Win32", userAgent: "Mozilla" })).to.equal(false);
  });
  it("falls back to userAgent for Mac", () => {
    expect(
      isMacPlatform({ platform: "", userAgent: "Mozilla/5.0 (Macintosh; ...)" })
    ).to.equal(true);
  });
});
