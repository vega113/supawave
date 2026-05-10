import { t } from "./t.js";

// Helper for icon-only buttons that need both `aria-label` (for screen
// readers) and `title` (for hover tooltips). Routing both through a single
// helper guarantees they cannot drift, and forces every visible-regression
// affordance through the i18n primitive.
//
// Returns `{ label, ariaLabel, title }` where `label === ariaLabel === title`.
// Components spread the relevant subset onto the rendered <button>.
export function localizedButton({ key, fallback }) {
  const label = t(key, fallback);
  return { label, ariaLabel: label, title: label };
}
