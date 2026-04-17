#!/usr/bin/env bash
set -euo pipefail

# -- check-doc-links.sh --------------------------------------------------
# Finds broken intra-repo markdown links under docs/.
# Exits non-zero if any link target is missing.
#
# Excludes frozen snapshot directories (plans, specs, JWT inventories)
# whose source-code links are expected to drift as code evolves.
# -----------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT_REAL="$(cd "$REPO_ROOT" && pwd -P)"
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

canonicalize_existing_path() {
  local candidate="$1"

  [ -e "$candidate" ] || return 1

  python3 - "$candidate" <<'PY'
import os
import sys

print(os.path.realpath(sys.argv[1]))
PY
}

# extract_links FILE
# Outputs: LINE_NUMBER<space>TARGET for each markdown link in FILE,
# skipping URLs, mailto, and anchor-only references.
extract_links() {
  local file="$1"
  awk '
  BEGIN { in_fence = 0 }

  function is_fence_line(value) {
    return value ~ /^[[:space:]]*(```|~~~)/
  }

  function trim(value) {
    sub(/^[[:space:]]+/, "", value)
    sub(/[[:space:]]+$/, "", value)
    return value
  }

  function strip_inline_code(value,   result, i, tick_count, marker, closing_offset) {
    result = ""
    i = 1

    while (i <= length(value)) {
      if (substr(value, i, 1) != "`") {
        result = result substr(value, i, 1)
        i++
        continue
      }

      tick_count = 1
      while (substr(value, i + tick_count, 1) == "`") {
        tick_count++
      }

      marker = substr(value, i, tick_count)
      closing_offset = index(substr(value, i + tick_count), marker)

      if (!closing_offset) {
        result = result substr(value, i, 1)
        i++
        continue
      }

      i += closing_offset + (2 * tick_count) - 1
    }

    return result
  }

  function strip_markdown_title(value, gt) {
    value = trim(value)

    if (value ~ /^<[^>]+>[[:space:]]*$/) {
      return substr(value, 2, length(value) - 2)
    }

    if (value ~ /^<[^>]+>[[:space:]]*("[^"]*"|'\''[^'\'']*'\''|\([^)]*\))$/) {
      gt = index(value, ">")
      return substr(value, 2, gt - 2)
    }

    sub(/[[:space:]]+"[^"]*"$/, "", value)
    sub(/[[:space:]]+'\''[^'\'']*'\''$/, "", value)
    sub(/[[:space:]]+\([^)]*\)$/, "", value)
    value = trim(value)

    if (value ~ /^<[^>]+>$/) {
      value = substr(value, 2, length(value) - 2)
    }

    return value
  }

  function extract_next_target(text,   start, open_index, target_start, i, ch, depth) {
    link_found = 0
    link_complete = 0
    link_match_end = 0

    start = match(text, /\[[^][]*\]\(/)
    if (!start) {
      return ""
    }

    link_found = 1
    open_index = start + RLENGTH - 1
    target_start = open_index + 1
    depth = 1

    for (i = target_start; i <= length(text); i++) {
      ch = substr(text, i, 1)

      if (ch == "\\" && i < length(text)) {
        i++
        continue
      }

      if (ch == "(") {
        depth++
      } else if (ch == ")") {
        depth--
        if (depth == 0) {
          link_complete = 1
          link_match_end = i
          return substr(text, target_start, i - target_start)
        }
      }
    }

    link_match_end = open_index
    return ""
  }
  {
    if (is_fence_line($0)) {
      in_fence = !in_fence
      next
    }
    if (in_fence) next
    line = strip_inline_code($0)
    lnum = NR
    while (1) {
      target = extract_next_target(line)
      if (!link_found) break
      line = substr(line, link_match_end + 1)
      if (!link_complete) continue
      if (target == "") continue
      target = strip_markdown_title(target)
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

    if ! canonical_resolved="$(canonicalize_existing_path "$resolved")"; then
      echo "[doc-links] FAIL: $rel_file:$line_num -> $target (file not found)"
      broken=$((broken + 1))
      failures="yes"
    elif [[ "$canonical_resolved" != "$REPO_ROOT_REAL" && "$canonical_resolved" != "$REPO_ROOT_REAL/"* ]]; then
      echo "[doc-links] FAIL: $rel_file:$line_num -> $target (outside repository root)"
      broken=$((broken + 1))
      failures="yes"
    fi
  done < <(extract_links "$md_file")
done < <(find "$DOCS_DIR" -name '*.md' | sort)

echo "[doc-links] $total links checked, $broken broken"

if [ -n "$failures" ]; then
  exit 1
fi
