import { svg } from "lit";

/*
 * G-PORT-2 (#1111) icon set for the J2CL <wavy-search-rail> action
 * row. The glyphs mirror the GWT lucide-style icons used by
 * SearchPresenter (ICON_REFRESH, ICON_MODIFY) plus a sort icon for the
 * visible "sort" affordance the GWT inventory now exposes via the
 * orderby: search tokens. All glyphs are 16x16, currentColor stroked,
 * 1.6px line, rounded caps/joins so they read at the same weight as
 * the format-toolbar icons under V-3.
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
