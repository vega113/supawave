import { svg } from "lit";

/*
 * V-3 (#1101) icon set for the J2CL format toolbar. Each entry is a
 * Lit `svg`-tagged template authored on a 16x16 viewBox so the parent
 * 32x32 tile keeps an 8px halo around the glyph. All strokes / fills
 * use `currentColor` so the consuming `<button>`'s color flips them
 * via aria-pressed / hover.
 */
const ICON_TEMPLATES = {
  bold: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M4 2.5h4.5a2.5 2.5 0 0 1 0 5H4z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
    <path d="M4 7.5h5.25a2.75 2.75 0 0 1 0 5.5H4z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
  </svg>`,
  italic: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="6.5" y1="2.5" x2="12" y2="2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <line x1="4" y1="13.5" x2="9.5" y2="13.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <line x1="9.5" y1="2.5" x2="6.5" y2="13.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
  </svg>`,
  underline: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M4 2.5v6a4 4 0 0 0 8 0v-6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <line x1="3" y1="13.5" x2="13" y2="13.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
  </svg>`,
  strikethrough: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M11.5 4.5a3 3 0 0 0-3-2h-1a2.5 2.5 0 0 0-1.5 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M5 11a3 3 0 0 0 3 2.5h.5a2.75 2.75 0 0 0 2.5-3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <line x1="2.5" y1="8" x2="13.5" y2="8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
  </svg>`,
  superscript: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M3 11.5l5-7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M3 4.5l5 7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M10.5 3.25h2.75l-2.75 3.5h2.9" stroke="currentColor" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`,
  subscript: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M3 8.5l5-7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M3 1.5l5 7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M10.5 10.25h2.75l-2.75 3.5h2.9" stroke="currentColor" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`,
  heading: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="3.5" y1="2.5" x2="3.5" y2="13.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <line x1="12.5" y1="2.5" x2="12.5" y2="13.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <line x1="3.5" y1="8" x2="12.5" y2="8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
  </svg>`,
  "unordered-list": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <circle cx="3" cy="4" r="1.1" fill="currentColor"/>
    <circle cx="3" cy="8" r="1.1" fill="currentColor"/>
    <circle cx="3" cy="12" r="1.1" fill="currentColor"/>
    <line x1="6" y1="4" x2="13.5" y2="4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6" y1="8" x2="13.5" y2="8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6" y1="12" x2="13.5" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  "ordered-list": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <text x="1" y="6" font-size="4.5" font-family="sans-serif" font-weight="700" fill="currentColor">1.</text>
    <text x="1" y="10" font-size="4.5" font-family="sans-serif" font-weight="700" fill="currentColor">2.</text>
    <text x="1" y="14" font-size="4.5" font-family="sans-serif" font-weight="700" fill="currentColor">3.</text>
    <line x1="6" y1="4" x2="13.5" y2="4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6" y1="8" x2="13.5" y2="8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6" y1="12" x2="13.5" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  indent: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="2.5" y1="3" x2="13.5" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6.5" y1="6.5" x2="13.5" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6.5" y1="10" x2="13.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="13.5" x2="13.5" y2="13.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <path d="M2 6.5l2.5 1.75L2 10z" fill="currentColor"/>
  </svg>`,
  outdent: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="2.5" y1="3" x2="13.5" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6.5" y1="6.5" x2="13.5" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="6.5" y1="10" x2="13.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="13.5" x2="13.5" y2="13.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <path d="M5 6.5l-2.5 1.75L5 10z" fill="currentColor"/>
  </svg>`,
  "align-left": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="2.5" y1="3" x2="13.5" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="6.5" x2="9" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="10" x2="13.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="13.5" x2="9" y2="13.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  "align-center": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="2.5" y1="3" x2="13.5" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="4.5" y1="6.5" x2="11.5" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="10" x2="13.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="4.5" y1="13.5" x2="11.5" y2="13.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  "align-right": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <line x1="2.5" y1="3" x2="13.5" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="7" y1="6.5" x2="13.5" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="10" x2="13.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="7" y1="13.5" x2="13.5" y2="13.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  rtl: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M11.5 3.5h-4a2.5 2.5 0 0 0 0 5h.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
    <line x1="8.5" y1="3.5" x2="8.5" y2="13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="11.5" y1="3.5" x2="11.5" y2="13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <path d="M5 10.5l-2 1.5 2 1.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
    <line x1="3" y1="12" x2="6.5" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  link: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M7 9.5L9.5 7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M9 5l1-1a2.8 2.8 0 0 1 4 4l-1 1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M7 11l-1 1a2.8 2.8 0 0 1-4-4l1-1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`,
  unlink: svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M9 5l1-1a2.8 2.8 0 0 1 4 4l-1 1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M7 11l-1 1a2.8 2.8 0 0 1-4-4l1-1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    <line x1="2.5" y1="13.5" x2="13.5" y2="2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
  </svg>`,
  "clear-formatting": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M3 13.5l4-11h2l4 11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    <line x1="4.5" y1="9.5" x2="11.5" y2="9.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
    <line x1="2.5" y1="13.5" x2="13.5" y2="2.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
  </svg>`,
  "insert-task": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <rect x="2.5" y="2.5" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.5"/>
    <path d="M5 8l2.25 2.25L11 6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`,
  "text-color": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M4 13.5h8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
    <path d="M5.25 11.25L8 2.5l2.75 8.75" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M6.5 8.25h3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
    <path d="M3.5 15h9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
  </svg>`,
  "highlight-color": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M10.75 2.5l3 3-6.25 6.25-3.75.75.75-3.75 6.25-6.25z" stroke="currentColor" stroke-width="1.35" stroke-linejoin="round"/>
    <path d="M9.5 3.75l3 3" stroke="currentColor" stroke-width="1.35" stroke-linecap="round"/>
    <path d="M2.5 14h11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  "attachment-insert": svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none">
    <path d="M11.5 4l-5.5 5.5a2.4 2.4 0 0 0 3.4 3.4l6.1-6.1a4 4 0 0 0-5.66-5.66l-6.1 6.1a5.6 5.6 0 0 0 7.92 7.92l5.5-5.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`
};

export function getToolbarIcon(actionId) {
  return ICON_TEMPLATES[actionId] || null;
}

export const TOOLBAR_ICON_IDS = Object.keys(ICON_TEMPLATES);
