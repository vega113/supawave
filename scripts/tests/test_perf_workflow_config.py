import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "perf.yml"


class PerfWorkflowConfigTest(unittest.TestCase):
  @staticmethod
  def _step_window(workflow: str, step_name: str, line_count: int = 18) -> str:
    lines = workflow.splitlines()
    marker = f"      - name: {step_name}"
    index = lines.index(marker)
    return "\n".join(lines[index:index + line_count])

  def test_perf_workflow_contains_grafana_observability_steps(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn("Start perf metrics exporter", workflow)
    self.assertIn("Render perf Alloy config", workflow)
    self.assertIn("Start Grafana Alloy", workflow)
    self.assertIn("Upsert Grafana dashboard", workflow)
    self.assertIn("GCLOUD_HOSTED_METRICS_URL", workflow)
    self.assertIn("GRAFANA_DASHBOARD_API_TOKEN", workflow)

  def test_perf_workflow_keeps_artifact_upload_on_always(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    upload_step = self._step_window(workflow, "Upload performance results")

    self.assertIn("- name: Upload performance results", upload_step)
    self.assertIn("if: always()", upload_step)

  def test_perf_workflow_uses_env_reference_for_alloy_api_key(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn('--api-key-env-var "GCLOUD_RW_API_KEY"', workflow)
    self.assertNotIn('--api-key "$GCLOUD_RW_API_KEY"', workflow)

  def test_perf_workflow_normalizes_branch_labels_for_prs(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
    render_step = self._step_window(workflow, "Render perf Alloy config")

    self.assertIn("PERF_BRANCH_NAME:", workflow)
    self.assertIn("github.head_ref || github.ref_name", workflow)
    self.assertIn('--branch "${PERF_BRANCH_NAME}"', render_step)

  def test_perf_workflow_installs_alloy_to_runner_temp_bin(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn('mkdir -p "$RUNNER_TEMP/bin"', workflow)
    self.assertIn('install "$RUNNER_TEMP/alloy/alloy-linux-amd64" "$RUNNER_TEMP/bin/alloy"', workflow)
    self.assertIn('echo "$RUNNER_TEMP/bin" >> "$GITHUB_PATH"', workflow)

  def test_perf_workflow_verifies_alloy_checksum_before_installing(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
    install_step = self._step_window(workflow, "Install Grafana Alloy")

    self.assertIn('curl -fsSL -o "$RUNNER_TEMP/alloy-linux-amd64.zip"', install_step)
    self.assertIn("SHA256SUMS", install_step)
    self.assertIn("grep 'alloy-linux-amd64.zip$' SHA256SUMS", install_step)
    self.assertIn("sha256sum -c -", install_step)
    self.assertIn('unzip -q "$RUNNER_TEMP/alloy-linux-amd64.zip"', install_step)
    self.assertNotIn('curl -fsSL -o "$RUNNER_TEMP/alloy.zip"', install_step)
    self.assertNotIn('unzip "$RUNNER_TEMP/alloy.zip"', install_step)

  def test_perf_workflow_scopes_secrets_away_from_job_env(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
    job_block = workflow.split("\n    steps:\n", 1)[0]
    start_step = self._step_window(workflow, "Start Grafana Alloy")
    upsert_step = self._step_window(workflow, "Upsert Grafana dashboard")

    self.assertNotIn("GCLOUD_RW_API_KEY: ${{ secrets.GCLOUD_RW_API_KEY }}", job_block)
    self.assertNotIn(
        "GRAFANA_DASHBOARD_API_TOKEN: ${{ secrets.GRAFANA_DASHBOARD_API_TOKEN }}",
        job_block,
    )
    self.assertIn("HAS_GCLOUD_RW_API_KEY", job_block)
    self.assertIn("HAS_GRAFANA_DASHBOARD_API_TOKEN", job_block)
    self.assertIn("GCLOUD_RW_API_KEY: ${{ secrets.GCLOUD_RW_API_KEY }}", start_step)
    self.assertIn(
        "GRAFANA_DASHBOARD_API_TOKEN: ${{ secrets.GRAFANA_DASHBOARD_API_TOKEN }}",
        upsert_step,
    )

  def test_perf_workflow_treats_dashboard_upsert_as_best_effort(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn("- name: Upsert Grafana dashboard", workflow)
    self.assertIn("continue-on-error: true", workflow)


if __name__ == "__main__":
  unittest.main()
