import unittest

from scripts.repair_corrupted_waves import classify_group
from scripts.repair_corrupted_waves import collect_safe_repairs


def make_group(applied_version, docs, later_hashes):
  return {
      "waveid": "supawave.ai/test",
      "waveletid": "supawave.ai!conv+root",
      "applied_version": applied_version,
      "docs": docs,
      "later_hashes": set(later_hashes),
  }


class DuplicateGroupClassificationTest(unittest.TestCase):
  def test_safe_group_keeps_single_surviving_replay_branch(self):
    docs = [
        type("Doc", (), {
            "doc_id": "old",
            "author": "vega@supawave.ai",
            "application_ts": 1,
            "applied_version": 38,
            "applied_hash": "H38",
            "resulting_version": 39,
            "resulting_hash": "dead",
            "op_types": ["WaveletBlipOperation"],
            "blip_ids": ["b+abc"],
            "op_count": 1,
        })(),
        type("Doc", (), {
            "doc_id": "new",
            "author": "vega@supawave.ai",
            "application_ts": 2,
            "applied_version": 38,
            "applied_hash": "H38",
            "resulting_version": 39,
            "resulting_hash": "live",
            "op_types": ["WaveletBlipOperation"],
            "blip_ids": ["b+abc"],
            "op_count": 1,
        })(),
    ]
    result = classify_group(make_group(38, docs, {"live"}))
    self.assertEqual("safe", result.status)
    self.assertEqual("new", result.keep_doc_id)
    self.assertEqual(["old"], result.drop_doc_ids)

  def test_ambiguous_when_multiple_branches_continue(self):
    docs = [
        type("Doc", (), {
            "doc_id": "a",
            "author": "vega@supawave.ai",
            "application_ts": 1,
            "applied_version": 233,
            "applied_hash": "H233",
            "resulting_version": 234,
            "resulting_hash": "left",
            "op_types": ["WaveletBlipOperation"],
            "blip_ids": ["b+abc"],
            "op_count": 1,
        })(),
        type("Doc", (), {
            "doc_id": "b",
            "author": "vega@supawave.ai",
            "application_ts": 2,
            "applied_version": 233,
            "applied_hash": "H233",
            "resulting_version": 234,
            "resulting_hash": "right",
            "op_types": ["WaveletBlipOperation"],
            "blip_ids": ["b+abc"],
            "op_count": 1,
        })(),
    ]
    result = classify_group(make_group(233, docs, {"left", "right"}))
    self.assertEqual("ambiguous", result.status)
    self.assertIsNone(result.keep_doc_id)

  def test_ambiguous_when_replay_shape_differs(self):
    docs = [
        type("Doc", (), {
            "doc_id": "cursor-close",
            "author": "register3@supawave.ai",
            "application_ts": 1,
            "applied_version": 297,
            "applied_hash": "H297",
            "resulting_version": 298,
            "resulting_hash": "dead",
            "op_types": ["WaveletBlipOperation"],
            "blip_ids": ["b+cursor"],
            "op_count": 1,
        })(),
        type("Doc", (), {
            "doc_id": "bot-reply",
            "author": "gpt-ts-bot@supawave.ai",
            "application_ts": 2,
            "applied_version": 297,
            "applied_hash": "H297",
            "resulting_version": 301,
            "resulting_hash": "live",
            "op_types": [
                "WaveletBlipOperation",
                "WaveletBlipOperation",
                "WaveletBlipOperation",
                "WaveletBlipOperation",
            ],
            "blip_ids": ["conversation", "b+reply", "conversation", "b+reply"],
            "op_count": 4,
        })(),
    ]
    result = classify_group(make_group(297, docs, {"live"}))
    self.assertEqual("ambiguous", result.status)
    self.assertIn("single-author single-op replay", result.reason)

  def test_collect_safe_repairs_only_includes_fully_safe_waves(self):
    waves = {
        "safe-wave": {
            "waveid": "safe-wave",
            "waveletid": "wavelet",
            "status": "safe",
            "groups": [
                {"drop_doc_ids": ["a", "b"]},
                {"drop_doc_ids": ["c"]},
            ],
        },
        "ambiguous-wave": {
            "waveid": "ambiguous-wave",
            "waveletid": "wavelet",
            "status": "ambiguous",
            "groups": [
                {"drop_doc_ids": ["x"]},
            ],
        },
    }

    repairs = collect_safe_repairs(waves)

    self.assertEqual(
        [{"waveid": "safe-wave", "waveletid": "wavelet", "drop_doc_ids": ["a", "b", "c"]}],
        repairs,
    )


if __name__ == "__main__":
  unittest.main()
