import { getLocale } from "./locale.js";
import { lookup } from "./catalog.js";

const FALLBACK_LOCALE = "en";

// Look up a localized string, falling back to the English catalog and then
// the supplied English fallback. The fallback is mandatory so a missing key
// can never render as `undefined` in the UI.
export function t(key, fallback) {
  if (typeof fallback !== "string") {
    throw new Error(`t(${JSON.stringify(key)}): missing English fallback`);
  }
  const active = getLocale();
  if (active !== FALLBACK_LOCALE) {
    const hit = lookup(active, key);
    if (hit != null) return hit;
  }
  const en = lookup(FALLBACK_LOCALE, key);
  return en != null ? en : fallback;
}
