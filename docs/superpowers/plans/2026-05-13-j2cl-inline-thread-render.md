# J2CL Inline Thread Rendering Plan

## Problem

GWT renders inline reply threads under their root blip anchor, but the J2CL read surface can render the same wave as flat root-level blips. Existing J2CL renderer code already supports `J2clInlineReplyAnchor` metadata when viewport fragments carry raw `<reply id="...">` tags. The sidecar document snapshot path decodes document operations to plain text and currently loses those anchors.

## Scope

- Preserve `<reply id="...">` anchors while decoding selected-wave document operations.
- Thread decoded anchors through document-backed viewport entries and read blips.
- Keep existing fragment-derived anchors authoritative when fragments already provided them.
- Add a user-facing changelog fragment for J2CL parity.

## Implementation Steps

1. Add transport and projection regression coverage for document-operation inline reply anchors.
2. Extend `SidecarSelectedWaveDocument` and `SidecarTransportCodec.DocumentExtraction` with immutable inline anchor lists.
3. Capture `reply` element starts as `J2clInlineReplyAnchor(threadId, visibleTextOffset)` during document extraction.
4. Pass document anchors through `J2clSelectedWaveViewportState` and `J2clSelectedWaveProjector`, preserving existing fragment anchors when present.
5. Run focused unit tests, changelog validation, and local sanity verification before PR.

## Self-Review Checklist

- Anchors do not become visible text.
- Anchor offsets are measured in decoded visible text, matching fragment parsing.
- Empty/null anchor lists stay immutable and safe.
- Existing fragment anchors are not overwritten by document refreshes.
- Changelog and issue/PR traceability are updated.
