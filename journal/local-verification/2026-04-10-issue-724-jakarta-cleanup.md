# Issue 724 Local Verification

Date: 2026-04-10 02:41:57 IDT
Worktree: `/Users/vega/devroot/worktrees/issue-724-jakarta-cleanup-20260410`
Branch: `issue-724-jakarta-cleanup-20260410`
Plan: `docs/superpowers/plans/2026-04-10-issue-724-jakarta-cleanup-revalidation-plan.md`

## Investigation Evidence

Command:
```bash
comm -12 /tmp/issue724-overrides.txt /tmp/issue724-main.txt | wc -l
```

Result:
- PASS
- confirmed `48` duplicate main/Jakarta paths still exist in the current tree

Command:
```bash
rg -n "jakarta-overrides|mainExactExcludes|ServerMain\.java" build.sbt | sed -n '1,12p'
```

Result:
- PASS
- confirmed `build.sbt` still actively references `wave/src/jakarta-overrides/java/`
- confirmed `mainExactExcludes` still includes `org/waveprotocol/box/server/ServerMain.java`
- this invalidates the stale issue claim that build configs no longer reference `jakarta-overrides`

## Automated Verification

Command:
```bash
rg -n "mainExactExcludes in build\.sbt|mainExactExcludes in build\.gradle|jakarta-overrides variant of ServerMain" wave/src/main/java/org/waveprotocol/box/server/ServerMain.java
```

Result:
- PASS
- line `498` now points to `build.sbt`
- line `499` still documents that the active registration lives in the `jakarta-overrides` variant
- no `build.gradle` reference remains in that file

Command:
```bash
git diff --check
```

Result:
- PASS
- no whitespace or patch-application errors

## Review

Direct review:
- PASS
- no findings; the diff is limited to one inline Java comment plus the task plan file

External Claude review:
- PASS
- plan review: pass; scope stays limited to the stale `build.gradle` comment and explicitly avoids file deletion or directory rename
- implementation review: pass; no blockers or important concerns, only a minor note that `javax` -> `javax-era` is comment-only clarification

## Local Server Sanity

Not required:
- the branch changes documentation/comment text only and does not affect runtime behavior
