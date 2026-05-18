import { svg } from "lit";

/*
 * G-PORT-2 (#1111, updated #1253) icon set for the J2CL
 * <wavy-search-rail> compact action toolbar. Includes: refresh,
 * new-wave (compose), manage-saved-searches (sliders), and folder
 * navigation icons (inbox, mentions, tasks, public, archive, pinned).
 * Sort/filter icons are retained as exports for backward compatibility
 * but are no longer rendered in the toolbar — sorting is handled via
 * query tokens and filtering via the inline filter chip strip.
 * All glyphs are 16x16, currentColor stroked, 1.6px line, rounded
 * caps/joins so they read at the same weight as the format-toolbar
 * icons under V-3.
 */
export const SEARCH_RAIL_ICON_REFRESH = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <polyline points="14.5 3 14.5 6.5 11 6.5"></polyline>
  <path d="M13.1 9.5a5.5 5.5 0 1 1-1.3-5.7l2.7 2.7"></path>
</svg>`;

export const SEARCH_RAIL_ICON_SORT = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <line x1="4" y1="3" x2="4" y2="13"></line>
  <polyline points="2 11 4 13 6 11"></polyline>
  <line x1="11" y1="13" x2="11" y2="3"></line>
  <polyline points="9 5 11 3 13 5"></polyline>
</svg>`;

export const SEARCH_RAIL_ICON_FILTER = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <polygon points="2 3 14 3 9.5 8.5 9.5 13 6.5 11.5 6.5 8.5"></polygon>
</svg>`;

export const SEARCH_RAIL_ICON_NEW_WAVE = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <path d="M7.3 2.8H3.2a1.4 1.4 0 0 0-1.4 1.4v8.6a1.4 1.4 0 0 0 1.4 1.4h8.6a1.4 1.4 0 0 0 1.4-1.4V8.7"></path>
  <path d="M11.9 1.8a1.5 1.5 0 0 1 2.1 2.1L8 9.9l-2.6.7.7-2.6 5.8-6.2z"></path>
</svg>`;

export const SEARCH_RAIL_ICON_MANAGE_SAVED = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <line x1="3.5" y1="14" x2="3.5" y2="9.5"></line>
  <line x1="3.5" y1="6.5" x2="3.5" y2="2"></line>
  <line x1="8" y1="14" x2="8" y2="8"></line>
  <line x1="8" y1="5" x2="8" y2="2"></line>
  <line x1="12.5" y1="14" x2="12.5" y2="10.5"></line>
  <line x1="12.5" y1="7.5" x2="12.5" y2="2"></line>
  <line x1="2" y1="9.5" x2="5" y2="9.5"></line>
  <line x1="6.5" y1="5" x2="9.5" y2="5"></line>
  <line x1="11" y1="10.5" x2="14" y2="10.5"></line>
</svg>`;

export const SEARCH_RAIL_ICON_INBOX = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <path d="M3.2 4.2h9.6l1.6 5.1v3.1a1.2 1.2 0 0 1-1.2 1.2H2.8a1.2 1.2 0 0 1-1.2-1.2V9.3l1.6-5.1z"></path>
  <path d="M1.8 9.3h3.7l1 1.5h3l1-1.5h3.7"></path>
</svg>`;

export const SEARCH_RAIL_ICON_MENTIONS = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <circle cx="8" cy="8" r="2.3"></circle>
  <path d="M10.3 5.7v3.1a1.8 1.8 0 0 0 3.6 0V8a5.9 5.9 0 1 0-2.2 4.6"></path>
</svg>`;

export const SEARCH_RAIL_ICON_TASKS = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <path d="M13.5 7.5v4.8a1.3 1.3 0 0 1-1.3 1.3H3.7a1.3 1.3 0 0 1-1.3-1.3V3.7a1.3 1.3 0 0 1 1.3-1.3h6"></path>
  <path d="M6 7.7l2 2 5.6-6"></path>
</svg>`;

export const SEARCH_RAIL_ICON_PUBLIC = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <circle cx="8" cy="8" r="6.3"></circle>
  <path d="M1.7 8h12.6"></path>
  <path d="M8 1.7c1.7 1.7 2.5 3.8 2.5 6.3s-.8 4.6-2.5 6.3C6.3 12.6 5.5 10.5 5.5 8S6.3 3.4 8 1.7z"></path>
</svg>`;

export const SEARCH_RAIL_ICON_ARCHIVE = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <path d="M2.5 5.5v7.2a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1V5.5"></path>
  <path d="M1.8 2.8h12.4v2.7H1.8z"></path>
  <path d="M6.5 8.2h3"></path>
</svg>`;

export const SEARCH_RAIL_ICON_PINNED = svg`<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
  <path d="M8 11v3.2"></path>
  <path d="M4.1 11h7.8V9.9a1.4 1.4 0 0 0-.8-1.2l-1-.5a1.4 1.4 0 0 1-.8-1.2V3.8h.6a1.1 1.1 0 0 0 0-2.2H6.1a1.1 1.1 0 0 0 0 2.2h.6V7a1.4 1.4 0 0 1-.8 1.2l-1 .5a1.4 1.4 0 0 0-.8 1.2V11z"></path>
</svg>`;
