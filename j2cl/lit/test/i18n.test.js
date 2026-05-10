import { expect } from "@open-wc/testing";
import {
  getLocale,
  setLocale,
  subscribe,
  normalizeLocale,
  SHIPPED_LOCALES,
  SUPPORTED_LOCALES,
  _resetLocaleForTesting
} from "../src/i18n/locale.js";
import { t } from "../src/i18n/t.js";
import { lookup, hasLocale } from "../src/i18n/catalog.js";
import { en } from "../src/i18n/catalogs/en.js";
import { de } from "../src/i18n/catalogs/de.js";
import { localizedButton } from "../src/i18n/button.js";

describe("i18n / locale", () => {
  beforeEach(() => {
    _resetLocaleForTesting();
    delete window.__bootstrap;
    document.documentElement.lang = "";
  });

  afterEach(() => {
    _resetLocaleForTesting();
    delete window.__bootstrap;
    document.documentElement.lang = "";
  });

  it("normalizes BCP-47 + Java forms", () => {
    expect(normalizeLocale("de")).to.equal("de");
    expect(normalizeLocale("de-DE")).to.equal("de");
    expect(normalizeLocale("DE_de")).to.equal("de");
    expect(normalizeLocale("zh-TW")).to.equal("zh_TW");
    expect(normalizeLocale("zh_TW")).to.equal("zh_TW");
    expect(normalizeLocale("xx")).to.equal(null);
    expect(normalizeLocale(null)).to.equal(null);
    expect(normalizeLocale("")).to.equal(null);
  });

  it("getLocale reads from window.__bootstrap.session.locale", () => {
    window.__bootstrap = { session: { locale: "de_DE" } };
    expect(getLocale()).to.equal("de");
  });

  it("getLocale falls back to <html lang> then 'en'", () => {
    document.documentElement.lang = "de";
    expect(getLocale()).to.equal("de");

    _resetLocaleForTesting();
    document.documentElement.lang = "";
    expect(getLocale()).to.equal("en");
  });

  it("getLocale prefers __bootstrap.session.locale over <html lang>", () => {
    document.documentElement.lang = "fr";
    window.__bootstrap = { session: { locale: "de" } };
    expect(getLocale()).to.equal("de");
  });

  it("getLocale picks up __bootstrap that arrives AFTER first read", () => {
    expect(getLocale()).to.equal("en");
    window.__bootstrap = { session: { locale: "de" } };
    expect(getLocale()).to.equal("de");
  });

  it("manual setLocale is honored even after __bootstrap changes", () => {
    window.__bootstrap = { session: { locale: "de" } };
    expect(getLocale()).to.equal("de");
    setLocale("en");
    window.__bootstrap = { session: { locale: "de" } };
    expect(getLocale()).to.equal("en");
  });

  it("setLocale notifies subscribers (only on shipped-locale changes)", () => {
    const calls = [];
    subscribe((next) => calls.push(next));
    setLocale("de");
    setLocale("de"); // duplicate — no-op.
    setLocale("en");
    expect(calls).to.deep.equal(["de", "en"]);
  });

  it("subscribe returns an unsubscribe function", () => {
    let calls = 0;
    const unsubscribe = subscribe(() => calls++);
    setLocale("de");
    unsubscribe();
    setLocale("en");
    expect(calls).to.equal(1);
  });

  it("setLocale rejects locales without a registered catalog", () => {
    setLocale("de");
    expect(getLocale()).to.equal("de");
    setLocale("fr"); // SUPPORTED but not SHIPPED
    expect(getLocale()).to.equal("de");
  });

  it("setLocale(null) is a no-op rather than collapsing to en", () => {
    setLocale("de");
    setLocale(null);
    setLocale("");
    setLocale(undefined);
    expect(getLocale()).to.equal("de");
  });

  it("setLocale syncs <html lang> for CSS :lang() / a11y", () => {
    setLocale("zh_TW");
    // setLocale only commits to shipped locales — zh_TW is unshipped, so
    // <html lang> stays unchanged.
    expect(document.documentElement.lang).to.equal("");
    setLocale("de");
    expect(document.documentElement.lang).to.equal("de");
  });

  it("a throwing subscriber does not stop other subscribers", () => {
    let secondCalls = 0;
    subscribe(() => {
      throw new Error("boom");
    });
    subscribe(() => secondCalls++);
    setLocale("de");
    expect(secondCalls).to.equal(1);
  });

  it("SHIPPED_LOCALES is a subset of SUPPORTED_LOCALES", () => {
    for (const code of SHIPPED_LOCALES) {
      expect(SUPPORTED_LOCALES, `${code} must be in SUPPORTED`).to.include(code);
      expect(hasLocale(code), `${code} must have a catalog`).to.equal(true);
    }
  });
});

describe("i18n / catalog", () => {
  it("hasLocale reports en + de", () => {
    expect(hasLocale("en")).to.equal(true);
    expect(hasLocale("de")).to.equal(true);
    expect(hasLocale("xx")).to.equal(false);
  });

  it("lookup returns a string for known keys", () => {
    expect(lookup("en", "waveActions.addParticipant")).to.be.a("string");
    expect(lookup("de", "waveActions.addParticipant")).to.be.a("string");
  });

  it("lookup returns undefined for unknown keys", () => {
    expect(lookup("en", "nonsense.key")).to.equal(undefined);
    expect(lookup("xx", "any")).to.equal(undefined);
  });

  // Catalog parity gate: catch a regression where a contributor adds a
  // German translation but forgets the matching English seed (the
  // English catalog is the source of truth for what keys ship). The
  // reverse direction is not gated — it is by design that some German
  // keys are intentionally missing so they fall back to English.
  it("every de.js key has a matching en.js entry", () => {
    const missing = Object.keys(de).filter(
      (key) => !Object.prototype.hasOwnProperty.call(en, key)
    );
    expect(missing, `de keys without en counterparts: ${missing.join(", ")}`)
      .to.deep.equal([]);
  });

  it("no key in en or de renders as the empty string", () => {
    for (const [k, v] of Object.entries(en)) {
      expect(v, `en.${k} must not be empty`).to.be.a("string").and.to.have.length.greaterThan(0);
    }
    for (const [k, v] of Object.entries(de)) {
      expect(v, `de.${k} must not be empty`).to.be.a("string").and.to.have.length.greaterThan(0);
    }
  });
});

describe("i18n / t", () => {
  beforeEach(() => {
    _resetLocaleForTesting();
    delete window.__bootstrap;
    document.documentElement.lang = "";
  });

  afterEach(() => {
    _resetLocaleForTesting();
  });

  it("returns the active-locale value when present", () => {
    setLocale("de");
    expect(t("waveActions.addParticipant", "FALLBACK")).to.contain("Teilnehmer");
  });

  it("falls back to en when the active locale lacks the key", () => {
    setLocale("de");
    // `scrollToNew.newSuffix` is seeded only in en.js (the German fallback
    // chain serves the en value because the German UI uses a wholly
    // different sentence). Asserting its presence in en and absence in
    // de makes the en-fallback path observable in the test rather than
    // implicit.
    expect(en["scrollToNew.newSuffix"]).to.equal("new");
    expect(de["scrollToNew.newSuffix"]).to.equal(undefined);
    expect(t("scrollToNew.newSuffix", "FALLBACK")).to.equal("new");
  });

  it("falls back to the supplied English when the key is absent everywhere", () => {
    setLocale("de");
    expect(t("zzz.absent.key", "fallbackString")).to.equal("fallbackString");
  });

  it("throws when the fallback is missing", () => {
    expect(() => t("anything")).to.throw();
  });
});

describe("i18n / localizedButton", () => {
  beforeEach(() => {
    _resetLocaleForTesting();
    setLocale("en");
  });

  afterEach(() => {
    _resetLocaleForTesting();
  });

  it("returns matching label / ariaLabel / title", () => {
    const out = localizedButton({
      key: "waveActions.addParticipant",
      fallback: "Add participant"
    });
    expect(out.label).to.be.a("string");
    expect(out.label).to.equal(out.ariaLabel);
    expect(out.label).to.equal(out.title);
  });
});
