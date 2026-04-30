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
// Shared with J2clSelectedWaveView. Java owns model-published folder state;
// Lit writes this marker on initial bind and source-wave-id reuse, and Java
// overwrites it whenever it publishes authoritative folder state.
const ATTR_FOLDER_STATE_WAVE_ID = "data-folder-state-wave-id";
const ATTR_BUSY_WAVE_ID = "data-folder-busy-wave-id";

let installed = false;
let observer = null;
const boundRows = new Set();
const overlayBindings = new WeakMap();

function isNavRow(node) {
  return (
    node && node.nodeType === 1 && node.tagName === NAV_ROW_TAG
  );
}

function bindNavRow(row) {
  if (!row || boundRows.has(row)) return;
  boundRows.add(row);
  row.setAttribute(ATTR_BOUND, "");
  syncFolderStateForWave(row, readWaveIdFromHost(row));
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

function unbindNavRow(row) {
  if (!row || !boundRows.has(row)) return;
  row.removeAttribute(ATTR_BOUND);
  row.removeEventListener("wave-nav-archive-toggle-requested", onArchiveToggle);
  row.removeEventListener("wave-nav-pin-toggle-requested", onPinToggle);
  row.removeEventListener("wave-nav-version-history-requested", onVersionHistoryRequest);
  boundRows.delete(row);
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

function unbindFrom(root) {
  if (!root) return;
  if (isNavRow(root)) {
    unbindNavRow(root);
    return;
  }
  if (typeof root.querySelectorAll === "function") {
    const rows = root.querySelectorAll(NAV_ROW_TAG.toLowerCase());
    for (const row of rows) unbindNavRow(row);
  }
}

function readWaveIdFromHost(host) {
  if (!host || typeof host.getAttribute !== "function") return "";
  return host.getAttribute("source-wave-id") || "";
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

function hydrateFromDigest(host, waveId) {
  if (!waveId) return;
  const doc =
    (host && host.ownerDocument) ||
    (typeof document !== "undefined" ? document : null);
  if (!doc || typeof doc.querySelectorAll !== "function") return;
  const rail = doc.querySelector("wavy-search-rail");
  const activeFolder = rail ? rail.getAttribute("data-active-folder") : "";
  const cards = doc.querySelectorAll("wavy-search-rail-card");
  for (const card of cards) {
    if (card.getAttribute("data-wave-id") !== waveId) continue;
    // wavy-search-rail-card reflects `pinned` as a DOM attribute.
    // If the rail itself is showing in:pinned results, the active-folder
    // context is also authoritative for route-restored rows.
    if (card.hasAttribute("pinned") || activeFolder === "pinned") {
      host.setAttribute("pinned", "");
    }
    // `archived` is not yet wired on the card as a DOM attribute (#1055/S5).
    // Fall back to the rail's active-folder context: if the search rail is
    // showing in:archive results and this card is present, the wave is archived.
    if (card.hasAttribute("archived")) {
      host.setAttribute("archived", "");
    } else {
      if (activeFolder === "archive") {
        host.setAttribute("archived", "");
      }
    }
    return;
  }
  // No matching digest card: the wave was opened from route state without a
  // visible card in the current rail view. The active folder is the only
  // available authoritative context until full selected-wave folder state is
  // wired through #1055/S5; use it for folder-scoped route restoration.
  if (activeFolder === "pinned") {
    host.setAttribute("pinned", "");
  }
  if (activeFolder === "archive") {
    host.setAttribute("archived", "");
  }
}

function syncFolderStateForWave(host, waveId) {
  if (!host || typeof host.getAttribute !== "function") return;
  const current = host.getAttribute(ATTR_FOLDER_STATE_WAVE_ID);
  if (!waveId) {
    // No selected wave owns the row anymore, so clear any pending optimistic
    // affordance regardless of the previous busy-wave-id owner.
    setBusy(host, false);
    host.removeAttribute("pinned");
    host.removeAttribute("archived");
    host.removeAttribute(ATTR_FOLDER_STATE_WAVE_ID);
    return;
  }
  if (current === waveId) {
    // Java may stamp the new wave's marker synchronously before the
    // source-wave-id MutationObserver fires, so the `current != null` path
    // below never ran for the previous wave. Clear any stale busy owner now.
    const busyOwner = host.getAttribute(ATTR_BUSY_WAVE_ID);
    if (busyOwner && busyOwner !== waveId) setBusy(host, false, busyOwner);
    return;
  }
  if (current != null) {
    setBusy(host, false, current);
    // A mismatched marker means the visible folder flags still belong to the
    // previous wave or an optimistic request. Java-stamped model state updates
    // the marker to waveId before this observer flushes, so current === waveId
    // returns above and preserves authoritative model-published flags. After
    // clearing stale flags, fall through to stamp the new wave and re-seed
    // from rail digest as the only available pre-publish hint.
    host.removeAttribute("pinned");
    host.removeAttribute("archived");
  }
  host.setAttribute(ATTR_FOLDER_STATE_WAVE_ID, waveId);
  // Seed initial folder state from a matching search-rail digest card.
  // Handles the case where J2clSelectedWaveView.setNavRowFolderState
  // (#1055/S5) has not been called yet — a freshly opened already-pinned
  // or already-archived wave would otherwise derive the wrong toggle
  // direction on first click.
  hydrateFromDigest(host, waveId);
}

function buildFolderUrl(params) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== "") search.set(k, String(v));
  }
  return "/folder/?" + search.toString();
}

function parseSelectedWaveId(waveId) {
  if (typeof waveId !== "string") return null;
  const slash = waveId.indexOf("/");
  if (slash <= 0 || slash >= waveId.length - 1) return null;
  const waveDomain = waveId.slice(0, slash);
  const waveIdPart = waveId.slice(slash + 1);
  if (!waveDomain || !waveIdPart) return null;
  return {
    waveDomain,
    waveId: waveIdPart,
    waveletDomain: waveDomain,
    waveletId: "conv+root"
  };
}

function enc(segment) {
  return encodeURIComponent(segment);
}

function buildHistoryApiBase(parts) {
  return (
    "/history/" +
    enc(parts.waveDomain) +
    "/" +
    enc(parts.waveId) +
    "/" +
    enc(parts.waveletDomain) +
    "/" +
    enc(parts.waveletId)
  );
}

function buildHistoryUrl(apiBase, endpoint, params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value == null) continue;
    if (typeof value === "number" && !Number.isFinite(value)) continue;
    search.set(key, String(value));
  }
  const query = search.toString();
  return apiBase + endpoint + (query ? "?" + query : "");
}

async function fetchJson(url, init = {}) {
  const { headers = {}, ...rest } = init;
  const response = await fetch(url, {
    ...rest,
    credentials: "same-origin",
    headers: { accept: "application/json", ...headers }
  });
  if (!response.ok) {
    let text = "";
    try { text = await response.text(); } catch (_e) {}
    throw new Error(text || `Request failed with status ${response.status}`);
  }
  return await response.json();
}

function mapHistoryEntries(entries) {
  if (!Array.isArray(entries)) return [];
  return entries.map((entry, index) => {
    const version = Number(
      entry && entry.resultingVersion != null
        ? entry.resultingVersion
        : entry && entry.version != null
          ? entry.version
          : index
    );
    return {
      index,
      version,
      label: Number.isFinite(version) ? `v${version}` : `v${index}`,
      timestamp: entry && entry.timestamp != null ? String(entry.timestamp) : "",
      appliedAt: entry && entry.appliedAt != null ? Number(entry.appliedAt) : null,
      author: entry && entry.author ? String(entry.author) : "",
      opCount: entry && entry.opCount != null ? Number(entry.opCount) : 0
    };
  });
}

function versionNumberFromDetail(detail) {
  if (!detail || !detail.version) return null;
  const candidate =
    detail.version.version != null
      ? detail.version.version
      : detail.version.resultingVersion != null
        ? detail.version.resultingVersion
        : detail.version.index;
  const parsed = Number(candidate);
  return Number.isFinite(parsed) ? parsed : null;
}

function messageFromError(err, fallback) {
  if (!err) return fallback;
  if (typeof err.message === "string" && err.message) return err.message;
  const text = String(err);
  return text || fallback;
}

function resetOverlayForWave(overlay) {
  if (overlay && typeof overlay.resetForWave === "function") {
    overlay.resetForWave();
    return;
  }
  overlay.versions = [];
  overlay.value = 0;
  overlay.loading = false;
  overlay.error = "";
  overlay.snapshot = null;
  overlay.restoreStatus = "";
  overlay.restoreEnabled = false;
  overlay._loaderRan = false;
}

function unbindOverlay(overlay) {
  const binding = overlayBindings.get(overlay);
  if (!binding) return;
  overlay.removeEventListener("wavy-version-changed", binding.onVersionChanged);
  overlay.removeEventListener("wavy-version-restore-confirmed", binding.onRestoreConfirmed);
  overlayBindings.delete(overlay);
}

function configureVersionHistoryOverlay(overlay, host, waveId, apiBase) {
  unbindOverlay(overlay);
  resetOverlayForWave(overlay);
  overlay.versionLoader = async (start, end) => {
    const info = await fetchJson(buildHistoryUrl(apiBase, "/api/info"));
    const versions = await fetchJson(
      buildHistoryUrl(apiBase, "/api/history", { start, end })
    );
    const mapped = mapHistoryEntries(versions);
    if (isCurrentWave(host, waveId)) {
      overlay.restoreEnabled = !!(info && info.canRestore) && mapped.length > 0;
    }
    return mapped;
  };

  const onVersionChanged = async (event) => {
    const version = versionNumberFromDetail(event.detail);
    if (version == null || !isCurrentWave(host, waveId)) return;
    overlay.loading = true;
    overlay.error = "";
    try {
      const snapshot = await fetchJson(
        buildHistoryUrl(apiBase, "/api/snapshot", { version })
      );
      if (!isCurrentWave(host, waveId)) return;
      overlay.snapshot = snapshot;
      overlay.error = "";
    } catch (err) {
      if (isCurrentWave(host, waveId)) {
        overlay.error = messageFromError(err, "Unable to load version snapshot.");
      }
    } finally {
      if (isCurrentWave(host, waveId)) {
        overlay.loading = false;
      }
    }
  };

  const onRestoreConfirmed = async (event) => {
    const version = versionNumberFromDetail(event.detail);
    if (version == null || !overlay.restoreEnabled || !isCurrentWave(host, waveId)) return;
    overlay.restoreStatus = `Restoring version ${version}…`;
    overlay.error = "";
    try {
      const result = await fetchJson(
        buildHistoryUrl(apiBase, "/api/restore", { version }),
        {
          method: "POST",
          headers: { "content-type": "application/x-www-form-urlencoded" },
          body: ""
        }
      );
      if (!isCurrentWave(host, waveId)) return;
      const restoredToVersion = Number(
        result && result.restoredToVersion != null ? result.restoredToVersion : version
      );
      overlay.restoreStatus = `Version ${Number.isFinite(restoredToVersion) ? restoredToVersion : version} restored. Refreshing wave.`;
      host.dispatchEvent(
        new CustomEvent("wavy-selected-wave-refresh-requested", {
          bubbles: true,
          composed: true,
          detail: {
            waveId,
            reason: "version-restore",
            restoredToVersion: Number.isFinite(restoredToVersion) ? restoredToVersion : version
          }
        })
      );
    } catch (err) {
      if (isCurrentWave(host, waveId)) {
        overlay.restoreStatus = "";
        overlay.error = messageFromError(err, "Unable to restore version.");
      }
    }
  };

  overlay.addEventListener("wavy-version-changed", onVersionChanged);
  overlay.addEventListener("wavy-version-restore-confirmed", onRestoreConfirmed);
  overlayBindings.set(overlay, { onVersionChanged, onRestoreConfirmed });
}

function folderFetchTimeoutMs() {
  if (typeof window === "undefined") return 15_000;
  const override = window.__G_PORT_8_FOLDER_TIMEOUT_MS;
  return Number.isFinite(override) && override > 0 ? override : 15_000;
}

function fetchFolderOp(url, timeoutMs = folderFetchTimeoutMs()) {
  const supportsAbort = typeof AbortController !== "undefined";
  const controller = supportsAbort ? new AbortController() : null;
  const timer =
    supportsAbort && timeoutMs > 0
      ? setTimeout(() => controller.abort(), timeoutMs)
      : null;
  // Use fetch with method POST + same-origin credentials so the
  // session cookie ships. The servlet reads parameters via
  // request.getParameter (works for query string params on a POST).
  return fetch(url, {
    method: "POST",
    credentials: "same-origin",
    ...(controller ? { signal: controller.signal } : {}),
    // Empty body is fine — params live on the query string. Provide
    // a content-type so misconfigured intermediaries don't reject.
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: ""
  }).finally(() => {
    if (timer) clearTimeout(timer);
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
  syncFolderStateForWave(host, waveId);
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
      const currentWave = isCurrentWave(host, waveId);
      if (response.ok) {
        dispatchCompleted(host, {
          waveId,
          operation: "move",
          folder
        });
      } else {
        // Rollback row-local optimistic state only if this row still
        // represents the wave that initiated the request.
        if (currentWave) setBoolAttr(host, "archived", wasArchived);
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
      if (isCurrentWave(host, waveId)) {
        setBoolAttr(host, "archived", wasArchived);
      }
      dispatchFailed(host, {
        waveId,
        operation: "move",
        folder,
        status: 0,
        error: String(err && err.message ? err.message : err)
      });
    }
  );
}

function onPinToggle(event) {
  const host = event.currentTarget;
  const waveId = readWaveId(event);
  if (!waveId) return;
  syncFolderStateForWave(host, waveId);
  if (isBusyForWave(host, waveId)) return;
  const wasPinned = readBoolAttr(host, "pinned");
  const operation = wasPinned ? "unpin" : "pin";
  setBoolAttr(host, "pinned", !wasPinned);
  setBusy(host, true, waveId);
  const url = buildFolderUrl({ operation, waveId });
  fetchFolderOp(url).then(
    (response) => {
      setBusy(host, false, waveId);
      const currentWave = isCurrentWave(host, waveId);
      if (response.ok) {
        dispatchCompleted(host, { waveId, operation, folder: null });
      } else {
        if (currentWave) setBoolAttr(host, "pinned", wasPinned);
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
      if (isCurrentWave(host, waveId)) {
        setBoolAttr(host, "pinned", wasPinned);
      }
      dispatchFailed(host, {
        waveId,
        operation,
        folder: null,
        status: 0,
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
  const waveId = readWaveId(event);
  const parsed = parseSelectedWaveId(waveId);
  if (!parsed) return;
  configureVersionHistoryOverlay(overlay, host, waveId, buildHistoryApiBase(parsed));
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
  try {
    // Watch for nav-rows added or removed as the J2CL renderer cycles.
    observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        if (
          mutation.type === "attributes" &&
          mutation.attributeName === "source-wave-id" &&
          isNavRow(mutation.target)
        ) {
          syncFolderStateForWave(mutation.target, readWaveIdFromHost(mutation.target));
        }
        if (mutation.addedNodes) {
          mutation.addedNodes.forEach((node) => scanFor(node));
        }
        if (mutation.removedNodes) {
          mutation.removedNodes.forEach((node) => unbindFrom(node));
        }
      }
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["source-wave-id"],
      childList: true,
      subtree: true
    });
  } catch (_e) {
    observer = null;
    // MutationObserver construction or observe() failed; fall back to
    // one-shot scan already done above.
  }
}

export function stop() {
  if (!installed) return;
  installed = false;
  if (observer) {
    observer.disconnect();
    observer = null;
  }
  for (const row of Array.from(boundRows)) unbindNavRow(row);
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
