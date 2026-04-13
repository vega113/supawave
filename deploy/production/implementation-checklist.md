# Wave Resource Optimization Implementation Checklist

Reference: `RESOURCE_OPTIMIZATION_ANALYSIS.md`

---

## Prerequisites

- SSH access to supawave host
- Root or sudo privileges
- Current deployment: single Wave instance with 1 GiB heap
- Host specs: 94 GiB RAM, 18 CPU cores, 678 GiB disk

---

## Phase 1: Immediate Fixes (Before Blue/Green)

### 1.1 Fix Zombie mongosh Processes

**Time estimate:** 30 minutes

```bash
# Step 1: Count current zombie processes
ps aux | grep defunct | wc -l

# Step 2: Find scripts creating unmanaged mongosh connections
find /home/ubuntu/supawave -name '*.sh' -exec grep -l 'mongosh' {} +

# Step 3: Kill existing zombie processes (safe -- they hold no resources)
# The parent of zombie processes must reap them.  If the parent is PID 1
# (init/systemd), they will be reaped automatically on next wait().
# Otherwise, restart the parent process.
ps -eo pid,ppid,stat,cmd | awk '$3 ~ /Z/ {print $1, $2}'

# Step 4: Fix offending scripts
# In each script that invokes mongosh, ensure:
#   - mongosh is called with --quiet and a timeout
#   - stderr is redirected
#   - The script uses 'wait' to reap child processes
#   - Example healthcheck pattern:
#       mongosh --quiet --eval 'db.runCommand({ping:1}).ok' 2>/dev/null || exit 1
```

**Validation:**
```bash
# Wait 5 minutes, then re-check -- count should stay at 0
ps aux | grep defunct | wc -l
```

---

### 1.2 Add Swap Space

**Time estimate:** 20 minutes

```bash
# Step 1: Verify no swap exists
sudo swapon --show
free -h | grep Swap

# Step 2: Create 32 GiB swap file
sudo fallocate -l 32G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Step 3: Verify
sudo swapon --show
free -h | grep Swap
# Expected: Swap total = 32 GiB

# Step 4: Persist across reboots
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Step 5: Set swappiness (also in sysctl-tuning.conf)
echo 10 | sudo tee /proc/sys/vm/swappiness
```

**Validation:**
```bash
free -h          # Swap line should show 32G
cat /proc/sys/vm/swappiness  # Should print 10
```

---

### 1.3 Apply Kernel Parameters (sysctl)

**Time estimate:** 10 minutes

```bash
# Step 1: Copy the tuning config
sudo cp deploy/production/sysctl-tuning.conf /etc/sysctl.d/99-wave.conf

# Step 2: Apply without reboot
sudo sysctl --system

# Step 3: Verify key values
sysctl fs.file-max          # 2097152
sysctl fs.nr_open           # 1048576
sysctl vm.swappiness        # 10
sysctl net.core.somaxconn   # 65535
sysctl net.ipv4.tcp_tw_reuse  # 1
```

**Validation:**
```bash
sysctl -a 2>/dev/null | grep -E 'file-max|nr_open|swappiness|somaxconn'
```

---

### 1.4 Apply File Descriptor and Process Limits

**Time estimate:** 10 minutes + re-login to verify

```bash
# Step 1: Copy limits config
sudo cp deploy/production/limits.conf.prod /etc/security/limits.d/99-wave.conf

# Step 2: Ensure PAM module is enabled (usually default on Ubuntu)
grep -q 'pam_limits.so' /etc/pam.d/common-session || \
  echo 'session required pam_limits.so' | sudo tee -a /etc/pam.d/common-session

# Step 3: Log out and log back in, then verify
ulimit -n   # Should show 131072 (or 262144 for root)
ulimit -u   # Should show 65536
```

**Validation:**
```bash
# As wave user (or root):
ulimit -Sn   # soft nofile
ulimit -Hn   # hard nofile
ulimit -Su   # soft nproc
ulimit -Hu   # hard nproc
```

---

### 1.5 Increase Java Heap (Single Instance)

**Time estimate:** 5 minutes + restart time

For the current single-instance deployment, increase the heap to 24 GiB.
When blue/green is ready, switch to 20 GiB per instance.

```bash
# Option A: Via environment variable in deploy.env
echo 'JAVA_OPTS=-Xmx24G -Xms8G -XX:+UseG1GC -XX:MaxGCPauseMillis=200' \
  >> "$DEPLOY_ROOT/shared/deploy.env"

# Option B: Edit the compose.yml directly
# Under services.wave.environment, add:
#   JAVA_OPTS: -Xmx24G -Xms8G -XX:+UseG1GC -XX:MaxGCPauseMillis=200

# Restart the service
docker compose --project-name supawave -f "$DEPLOY_ROOT/current/compose.yml" up -d

# Verify the heap was applied
docker exec supawave-wave-1 jcmd 1 VM.flags | grep -i xmx
# Expected: -XX:MaxHeapSize=25769803776 (24G in bytes)
```

**Validation:**
```bash
docker exec supawave-wave-1 jcmd 1 VM.flags 2>/dev/null | grep -E 'MaxHeap|InitialHeap|UseG1'
docker logs supawave-wave-1 2>&1 | tail -20
free -h  # Verify RAM usage is reasonable
```

---

## Phase 2: Blue/Green Deployment Setup

### 2.1 Deploy Production Docker Compose

**Time estimate:** 30 minutes

```bash
# Step 1: Copy the production compose into the release bundle
cp deploy/production/docker-compose.yml.prod "$DEPLOY_ROOT/current/compose-prod.yml"

# Step 2: Make wave-startup.sh available in the image or mount it
cp deploy/production/wave-startup.sh "$DEPLOY_ROOT/shared/wave-startup.sh"
chmod +x "$DEPLOY_ROOT/shared/wave-startup.sh"

# Step 3: Start with only the blue instance initially
#         (wave-green can be started when the new release is ready)
DEPLOY_ROOT="$DEPLOY_ROOT" \
WAVE_IMAGE="ghcr.io/yourorg/wave:latest" \
CANONICAL_HOST="supawave.ai" \
ROOT_HOST="wave.supawave.ai" \
WWW_HOST="www.supawave.ai" \
  docker compose --project-name supawave \
    -f "$DEPLOY_ROOT/current/compose-prod.yml" \
    up -d mongo wave-blue caddy

# Step 4: Wait for blue to become healthy
docker compose --project-name supawave \
  -f "$DEPLOY_ROOT/current/compose-prod.yml" \
  ps

# For Mongo-backed v4 deployments, confirm startup logged:
#   "Completed Mongock Mongo schema migrations"
# before treating readiness as complete.
```

---

### 2.2 Blue/Green Switchover Procedure

```bash
# Step 1: Deploy new image to green
WAVE_IMAGE_GREEN="ghcr.io/yourorg/wave:new-version" \
  docker compose --project-name supawave \
    -f "$DEPLOY_ROOT/current/compose-prod.yml" \
    up -d wave-green

# Step 2: Wait for green to pass health checks
#         (health check: curl -fsSI http://127.0.0.1:9899/readyz)
timeout 300 bash -c 'until curl -fsSI --max-time 3 http://127.0.0.1:9899/readyz 2>/dev/null; do sleep 5; done'

# For Mongo-backed v4 deployments, verify the new slot logs:
#   "Completed Mongock Mongo schema migrations"
# before switching traffic.

# Step 3: Switch Caddy upstream from blue (9898) to green (9899)
#         Update WAVE_INTERNAL_PORT and reload Caddy:
WAVE_INTERNAL_PORT=9899 \
  docker compose --project-name supawave \
    -f "$DEPLOY_ROOT/current/compose-prod.yml" \
    up -d caddy

# Step 4: Verify traffic flows to green
curl -fsSI -H "Host: supawave.ai" http://127.0.0.1/

# Step 5: Stop blue (only after green is confirmed healthy)
docker compose --project-name supawave \
  -f "$DEPLOY_ROOT/current/compose-prod.yml" \
  stop wave-blue

# Rollback: If green fails, restart blue and revert Caddy:
#   docker compose ... start wave-blue
#   WAVE_INTERNAL_PORT=9898 docker compose ... up -d caddy
```

---

### 2.3 MongoDB Optimization

```bash
# The production compose already includes WiredTiger cache and profiling
# settings.  Verify they are applied:
docker exec supawave-mongo-1 mongosh --quiet --eval '
  db.serverStatus().wiredTiger.cache["maximum bytes configured"] / (1024*1024*1024)
'
# Expected: 8 (GiB)

# Check slow query log:
docker exec supawave-mongo-1 mongosh --quiet --eval '
  db.system.profile.find({millis: {$gt: 100}}).sort({ts: -1}).limit(5).pretty()
'
```

---

## Phase 3: Monitoring and Validation

### 3.1 Post-Deployment Health Checks

Run these after every deployment:

```bash
# Memory overview
free -h
docker stats --no-stream

# JVM heap usage
docker exec supawave-wave-blue-1 jcmd 1 GC.heap_info 2>/dev/null || echo "jcmd not available"

# GC log review (look for long pauses)
tail -50 "$DEPLOY_ROOT/shared/logs/gc-blue.log" | grep -i pause

# File descriptor usage
ls /proc/$(docker inspect --format '{{.State.Pid}}' supawave-wave-blue-1)/fd | wc -l

# Network connection count
ss -s
```

### 3.2 Ongoing Monitoring Cadence

| Check | Frequency | Command |
|-------|-----------|---------|
| GC pause times | Daily | `grep 'Pause' $DEPLOY_ROOT/shared/logs/gc-*.log \| tail -20` |
| Heap usage | Daily | `docker exec <container> jcmd 1 GC.heap_info` |
| FD usage | Weekly | `ls /proc/<pid>/fd \| wc -l` |
| MongoDB data size | Weekly | `docker exec supawave-mongo-1 mongosh --quiet --eval 'db.stats().dataSize'` |
| Lucene index size | Weekly | `du -sh $DEPLOY_ROOT/shared/indexes` |
| Zombie processes | Weekly | `ps aux \| grep defunct \| wc -l` |
| Swap usage | Daily | `free -h \| grep Swap` |
| Disk usage | Weekly | `df -h` |

### 3.3 Alert Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Heap usage | > 80% of Xmx | > 95% of Xmx |
| GC pause | > 500 ms | > 2000 ms |
| FD count per process | > 50,000 | > 100,000 |
| Swap usage | > 4 GiB | > 16 GiB |
| Disk usage | > 80% | > 90% |
| Zombie processes | > 10 | > 50 |
| MongoDB data size | > 10 GiB | > 50 GiB |
| Lucene index size | > 5 GiB | > 20 GiB |

---

## File Inventory

| File | Purpose | Install Location |
|------|---------|-----------------|
| `docker-compose.yml.prod` | Production compose with resource limits and blue/green | Deploy bundle |
| `wave-startup.sh` | JVM startup script with optimized options | `/opt/wave/bin/` or mounted volume |
| `sysctl-tuning.conf` | Kernel parameter tuning | `/etc/sysctl.d/99-wave.conf` |
| `limits.conf.prod` | FD and process limits | `/etc/security/limits.d/99-wave.conf` |

---

## Rollback Plan

If any configuration change causes issues:

1. **Swap**: `sudo swapoff /swapfile` (remove from `/etc/fstab`)
2. **Sysctl**: `sudo rm /etc/sysctl.d/99-wave.conf && sudo sysctl --system`
3. **Limits**: `sudo rm /etc/security/limits.d/99-wave.conf` (re-login)
4. **Java heap**: Set `JAVA_OPTS=-Xmx1024M` and restart
5. **Blue/green**: Stop green, ensure blue is running, revert Caddy port
