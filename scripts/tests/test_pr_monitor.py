import pathlib
import subprocess
import unittest
from unittest.mock import patch

from scripts.pr_monitor import LauncherConfig
from scripts.pr_monitor import build_codex_command
from scripts.pr_monitor import build_monitor_paths
from scripts.pr_monitor import build_pane_title
from scripts.pr_monitor import build_runner_script
from scripts.pr_monitor import build_live_head_branch_name
from scripts.pr_monitor import build_launcher_config
from scripts.pr_monitor import list_window_panes
from scripts.pr_monitor import parse_args
from scripts.pr_monitor import render_prompt


class PrMonitorTest(unittest.TestCase):
    def test_render_prompt_requires_actual_merge_or_real_blocker(self) -> None:
        prompt = render_prompt(
            repo="vega113/supawave",
            pr_number=405,
            pr_title="Fix monitor reliability",
            worktree_path="/tmp/worktree",
        )

        self.assertIn("Goal: drive this PR all the way to merged state.", prompt)
        self.assertIn("Keep monitoring until the PR is actually merged", prompt)
        self.assertIn("truly blocked", prompt)
        self.assertIn("work only in the assigned worktree", prompt)

    def test_build_codex_command_uses_explicit_dangerous_launch(self) -> None:
        command = build_codex_command()

        self.assertIn("exec", command)
        self.assertIn("--dangerously-bypass-approvals-and-sandbox", command)
        self.assertIn("--sandbox", command)
        self.assertIn("danger-full-access", command)
        self.assertNotIn("--full-auto", command)

    def test_build_monitor_paths_uses_persistent_runner_location(self) -> None:
        paths = build_monitor_paths(
            shared_root=pathlib.Path("/tmp/pr-monitors"),
            monitor_name="pr405-live-monitor",
        )

        self.assertEqual(
            pathlib.Path("/tmp/pr-monitors/runners/pr405-live-monitor.sh"),
            paths.runner_path,
        )
        self.assertEqual(
            pathlib.Path("/tmp/pr-monitors/prompts/pr405-live-monitor.txt"),
            paths.prompt_path,
        )
        self.assertEqual(
            pathlib.Path("/tmp/pr-monitors/logs/pr405-live-monitor.log"),
            paths.log_path,
        )

    def test_build_runner_script_restarts_when_pr_stays_open(self) -> None:
        config = LauncherConfig(
            repo="vega113/supawave",
            pr_number=405,
            pr_title="Fix monitor reliability",
            worktree_path=pathlib.Path("/tmp/worktree"),
            prompt_path=pathlib.Path("/tmp/pr-monitors/prompts/pr405-live-monitor.txt"),
            log_path=pathlib.Path("/tmp/pr-monitors/logs/pr405-live-monitor.log"),
            runner_path=pathlib.Path("/tmp/pr-monitors/runners/pr405-live-monitor.sh"),
            pane_title="PR #405 Fix monitor reliability",
            pr_head_oid="deadbeef",
        )

        script = build_runner_script(config)

        self.assertIn("while true; do", script)
        self.assertIn("gh pr view \"$PR_NUMBER\"", script)
        self.assertIn("PROMPT=\"$(cat \"$PROMPT_PATH\")\"", script)
        self.assertIn("PR is still open", script)
        self.assertIn("sleep \"$RESTART_DELAY_SECONDS\"", script)
        self.assertIn("PR merged at", script)
        self.assertIn("closed without merge", script)

    def test_build_runner_script_fails_fast_if_worktree_cd_fails(self) -> None:
        config = LauncherConfig(
            repo="vega113/supawave",
            pr_number=405,
            pr_title="Fix monitor reliability",
            worktree_path=pathlib.Path("/tmp/worktree"),
            prompt_path=pathlib.Path("/tmp/pr-monitors/prompts/pr405-live-monitor.txt"),
            log_path=pathlib.Path("/tmp/pr-monitors/logs/pr405-live-monitor.log"),
            runner_path=pathlib.Path("/tmp/pr-monitors/runners/pr405-live-monitor.sh"),
            pane_title="PR #405 Fix monitor reliability",
            pr_head_oid="deadbeef",
        )

        script = build_runner_script(config)

        self.assertIn("set -euo pipefail", script)
        self.assertIn("cd \"$WORKTREE\"", script)

    def test_list_window_panes_tolerates_tabs_in_pane_title(self) -> None:
        tmux_output = "%1\tPR #405\tFix monitor reliability\tzsh\t0\n"

        with patch(
            "scripts.pr_monitor.run_tmux",
            return_value=subprocess.CompletedProcess(
                args=["tmux"],
                returncode=0,
                stdout=tmux_output,
                stderr="",
            ),
        ):
            panes = list_window_panes("vibe-code", "wave-pr-monitor")

        self.assertEqual(
            [
                {
                    "pane_id": "%1",
                    "pane_title": "PR #405\tFix monitor reliability",
                    "pane_command": "zsh",
                    "pane_dead": "0",
                }
            ],
            panes,
        )

    def test_build_launcher_config_rejects_monitor_name_path_escape(self) -> None:
        args = parse_args(
            [
                "start",
                "--pr-number",
                "405",
                "--worktree",
                "/tmp/pr405-live-head",
                "--monitor-name",
                "../../escape",
            ]
        )

        with patch(
            "scripts.pr_monitor.fetch_pr_metadata",
            return_value={"title": "Fix monitor reliability", "headRefOid": "deadbeef"},
        ):
            with self.assertRaises(ValueError):
                build_launcher_config(args)

    def test_parse_args_rejects_non_positive_integers(self) -> None:
        with self.assertRaises(SystemExit):
            parse_args(
                [
                    "start",
                    "--pr-number",
                    "0",
                    "--worktree",
                    "/tmp/pr405-live-head",
                ]
            )

        with self.assertRaises(SystemExit):
            parse_args(
                [
                    "start",
                    "--pr-number",
                    "405",
                    "--worktree",
                    "/tmp/pr405-live-head",
                    "--restart-delay-seconds",
                    "0",
                ]
            )

    def test_parse_args_defaults_repo_to_supawave(self) -> None:
        args = parse_args(
            [
                "start",
                "--pr-number",
                "405",
                "--worktree",
                "/tmp/pr405-live-head",
            ]
        )

        self.assertEqual("vega113/supawave", args.repo)

    def test_build_pane_title_includes_pr_number_and_title(self) -> None:
        self.assertEqual(
            "PR #405 Fix monitor reliability",
            build_pane_title(405, "Fix monitor reliability"),
        )

    def test_build_live_head_branch_name_uses_pr_number(self) -> None:
        self.assertEqual("pr405-live-head", build_live_head_branch_name(405))


if __name__ == "__main__":
    unittest.main()
