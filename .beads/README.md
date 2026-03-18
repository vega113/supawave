# Beads - Project Backlog

This repository uses **Beads** for the live roadmap and task backlog.

## Source of truth

- Human-readable roadmap summary: `docs/current-state.md`
- Epic overview: `docs/epics/README.md`
- Live issue data: `.beads/issues.jsonl`

## Notes

- This repo is configured for Beads `no-db` mode so the JSONL file can be
  tracked directly in git without committing daemon state.
- Runtime Beads files such as sockets, logs, SQLite databases, and daemon locks
  are intentionally ignored by `.beads/.gitignore`.
