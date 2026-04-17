Status: Current
Owner: Project Maintainers
Updated: 2026-04-17
Review cadence: quarterly

# Doc Guardrails

Two shell scripts enforce documentation quality in CI. Both run on pushes
and PRs targeting `main` and `master` and block merge on failure.

## What the checks do

### check-doc-links.sh

Scans every `.md` file under `docs/` (excluding frozen snapshot
directories) for markdown links that point to non-existent files.

```bash
bash scripts/check-doc-links.sh
```

Excluded directories (historical snapshots with stale source-code links):
- `docs/superpowers/plans/`
- `docs/superpowers/specs/`
- `docs/JWT/`
- `docs/plans/`

Individual exclusions are listed in the script's `EXCLUDE_FILES` array.

### check-doc-freshness.sh

Reads `docs/DOC_REGISTRY.md` and verifies every listed doc has the four
required metadata markers in its first 10 lines:

- `Status:` — e.g. Canonical, Current, In Progress, Draft, Archive
- `Owner:` — e.g. Project Maintainers
- `Updated:` — YYYY-MM-DD format
- `Review cadence:` — e.g. monthly, quarterly, on-change, or quarterly and when workflow conventions change

```bash
bash scripts/check-doc-freshness.sh
```

## How to add a new covered doc

1. Add the repo-relative path to `docs/DOC_REGISTRY.md` under `## Covered docs`.
2. Add the four metadata fields within the first 10 lines of the new doc
   (before or after the opening `#` heading):

   ```text
   Status: Current
   Owner: Project Maintainers
   Updated: 2026-04-17
   Review cadence: quarterly
   ```

3. Run both checks locally to confirm they pass:

   ```bash
   bash scripts/check-doc-links.sh
   bash scripts/check-doc-freshness.sh
   ```

4. Commit the registry entry and the doc together.

## How to interpret failures

### Broken link failure

```text
[doc-links] FAIL: docs/runbooks/README.md:19 -> ../DEV_SETUP_TYPO.md (file not found)
```

The file at `docs/runbooks/README.md` line 19 has a link whose target does
not exist when resolved relative to the file's directory. Fix the link path
or create the missing file.

### Missing metadata failure

```text
[doc-freshness] FAIL: docs/runbooks/browser-verification.md — missing: Owner:, Review cadence:
```

The listed doc is in `docs/DOC_REGISTRY.md` but lacks one or more required
metadata fields in its first 10 lines. Add the missing fields at the top
of the file.

## How to exclude a file from link checking

If a doc legitimately contains links to paths outside the repo (source code
references in historical snapshots), add it to the `EXCLUDE_FILES` or
`EXCLUDE_DIRS` arrays at the top of `scripts/check-doc-links.sh`.

## CI wiring

The checks run in `.github/workflows/doc-guardrails.yml` as blocking steps
on push to `main` or `master` and on PRs targeting either branch. Neither
step uses `continue-on-error` — any failure blocks merge.
