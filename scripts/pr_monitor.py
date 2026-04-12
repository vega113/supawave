#!/usr/bin/env python3

import argparse
import pathlib
import shlex
import subprocess
import sys
from dataclasses import dataclass


DEFAULT_SESSION = "vibe-code"
DEFAULT_WINDOW = "wave-pr-monitor"
DEFAULT_RESTART_DELAY_SECONDS = 20
DEFAULT_SHARED_ROOT = pathlib.Path("/Users/vega/devroot/worktrees/pr-monitors")
DEFAULT_CODEX_BIN = "codex"
DEFAULT_MODEL = "gpt-5.4"
DEFAULT_REASONING = "high"


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

If there is nothing to do right now, keep polling GitHub and stay alive in this lane instead of exiting.
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


def build_runner_script(config: LauncherConfig) -> str:
    codex_command = shlex.join(
        build_codex_command(config.codex_bin, config.model, config.reasoning_effort)
    )
    worktree = shlex.quote(str(config.worktree_path))
    repo = shlex.quote(config.repo)
    prompt_path = shlex.quote(str(config.prompt_path))
    return f"""#!/usr/bin/env bash
set -euo pipefail

REPO={repo}
PR_NUMBER={config.pr_number}
WORKTREE={worktree}
PROMPT_PATH={prompt_path}
RESTART_DELAY_SECONDS={config.restart_delay_seconds}

read_pr_state() {{
  gh pr view "$PR_NUMBER" --repo "$REPO" --json state,mergedAt --jq '.state'
}}

read_pr_merged_at() {{
  gh pr view "$PR_NUMBER" --repo "$REPO" --json mergedAt --jq '.mergedAt // ""'
}}

print_timestamp() {{
  date '+%Y-%m-%d %H:%M:%S'
}}

cd "$WORKTREE"

attempt=0
while true; do
  merged_at="$(read_pr_merged_at 2>/dev/null || true)"
  state="$(read_pr_state 2>/dev/null || true)"
  if [ -n "$merged_at" ] && [ "$merged_at" != "null" ]; then
    printf '[%s] PR merged at %s\\n' "$(print_timestamp)" "$merged_at"
    exit 0
  fi
  if [ "$state" = "CLOSED" ]; then
    printf '[%s] PR closed without merge; treating monitor as blocked.\\n' "$(print_timestamp)"
    exit 0
  fi

  attempt=$((attempt + 1))
  printf '[%s] Starting Codex monitor attempt %s for PR #%s\\n' "$(print_timestamp)" "$attempt" "$PR_NUMBER"
  PROMPT="$(cat "$PROMPT_PATH")"
  set +e
  {codex_command} "$PROMPT"
  exit_code=$?
  set -e

  merged_at="$(read_pr_merged_at 2>/dev/null || true)"
  state="$(read_pr_state 2>/dev/null || true)"
  if [ -n "$merged_at" ] && [ "$merged_at" != "null" ]; then
    printf '[%s] PR merged at %s\\n' "$(print_timestamp)" "$merged_at"
    exit 0
  fi
  if [ "$state" = "CLOSED" ]; then
    printf '[%s] PR closed without merge; treating monitor as blocked.\\n' "$(print_timestamp)"
    exit 0
  fi

  printf '[%s] Codex exited with code %s while PR is still open. Restarting in %ss.\\n' "$(print_timestamp)" "$exit_code" "$RESTART_DELAY_SECONDS"
  sleep "$RESTART_DELAY_SECONDS"
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

    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.command != "start":
        raise ValueError(f"Unsupported command {args.command}")
    config = build_launcher_config(args)
    pane_id = launch_monitor(config)
    print(f"pane_id={pane_id}")
    print(f"pane_title={config.pane_title}")
    print(f"prompt_path={config.prompt_path}")
    print(f"log_path={config.log_path}")
    print(f"runner_path={config.runner_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
