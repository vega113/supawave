# MongoDB Production Hardening Runbook

Status: Active runbook for modernization.11
Last updated: 2026-03-23

## Current state (Contabo production)

| Item | Value |
|---|---|
| MongoDB version | 6.0.27 |
| Image | `mongo:6.0` |
| Authentication | **NONE** (`--auth` not enabled, zero users in `admin.system.users`) |
| Startup flags | `--bind_ip_all` only |
| Network exposure | Private Compose network only; not published on host ports |
| Data volume | `$DEPLOY_ROOT/shared/mongo/db` (~323 MB on disk) |
| Database | `wiab` (2 collections: `deltas`, `account`, ~209 objects) |
| Backup schedule | **NONE** (no cron job for mongodump) |
| Driver | Mongo Java Driver v4 via `Mongo4DbProvider` |
| Credential config support | **NOT YET** -- `Mongo4DbProvider` builds `mongodb://host:port/db` with no user/pass |

## 1. Enable MongoDB authentication

### 1a. Code change required: Mongo4DbProvider credential wiring

`Mongo4DbProvider` currently constructs the connection URI without credentials:

```java
// wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java
String uri = "mongodb://" + host + ":" + port + "/" + database;
```

The MongoDB `ConnectionString` class already supports the standard URI format
`mongodb://user:pass@host:port/db?authSource=admin`, so the fix is to accept
optional username/password parameters and include them in the URI when present.

Required changes (three files):

**wave/config/reference.conf** -- add optional credential fields under `core {}`:

```hocon
  # MongoDB credentials (optional; leave empty for unauthenticated connections).
  mongodb_username : ""
  mongodb_password : ""
```

**PersistenceModule.java** -- read the new fields and pass them through:

```java
// In PersistenceModule constructor:
this.mongoDBUsername = config.getString("core.mongodb_username");
this.mongoDBPassword = config.getString("core.mongodb_password");

// In getMongo4Provider():
mongo4Provider = new Mongo4DbProvider(mongoDBHost, mongoDBPort, mongoDBdatabase,
                                      mongoDBUsername, mongoDBPassword);
```

**Mongo4DbProvider.java** -- accept optional credentials:

```java
public Mongo4DbProvider(String host, String port, String database,
                        String username, String password) {
  ...
}

private void ensure() {
  if (client == null) {
    String userInfo = (username != null && !username.isEmpty())
        ? username + ":" + password + "@"
        : "";
    String uri = "mongodb://" + userInfo + host + ":" + port + "/" + database
        + (userInfo.isEmpty() ? "" : "?authSource=admin");
    ...
  }
}
```

These changes are backward-compatible: when credentials are empty strings the
URI is unchanged from today.

### 1b. Create MongoDB users (on the running server, BEFORE enabling --auth)

Connect to the running unauthenticated instance and create users:

```bash
# From the Contabo host:
docker exec -it supawave-mongo-1 mongosh

# Inside mongosh:
use admin

db.createUser({
  user: "waveadmin",
  pwd: passwordPrompt(),   // interactive prompt -- do not hardcode
  roles: [
    { role: "userAdminAnyDatabase", db: "admin" },
    { role: "dbAdminAnyDatabase",   db: "admin" },
    { role: "backup",               db: "admin" },
    { role: "restore",              db: "admin" }
  ]
})

db.createUser({
  user: "wave",
  pwd: passwordPrompt(),
  roles: [
    { role: "readWrite", db: "wiab" }
  ]
})

db.createUser({
  user: "wavebackup",
  pwd: passwordPrompt(),
  roles: [
    { role: "read", db: "wiab" },
    { role: "backup", db: "admin" }
  ]
})
```

Store the generated passwords in `$DEPLOY_ROOT/shared/deploy.env` (not in the
repo), for example:

```bash
MONGO_ADMIN_PASSWORD=<generated>
MONGO_WAVE_PASSWORD=<generated>
MONGO_BACKUP_PASSWORD=<generated>
```

### 1c. Verify authentication works before cutting over

Test from inside the container while auth is still off:

```bash
docker exec supawave-mongo-1 mongosh \
  'mongodb://wave:<password>@localhost:27017/wiab?authSource=admin' \
  --quiet --eval 'db.runCommand({ping:1})'
```

### 1d. Enable --auth in compose.yml

Change the mongo service command:

```yaml
services:
  mongo:
    image: mongo:6.0
    restart: unless-stopped
    command:
      - --bind_ip_all
      - --auth
    healthcheck:
      test: ["CMD-SHELL", "mongosh --quiet -u waveadmin -p $MONGO_ADMIN_PASSWORD --authenticationDatabase admin --eval 'db.runCommand({ ping: 1 }).ok' || exit 1"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s
    environment:
      MONGO_ADMIN_PASSWORD: ${MONGO_ADMIN_PASSWORD:?set MONGO_ADMIN_PASSWORD}
    volumes:
      - ${DEPLOY_ROOT:?set DEPLOY_ROOT before running compose}/shared/mongo/db:/data/db
```

### 1e. Update application.conf with credentials

Add to `deploy/caddy/application.conf` (values sourced from deploy.env at
deploy time or injected by deploy.sh):

```hocon
core {
  ...
  mongodb_username = "wave"
  mongodb_password = ${MONGO_WAVE_PASSWORD}
}
```

Then pass `MONGO_WAVE_PASSWORD` into the wave container via environment or
deploy.env.

### 1f. Execution order (zero-downtime)

1. Deploy the code change that makes `Mongo4DbProvider` accept credentials.
2. Create users on the running unauthenticated Mongo (step 1b).
3. Verify auth works (step 1c).
4. Deploy new `application.conf` with credentials (step 1e) AND new
   `compose.yml` with `--auth` (step 1d) in the same release.
5. Smoke-test: the existing `deploy.sh` `wait_for_ready` and `check_proxy`
   will catch a broken startup.

## 2. Backup strategy

### 2a. Daily mongodump via cron

The `deploy/mongo/backup.sh` script already exists and wraps `mongodump`.
Schedule it as a host cron job:

```bash
# On the Contabo host, run as the ubuntu user:
crontab -e

# Add:
0 3 * * * MONGODB_URI='mongodb://wavebackup:<password>@localhost:27017/wiab?authSource=admin' /home/ubuntu/supawave/current/deploy/mongo/backup.sh /home/ubuntu/supawave/shared/backups/wiab-$(date -u +\%Y\%m\%dT\%H\%M\%SZ).archive.gz >> /home/ubuntu/supawave/shared/backups/backup.log 2>&1
```

Note: `mongodump` must be available on the host or run inside the container.
For the container approach:

```bash
# Alternative: run mongodump inside the mongo container
0 3 * * * docker exec supawave-mongo-1 mongodump --uri='mongodb://wavebackup:<password>@localhost:27017/wiab?authSource=admin' --archive --gzip > /home/ubuntu/supawave/shared/backups/wiab-$(date -u +\%Y\%m\%dT\%H\%M\%SZ).archive.gz 2>> /home/ubuntu/supawave/shared/backups/backup.log
```

### 2b. Retention policy

Keep:
- Daily backups: 7 days
- Weekly backups (Sunday): 4 weeks
- Monthly backups (1st of month): 3 months

Prune script (add after backup in cron or as a separate daily job):

```bash
# Delete daily backups older than 7 days (excluding those on the 1st or Sundays)
find /home/ubuntu/supawave/shared/backups/ -name 'wiab-*.archive.gz' -mtime +7 -delete
```

For a more granular retention policy, tag weekly/monthly backups by copying
them into separate subdirectories.

### 2c. Off-host backup copy

Backups stored only on the Mongo host are not safe against host failure.
Options:
- `rsync` or `scp` to a second Contabo VPS or object storage after each backup
- Mount an external volume (Contabo Object Storage or S3-compatible) and write
  directly there

Minimum viable: a daily `rsync` to a second host or object store bucket.

### 2d. Restore procedure

Using the existing `deploy/mongo/restore.sh`:

```bash
# Stop the wave service first to avoid write conflicts:
docker compose --project-name supawave stop wave

# Restore:
MONGODB_URI='mongodb://waveadmin:<password>@localhost:27017/wiab?authSource=admin' \
  ./deploy/mongo/restore.sh /path/to/wiab-YYYYMMDDTHHMMSSZ.archive.gz

# Restart:
docker compose --project-name supawave start wave
```

Or from inside the container:

```bash
docker exec -i supawave-mongo-1 mongorestore \
  --uri='mongodb://waveadmin:<password>@localhost:27017/wiab?authSource=admin' \
  --archive --gzip --drop \
  < /path/to/wiab-YYYYMMDDTHHMMSSZ.archive.gz
```

### 2e. Restore drill

Before relying on the backup pipeline, run a restore drill:

1. Spin up a throwaway Mongo container on a different port.
2. Restore the latest backup into it.
3. Verify the `wiab` database has the expected collections and document counts.
4. Drop the throwaway container.

Schedule this quarterly or after any backup infrastructure change.

## 3. Monitoring and log rotation

### 3a. Health check

Already in place via compose.yml:

```yaml
healthcheck:
  test: ["CMD-SHELL", "mongosh --quiet --eval 'db.runCommand({ ping: 1 }).ok' || exit 1"]
  interval: 5s
  timeout: 5s
  retries: 20
  start_period: 10s
```

After auth is enabled, update the healthcheck command to authenticate (see
section 1d above).

### 3b. Disk space monitoring

MongoDB data is at `$DEPLOY_ROOT/shared/mongo/db` (currently ~323 MB).
Add a simple disk-space check to cron:

```bash
# Alert if disk usage exceeds 80%
0 * * * * df -h /home/ubuntu/supawave/shared/mongo/db | awk 'NR==2 && int($5) > 80 {print "DISK WARNING: " $5 " used on mongo volume"}' | logger -t mongo-disk
```

### 3c. Docker log rotation

Docker defaults can produce unbounded log files. Add to the compose mongo
service or set daemon-level defaults:

```yaml
services:
  mongo:
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
```

## 4. Checklist

- [ ] Code: add `mongodb_username` / `mongodb_password` to `reference.conf`
- [ ] Code: update `PersistenceModule` to read and pass credentials
- [ ] Code: update `Mongo4DbProvider` to include credentials in URI
- [ ] Code: update `MongoDbProvider` (v2) similarly, or document it as deprecated
- [ ] Deploy: create MongoDB users on production (step 1b)
- [ ] Deploy: verify auth before cutover (step 1c)
- [ ] Deploy: enable `--auth` in compose.yml (step 1d)
- [ ] Deploy: configure application.conf with credentials (step 1e)
- [ ] Deploy: update healthcheck to authenticate (step 1d)
- [ ] Ops: set up daily backup cron (step 2a)
- [ ] Ops: set up retention pruning (step 2b)
- [ ] Ops: set up off-host backup copy (step 2c)
- [ ] Ops: run first restore drill (step 2e)
- [ ] Ops: add Docker log rotation (step 3c)
- [ ] Ops: add disk-space monitoring (step 3b)
