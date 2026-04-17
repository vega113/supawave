#!/usr/bin/env bash
set -euo pipefail

# ── check-doc-links.sh ──────────────────────────────────────────────
# Finds broken intra-repo markdown links under docs/.
# Exits non-zero if any link target is missing.
#
# Excludes frozen snapshot directories (plans, specs, JWT inventories)
# whose source-code links are expected to drift as code evolves.
# ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCS_DIR="$REPO_ROOT/docs"

# Directories excluded from link checking (frozen snapshots with
# source-code-relative links that are not maintained).
EXCLUDE_DIRS=(
  "docs/superpowers/plans"
  "docs/superpowers/specs"
  "docs/JWT"
  "docs/plans"
)

# Individual files excluded (historical docs with stale source-code links
# from pre-Jakarta/pre-SBT era).
EXCLUDE_FILES=(
  "docs/j2cl-gwt3-inventory.md"
  "docs/persistence-topology-audit.md"
)

total=0
broken=0
failures=""

is_excluded() {
  local rel_path="$1"
  for excl in "${EXCLUDE_DIRS[@]}"; do
    if [[ "$rel_path" == "$excl/"* ]]; then
      return 0
    fi
  done
  for excl in "${EXCLUDE_FILES[@]}"; do
    if [[ "$rel_path" == "$excl" ]]; then
      return 0
    fi
  done
  return 1
}

# extract_links FILE
# Outputs: LINE_NUMBER<space>TARGET for each markdown link in FILE,
# skipping URLs, mailto, and anchor-only references.
extract_links() {
  local file="$1"
  awk '{
    line = $0
    lnum = NR
    while (match(line, /\[[^\]]*\]\([^)]+\)/)) {
      full = substr(line, RSTART, RLENGTH)
      paren = index(full, "(")
      target = substr(full, paren + 1, length(full) - paren - 1)
      # Strip optional Markdown link title: path "title" or path 'title'
      sub(/ .*$/, "", target)
      line = substr(line, RSTART + RLENGTH)
      if (target ~ /^https?:\/\// || target ~ /^mailto:/) continue
      if (target ~ /^#/) continue
      print lnum " " target
    }
  }' "$file"
}

while IFS= read -r md_file; do
  rel_file="${md_file#"$REPO_ROOT"/}"

  if is_excluded "$rel_file"; then
    continue
  fi

  file_dir="$(dirname "$md_file")"

  while read -r line_num target; do
    [ -z "$target" ] && continue

    # Strip anchor fragment from the path
    path_part="${target%%#*}"
    [ -z "$path_part" ] && continue

    # Resolve relative to the file's directory
    resolved="$file_dir/$path_part"

    total=$((total + 1))

    if [ ! -e "$resolved" ]; then
      echo "[doc-links] FAIL: $rel_file:$line_num -> $target (file not found)"
      broken=$((broken + 1))
      failures="yes"
    fi
  done < <(extract_links "$md_file")
done < <(find "$DOCS_DIR" -name '*.md' | sort)

echo "[doc-links] $total links checked, $broken broken"

if [ -n "$failures" ]; then
  exit 1
fi
