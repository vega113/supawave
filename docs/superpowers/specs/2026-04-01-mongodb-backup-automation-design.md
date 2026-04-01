# MongoDB Backup Automation Design

Status: Draft
Date: 2026-04-01

## Problem

The production MongoDB instance (`wiab` database on `supawave`) has no automated backups.
A data loss event would be unrecoverable. The existing `deploy/mongo/backup.sh` and
`restore.sh` scripts assume `mongodump`/`mongorestore` are installed on the host, but in
production these tools only exist inside the `supawave-mongo-1` container.

## Goals

1. Automated backups every 6 hours via host crontab.
2. Retain the last 10 backups on disk, delete older ones automatically.
3. Manual backup and restore scripts that work in the production Docker environment.
4. Documentation explaining setup, usage, and troubleshooting.

## Non-Goals

- Off-host backup replication (S3, rsync) — future work.
- MongoDB authentication wiring — tracked separately.
- Point-in-time recovery via oplog — overkill for current scale.

## Environment

| Property | Value |
|---|---|
| Host | `supawave` (ssh alias) |
| Deploy root | `/home/ubuntu/supawave` |
| MongoDB container | `supawave-mongo-1` (mongo:6.0) |
| Database | `wiab` |
| Collections | 12 (deltas, account, contacts, snapshots, attachments, feature_flags, etc.) |
| Data size | ~10 MB logical, ~414 MB on disk (WiredTiger) |
| Compressed backup | ~5-10 MB per archive |
| 10 backups | ~100 MB total |
| Disk available | 645 GB free (5% used of 678 GB) |
| mongodump location | `/usr/bin/mongodump` inside container |

## Design

### Approach: docker exec into existing container

Use `docker exec supawave-mongo-1 mongodump --db wiab` with `--archive --gzip` streamed
to stdout, piped to a file on the host filesystem. This avoids installing
`mongodb-database-tools` on the host and keeps tools version-matched with the running
MongoDB. No auto-detection of Docker vs local tools — these scripts are Docker-first for
production use.

### File changes

#### 1. `deploy/mongo/backup.sh` — Rewrite

Replace the current script (which calls `mongodump` directly) with a Docker-aware version.

**Behavior:**
- Runs `docker exec $CONTAINER mongodump --db $MONGO_DATABASE --archive --gzip` piped to
  a temp file on the host, then atomically renames to final path on success.
- On failure: cleans up partial temp file.
- Sets `umask 077` so archives are created with mode 600 (owner-only).
- Archive naming: `wiab-YYYYMMDD-HHMMSS.archive.gz` in `$BACKUP_DIR`.
- Default `BACKUP_DIR`: `$DEPLOY_ROOT/shared/mongo/backups` where `DEPLOY_ROOT`
  defaults to `/home/ubuntu/supawave`.
- After dump: validate file exists and size > 0.
- Rotation: list archives sorted by name (lexicographic = chronological), delete all but
  the newest `$KEEP_COUNT` (default 10).
- Disk check: before backup, warn and abort if less than `$MIN_DISK_MB` free on the
  backup partition (default: 1024 MB).
- Exit codes: 0 success, 1 backup failed, 2 disk space insufficient.
- Stdout: prints the archive path on success.
- Stderr: logs timestamped messages for cron visibility.

**Environment variables:**
- `DEPLOY_ROOT` — base deploy directory (default: `/home/ubuntu/supawave`)
- `BACKUP_DIR` — override backup directory (default: `$DEPLOY_ROOT/shared/mongo/backups`)
- `MONGO_CONTAINER` — container name (default: `supawave-mongo-1`)
- `MONGO_DATABASE` — database to dump (default: `wiab`)
- `KEEP_COUNT` — number of backups to retain (default: `10`)
- `MIN_DISK_MB` — minimum free disk space in MB before aborting (default: `1024`)

#### 2. `deploy/mongo/restore.sh` — Rewrite

Replace the current script with a Docker-aware version.

**Behavior:**
- If no argument given: list available backups in `$BACKUP_DIR` and exit.
- Accepts archive path as argument.
- Validates archive file exists and is non-empty.
- Prints a confirmation prompt ("This will DROP existing data. Continue? [y/N]") unless
  `--yes` flag is passed (for scripted use).
- Pipes archive into
  `docker exec -i $CONTAINER mongorestore --db $MONGO_DATABASE --archive --gzip --drop`.
- Exit codes: 0 success, 1 restore failed, 66 archive not found.

**Environment variables:**
- `DEPLOY_ROOT`, `BACKUP_DIR`, `MONGO_CONTAINER`, `MONGO_DATABASE` — same defaults as
  backup.sh.

#### 3. `deploy/mongo/install-cron.sh` — New file

One-time setup script that installs the cron job on the host.

**Behavior:**
- Checks current crontab for existing Wave backup entry.
- If missing, appends two lines to the user's crontab:
  ```
  CRON_TZ=UTC
  0 */6 * * * /home/ubuntu/supawave/shared/mongo/backup.sh >> /home/ubuntu/supawave/shared/logs/backup.log 2>&1
  ```
  (`CRON_TZ` must be on its own line — it is a crontab environment variable, not inline.)
- If present, prints current entry and exits.
- Copies `backup.sh`, `restore.sh` into `$DEPLOY_ROOT/shared/mongo/` if not already
  present, and ensures they are executable.
- Creates backup and log directories if needed.

#### 4. `deploy/mongo/README.md` — Update

Expand the existing README to add:
- Quick-start section for manual backup and restore.
- Cron setup instructions (manual and via `install-cron.sh`).
- Retention policy explanation.
- Troubleshooting section (common failures, how to verify backups).
- Restore drill procedure (against a scratch container, not production).

#### 5. `docs/deployment/mongo-hardening.md` — Update

Sync the backup section to match the new scripts and schedule. Currently documents a
different schedule and invocation style that would contradict this spec.

### Deployment packaging

**Problem:** The CI deploy bundle (`.github/workflows/deploy-contabo.yml`) currently ships
only `compose.yml`, `Caddyfile`, `application.conf`, and `deploy.sh`. It does not include
`deploy/mongo/*`, so the cron target path would not exist on the host.

**Solution:** Copy `deploy/mongo/backup.sh`, `restore.sh`, and `install-cron.sh` into
`$DEPLOY_ROOT/shared/mongo/` on first setup (not per-deploy). These are operational scripts
that live alongside the data, not per-release artifacts. The cron entry and README will
reference `$DEPLOY_ROOT/shared/mongo/backup.sh` instead of `$DEPLOY_ROOT/current/...`.

Initial setup (one-time, done manually or by `install-cron.sh`):
```bash
cp deploy/mongo/backup.sh deploy/mongo/restore.sh /home/ubuntu/supawave/shared/mongo/
chmod +x /home/ubuntu/supawave/shared/mongo/{backup,restore}.sh
```

Updates to these scripts are deployed by re-running the copy step, which is infrequent.

### Cron schedule

```
CRON_TZ=UTC
0 */6 * * *   — runs at 00:00, 06:00, 12:00, 18:00 UTC
```

Output appended to `$DEPLOY_ROOT/shared/logs/backup.log`. The log file will be small
since each entry is just a few lines of status + the archive path.

### Retention

Backups are named `wiab-YYYYMMDD-HHMMSS.archive.gz`. The rotation logic:
1. `ls -1` the backup directory, filter for `wiab-*.archive.gz`.
2. Sort lexicographically (ISO timestamp ensures chronological order).
3. If count > `KEEP_COUNT`, delete the oldest files.

This gives ~60 hours of backup history at the 6-hour interval with 10 retained.

### Restore procedure

All commands run from `$DEPLOY_ROOT` (`/home/ubuntu/supawave`):

```bash
cd /home/ubuntu/supawave

# 1. Stop Wave (Mongo stays running)
docker compose -p supawave -f current/compose.yml stop wave

# 2. Restore from backup
./shared/mongo/restore.sh shared/mongo/backups/wiab-YYYYMMDD-HHMMSS.archive.gz

# 3. Start Wave
docker compose -p supawave -f current/compose.yml start wave
```

Stopping Wave first prevents it from writing while the restore drops and replaces data.
The `-p supawave` and `-f current/compose.yml` flags are required to match the project
name and compose file used by `deploy.sh`.

### Security considerations

- No credentials needed currently (MongoDB has no auth enabled).
- When auth is added later, the scripts will need `MONGODB_URI` or separate credential
  env vars. The README will note this as a future enhancement.
- Scripts set `umask 077` so archives are created mode 600 (owner-only).
- Writes to a temp file first, atomically renames on success, cleans up on failure.
- The backup directory should be owned by the deploy user (ubuntu).

## Testing

- Run `backup.sh` manually on the host, verify archive is created and non-empty.
- Verify archive permissions are 600 (owner-only).
- Restore into a scratch container (`docker run --rm mongo:6.0 ...`) to verify archive
  integrity, rather than testing against production.
- Run backup 12 times, verify only 10 archives remain (oldest 2 deleted).
- Simulate low disk space (`MIN_DISK_MB=999999`), verify script aborts gracefully.
- Kill backup mid-stream, verify no partial archive is left behind.
- Install cron, wait 6 hours, verify automatic backup appears.
