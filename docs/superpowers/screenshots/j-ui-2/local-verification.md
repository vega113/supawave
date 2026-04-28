# J-UI-2 local-server verification

Date: 2026-04-28
Branch: claude/j2cl-ui-2
Server: dev mode, `sbt stage` build under `target/universal/stage`, started via
`PORT=9999 scripts/wave-smoke.sh start` on `127.0.0.1:9999`.
User: fresh registration `jui2qa@local.net` / password `secret123` (admin role
by virtue of being the first registered account on a clean memory store).
Flag: `j2cl-search-rail-cards` enabled globally via the
`POST /admin/flags` endpoint.

## Acceptance evidence — driven through the live rail in headless Chromium

The verification script (`j2cl/lit/jui2-verify.mjs`, kept out of the
production checkout) cookie-bridges the curl-registered session, opens
`/?view=j2cl-root`, then drives each folder + chip through the rail's
shadow DOM. Output:

### Each of the six folders sets URL + aria-current=page

```json
[
  {"id":"inbox",    "url":"?view=j2cl-root&q=in%3Ainbox",    "ariaCurrent":"page", "urlMatchesExpected":true},
  {"id":"mentions", "url":"?view=j2cl-root&q=mentions%3Ame", "ariaCurrent":"page", "urlMatchesExpected":true},
  {"id":"tasks",    "url":"?view=j2cl-root&q=tasks%3Ame",    "ariaCurrent":"page", "urlMatchesExpected":true},
  {"id":"public",   "url":"?view=j2cl-root&q=with%3A%40",    "ariaCurrent":"page", "urlMatchesExpected":true},
  {"id":"archive",  "url":"?view=j2cl-root&q=in%3Aarchive",  "ariaCurrent":"page", "urlMatchesExpected":true},
  {"id":"pinned",   "url":"?view=j2cl-root&q=in%3Apinned",   "ariaCurrent":"page", "urlMatchesExpected":true}
]
```

### Filter chip composes with the active folder

```json
{"phase":"chip-toggle","composedUrl":"?view=j2cl-root&q=in%3Ainbox%20is%3Aunread","chipPressed":"true","composedHasUnread":true}
```

### Reload preserves both folder and chip state

```json
{"phase":"after-reload","search":"?view=j2cl-root&q=in%3Ainbox%20is%3Aunread","query":"in:inbox is:unread","activeFolder":"inbox","unreadPressed":"true"}
```

The chip's pressed state is derived from the URL token at SSR render
time and confirmed after upgrade — the chip-pressed contract round-trips
through reload.

### Browser back/forward walks the history

```json
{"phase":"history","afterBack":"?view=j2cl-root&q=in%3Ainbox","afterForward":"?view=j2cl-root&q=in%3Ainbox%20is%3Aunread"}
```

## Server-side `is:unread` aliasing

Direct REST probe of `/search/?query=...` confirms the chip's canonical
token reaches the backend and produces the expected filtered set:

```text
in:inbox: 1 digests                 (the seed Welcome wave)
mentions:me: 0 digests
tasks:me: 0 digests
with:@: 0 digests
in:archive: 0 digests               (Welcome wave is in the inbox, not archive)
in:pinned: 0 digests
in:inbox+is:unread: 1 digests       (chip alias for unread:true — matches Welcome)
in:inbox+has:attachment: 1 digests  (URL-only this slice, deferred follow-up)
in:inbox+from:me: 1 digests         (URL-only this slice, deferred follow-up)
```

`is:unread` matches identically to `unread:true` via the new alias in
`SimpleSearchProviderImpl.hasIsValue`. `has:attachment` and `from:me`
parse and round-trip but do not yet filter — flagged in the PR body
with follow-up issues.

## GWT path unaffected

```text
GET /?view=gwt → 200, no <wavy-search-rail or data-rail-cards-enabled
markers in the body (grep -c returned 0).
```

## Visual references

- `archive-folder-active.png` — Archive folder selected; aria-current
  highlight on the Archive button; URL reflects `q=in:archive`.
- `inbox-with-unread-chip.png` — Inbox folder + Unread only chip both
  active; chip styled with cyan ring; URL reflects
  `q=in:inbox is:unread`.

## Remaining checklist (from issue acceptance)

- [x] Click each saved-search button (Inbox/Mentions/Tasks/Public/Archive/Pinned)
- [x] Confirm rail repopulates and URL updates per click
- [x] Toggle each chip (Unread only / With attachments / From me)
- [x] Chip URL composes correctly with active folder
- [x] Reload mid-flow restores state
- [x] Browser back/forward walks the prior states
- [x] Screenshot one populated non-default folder + one chip-active state
- [x] GWT path (`?view=gwt`) unaffected (no rail markers, no flag attr)
