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
// F-0 (#1035): wavy design recipe elements. CSS tokens are loaded
// separately via <link rel="stylesheet" href="…wavy-tokens.css"> by
// the server template (renderJ2clRootShellPage and the design preview
// route) so esbuild does NOT inline the CSS into shell.css.
import "./design/wavy-blip-card.js";
import "./design/wavy-compose-card.js";
import "./design/wavy-rail-panel.js";
import "./design/wavy-edit-toolbar.js";
import "./design/wavy-depth-nav.js";
import "./design/wavy-pulse-stage.js";
// F-2 (#1037): StageOne read-surface elements that wrap the F-0
// design recipes. The renderer mounts <wave-blip> per blip.
import "./elements/wave-blip-toolbar.js";
import "./elements/wave-blip.js";
// F-2 slice 2 (#1046): wave chrome — focus frame, wave nav row,
// depth nav bar. Mounted by J2clSelectedWaveView around the read
// surface; the focus frame is mounted by J2clReadSurfaceDomRenderer
// inside its host so the bounds-measurement and positioning ancestor
// converge on the same node.
import "./elements/wavy-focus-frame.js";
import "./elements/wavy-wave-nav-row.js";
import "./elements/wavy-depth-nav-bar.js";
// F-2 slice 3 (#1047): search rail + search-help modal + wavy header
// chrome. <wavy-search-help> mounts as a single document-level
// instance under <shell-root>; <wavy-search-rail> drives it via the
// wavy-search-help-toggle CustomEvent.
import "./elements/wavy-search-help.js";
import "./elements/wavy-search-rail-card.js";
import "./elements/wavy-search-rail.js";
import "./elements/wavy-header.js";
// F-2.S4 (#1048): floating + accessory controls + version-history
// overlay + profile-overlay scaffolding. Imports listed alphabetically
// so future slices can append without scanning.
import "./elements/wavy-back-to-inbox.js";
import "./elements/wavy-floating-scroll-to-new.js";
import "./elements/wavy-nav-drawer-toggle.js";
import "./elements/wavy-profile-overlay.js";
import "./elements/wavy-version-history.js";
import "./elements/wavy-wave-controls-toggle.js";
// F-3.S1 (#1038): inline rich-text composer foundation —
// <wavy-composer> mounts inline at the chosen reply position attached
// to a <wave-blip>; <wavy-format-toolbar> floats anchored to the active
// selection; <wavy-link-modal> handles H.17 Insert link;
// <wavy-tags-row> covers I.1-I.6 tag editing affordances;
// <wavy-wave-root-reply-trigger> covers J.1 click-to-reply.
import "./elements/wavy-composer.js";
import "./elements/wavy-format-toolbar.js";
import "./elements/wavy-link-modal.js";
import "./elements/wavy-tags-row.js";
import "./elements/wavy-wave-root-reply-trigger.js";
// F-3.S2 (#1038): mentions + tasks —
// <wavy-task-affordance> mounts on every <wave-blip> next to the toolbar;
// reuses the F-1 <task-metadata-popover> for owner + due-date editing.
// The mention popover (<mention-suggestion-popover>) is reused as-is
// from F-1 and consumed by <wavy-composer>'s @-trigger.
import "./elements/wavy-task-affordance.js";

window.__litShellInput =
  window.__bootstrap && typeof window.__bootstrap === "object"
    ? createJsonShellInput(window)
    : createInlineShellInput(window);
