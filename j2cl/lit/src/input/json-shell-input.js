import { EMPTY_SNAPSHOT } from "./lit-shell-input.js";

const ALLOWED_ROLES = new Set(["admin", "owner", "user"]);

function normalizeRole(raw) {
  return ALLOWED_ROLES.has(raw) ? raw : "user";
}

export function createJsonShellInput(win) {
  return {
    read() {
      const bootstrap = win.__bootstrap;
      if (!bootstrap || typeof bootstrap !== "object") {
        return EMPTY_SNAPSHOT;
      }
      const session = bootstrap.session || {};
      const socket = bootstrap.socket || {};
      const address = typeof session.address === "string" ? session.address : "";
      return {
        signedIn: address.length > 0,
        address,
        role: normalizeRole(session.role),
        domain: typeof session.domain === "string" ? session.domain : "",
        locale: typeof session.locale === "string" ? session.locale : "",
        // /bootstrap.json intentionally does not provide a client ID seed.
        idSeed: "",
        features: Array.isArray(session.features) ? [...session.features] : [],
        websocketAddress:
          typeof socket.address === "string"
            ? socket.address
            : typeof bootstrap.websocketAddress === "string"
              ? bootstrap.websocketAddress
              : ""
      };
    }
  };
}
