#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys
from dataclasses import dataclass
from typing import Any


DISCOVERY_JS = r"""
const groups = db.deltas.aggregate([
  {$group:{
    _id:{waveid:'$waveid', waveletid:'$waveletid', applied:'$transformed.appliedatversion'},
    count:{$sum:1}
  }},
  {$match:{count:{$gt:1}}},
  {$sort:{'_id.waveid':1,'_id.waveletid':1,'_id.applied':1}}
]).toArray();

for (const group of groups) {
  const docs = db.deltas.find(
    {
      waveid: group._id.waveid,
      waveletid: group._id.waveletid,
      'transformed.appliedatversion': group._id.applied
    }
  ).sort({'transformed.applicationtimestamp': 1, _id: 1}).toArray();

  const later = db.deltas.find(
    {
      waveid: group._id.waveid,
      waveletid: group._id.waveletid,
      'transformed.appliedatversion': {$gt: group._id.applied}
    },
    {
      appliedatversion: 1,
      transformed: 1,
      _id: 0
    }
  ).sort({'transformed.appliedatversion': 1}).toArray();

  print(EJSON.stringify({group, docs, later}));
}
"""


@dataclass
class DuplicateDoc:
  doc_id: str
  author: str
  application_ts: int
  applied_version: int
  applied_hash: str
  resulting_version: int
  resulting_hash: str
  op_types: list[str]
  blip_ids: list[str]
  op_count: int


@dataclass
class GroupClassification:
  status: str
  reason: str
  keep_doc_id: str | None
  drop_doc_ids: list[str]


def unwrap_extended_json(value: Any) -> Any:
  if isinstance(value, dict):
    if "$numberLong" in value:
      return int(value["$numberLong"])
    if "$oid" in value:
      return value["$oid"]
    if "$binary" in value:
      return value["$binary"]["base64"]
    return {k: unwrap_extended_json(v) for k, v in value.items()}
  if isinstance(value, list):
    return [unwrap_extended_json(v) for v in value]
  return value


def normalize_group(raw: dict[str, Any]) -> dict[str, Any]:
  payload = unwrap_extended_json(raw)
  group = payload["group"]
  docs = []
  for doc in payload["docs"]:
    transformed = doc["transformed"]
    ops = transformed["ops"]
    docs.append(
        DuplicateDoc(
            doc_id=doc["_id"],
            author=transformed["author"]["address"],
            application_ts=transformed["applicationtimestamp"],
            applied_version=transformed["appliedatversion"],
            applied_hash=doc["appliedatversion"]["historyhash"],
            resulting_version=transformed["resultingversion"]["version"],
            resulting_hash=transformed["resultingversion"]["historyhash"],
            op_types=[op["type"] for op in ops],
            blip_ids=[op.get("blipid", "") for op in ops],
            op_count=len(ops),
        )
    )
  later_hashes = {
      later_doc["appliedatversion"]["historyhash"]
      for later_doc in payload["later"]
  }
  return {
      "waveid": group["_id"]["waveid"],
      "waveletid": group["_id"]["waveletid"],
      "applied_version": group["_id"]["applied"],
      "docs": docs,
      "raw_docs": payload["docs"],
      "later_hashes": later_hashes,
  }


def classify_group(group: dict[str, Any]) -> GroupClassification:
  docs: list[DuplicateDoc] = group["docs"]
  later_hashes: set[str] = group["later_hashes"]

  winner_ids = [doc.doc_id for doc in docs if doc.resulting_hash in later_hashes]
  if len(set(winner_ids)) > 1:
    return GroupClassification(
        status="ambiguous",
        reason="multiple duplicate branches are referenced by later history",
        keep_doc_id=None,
        drop_doc_ids=[],
    )
  if not winner_ids:
    return GroupClassification(
        status="ambiguous",
        reason="no unique surviving branch found in later history",
        keep_doc_id=None,
        drop_doc_ids=[],
    )

  same_author = len({doc.author for doc in docs}) == 1
  same_resulting_version = len({doc.resulting_version for doc in docs}) == 1
  same_op_type_shape = len({tuple(doc.op_types) for doc in docs}) == 1
  same_blip_shape = len({tuple(doc.blip_ids) for doc in docs}) == 1
  one_op_each = all(doc.op_count == 1 for doc in docs)

  if not (same_author and same_resulting_version and same_op_type_shape and same_blip_shape and one_op_each):
    return GroupClassification(
        status="ambiguous",
        reason="duplicate branches are not a single-author single-op replay shape",
        keep_doc_id=None,
        drop_doc_ids=[],
    )

  keep_doc_id = winner_ids[0]
  drop_doc_ids = [doc.doc_id for doc in docs if doc.doc_id != keep_doc_id]
  return GroupClassification(
      status="safe",
      reason="single surviving branch and duplicate docs look like same-author one-op replay",
      keep_doc_id=keep_doc_id,
      drop_doc_ids=drop_doc_ids,
  )


def classify_waves(groups: list[dict[str, Any]]) -> dict[str, Any]:
  waves: dict[str, dict[str, Any]] = {}
  for group in groups:
    classification = classify_group(group)
    wave = waves.setdefault(
        group["waveid"],
        {
            "waveid": group["waveid"],
            "waveletid": group["waveletid"],
            "groups": [],
            "safe_to_repair": True,
        },
    )
    wave["groups"].append(
        {
            "applied_version": group["applied_version"],
            "status": classification.status,
            "reason": classification.reason,
            "keep_doc_id": classification.keep_doc_id,
            "drop_doc_ids": classification.drop_doc_ids,
            "docs": [
                {
                    "doc_id": doc.doc_id,
                    "author": doc.author,
                    "application_ts": doc.application_ts,
                    "resulting_version": doc.resulting_version,
                    "op_types": doc.op_types,
                    "blip_ids": doc.blip_ids,
                }
                for doc in group["docs"]
            ],
        }
    )
    if classification.status != "safe":
      wave["safe_to_repair"] = False

  for wave in waves.values():
    wave["status"] = "safe" if wave["safe_to_repair"] else "ambiguous"
  return waves


def run_discovery(shell_command: str) -> list[dict[str, Any]]:
  proc = subprocess.run(
      shell_command,
      input=DISCOVERY_JS,
      text=True,
      shell=True,
      executable="/bin/bash",
      capture_output=True,
      check=False,
  )
  if proc.returncode != 0:
    raise RuntimeError(proc.stderr.strip() or proc.stdout.strip() or f"mongo shell failed: {proc.returncode}")

  groups = []
  for line in proc.stdout.splitlines():
    line = line.strip()
    if not line:
      continue
    start = line.find("{")
    if start < 0:
      continue
    groups.append(normalize_group(json.loads(line[start:])))
  return groups


def render_text_report(waves: dict[str, Any]) -> str:
  lines = []
  safe = [wave for wave in waves.values() if wave["status"] == "safe"]
  ambiguous = [wave for wave in waves.values() if wave["status"] != "safe"]
  lines.append(f"safe_to_repair={len(safe)}")
  lines.append(f"ambiguous={len(ambiguous)}")
  for wave in sorted(waves.values(), key=lambda item: item["waveid"]):
    lines.append(f"{wave['status']}: {wave['waveid']}")
    for group in wave["groups"]:
      lines.append(
          f"  applied={group['applied_version']} status={group['status']} reason={group['reason']}"
      )
      if group["keep_doc_id"]:
        lines.append(f"  keep={group['keep_doc_id']} drop={','.join(group['drop_doc_ids'])}")
  return "\n".join(lines)


def collect_safe_repairs(waves: dict[str, Any]) -> list[dict[str, Any]]:
  repairs = []
  for wave in waves.values():
    if wave["status"] != "safe":
      continue
    drop_doc_ids = []
    for group in wave["groups"]:
      drop_doc_ids.extend(group["drop_doc_ids"])
    repairs.append(
        {
            "waveid": wave["waveid"],
            "waveletid": wave["waveletid"],
            "drop_doc_ids": drop_doc_ids,
        }
    )
  return repairs


def apply_safe_repairs(shell_command: str, repairs: list[dict[str, Any]], backup_path: str | None) -> None:
  if not repairs:
    return
  if not backup_path:
    raise RuntimeError("--apply-safe requires --backup-file so dropped docs are recorded")

  backup_payload = {"repairs": repairs}
  with open(backup_path, "w", encoding="utf-8") as fh:
    json.dump(backup_payload, fh, indent=2, sort_keys=True)

  js_lines = []
  for repair in repairs:
    ids = ", ".join(f'ObjectId("{doc_id}")' for doc_id in repair["drop_doc_ids"])
    js_lines.append(
        "db.deltas.deleteMany({_id: {$in: [" + ids + "]}});"
    )
    js_lines.append(
        "db.snapshots.deleteMany({waveId: "
        + json.dumps(repair["waveid"])
        + ", waveletId: "
        + json.dumps(repair["waveletid"])
        + "});"
    )
  proc = subprocess.run(
      shell_command,
      input="\n".join(js_lines) + "\n",
      text=True,
      shell=True,
      executable="/bin/bash",
      capture_output=True,
      check=False,
  )
  if proc.returncode != 0:
    raise RuntimeError(proc.stderr.strip() or proc.stdout.strip() or f"mongo shell failed: {proc.returncode}")


def main() -> int:
  parser = argparse.ArgumentParser(description="Classify duplicate applied-version wavelet histories.")
  parser.add_argument(
      "--mongo-shell",
      default="mongosh --quiet wiab",
      help="Shell command that accepts Mongo JS on stdin and prints JSON lines to stdout.",
  )
  parser.add_argument(
      "--format",
      choices=("text", "json"),
      default="text",
      help="Output format.",
  )
  parser.add_argument(
      "--apply-safe",
      action="store_true",
      help="Delete orphan delta docs only for waves classified as safe.",
  )
  parser.add_argument(
      "--backup-file",
      help="Write repair metadata here before any --apply-safe deletions.",
  )
  args = parser.parse_args()

  waves = classify_waves(run_discovery(args.mongo_shell))
  if args.apply_safe:
    apply_safe_repairs(args.mongo_shell, collect_safe_repairs(waves), args.backup_file)
  if args.format == "json":
    print(json.dumps(waves, indent=2, sort_keys=True))
  else:
    print(render_text_report(waves))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
