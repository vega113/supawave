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

const FOCUS_CHANGED_EVENT = "wave-blip-focus-changed";

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

/** Returns the index of the currently focused blip, or -1. */
function findFocusedIndex(list) {
  for (let i = 0; i < list.length; i++) {
    if (list[i].hasAttribute("focused")) return i;
  }
  return -1;
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
 * Fires a `wave-blip-focus-changed` CustomEvent (bubbles + composed)
 * so external consumers (telemetry, the route controller) can react.
 */
export function setFocusedBlip(target, list = snapshotBlips()) {
  if (!target) return;
  for (const blip of list) {
    if (blip !== target && blip.hasAttribute("focused")) {
      blip.removeAttribute("focused");
      blip.removeAttribute("data-blip-focused");
      // Mirror the JS property too so the Lit reflection cache stays
      // in sync; otherwise the next render can re-add the attribute.
      if ("focused" in blip) blip.focused = false;
    }
  }
  target.setAttribute("focused", "");
  target.setAttribute("data-blip-focused", "true");
  if ("focused" in target) target.focused = true;
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
}

/**
 * Drop the focused-blip selection. Returns true when something was
 * cleared (so the caller knows the Esc was consumed).
 */
export function clearBlipFocus(root = document) {
  const list = snapshotBlips(root);
  let cleared = false;
  for (const blip of list) {
    if (blip.hasAttribute("focused")) {
      blip.removeAttribute("focused");
      blip.removeAttribute("data-blip-focused");
      if ("focused" in blip) blip.focused = false;
      cleared = true;
    }
  }
  return cleared;
}

export const _internalForTesting = {
  snapshotBlips,
  findFocusedIndex,
  FOCUS_CHANGED_EVENT
};
