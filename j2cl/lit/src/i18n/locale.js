// Locale module for the J2CL Lit shell.
//
// The active locale is derived once on first access from
// `window.__bootstrap.session.locale` (emitted by J2clBootstrapServlet),
// falling back to `<html lang>` and finally to "en". Components that call
// `t()` should `subscribe()` from `connectedCallback` so they re-render
// when the user changes the locale via <wavy-header>.

export const SUPPORTED_LOCALES = ["en", "de", "es", "fr", "ru", "sl", "zh_TW"];
const DEFAULT_LOCALE = "en";

let currentLocale = null;
const listeners = new Set();

export function normalizeLocale(raw) {
  if (raw == null) return null;
  const trimmed = String(raw).trim();
  if (!trimmed) return null;
  // BCP-47 forms (de-DE, zh-TW) and Java forms (de_DE, zh_TW) both reduce to
  // a canonical "lang" or "lang_REGION" key. Match the GWT locale set: bare
  // language code unless it's zh_TW (the only region-qualified locale we ship).
  const parts = trimmed.replace(/-/g, "_").split("_");
  const lang = parts[0].toLowerCase();
  // For zh (Chinese), derive the region from the last subtag so that
  // zh-Hant-TW and zh_TW both normalize to zh_TW correctly.
  const lastPart = parts.length > 1 ? parts[parts.length - 1].toUpperCase() : "";
  const candidate = lastPart && lang === "zh" ? `${lang}_${lastPart}` : lang;
  return SUPPORTED_LOCALES.includes(candidate) ? candidate : null;
}

function readBootstrapLocale() {
  if (typeof window === "undefined") return null;
  const bootstrap = window.__bootstrap;
  if (!bootstrap || typeof bootstrap !== "object") return null;
  const session = bootstrap.session;
  if (!session || typeof session !== "object") return null;
  return normalizeLocale(session.locale);
}

function readDocumentLocale() {
  if (typeof document === "undefined" || !document.documentElement) return null;
  return normalizeLocale(document.documentElement.lang);
}

export function getLocale() {
  if (currentLocale) return currentLocale;
  currentLocale = readBootstrapLocale() || readDocumentLocale() || DEFAULT_LOCALE;
  return currentLocale;
}

export function setLocale(code) {
  const next = normalizeLocale(code) || DEFAULT_LOCALE;
  if (next === currentLocale) return next;
  currentLocale = next;
  for (const listener of listeners) {
    try {
      listener(next);
    } catch (_err) {
      // Listeners are best-effort; one component throwing must not stop others.
    }
  }
  return next;
}

export function subscribe(listener) {
  if (typeof listener !== "function") return () => {};
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

// Test-only: drop cached state so each unit test starts from a clean slate.
export function _resetLocaleForTesting() {
  currentLocale = null;
  listeners.clear();
}
