import { svg } from "lit";

/*
 * G-PORT-8 (#1117) — top-of-wave action bar icon set. Cloned 1-to-1
 * from the GWT toolbar at
 *   wave/src/main/java/.../wavepanel/impl/toolbar/ViewToolbar.java
 * (constants ICON_RECENT, ICON_NEXT_UNREAD, ICON_PREV, ICON_NEXT,
 * ICON_LAST, ICON_PREV_MENTION, ICON_NEXT_MENTION, ICON_ARCHIVE,
 * ICON_PIN, ICON_HISTORY).
 *
 * Each glyph is authored on a 24-unit viewBox with 1.75 stroke,
 * round caps + joins (matching GWT's SVG_OPEN constant). The
 * consuming `<button>` colors them via `currentColor`, so
 * aria-pressed / hover flips the visual without a per-icon variant.
 */
const ICONS = {
  recent: svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="9"></circle>
    <path d="M12 7v5l-3 2"></path>
  </svg>`,
  "next-unread": svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M9 7l6 5-6 5"></path>
    <circle class="toolbar-accent-dot" cx="18" cy="12" r="2.25" stroke="none" fill="currentColor"></circle>
  </svg>`,
  previous: svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M18 15l-6-6-6 6"></path>
  </svg>`,
  next: svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M6 9l6 6 6-6"></path>
  </svg>`,
  end: svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M7 13l5 5 5-5"></path>
    <path d="M7 6l5 5 5-5"></path>
  </svg>`,
  "prev-mention": svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="4"></circle>
    <path d="M16 8v5a3 3 0 0 0 6 0V12a10 10 0 1 0-4 8"></path>
    <path d="M6 12H2"></path>
    <path d="M5 9l-3 3 3 3"></path>
  </svg>`,
  "next-mention": svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="4"></circle>
    <path d="M16 8v5a3 3 0 0 0 6 0V12a10 10 0 1 0-4 8"></path>
    <path d="M18 12h4"></path>
    <path d="M19 9l3 3-3 3"></path>
  </svg>`,
  archive: svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <rect x="2" y="3" width="20" height="5" rx="1"></rect>
    <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"></path>
    <path d="M10 12h4"></path>
  </svg>`,
  pin: svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <line x1="12" y1="17" x2="12" y2="22"></line>
    <path d="M5 17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6h1a2 2 0 000-4H8a2 2 0 000 4h1v4.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24z"></path>
  </svg>`,
  "version-history": svg`<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M3 3v5h5"></path>
    <path d="M3.05 13a9 9 0 1 0 2.64-6.36L3 8"></path>
    <path d="M12 7v5l3 2"></path>
  </svg>`
};

export function getWaveActionIcon(actionId) {
  return ICONS[actionId] || null;
}

export const WAVE_ACTION_ICON_IDS = Object.keys(ICONS);
