# MongoDB Backup and Restore

Operational scripts for backing up and restoring the Wave MongoDB database
(`wiab`) on the production host. Scripts run `mongodump`/`mongorestore` inside
the existing Docker container — no host-side Mongo tools needed.

## Quick Start

### Manual backup

```bash
# From anywhere on the host:
/home/ubuntu/supawave/shared/mongo/backup.sh
```

The archive is saved to `/home/ubuntu/supawave/shared/mongo/backups/` and the
path is printed to stdout. Old backups beyond the retention count are
automatically deleted.

### Manual restore

```bash
# List available backups:
/home/ubuntu/supawave/shared/mongo/restore.sh

# Restore a specific backup (will prompt for confirmation):
cd /home/ubuntu/supawave
docker compose -p supawave -f current/compose.yml stop wave
./shared/mongo/restore.sh shared/mongo/backups/wiab-20260401-060000.archive.gz
docker compose -p supawave -f current/compose.yml start wave
```

Stop Wave before restoring to prevent writes during the drop-and-replace. The
`-p supawave -f current/compose.yml` flags match the project name used by
`deploy.sh`.

To skip the confirmation prompt (for scripted use), pass `--yes`:

```bash
./shared/mongo/restore.sh --yes shared/mongo/backups/wiab-20260401-060000.archive.gz
```

## Automated Backups (Cron)

### Setup

Run the installer (one-time, or re-run to update scripts):

```bash
cd /path/to/repo
./deploy/mongo/install-cron.sh
```

This:
1. Copies `backup.sh` and `restore.sh` to `/home/ubuntu/supawave/shared/mongo/`
2. Installs a crontab entry to run backups every 6 hours (UTC)

### Schedule

Backups run at 00:00, 06:00, 12:00, and 18:00 UTC. Output is logged to
`/home/ubuntu/supawave/shared/logs/backup.log`.

Verify the cron job:

```bash
crontab -l | grep wave-mongo-backup
```

### Retention

The 10 most recent backups are kept. Older archives are deleted after each
backup run. At 6-hour intervals, this gives ~60 hours of backup history.

Override with `KEEP_COUNT`:

```bash
KEEP_COUNT=20 /home/ubuntu/supawave/shared/mongo/backup.sh
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DEPLOY_ROOT` | `/home/ubuntu/supawave` | Base deploy directory |
| `BACKUP_DIR` | `$DEPLOY_ROOT/shared/mongo/backups` | Where archives are stored |
| `MONGO_CONTAINER` | `supawave-mongo-1` | Docker container name |
| `MONGO_DATABASE` | `wiab` | Database to dump/restore |
| `KEEP_COUNT` | `10` | Number of backups to retain |
| `MIN_DISK_MB` | `1024` | Minimum free disk (MB) before aborting |

## Troubleshooting

**"Cannot connect to the Docker daemon"**
- The user running the script must have Docker access. On production, the
  `ubuntu` user is in the `docker` group.

**"Error: container not found"**
- Verify the container is running: `docker ps | grep mongo`
- Check the container name matches `MONGO_CONTAINER`.

**"disk space insufficient" (exit code 2)**
- Free up space or lower `MIN_DISK_MB`. Check backup directory for unexpectedly
  large archives.

**Backup produced empty file**
- Check that MongoDB is healthy: `docker exec supawave-mongo-1 mongosh --quiet --eval 'db.runCommand({ping:1})'`
- Check Docker logs: `docker logs supawave-mongo-1 --tail 20`

**Restore fails with "not authorized"**
- When MongoDB authentication is enabled, the scripts will need to be updated
  to pass credentials. This is not yet needed (auth is currently disabled).

## Restore Drill

Periodically verify backups by restoring into a scratch container:

```bash
# Start a throwaway Mongo:
docker run -d --name mongo-drill mongo:6.0

# Restore:
# Use the most recent backup (ls -1t sorts by modification time)
LATEST=$(ls -1t /home/ubuntu/supawave/shared/mongo/backups/wiab-*.archive.gz | head -1)
MONGO_CONTAINER=mongo-drill \
  /home/ubuntu/supawave/shared/mongo/restore.sh --yes "$LATEST"

# Verify:
docker exec mongo-drill mongosh --quiet --eval 'db.getCollectionNames()' wiab

# Cleanup:
docker rm -f mongo-drill
```

## Future Enhancements

- **Authentication:** When MongoDB auth is enabled, scripts will need updating
  to pass credentials to mongodump/mongorestore.
- **Off-host replication:** rsync or S3-compatible storage for disaster recovery.
- **Monitoring:** Alert on backup failures (email, Slack, PagerDuty).
