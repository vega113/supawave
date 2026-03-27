# Wave Federation Draft Review

Date: 2026-03-27
Source reviewed: `https://github.com/LaPingvino/incubator-wave/blob/federation-spec-draft/docs/wave-federation-spec-draft.md`

## Decision summary

The draft is directionally interesting, especially its push toward HTTPS-based
discovery and simpler operator setup. It is not desirable to adopt as the
implementation plan in its current form.

My recommendation is:

- do not implement this draft as written
- keep the transport-simplification goal
- split the work into narrower design documents
- pursue shareable links and guest access ahead of full server federation
- if federation stays in scope, start with a much smaller compatibility-first
  transport profile

## Summary of the proposed approach

The draft proposes replacing Wave's original federation transport with a new
HTTPS and WebSocket based protocol that:

- uses `/.well-known/wave/server` for discovery
- exposes signing keys over `/_wave/keys/v1`
- submits deltas over HTTP
- fetches history over HTTP
- streams live deltas and commit notices over WebSocket
- optionally supports JSON and S-expression encodings in addition to protobuf
- introduces a new wavelet snapshot endpoint for fast bootstrap
- treats LLM-assisted federation setup as part of the intended operational story

The stated goal is to keep Wave's OT and protobuf-level semantics while
dropping XMPP and certificate-chain complexity.

## What the draft gets right

- It correctly identifies operator friction as a real blocker. HTTPS,
  `/.well-known`, and proxy-friendly deployment are materially easier to
  operate than the historical federation stack.
- It aims at the right seam conceptually: the existing
  `WaveletFederationProvider` and `WaveletFederationListener` interfaces are
  the correct architectural boundary for a transport replacement.
- It is right to prefer incremental catch-up instead of requiring every new
  remote to reimplement Wave's entire internal storage model.
- It is also right to separate product-level collaboration convenience from
  heavyweight cross-server federation.

## Main concerns

### 1. The draft understates how much of the current implementation is tied to the old trust model

The draft says no `WaveServerImpl` changes are needed and implies the work is
mostly a transport swap. The seam is real, but the implementation cost is
larger than that claim suggests.

In this repo today:

- startup always wires `NoOpFederationModule` in
  `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- federation bindings are split between `FederationRemoteBridge` and
  `FederationHostBridge` in
  `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerModule.java`
- signature verification is centered on X.509-backed `ProtocolSignerInfo` in
  `wave/src/main/java/org/waveprotocol/box/server/waveserver/CertificateManagerImpl.java`
- signing currently comes from RSA/X.509 handling in
  `wave/src/main/java/org/waveprotocol/wave/crypto/WaveSignerFactory.java`
- verification is certificate-chain based in
  `wave/src/main/java/org/waveprotocol/wave/crypto/WaveSignatureVerifier.java`

That means Ed25519 and HTTP-fetched keys are not just a wire-format change.
They imply a new signer representation, verifier path, persistence model, key
rotation model, and compatibility story for `ProtocolSignerInfo`.

### 2. The trust model is too thin for a v1 federation spec

The proposal replaces certificate exchange with HTTPS-fetched keys. That is
attractive operationally, but the spec currently leaves several important trust
questions unresolved:

- how hostname verification works when the wave domain and serving host differ
- how `server_name` is bound to the `.well-known` result
- how compromise recovery works after a key leak
- whether old keys are required for replay verification of historical deltas
- whether first-seen keys are pinned
- whether multi-perspective or notary validation is optional, recommended, or
  required for some deployment classes

For an internal prototype, this can be hand-waved. For a federation spec, it
cannot.

### 3. The snapshot endpoint is the least mature part of the proposal

The draft introduces a whole-wavelet snapshot endpoint for fast sync. That may
be useful eventually, but it is underspecified and risky as a first-class
federation primitive.

Open questions include:

- what exact canonical representation is signed or trusted
- whether snapshots are authoritative or advisory bootstrap material
- how snapshot integrity is related to delta history
- whether snapshot import bypasses invariants enforced by normal delta
  application
- how private data, attachments, annotations, and future fragment or segment
  state should be represented

Wave already has a delta-and-snapshot store internally, but exposing a
cross-server snapshot contract is not the same as reusing a local persistence
detail.

### 4. Delivery semantics for the WebSocket stream are not specified tightly enough

The proposed stream covers subscribe, delta update, and commit update. What it
does not yet specify clearly is:

- ordering guarantees across reconnects
- whether delivery is at-least-once or exactly-once
- how a remote acknowledges receipt
- how backpressure is handled
- how long a sender retains replay buffers
- how commit notices interact with persistence on the receiving side

This matters because the existing `WaveletFederationListener` callback model is
not just a fire-and-forget notification surface. It encodes success and retry
expectations. The current `WaveletNotificationDispatcher` also assumes per-domain
delivery behavior that a new stream layer would need to preserve or redefine.

### 5. The interface mapping is not yet as clean as the draft claims

The draft is strongest when it says "new transport, not new protocol." It gets
weaker when the concrete endpoint semantics drift from the current interfaces.

Examples:

- `requestHistory()` today includes requester domain and a length limit. The
  draft's history endpoint changes the shape and semantics.
- `getDeltaSignerInfo()` today is tied to a signer id and a specific delta end
  version. The draft replaces that with general key fetches and does not fully
  explain the migration for already-signed historical deltas.
- `postSignerInfo()` becomes a no-op. That may be reasonable, but it is a
  semantic change, not just a transport remap.

A better next step would be a one-to-one mapping table that includes request
authorization rules, persistence expectations, retry expectations, and failure
semantics for every existing method.

### 6. Serialization ambition is too broad for v1

Protobuf plus JSON plus S-expressions is unnecessary for an initial
implementation.

For v1, each added encoding multiplies:

- parser surface
- test surface
- interoperability matrix
- security review surface
- debugging complexity when two peers negotiate differently

If the goal is working federation, protobuf-only is the correct starting point.
JSON can remain a debugging or documentation format. S-expressions should stay
out of the protocol until there is a concrete adoption need.

### 7. LLM-assisted setup should not live inside the protocol specification

Operator tooling matters, but protocol design and setup automation are separate
documents. Mixing them weakens the spec.

The protocol should define:

- wire contracts
- trust contracts
- versioning
- error semantics
- conformance expectations

Tooling docs can then explain:

- how to generate keys
- how to publish `.well-known`
- how to configure reverse proxies
- how to diagnose failures

### 8. The shareable-links section is more compelling as a product strategy than full federation

This is the most important product conclusion from the draft.

This repo already has meaningful public-wave and sharing-adjacent work:

- public wave rendering and fetch servlets
- public directory and sitemap support
- shared-domain participant based public visibility
- active JWT work for modern auth surfaces

The shareable-links path aligns with that existing direction much better than a
full federation rewrite does. It delivers user value sooner, with less protocol
risk and much lower operational complexity.

## Suggested improvements to the draft

### 1. Split the draft into separate documents

At minimum:

- transport profile
- trust and key-distribution model
- optional snapshot/bootstrap profile
- operator tooling guide
- product collaboration strategy for share links and guest access

This will force sharper decisions and prevent transport, security, product, and
automation concerns from being blurred together.

### 2. Recast v1 as a compatibility-first transport profile

A stronger first spec would say:

- protobuf only
- HTTPS request/response transport
- no snapshot endpoint in v1
- no alternate encodings in v1
- minimal streaming model, or even polling-first if needed
- explicit compatibility with current signer-info semantics until a later auth
  profile replaces them

That is much easier to reason about and test.

### 3. Define the trust model before changing the crypto model

If the project wants to leave X.509 behind, the next draft should specify:

- authoritative server-name binding
- key rotation rules
- old-key retention rules
- compromise recovery
- caching and expiry rules
- replay verification rules for historical deltas

Without that, the Ed25519 move is a sketch rather than a design.

### 4. Downgrade snapshots from core protocol to optional extension

If snapshots stay in scope, they should be an extension with:

- explicit canonical format
- integrity rules
- authorization rules
- fallback behavior when unsupported
- a statement that normal delta history remains the source of truth

### 5. Specify stream delivery semantics precisely

The next revision should answer:

- ordering
- replay cursor format
- deduplication rules
- ack model
- retry model
- failure recovery
- commit semantics

### 6. Remove S-expression from the implementation path

If it remains in the document at all, it should be moved to "future
experiments" rather than v1 capability negotiation.

### 7. Separate product strategy from federation strategy

The shareable-links track should become its own architecture note with a clear
scope:

- read-only public links
- signed edit links
- guest identity model
- upgrade path from guest to registered account
- JWT alignment with the broader auth redesign

## Alternative approach suggestions

### Alternative A: share links first, federation later

This is the most desirable practical path.

Build on the repo's existing public-wave work and JWT direction to support:

- stable share URLs
- read-only public access
- signed edit invitations
- guest participants with explicit JWT-backed capabilities

This solves real collaboration problems without requiring server-to-server OT
federation.

### Alternative B: transport-only federation pilot

If federation itself is strategically important, start with a limited pilot:

- protobuf only
- HTTP transport only
- existing signer-info semantics preserved initially
- history fetch plus delta submit only
- no snapshot endpoint
- no S-expression
- no LLM setup scope in the protocol

That would prove whether the transport seam is viable before redesigning trust,
bootstrap, and streaming all at once.

### Alternative C: separate auth modernization from federation modernization

The repo is already doing JWT-related design work. That should not be casually
merged into federation design.

A cleaner sequence is:

1. modernize local auth and token surfaces first
2. decide whether cross-server federation needs the same trust model
3. only then design remote key distribution and federation auth around a stable
   local security foundation

## Pros and cons

### Pros

- much easier for operators to understand than legacy federation
- aligns with common web infrastructure
- keeps attention on Wave's real differentiator, which is OT on structured
  documents
- recognizes that existing federation interfaces are the right abstraction seam
- identifies shareable links as a lower-friction adoption path

### Cons

- underestimates the current codebase's coupling to X.509 and signer-info flows
- mixes protocol design, trust design, product strategy, and setup automation
- introduces too many protocol features at once
- leaves key delivery and replay-trust semantics underspecified
- adds a snapshot model before the trust and canonical-state story is clear
- broadens the encoding surface without a strong reason

## Concrete weak-spot resolutions

1. Replace the single draft with a transport RFC plus a separate trust RFC.
2. Add a repo-specific implementation impact appendix that names the actual
   files and subsystems that would change in this tree.
3. Reduce v1 scope to protobuf and HTTP.
4. Move snapshots to an optional extension or remove them from v1 entirely.
5. Define exact delivery and retry semantics for live updates.
6. Define key rotation and historical verification rules before adopting
   Ed25519-over-HTTP as the baseline.
7. Move LLM-assisted setup into operator tooling documentation.
8. Advance shareable links and guest access as the preferred near-term product
   path.

## Final recommendation

The proposed direction is worth keeping as research, but not as the immediate
federation implementation plan.

The desirable path for this repo is:

- near term: invest in shareable links, public and semi-public collaboration,
  and JWT-backed guest access
- medium term: if federation remains strategically important, write a smaller
  compatibility-first transport profile and prove it against the current code
  seams
- long term: revisit a modern key-distribution model only after the trust,
  replay, and rotation story is fully specified

In short: keep the goal, reject the draft as written, and narrow the scope
before any implementation branch opens.
