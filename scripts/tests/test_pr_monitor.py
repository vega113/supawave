import io
import pathlib
import subprocess
import unittest
from contextlib import redirect_stderr
from contextlib import redirect_stdout
from unittest.mock import patch

from scripts.pr_monitor import EXIT_POLLER_FATAL
from scripts.pr_monitor import LauncherConfig
from scripts.pr_monitor import build_codex_command
from scripts.pr_monitor import build_monitor_paths
from scripts.pr_monitor import build_pane_title
from scripts.pr_monitor import build_runner_script
from scripts.pr_monitor import build_live_head_branch_name
from scripts.pr_monitor import build_launcher_config
from scripts.pr_monitor import classify_pr_snapshot
from scripts.pr_monitor import list_window_panes
from scripts.pr_monitor import parse_args
from scripts.pr_monitor import render_prompt
from scripts.pr_monitor import wait_for_actionable_pr_state


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
        self.assertIn("exit promptly", prompt)
        self.assertIn("without model tokens", prompt)

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

    def test_build_runner_script_waits_without_codex_before_restarting(self) -> None:
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
        self.assertIn("wait-for-actionable", script)
        self.assertIn("PROMPT=\"$(cat \"$PROMPT_PATH\")\"", script)
        self.assertIn("Rechecking GitHub state before any restart", script)
        self.assertIn("Codex failed; waiting", script)
        self.assertIn('if [ "$exit_code" -ne 0 ]; then', script)
        self.assertIn("sleep \"$RESTART_DELAY_SECONDS\"", script)
        self.assertIn("GitHub state poller hit a fatal error", script)
        self.assertLess(
            script.index("  wait_for_actionable_or_done\n"),
            script.index("Starting Codex monitor attempt"),
        )

    def test_wait_for_actionable_returns_fatal_after_repeated_poll_errors(self) -> None:
        with (
            patch(
                "scripts.pr_monitor.fetch_pr_snapshot",
                side_effect=RuntimeError("bad gh auth"),
            ) as fetch_snapshot,
            patch("scripts.pr_monitor.time.sleep"),
            redirect_stdout(io.StringIO()),
            redirect_stderr(io.StringIO()),
        ):
            exit_code = wait_for_actionable_pr_state(
                "vega113/supawave",
                405,
                poll_delay_seconds=1,
            )

        self.assertEqual(EXIT_POLLER_FATAL, exit_code)
        self.assertEqual(5, fetch_snapshot.call_count)

    def test_classify_pr_snapshot_treats_pending_checks_as_idle(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "BLOCKED",
                "autoMergeRequest": {"enabledAt": "2026-04-25T00:00:00Z"},
            },
            [{"name": "Server Build", "bucket": "pending", "description": ""}],
            unresolved_review_threads=0,
        )

        self.assertEqual("idle", decision.state)
        self.assertIn("waiting for checks", decision.reason)

    def test_classify_pr_snapshot_treats_review_gate_wait_as_idle(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "BLOCKED",
                "autoMergeRequest": {"enabledAt": "2026-04-25T00:00:00Z"},
            },
            [
                {
                    "name": "Codex Review Gate",
                    "bucket": "fail",
                    "description": "Waiting for 10-minute review window",
                    "workflow": "",
                },
                {
                    "name": "PR Review Gate",
                    "bucket": "fail",
                    "description": "",
                    "workflow": "Codex Review Gate",
                },
            ],
            unresolved_review_threads=0,
        )

        self.assertEqual("idle", decision.state)
        self.assertIn("review gate", decision.reason)

    def test_classify_pr_snapshot_treats_non_waiting_review_gate_failure_as_actionable(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "BLOCKED",
                "autoMergeRequest": {"enabledAt": "2026-04-25T00:00:00Z"},
            },
            [
                {
                    "name": "PR Review Gate",
                    "bucket": "fail",
                    "description": "Unable to determine last commit time",
                    "workflow": "Codex Review Gate",
                },
                {"name": "Server Build", "bucket": "pending", "description": ""},
            ],
            unresolved_review_threads=0,
        )

        self.assertEqual("actionable", decision.state)
        self.assertIn("review gate failed", decision.reason)

    def test_classify_pr_snapshot_treats_unresolved_threads_as_actionable(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "BLOCKED",
                "autoMergeRequest": {"enabledAt": "2026-04-25T00:00:00Z"},
            },
            [
                {
                    "name": "Codex Review Gate",
                    "bucket": "fail",
                    "description": "Waiting for 10-minute review window",
                    "workflow": "",
                }
            ],
            unresolved_review_threads=2,
        )

        self.assertEqual("actionable", decision.state)
        self.assertIn("unresolved review", decision.reason)

    def test_classify_pr_snapshot_treats_non_gate_failures_as_actionable(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "UNSTABLE",
                "autoMergeRequest": None,
            },
            [{"name": "Server Build", "bucket": "fail", "description": ""}],
            unresolved_review_threads=0,
        )

        self.assertEqual("actionable", decision.state)
        self.assertIn("failed checks", decision.reason)

    def test_classify_pr_snapshot_treats_merge_ready_without_auto_merge_as_actionable(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "CLEAN",
                "autoMergeRequest": None,
            },
            [{"name": "Server Build", "bucket": "pass", "description": ""}],
            unresolved_review_threads=0,
        )

        self.assertEqual("actionable", decision.state)
        self.assertIn("auto-merge is not armed", decision.reason)

    def test_classify_pr_snapshot_treats_clean_pr_without_checks_as_actionable(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "OPEN",
                "mergedAt": "",
                "isDraft": False,
                "mergeable": "MERGEABLE",
                "mergeStateStatus": "CLEAN",
                "autoMergeRequest": None,
            },
            [],
            unresolved_review_threads=0,
        )

        self.assertEqual("actionable", decision.state)
        self.assertIn("auto-merge is not armed", decision.reason)

    def test_classify_pr_snapshot_reports_merged_pr_done(self) -> None:
        decision = classify_pr_snapshot(
            {
                "state": "MERGED",
                "mergedAt": "2026-04-25T05:22:54Z",
                "isDraft": False,
                "mergeable": "UNKNOWN",
                "mergeStateStatus": "UNKNOWN",
                "autoMergeRequest": None,
            },
            [],
            unresolved_review_threads=0,
        )

        self.assertEqual("merged", decision.state)

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

    def test_parse_args_supports_wait_for_actionable_subcommand(self) -> None:
        args = parse_args(
            [
                "wait-for-actionable",
                "--pr-number",
                "405",
                "--poll-delay-seconds",
                "60",
            ]
        )

        self.assertEqual("wait-for-actionable", args.command)
        self.assertEqual(405, args.pr_number)
        self.assertEqual(60, args.poll_delay_seconds)

    def test_build_pane_title_includes_pr_number_and_title(self) -> None:
        self.assertEqual(
            "PR #405 Fix monitor reliability",
            build_pane_title(405, "Fix monitor reliability"),
        )

    def test_build_live_head_branch_name_uses_pr_number(self) -> None:
        self.assertEqual("pr405-live-head", build_live_head_branch_name(405))


if __name__ == "__main__":
    unittest.main()
