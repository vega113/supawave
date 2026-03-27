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
import subprocess
import sys
from pathlib import Path


REQUIRED_KEYS = ("releaseId", "date", "title", "summary", "sections")


def load_changelog(path: Path) -> list[dict]:
  with path.open("r", encoding="utf-8") as handle:
    data = json.load(handle)
  if not isinstance(data, list):
    raise ValueError("changelog root must be a JSON array")
  return data


def load_base_changelog(ref: str, changelog_path: Path, repo_root: Path) -> list[dict]:
  relative_path = changelog_path.relative_to(repo_root).as_posix()
  result = subprocess.run(
      ["git", "show", f"{ref}:{relative_path}"],
      check=False,
      capture_output=True,
      text=True,
      cwd=repo_root,
  )
  if result.returncode != 0:
    raise ValueError(f"unable to read base changelog from {ref}: {result.stderr.strip()}")
  data = json.loads(result.stdout)
  if not isinstance(data, list):
    raise ValueError("base changelog root must be a JSON array")
  return data


def validate_entry(entry: dict, index: int) -> list[str]:
  errors: list[str] = []
  if not isinstance(entry, dict):
    return [f"entry {index} must be a JSON object"]
  for key in REQUIRED_KEYS:
    if key not in entry:
      errors.append(f"entry {index} is missing required key '{key}'")
  release_id = entry.get("releaseId")
  if not isinstance(release_id, str) or not release_id.strip():
    errors.append(f"entry {index} has an invalid releaseId")
  date = entry.get("date")
  if not isinstance(date, str) or not date.strip():
    errors.append(f"entry {index} has an invalid date")
  title = entry.get("title")
  if not isinstance(title, str) or not title.strip():
    errors.append(f"entry {index} has an invalid title")
  summary = entry.get("summary")
  if not isinstance(summary, str) or not summary.strip():
    errors.append(f"entry {index} has an invalid summary")
  sections = entry.get("sections")
  if not isinstance(sections, list) or len(sections) == 0:
    errors.append(f"entry {index} must contain a non-empty sections array")
  return errors


def validate_schema(entries: list[dict]) -> list[str]:
  errors: list[str] = []
  seen_release_ids: set[str] = set()
  previous_date: str | None = None
  for index, entry in enumerate(entries):
    errors.extend(validate_entry(entry, index))
    release_id = entry.get("releaseId")
    if isinstance(release_id, str):
      if release_id in seen_release_ids:
        errors.append(f"duplicate releaseId '{release_id}'")
      else:
        seen_release_ids.add(release_id)
    date = entry.get("date")
    if isinstance(date, str) and previous_date is not None and date > previous_date:
      errors.append(
          f"release '{release_id}' has date {date}, which is newer than the preceding entry date"
      )
    if isinstance(date, str):
      previous_date = date
  return errors


def validate_against_base(base_entries: list[dict], current_entries: list[dict]) -> list[str]:
  errors: list[str] = []
  base_ids = [entry["releaseId"] for entry in base_entries]
  current_ids = [entry["releaseId"] for entry in current_entries]
  current_set = set(current_ids)

  missing_ids = [release_id for release_id in base_ids if release_id not in current_set]
  if missing_ids:
    errors.append(
        "current changelog removes releaseIds from base history: " + ", ".join(missing_ids)
    )
    return errors

  previous_index = -1
  first_existing_index = None
  for release_id in base_ids:
    current_index = current_ids.index(release_id)
    if current_index <= previous_index:
      errors.append(
          f"releaseId '{release_id}' changed order relative to the base changelog"
      )
      return errors
    if first_existing_index is None:
      first_existing_index = current_index
    previous_index = current_index

  if first_existing_index is None:
    return errors

  base_set = set(base_ids)
  for index in range(first_existing_index, len(current_ids)):
    release_id = current_ids[index]
    if release_id not in base_set:
      errors.append(
          "new releaseIds must be prepended above existing history; "
          f"found '{release_id}' after base entries began"
      )
      break

  return errors


def base_entries_support_release_ids(entries: list[dict]) -> bool:
  for entry in entries:
    release_id = entry.get("releaseId") if isinstance(entry, dict) else None
    if not isinstance(release_id, str) or not release_id.strip():
      return False
  return True


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(description="Validate SupaWave changelog ordering and schema.")
  parser.add_argument("--changelog", required=True, help="Path to wave/config/changelog.json")
  parser.add_argument(
      "--base-ref",
      help="Git ref or SHA to compare against for stale-branch overwrite detection",
  )
  return parser.parse_args()


def main() -> int:
  args = parse_args()
  changelog_path = Path(args.changelog).resolve()
  repo_root = Path(
      subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
  )

  errors: list[str] = []
  try:
    current_entries = load_changelog(changelog_path)
  except Exception as exc:
    print(f"changelog validation failed: {exc}", file=sys.stderr)
    return 1

  errors.extend(validate_schema(current_entries))

  if args.base_ref:
    try:
      base_entries = load_base_changelog(args.base_ref, changelog_path, repo_root)
      if base_entries_support_release_ids(base_entries):
        errors.extend(validate_schema(base_entries))
        errors.extend(validate_against_base(base_entries, current_entries))
      else:
        print(
            "changelog validation note: base changelog is still on the legacy schema; "
            "skipping base-history preservation checks for this run",
            file=sys.stderr,
        )
    except Exception as exc:
      errors.append(str(exc))

  if errors:
    for error in errors:
      print(f"changelog validation error: {error}", file=sys.stderr)
    return 1

  print("changelog validation passed")
  return 0


if __name__ == "__main__":
  sys.exit(main())
