import { EMPTY_SNAPSHOT } from "./lit-shell-input.js";

const ALLOWED_ROLES = new Set(["admin", "owner", "user"]);

function normalizeRole(raw) {
  return ALLOWED_ROLES.has(raw) ? raw : "user";
}

/**
 * Pre-#963 adapter: reads bootstrap data from inline script globals.
 * Post-#963, swap this for the JSON-based adapter at the import site in
 * src/index.js without touching any shell element.
 */
export function createInlineShellInput(win) {
  return {
    read() {
      const session = win.__session;
      const wsAddress =
        typeof win.__websocket_address === "string" ? win.__websocket_address : "";
      if (!session || typeof session !== "object") {
        return { ...EMPTY_SNAPSHOT, websocketAddress: wsAddress };
      }
      const address = typeof session.address === "string" ? session.address : "";
      const signedIn = address.length > 0;
      return {
        signedIn,
        address,
        role: normalizeRole(session.role),
        domain: typeof session.domain === "string" ? session.domain : "",
        idSeed: typeof session.idSeed === "string" ? session.idSeed : "",
        features: Array.isArray(session.features) ? [...session.features] : [],
        websocketAddress: wsAddress
      };
    }
  };
}
