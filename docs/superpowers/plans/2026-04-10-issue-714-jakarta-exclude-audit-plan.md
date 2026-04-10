# Issue 714 Jakarta Exclude Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-audit the live `build.sbt` Jakarta/main exact exclude lists, remove dead entries that no longer map to any source file, and keep the documented dual-source runtime architecture intact.

**Architecture:** Treat `wave/src/jakarta-overrides/java/` as the runtime-active source tree when a duplicate path exists, matching `docs/architecture/jakarta-dual-source.md`. Do not delete duplicate source files in this issue. Instead, make `build.sbt` accurately distinguish duplicate-path excludes from legacy main-only compile skips, and drop any exclude entries whose files no longer exist in either source tree.

**Tech Stack:** SBT build metadata, Markdown issue evidence, `node`, `rg`, `sbt compile`, `git diff`

---

## Task 1: Audit The Live Exact Exclude Lists

**Files:**
- Verify: `build.sbt`
- Verify: `docs/architecture/jakarta-dual-source.md`

- [ ] **Step 1: Confirm the current dual-source architecture is still documented**

Run: `sed -n '1,200p' docs/architecture/jakarta-dual-source.md`

Expected: the doc states that duplicate-path main/Jakarta pairs are intentional and that `wave/src/jakarta-overrides/java/` is the runtime source of truth when a matching override exists.

- [ ] **Step 2: Classify every `mainExactExcludes` path against the current tree**

Run:

```bash
node <<'NODE'
const fs = require('fs');
const path = require('path');
const build = fs.readFileSync('build.sbt', 'utf8');
function extractSetEntries(setName) {
  const marker = `val ${setName}: Set[String] = Set(`;
  const start = build.indexOf(marker);
  if (start === -1) {
    throw new Error(`Could not find ${setName} in build.sbt`);
  }
  const sub = build.slice(start);
  const end = sub.indexOf('\n  )');
  if (end === -1) {
    throw new Error(`Could not find end of ${setName} block in build.sbt`);
  }
  const block = sub.slice(0, end);
  return [...block.matchAll(/"([^"]+)"/g)].map(m => m[1]).filter(s => s.includes('/'));
}
function optionalSetEntries(setName) {
  return build.includes(`val ${setName}: Set[String] = Set(`) ? extractSetEntries(setName) : [];
}
const entries = [
  ...extractSetEntries('mainExactExcludes'),
  ...optionalSetEntries('mainLegacyCompileExcludes')
];
let both = 0, mainOnly = 0, missing = 0;
for (const rel of entries) {
  const hasMain = fs.existsSync(path.join('wave/src/main/java', rel));
  const hasJakarta = fs.existsSync(path.join('wave/src/jakarta-overrides/java', rel));
  if (hasMain && hasJakarta) both++;
  else if (hasMain) mainOnly++;
  else missing++;
}
console.log(JSON.stringify({ total: entries.length, both, mainOnly, missing }, null, 2));
NODE
```

Expected: `total: 56`, `both: 43`, `mainOnly: 9`, `missing: 4`.

- [ ] **Step 3: Confirm the duplicate pairs are not simple namespace-only copies**

Run:

```bash
node <<'NODE'
const fs = require('fs');
const path = require('path');
const build = fs.readFileSync('build.sbt','utf8');
function extractSetEntries(setName) {
  const marker = `val ${setName}: Set[String] = Set(`;
  const start = build.indexOf(marker);
  if (start === -1) {
    throw new Error(`Could not find ${setName} in build.sbt`);
  }
  const sub = build.slice(start);
  const end = sub.indexOf('\n  )');
  if (end === -1) {
    throw new Error(`Could not find end of ${setName} block in build.sbt`);
  }
  const block = sub.slice(0, end);
  return [...block.matchAll(/"([^"]+)"/g)].map(m => m[1]).filter(s => s.includes('/'));
}
const entries = extractSetEntries('mainExactExcludes');
const commentRe = /\/\*[\s\S]*?\*\/|(^|\s+)\/\/.*$/gm;
const normalizeNs = (s) => s.replace(/javax\./g, 'jakarta.').replace(/javax\//g, 'jakarta/').replace(/Javax/g, 'Jakarta');
const stripCommentsAndWs = (s) => s.replace(commentRe, '\n').split(/\r?\n/).map(l => l.trim()).filter(Boolean).join('\n');
let divergent = 0;
for (const rel of entries) {
  const main = path.join('wave/src/main/java', rel);
  const jakarta = path.join('wave/src/jakarta-overrides/java', rel);
  if (!fs.existsSync(main) || !fs.existsSync(jakarta)) continue;
  const a = fs.readFileSync(main, 'utf8');
  const b = fs.readFileSync(jakarta, 'utf8');
  if (stripCommentsAndWs(normalizeNs(a)) !== stripCommentsAndWs(normalizeNs(b))) divergent++;
}
console.log({ divergent });
NODE
```

Expected: `divergent: 43`, proving the current duplicate pairs are materially different beyond namespace/comment churn.

- [ ] **Step 4: Classify the current `jakartaExactExcludes` entries before editing them**

Run:

```bash
node <<'NODE'
const fs = require('fs');
const path = require('path');
const build = fs.readFileSync('build.sbt', 'utf8');
const start = build.indexOf('val jakartaExactExcludes: Set[String] = Set(');
const sub = build.slice(start);
const end = sub.indexOf('\n  )');
const block = sub.slice(0, end);
const entries = [...block.matchAll(/"([^"]+)"/g)].map(m => m[1]).filter(s => s.includes('/'));
let both = 0, jakartaOnly = 0, missing = 0;
for (const rel of entries) {
  const hasMain = fs.existsSync(path.join('wave/src/main/java', rel));
  const hasJakarta = fs.existsSync(path.join('wave/src/jakarta-overrides/java', rel));
  if (hasMain && hasJakarta) both++;
  else if (hasJakarta) jakartaOnly++;
  else missing++;
}
console.log(JSON.stringify({ total: entries.length, both, jakartaOnly, missing }, null, 2));
NODE
```

Expected: `total: 4`, `both: 1`, `jakartaOnly: 0`, `missing: 3`.

## Task 2: Clean Up Dead Exclude Metadata In `build.sbt`

**Files:**
- Modify: `build.sbt`

- [ ] **Step 1: Split duplicate-path excludes from legacy main-only excludes**

Update the main exact exclude block in `build.sbt` so it becomes two sets:

```scala
  // --- Exact excludes under src/main/java ---
  // These files have active Jakarta replacements in src/jakarta-overrides/java.
  val mainExactExcludes: Set[String] = Set(
    // existing duplicate-path entries only
  )

  // Legacy main-tree files intentionally kept out of the Jakarta/SBT compile surface.
  val mainLegacyCompileExcludes: Set[String] = Set(
    "com/google/wave/api/WaveService.java",
    "org/waveprotocol/box/expimp/Console.java",
    "org/waveprotocol/box/expimp/DeltaParser.java",
    "org/waveprotocol/box/expimp/DomainConverter.java",
    "org/waveprotocol/box/expimp/FileNames.java",
    "org/waveprotocol/box/expimp/OAuth.java",
    "org/waveprotocol/box/expimp/WaveImport.java",
    "org/waveprotocol/box/expimp/WaveExport.java",
    "org/waveprotocol/box/server/util/RegistrationUtil.java"
  )
```

Remove the dead missing paths from the old main list:

```scala
    "org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java",
    "org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java",
    "org/waveprotocol/box/server/rpc/GadgetProviderServlet.java",
    "org/waveprotocol/box/server/util/OAuthUtil.java",
```

- [ ] **Step 2: Update the filter predicate to use both main-tree sets**

Replace:

```scala
  val mainFileExcluded = underMain && mainExactExcludes.exists(suffix => p.endsWith("/" + suffix))
```

with:

```scala
  val mainFileExcluded = underMain && (
    mainExactExcludes.exists(suffix => p.endsWith("/" + suffix)) ||
    mainLegacyCompileExcludes.exists(suffix => p.endsWith("/" + suffix))
  )
```

- [ ] **Step 3: Remove dead Jakarta-side excludes**

Delete the missing-file entries from `jakartaExactExcludes`:

```scala
    "org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java",
    "org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java",
    "org/waveprotocol/box/server/robots/util/JakartaHttpRequestMessage.java",
```

Keep the live entry:

```scala
    "org/waveprotocol/box/server/robots/RobotApiModule.java"
```

## Task 3: Verify That The Cleanup Is Pure Metadata Hygiene

**Files:**
- Verify: `build.sbt`
- Create locally only (gitignored; do not commit): `journal/local-verification/2026-04-10-issue-714-jakarta-excludes.md`

- [ ] **Step 1: Re-run the exclude audit and confirm there are no missing paths left**

Run:

```bash
node <<'NODE'
const fs = require('fs');
const path = require('path');
const build = fs.readFileSync('build.sbt', 'utf8');
for (const name of ['mainJakartaOverrideExcludes', 'mainLegacyCompileExcludes', 'jakartaExactExcludes']) {
  const start = build.indexOf(`val ${name}: Set[String] = Set(`);
  const sub = build.slice(start);
  const end = sub.indexOf('\n  )');
  const block = sub.slice(0, end);
  const entries = [...block.matchAll(/"([^"]+)"/g)].map(m => m[1]).filter(s => s.includes('/'));
  const counts = entries.reduce((acc, rel) => {
    const hasMain = fs.existsSync(path.join('wave/src/main/java', rel));
    const hasJakarta = fs.existsSync(path.join('wave/src/jakarta-overrides/java', rel));
    if (hasMain && hasJakarta) acc.both++;
    else if (hasMain) acc.mainOnly++;
    else if (hasJakarta) acc.jakartaOnly++;
    else acc.missing++;
    return acc;
  }, { total: entries.length, both: 0, mainOnly: 0, jakartaOnly: 0, missing: 0 });
  console.log(name, counts);
}
NODE
```

Expected:
- `mainJakartaOverrideExcludes` reports `total: 43`, `both: 43`, `missing: 0`
- `mainLegacyCompileExcludes` reports `total: 9`, `mainOnly: 9`, `missing: 0`
- `jakartaExactExcludes` reports `total: 1`, `both: 1`, `missing: 0`

- [ ] **Step 2: Confirm the edited build file still compiles**

Run: `sbt compile`

Expected: exit `0`.

- [ ] **Step 3: Confirm the diff stays narrow**

Run:

```bash
git diff -- build.sbt
git diff --check
```

Expected:
- only `build.sbt` changes for the implementation
- `git diff --check` exits `0`

- [ ] **Step 4: Record audit and verification evidence**

Write `journal/local-verification/2026-04-10-issue-714-jakarta-excludes.md` with:
- pre-change audit counts (`43` duplicate pairs, `9` main-only legacy excludes, `4` missing main-list entries)
- the removed dead paths
- compile command and result
- final post-change counts for the three sets

- [ ] **Step 5: Commit**

```bash
git add build.sbt docs/superpowers/plans/2026-04-10-issue-714-jakarta-exclude-audit-plan.md
git commit -m "build: clean stale jakarta exclude metadata"
```
