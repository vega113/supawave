// G-PORT-7 (#1116): single source of truth for shell-level keyboard
// shortcuts. Pure functions only — the matcher and the editable-target
// guard are unit-testable in isolation.
//
// Cloned from the GWT keyboard handler in
// `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/event/`
// (KeySignalRouter + FocusFrameController.onKeySignal). We pick the
// six combos called out in issue #1116 as the parity baseline:
//
//   - j / ArrowDown
//              BLIP_FOCUS_NEXT       (move focused blip down)
//   - k / ArrowUp
//              BLIP_FOCUS_PREV       (move focused blip up)
//   - Shift+   OPEN_NEW_WAVE         (open the create-wave surface)
//     Cmd+O / Shift+Ctrl+O
//   - Esc      CLOSE_TOPMOST         (close topmost dialog OR
//                                     deselect focused blip)
//
// Per-context bindings (Enter on search input, Arrow/Enter on the
// mention popover) are owned by their respective custom elements and
// are NOT routed through this matcher — they are the per-element
// natural keys, and going through a global registry would just risk
// double-handling them.

/** Shortcut action ids. */
export const KEY_ACTION = Object.freeze({
  BLIP_FOCUS_NEXT: "BLIP_FOCUS_NEXT",
  BLIP_FOCUS_PREV: "BLIP_FOCUS_PREV",
  OPEN_NEW_WAVE: "OPEN_NEW_WAVE",
  CLOSE_TOPMOST: "CLOSE_TOPMOST"
});

/**
 * Returns true when the keyboard event originated inside an editable
 * surface (input, textarea, select, contenteditable). Bindings tagged
 * `global: true` actions still fire when the target is editable. Esc
 * remains global for native dialog semantics. Shift+Cmd/Ctrl+O is not
 * global because #1076 requires New Wave to stay suppressed while the
 * user is typing in a composer body, search input, or form field.
 */
export function isEditableTarget(evt) {
  if (!evt) return false;
  // Use composedPath so we see through shadow roots — the J2CL surface
  // has lots of them and `evt.target` retargets at the boundary.
  const path = typeof evt.composedPath === "function" ? evt.composedPath() : [];
  const candidates = path.length > 0 ? path : [evt.target].filter(Boolean);
  for (const node of candidates) {
    if (!node || node.nodeType !== 1 /* ELEMENT_NODE */) continue;
    const el = /** @type {Element} */ (node);
    const tag = el.tagName ? el.tagName.toLowerCase() : "";
    if (tag === "input" || tag === "textarea" || tag === "select") {
      return true;
    }
    // contenteditable; also catches the wavy-composer body which sets
    // contenteditable="true" via a Lit attribute binding. Use hasAttribute
    // so bare `contenteditable` / `contenteditable=""` (empty-string value,
    // which is falsy) is also detected correctly.
    if (
      typeof el.hasAttribute === "function" &&
      el.hasAttribute("contenteditable") &&
      el.getAttribute("contenteditable").toLowerCase() !== "false"
    ) {
      return true;
    }
    // Stop walking once we hit a known shadow host boundary; the
    // composedPath has already given us everything inside.
    if (el === document.documentElement) break;
  }
  return false;
}

/**
 * True when the user is on a Mac-like platform. Treats Cmd as the
 * "primary" modifier on Mac and Ctrl elsewhere. We deliberately read
 * `navigator.platform` first (still the broadly available signal in
 * 2026 across the browsers SupaWave supports) and fall through to
 * `userAgentData.platform` and the userAgent string for resilience.
 */
export function isMacPlatform(nav = typeof navigator !== "undefined" ? navigator : null) {
  if (!nav) return false;
  const platform =
    (nav.platform && String(nav.platform)) ||
    (nav.userAgentData && nav.userAgentData.platform) ||
    "";
  if (/mac|iphone|ipad|ipod/i.test(platform)) return true;
  // Some Chromium builds blank `platform`; fall back to userAgent.
  const ua = (nav.userAgent && String(nav.userAgent)) || "";
  return /mac|iphone|ipad|ipod/i.test(ua);
}

/**
 * Match a `KeyboardEvent` to a `KEY_ACTION` or null. Pure function —
 * does NOT consume the event. Caller is responsible for
 * preventDefault + stopPropagation when an action is dispatched.
 *
 * Returns an object `{action, global}`. `global: true` means the
 * caller should fire the shortcut even when the event target is an
 * editable surface; `global: false` means the caller should bail out
 * if `isEditableTarget(evt)` returns true.
 *
 * Per-context handlers (search Enter, mention Arrow/Enter) are NOT
 * surfaced here — they live with their owning element.
 */
export function matchShortcut(evt, opts = {}) {
  if (!evt || typeof evt.key !== "string") return null;
  const isMac = "isMac" in opts ? !!opts.isMac : isMacPlatform();

  // Esc — close topmost dialog or deselect focused blip. Global so it
  // fires even when an input is focused (matches native dialog
  // semantics: Esc dismisses). Block repeats to avoid slamming the stack.
  if (evt.key === "Escape" || evt.key === "Esc") {
    if (evt.repeat) return null;
    if (evt.shiftKey || evt.ctrlKey || evt.metaKey || evt.altKey) {
      // Bare Esc only — modified Esc combinations are reserved for
      // browser / a11y conventions and may be claimed by other tools.
      return null;
    }
    return { action: KEY_ACTION.CLOSE_TOPMOST, global: true };
  }

  // Shift+Cmd+O on Mac, Shift+Ctrl+O elsewhere. Not global: #1076 says
  // the shortcut must not fire from editable targets. Block repeats.
  if (
    (evt.key === "O" || evt.key === "o") &&
    evt.shiftKey &&
    !evt.altKey &&
    ((isMac && evt.metaKey && !evt.ctrlKey) ||
      (!isMac && evt.ctrlKey && !evt.metaKey))
  ) {
    if (evt.repeat) return null;
    return { action: KEY_ACTION.OPEN_NEW_WAVE, global: false };
  }

  // j/k and ArrowDown/ArrowUp — blip navigation. Bare key only; not
  // global. Modifiers disqualify because Cmd+J / Ctrl+K etc. are
  // claimed by browsers. Allow repeat so holding the key continuously
  // moves focus (GWT parity). The Lit shell is the single repeated
  // next/previous owner so focus always follows actual <wave-blip>
  // DOM order, including inline-reply blips mounted inside Lit chrome.
  if (
    !evt.shiftKey &&
    !evt.ctrlKey &&
    !evt.metaKey &&
    !evt.altKey
  ) {
    if (evt.key === "j" || evt.key === "J" || evt.key === "ArrowDown") {
      return { action: KEY_ACTION.BLIP_FOCUS_NEXT, global: false };
    }
    if (evt.key === "k" || evt.key === "K" || evt.key === "ArrowUp") {
      return { action: KEY_ACTION.BLIP_FOCUS_PREV, global: false };
    }
  }
  return null;
}
