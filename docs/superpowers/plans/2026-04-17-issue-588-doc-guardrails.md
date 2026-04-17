# Issue #588 — Blocking Doc & Owner/Freshness Guardrails

Status: In Progress
Created: 2026-04-17

## Goal

Turn deterministic documentation checks into blocking CI guardrails so broken
doc links and missing owner/freshness metadata fail the build before merge.

## Design Decisions

### Covered-doc definition: DOC_REGISTRY.md

Use a `docs/DOC_REGISTRY.md` file listing every doc path that requires the
metadata block. Rationale:

- Explicit — no heuristic to guess which docs are covered.
- Easy to extend — add a line, commit, done.
- The check script reads the registry; no magic directory scanning.

### Metadata convention: plain-text header block

Metadata lives at the very top of the file, before the first `#` heading:

```
Status: <value>
Owner: <value>
Updated: <YYYY-MM-DD>
Review cadence: <value>
```

This matches the existing convention already used by `docs/github-issues.md`,
`docs/runbooks/README.md`, and several architecture docs. Required fields for
every covered doc: all four.

### Covered docs (initial set)

All runbooks:
- docs/runbooks/README.md
- docs/runbooks/browser-verification.md
- docs/runbooks/change-type-verification-matrix.md
- docs/runbooks/mongo-migrations.md
- docs/runbooks/worktree-diagnostics.md
- docs/runbooks/worktree-lane-lifecycle.md

Canonical/authoritative docs referenced by runbooks or marked Canonical:
- docs/README.md
- docs/DEV_SETUP.md
- docs/BUILDING-sbt.md
- docs/SMOKE_TESTS.md
- docs/current-state.md
- docs/github-issues.md
- docs/agents/README.md
- docs/architecture/README.md
- docs/deployment/README.md

Total: 16 files.

## Check Algorithms

### check-doc-links.sh

1. Find all `.md` files under `docs/`.
2. Extract markdown links: `[text](path)` where path is a relative file path
   (not http/https/mailto/anchor-only).
3. Resolve each link relative to the file that contains it.
4. If the resolved path does not exist, report it.
5. Exit non-zero if any broken link found.

Output format:
```
[doc-links] FAIL: docs/runbooks/README.md:19 -> ../DEV_SETUP_TYPO.md (file not found)
[doc-links] OK: 142 links checked, 0 broken
```

### check-doc-freshness.sh

1. Read `docs/DOC_REGISTRY.md` to get the list of covered doc paths.
2. For each path, check that the file exists and contains all four required
   markers (`Status:`, `Owner:`, `Updated:`, `Review cadence:`) in the
   pre-heading block.
3. Report missing markers per file.
4. Exit non-zero if any covered doc is missing or has incomplete metadata.

Output format:
```
[doc-freshness] FAIL: docs/runbooks/browser-verification.md — missing: Owner, Updated, Review cadence
[doc-freshness] OK: 16 covered docs checked, all complete
```

## CI Wiring

New `.github/workflows/doc-guardrails.yml`:
- Triggers on: pull_request targeting main/master, push to main/master.
- Single job with two steps: run check-doc-links.sh, run check-doc-freshness.sh.
- Both steps are blocking (no continue-on-error).

## Deliverables

1. `scripts/check-doc-links.sh`
2. `scripts/check-doc-freshness.sh`
3. `docs/DOC_REGISTRY.md`
4. Metadata headers added to all 16 covered docs
5. `.github/workflows/doc-guardrails.yml`
6. `docs/runbooks/doc-guardrails.md`
7. Updated `docs/DEV_SETUP.md` and `docs/runbooks/README.md`
