# Pinned Waves UX — Design Spec

**Date:** 2026-04-07  
**Branch:** feat/pinned-waves-inbox-top  
**Approach:** Approach A — proto field + server supplement read

---

## Goals

1. In the plain `in:inbox` view (no `orderby:` modifier), pinned waves sort to the top. Within pinned waves, date-desc order is preserved. Non-pinned waves follow, also date-desc.
2. Each pinned wave in any search results list shows a small pin icon so users can visually identify it.
3. No behavior change when `orderby:` is present, or for `in:pinned` / `in:archive` queries.

---

## Architecture

Pin state already exists server-side: `SupplementedWaveImpl.PINNED_FOLDER = 9` in the user data wavelet (UDW). The server reads it today via `SimpleSearchProviderImpl.readPinnedStateFromUdw()` and already calls `promotePinnedWaves()` to reorder results. The gap is:

- `promotePinnedWaves()` fires even when `orderby:` is specified (requirement says skip it then)
- `isPinned` is never serialized into the `SearchResponse` proto, so the client always sees `false`
- `DigestDomImpl` has no pin icon element

The fix threads pin state all the way from the UDW supplement → proto wire format → GWT `DigestSnapshot` → `DigestDomImpl` rendering.

---

## Data Flow

```text
UDW (supplement folder 9)
  └── WaveDigester.generateDigest(supplement)
        supplement.isPinned()  [ReadableSupplementedWave]
  └── com.google.wave.api.SearchResult.Digest  [add pinned field]
  └── SearchServlet.serializeDigest()           [add setPinned()]
  └── SearchResponse.Digest proto               [add optional bool pinned = 9]
  └── JsoSearchBuilderImpl.deserializeDigest()  [pass pinned to DigestSnapshot]
  └── SearchService.DigestSnapshot              [add pinned field]
  └── DigestProxy.isPinned()                    [already delegates]
  └── SearchPanelRenderer.render()              [add setPinned() call]
  └── DigestDomImpl.setPinned()                 [show/hide pinIcon element]
```

Separately: `SimpleSearchProviderImpl` already calls `promotePinnedWaves()` correctly (pinned first, date-desc within group). Add one guard: skip promotion when `queryParams` contains `ORDERBY` token.

---

## File-by-File Changes

### Server (5 files)

**1. `wave/src/proto/proto/org/waveprotocol/box/search/search.proto`**
- Add `optional bool pinned = 9;` to `SearchResponse.Digest` message

**2. `wave/src/main/java/com/google/wave/api/SearchResult.java`**
- `Digest`: add `private final boolean pinned;`
- Extend constructor signature: add `boolean pinned` param (append to end for minimal impact)
- Add `isPinned()` getter returning the field

**3. `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java`**
- In `generateDigest(ObservableConversationView, SupplementedWave, ...)` call `supplement.isPinned()`
- Pass `pinned` to the `Digest` constructor

**4. `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java`**
- In `serializeDigest()`: add `digestBuilder.setPinned(searchResultDigest.isPinned())`

**5. `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`**
- Before the `promotePinnedWaves()` call, add:
  ```java
  boolean hasOrderBy = queryParams.containsKey(TokenQueryType.ORDERBY);
  if (!isPinnedQuery && !hasOrderBy) {
      sortedResults = promotePinnedWaves(...);
  }
  ```

### Client (7 files)

**6. `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchService.java`**
- `DigestSnapshot`: add `private final boolean pinned;`
- Extend constructor: append `boolean pinned`
- `isPinned()`: return the field (overrides stub in `Digest` interface)

**7. `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java`**
- In `deserializeDigest()`: pass `digest.getPinned()` as last arg to `DigestSnapshot` constructor

**8. `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestView.java`**
- Add `void setPinned(boolean pinned);`

**9. `wave/src/main/resources/org/waveprotocol/box/webclient/search/DigestDomImpl.ui.xml`**
- Inside `<div class='{css.inner}'>`, add:
  ```xml
  <span ui:field='pinIcon' class='{css.pinIcon}'>&#x1F4CC;</span>
  ```
  Position: after the `info` div (near timestamp area), or use SVG inline

**10. `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestDomImpl.java`**
- Add `@UiField Element pinIcon;`
- Implement `setPinned(boolean pinned)`:
  ```java
  pinIcon.getStyle().setDisplay(pinned ? Display.INLINE : Display.NONE);
  ```
  Direct style manipulation avoids GWT CSS obfuscation issues.

**11. CSS resource file (GWT CssResource)**
- Add initial hidden state: `.pinIcon { display: none; font-size: 11px; color: #888; margin-right: 4px; vertical-align: middle; }`
- Note: visibility toggled via inline style in Java, not CSS class

**12. `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelRenderer.java`**
- After existing `digestUi.setTimestamp(...)`, add `digestUi.setPinned(digest.isPinned());`

### Callers creating DigestSnapshot with old constructor (add `false` for pinned)

**13. `wave/src/main/java/org/waveprotocol/box/webclient/search/testing/FakeSearchService.java`**
- Pass `false` as `pinned` in `DigestSnapshot` construction

**14. `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`**
- `parseDigest()` method: pass `false` as `pinned`

**15. `wave/src/main/java/org/waveprotocol/box/webclient/search/SimpleSearch.java`**
- `DigestProxy` snapshot recreation (line ~104): pass `isPinned()` for the pinned param

---

## Pin Icon Design

- Character: `📌` (U+1F4CC) or a small SVG thumbtack
- Preferred: SVG inline in ui.xml for crisp rendering at small size
- Size: 12px, color: `#888` (neutral gray, doesn't compete with unread teal badge)
- Positioned: in the `info` area (top-right, near timestamp), or prepended to title

SVG option:
```xml
<svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="#888">
  <path d="M16,12V4H17V2H7V4H8V12L6,14V16H11.2V22H12.8V16H18V14L16,12Z"/>
</svg>
```

---

## What Does NOT Change

- `WaveBasedDigest.isPinned()` — remains `false`; live-wave pin wiring is out of scope
- `in:pinned` query behavior — no change
- Lucene sort fields — no change
- Any `orderby:` query — promotion skipped, no sort change

---

## Build & Test

```bash
sbt compile         # verify no compilation errors
sbt test            # run unit tests
```

Key test targets: `SearchPanelRendererTest`, `SimpleSearchProviderImplTest` (if exists), `WaveDigesterTest`.

---

## Files Changed Summary (15 files)

| # | File | Change |
|---|------|--------|
| 1 | search.proto | add `pinned` field |
| 2 | SearchResult.java | add `pinned` to Digest |
| 3 | WaveDigester.java | read `supplement.isPinned()` |
| 4 | SearchServlet.java | serialize `pinned` to proto |
| 5 | SimpleSearchProviderImpl.java | guard promotion on no-orderby |
| 6 | SearchService.java (client) | add `pinned` to DigestSnapshot |
| 7 | JsoSearchBuilderImpl.java | pass `pinned` in deserialization |
| 8 | DigestView.java | add `setPinned()` |
| 9 | DigestDomImpl.ui.xml | add pin icon element |
| 10 | DigestDomImpl.java | implement `setPinned()` |
| 11 | digest.css (GWT CssResource) | add `.pinIcon` styles |
| 12 | SearchPanelRenderer.java | call `setPinned()` |
| 13 | FakeSearchService.java | `pinned=false` default |
| 14 | SearchPresenter.java | `pinned=false` in parseDigest |
| 15 | SimpleSearch.java | pass `isPinned()` in snapshot |
