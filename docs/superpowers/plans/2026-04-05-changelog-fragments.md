# Changelog Fragment System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the monolithic changelog.json with per-release fragment files to eliminate merge conflicts between concurrent PRs.

**Architecture:** Each changelog entry becomes an individual JSON file in `wave/config/changelog.d/`. An assembler script reads all fragments, sorts by (date desc, releaseId desc), and writes the assembled `changelog.json` as a build artifact. CI runs assemble before validate and build.

**Tech Stack:** Python 3 scripts, GitHub Actions YAML, sbt (unchanged)

---

### Task 1: Create the assembler script

**Files:**
- Create: `scripts/assemble-changelog.py`

- [ ] **Step 1: Write `scripts/assemble-changelog.py`**

```python
#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import argparse
import json
import sys
from pathlib import Path


def assemble(fragments_dir: Path) -> list[dict]:
    entries: list[dict] = []
    for path in sorted(fragments_dir.glob("*.json")):
        with path.open("r", encoding="utf-8") as f:
            entry = json.load(f)
        entries.append(entry)
    # Sort by date descending, then releaseId descending for stable ordering
    entries.sort(key=lambda e: (e.get("date", ""), e.get("releaseId", "")), reverse=True)
    return entries


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Assemble changelog fragments into changelog.json")
    parser.add_argument(
        "--fragments-dir",
        default="wave/config/changelog.d",
        help="Directory containing fragment JSON files (default: wave/config/changelog.d)",
    )
    parser.add_argument(
        "--output",
        default="wave/config/changelog.json",
        help="Output path for assembled changelog (default: wave/config/changelog.json)",
    )
    parser.add_argument(
        "--stdout",
        action="store_true",
        help="Write assembled JSON to stdout instead of --output file",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    fragments_dir = Path(args.fragments_dir)
    if not fragments_dir.is_dir():
        print(f"error: fragments directory not found: {fragments_dir}", file=sys.stderr)
        return 1

    entries = assemble(fragments_dir)
    output = json.dumps(entries, indent=2, ensure_ascii=False) + "\n"

    if args.stdout:
        sys.stdout.write(output)
    else:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(output, encoding="utf-8")
        print(f"assembled {len(entries)} entries -> {output_path}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Make the script executable and test it (dry run)**

```bash
chmod +x scripts/assemble-changelog.py
python3 scripts/assemble-changelog.py --help
```

Expected: prints argparse help text without error.

- [ ] **Step 3: Commit**

```bash
git add scripts/assemble-changelog.py
git commit -m "feat: add changelog fragment assembler script"
```

---

### Task 2: Migrate existing entries to fragment files

**Files:**
- Create: `wave/config/changelog.d/*.json` (59 files)

- [ ] **Step 1: Create the migration script (temporary, not committed)**

Write a one-off Python script that reads `wave/config/changelog.json`, splits each entry into `wave/config/changelog.d/{releaseId}.json`:

```python
#!/usr/bin/env python3
"""One-time migration: split changelog.json into fragment files."""
import json
from pathlib import Path

changelog = Path("wave/config/changelog.json")
fragments_dir = Path("wave/config/changelog.d")
fragments_dir.mkdir(parents=True, exist_ok=True)

entries = json.loads(changelog.read_text(encoding="utf-8"))
for entry in entries:
    release_id = entry["releaseId"]
    fragment_path = fragments_dir / f"{release_id}.json"
    fragment_path.write_text(
        json.dumps(entry, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"  wrote {fragment_path}")

print(f"migrated {len(entries)} entries")
```

- [ ] **Step 2: Run the migration**

```bash
python3 /tmp/migrate-changelog.py
ls wave/config/changelog.d/ | wc -l
```

Expected: `59` files created.

- [ ] **Step 3: Verify round-trip — assembled output matches original**

```bash
python3 scripts/assemble-changelog.py --stdout > /tmp/assembled.json
python3 -c "
import json
original = json.loads(open('wave/config/changelog.json').read())
assembled = json.loads(open('/tmp/assembled.json').read())
assert original == assembled, 'Round-trip mismatch!'
print('Round-trip OK: assembled output matches original changelog.json')
"
```

Expected: `Round-trip OK` message. If it fails, check sort order — the assembler sorts by (date desc, releaseId desc) which must match the existing order.

- [ ] **Step 4: Commit the fragment files**

```bash
git add wave/config/changelog.d/
git commit -m "feat: migrate 59 changelog entries to fragment files"
```

---

### Task 3: Remove changelog.json from git and add to .gitignore

**Files:**
- Modify: `.gitignore`
- Delete (from git): `wave/config/changelog.json`

- [ ] **Step 1: Add `wave/config/changelog.json` to `.gitignore`**

Add this line at the end of the `### config` section in `.gitignore` (after `wave/config/wave.conf`):

```
wave/config/changelog.json
```

- [ ] **Step 2: Remove changelog.json from git tracking (keep the file on disk)**

```bash
git rm --cached wave/config/changelog.json
```

- [ ] **Step 3: Generate the file from fragments (so sbt and other tools still find it)**

```bash
python3 scripts/assemble-changelog.py
```

Expected: `assembled 59 entries -> wave/config/changelog.json`

- [ ] **Step 4: Commit**

```bash
git add .gitignore
git commit -m "chore: remove changelog.json from git, add to .gitignore

changelog.json is now generated from fragment files in changelog.d/"
```

---

### Task 4: Rewrite validate-changelog.py for fragment-only validation

**Files:**
- Modify: `scripts/validate-changelog.py`

- [ ] **Step 1: Rewrite `scripts/validate-changelog.py`**

Replace the entire file. Remove all monolithic-format logic (`load_base_changelog`, `validate_against_base`, `base_entries_support_release_ids`, `--base-ref`). Keep `validate_entry` (schema validation per entry). Add fragment-specific checks: filename matches releaseId, no duplicates across fragments.

```python
#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import argparse
import json
import re
import sys
from pathlib import Path


REQUIRED_KEYS = ("releaseId", "version", "date", "title", "summary", "sections")
ISO_DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def validate_entry(entry: dict, label: str) -> list[str]:
    errors: list[str] = []
    if not isinstance(entry, dict):
        return [f"{label} must be a JSON object"]
    for key in REQUIRED_KEYS:
        if key not in entry:
            errors.append(f"{label} is missing required key '{key}'")
    release_id = entry.get("releaseId")
    if not isinstance(release_id, str) or not release_id.strip():
        errors.append(f"{label} has an invalid releaseId")
    version = entry.get("version")
    if not isinstance(version, str) or not version.strip():
        errors.append(f"{label} has an invalid version")
    date = entry.get("date")
    if not isinstance(date, str) or not ISO_DATE_PATTERN.fullmatch(date.strip()):
        errors.append(f"{label} has an invalid date (expected YYYY-MM-DD)")
    title = entry.get("title")
    if not isinstance(title, str) or not title.strip():
        errors.append(f"{label} has an invalid title")
    summary = entry.get("summary")
    if not isinstance(summary, str) or not summary.strip():
        errors.append(f"{label} has an invalid summary")
    sections = entry.get("sections")
    if not isinstance(sections, list) or len(sections) == 0:
        errors.append(f"{label} must contain a non-empty sections array")
    else:
        for section_index, section in enumerate(sections):
            if not isinstance(section, dict):
                errors.append(f"{label} section {section_index} must be a JSON object")
                continue
            if section.get("type") not in {"feature", "fix"}:
                errors.append(
                    f"{label} section {section_index} must use a 'feature' or 'fix' type"
                )
            items = section.get("items")
            if (
                not isinstance(items, list)
                or len(items) == 0
                or not all(isinstance(item, str) and item.strip() for item in items)
            ):
                errors.append(
                    f"{label} section {section_index} must contain non-empty string items"
                )
    return errors


def validate_fragments(fragments_dir: Path) -> list[str]:
    errors: list[str] = []
    seen_release_ids: set[str] = set()

    fragment_files = sorted(fragments_dir.glob("*.json"))
    if not fragment_files:
        errors.append(f"no fragment files found in {fragments_dir}")
        return errors

    for path in fragment_files:
        label = f"fragment '{path.name}'"
        try:
            with path.open("r", encoding="utf-8") as f:
                entry = json.load(f)
        except (json.JSONDecodeError, OSError) as exc:
            errors.append(f"{label}: failed to read: {exc}")
            continue

        entry_errors = validate_entry(entry, label)
        errors.extend(entry_errors)

        if isinstance(entry, dict):
            release_id = entry.get("releaseId")
            expected_id = path.stem
            if isinstance(release_id, str) and release_id != expected_id:
                errors.append(
                    f"{label}: releaseId '{release_id}' does not match filename '{expected_id}'"
                )
            if isinstance(release_id, str):
                if release_id in seen_release_ids:
                    errors.append(f"duplicate releaseId '{release_id}'")
                else:
                    seen_release_ids.add(release_id)

    return errors


def validate_assembled(changelog_path: Path) -> list[str]:
    errors: list[str] = []
    try:
        with changelog_path.open("r", encoding="utf-8") as f:
            entries = json.load(f)
    except (json.JSONDecodeError, OSError) as exc:
        return [f"failed to read assembled changelog: {exc}"]

    if not isinstance(entries, list):
        return ["assembled changelog root must be a JSON array"]

    previous_date: str | None = None
    for index, entry in enumerate(entries):
        date = entry.get("date") if isinstance(entry, dict) else None
        if isinstance(date, str) and previous_date is not None and date > previous_date:
            release_id = entry.get("releaseId", f"entry {index}")
            errors.append(
                f"assembled entry '{release_id}' has date {date}, "
                f"which is newer than the preceding entry date {previous_date}"
            )
        if isinstance(date, str):
            previous_date = date

    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate SupaWave changelog fragments and assembled output."
    )
    parser.add_argument(
        "--fragments-dir",
        default="wave/config/changelog.d",
        help="Directory containing fragment JSON files (default: wave/config/changelog.d)",
    )
    parser.add_argument(
        "--changelog",
        help="Path to assembled changelog.json to validate ordering",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors: list[str] = []

    fragments_dir = Path(args.fragments_dir)
    if fragments_dir.is_dir():
        errors.extend(validate_fragments(fragments_dir))
    else:
        errors.append(f"fragments directory not found: {fragments_dir}")

    if args.changelog:
        changelog_path = Path(args.changelog)
        if changelog_path.is_file():
            errors.extend(validate_assembled(changelog_path))
        else:
            errors.append(f"assembled changelog not found: {changelog_path}")

    if errors:
        for error in errors:
            print(f"changelog validation error: {error}", file=sys.stderr)
        return 1

    print("changelog validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Test the rewritten validator against the fragment files**

```bash
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected: `changelog validation passed`

- [ ] **Step 3: Commit**

```bash
git add scripts/validate-changelog.py
git commit -m "refactor: rewrite validate-changelog.py for fragment-only validation

Remove monolithic changelog.json support: validate_against_base,
load_base_changelog, --base-ref. Fragments are now the sole source
of truth."
```

---

### Task 5: Update build.yml CI workflow

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Replace the "Validate changelog history" step**

Replace the existing step (lines 27-39) with two steps — assemble then validate:

```yaml
      - name: Assemble changelog from fragments
        run: python3 scripts/assemble-changelog.py

      - name: Validate changelog
        run: python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

This removes the `base_ref` logic and `--base-ref` argument entirely.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "ci: update build.yml to assemble changelog from fragments"
```

---

### Task 6: Update deploy-contabo.yml CI workflow

**Files:**
- Modify: `.github/workflows/deploy-contabo.yml`

- [ ] **Step 1: Replace the "Validate changelog history before deploy" step**

Replace the existing step (lines 69-78) with two steps — assemble then validate:

```yaml
      - name: Assemble changelog from fragments
        if: github.event_name == 'push' || github.event.inputs.action == 'deploy'
        run: python3 scripts/assemble-changelog.py

      - name: Validate changelog
        if: github.event_name == 'push' || github.event.inputs.action == 'deploy'
        run: python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

- [ ] **Step 2: Verify the "Extract changelog for deploy email" step still works**

The extract step (line 311-318) reads `wave/config/changelog.json` which the assemble step generates. No change needed — the file exists on disk after assemble runs.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy-contabo.yml
git commit -m "ci: update deploy-contabo.yml to assemble changelog from fragments"
```

---

### Task 7: End-to-end verification

- [ ] **Step 1: Run the full assemble + validate pipeline locally**

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected: both succeed.

- [ ] **Step 2: Verify sbt still compiles (changelog.json is in place)**

```bash
sbt --batch wave/compile
```

Expected: compiles without error. The `resourceGenerators` task reads `wave/config/changelog.json` which the assemble step generated.

- [ ] **Step 3: Verify staged changelog asset matches**

```bash
sbt --batch Universal/stage
diff -u wave/config/changelog.json target/universal/stage/config/changelog.json
```

Expected: no diff (files are identical).

- [ ] **Step 4: Test adding a new fragment (simulates what a future PR would do)**

```bash
cat > /tmp/test-fragment.json << 'EOF'
{
  "releaseId": "2026-04-05-test-fragment",
  "version": "PR #999",
  "date": "2026-04-05",
  "title": "Test fragment entry",
  "summary": "This is a test to verify the fragment workflow.",
  "sections": [
    {
      "type": "feature",
      "items": ["Verify fragment-based changelog workflow"]
    }
  ]
}
EOF
cp /tmp/test-fragment.json wave/config/changelog.d/2026-04-05-test-fragment.json
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
python3 -c "import json; d=json.load(open('wave/config/changelog.json')); print(f'Entry count: {len(d)}'); print(f'First entry: {d[0][\"releaseId\"]}')"
rm wave/config/changelog.d/2026-04-05-test-fragment.json
python3 scripts/assemble-changelog.py
```

Expected: 60 entries with the test entry at the top, then back to 59 after removal.
