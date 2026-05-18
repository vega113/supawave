# Issue 1254: J2CL Inline Blip Insertion And Open-Wave Scroll Parity

## Root Cause

- GWT toolbar Reply calls `ReplyLocationResolver.resolve(...)`; when no caret/selection is found it falls back to `blip.getContent().size() - 1`, then calls `WaveletBasedConversationBlip.addReplyThread(location).appendBlip()`.
- J2CL already has a rich-content path that can emit the same parent-body `<reply id="...">` anchor, but `J2clComposeSurfaceController.onInlineReplyAnchorRequested(...)` stores `-1` when `wave-blip` reports no captured caret. The next submit therefore creates only the manifest `<thread>` and no body anchor.
- A manifest-only child thread is not equivalent to GWT's anchored inline reply. It renders like a continuation/flat child and can disappear from the expected inline location after reload because rehydration follows document anchors.
- Open-wave scrolling already has client-side `FocusBlipSelector.selectInitialBlip` parity on `main`; however the initial server viewport can still exclude the root blip when it anchors around the last unread/last blip. PR #1251 has the narrow server-side fix that keeps the root visible in the initial J2CL viewport.

## Implementation Plan

- In `J2clComposeSurfaceController`, normalize a missing inline anchor to `parentBodyItemCount - 1` when the parent body has a valid end position, matching GWT's fallback.
- Keep explicit captured caret offsets unchanged.
- Keep continuation replies unchanged; they do not call the inline-anchor request path and `submitReply(..., continuation=true)` still suppresses anchors.
- Bring the small root-visible initial viewport fix from PR #1251 into this branch if it is not on `main`, so open-wave scroll behavior has both client focus and server window parity.
- Add a changelog fragment for the user-facing J2CL behavior fix.

## Verification

- Red/green: `./mvnw -f pom.xml -Psearch-sidecar -Dtest=org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceControllerTest test` from `j2cl/`.
- Focused server viewport test if root-visible fix is included.
- Changelog assemble/validate.
- PR gate subset from the J2CL runbook as time permits: `sbt -batch "j2clSearchBuild; j2clSearchTest"`.
