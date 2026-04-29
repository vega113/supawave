// G-PORT-8 (#1117) — wires <wavy-wave-nav-row> archive/pin/history
// click events to the GWT-equivalent /folder servlet POST and to the
// existing <wavy-version-history> overlay.
//
// Bound to each <wavy-wave-nav-row> host individually (via a
// MutationObserver on document.documentElement) instead of attaching
// listeners on `document`, so the optimistic flip cannot race the
// eventual S5 wiring of J2clSelectedWaveView.setNavRowFolderState
// (#1055/S5) which owns the same attributes once the model exposes
// pin/archive state.
//
// Public API: import this module for its side effect. The controller
// installs itself on first import and is idempotent. `start()` runs
// the install; tests can call `stop()` to tear down.
//
// Network contract (mirrors org.waveprotocol.box.webclient.folder
// and FolderServlet.doPost):
//   POST /folder/?operation=move&folder=archive|inbox&waveId=<id>
//   POST /folder/?operation=pin|unpin&waveId=<id>
//
// Folder result events emitted on document (bubbles + composed):
//   - wavy-folder-action-completed { waveId, operation, folder }
//   - wavy-folder-action-failed    { waveId, operation, folder, status }
// Search refresh is emitted on <wavy-search-rail> when present because
// J2clSearchPanelView owns that listener on the rail element.

const NAV_ROW_TAG = "WAVY-WAVE-NAV-ROW";
const VERSION_HISTORY_TAG = "WAVY-VERSION-HISTORY";

const ATTR_BOUND = "data-action-bar-bound";
const ATTR_FOLDER_STATE_WAVE_ID = "data-folder-state-wave-id";
const ATTR_BUSY_WAVE_ID = "data-folder-busy-wave-id";

let installed = false;
let observer = null;
const boundRows = new Set();

function isNavRow(node) {
  return (
    node && node.nodeType === 1 && node.tagName === NAV_ROW_TAG
  );
}

function bindNavRow(row) {
  if (!row || boundRows.has(row)) return;
  boundRows.add(row);
  row.setAttribute(ATTR_BOUND, "");
  row.addEventListener(
    "wave-nav-archive-toggle-requested",
    onArchiveToggle
  );
  row.addEventListener(
    "wave-nav-pin-toggle-requested",
    onPinToggle
  );
  row.addEventListener(
    "wave-nav-version-history-requested",
    onVersionHistoryRequest
  );
}

function scanFor(root) {
  if (!root) return;
  if (isNavRow(root)) {
    bindNavRow(root);
    return;
  }
  if (typeof root.querySelectorAll === "function") {
    const rows = root.querySelectorAll(NAV_ROW_TAG.toLowerCase());
    for (const row of rows) bindNavRow(row);
  }
}

function readWaveId(event) {
  const detail = event && event.detail ? event.detail : {};
  if (typeof detail.sourceWaveId === "string" && detail.sourceWaveId) {
    return detail.sourceWaveId;
  }
  // Fall back to the row's reflected attribute. CustomEvent.target
  // may be the inner button after retargeting; currentTarget is the
  // bound nav-row host.
  const host = event.currentTarget;
  if (host && typeof host.getAttribute === "function") {
    const attr = host.getAttribute("source-wave-id");
    if (attr) return attr;
  }
  return "";
}

function readBoolAttr(host, attrName) {
  if (!host || typeof host.hasAttribute !== "function") return false;
  return host.hasAttribute(attrName);
}

function setBoolAttr(host, attrName, value) {
  if (!host || typeof host.setAttribute !== "function") return;
  if (value) {
    host.setAttribute(attrName, "");
  } else {
    host.removeAttribute(attrName);
  }
}

function isBusyForWave(host, waveId) {
  if (!host || typeof host.hasAttribute !== "function") return false;
  return (
    host.hasAttribute("data-folder-busy") &&
    host.getAttribute(ATTR_BUSY_WAVE_ID) === waveId
  );
}

function isCurrentWave(host, waveId) {
  if (!host || typeof host.getAttribute !== "function") return true;
  return host.getAttribute("source-wave-id") === waveId;
}

function setBusy(host, busy, waveId) {
  if (!host || typeof host.setAttribute !== "function") return;
  if (busy) {
    host.setAttribute("data-folder-busy", "");
    if (waveId) host.setAttribute(ATTR_BUSY_WAVE_ID, waveId);
  } else {
    const owner = host.getAttribute(ATTR_BUSY_WAVE_ID);
    if (waveId && owner && owner !== waveId) return;
    host.removeAttribute("data-folder-busy");
    host.removeAttribute(ATTR_BUSY_WAVE_ID);
  }
  // Also stamp the inner action button so CSS can show progress
  // cursor / fade. Best-effort — if shadowRoot is closed, skip.
  try {
    const root = host.shadowRoot;
    if (!root) return;
    const buttons = root.querySelectorAll(
      "button[data-action='archive'], button[data-action='pin']"
    );
    for (const btn of buttons) {
      if (busy) {
        btn.setAttribute("data-busy", "true");
      } else {
        btn.removeAttribute("data-busy");
      }
    }
  } catch (_e) {
    // shadow access may throw in some test harnesses; ignore.
  }
}

function resetFolderStateIfWaveChanged(host, waveId) {
  if (!host || typeof host.getAttribute !== "function") return;
  const current = host.getAttribute(ATTR_FOLDER_STATE_WAVE_ID);
  if (current === waveId) return;
  host.setAttribute(ATTR_FOLDER_STATE_WAVE_ID, waveId);
  if (current == null) return;
  // The same nav-row host can be reused while switching waves. Until
  // server folder state is wired into the model (#1055/S5), optimistic
  // toggle attributes must not bleed from the previous wave.
  host.removeAttribute("pinned");
  host.removeAttribute("archived");
}

function buildFolderUrl(params) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== "") search.set(k, String(v));
  }
  return "/folder/?" + search.toString();
}

function fetchFolderOp(url) {
  // Use fetch with method POST + same-origin credentials so the
  // session cookie ships. The servlet reads parameters via
  // request.getParameter (works for query string params on a POST).
  return fetch(url, {
    method: "POST",
    credentials: "same-origin",
    // Empty body is fine — params live on the query string. Provide
    // a content-type so misconfigured intermediaries don't reject.
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: ""
  });
}

function dispatchCompleted(host, payload) {
  const doc = (host && host.ownerDocument) || document;
  doc.dispatchEvent(
    new CustomEvent("wavy-folder-action-completed", {
      bubbles: true,
      composed: true,
      detail: payload
    })
  );
  // Also fire `wavy-search-refresh-requested` so the search rail
  // re-runs the active query and the digest list reflects the new
  // pinned/archived state immediately. The J2CL search panel view
  // already subscribes to this event (see
  // J2clSearchPanelView.java:545 — "wavy-search-refresh-requested"
  // re-fetches the active query). Mirrors the GWT
  // FolderActionListener behavior of repainting the digest list
  // after a folder write.
  const rail = doc.querySelector("wavy-search-rail");
  const refreshTarget = rail || doc;
  refreshTarget.dispatchEvent(
    new CustomEvent("wavy-search-refresh-requested", {
      bubbles: true,
      composed: true,
      detail: { reason: "folder-action", ...payload }
    })
  );
}

function dispatchFailed(host, payload) {
  const target = (host && host.ownerDocument) || document;
  target.dispatchEvent(
    new CustomEvent("wavy-folder-action-failed", {
      bubbles: true,
      composed: true,
      detail: payload
    })
  );
}

function onArchiveToggle(event) {
  const host = event.currentTarget;
  const waveId = readWaveId(event);
  if (!waveId) return;
  resetFolderStateIfWaveChanged(host, waveId);
  if (isBusyForWave(host, waveId)) return;
  const wasArchived = readBoolAttr(host, "archived");
  const folder = wasArchived ? "inbox" : "archive";
  // Optimistic flip immediately — matches GWT setDown(true) feel.
  setBoolAttr(host, "archived", !wasArchived);
  setBusy(host, true, waveId);
  const url = buildFolderUrl({
    operation: "move",
    folder,
    waveId
  });
  fetchFolderOp(url).then(
    (response) => {
      setBusy(host, false, waveId);
      if (!isCurrentWave(host, waveId)) return;
      if (response.ok) {
        dispatchCompleted(host, {
          waveId,
          operation: "move",
          folder
        });
      } else {
        // Rollback on non-200.
        setBoolAttr(host, "archived", wasArchived);
        dispatchFailed(host, {
          waveId,
          operation: "move",
          folder,
          status: response.status
        });
      }
    },
    (err) => {
      setBusy(host, false, waveId);
      if (!isCurrentWave(host, waveId)) return;
      setBoolAttr(host, "archived", wasArchived);
      dispatchFailed(host, {
        waveId,
        operation: "move",
        folder,
        error: String(err && err.message ? err.message : err)
      });
    }
  );
}

function onPinToggle(event) {
  const host = event.currentTarget;
  const waveId = readWaveId(event);
  if (!waveId) return;
  resetFolderStateIfWaveChanged(host, waveId);
  if (isBusyForWave(host, waveId)) return;
  const wasPinned = readBoolAttr(host, "pinned");
  const operation = wasPinned ? "unpin" : "pin";
  setBoolAttr(host, "pinned", !wasPinned);
  setBusy(host, true, waveId);
  const url = buildFolderUrl({ operation, waveId });
  fetchFolderOp(url).then(
    (response) => {
      setBusy(host, false, waveId);
      if (!isCurrentWave(host, waveId)) return;
      if (response.ok) {
        dispatchCompleted(host, { waveId, operation, folder: null });
      } else {
        setBoolAttr(host, "pinned", wasPinned);
        dispatchFailed(host, {
          waveId,
          operation,
          folder: null,
          status: response.status
        });
      }
    },
    (err) => {
      setBusy(host, false, waveId);
      if (!isCurrentWave(host, waveId)) return;
      setBoolAttr(host, "pinned", wasPinned);
      dispatchFailed(host, {
        waveId,
        operation,
        folder: null,
        error: String(err && err.message ? err.message : err)
      });
    }
  );
}

function onVersionHistoryRequest(event) {
  const host = event.currentTarget;
  const doc = (host && host.ownerDocument) || document;
  const overlay = doc.querySelector(VERSION_HISTORY_TAG.toLowerCase());
  if (!overlay) return;
  // Open the overlay. The element reflects to the `open` attribute
  // and removes `hidden` in its _syncOpen. The overlay's own keydown
  // handler closes it on Esc and restores focus.
  try {
    overlay.open = true;
  } catch (_e) {
    overlay.setAttribute("open", "");
    overlay.removeAttribute("hidden");
  }
}

export function start() {
  if (installed) return;
  if (typeof document === "undefined") return;
  installed = true;
  // Bind any nav-rows already in the DOM at install time.
  scanFor(document);
  // Watch for nav-rows added or removed as the J2CL renderer cycles.
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (!mutation.addedNodes) continue;
      mutation.addedNodes.forEach((node) => scanFor(node));
    }
  });
  try {
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true
    });
  } catch (_e) {
    // jsdom or other observer failures: fall back to one-shot scan.
  }
}

export function stop() {
  if (!installed) return;
  installed = false;
  if (observer) {
    observer.disconnect();
    observer = null;
  }
  for (const row of boundRows) {
    row.removeAttribute(ATTR_BOUND);
    row.removeEventListener("wave-nav-archive-toggle-requested", onArchiveToggle);
    row.removeEventListener("wave-nav-pin-toggle-requested", onPinToggle);
    row.removeEventListener("wave-nav-version-history-requested", onVersionHistoryRequest);
  }
  boundRows.clear();
}

// Auto-start on import in real browsers. Tests that want to control
// timing can import { start, stop } and skip this.
if (
  typeof document !== "undefined" &&
  typeof window !== "undefined" &&
  !window.__G_PORT_8_DISABLE_AUTOSTART
) {
  // Defer to next microtask so element registrations can complete
  // before we scan.
  Promise.resolve().then(() => start());
}
