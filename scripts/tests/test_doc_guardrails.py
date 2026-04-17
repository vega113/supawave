import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
CHECK_DOC_LINKS = REPO_ROOT / "scripts" / "check-doc-links.sh"
CHECK_DOC_FRESHNESS = REPO_ROOT / "scripts" / "check-doc-freshness.sh"
DEFAULT_SUBPROCESS_TIMEOUT_SECONDS = 30


def run_command(
    args: list[str], *, cwd: Path, timeout_context: str
) -> subprocess.CompletedProcess[str]:
  try:
    return subprocess.run(
        args,
        cwd=cwd,
        check=False,
        text=True,
        capture_output=True,
        timeout=DEFAULT_SUBPROCESS_TIMEOUT_SECONDS,
    )
  except subprocess.TimeoutExpired as exc:
    raise AssertionError(
        f"{timeout_context} timed out after "
        f"{DEFAULT_SUBPROCESS_TIMEOUT_SECONDS}s: {args}"
    ) from exc


class DocGuardrailsScriptTest(unittest.TestCase):
  def setUp(self) -> None:
    self.temp_dir = Path(tempfile.mkdtemp(prefix="doc-guardrails-"))
    self.repo = self.temp_dir / "repo"
    (self.repo / "docs").mkdir(parents=True, exist_ok=True)
    (self.repo / "scripts").mkdir(parents=True, exist_ok=True)
    shutil.copy2(CHECK_DOC_LINKS, self.repo / "scripts" / CHECK_DOC_LINKS.name)
    shutil.copy2(
        CHECK_DOC_FRESHNESS,
        self.repo / "scripts" / CHECK_DOC_FRESHNESS.name,
    )

  def tearDown(self) -> None:
    shutil.rmtree(self.temp_dir)

  def test_check_doc_links_accepts_titles_and_angle_brackets(self) -> None:
    self._write("README.md", "# Root\n")
    self._write("docs/sub dir/other.md", "# Other\n")
    self._write(
        "docs/guide.md",
        textwrap.dedent(
            """
            [Root](../README.md "repo root")
            [Other](<sub dir/other.md> 'nested doc')
            """
        ).strip()
        + "\n",
    )

    result = self._run_script(CHECK_DOC_LINKS.name)

    self.assertEqual(0, result.returncode)
    self.assertIn("[doc-links] 2 links checked, 0 broken", result.stdout)
    self.assertEqual("", result.stderr)

  def test_check_doc_links_reports_broken_links(self) -> None:
    self._write("docs/guide.md", "[Missing](missing.md)\n")

    result = self._run_script(CHECK_DOC_LINKS.name)

    self.assertEqual(1, result.returncode)
    self.assertIn(
        "[doc-links] FAIL: docs/guide.md:1 -> missing.md (file not found)",
        result.stdout,
    )
    self.assertIn("[doc-links] 1 links checked, 1 broken", result.stdout)

  def test_check_doc_links_skips_excluded_directories(self) -> None:
    self._write(
        "docs/superpowers/plans/frozen.md",
        "[Stale](../../missing.md)\n",
    )

    result = self._run_script(CHECK_DOC_LINKS.name)

    self.assertEqual(0, result.returncode)
    self.assertIn("[doc-links] 0 links checked, 0 broken", result.stdout)

  def test_check_doc_freshness_stops_after_covered_docs_section(self) -> None:
    self._write(
        "docs/DOC_REGISTRY.md",
        textwrap.dedent(
            """
            ## Covered docs

            docs/runbooks/example.md

            ## Notes

            this should not be parsed as a path
            """
        ).strip()
        + "\n",
    )
    self._write(
        "docs/runbooks/example.md",
        textwrap.dedent(
            """
            Status: Current
            Owner: Project Maintainers
            Updated: 2026-04-17
            Review cadence: quarterly

            # Example
            """
        ).strip()
        + "\n",
    )

    result = self._run_script(CHECK_DOC_FRESHNESS.name)

    self.assertEqual(0, result.returncode)
    self.assertIn("[doc-freshness] 1 covered docs checked, 0 incomplete", result.stdout)

  def test_check_doc_freshness_reports_missing_markers(self) -> None:
    self._write(
        "docs/DOC_REGISTRY.md",
        textwrap.dedent(
            """
            ## Covered docs

            docs/runbooks/example.md
            """
        ).strip()
        + "\n",
    )
    self._write(
        "docs/runbooks/example.md",
        textwrap.dedent(
            """
            Status: Current
            Updated: 2026-04-17

            # Example
            """
        ).strip()
        + "\n",
    )

    result = self._run_script(CHECK_DOC_FRESHNESS.name)

    self.assertEqual(1, result.returncode)
    self.assertIn(
        "[doc-freshness] FAIL: docs/runbooks/example.md — missing: Owner:, Review cadence:",
        result.stdout,
    )
    self.assertIn("[doc-freshness] 1 covered docs checked, 1 incomplete", result.stdout)

  def _run_script(self, script_name: str) -> subprocess.CompletedProcess[str]:
    return run_command(
        ["bash", str(self.repo / "scripts" / script_name)],
        cwd=self.repo,
        timeout_context=f"{script_name} invocation",
    )

  def _write(self, relative_path: str, content: str) -> None:
    target = self.repo / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


if __name__ == "__main__":
  unittest.main()
