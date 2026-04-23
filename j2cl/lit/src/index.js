// Entry point for the SupaWave Lit shell bundle (issue #964).
import { createInlineShellInput } from "./input/inline-shell-input.js";
import { createJsonShellInput } from "./input/json-shell-input.js";
import "./elements/shell-skip-link.js";
import "./elements/shell-header.js";
import "./elements/shell-nav-rail.js";
import "./elements/shell-main-region.js";
import "./elements/shell-status-strip.js";
import "./elements/shell-root.js";
import "./elements/shell-root-signed-out.js";

window.__litShellInput =
  window.__bootstrap && typeof window.__bootstrap === "object"
    ? createJsonShellInput(window)
    : createInlineShellInput(window);
