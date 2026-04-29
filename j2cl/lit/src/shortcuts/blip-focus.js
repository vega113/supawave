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
 * Snapshot the visible <wave-blip> hosts, ordered by document
 * position. Hidden blips (the F-2 viewport renderer parks below-fold
 * blips with `[hidden]`) are excluded so j/k cannot land on a node
 * the user cannot see.
 */
function snapshotBlips(root = document) {
  const blips = Array.from(root.querySelectorAll("wave-blip"));
  return blips.filter((b) => !b.hasAttribute("hidden"));
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
 */
function findReadSurface(blip, root = document) {
  let el = blip && blip.parentElement;
  while (el) {
    if (el.getAttribute(READ_SURFACE_ATTR) === "true") return el;
    el = el.parentElement;
  }
  return root && root.querySelector ? root.querySelector(`[${READ_SURFACE_ATTR}="true"]`) : null;
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
 * `true` if focus moved (so the shell handler knows to swallow the
 * key event), or `false` if there were no blips at all (let the key
 * fall through).
 *
 * Wraps at the end: pressing `j` past the last blip lands on the
 * first; pressing `k` past the first lands on the last. Wrap matches
 * the GWT `FocusFramePresenter.moveDown` behaviour for consistency
 * with the umbrella parity contract.
 */
export function moveBlipFocus(direction, root = document) {
  const list = snapshotBlips(root);
  if (list.length === 0) return false;

  const currentIdx = findFocusedIndex(list);
  let nextIdx;
  if (currentIdx === -1) {
    nextIdx = direction > 0 ? 0 : list.length - 1;
  } else {
    nextIdx = (currentIdx + direction + list.length) % list.length;
  }
  setFocusedBlip(list[nextIdx], list);
  return true;
}

/**
 * Clear `focused` on every other blip in `list`, then set it on
 * `target`. Reflects to `data-blip-focused` on the host (alias for
 * the existing `focused` attribute) so the parity test can target
 * the same selector across both views.
 *
 * Also manages `j2cl-read-blip-focused` (the renderer CSS class) and
 * fires `wavy-focus-changed` on the nearest read surface so the
 * <wavy-focus-frame> overlay stays in sync with keyboard navigation.
 *
 * Fires a `wave-blip-focus-changed` CustomEvent (bubbles + composed)
 * so external consumers (telemetry, the route controller) can react.
 */
export function setFocusedBlip(target, list = snapshotBlips()) {
  if (!target) return;
  // Clear ALL blips (including hidden/parked) so stale markers left by
  // the viewport renderer do not accumulate. `list` still scopes j/k
  // navigation to visible blips; clearing goes broader.
  const allBlips = snapshotAllBlips();
  for (const blip of allBlips) {
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
  if ("focused" in target) target.focused = true;
  target.classList.add(RENDERER_FOCUS_CLASS);
  if (typeof target.scrollIntoView === "function") {
    target.scrollIntoView({ block: "nearest", inline: "nearest" });
  }
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
  dispatchRendererFocusChanged(findReadSurface(target), target);
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
  const list = snapshotAllBlips(root);
  let cleared = false;
  let surface = null;
  for (const blip of list) {
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
