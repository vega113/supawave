# Mongo Operational Hardening

Status: Follow-through
Last updated: 2026-03-20

This directory captures the operational guidance for the Mongo-backed Wave
stores.

Current reality:
- The codebase already has Mongo-backed store implementations.
- The deployment stacks already keep Mongo on the private Compose network.
- The application does not yet have Mongo username/password config wiring.
- That means the live overlay is not production-safe yet, even though Mongo is
  now the intended persistence direction.

Do not treat this as a completed production hardening story. Use it as the
operator baseline for the remaining auth, backup, restore, and durability work.

## 1. Authentication baseline

Minimum production posture:
- Never publish Mongo on a public port.
- Put Mongo on a private host or private Compose network only.
- Use a dedicated MongoDB admin account for bootstrap and a separate Wave
  application account with the narrowest practical privileges.
- Store secrets outside the application repo and outside `application.conf`.

At the moment Wave cannot consume Mongo credentials from config, so a fully
auth-enabled Mongo deployment still needs application work before it can carry
live traffic.

Recommended target privilege split once app credential support lands:
- Admin user: create users, rotate credentials, and manage backups.
- Wave user: `readWrite` on the `wiab` database only.
- Backup user: read-only access sufficient for `mongodump`.

## 2. Backup and restore

The scripts in this directory are intentionally small wrappers around the
standard Mongo tools.

Backup:

```bash
MONGODB_URI='mongodb://user:pass@mongo:27017/wiab?authSource=admin' \
  ./deploy/mongo/backup.sh /backups/wiab.archive.gz
```

Restore:

```bash
MONGODB_URI='mongodb://user:pass@mongo:27017/wiab?authSource=admin' \
  ./deploy/mongo/restore.sh /backups/wiab.archive.gz
```

Operational rules:
- Keep backups off the Mongo host.
- Encrypt backups at rest if the storage layer does not already do so.
- Run a restore drill against a scratch database or throwaway container before
  you rely on a new backup set.
- Treat backup success as incomplete until restore has been verified.

## 3. Durability guidance

For the current production direction, the durable baseline should be:
- WiredTiger with journaling enabled.
- Stable persistent storage, not tmpfs or ephemeral container storage.
- A replica set or managed Mongo service when you need restart tolerance beyond
  a single node.
- Majority write concern for the Wave data path once the deployment can express
  it consistently.

Single-node Mongo is acceptable for local development and disposable test
deployments, but it is not a strong production durability story.

## 4. Live-overlay gate

The Mongo-backed stores are not yet safe for a live overlay because the repo is
still missing:
- application-level Mongo credential wiring
- a validated backup/restore drill
- a documented production durability target

Only after those are in place should the Mongo-backed deployment be treated as
production-grade.
