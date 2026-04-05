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
