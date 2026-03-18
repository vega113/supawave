# Wave Modernization Resumption Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resume Apache Wave modernization and Wiab.pro feature adoption from a documented, non-stale baseline with Beads as the live backlog.

**Architecture:** Keep one canonical resume document, keep the detailed migration ledgers that still add implementation context, and move execution tracking into repo-local Beads epics and tasks. Future implementation should follow the epic order below so infrastructure validation happens before deeper feature imports.

**Tech Stack:** Markdown documentation, Beads no-db issue tracking, Gradle, additive SBT, Java 17, GWT 2.x, Jetty 12, existing Wave/Wiab migration docs.

---

### Task 1: Documentation foundation

**Files:**
- Modify: `README.md`
- Create: `docs/current-state.md`
- Create: `docs/epics/README.md`
- Create: `.beads/issues.jsonl`
- Create: `.beads/config.yaml`
- Create: `.beads/README.md`

- [ ] **Step 1: Confirm the canonical doc set**

Use `docs/current-state.md` as the top-level resume guide and keep links to the
detailed ledgers from there.

- [ ] **Step 2: Retire stale one-off docs**

Remove review snapshots and outdated migration notes once their actionable
content is folded into the canonical docs.

- [ ] **Step 3: Initialize repo-local Beads tracking**

Create the `.beads/` files in no-db mode and add epics/tasks for the remaining
work.

- [ ] **Step 4: Review and commit**

Verify documentation consistency, then commit the doc + Beads refresh as a
single change.

### Task 2: Modernization phase completion

**Files:**
- Modify: `docs/modernization-plan.md`
- Reference: `.beads/issues.jsonl`

- [ ] **Step 1: Complete config hygiene**
- [ ] **Step 2: Finish MongoDB v4 delta-store migration**
- [ ] **Step 3: Close remaining library upgrade work**
- [ ] **Step 4: Verify packaging, DX, and SBT parity**
- [ ] **Step 5: Produce the J2CL / GWT 3 inventory**

Beads epic: `incubator-wave-modernization`

### Task 3: Wiab core import completion

**Files:**
- Modify: `docs/migrate-conversation-renderer-to-apache-wave.md`
- Modify: `docs/blocks-adoption-plan.md`
- Reference: `.beads/issues.jsonl`

- [ ] **Step 1: Run a combined smoke of dynamic rendering, fragments, and quasi deletion**
- [ ] **Step 2: Finish remaining renderer entrypoints**
- [ ] **Step 3: Decide and implement the canonical fragment transport**
- [ ] **Step 4: Revisit snapshot gating and segment-state follow-ups**

Beads epic: `incubator-wave-wiab-core`

### Task 4: Wiab product feature evaluation

**Files:**
- Reference: `docs/current-state.md`
- Reference: `.beads/issues.jsonl`

- [ ] **Step 1: Audit tags, archive, and stored-search behavior**
- [ ] **Step 2: Evaluate and port draft mode**
- [ ] **Step 3: Evaluate and port contacts**
- [ ] **Step 4: Decide whether to adopt the deeper Wiab data layer**

Beads epic: `incubator-wave-wiab-product`
