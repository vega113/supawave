# Wave Server Resource Optimization Analysis
## Supawave Host Investigation (2026-04-01)

---

## EXECUTIVE SUMMARY

The supawave host is **severely under-utilizing** available resources. With 94GB RAM and 18 CPU cores available, the Wave service is currently configured with only **1GB Java heap** (-Xmx1024M). For a blue/green deployment scenario, this creates a critical bottleneck.

**Critical Finding**: 118 zombie mongosh processes indicate process cleanup issues that will eventually exhaust the system's PID capacity.

---

## HOST SPECIFICATIONS

| Resource | Value | Status |
|----------|-------|--------|
| **Physical RAM** | 94 GiB | Excellent |
| **CPU Cores** | 18 (AMD EPYC) | Good |
| **Disk Space** | 678 GiB total, 645 GiB available (95%) | Excellent |
| **Architecture** | x86_64, KVM virtualized | Good |
| **Java Process FD Limit** | 524,288 (current) | Healthy |
| **System FD Limit** | 1,024 (per-session) | **RISKY** |
| **Swap** | 0 GiB | **NO SWAP** |

---

## CURRENT DEPLOYMENT STATUS

### Single Instance Configuration
```
Service: Wave (running in Docker)
Memory: -Xmx1024M (1GB heap)
Actual Used: ~400 MiB
Available: ~92 GiB unused
```

### Data Storage Usage
| Component | Size | Growth Potential |
|-----------|------|-----------------|
| MongoDB data | 6.1 MiB | Very small - new instance |
| Logs | 39 MiB | Moderate |
| Indexes (Lucene) | 76 KiB | Very small |
| Sessions | 180 KiB | Will grow |
| Deltas | 244 KiB | Will grow |
| **Total** | **~50 MiB** | **Low** |

### Process Issues
- **118 zombie mongosh processes** (mostly from Mar 20-31, some recent)
- These are defunct shell processes from MongoDB connection attempts
- Consuming PIDs but not resources
- Root cause: Likely uncleaned MongoDB shell connections in scripts

---

## IDENTIFIED RISKS & ISSUES

### 🔴 CRITICAL ISSUES

1. **Memory Under-allocation (1GB for 94GB host)**
   - Java process runs with -Xmx1024M limit
   - Only 1.2% of available RAM utilized
   - Blue/green deployment would require 2GB total = 2.1% utilization
   - No room for growth or spikes

2. **No Swap Space**
   - Zero swap configured
   - If memory exhaustion occurs, OOM killer will terminate process
   - No graceful degradation path
   - Critical for production reliability

3. **Zombie Processes (118 mongosh) **
   - Each PID limits available processes (ulimit -u: 385,836)
   - Current usage: ~0.03% - still safe, but growing
   - Pattern suggests script issues with MongoDB connections
   - Risk: If rate continues, will hit PID limit in months

4. **File Descriptor Limit at System Level (1,024)**
   - Java process has 524,288 (good)
   - But root shell limit is only 1,024 per session
   - Lucene index files + MongoDB + Jetty sockets could exceed this
   - Risk: "Too many open files" errors possible

### 🟡 MEDIUM ISSUES

5. **No Resource Limits in Docker Compose**
   - Container memory limit: 0 (unlimited)
   - No CPU limits
   - Allows memory overcommit but no protection
   - One runaway instance could crash host

6. **Lucene Index Location**
   - Currently at /opt/wave/_indexes (76 KiB)
   - Will grow with search queries and documents
   - No separate mount point
   - Shares filesystem with application logs

7. **MongoDB Configuration**
   - Running with default settings
   - Only 6.1 MiB data currently
   - Will grow linearly with documents
   - No replication or backup strategy visible

### 🟢 MEDIUM OPPORTUNITIES

8. **CPU Under-utilization**
   - 18 cores available, likely <5% usage
   - Java GC threads could scale up
   - Concurrent request handling untapped

9. **Database Connection Pooling**
   - MongoDB driver likely using small default pool
   - Could increase for blue/green scenario

---

## RECOMMENDATIONS

### Phase 1: Immediate Fixes (Before Blue/Green)

#### 1.1 Fix Zombie Processes
**Priority: HIGH** | **Time: 30 mins**
```bash
# Identify script creating zombie connections
find /home/ubuntu/supawave -name '*.sh' | xargs grep 'mongosh'

# Fix: Ensure proper process cleanup in deployment scripts
# - Add error handling to shell scripts
# - Use 'wait' to clean up child processes
# - Redirect stderr properly for mongosh health checks
```

#### 1.2 Increase Java Heap for Single Instance
**Priority: HIGH** | **Time: 5 mins + restart**

**Recommendation: -Xmx24G (single instance)**

```yaml
# In compose.yml or entrypoint
environment:
  - JAVA_OPTS=-Xmx24G -Xms8G -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

**Rationale:**
- 24GB heap = 25% of host (safe for single instance)
- Initial heap of 8GB = faster startup, less GC thrashing
- G1GC better for large heaps (>4GB)
- Leaves 45GB for OS, MongoDB, container overhead

#### 1.3 Add Swap Space
**Priority: MEDIUM** | **Time: 20 mins**

```bash
# Create 32GB swap on host
sudo fallocate -l 32G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
sudo swapon -s  # verify

# Persist across reboot
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Tune swappiness for Java
echo 10 | sudo tee /proc/sys/vm/swappiness
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
```

**Rationale:**
- 32GB swap = 1/3 of RAM (standard practice for 94GB systems)
- Provides OOM protection without crashing
- Allows temporary spikes beyond heap
- Swappiness=10 means strongly prefer RAM, fallback to swap

#### 1.4 Increase System-Wide File Descriptors
**Priority: MEDIUM** | **Time: 10 mins + reboot**

```bash
# /etc/security/limits.conf
* soft nofile 131072
* hard nofile 262144
* soft nproc 131072
* hard nproc 262144

# /etc/sysctl.conf
fs.file-max = 2097152
fs.nr_open = 1048576
```

**Rationale:**
- Lucene will create index files
- Jetty + WebSocket connections
- MongoDB connections
- 131K per user = safe margin

---

### Phase 2: Blue/Green Deployment Optimization

#### 2.1 Memory Allocation for Blue/Green
**Priority: HIGH** | **When**: Before blue/green launch

```yaml
# compose.yml update
services:
  wave:
    environment:
      JAVA_OPTS: -Xmx20G -Xms6G -XX:+UseG1GC -XX:MaxGCPauseMillis=200
    deploy:
      resources:
        limits:
          memory: 22G      # 20G heap + 2G overhead
        reservations:
          memory: 20G      # Guaranteed allocation
```

**Calculation for Blue/Green:**
- Blue instance: 20G heap + 2G overhead = 22G
- Green instance: 20G heap + 2G overhead = 22G
- **Total: 44G max** (out of 94G available)
- Safety margin: 50G free for OS, MongoDB, swap usage

**Note:** -Xms (initial heap) = 6G per instance
- Faster JVM startup
- Reduced initial GC overhead
- Won't allocate full 20G until needed

#### 2.2 MongoDB Optimization for Multiple Instances
**Priority: MEDIUM**

```bash
# Shared MongoDB instance (not separate instances per blue/green)
# Both Wave instances connect to same MongoDB

# Increase MongoDB memory:
command:
  - --wiredTigerCacheSizeGB=8    # 8GB cache for both instances
  - --bind_ip_all
  - --slowms=100                 # Log slow queries >100ms
  - --profile=1                  # Basic profiling
```

#### 2.3 Container Resource Limits
**Priority: MEDIUM**

```yaml
services:
  wave-blue:
    deploy:
      resources:
        limits:
          memory: 22G
          cpus: '8'          # Limit to 8 of 18 cores
        reservations:
          memory: 20G
          cpus: '6'

  wave-green:
    deploy:
      resources:
        limits:
          memory: 22G
          cpus: '8'
        reservations:
          memory: 20G
          cpus: '6'

  mongo:
    deploy:
      resources:
        limits:
          memory: 12G        # 8G cache + 4G overhead
        reservations:
          memory: 10G
```

---

### Phase 3: Monitoring & Growth Planning

#### 3.1 Add JVM Monitoring
**Priority: MEDIUM** | **Time**: 2 hours

```bash
# Enable JVM options for monitoring
JAVA_OPTS="... \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Dcom.sun.management.jmxremote.rmi.port=9010 \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/opt/wave/logs/gc.log"
```

#### 3.2 Lucene Index Monitoring
**Priority: LOW**

```bash
# Add to monitoring script
find /opt/wave/_indexes -type f | wc -l     # File count
du -sh /opt/wave/_indexes                    # Size growth
```

**Warning Thresholds:**
- Indexes > 5GB: Consider index optimization
- Indexes > 20GB: Consider partitioning index

#### 3.3 Database Growth Tracking
**Priority: MEDIUM**

```bash
# Monthly check
docker exec supawave-mongo-1 \
  mongosh --eval "db.stats()" | grep dataSize
```

**Expected Growth Rates:**
- Early stage: <100MB/month
- Production load: 500MB-1GB/month
- Requires archival after ~12 months at 1GB/month

---

## SPECIFIC LINUX KERNEL PARAMETERS

### Current Status
```
ulimit -n (file descriptors):  1024  ❌ TOO LOW
ulimit -u (processes):         385836 ✓ GOOD
ulimit -m (memory):            unlimited ✓ GOOD
```

### Required Changes
```bash
# /etc/security/limits.conf - add these lines
wave     soft    nofile    131072
wave     hard    nofile    262144
wave     soft    nproc     65536
wave     hard    nproc     65536

root     soft    nofile    262144
root     hard    nofile    262144

# /etc/sysctl.conf - optimize I/O and networking
net.core.somaxconn=65535                    # TCP listen queue
net.ipv4.tcp_max_syn_backlog=65535          # SYN backlog
fs.file-max=2097152                         # Max open files system-wide
fs.nr_open=1048576                          # Max open per process
net.ipv4.ip_local_port_range=1024 65535     # Available ports
net.ipv4.tcp_tw_reuse=1                     # Reuse TIME_WAIT sockets

# Apply without reboot
sysctl -p
```

---

## RISK MATRIX: No Changes vs. Optimized

### Scenario 1: Current Setup (1GB Heap, No Changes)
| Scenario | Risk Level | Issue |
|----------|-----------|-------|
| Single instance running | **🟢 LOW** | Works but slow |
| Blue/green swap | **🔴 CRITICAL** | Both at 1GB, insufficient |
| Index growth to 100MB | **🟢 LOW** | Still OK |
| 10K concurrent users | **🔴 CRITICAL** | Memory exhaustion |
| Spike in socket connections | **🟡 MEDIUM** | FD limits hit |
| No swap, OOM event | **🔴 CRITICAL** | Process killed immediately |

### Scenario 2: With Recommendations
| Scenario | Risk Level | Issue |
|----------|-----------|-------|
| Single instance running | **🟢 LOW** | Excellent performance |
| Blue/green swap | **🟢 LOW** | Smooth transition |
| Index growth to 1GB+ | **🟢 LOW** | Plenty of capacity |
| 100K concurrent users | **🟢 LOW** | Can scale up further |
| Spike in socket connections | **🟢 LOW** | 262K FD limit |
| No swap, OOM event | **🟡 MEDIUM** | Swap provides buffer |

---

## IMPLEMENTATION CHECKLIST

### Pre-Blue/Green (Week 1)
- [ ] Fix zombie mongosh processes in scripts
- [ ] Increase Java heap to 24GB (single instance)
- [ ] Add 32GB swap space
- [ ] Increase system file descriptor limits
- [ ] Test restart and monitoring
- [ ] Load test with increased memory

### Blue/Green Preparation (Week 2)
- [ ] Update compose.yml with resource limits
- [ ] Set Java heap to 20GB per instance
- [ ] Configure MongoDB caching (8GB)
- [ ] Set up blue/green health check timing
- [ ] Document failover procedures

### Post-Deployment (Week 3+)
- [ ] Monitor GC logs and heap usage
- [ ] Track database growth rate
- [ ] Track Lucene index growth
- [ ] Establish backup/archival strategy
- [ ] Set up alerts for resource thresholds

---

## QUICK REFERENCE: Commands to Execute

```bash
# Check current state
free -h
docker ps -a
ps aux | grep defunct | wc -l
docker inspect supawave-wave-1 | grep -i memory
docker exec supawave-wave-1 ps aux | grep Xmx

# After implementing changes
# 1. Add swap
sudo swapon -s

# 2. Verify limits
ulimit -a
cat /etc/security/limits.conf | grep nofile

# 3. Monitor (post-restart)
free -h  # Should show swap
docker logs supawave-wave-1 | grep "Xmx"
jcmd <PID> VM.flags | grep -i xmx
```

---

## COST/BENEFIT ANALYSIS

### Implementation Effort
- **Swap Setup**: 20 minutes
- **FD Limits**: 10 minutes
- **Zombie Process Fix**: 30 minutes (debugging included)
- **Java Heap Update**: 5 minutes + validation
- **Total**: ~75 minutes of active time

### Benefits
- **Prevents crashes**: Swap + increased heap
- **Enables blue/green**: Sufficient memory allocation
- **Improves performance**: Larger heap = less GC pausing
- **Reduces risk**: Better resource management
- **Scalability**: Room to grow 10x before re-evaluation

### Risk Mitigation
- **Swap with low swappiness (10)**: Provides OOM buffer without performance hit
- **Gradual heap increase**: Monitor GC logs during initial deployment
- **Resource limits per container**: Prevents runaway instance

---

## CONCLUSION

The supawave host is **significantly over-provisioned in hardware but under-configured in software**. The current 1GB Java heap allocation leaves 98.9% of available RAM unused and creates a critical bottleneck for the planned blue/green deployment.

**Recommended immediate actions:**
1. **Fix zombie processes** (cleanup scripts)
2. **Increase Java heap to 24GB** (single instance)
3. **Add 32GB swap space** (OOM protection)
4. **Increase system FD limits to 262K** (socket/file limit)

**Expected outcome:** Production-ready infrastructure supporting blue/green deployments with safe growth to 10x current load before needing hardware upgrades.
