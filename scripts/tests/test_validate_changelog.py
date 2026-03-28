import contextlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock


MODULE_PATH = Path(__file__).resolve().parents[1] / "validate-changelog.py"
SPEC = importlib.util.spec_from_file_location("validate_changelog", MODULE_PATH)
validate_changelog = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validate_changelog)


class ValidateChangelogTest(unittest.TestCase):
  def test_validate_entry_rejects_blank_and_non_string_section_items(self):
    entry = {
        "releaseId": "2026-03-28-release-aware-upgrade-notes",
        "version": "PR #405",
        "date": "2026-03-28",
        "title": "Release-Aware Upgrade Notes",
        "summary": "The upgrade popup follows the deployed release.",
        "sections": [{"type": "fix", "items": ["", 42]}],
    }

    errors = validate_changelog.validate_entry(entry, 0)

    self.assertIn(
        "entry 0 section 0 must contain non-empty string items",
        errors,
    )

  def test_main_skips_base_history_validation_when_current_schema_is_invalid(self):
    temp_dir = Path(tempfile.mkdtemp(prefix="validate-changelog-test"))
    changelog_path = temp_dir / "changelog.json"
    changelog_path.write_text("[]", encoding="utf-8")
    current_entries = [
        {
            "version": "PR #405",
            "date": "2026-03-28",
            "title": "Broken Release",
            "summary": "Missing release id.",
            "sections": [{"type": "fix", "items": ["Broken entry"]}],
        }
    ]
    base_entries = [
        {
            "releaseId": "2026-03-27-release-aware-upgrade-notes",
            "version": "PR #405",
            "date": "2026-03-27",
            "title": "Release-Aware Upgrade Notes",
            "summary": "The upgrade popup follows the deployed release.",
            "sections": [{"type": "fix", "items": ["Initial release"]}],
        }
    ]
    stderr = io.StringIO()

    with contextlib.redirect_stderr(stderr):
      with mock.patch.object(
          validate_changelog,
          "parse_args",
          return_value=SimpleNamespace(changelog=str(changelog_path), base_ref="origin/main"),
      ):
        with mock.patch.object(validate_changelog, "load_changelog", return_value=current_entries):
          with mock.patch.object(
              validate_changelog,
              "load_base_changelog",
              return_value=base_entries,
          ):
            with mock.patch.object(
                validate_changelog,
                "base_entries_support_release_ids",
                return_value=True,
            ):
              with mock.patch.object(
                  validate_changelog.subprocess,
                  "check_output",
                  return_value=str(temp_dir),
              ):
                result = validate_changelog.main()

    self.assertEqual(1, result)
    self.assertIn("entry 0 is missing required key 'releaseId'", stderr.getvalue())
    self.assertNotIn("KeyError", stderr.getvalue())
    self.assertNotIn("changelog validation error: 'releaseId'", stderr.getvalue())

  def test_main_does_not_resolve_repo_root_without_base_ref(self):
    temp_dir = Path(tempfile.mkdtemp(prefix="validate-changelog-no-base-ref"))
    changelog_path = temp_dir / "changelog.json"
    changelog_path.write_text("[]", encoding="utf-8")
    stdout = io.StringIO()
    stderr = io.StringIO()
    current_entries = [
        {
            "releaseId": "2026-03-28-release-aware-upgrade-notes",
            "version": "PR #405",
            "date": "2026-03-28",
            "title": "Release-Aware Upgrade Notes",
            "summary": "The upgrade popup follows the deployed release.",
            "sections": [{"type": "fix", "items": ["Initial release"]}],
        }
    ]

    with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
      with mock.patch.object(
          validate_changelog,
          "parse_args",
          return_value=SimpleNamespace(changelog=str(changelog_path), base_ref=None),
      ):
        with mock.patch.object(validate_changelog, "load_changelog", return_value=current_entries):
          with mock.patch.object(
              validate_changelog.subprocess,
              "check_output",
              side_effect=AssertionError("git should not run without --base-ref"),
          ):
            result = validate_changelog.main()

    self.assertEqual(0, result)
    self.assertEqual("", stderr.getvalue())
    self.assertIn("changelog validation passed", stdout.getvalue())


if __name__ == "__main__":
  unittest.main()
