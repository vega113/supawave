# Beads - Historical Project Backlog

This repository no longer uses **Beads** as the live roadmap and task backlog.
GitHub Issues are now the system of record for new planning and implementation
work.

## Current Source of Truth

- Live roadmap summary: `docs/current-state.md`
- Live GitHub issue workflow: `docs/github-issues.md`
- Live issue tracker: `https://github.com/vega113/incubator-wave/issues`

## Archive Contents

- Historical Beads issue data: `.beads/issues.jsonl`
- Historical Beads epic overview: `docs/epics/README.md`

## Notes

- This repo is configured for Beads `no-db` mode so the JSONL file can be
  tracked directly in git without committing daemon state.
- Runtime Beads files such as sockets, logs, SQLite databases, and daemon locks
  are intentionally ignored by `.beads/.gitignore`.
- Keep the archive read-only for historical reference. Do not create or update
  new tasks or comments here.
