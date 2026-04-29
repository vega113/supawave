// G-PORT-7 (#1116): dialog-stack helper for the Esc shell shortcut.
// Walks the document for "any closeable surface", picks the topmost,
// and closes it. Returns true when something was closed so the Esc
// dispatcher knows to STOP rather than also dropping blip focus.
//
// "Topmost" priority order:
//   1. Modal dialogs (wavy-confirm-dialog, wavy-link-modal,
//      wavy-version-history) — these eat all other input on screen so
//      they MUST close first.
//   2. Anchored popovers (reaction-picker-popover,
//      reaction-authors-popover, task-metadata-popover,
//      mention-suggestion-popover, wavy-search-help) — secondary.
//   3. The wavy-profile-overlay surface (a soft modal — sits over
//      the wave but does not trap input as hard as the dialogs).
//
// Within a tier, ties resolve by document order: later-mounted
// surfaces close first, matching the natural "stack" semantics the
// user perceives.

/**
 * Selectors per tier. Each candidate must satisfy:
 *   (a) the element is currently in the DOM, and
 *   (b) the element exposes one of:
 *         - `.open` boolean property is true; or
 *         - the host has the `open` HTML attribute.
 * Closing logic:
 *   (a) prefer `host.close()` if the surface defines one (matches the
 *       <dialog> close-method convention);
 *   (b) otherwise set `host.open = false` (Lit reflects that to the
 *       attribute; popovers re-render closed).
 */
const TIERS = [
  // Tier 1 — modal dialogs.
  [
    "wavy-confirm-dialog",
    "wavy-link-modal",
    "wavy-version-history"
  ],
  // Tier 2 — anchored popovers.
  [
    "reaction-picker-popover",
    "reaction-authors-popover",
    "task-metadata-popover",
    "mention-suggestion-popover",
    "wavy-search-help"
  ],
  // Tier 3 — soft modal.
  [
    "wavy-profile-overlay"
  ]
];

function isOpen(host) {
  if (!host) return false;
  if (typeof host.open === "boolean") return host.open === true;
  if (host.hasAttribute && host.hasAttribute("open")) return true;
  return false;
}

function closeHost(host) {
  if (!host) return false;
  try {
    if (typeof host.close === "function") {
      host.close();
      return true;
    }
    if (typeof host.open === "boolean") {
      host.open = false;
      return true;
    }
    if (host.removeAttribute) {
      host.removeAttribute("open");
      return true;
    }
  } catch (e) {
    // Best-effort: a custom element may throw during close if it is
    // not yet fully upgraded. Return false so the caller can fall
    // through to the blip-focus drop path.
    return false;
  }
  return false;
}

/**
 * Collect all shadow roots reachable from `root`, depth-first. Tier-2
 * popovers (task-metadata-popover, mention-suggestion-popover) are
 * rendered inside Lit component shadow trees, so plain querySelectorAll
 * on the document misses them.
 */
function collectShadowRoots(root, result = []) {
  for (const el of root.querySelectorAll("*")) {
    if (el.shadowRoot) {
      result.push(el.shadowRoot);
      collectShadowRoots(el.shadowRoot, result);
    }
  }
  return result;
}

/**
 * Return the composed-tree ancestor that lives in the top-level
 * document (i.e. the shadow host chain root). Used to compare two
 * nodes that may live in different shadow trees.
 */
function topLevelAncestor(node) {
  let el = node;
  while (el) {
    const r = el.getRootNode ? el.getRootNode() : null;
    if (!r || r === document) return el;
    // r is a ShadowRoot — step up to its host.
    el = r.host || null;
  }
  return node;
}

/**
 * Compare two elements in composed-tree order. Returns a negative
 * number if `a` precedes `b`, 0 if they are the same, and positive
 * if `a` follows `b`. Elements in different shadow trees are compared
 * via their top-level document ancestors so the result always
 * reflects visual/DOM stacking order as perceived by the user.
 */
function composedTreeCompare(a, b) {
  if (a === b) return 0;
  const aTop = topLevelAncestor(a);
  const bTop = topLevelAncestor(b);
  if (aTop !== bTop) {
    // The shadow hosts live in the same light DOM — compare them.
    const pos = aTop.compareDocumentPosition(bTop);
    // DOCUMENT_POSITION_FOLLOWING = 4
    if (pos & 4) return -1; // aTop comes before bTop
    if (pos & 2) return 1;  // aTop comes after bTop (PRECEDING = 2)
    return 0;
  }
  // Both in the same root (light DOM or same shadow root).
  const pos = a.compareDocumentPosition(b);
  if (pos & 4) return -1;
  if (pos & 2) return 1;
  return 0;
}

/**
 * Find every open closeable surface in tier order. Returns the first
 * tier that has at least one open surface, then picks the one latest
 * in composed-tree order (= the "topmost" surface the user perceives)
 * so the most recently mounted surface closes first.
 *
 * Queries both light DOM and shadow roots so tier-2 popovers rendered
 * inside component shadow trees (e.g. task-metadata-popover inside
 * wavy-task-affordance) are reachable. Sorting by composed-tree
 * position ensures a shadow-root popover whose host appears early in
 * the document does not incorrectly outrank a later light-DOM surface.
 */
function findTopmostOpen(root = document) {
  const roots = [root, ...collectShadowRoots(root)];
  for (const tier of TIERS) {
    const selector = tier.join(", ");
    const open = [];
    for (const r of roots) {
      const nodes = Array.from(r.querySelectorAll(selector));
      open.push(...nodes.filter((n) => isOpen(n)));
    }
    if (open.length > 0) {
      // Sort by composed-tree position; pick the last (= topmost).
      open.sort(composedTreeCompare);
      return open[open.length - 1];
    }
  }
  return null;
}

/**
 * Find the innermost open native <dialog> element inside `host`'s own
 * subtree (light DOM children + shadow roots). Returns it when found,
 * null otherwise.
 *
 * Used to prioritize closing a sub-confirmation dialog (e.g. the
 * restore-confirm <dialog class="confirm"> inside wavy-version-history)
 * before closing the surrounding overlay host. This preserves the
 * "one action per Esc" contract when a component renders secondary
 * native dialogs that are not represented in the TIERS list.
 */
function findInnerNativeDialog(host) {
  const open = [];
  // Light-DOM children of host (slot content / direct children).
  if (typeof host.querySelectorAll === "function") {
    open.push(...Array.from(host.querySelectorAll("dialog")).filter((d) => d.open === true));
  }
  // Shadow DOM: immediate shadow root + nested shadow roots within it.
  if (host.shadowRoot) {
    const shadowRoots = [host.shadowRoot, ...collectShadowRoots(host.shadowRoot)];
    for (const r of shadowRoots) {
      open.push(...Array.from(r.querySelectorAll("dialog")).filter((d) => d.open === true));
    }
  }
  if (open.length === 0) return null;
  open.sort(composedTreeCompare);
  return open[open.length - 1];
}

/**
 * Close the topmost open dialog/popover. Returns true when something
 * closed so the Esc dispatcher can STOP (one action per keypress).
 *
 * Before closing the found host, checks whether its subtree contains an
 * open native <dialog> element. If so, closes that inner dialog first
 * (leaving the host open) so that a secondary confirmation prompt (e.g.
 * wavy-version-history's restore-confirm) dismisses before the host
 * overlay does.
 */
export function closeTopmostDialog(root = document) {
  const target = findTopmostOpen(root);
  if (!target) return false;
  const inner = findInnerNativeDialog(target);
  if (inner && inner !== target) return closeHost(inner);
  return closeHost(target);
}

export const _internalForTesting = { TIERS, isOpen, closeHost, findTopmostOpen, collectShadowRoots, composedTreeCompare, topLevelAncestor, findInnerNativeDialog };
