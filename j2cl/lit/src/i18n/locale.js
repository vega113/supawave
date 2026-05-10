// Locale module for the J2CL Lit shell.
//
// The active locale is derived once on first access from
// `window.__bootstrap.session.locale` (emitted by J2clBootstrapServlet),
// falling back to `<html lang>` and finally to "en". Components that call
// `t()` should `subscribe()` from `connectedCallback` so they re-render
// when the user changes the locale via <wavy-header>.

// SUPPORTED_LOCALES is the GWT locale set — what the picker may *advertise*.
// SHIPPED_LOCALES is the subset we actually have a JS catalog for. setLocale
// only accepts shipped locales so a bogus picker value can never strand the UI
// in a state where every t() call falls through to the English fallback.
export const SUPPORTED_LOCALES = ["en", "de", "es", "fr", "ru", "sl", "zh_TW"];
export const SHIPPED_LOCALES = ["en", "de"];
const DEFAULT_LOCALE = "en";

let currentLocale = null;
let manualOverride = false;
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
  // While the user has not explicitly picked a locale via setLocale(), keep
  // re-reading the bootstrap surface on every call. This means a deferred
  // <script> that populates window.__bootstrap *after* the first <wavy-...>
  // upgrade is still picked up by subsequent t() calls. Once a manual
  // override has happened, we honor it without re-checking the bootstrap.
  if (manualOverride && currentLocale) return currentLocale;
  const bootstrap = readBootstrapLocale();
  if (bootstrap) {
    currentLocale = bootstrap;
    return currentLocale;
  }
  if (!currentLocale) {
    currentLocale = readDocumentLocale() || DEFAULT_LOCALE;
  }
  return currentLocale;
}

export function setLocale(code) {
  const normalized = normalizeLocale(code);
  if (!normalized) return currentLocale || DEFAULT_LOCALE;
  // Only commit to a locale we actually have a catalog for; otherwise every
  // t() call would silently fall through to the English fallback. Returning
  // the current (or default) locale keeps the picker rerender deterministic.
  if (!SHIPPED_LOCALES.includes(normalized)) {
    return currentLocale || DEFAULT_LOCALE;
  }
  manualOverride = true;
  if (normalized === currentLocale) return normalized;
  currentLocale = normalized;
  if (typeof document !== "undefined" && document.documentElement) {
    // Keep <html lang> in sync so CSS :lang() and assistive tech see the
    // same locale the i18n module is using.
    document.documentElement.lang = normalized.replace("_", "-");
  }
  for (const listener of listeners) {
    try {
      listener(normalized);
    } catch (err) {
      console.warn("i18n locale listener threw:", err);
    }
  }
  return normalized;
}

export function subscribe(listener) {
  if (typeof listener !== "function") return () => {};
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

// Test-only: drop cached state so each unit test starts from a clean slate.
// Also clears the side-effects setLocale writes into the document/window so
// downstream tests cannot inherit a stale locale from an earlier test.
export function _resetLocaleForTesting() {
  currentLocale = null;
  manualOverride = false;
  listeners.clear();
  if (typeof document !== "undefined" && document.documentElement) {
    document.documentElement.lang = "";
  }
  if (typeof window !== "undefined") {
    delete window.__bootstrap;
  }
}
