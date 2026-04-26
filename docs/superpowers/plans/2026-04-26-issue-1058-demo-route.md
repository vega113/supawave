# F-2 Slice 6 plan — issue #1058

Closes #1037 (umbrella). Updates #904. References #1058.

## Bug — root cause analysis

Slice 5 (#1057) added `data-j2cl-legacy-search-card="hidden"` on the
`.sidecar-search-card` wrapper and a CSS rule:

```css
.sidecar-search-card[data-j2cl-legacy-search-card="hidden"] {
  display: contents;
}
```

`display: contents` removes the wrapper's box but **keeps every child
visible**. The wrapper still contains:

- `<form data-j2cl-legacy-search-form="true" hidden>` — hidden by attribute and
  a separate `display:none` rule, OK
- `<p class="sidecar-search-session" hidden>` — OK
- `<div class="sidecar-search-compose"></div>` — empty, OK
- `<p class="sidecar-search-status" hidden>` — OK
- `<p class="sidecar-wave-count" hidden>` — OK
- **`<div class="sidecar-digests"></div>`** — NOT hidden. Once
  `J2clSearchPanelView.render()` writes `J2clDigestView` items here on
  the first search response, they render in the legacy `sidecar-*`
  light styling — directly below the new dark wavy rail. **This is the
  duplicate the user reported.**
- **`<div class="sidecar-empty-state">Search results will appear here.</div>`** —
  NOT hidden. Renders the light empty-state copy as a duplicate before
  any result arrives. The empty-state then gets rewritten to "No waves
  matched this query." on the first empty response — still visible,
  still light.
- `<button class="sidecar-show-more" hidden>` — OK

S5 also marked these "hidden via marker" elements without proving
visual invisibility:

1. `data-j2cl-compose-host="true"` — the `.sidecar-selected-compose`
   div hosts the legacy editor toolbar wall. S5's renderer leaves it
   visible by default; J2CL un-hides only during edit. CSS-wise, no
   `display: none` rule exists keyed on the marker. The host is empty
   pre-edit so visually invisible — fine in practice but not asserted.
2. `data-j2cl-awareness-pill="true" hidden` — uses native `hidden`
   attribute, OK.
3. The selected-wave card's "Select a wave" placeholder copy
   (`<p class="sidecar-selected-detail">Select a wave to open it here.</p>`)
   — by design, this is intentionally visible until a wave is opened
   (matches "Open a wave before replying" hint pattern). The wavy
   empty-state recipe inside `.sidecar-empty-state` already provides
   the wavy-styled version — but the legacy `.sidecar-selected-status`
   + `.sidecar-selected-detail` paragraphs still render alongside.
   This was intentional in S5 (status + detail belong to the no-wave
   chrome). Audit: keep them.

So the **single bug** is the visible adoption-target light children
(`sidecar-digests` + `sidecar-empty-state`) inside the
`display: contents` wrapper.

## Fix

### Part A — Visible-rail fix + audit + assertion tightening

1. **CSS fix** in `j2cl/lit/src/design/wavy-thread-collapse.css`:
   change `display: contents` → `display: none !important` on the
   `.sidecar-search-card[data-j2cl-legacy-search-card="hidden"]`
   selector. The legacy panel adoption still works because:
   - `J2clSearchPanelView.queryRequired` uses `querySelector`, which
     finds elements regardless of computed visibility.
   - The view writes into `digestList.innerHTML = ""` and appends
     `J2clDigestView` elements as children — but these are inside the
     hidden wrapper, so they never paint. Today they paint directly
     under the wavy rail; after the fix, the wavy
     `<wavy-search-rail>` is the only visible search surface.

2. **Audit**: confirm the other "hidden via marker" elements from S5:
   - `[data-j2cl-compose-host="true"]` — empty by default, no rule
     needed. Add a defensive CSS `:empty { display: none }` so the
     div never inserts an unwanted gap before edit, and add an
     integration assertion that the host is empty + the marker is
     present.
   - `[data-j2cl-legacy-search-form="true"][hidden]` — already covered
     by the `[hidden]` global rule + the existing scoped CSS rule.
     Confirm via assertion.
   - `[data-j2cl-awareness-pill="true"][hidden]` — covered by `[hidden]`.

3. **Tighten assertions** in
   `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellIntegrationTest.java`:
   - Add a `testLegacySearchCardCssRuleHidesItVisibly` test that
     loads `wavy-thread-collapse.css` and asserts the rule string
     uses `display: none` (NOT `display: contents`).
   - Add a `testLegacyComposeHostStartsEmpty` test that asserts the
     compose host renders with no children pre-edit.

4. **Lit fixture** at `j2cl/lit/test/legacy-rail-hidden.test.js` —
   mounts a stub HTML structure that mirrors the SSR'd legacy card +
   `<wavy-search-rail>`, loads `wavy-thread-collapse.css`, and
   asserts `getComputedStyle(legacyCard).display === 'none'`.

### Part B — Demo route + final closeout

5. **Server-side demo route** at `?view=j2cl-root&q=read-surface-preview`:
   - In `WaveClientServlet`, route the `q=read-surface-preview`
     parameter to a new `HtmlRenderer.renderJ2clReadSurfacePreviewPage`
     method. Gating: signed-in only (no admin requirement — this is a
     review-friendly route). For signed-out, redirect to sign-in like
     the regular root shell does.
   - The new page mounts the full F-2 chrome surface with a fixture
     wave (5 blips, threaded, focus frame on b2, b3 collapsed,
     reactions on b1, mention chip on b3, task chip on b4, attachment
     tile on b5, version-history overlay open, profile overlay open
     for participant carol@example.com, depth-nav showing
     "Inbox > Sample wave > Top thread", awareness pill visible).
   - All existing wavy-* recipes render using the same SSR markup
     they emit on the regular root shell route, just with the fixture
     content pre-mounted.

6. **Runbook** at `docs/runbooks/j2cl-read-surface-preview.md`:
   - URL: `/?view=j2cl-root&q=read-surface-preview`
   - What each affordance demonstrates
   - Browser side-by-side checklist
   - Maintenance note: the fixture wave content lives in
     `HtmlRenderer.renderJ2clReadSurfacePreviewPage` only — no real
     WaveletProvider lookup, so reviewers always see the same content
     regardless of session.

7. **Final per-row parity roll-up** at
   `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneFinalParityTest.java`:
   - JUnit `@Suite` chaining the existing per-row parity test
     classes: `J2clStageOneReadSurfaceParityTest`,
     `J2clStageOneFloatingOverlaysParityTest`,
     `J2clSearchRailParityTest`, `J2clViewportFirstPaintParityTest`,
     and `HtmlRendererJ2clRootShellIntegrationTest`.
   - Adds one final summary test that asserts the demo route renders
     and that the row-coverage map covers every F-2 owned row from
     the parity matrix (R-3.1, R-3.2, R-3.3, R-3.4, R-3.7, R-4.1,
     R-4.2, R-4.3, R-4.5, R-4.6, R-4.7, R-6.1, R-6.2, R-6.3, R-6.4,
     R-7.1, R-7.2, R-7.3, R-7.4 — R-4.4 deferred to F-4 #1056).

## Verification

- `sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild`
- `sbt -batch jakartaTest:testOnly *HtmlRendererJ2clRootShellIntegrationTest *J2clStageOneFinalParityTest`
- Browser side-by-side at `?view=j2cl-root` (clean dark wavy chrome,
  zero light duplicate), `?view=j2cl-root&q=read-surface-preview`
  (full chrome surface mounted), `?view=gwt` (legacy unchanged).

## PR

- Title: `F-2 (slice 6): Demo route + integration polish + closeout`
- Body starts with: `Closes #1037. Updates #904. References #1058.`
- Auto-merge squash.
