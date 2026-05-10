// Catalog registry for the J2CL Lit shell.
//
// esbuild inlines JSON imports at build time, so each locale ships as part
// of the shell.js bundle. New locales are added by dropping `xx.json` next
// to the existing catalogs and importing it here. See ./README.md.

import { en } from "./catalogs/en.js";
import { de } from "./catalogs/de.js";

const CATALOGS = Object.freeze({ en, de });

export function lookup(locale, key) {
  const catalog = CATALOGS[locale];
  if (!catalog || typeof key !== "string") return undefined;
  const value = catalog[key];
  return typeof value === "string" ? value : undefined;
}

export function hasLocale(locale) {
  return Object.prototype.hasOwnProperty.call(CATALOGS, locale);
}
