// G-PORT-7 (#1116): blip focus navigation helper for the j/k shell
// shortcuts. Owns the "find current focused blip, move +1 / -1" walk.
//
// Cloned from `FocusFramePresenter.moveDown` / `moveUp` — the GWT
// presenter walks the same per-wave blip list. The J2CL renderer
// already reflects `focused` to a host attribute on every <wave-blip>,
// so we drive the navigation by reading + flipping that attribute on
// snapshot Arrays (NOT live NodeLists; the F-2 viewport renderer
// mounts/unmounts blips off-screen and a live list races a mid-walk
// read).
//
// Renderer sync: setFocusedBlip also manages `j2cl-read-blip-focused`
// (the class owned by J2clReadSurfaceDomRenderer.focusBlip) and fires
// `wavy-focus-changed` on the nearest read surface so the
// <wavy-focus-frame> overlay tracks keyboard-driven navigation.

const FOCUS_CHANGED_EVENT = "wave-blip-focus-changed";
const RENDERER_FOCUS_CLASS = "j2cl-read-blip-focused";
const READ_SURFACE_ATTR = "data-j2cl-read-surface";

/**
 * Snapshot the navigable <wave-blip> hosts, ordered by document
 * position. Excludes blips hidden by the F-2 viewport renderer
 * (`[hidden]`) and blips inside collapsed reply-thread containers
 * (`.j2cl-read-thread-collapsed`), matching the GWT renderer's own
 * `visibleBlips()` set.
 */
function snapshotBlips(root = document) {
  const blips = Array.from(root.querySelectorAll("wave-blip"));
  return blips.filter((b) => {
    if (b.hasAttribute("hidden")) return false;
    if (b.closest && b.closest(".j2cl-read-thread-collapsed")) return false;
    return true;
  });
}

/**
 * Snapshot ALL <wave-blip> hosts including parked/hidden ones.
 * Used when clearing focus markers so a blip that was focused before
 * the viewport renderer parked it off-screen (adding `[hidden]`) does
 * not keep stale `focused`/`j2cl-read-blip-focused` markers.
 */
function snapshotAllBlips(root = document) {
  return Array.from(root.querySelectorAll("wave-blip"));
}

/**
 * Returns the index of the currently focused blip, or -1.
 * Checks both the Lit `focused` attribute and the renderer-managed
 * `j2cl-read-blip-focused` class so j/k continues from wherever focus
 * was established (keyboard shortcut or renderer click handler).
 */
function findFocusedIndex(list) {
  for (let i = 0; i < list.length; i++) {
    if (list[i].hasAttribute("focused") || list[i].classList.contains(RENDERER_FOCUS_CLASS)) return i;
  }
  return -1;
}

/**
 * Walk up from `blip` to find the nearest [data-j2cl-read-surface]
 * element (where J2clReadSurfaceDomRenderer fires `wavy-focus-changed`).
 * Falls back to a root-level query so the dispatcher still works when
 * only one panel is open.
 *
 * Matches any truthy value of the attribute (e.g. "true" or "preview")
 * so the preview read-surface route is also reachable.
 */
function findReadSurface(blip, root = document) {
  let el = blip && blip.parentElement;
  while (el) {
    if (typeof el.hasAttribute === "function" && el.hasAttribute(READ_SURFACE_ATTR)) return el;
    el = el.parentElement;
  }
  // `root` itself may be the read-surface element (e.g. when scoped to
  // a shadow root or a fixture element for testing).
  if (root && typeof root.hasAttribute === "function" && root.hasAttribute(READ_SURFACE_ATTR)) return root;
  return root && root.querySelector ? root.querySelector(`[${READ_SURFACE_ATTR}]`) : null;
}

/**
 * Fire `wavy-focus-changed` on the read surface so the
 * <wavy-focus-frame> overlay repaints. Mirrors the detail shape that
 * J2clReadSurfaceDomRenderer.dispatchFocusChanged emits. Pass
 * `blip = null` to clear the frame.
 */
function dispatchRendererFocusChanged(surface, blip) {
  if (!surface) return;
  const blipId = blip ? (blip.getAttribute("data-blip-id") || "") : "";
  let bounds = { top: 0, left: 0, width: 0, height: 0 };
  if (blip) {
    try {
      const br = blip.getBoundingClientRect();
      const sr = surface.getBoundingClientRect();
      bounds = {
        top: br.top - sr.top + surface.scrollTop,
        left: br.left - sr.left + surface.scrollLeft,
        width: br.width,
        height: br.height
      };
    } catch (_) {
      // best-effort; bounds failure must never break focus state
    }
  }
  try {
    surface.dispatchEvent(
      new CustomEvent("wavy-focus-changed", {
        bubbles: true,
        composed: true,
        detail: { blipId, bounds, key: "" }
      })
    );
  } catch (_) {
    // observational — never break focus state on dispatch failure
  }
}

/**
 * Move blip focus by `direction` (+1 = next, -1 = prev). Returns
 * `true` if focus moved or was already at the boundary (key consumed),
 * or `false` if there were no navigable blips at all.
 *
 * Clamps at list boundaries — pressing `j` past the last blip or `k`
 * past the first is a no-op (matches GWT `FocusFramePresenter.moveDown`/
 * `moveUp` and `J2clReadSurfaceDomRenderer.focusVisibleByIndex` which
 * both clamp rather than wrap).
 */
export function moveBlipFocus(direction, root = document) {
  const list = snapshotBlips(root);
  if (list.length === 0) return false;

  const currentIdx = findFocusedIndex(list);
  let nextIdx;
  if (currentIdx === -1) {
    nextIdx = direction > 0 ? 0 : list.length - 1;
  } else {
    nextIdx = currentIdx + direction;
    if (nextIdx < 0 || nextIdx >= list.length) return true; // at boundary, consume key
  }
  setFocusedBlip(list[nextIdx], root);
  return true;
}

/**
 * Clear `focused` on every other blip, then set it on `target`.
 * Reflects to `data-blip-focused` on the host so the parity test can
 * target the same selector across both views.
 *
 * Clears ALL wave-blip nodes (including hidden/parked ones) to avoid
 * stale markers when the viewport renderer parks the previously-focused
 * blip off-screen between j/k presses.
 *
 * Also manages `j2cl-read-blip-focused` (the renderer CSS class) and
 * fires `wavy-focus-changed` on the nearest read surface so the
 * <wavy-focus-frame> overlay stays in sync with keyboard navigation.
 *
 * Fires a `wave-blip-focus-changed` CustomEvent (bubbles + composed)
 * so external consumers (telemetry, the route controller) can react.
 */
export function setFocusedBlip(target, root = document) {
  if (!target) return;
  // Clear ALL blips (including hidden/parked) so stale markers left by
  // the viewport renderer do not accumulate. `snapshotBlips` still scopes
  // j/k navigation to visible blips; clearing goes broader.
  for (const blip of snapshotAllBlips(root)) {
    if (blip !== target) {
      blip.removeAttribute("focused");
      blip.removeAttribute("data-blip-focused");
      blip.removeAttribute("aria-current");
      if ("focused" in blip) blip.focused = false;
      blip.classList.remove(RENDERER_FOCUS_CLASS);
    }
  }
  target.setAttribute("focused", "");
  target.setAttribute("data-blip-focused", "true");
  target.setAttribute("aria-current", "true");
  if ("focused" in target) target.focused = true;
  target.classList.add(RENDERER_FOCUS_CLASS);
  if (typeof target.scrollIntoView === "function") {
    target.scrollIntoView({ block: "nearest", inline: "nearest" });
  }
  // Keep DOM focus where the shell-level keyboard handler received it.
  // Moving browser focus onto the <wave-blip> host makes the next
  // repeated j/k keydown target the custom element itself; in the
  // viewport-windowed read surface that event can be consumed at the
  // current blip boundary before the renderer has grown the next window.
  // GWT's focus frame is visual state rather than native DOM focus, so
  // the parity behavior is to reflect focus via attributes/classes and
  // leave keyboard ownership with the shell.
  target.dispatchEvent(
    new CustomEvent(FOCUS_CHANGED_EVENT, {
      bubbles: true,
      composed: true,
      detail: {
        blipId: target.getAttribute("data-blip-id") || "",
        waveId: target.getAttribute("data-wave-id") || ""
      }
    })
  );
  dispatchRendererFocusChanged(findReadSurface(target, root), target);
}

/**
 * Drop the focused-blip selection. Returns true when something was
 * cleared (so the caller knows the Esc was consumed).
 *
 * Clears both the Lit `focused` attribute and the renderer-managed
 * `j2cl-read-blip-focused` class so Esc works regardless of how focus
 * was established. Fires `wavy-focus-changed` (blipId = "") to hide
 * the <wavy-focus-frame> overlay.
 */
export function clearBlipFocus(root = document) {
  const allNodes = snapshotAllBlips(root);
  let cleared = false;
  let surface = null;
  for (const blip of allNodes) {
    if (blip.hasAttribute("focused") || blip.classList.contains(RENDERER_FOCUS_CLASS)) {
      blip.removeAttribute("focused");
      blip.removeAttribute("data-blip-focused");
      blip.removeAttribute("aria-current");
      if ("focused" in blip) blip.focused = false;
      blip.classList.remove(RENDERER_FOCUS_CLASS);
      if (!surface) surface = findReadSurface(blip, root);
      cleared = true;
    }
  }
  if (cleared) {
    dispatchRendererFocusChanged(surface || findReadSurface(null, root), null);
  }
  return cleared;
}

export const _internalForTesting = {
  snapshotBlips,
  snapshotAllBlips,
  findFocusedIndex,
  findReadSurface,
  dispatchRendererFocusChanged,
  FOCUS_CHANGED_EVENT,
  RENDERER_FOCUS_CLASS
};
