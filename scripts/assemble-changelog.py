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
        try:
            with path.open("r", encoding="utf-8") as f:
                entry = json.load(f)
        except (json.JSONDecodeError, OSError) as exc:
            print(f"error: invalid fragment {path}: {exc}", file=sys.stderr)
            raise SystemExit(1)
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
