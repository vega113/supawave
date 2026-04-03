import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "jakarta-wrong-edit-guard.sh"


def run_git(repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
  return subprocess.run(
      ["git", "-C", str(repo), *args],
      check=True,
      text=True,
      capture_output=True,
  )


class JakartaWrongEditGuardTest(unittest.TestCase):
  def setUp(self) -> None:
    self.temp_dir = Path(tempfile.mkdtemp(prefix="jakarta-wrong-edit-guard-"))
    self.repo = self.temp_dir / "repo"
    self.repo.mkdir()
    run_git(self.repo, "init")
    run_git(self.repo, "config", "user.name", "Codex Test")
    run_git(self.repo, "config", "user.email", "codex@example.com")
    self._write(
        "build.sbt",
        textwrap.dedent(
            """
            val mainExactExcludes: Set[String] = Set(
              "org/example/Foo.java",
              "org/example/JakartaHttpRequestMessage.java"
            )

            val mainDirExcluded = underMain && (
              p.contains("/org/example/dirshadow/")
            )

            val jakartaExactExcludes: Set[String] = Set(
              "org/example/JakartaHttpRequestMessage.java"
            )
            """
        ).strip()
        + "\n",
    )
    self._write("ORCHESTRATOR.md", "Jakarta override guidance lives here.\n")
    self._write("wave/src/main/java/org/example/Foo.java", "class Foo {}\n")
    self._write("wave/src/jakarta-overrides/java/org/example/Foo.java", "class Foo {}\n")
    self._write(
        "wave/src/main/java/org/example/dirshadow/DirShadow.java",
        "class DirShadow {}\n",
    )
    self._write(
        "wave/src/jakarta-overrides/java/org/example/dirshadow/DirShadow.java",
        "class DirShadow {}\n",
    )
    self._write(
        "wave/src/main/java/org/example/JakartaHttpRequestMessage.java",
        "class JakartaHttpRequestMessage {}\n",
    )
    self._write(
        "wave/src/jakarta-overrides/java/org/example/JakartaHttpRequestMessage.java",
        "class JakartaHttpRequestMessage {}\n",
    )
    run_git(self.repo, "add", ".")
    run_git(self.repo, "commit", "-m", "baseline")

  def tearDown(self) -> None:
    shutil.rmtree(self.temp_dir)

  def test_warns_for_changed_main_file_with_runtime_active_override(self) -> None:
    self._write("wave/src/main/java/org/example/Foo.java", "class Foo { int value = 1; }\n")

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertIn("WARNING", result.stdout)
    self.assertIn("wave/src/main/java/org/example/Foo.java", result.stdout)
    self.assertIn("wave/src/jakarta-overrides/java/org/example/Foo.java", result.stdout)
    self.assertIn("ORCHESTRATOR.md", result.stdout)

  def test_suppresses_warning_when_both_main_and_override_change(self) -> None:
    self._write("wave/src/main/java/org/example/Foo.java", "class Foo { int value = 1; }\n")
    self._write(
        "wave/src/jakarta-overrides/java/org/example/Foo.java",
        "class Foo { int value = 1; }\n",
    )

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def test_skips_known_excluded_jakarta_override_paths(self) -> None:
    self._write(
        "wave/src/main/java/org/example/JakartaHttpRequestMessage.java",
        "class JakartaHttpRequestMessage { int value = 1; }\n",
    )

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def test_does_not_warn_when_no_override_exists(self) -> None:
    self._write(
        "wave/src/main/java/org/example/MainOnly.java",
        "class MainOnly {}\n",
    )
    run_git(self.repo, "add", "wave/src/main/java/org/example/MainOnly.java")
    run_git(self.repo, "commit", "-m", "add main-only file")
    self._write(
        "wave/src/main/java/org/example/MainOnly.java",
        "class MainOnly { int value = 1; }\n",
    )

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def test_warns_for_directory_level_shadowed_files(self) -> None:
    self._write(
        "wave/src/main/java/org/example/dirshadow/DirShadow.java",
        "class DirShadow { int value = 1; }\n",
    )

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertIn("WARNING", result.stdout)
    self.assertIn("wave/src/main/java/org/example/dirshadow/DirShadow.java", result.stdout)

  def test_handles_rename_entries_without_warning(self) -> None:
    run_git(
        self.repo,
        "mv",
        "wave/src/main/java/org/example/Foo.java",
        "wave/src/main/java/org/example/FooRenamed.java",
    )

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def test_skips_delete_only_changes(self) -> None:
    run_git(self.repo, "rm", "wave/src/main/java/org/example/Foo.java")

    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def test_supports_base_ref_mode(self) -> None:
    base_ref = run_git(self.repo, "rev-parse", "HEAD").stdout.strip()
    self._write("wave/src/main/java/org/example/Foo.java", "class Foo { int value = 2; }\n")
    run_git(self.repo, "add", "wave/src/main/java/org/example/Foo.java")
    run_git(self.repo, "commit", "-m", "change main copy")

    result = self._run_guard("--base-ref", base_ref)

    self.assertEqual(0, result.returncode)
    self.assertIn("WARNING", result.stdout)

  def test_returns_success_with_clean_tree(self) -> None:
    result = self._run_guard()

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def test_script_is_executable_and_runs_directly(self) -> None:
    self.assertTrue(os.access(SCRIPT_PATH, os.X_OK))

    result = subprocess.run(
        [str(SCRIPT_PATH), "--repo-root", str(self.repo)],
        text=True,
        capture_output=True,
    )

    self.assertEqual(0, result.returncode)
    self.assertNotIn("WARNING", result.stdout)

  def _run_guard(self, *extra_args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["bash", str(SCRIPT_PATH), "--repo-root", str(self.repo), *extra_args],
        text=True,
        capture_output=True,
    )

  def _write(self, relative_path: str, content: str) -> None:
    target = self.repo / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


if __name__ == "__main__":
  unittest.main()
