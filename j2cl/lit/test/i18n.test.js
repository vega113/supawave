import { expect } from "@open-wc/testing";
import {
  getLocale,
  setLocale,
  subscribe,
  normalizeLocale,
  _resetLocaleForTesting
} from "../src/i18n/locale.js";
import { t } from "../src/i18n/t.js";
import { lookup, hasLocale } from "../src/i18n/catalog.js";
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

  it("setLocale notifies subscribers", () => {
    const calls = [];
    subscribe((next) => calls.push(next));
    setLocale("de");
    setLocale("de"); // duplicate — no-op.
    setLocale("zh_TW");
    expect(calls).to.deep.equal(["de", "zh_TW"]);
  });

  it("subscribe returns an unsubscribe function", () => {
    let calls = 0;
    const unsubscribe = subscribe(() => calls++);
    setLocale("de");
    unsubscribe();
    setLocale("fr");
    expect(calls).to.equal(1);
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
    // scrollToNew.newSuffix is in en but intentionally absent from de.
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
