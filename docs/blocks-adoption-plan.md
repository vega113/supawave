# Server-First Blocks Adoption Plan

Owner: Migration Engineering
Last updated: 2025-09-02

Statuses: planned | in_progress | completed

-------------------------------------------------------------------------------

## Why Blocks (Segments) Now

wiab.pro’s fragment fetch relies on a segment-oriented persistence API (blocks) to fetch only the portions of a wavelet needed for the current viewport. To achieve parity and long-term performance in Apache Wave, we will adopt a compatible server-side blocks interface first, then evolve transport and client application.

-------------------------------------------------------------------------------

## Phases Overview

1) Foundations & API Scaffolding (server-first)
2) Compatibility SegmentWaveletState (no storage migration yet)
3) FragmentsFetcher & Request (server logic)
4) Server Endpoint (HTTP JSON for bring-up) and Transport Prep
5) Client Applier (future) & RPC Evolution (WebSocket)
6) Rollout, Observability, and Cleanup

-------------------------------------------------------------------------------

## Phase 1 — Foundations & API Scaffolding (server-first)

Status: in_progress

Goal: Introduce core types and interfaces needed by a segment-based fetch path, behind flags and without changing runtime behavior.

### Task 1.1 — Define Core Types (SegmentId, VersionRange, Interval)

- Context:
  - wiab.pro declares SegmentId (INDEX, MANIFEST, PARTICIPANTS, TAGS, per-blip segments), VersionRange (from,to), and Interval (snapshot across a version range).
  - We add compatible types. Implementation will be minimal; behavior will be provided in Phase 2.

- Implementation:
  - Add `org.waveprotocol.wave.model.id.SegmentId` (static IDs for INDEX/MANIFEST/etc. and factory for blip segments; `isBlip()`), `equals/hashCode/compareTo`.
  - Add `org.waveprotocol.box.server.persistence.blocks.VersionRange` (inclusive `from()` and `to()`), static `of(long,long)`.
  - Add `org.waveprotocol.box.server.persistence.blocks.Interval` interface (marker + minimal `Object getSnapshot(long version)`; Phase 2 will add actual structure).

- Tests:
  - Unit: SegmentId equality/ordering; VersionRange boundary correctness; `of()` rejects invalid ranges (from > to).

- DoD:
  - Types compile; unit tests pass; no references wired into runtime.

- AI implementer notes:
  - Keep API names aligned with wiab.pro to ease porting (even if implementations differ initially).

### Task 1.2 — SegmentWaveletState Interface

- Context:
  - wiab.pro provides a `SegmentWaveletState` that can return intervals for requested segments and version ranges.

- Implementation:
  - Add `org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState` with methods:
    - `Map<SegmentId, Interval> getIntervals(long version)`
    - `Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache)`
    - Streaming variant with `Receiver<Pair<SegmentId, Interval>>`

- Tests:
  - Interface only; no tests required beyond compilation.

- DoD:
  - Interface compiles; no behavior wired.

### Task 1.3 — Flags & Config Defaults

- Context:
  - Keep blocks access fully gated. Config must live in `reference.conf` with overrides in `application.conf`.

- Implementation:
  - Add `client.flags.defaults` merging path already implemented for dynamic client flags.
  - Add server toggle key (future): `blocks.enable=true|false` (not yet used).

- Tests:
  - Config loads with default; overridden in `config/application.conf`.

- DoD:
  - Keys recognized by Typesafe Config; no behavior change when off.

-------------------------------------------------------------------------------

## Phase 2 — Compatibility SegmentWaveletState (no storage migration)

Status: planned

Goal: Provide a basic implementation that can return intervals reconstructed from the current store, sufficient for building fragment ranges and returning metadata.

### Task 2.1 — SegmentWaveletStateCompat

- Context:
  - Without the blocks backend, emulate minimal intervals from the existing `WaveletProvider`/snapshot and `Conversation` structures.

- Implementation:
  - Add `SegmentWaveletStateCompat` that:
    - Accepts a wavelet name and version; reconstructions are best-effort.
    - For INDEX/MANIFEST: produce pseudo-intervals from the latest snapshot.
    - For blip segments: record presence + creation “version” using `lastModifiedVersion` as an approximation.
  - This is for bring-up; real intervals will come later.

- Tests:
  - Unit: builds non-empty maps for INDEX/MANIFEST on a wavelet with content.
  - Integration (mock): fake a wavelet with a few blips; verify segment list contains corresponding blip segments.

- DoD:
  - Basic non-empty results for common cases; safe behavior when data is missing.

- AI notes:
  - Emphasis on safety and logging; do not alter runtime unless called by Phase 3 explicitly.

-------------------------------------------------------------------------------

## Phase 3 — FragmentsFetcher & Request (server logic)

Status: planned

Goal: Port a compatible `FragmentsRequest` and `FragmentsFetcher` that compute VersionRanges using `SegmentWaveletState`.

### Task 3.1 — FragmentsRequest

- Context:
  - Mirror wiab.pro’s builder API to encode either (a) an explicit map of segment ranges, or (b) a common [start,end] version when no per-segment ranges exist.

- Implementation:
  - Port a builder with validation; apply Apache Wave package layout.

- Tests:
  - Unit: builder rejects conflicting inputs; produces the expected map; serializes toString sanely for logs.

- DoD:
  - Request object ready for fetcher; used only in Phase 3.2.

### Task 3.2 — FragmentsFetcher (compat)

- Context:
  - Using `SegmentWaveletState`, compute ranges for INDEX, MANIFEST, and a small set of blip segments around the viewport.

- Implementation:
  - Implement `FragmentsFetcher.fetchWavelet(...)` and `fetchFragmentsRequest(...)` with compatibility behavior:
    - Stage 1: INDEX+MANIFEST at latest version; Stage 2: derive blip ranges using simple heuristics (e.g., N closest blips to visible start) until compatibility StartVersionHelper arrives.
    - Respect a reply-size budget (bytes) if possible; otherwise “best-effort” in compat mode.

- Tests:
  - Integration (mock provider + compat state): verify ranges contain INDEX/MANIFEST and at least one blip segment when present, in both forward and backward directions.

- DoD:
  - Fetcher returns a FragmentsBuffer-like structure (or simple DTO) with segment intervals; safe under missing data.

-------------------------------------------------------------------------------

## Phase 4 — Server Endpoint & Transport Prep

Status: planned

Goal: Replace `/fragments` stub with real fetcher-backed JSON; spec the future WebSocket proto.

### Task 4.1 — HTTP `/fragments` backed by FragmentsFetcher

- Implementation:
  - Parse: waveRef (or path), startBlipId, direction, limit.
  - Call FragmentsFetcher; serialize JSON listing segment ids + version ranges + minimal metadata (author, lastModified, blip ids).
  - Config: keep behind `enableFragmentFetch`.

- Tests:
  - Contract: 200 OK for valid ref; JSON contains requested fields.
  - Error: invalid ref → 400; unauthorized → 403.

- DoD:
  - Endpoint returns structured results; safe to run in dev.

### Task 4.2 — Proto & RPC handler (spec only in this phase)

- Implementation:
  - Draft clientserver.proto additions (FetchFragmentsRequest/Response) compatible with wiab.pro.
  - Add a no-op handler stub (behind flag) that returns an error until Phase 5.

- DoD:
  - Spec and stubs in place; not used by default.

-------------------------------------------------------------------------------

## Phase 5 — Client Applier & Transport Evolution (Future)

Status: planned

Goal: Port `RawFragment` types and client applier; switch client to RPC once server validated.

### Task 5.1 — Client RawFragment Applier (model)

- Implementation:
  - Port minimal `ObservableWaveletFragmentData.applyRawFragment(...)` and dependent types.
  - Add guardrails (sizes, version checks) and tests with fake fragments.

- DoD:
  - Apply path compiles + unit tests pass (no wiring yet).

### Task 5.2 — Client FragmentRequester over ViewChannel

- Implementation:
  - Port FragmentRequesterImpl (queueing, concurrency caps, listeners) and wire to ViewChannel RPC.

- DoD:
  - Works behind `enableFragmentFetch`; dynamic renderer requests segments near viewport.

-------------------------------------------------------------------------------

## Phase 6 — Rollout, Observability, Cleanup

Status: planned

### Task 6.1 — Observability

- Implementation:
  - Add debug counters/timers: fragment requests count, average reply size, cache hit rate.
  - Gate logs under `enableViewportStats`.

### Task 6.2 — Cleanup deprecated flags/paths

- Implementation:
  - Remove JVM `-Dwave.clientFlags`; consolidate config–driven defaults.
  - Deprecate experimental tuning flags if unneeded.

-------------------------------------------------------------------------------

## Status Log (Commits)

- 2025-09-01 — Dynamic renderer MVP + flags + minimal fragments stub
  - Commits: see repo history (e.g., f7f02b6d, 55fdbf3d, …)
  - Summary: dynamic windowing, placeholders, resource cleanup; `/fragments` stub and HTTP client requester behind flag.

Each completed task will add its commit ids and a short summary here.

