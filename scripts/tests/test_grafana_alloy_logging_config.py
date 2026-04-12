import json
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ALLOY_CONFIG_SCRIPT = REPO_ROOT / "deploy" / "supawave-host" / "configure-grafana-alloy.sh"
LOGBACK_CONFIG = REPO_ROOT / "wave" / "config" / "logback.xml"


def _jar(*parts: str) -> Path:
  return REPO_ROOT.joinpath(".coursier-cache", "https", "repo1.maven.org", "maven2", *parts)


def _resolve_versioned_jar(*parts: str) -> Path:
  directory = _jar(*parts)
  artifact = parts[-1]
  matches = sorted(directory.glob(f"*/{artifact}-*.jar"))
  if not matches:
    raise unittest.SkipTest(
        "Missing probe dependency. Run `sbt -batch -no-colors 'show Runtime / fullClasspath'` first.\n"
        + str(directory)
    )
  return matches[-1]


def _probe_classpath() -> str:
  jars = [
      _resolve_versioned_jar("org", "slf4j", "slf4j-api"),
      _resolve_versioned_jar("ch", "qos", "logback", "logback-classic"),
      _resolve_versioned_jar("ch", "qos", "logback", "logback-core"),
      _resolve_versioned_jar("net", "logstash", "logback", "logstash-logback-encoder"),
      _resolve_versioned_jar("com", "fasterxml", "jackson", "core", "jackson-databind"),
      _resolve_versioned_jar("com", "fasterxml", "jackson", "core", "jackson-core"),
      _resolve_versioned_jar("com", "fasterxml", "jackson", "core", "jackson-annotations"),
  ]
  return os.pathsep.join(str(path) for path in jars)


def emit_structured_log_entry() -> dict:
  with tempfile.TemporaryDirectory(prefix="grafana-logback-probe-") as tmpdir_str:
    tmpdir = Path(tmpdir_str)
    probe_path = tmpdir / "Probe.java"
    probe_path.write_text(
        textwrap.dedent(
            """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            import org.slf4j.MDC;

            public final class Probe {
              public static void main(String[] args) {
                Logger log = LoggerFactory.getLogger("ProbeLogJson");
                MDC.put("participantId", "alice@example.com");
                MDC.put("waveId", "wave-test");
                log.info("json-proof-message");
                MDC.clear();
              }
            }
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    classpath = _probe_classpath()
    subprocess.run(
        ["javac", "-cp", classpath, str(probe_path)],
        check=True,
        cwd=tmpdir,
    )
    subprocess.run(
        [
            "java",
            "-cp",
            f"{classpath}{os.pathsep}{tmpdir}",
            f"-Dlogback.configurationFile={LOGBACK_CONFIG}",
            "Probe",
        ],
        check=True,
        cwd=tmpdir,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    line = (tmpdir / "logs" / "wave-json.log").read_text(encoding="utf-8").strip()
    return json.loads(line)


class GrafanaAlloyLoggingConfigTest(unittest.TestCase):
  def test_logback_emits_microsecond_precision_json_timestamp(self):
    entry = emit_structured_log_entry()

    self.assertEqual("json-proof-message", entry["message"])
    self.assertRegex(
        entry["timestamp"],
        r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3,9}(?:Z|[+-]\d{2}:\d{2})$",
    )

  def test_alloy_timestamp_parser_matches_wave_json_timestamp_precision(self):
    script_text = ALLOY_CONFIG_SCRIPT.read_text(encoding="utf-8")

    self.assertIn('WAVE_TIMESTAMP_FORMAT=${WAVE_TIMESTAMP_FORMAT:-RFC3339Nano}', script_text)
    self.assertIn('format = \\"$WAVE_TIMESTAMP_FORMAT\\"', script_text)

  def test_alloy_file_targets_use_internal_path_labels_and_service_name(self):
    script_text = ALLOY_CONFIG_SCRIPT.read_text(encoding="utf-8")

    self.assertRegex(script_text, r'__path__\s*=\s*\\"/var/log/\{syslog,messages,\*\.log\}\\"')
    self.assertRegex(script_text, r'__path__\s*=\s*\\"\$WAVE_LOG_PATH\\"')
    self.assertRegex(script_text, r'service_name\s*=\s*\\"supawave\\"')
    self.assertNotRegex(script_text, r'(?m)^\s*path\s*=\s*\\"\$WAVE_LOG_PATH\\"')


if __name__ == "__main__":
  unittest.main()
