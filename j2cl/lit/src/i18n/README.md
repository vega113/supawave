# J2CL Lit shell — i18n

Lightweight i18n for the SupaWave J2CL Lit shell. Built for the
"objective-dewdney" PR (issue under tracking) to:

- Restore tooltip parity with the GWT side (every `<button>` carries
  both `aria-label` and `title`, audited at build time).
- Surface the user's preferred locale through a single primitive so
  wave-panel chrome localizes without ad-hoc per-component plumbing.

This is intentionally pre-MVP — no plurals, no ICU, no Intl.MessageFormat.
Add those when a real translator workflow lands.

## Files

| File                       | Role                                                                        |
| -------------------------- | --------------------------------------------------------------------------- |
| `locale.js`                | Reads `window.__bootstrap.session.locale`, normalizes, broadcasts changes.  |
| `t.js`                     | `t(key, fallback)` — looks up active → `en` → caller-supplied fallback.     |
| `catalog.js`               | Imports the per-locale catalog modules and exposes `lookup`/`hasLocale`.    |
| `button.js`                | `localizedButton({key, fallback})` — same-string `aria-label` + `title`.    |
| `catalogs/<code>.js`       | Per-locale string table. Plain ES module exporting `{ <code>: {...} }`.    |

## Adding a locale

1. Drop `catalogs/xx.js` next to the existing catalogs, exporting
   `export const xx = { ... }` with the same keys.
2. `import { xx } from "./catalogs/xx.js";` in `catalog.js` and add
   it to `CATALOGS`.
3. Add the locale to `WavyHeader.LOCALES` in
   `../elements/wavy-header.js` (label + ISO code, including any
   `_REGION` qualifier).
4. Add the same code to `SUPPORTED_LOCALES` in `locale.js`.
5. Run `npm --prefix j2cl/lit run audit:buttons` and `... test` to
   ensure no regressions.

## Key naming

Per-source-file namespaces, lowerCamel keys:

```
waveActions.addParticipant        // wavy-wave-header-actions.js
formatToolbar.bold                // wavy-format-toolbar.js
searchRail.refresh                // wavy-search-rail.js
common.cancel                     // shared
```

The `common.*` namespace covers strings used by ≥ 2 elements (Cancel,
Save, etc.).

## Lookup chain

`t(key, fallback)` returns:

1. `catalogs[<active>][key]` if present.
2. `catalogs.en[key]` if present (so a German catalog with a missing
   key still renders English instead of `undefined`).
3. The `fallback` argument (so a key absent from every catalog still
   renders the source-of-truth English the developer wrote inline).

The fallback is a required argument — `t()` throws when omitted to
keep accidental `undefined` strings out of the UI.

## Active locale

`getLocale()` resolves once, in this order:

1. `window.__bootstrap.session.locale` — the canonical server-supplied
   value, set by `J2clBootstrapServlet#getSessionJson` from the user's
   `HumanAccountData#getLocale()`.
2. `<html lang>` — keeps SSR-only previews sensible.
3. `"en"`.

`SUPPORTED_LOCALES` (en/de/es/fr/ru/sl/zh_TW) mirrors the GWT locale
set. Anything unknown silently falls through to step 2 / 3, never to
an unrecognized code.

`setLocale(code)` is called by `<wavy-header>`'s locale picker. It
notifies subscribers (components that called `subscribe()` from their
`connectedCallback`) so they can re-render. Components currently
re-rendering on locale change: `wavy-header`, `wavy-wave-header-actions`.
Add `subscribe(() => this.requestUpdate())` from any component that
uses `t()` and needs live re-render.

## Catalog seed strategy

The `de` catalog reuses GWT-side translations verbatim where a
semantically equivalent string already exists under
`wave/src/main/resources/.../*Messages_de.properties`. Lines that
reuse a GWT string are tagged with the source `.properties` filename
in a comment.

Net-new keys (the four wave-panel action buttons, the saved-search
dialog strings, etc.) are translated inline. Any string we are not
confident in is intentionally left in English with a `// TODO de:`
annotation rather than ship a wrong word — `t()`'s fallback chain
serves the English string in those slots.

## Long-term plan

The GWT side already ships full `*_de`, `*_es`, `*_fr`, `*_ru`,
`*_sl`, `*_zh_TW` `.properties` files. The intended end state is a
Maven `generate-resources` step that walks
`wave/src/main/resources/**/*Messages_*.properties` and emits the
corresponding `j2cl/lit/src/i18n/catalogs/*.js` so both UIs stay in
lockstep without manual translation drift. Until that exists, this
module is the J2CL-side source of truth.

## Build-time audit

`scripts/audit-buttons.mjs` is wired as `prebuild` so any `<button>`
that lacks both `aria-label` and `title` fails the build before
shipping. The audit walks `j2cl/lit/src/elements/*.js` and skips both
line and block comments to avoid false positives on JSDoc that
mentions `<button>` literally.
