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
import "./elements/composer-submit-affordance.js";
import "./elements/composer-inline-reply.js";
import "./elements/composer-shell.js";
import "./elements/toolbar-button.js";
import "./elements/toolbar-group.js";
import "./elements/toolbar-overflow-menu.js";
import "./elements/compose-attachment-picker.js";
import "./elements/compose-attachment-card.js";
import "./elements/interaction-overlay-layer.js";
import "./elements/mention-suggestion-popover.js";
import "./elements/task-metadata-popover.js";
import "./elements/reaction-row.js";
import "./elements/reaction-picker-popover.js";
import "./elements/reaction-authors-popover.js";

window.__litShellInput =
  window.__bootstrap && typeof window.__bootstrap === "object"
    ? createJsonShellInput(window)
    : createInlineShellInput(window);
