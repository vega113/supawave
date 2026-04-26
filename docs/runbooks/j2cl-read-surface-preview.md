# J2CL read-surface preview route

**URL:** `/?view=j2cl-root&q=read-surface-preview`

**Owner:** F-2 (#1037, slice 6 #1058).
**Status:** server-rendered fixture; no WaveletProvider lookup.
**Audience:** design + review contributors who want to verify the full
F-2 chrome surface on a single URL without provisioning a real wave.

## What it is

A signed-in J2CL root shell that renders a hard-coded five-blip
fixture wave through the same chrome that ships on the regular
`?view=j2cl-root` route. Every F-2 affordance is mounted in its
visible state so reviewers can scroll through them all in one screen.

## What you see

Top-down on the page:

| Region | Affordance | Notes |
| --- | --- | --- |
| Header (`<wavy-header>`) | A.1 brand link, A.2 locale picker, A.5 notifications bell, A.6 inbox icon, A.7 user-menu trigger | Same SSR path as the regular root shell |
| Nav (`<wavy-search-rail>`) | B.1–B.18 saved-search rail, B.5–B.10 folders, B.4 manage-saved button | Pre-loaded with `query=read-surface-preview` so the rail's active-folder highlight is empty (intentional — no folder owns this query) |
| Selected wave card | Depth-nav crumb, eyebrow, title "Sample read-surface preview wave", 3 unread badge, awareness pill ("2 new replies above"), participants strip, wave-nav-row buttons | The legacy adoption wrapper carries `data-j2cl-legacy-search-card="hidden"` so the wavy-thread-collapse.css rule collapses it visually |
| Read surface (`.j2cl-read-surface`) | Five blips: b1 (alice, reactions), b2 (bob, focused), b3 (carol, collapsed thread + depth-nav drill chip + task chip), b4 (dave, unread + attachment tile), b5 (eve, end of wave) | The focus frame anchors on b2 (`tabindex="0"`); pressing `j` / `k` moves it |
| Tag row | F-2 reads only — three sample tags (`design`, `f-2-preview`, `parity`) | F-3 owns tag editing |
| Floating controls | `<wavy-back-to-inbox>`, `<wavy-nav-drawer-toggle>`, `<wavy-wave-controls-toggle>`, `<wavy-floating-scroll-to-new>` | All four mount in their default position |
| Overlays (pre-opened) | `<wavy-version-history open>` and `<wavy-profile-overlay open>` for `carol@example.com` | Open-state styling visible without any user interaction |

## Browser walkthrough

1. Sign in as any user (the route requires `id != null`; signed-out
   viewers fall through to the regular shell + sign-in flow).
2. Navigate to `/?view=j2cl-root&q=read-surface-preview`.
3. Verify the dark wavy chrome paints with **no visible legacy light
   duplicate** below the search rail (Part A regression-locked). If a
   light-styled "Search results will appear here." or sidecar digest
   list appears, the bug is back — file a regression against #1058.
4. Press `Tab` to confirm the focus frame anchors on b2 (bob's blip).
5. Press `j` / `k` to move focus across blips.
6. Click the depth-nav drill chip on b3 to verify the drill-in
   transition.
7. Confirm the version-history and profile overlays render in their
   open state.

## Side-by-side with `?view=gwt`

Open `/?view=j2cl-root&q=read-surface-preview` and `/?view=gwt` in
the same signed-in session (the GWT side will show your real waves; the
preview is a fixture so the comparison is structural, not literal).
The chrome surface should match GWT's affordance inventory from
[`docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md`](../superpowers/audits/2026-04-26-gwt-functional-inventory.md)
row-for-row.

## Maintenance

The fixture content is hard-coded in
`HtmlRenderer.renderJ2clReadSurfacePreviewPage` and
`appendReadSurfacePreviewWorkflow` /
`appendReadSurfacePreviewBlips`. To add or remove a blip / chip /
overlay, edit those methods directly — there is no model layer.

The route is exercised by:

- `HtmlRendererJ2clRootShellIntegrationTest.testReadSurfacePreviewPageRenders`
- `HtmlRendererJ2clRootShellIntegrationTest.testReadSurfacePreviewDoesNotShowLegacySearchCardLight`
- `J2clStageOneFinalParityTest` (via the demo-route smoke assertion)

## Out of scope

- Persistence — the fixture is server-rendered HTML, no wavelet exists.
- Live websocket activity — the J2CL bundle still loads (so collapse
  animations, focus frame motion, depth-nav drill, modal toggles work),
  but no server delta stream is opened for the fixture wave id.
- Mark-read / mark-unread — F-4 (#1056) owns the live read-state path.
