# Persistence Topology Audit

Status: Current
Updated: 2026-03-19
Owner: Project Maintainers

## Goal

Determine what persistence surfaces are still local to a single Wave server
process, what is already Mongo-capable, and what blocks safe multi-instance or
blue-green deployment.

## Current Defaults

The current default config in [reference.conf](../wave/config/reference.conf) still points the main mutable stores at local disk:

- `core.signer_info_store_type : file`
- `core.attachment_store_type : disk`
- `core.account_store_type : file`
- `core.delta_store_type : file`
- `core.sessions_store_directory : _sessions`
- `core.search_type : memory`
- `core.mongodb_driver : v2`

That means the out-of-the-box runtime is still optimized for a single node with
local mutable state.

## Production Hardening Status

Mongo is the intended production persistence direction, but the deployment is
not yet safe for a live overlay.

Reason:
- the repo does not yet wire Mongo username/password config through the Wave
  application
- the deployment story still lacks a validated backup/restore drill
- the durability target is not yet documented as a production requirement

Use `../deploy/mongo/README.md` for the operator follow-through that still has to
land before the Mongo-backed deployment can be treated as production-grade.

## What Already Exists

### File-backed stores

The file-backed path is already a specialized append-oriented store, especially
for deltas:

- [FileDeltaStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileDeltaStore.java)
- [FileDeltaCollection.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileDeltaCollection.java)
- [FileAccountStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java)
- [FileAttachmentStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAttachmentStore.java)
- [FileSignerInfoStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileSignerInfoStore.java)

Observations:

- Deltas are stored as wavelet-specific append logs plus sidecar indexes.
- Accounts and signer info are flat-file keyed blobs.
- Attachments are separate files on disk.
- This is already close to a Wave-specific embedded storage engine.
- It is not a standalone storage service. Extracting it would require new API,
  concurrency, clustering, replication, and operational tooling.

### Mongo-backed stores

The repo already has Mongo-backed equivalents for the core mutable stores:

- [Mongo4DeltaStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStore.java)
- [Mongo4DeltaCollection.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaCollection.java)
- [Mongo4AccountStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java)
- [Mongo4AttachmentStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AttachmentStore.java)
- [Mongo4SignerInfoStore.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4SignerInfoStore.java)
- [Mongo4DbProvider.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java)

The binding points already exist in [PersistenceModule.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java).

Observations:

- Delta append semantics are already modeled cleanly in Mongo.
- Attachments already use GridFS.
- Accounts and signer info already have Mongo-backed implementations.
- The doc surface was stale; the code already contains `Mongo4DeltaStore`.

## What Still Blocks Multi-Instance Safety

### 1. Sessions on the active Jakarta runtime

The legacy server module builds a file-backed Jetty session data store:

- [ServerModule.java](../wave/src/main/java/org/waveprotocol/box/server/ServerModule.java)

The active Jakarta server module does not:

- [ServerModule.java](../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerModule.java)

Observations:

- The Jakarta path returns a minimal `SessionHandler`.
- It does not configure `FileSessionDataStore`.
- The Jakarta `SessionManagerImpl` still describes session lookup as best-effort
  and reflective rather than a firm shared-session design.

Conclusion:

- Session state is currently process-local or best-effort on the active runtime.
- Mongo does not solve that automatically.
- JWT work reduces this problem, but today it is still a multi-instance blocker.

### 2. Search and per-user wave views

The default search mode is `memory`:

- [SearchModule.java](../wave/src/main/java/org/waveprotocol/box/server/SearchModule.java)
- [MemoryPerUserWaveViewHandlerImpl.java](../wave/src/main/java/org/waveprotocol/box/server/waveserver/MemoryPerUserWaveViewHandlerImpl.java)

Alternative search mode is Lucene on local disk:

- [LucenePerUserWaveViewHandlerImpl.java](../wave/src/main/java/org/waveprotocol/box/server/waveserver/LucenePerUserWaveViewHandlerImpl.java)
- [FSIndexDirectory.java](../wave/src/main/java/org/waveprotocol/box/server/persistence/lucene/FSIndexDirectory.java)

Conclusion:

- `memory` search is node-local and not shared.
- Lucene is also node-local, just disk-backed.
- Mongo migration of deltas/accounts/attachments does not fix search consistency.

### 3. Default configuration still points to local stores

Even though Mongo implementations exist, the default runtime remains file/disk
or memory-based. That means switching to Mongo is not just a code-exists claim.
It still needs an explicit production configuration decision and validation pass.

## Evaluation Of The Two Directions

### Option A: Build a standalone WaveDB

Pros:

- Could match Wave's append-heavy delta model closely.
- Could own Wave-specific snapshots and wavelet sharding directly.
- Could be optimized around the real access patterns.

Cons:

- This is effectively building a new storage product.
- It needs network protocols, concurrency semantics, replication, backup,
  failover, migration, and operations.
- It delays production-safe multi-instance deployment much more than reusing the
  existing Mongo path.

Conclusion:

- Not the next move.
- Reasonable only if Mongo becomes a proven bottleneck after production use.

### Option B: Finish the Mongo production path

Pros:

- Most of the store implementations already exist.
- It externalizes the biggest mutable state surfaces without inventing a new
  database.
- It improves deployability sooner.

Cons:

- Mongo alone does not solve sessions or search topology.
- The current default config still points away from Mongo.

Conclusion:

- This is the recommended next move.

## Recommendation

Use Mongo as the production persistence direction.

Do not build a standalone WaveDB now.

The practical order should be:

1. Make the Mongo-backed store set the intended production path.
2. Audit and fix session strategy on the active Jakarta runtime.
3. Decide search topology for multi-instance operation.
4. Only after that, evaluate blue-green or multiple active app instances.

## Follow-on Work Suggested By This Audit

1. Add a production-config task to switch the intended persistent stores to
   Mongo-backed implementations on the active runtime.
2. Add a Jakarta session topology task:
   - either shared session persistence
   - or explicit JWT-only session design as the path away from local sessions
3. Add a search topology task:
   - decide whether search remains node-local and rebuilt
   - or moves to an explicitly shared indexing/search system
4. Only after those are settled, revisit zero-downtime or blue-green rollout
   assumptions for Wave.
