import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN_ROOT = REPO_ROOT / "wave" / "src" / "main" / "java"
JAKARTA_ROOT = REPO_ROOT / "wave" / "src" / "jakarta-overrides" / "java"
BUILD_FILE = REPO_ROOT / "build.sbt"


def java_paths(root: Path) -> set[str]:
  return {
      str(path.relative_to(root)).replace("\\", "/")
      for path in root.rglob("*.java")
  }


def parse_set_entries(text: str, name: str) -> list[str]:
  declaration = re.search(
      rf"val\s+{re.escape(name)}\s*:\s*Set\[String\]\s*=\s*Set\(",
      text,
  )
  if not declaration:
    raise AssertionError(f"missing {name} declaration in build.sbt")

  start = declaration.end()
  depth = 1
  for index in range(start, len(text)):
    char = text[index]
    if char == "(":
      depth += 1
    elif char == ")":
      depth -= 1
      if depth == 0:
        return re.findall(r'"([^"]+)"', text[start:index])

  raise AssertionError(f"unterminated {name} Set(...) declaration in build.sbt")


def build_set_entries(name: str) -> list[str]:
  text = BUILD_FILE.read_text(encoding="utf-8")
  return parse_set_entries(text, name)


class Issue714DuplicateSourcesTest(unittest.TestCase):

  def test_parse_set_entries_accepts_single_line_set(self) -> None:
    text = 'val mainExactExcludes: Set[String] = Set("foo", "bar")'
    self.assertEqual(["foo", "bar"], parse_set_entries(text, "mainExactExcludes"))

  def test_parse_set_entries_fails_closed_when_declaration_missing(self) -> None:
    with self.assertRaisesRegex(AssertionError, "missing mainExactExcludes declaration"):
      parse_set_entries('val jakartaExactExcludes: Set[String] = Set()', "mainExactExcludes")

  def test_no_same_path_java_duplicates_remain(self) -> None:
    duplicates = sorted(java_paths(MAIN_ROOT) & java_paths(JAKARTA_ROOT))
    self.assertEqual([], duplicates)

  def test_exact_duplicate_exclude_sets_are_empty(self) -> None:
    self.assertEqual([], build_set_entries("mainExactExcludes"))
    self.assertEqual([], build_set_entries("jakartaExactExcludes"))


if __name__ == "__main__":
  unittest.main()
