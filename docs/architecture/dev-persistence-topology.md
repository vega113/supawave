# Dev Persistence Topology

Status: Current
Updated: 2026-04-03
Owner: Project Maintainers

## Goal

Describe the safe local persistence layout that the Wave runtime boots with in
development and other single-node file-store setups.

## Default Local Store Selection

`wave/config/reference.conf` still points the core mutable stores at local
file or disk-backed implementations:

- `core.signer_info_store_type : file`
- `core.signer_info_store_directory : _certificates`
- `core.attachment_store_type : disk`
- `core.attachment_store_directory : _attachments`
- `core.account_store_type : file`
- `core.account_store_directory : _accounts`
- `core.delta_store_type : file`
- `core.delta_store_directory : _deltas`

Other local defaults that shape the dev topology are:

- `core.sessions_store_directory : _sessions`
- `core.search_type : memory`
- `core.index_directory : _indexes`

That means the out-of-the-box runtime is still optimized for a local developer
machine or another single-node environment with mutable state on the app host.

## What The Directories Mean

The default file-store layout separates the main mutable surfaces:

- `_accounts/`
  - account records
- `_attachments/`
  - binary attachment payloads
- `_deltas/`
  - wavelet delta logs and related sidecar state
- `_certificates/`
  - signer certificate records
- `_sessions/`
  - local Jetty session persistence
- `_indexes/`
  - local Lucene indexes when search is not left in `memory`

These paths are part of the developer/runtime topology, not an operator-grade
multi-instance storage story.

## Pluggable Persistence Seams

The runtime keeps storage behind pluggable interfaces and module bindings. The
main seams are:

- `AccountStore`
- `AttachmentStore`
- `DeltaStore`
- `SignerInfoStore`
- `IndexDirectory`

`PersistenceModule` selects the active backend for each store type. The codebase
already contains file, memory, disk, Mongo, Mongo4, and Lucene/RAM-backed
implementations, but the default config still keeps the live dev topology on
local state.

## Development And Worktree Usage

Because the safe baseline is file-backed, local testing often depends on the
same `_accounts`, `_attachments`, and `_deltas` directories that the main
checkout already uses. Worktrees that need realistic local state typically
reuse that file store via `scripts/worktree-file-store.sh` instead of creating
fresh isolated data for every lane.

## Boundary With Production Guidance

This document is intentionally limited to the developer/runtime topology. It
does not claim that Mongo is production-ready or that the runtime is already
safe for multi-instance deployment.

For the broader production and multi-instance analysis, read
[`docs/persistence-topology-audit.md`](../persistence-topology-audit.md).

The short version remains:

- file/disk-backed core stores are the safe local baseline
- sessions and search are still local-topology concerns
- Mongo-backed production remains a separate readiness track
