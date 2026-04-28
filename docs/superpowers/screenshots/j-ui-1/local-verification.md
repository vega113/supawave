# J-UI-1 local-server verification

Date: 2026-04-28
Branch: claude/j2cl-ui-1
Server: dev mode, `sbt 'set ThisBuild / skipGwt := true' run` on `127.0.0.1:9898`
User: fresh registration `qa1@local.net` / password `secret123` (admin by virtue of
being the first registered account on a clean memory store).

## Steps performed

1. Built `j2clProductionBuild` and `j2clLitBuild`.
2. Started server with skipGwt=true (J2CL surface only).
3. Registered fresh user via `/auth/register` → `qa1@local.net`.
4. Signed in via `/auth/signin`.
5. Enabled `j2cl-search-rail-cards` flag globally via `POST /admin/flags`.
6. Navigated to `/?view=j2cl-root&q=in:inbox`.
7. Inspected DOM via JS console.
8. Clicked the rendered card to verify route update.

## Results

### SSR carries the flag attribute (flag ON)

```html
<wavy-search-rail query="in:inbox" data-active-folder="inbox" result-count=""
                  data-rail-cards-enabled="true">
```

Reciprocal: with the flag OFF the attribute is absent (default render path
verified pre-flag-flip).

### Cards project as wavy-search-rail-card direct children

```json
{
  "railFound": true,
  "railCardsAttr": "true",
  "cardCount": 1,
  "cardWaveIds": ["local.net/w+1f4vogi825404A"],
  "cardTitles": ["Welcome to SupaWave"],
  "workflowDigests": 0,
  "statusText": "Showing 1 result(s) for in:inbox."
}
```

`workflowDigests: 0` confirms the legacy `J2clDigestView` path does NOT also
render — there is no double rendering. The card is the only digest surface
when the flag is on.

### R-4.5: clicking the card routes the URL

After clicking the card's inner `<article>`:

```
location.href -> http://127.0.0.1:9898/?view=j2cl-root&q=in%3Ainbox&wave=local.net%2Fw%2B1f4vogi825404A
```

The `wave=` parameter was appended; reload at this URL re-hydrates the
selection (round-trip verified).

### GWT path unaffected

```
GET /?view=gwt → 200, no <wavy-search-rail or data-rail-cards-enabled markers
in the body (grep -c returned 0).
```

### Visual reference

`local-render-flag-on.png` (also saved as the screenshot in the PR body):
shows the rail with the "Welcome to SupaWave" wavy-search-rail-card mounted
inside it — avatar (`QA`), title, snippet, msg-count badge (6), unread badge
(8 cyan), timestamp ("2m ago"), and the right-side "Select a wave" empty
state since no wave is open yet.
