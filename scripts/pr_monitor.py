#!/usr/bin/env python3

import argparse
import json
import pathlib
import shlex
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Any


DEFAULT_SESSION = "vibe-code"
DEFAULT_WINDOW = "wave-pr-monitor"
DEFAULT_RESTART_DELAY_SECONDS = 20
DEFAULT_SHARED_ROOT = pathlib.Path("/Users/vega/devroot/worktrees/pr-monitors")
DEFAULT_CODEX_BIN = "codex"
DEFAULT_MODEL = "gpt-5.4"
DEFAULT_REASONING = "high"
EXIT_ACTIONABLE = 0
EXIT_MERGED = 10
EXIT_CLOSED = 11
EXIT_POLLER_FATAL = 12
POLL_FAILURE_FATAL_THRESHOLD = 5
REVIEW_GATE_CHECK_NAMES = {"Codex Review Gate", "PR Review Gate"}
REVIEW_GATE_WORKFLOW = "Codex Review Gate"


@dataclass(frozen=True)
class MonitorPaths:
    prompt_path: pathlib.Path
    log_path: pathlib.Path
    runner_path: pathlib.Path


@dataclass(frozen=True)
class LauncherConfig:
    repo: str
    pr_number: int
    pr_title: str
    worktree_path: pathlib.Path
    prompt_path: pathlib.Path
    log_path: pathlib.Path
    runner_path: pathlib.Path
    pane_title: str
    pr_head_oid: str
    launcher_script_path: pathlib.Path = pathlib.Path(__file__).resolve()
    session_name: str = DEFAULT_SESSION
    window_name: str = DEFAULT_WINDOW
    codex_bin: str = DEFAULT_CODEX_BIN
    model: str = DEFAULT_MODEL
    reasoning_effort: str = DEFAULT_REASONING
    restart_delay_seconds: int = DEFAULT_RESTART_DELAY_SECONDS


def build_pane_title(pr_number: int, pr_title: str) -> str:
    normalized_title = " ".join(pr_title.split())
    return f"PR #{pr_number} {normalized_title}"


def build_live_head_branch_name(pr_number: int) -> str:
    return f"pr{pr_number}-live-head"


def build_monitor_paths(shared_root: pathlib.Path, monitor_name: str) -> MonitorPaths:
    return MonitorPaths(
        prompt_path=shared_root / "prompts" / f"{monitor_name}.txt",
        log_path=shared_root / "logs" / f"{monitor_name}.log",
        runner_path=shared_root / "runners" / f"{monitor_name}.sh",
    )


def render_prompt(repo: str, pr_number: int, pr_title: str, worktree_path: str) -> str:
    return f"""You own PR #{pr_number} ({pr_title}) for repo {repo}. Use GitHub state directly and work only in the assigned worktree.

Assigned worktree: {worktree_path}

Goal: drive this PR all the way to merged state.
Required success condition:
- current
- conflict-free
- CI-acceptable
- conversation-clean, unless blocked by something real

Act proactively:
- inspect unresolved review comments and review threads, including bot comments
- check CI, build, and required status checks and fix failures
- update or rebase the branch when GitHub state changes
- push fixes and keep going until the PR is actually merged or truly blocked
- report meaningful status changes in the terminal output

Do not stop at mergeable=true if unresolved review conversations remain.
Do not exit just because auto-merge is armed.
Do not exit just because the PR is temporarily merge-ready.
Keep monitoring until the PR is actually merged, because another PR may merge first and make this PR behind, conflicted, or conversation-dirty again.

If you inspect GitHub and find no actionable work, exit promptly; the shell runner polls GitHub without model tokens and will restart Codex when checks, review threads, conflicts, or merge state require action.
"""


def build_codex_command(
    codex_bin: str = DEFAULT_CODEX_BIN,
    model: str = DEFAULT_MODEL,
    reasoning_effort: str = DEFAULT_REASONING,
) -> list[str]:
    return [
        codex_bin,
        "exec",
        "--dangerously-bypass-approvals-and-sandbox",
        "-m",
        model,
        "-c",
        f"model_reasoning_effort={reasoning_effort}",
        "-c",
        "web_search=live",
        "--sandbox",
        "danger-full-access",
        "--skip-git-repo-check",
    ]


@dataclass(frozen=True)
class MonitorDecision:
    state: str
    reason: str


def _check_bucket(check: dict[str, Any]) -> str:
    return str(check.get("bucket") or "").lower()


def _check_name(check: dict[str, Any]) -> str:
    return str(check.get("name") or check.get("workflow") or "unnamed check")


def _is_review_gate_check(check: dict[str, Any]) -> bool:
    name = str(check.get("name") or "")
    workflow = str(check.get("workflow") or check.get("workflowName") or "")
    return name in REVIEW_GATE_CHECK_NAMES or workflow == REVIEW_GATE_WORKFLOW


def _is_waiting_review_gate_check(check: dict[str, Any]) -> bool:
    description = str(check.get("description") or "").lower()
    return "waiting" in description and "review" in description


def _summarize_checks(checks: list[dict[str, Any]]) -> str:
    names = [_check_name(check) for check in checks[:3]]
    if len(checks) > 3:
        names.append(f"{len(checks) - 3} more")
    return ", ".join(names)


def _all_checks_finished_successfully(checks: list[dict[str, Any]]) -> bool:
    return all(
        _check_bucket(check) in {"pass", "skipping"} for check in checks
    )


def classify_pr_snapshot(
    pr: dict[str, Any],
    checks: list[dict[str, Any]],
    unresolved_review_threads: int,
) -> MonitorDecision:
    state = str(pr.get("state") or "")
    merged_at = pr.get("mergedAt")
    if state == "MERGED" or (merged_at and merged_at != "null"):
        return MonitorDecision("merged", f"PR merged at {merged_at or 'unknown time'}")
    if state == "CLOSED":
        return MonitorDecision("closed", "PR closed without merge")

    if unresolved_review_threads > 0:
        return MonitorDecision(
            "actionable",
            f"{unresolved_review_threads} unresolved review thread(s)",
        )

    if pr.get("isDraft"):
        return MonitorDecision("actionable", "PR is still draft")

    mergeable = str(pr.get("mergeable") or "")
    merge_state = str(pr.get("mergeStateStatus") or "")
    if mergeable == "CONFLICTING" or merge_state == "DIRTY":
        return MonitorDecision("actionable", "PR has merge conflicts")
    if merge_state == "BEHIND":
        return MonitorDecision("actionable", "PR branch is behind the base branch")

    failed_checks = [
        check for check in checks if _check_bucket(check) in {"fail", "cancel"}
    ]
    review_gate_failures = [
        check for check in failed_checks if _is_review_gate_check(check)
    ]
    non_gate_failures = [
        check for check in failed_checks if not _is_review_gate_check(check)
    ]
    if non_gate_failures:
        return MonitorDecision(
            "actionable",
            f"failed checks: {_summarize_checks(non_gate_failures)}",
        )

    pending_checks = [check for check in checks if _check_bucket(check) == "pending"]
    if review_gate_failures:
        if any(_is_waiting_review_gate_check(check) for check in review_gate_failures):
            return MonitorDecision("idle", "waiting for review gate window")
        return MonitorDecision("actionable", "review gate failed")

    if pending_checks:
        return MonitorDecision(
            "idle",
            f"waiting for checks: {_summarize_checks(pending_checks)}",
        )

    if pr.get("autoMergeRequest"):
        return MonitorDecision("idle", "auto-merge is armed; waiting for GitHub")

    if merge_state == "CLEAN" and _all_checks_finished_successfully(checks):
        return MonitorDecision("actionable", "merge-ready but auto-merge is not armed")

    return MonitorDecision(
        "idle",
        f"waiting for GitHub merge state {merge_state or 'unknown'}",
    )


def _run_json(command: list[str], allow_nonzero: bool = False) -> Any:
    result = subprocess.run(
        command,
        check=False,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0 and not allow_nonzero:
        message = (
            result.stderr.strip()
            or result.stdout.strip()
            or f"exit {result.returncode}"
        )
        raise RuntimeError(message)
    output = result.stdout.strip()
    if not output:
        if result.returncode != 0:
            message = result.stderr.strip() or f"exit {result.returncode}"
            raise RuntimeError(message)
        return None
    return json.loads(output)


def fetch_pr_view(repo: str, pr_number: int) -> dict[str, Any]:
    fields = ",".join(
        [
            "autoMergeRequest",
            "isDraft",
            "mergeable",
            "mergedAt",
            "mergeStateStatus",
            "state",
        ]
    )
    return _run_json(
        [
            "gh",
            "pr",
            "view",
            str(pr_number),
            "--repo",
            repo,
            "--json",
            fields,
        ]
    )


def fetch_pr_checks(repo: str, pr_number: int) -> list[dict[str, Any]]:
    checks = _run_json(
        [
            "gh",
            "pr",
            "checks",
            str(pr_number),
            "--repo",
            repo,
            "--json",
            "name,bucket,description,workflow",
        ],
        allow_nonzero=True,
    )
    return checks or []


def _split_repo(repo: str) -> tuple[str, str]:
    parts = repo.split("/")
    if len(parts) < 2:
        raise ValueError(f"Unsupported repo format: {repo!r}")
    return parts[-2], parts[-1]


def fetch_unresolved_review_thread_count(repo: str, pr_number: int) -> int:
    owner, name = _split_repo(repo)
    query = """
query($owner: String!, $name: String!, $number: Int!, $cursor: String) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      reviewThreads(first: 100, after: $cursor) {
        nodes {
          isResolved
        }
        pageInfo {
          hasNextPage
          endCursor
        }
      }
    }
  }
}
"""
    cursor = ""
    unresolved = 0
    while True:
        command = [
            "gh",
            "api",
            "graphql",
            "-f",
            f"query={query}",
            "-F",
            f"owner={owner}",
            "-F",
            f"name={name}",
            "-F",
            f"number={pr_number}",
        ]
        if cursor:
            command.extend(["-F", f"cursor={cursor}"])
        response = _run_json(command)
        threads = response["data"]["repository"]["pullRequest"]["reviewThreads"]
        unresolved += sum(1 for node in threads["nodes"] if not node.get("isResolved"))
        page_info = threads["pageInfo"]
        if not page_info["hasNextPage"]:
            return unresolved
        cursor = page_info["endCursor"]


def fetch_pr_snapshot(
    repo: str,
    pr_number: int,
) -> tuple[dict[str, Any], list[dict[str, Any]], int]:
    pr = fetch_pr_view(repo, pr_number)
    state = str(pr.get("state") or "")
    merged_at = pr.get("mergedAt")
    if state in {"MERGED", "CLOSED"} or (merged_at and merged_at != "null"):
        return pr, [], 0
    return (
        pr,
        fetch_pr_checks(repo, pr_number),
        fetch_unresolved_review_thread_count(repo, pr_number),
    )


def _timestamp() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


def wait_for_actionable_pr_state(
    repo: str,
    pr_number: int,
    poll_delay_seconds: int,
) -> int:
    consecutive_failures = 0
    while True:
        try:
            pr, checks, unresolved_review_threads = fetch_pr_snapshot(repo, pr_number)
            decision = classify_pr_snapshot(pr, checks, unresolved_review_threads)
        except Exception as exc:
            consecutive_failures += 1
            if consecutive_failures >= POLL_FAILURE_FATAL_THRESHOLD:
                print(
                    f"[{_timestamp()}] GitHub polling failed {consecutive_failures} "
                    f"times without Codex tokens: {exc}. Stopping monitor lane.",
                    file=sys.stderr,
                    flush=True,
                )
                return EXIT_POLLER_FATAL
            print(
                f"[{_timestamp()}] Unable to inspect GitHub state without Codex tokens: {exc}. "
                f"Retrying in {poll_delay_seconds}s.",
                flush=True,
            )
            time.sleep(poll_delay_seconds)
            continue
        consecutive_failures = 0

        if decision.state == "idle":
            print(
                f"[{_timestamp()}] Idle without Codex tokens: {decision.reason}. "
                f"Polling again in {poll_delay_seconds}s.",
                flush=True,
            )
            time.sleep(poll_delay_seconds)
            continue
        print(
            f"[{_timestamp()}] GitHub state is {decision.state}: {decision.reason}",
            flush=True,
        )
        if decision.state == "merged":
            return EXIT_MERGED
        if decision.state == "closed":
            return EXIT_CLOSED
        return EXIT_ACTIONABLE


def build_runner_script(config: LauncherConfig) -> str:
    codex_command = shlex.join(
        build_codex_command(config.codex_bin, config.model, config.reasoning_effort)
    )
    worktree = shlex.quote(str(config.worktree_path))
    repo = shlex.quote(config.repo)
    prompt_path = shlex.quote(str(config.prompt_path))
    monitor_script_path = shlex.quote(str(config.launcher_script_path))
    return f"""#!/usr/bin/env bash
set -euo pipefail

REPO={repo}
PR_NUMBER={config.pr_number}
WORKTREE={worktree}
PROMPT_PATH={prompt_path}
MONITOR_SCRIPT_PATH={monitor_script_path}
RESTART_DELAY_SECONDS={config.restart_delay_seconds}

print_timestamp() {{
  date '+%Y-%m-%d %H:%M:%S'
}}

resolve_monitor_script_path() {{
  if [ -f "$MONITOR_SCRIPT_PATH" ]; then
    printf '%s\\n' "$MONITOR_SCRIPT_PATH"
    return 0
  fi
  if [ -f "$WORKTREE/scripts/pr_monitor.py" ]; then
    printf '%s\\n' "$WORKTREE/scripts/pr_monitor.py"
    return 0
  fi
  return 1
}}

wait_for_actionable_or_done() {{
  monitor_script="$(resolve_monitor_script_path)" || return {EXIT_POLLER_FATAL}
  python3 "$monitor_script" wait-for-actionable \\
    --repo "$REPO" \\
    --pr-number "$PR_NUMBER" \\
    --poll-delay-seconds "$RESTART_DELAY_SECONDS"
}}

cd "$WORKTREE"

attempt=0
while true; do
  set +e
  wait_for_actionable_or_done
  wait_exit=$?
  set -e
  if [ "$wait_exit" -eq {EXIT_MERGED} ]; then
    printf '[%s] PR merged; monitor complete.\\n' "$(print_timestamp)"
    exit 0
  fi
  if [ "$wait_exit" -eq {EXIT_CLOSED} ]; then
    printf '[%s] PR closed without merge; treating monitor as blocked.\\n' "$(print_timestamp)"
    exit 0
  fi
  if [ "$wait_exit" -eq {EXIT_POLLER_FATAL} ]; then
    printf '[%s] GitHub state poller hit a fatal error; stopping lane.\\n' "$(print_timestamp)"
    exit 1
  fi
  if [ "$wait_exit" -ne 0 ]; then
    printf '[%s] GitHub state poller exited with code %s. Retrying in %ss.\\n' "$(print_timestamp)" "$wait_exit" "$RESTART_DELAY_SECONDS"
    sleep "$RESTART_DELAY_SECONDS"
    continue
  fi

  attempt=$((attempt + 1))
  printf '[%s] Starting Codex monitor attempt %s for PR #%s\\n' "$(print_timestamp)" "$attempt" "$PR_NUMBER"
  PROMPT="$(cat "$PROMPT_PATH")"
  set +e
  {codex_command} "$PROMPT"
  exit_code=$?
  set -e

  printf '[%s] Codex exited with code %s. Rechecking GitHub state before any restart.\\n' "$(print_timestamp)" "$exit_code"
  if [ "$exit_code" -ne 0 ]; then
    printf '[%s] Codex failed; waiting %ss before retrying.\\n' "$(print_timestamp)" "$RESTART_DELAY_SECONDS"
    sleep "$RESTART_DELAY_SECONDS"
  fi
done
"""


def run_tmux(*args: str, capture_output: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["tmux", *args],
        check=True,
        text=True,
        capture_output=capture_output,
    )


def tmux_window_exists(session_name: str, window_name: str) -> bool:
    result = subprocess.run(
        ["tmux", "list-windows", "-t", session_name, "-F", "#{window_name}"],
        check=False,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        return False
    return window_name in result.stdout.splitlines()


def ensure_tmux_window(session_name: str, window_name: str) -> None:
    if tmux_window_exists(session_name, window_name):
        return
    run_tmux("new-window", "-d", "-t", session_name, "-n", window_name)


def list_window_panes(session_name: str, window_name: str) -> list[dict[str, str]]:
    output = run_tmux(
        "list-panes",
        "-t",
        f"{session_name}:{window_name}",
        "-F",
        "#{pane_id}\t#{pane_title}\t#{pane_current_command}\t#{pane_dead}",
    ).stdout
    panes: list[dict[str, str]] = []
    for line in output.splitlines():
        parts = line.split("\t", maxsplit=1)
        if len(parts) != 2:
            continue
        pane_id, pane_fields = parts
        pane_parts = pane_fields.rsplit("\t", maxsplit=2)
        if len(pane_parts) != 3:
            continue
        pane_title, pane_command, pane_dead = pane_parts
        panes.append(
            {
                "pane_id": pane_id,
                "pane_title": pane_title,
                "pane_command": pane_command,
                "pane_dead": pane_dead,
            }
        )
    return panes


def find_existing_monitor_pane(
    panes: list[dict[str, str]],
    pr_number: int,
) -> str | None:
    prefix = f"PR #{pr_number} "
    pane_id = None
    for pane in panes:
        if pane["pane_title"].startswith(prefix):
            pane_id = pane["pane_id"]
            break
    return pane_id


def find_reusable_pane(panes: list[dict[str, str]]) -> str | None:
    pane_id = None
    if len(panes) == 1:
        pane = panes[0]
        title = pane["pane_title"].strip()
        command = pane["pane_command"]
        if pane["pane_dead"] == "0" and command in {"bash", "zsh"} and not title.startswith("PR #"):
            pane_id = pane["pane_id"]
    return pane_id


def allocate_monitor_pane(session_name: str, window_name: str, pr_number: int) -> str:
    panes = list_window_panes(session_name, window_name)
    existing_pane = find_existing_monitor_pane(panes, pr_number)
    if existing_pane is not None:
        return existing_pane

    reusable_pane = find_reusable_pane(panes)
    if reusable_pane is not None:
        return reusable_pane

    pane_id = run_tmux(
        "split-window",
        "-h",
        "-t",
        f"{session_name}:{window_name}",
        "-P",
        "-F",
        "#{pane_id}",
    ).stdout.strip()
    run_tmux("select-layout", "-t", f"{session_name}:{window_name}", "tiled")
    return pane_id


def fetch_pr_title(repo: str, pr_number: int) -> str:
    metadata = fetch_pr_metadata(repo, pr_number)
    return metadata["title"]


def fetch_pr_metadata(repo: str, pr_number: int) -> dict[str, str]:
    result = subprocess.run(
        [
            "gh",
            "pr",
            "view",
        str(pr_number),
        "--repo",
        repo,
        "--json",
        "title,headRefOid",
        "--jq",
        "[.title,.headRefOid] | @tsv",
    ],
        check=True,
        text=True,
        capture_output=True,
    )
    title, head_ref_oid = result.stdout.strip().split("\t")
    return {"title": title, "headRefOid": head_ref_oid}


def run_git(worktree_path: pathlib.Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(worktree_path), *args],
        check=True,
        text=True,
        capture_output=True,
    )


def ensure_clean_worktree(worktree_path: pathlib.Path) -> None:
    status_output = run_git(worktree_path, "status", "--porcelain").stdout.strip()
    if status_output:
        raise RuntimeError(f"Worktree is dirty: {worktree_path}")


def ensure_worktree_matches_pr_head(config: LauncherConfig) -> None:
    if not config.worktree_path.is_dir():
        raise RuntimeError(f"Worktree does not exist: {config.worktree_path}")
    ensure_clean_worktree(config.worktree_path)
    run_git(config.worktree_path, "fetch", "origin", f"pull/{config.pr_number}/head")
    expected_branch = build_live_head_branch_name(config.pr_number)
    current_branch = run_git(config.worktree_path, "branch", "--show-current").stdout.strip()
    current_head = run_git(config.worktree_path, "rev-parse", "HEAD").stdout.strip()
    if current_branch != expected_branch or current_head != config.pr_head_oid:
        run_git(
            config.worktree_path,
            "checkout",
            "-B",
            expected_branch,
            config.pr_head_oid,
        )


def build_monitor_name(worktree_path: pathlib.Path) -> str:
    return worktree_path.name


def validate_monitor_name(monitor_name: str) -> str:
    candidate = pathlib.Path(monitor_name)
    if monitor_name in {".", ".."} or candidate.name != monitor_name:
        raise ValueError(f"Invalid monitor name: {monitor_name!r}")
    return monitor_name


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("value must be > 0")
    return parsed


def write_launcher_artifacts(config: LauncherConfig) -> None:
    config.prompt_path.parent.mkdir(parents=True, exist_ok=True)
    config.log_path.parent.mkdir(parents=True, exist_ok=True)
    config.runner_path.parent.mkdir(parents=True, exist_ok=True)
    config.prompt_path.write_text(
        render_prompt(
            config.repo,
            config.pr_number,
            config.pr_title,
            str(config.worktree_path),
        ),
        encoding="utf-8",
    )
    config.runner_path.write_text(build_runner_script(config), encoding="utf-8")
    config.runner_path.chmod(0o755)
    config.log_path.touch(exist_ok=True)


def build_launcher_config(args: argparse.Namespace) -> LauncherConfig:
    worktree_path = pathlib.Path(args.worktree).resolve()
    shared_root = pathlib.Path(args.shared_root).resolve()
    monitor_name = validate_monitor_name(
        args.monitor_name or build_monitor_name(worktree_path)
    )
    paths = build_monitor_paths(shared_root, monitor_name)
    metadata = fetch_pr_metadata(args.repo, args.pr_number)
    pr_title = args.pr_title or metadata["title"]
    pane_title = build_pane_title(args.pr_number, pr_title)
    return LauncherConfig(
        repo=args.repo,
        pr_number=args.pr_number,
        pr_title=pr_title,
        worktree_path=worktree_path,
        prompt_path=paths.prompt_path,
        log_path=paths.log_path,
        runner_path=paths.runner_path,
        pane_title=pane_title,
        pr_head_oid=metadata["headRefOid"],
        session_name=args.session_name,
        window_name=args.window_name,
        codex_bin=args.codex_bin,
        model=args.model,
        reasoning_effort=args.reasoning_effort,
        restart_delay_seconds=args.restart_delay_seconds,
    )


def launch_monitor(config: LauncherConfig) -> str:
    ensure_worktree_matches_pr_head(config)
    write_launcher_artifacts(config)
    ensure_tmux_window(config.session_name, config.window_name)
    pane_id = allocate_monitor_pane(config.session_name, config.window_name, config.pr_number)
    run_tmux("select-pane", "-t", pane_id, "-T", config.pane_title)
    run_tmux("send-keys", "-t", pane_id, "C-c")
    pane_command = (
        f"{shlex.quote(str(config.runner_path))} 2>&1 | tee -a {shlex.quote(str(config.log_path))}; "
        "echo; "
        "echo 'Monitor finished. Pane left open for inspection.'; "
        "exec bash -i"
    )
    run_tmux("send-keys", "-t", pane_id, pane_command, "C-m")
    return pane_id


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    start_parser = subparsers.add_parser("start")
    start_parser.add_argument("--repo", default="vega113/supawave")
    start_parser.add_argument("--pr-number", type=positive_int, required=True)
    start_parser.add_argument("--pr-title")
    start_parser.add_argument("--worktree", required=True)
    start_parser.add_argument("--shared-root", default=str(DEFAULT_SHARED_ROOT))
    start_parser.add_argument("--monitor-name")
    start_parser.add_argument("--session-name", default=DEFAULT_SESSION)
    start_parser.add_argument("--window-name", default=DEFAULT_WINDOW)
    start_parser.add_argument("--codex-bin", default=DEFAULT_CODEX_BIN)
    start_parser.add_argument("--model", default=DEFAULT_MODEL)
    start_parser.add_argument("--reasoning-effort", default=DEFAULT_REASONING)
    start_parser.add_argument(
        "--restart-delay-seconds",
        type=positive_int,
        default=DEFAULT_RESTART_DELAY_SECONDS,
    )

    wait_parser = subparsers.add_parser("wait-for-actionable")
    wait_parser.add_argument("--repo", default="vega113/supawave")
    wait_parser.add_argument("--pr-number", type=positive_int, required=True)
    wait_parser.add_argument(
        "--poll-delay-seconds",
        type=positive_int,
        default=DEFAULT_RESTART_DELAY_SECONDS,
    )

    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.command == "wait-for-actionable":
        return wait_for_actionable_pr_state(
            args.repo,
            args.pr_number,
            args.poll_delay_seconds,
        )
    if args.command == "start":
        config = build_launcher_config(args)
        pane_id = launch_monitor(config)
        print(f"pane_id={pane_id}")
        print(f"pane_title={config.pane_title}")
        print(f"prompt_path={config.prompt_path}")
        print(f"log_path={config.log_path}")
        print(f"runner_path={config.runner_path}")
        return 0
    raise ValueError(f"Unsupported command {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
