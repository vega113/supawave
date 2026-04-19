# Issue 915 Plan: Reply Depth Validator

**Goal:** Fix the server-side reply-depth guard so waves that already contain a depth-5 branch can still accept additional sibling threads that also end at depth 5, while still rejecting any insert that would create depth 6.

**Root cause:** `ReplyDepthValidator.validate(...)` currently checks only the pre-submit global manifest max depth. Once any branch already reaches the configured limit, it rejects every later thread insertion, even when the submitted delta would not increase the resulting max depth.

**Acceptance criteria:**

- [ ] A new unit regression proves a depth-4 blip can create another depth-5 sibling thread even when the wave already contains a separate depth-5 branch.
- [ ] A companion test still proves inserts that would create depth 6 are rejected.
- [ ] The validator compares against projected post-op manifest depth instead of the pre-op global max.
- [ ] Targeted `sbt testOnly` passes.
- [ ] Local staged-server smoke passes.
- [ ] Narrow browser verification confirms the affected reply flow no longer throws the depth-limit error for the valid sibling-at-depth-5 case.

**Scope:**

- `wave/src/main/java/org/waveprotocol/box/server/waveserver/ReplyDepthValidator.java`
- `wave/src/test/java/org/waveprotocol/box/server/waveserver/ReplyDepthValidatorTest.java`
- local verification evidence only

**Out of scope:**

- changing client-side reply-depth UI policy
- changing the configured reply-depth limit
- wider thread-focus or mobile-chrome behavior
