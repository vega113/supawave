#!/usr/bin/env bash
set -euo pipefail

# ── check-doc-freshness.sh ──────────────────────────────────────────
# Verifies that every doc listed in docs/DOC_REGISTRY.md has the
# required owner/freshness metadata block in its first 10 lines.
# Exits non-zero if any covered doc is missing or has incomplete metadata.
# ─────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REGISTRY="$REPO_ROOT/docs/DOC_REGISTRY.md"

REQUIRED_MARKERS=("Status:" "Owner:" "Updated:" "Review cadence:")

if [ ! -f "$REGISTRY" ]; then
  echo "[doc-freshness] ERROR: registry not found at docs/DOC_REGISTRY.md" >&2
  exit 2
fi

# Parse the registry: take lines after "## Covered docs", skip blanks,
# comments, and the HTML comment line.
in_section=0
doc_paths=()
while IFS= read -r line; do
  if [[ "$line" == "## Covered docs"* ]]; then
    in_section=1
    continue
  fi
  if [[ "$in_section" -eq 1 && "$line" == "## "* ]]; then
    break
  fi
  [ "$in_section" -eq 0 ] && continue

  # Skip blanks, comments, and HTML comment lines
  [[ -z "$line" ]] && continue
  [[ "$line" == "#"* ]] && continue
  [[ "$line" == "<!--"* ]] && continue

  doc_paths+=("$line")
done < "$REGISTRY"

if [ ${#doc_paths[@]} -eq 0 ]; then
  echo "[doc-freshness] ERROR: no doc paths found in registry" >&2
  exit 2
fi

total=${#doc_paths[@]}
failed=0
failures=""

for doc_path in "${doc_paths[@]}"; do
  full_path="$REPO_ROOT/$doc_path"

  if [ ! -f "$full_path" ]; then
    echo "[doc-freshness] FAIL: $doc_path — file not found"
    failed=$((failed + 1))
    failures="yes"
    continue
  fi

  # Read the first 10 lines
  header="$(head -n 10 "$full_path")"

  missing=()
  for marker in "${REQUIRED_MARKERS[@]}"; do
    if ! echo "$header" | grep -q "^${marker}"; then
      missing+=("$marker")
    fi
  done

  if [ ${#missing[@]} -gt 0 ]; then
    missing_str=""
    for m in "${missing[@]}"; do
      [ -n "$missing_str" ] && missing_str="$missing_str, "
      missing_str="$missing_str${m}"
    done
    echo "[doc-freshness] FAIL: $doc_path — missing: $missing_str"
    failed=$((failed + 1))
    failures="yes"
  fi
done

echo "[doc-freshness] $total covered docs checked, $failed incomplete"

if [ -n "$failures" ]; then
  exit 1
fi
