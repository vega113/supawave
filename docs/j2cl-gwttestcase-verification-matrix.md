# GWTTestCase Verification Matrix

Status: Current
Updated: 2026-04-19
Owner: Project Maintainers
Task: `#898`

This document is the canonical answer for where the remaining legacy
`GWTTestCase` debt lives after issue `#898`.

## Measured Baseline

Measured on the `issue-898-gwttest-split` worktree on 2026-04-19:

- direct `extends GWTTestCase` Java files before the lane conversions: `21`
- direct `extends GWTTestCase` Java files remaining after this lane: `19`
- inherited editor/test-base descendants still on the browser-era harness path: `11`
- plain JVM conversions landed in this issue:
  - `org.waveprotocol.wave.client.scheduler.DelayedJobRegistryTest`
  - `org.waveprotocol.wave.client.util.UrlParametersTest`

Browser verification for the retained browser-facing suites continues to use
[docs/runbooks/browser-verification.md](./runbooks/browser-verification.md)
and
[docs/runbooks/change-type-verification-matrix.md](./runbooks/change-type-verification-matrix.md)
as the execution baseline. Issue `#925` records the retirement accounting for
the remaining browser-harness descendants that are no longer part of the
supported runtime/test gate.

## Issue #925 Retirement Accounting

The following suites are explicitly accounted for in `#925` as no longer
blocking the supported browser runtime after the legacy authenticated GWT
client path is retired:

| Group | Items | Disposition | Follow-on |
| --- | --- | --- | --- |
| Direct `GWTTestCase` suites | `FastQueueGwtTest`, `BrowserBackedSchedulerGwtTest`, `EditorGwtTestCase`, `ImgDoodadGwtTest`, `EditorEventHandlerGwtTest`, `PasteExtractorGwtTest`, `PasteFormatRendererGwtTest`, `RepairerGwtTest`, `TypingExtractorGwtTest`, `GwtRenderingMutationHandlerGwtTest`, `NodeManagerGwtTest`, `CleanupGwtTest`, `TestBase`, `KeyBindingRegistryIntegrationGwtTest`, `AggressiveSelectionHelperGwtTest`, `ExtendedJSObjectGwtTest`, `WrappedJSObjectGwtTest`, `XmlStructureGwtTest`, `EventDispatcherPanelGwtTest` | still present but no longer build blockers because they are no longer part of the supported runtime/test gate | `#904` |
| Inherited browser-harness descendants | `ContentTestBase`, `LazyPersistentContentDocumentGwtTest`, `NodeEventRouterGwtTest`, `DomGwtTest`, `ContentElementGwtTest`, `ContentTextNodeGwtTest`, `ElementTestBase`, `OperationGwtTest`, `MobileWebkitFocusGwtTest`, `MobileImeFlushGwtTest`, `ParagraphGwtTest` | still present but no longer build blockers because they are no longer part of the supported runtime/test gate | `#904` |
| Extra Jakarta/client holdouts | `WaveWebSocketClientTest`, `RemoteWaveViewServiceEmptyUserDataSnapshotTest`, `FocusBlipSelectorTest`, `BlipMetaDomImplTest` | still present but no longer build blockers because they are no longer part of the supported runtime/test gate | `#904` |

## Direct Suites (Baseline Classification for This Lane)

This baseline table is broader than the "19 remaining" metric above. It keeps
the suites converted in `#898` alongside the retained browser-harness suites so
the lane has one traceable classification record.

| File | Current harness path | Category | Reason | Follow-on |
| --- | --- | --- | --- | --- |
| `wave/src/test/java/org/waveprotocol/wave/client/common/util/FastQueueGwtTest.java` | direct `extends GWTTestCase` | temporary retain | The thing under test is still the `FastQueue` JSO-backed storage path (`IntMapJsoView`), so a plain JVM conversion would require changing the runtime seam instead of only the harness. | Later common-util/browser-interop cleanup in `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/content/EditorGwtTestCase.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Base harness for editor content suites; correctness depends on live DOM, browser selection, and widget/event semantics. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/content/img/ImgDoodadGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Exercises editor image doodad rendering and attachment behavior that depends on browser DOM/widget behavior. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Editor event routing is defined by browser event semantics, not plain JVM logic. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/PasteExtractorGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Paste extraction depends on browser DOM paste/input structure. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/PasteFormatRendererGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Paste-format rendering depends on browser-backed rich-text rendering semantics. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/RepairerGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | The repair flow is validated against browser DOM/editor mutation behavior. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/TypingExtractorGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Typing extraction is tied to browser input and mutation semantics. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/gwt/GwtRenderingMutationHandlerGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Validates GWT DOM rendering/mutation behavior directly. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/impl/NodeManagerGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Node-manager correctness is coupled to browser-backed editor node lifecycles. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/CleanupGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Integration cleanup assertions depend on browser DOM/editor state transitions. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/TestBase.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Base integration harness for focus, IME, paragraph, and operation suites that depend on browser behavior. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/keys/KeyBindingRegistryIntegrationGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Keyboard binding correctness depends on browser key event dispatch and editor focus behavior. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/selection/content/AggressiveSelectionHelperGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Selection helper behavior is defined by browser selection/range semantics. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/scheduler/BrowserBackedSchedulerGwtTest.java` | direct `extends GWTTestCase` | temporary retain | The scheduler seam is still coupled to browser timer/widget integration, so forcing a JVM conversion now would widen the lane. | Later scheduler/browser seam cleanup under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryGwtTest.java` | direct `extends GWTTestCase` | plain JVM now | Pure delayed-job registry logic no longer needs the hosted/browser harness. Converted in this issue to `DelayedJobRegistryTest`. | Completed in `#898` |
| `wave/src/test/java/org/waveprotocol/wave/client/util/ExtendedJSObjectGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | JS overlay behavior is the thing under test; JVM execution would not prove the same contract. | Later JS overlay cleanup in `#902` / `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersGwtTest.java` | direct `extends GWTTestCase` | plain JVM now | The string-constructor/query-parser path and `buildQueryString(...)` can be verified without browser-global lookup. Converted in this issue to `UrlParametersTest`. | Completed in `#898` |
| `wave/src/test/java/org/waveprotocol/wave/client/util/WrappedJSObjectGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | JS overlay/wrapper semantics are browser-runtime behavior, not plain JVM logic. | Later JS overlay cleanup in `#902` / `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/block/xml/XmlStructureGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Wavepanel XML structure assertions depend on live DOM structure/rendering behavior. | Later wavepanel migration under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/event/EventDispatcherPanelGwtTest.java` | direct `extends GWTTestCase` | browser/J2CL-facing later | Wavepanel event dispatch correctness depends on browser DOM event routing. | Later wavepanel migration under `#904` |

## Editor Family Rows

| Family | Current harness path | Category | Reason | Follow-on |
| --- | --- | --- | --- | --- |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/content/ContentTestBase.java`, `LazyPersistentContentDocumentGwtTest.java`, `NodeEventRouterGwtTest.java`, `DomGwtTest.java`, `ContentElementGwtTest.java`, `ContentTextNodeGwtTest.java` | inherited through `EditorGwtTestCase` and `ContentTestBase` | browser/J2CL-facing later | These suites validate DOM-backed content documents, node routing, and editor content structure. Plain JVM execution would not prove the same browser/editor invariants. | Later editor DOM work under `#904` |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/ElementTestBase.java`, `OperationGwtTest.java`, `MobileWebkitFocusGwtTest.java`, `MobileImeFlushGwtTest.java`, `ParagraphGwtTest.java` | inherited through `TestBase` and `ElementTestBase` | browser/J2CL-facing later | These suites depend on browser focus, IME, paragraph layout, and editor integration behavior. | Later editor DOM work under `#904` |
