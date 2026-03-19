# J2CL / GWT 3 Inventory And Decision Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a codebase-backed J2CL / GWT 3 inventory and decision memo so future migration work starts from measured blockers, not assumptions.

**Architecture:** Treat this task as an inventory and decision artifact, not a migration spike. The work should first map the active GWT build and runtime surfaces, then classify the major incompatibility buckets, and only then write a decision memo that sequences realistic follow-on epics.

**Tech Stack:** Gradle 8, GWT 2.x build tooling, `.gwt.xml` module graph, Java 17, current Jakarta server runtime, repo-local Beads tracking, documentation under `docs/`.

---

## Current Evidence Snapshot

The current branch already shows the migration is not blocked by one subsystem, but by several large GWT-specific surfaces that must be inventoried separately:

- Treat the counts below as an architect snapshot, not as final deliverable values.
- The worker phase must re-run the counting commands on this branch and refresh the numbers in the final docs if they differ.

- `wave/build.gradle` still wires dedicated GWT toolchains and tasks:
  - `compileGwt`, `compileGwtDev`, `gwtCodeServer`, `gwtDev2`, `testGwt`, `testGwtHosted`
  - `gwt-user`, `gwt-dev`, and `gwt-codeserver` are still explicit dependencies.
- `guava-gwt` remains on the client compile path in `wave/build.gradle` as `compileOnly` and on the `gwt` configuration.
- There are `163` `.gwt.xml` files under `wave/src/main/resources` and `wave/src/test`.
- There are `57` `GWT.create(...)` callsites in `wave/src/main/java`.
- There are `30` Java files using `UiBinder`/`UiField`-style patterns plus `26` `.ui.xml` templates.
- There are `59` main-source files matching `JavaScriptObject` or JSNI patterns, with `232` JSNI native methods in `wave/src/main/java`.
- There are `27` `GWTTestCase`-based tests in `wave/src/test/java`.
- There are `0` existing `JsInterop` files and `0` `Elemental`/`Elemental2` imports in `wave/src/main/java`.
- There are `0` custom `Generator` subclasses in `wave/src/main/java`, but there are `11` `<replace-with>` deferred-binding directives in `.gwt.xml` files.

## Top Migration Blockers

1. **JSNI and `JavaScriptObject` are still pervasive.**
   - The largest blocker is not widgets alone; it is the amount of JSNI/native browser interop embedded across `org.waveprotocol.wave.client.*`, `org.waveprotocol.wave.communication.gwt.*`, `org.waveprotocol.box.webclient.*`, and the gadget/html-template code.
   - High-risk clusters include:
     - `wave/src/main/java/org/waveprotocol/wave/communication/gwt/`
     - `wave/src/main/java/org/waveprotocol/wave/client/common/util/`
     - `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/`
     - `wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/`
     - `wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/`
     - `wave/src/main/java/org/waveprotocol/box/webclient/stat/gwtevent/`
     - `wave/src/main/java/com/google/gwt/websockets/client/`

2. **UiBinder and declarative widget construction are still a major surface.**
   - The app still uses `UiBinder` and `.ui.xml` templates heavily in the webclient, widget, gadget, and attachment UI layers.
   - Representative entrypoints include:
     - `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`
     - `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
     - `wave/src/main/java/org/waveprotocol/wave/client/widget/profile/ProfilePopupWidget.java`
     - `wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetWidgetUi.java`
     - `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java`

3. **Deferred-binding and `.gwt.xml` module sprawl are still central to runtime composition.**
   - The module graph is large and layered rather than having a single small entry module.
   - Representative module hubs:
     - `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml`
     - `wave/src/main/resources/org/waveprotocol/wave/client/Client.gwt.xml`
     - `wave/src/main/resources/org/waveprotocol/wave/client/widget/popup/Popup.gwt.xml`
     - `wave/src/main/resources/org/waveprotocol/wave/client/common/util/Util.gwt.xml`
     - `wave/src/main/resources/org/waveprotocol/wave/client/debug/logger/Logger.gwt.xml`

4. **The test harness still depends on legacy hosted GWT test infrastructure.**
   - The repo still runs `testGwt` and `testGwtHosted`, and the client test tree contains many `GWTTestCase` suites with explicit module names.
   - That means the migration plan has to include a testing strategy, not just a production compile strategy.

5. **Client dependency alignment is incomplete for a J2CL-style world.**
   - `guava-gwt` is still present.
   - The client side still depends on GWT browser APIs, widget packages, and GWT-specific HTTP/DOM abstractions.
   - There is no existing JsInterop/Elemental2 bridge layer to migrate toward.

## Evidence Sources

Use these files and commands as the authoritative evidence base for the final inventory and decision memo:

- Build and tooling:
  - `wave/build.gradle`
- Top-level module graph:
  - `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml`
  - `wave/src/main/resources/org/waveprotocol/wave/client/Client.gwt.xml`
- Representative entrypoints and app shell:
  - `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/events/ClientEvents.java`
- Browser/interop-heavy clusters:
  - `wave/src/main/java/org/waveprotocol/wave/communication/gwt/`
  - `wave/src/main/java/org/waveprotocol/wave/client/common/util/`
  - `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/`
  - `wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/`
  - `wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/`
  - `wave/src/main/java/org/waveprotocol/box/webclient/stat/gwtevent/`
  - `wave/src/main/java/com/google/gwt/websockets/client/WebSocket.java`
- UiBinder/widget clusters:
  - `wave/src/main/resources/**/*.ui.xml`
  - `wave/src/main/java/org/waveprotocol/box/webclient/`
  - `wave/src/main/java/org/waveprotocol/wave/client/widget/`
- Existing roadmap notes:
  - `docs/modernization-plan.md`
  - `docs/current-state.md`

## Deliverables

This task should produce these artifacts:

1. `docs/j2cl-gwt3-inventory.md`
   - Canonical inventory of GWT-specific surfaces.
   - Must categorize blockers by area, not just list files.

2. `docs/j2cl-gwt3-decision-memo.md`
   - Go/no-go recommendation for a staged migration.
   - Must identify whether to pursue full J2CL migration, partial bridge work, or defer.

3. Beads update on `incubator-wave-modernization.6`
   - Must include the inventory doc path, decision memo path, commit SHAs, and review summary.

4. Follow-on backlog proposal
   - Subtasks or follow-on tasks for:
     - module graph reduction
     - JSNI/`JavaScriptObject` elimination strategy
     - UiBinder/widget replacement strategy
     - GWT test harness replacement strategy
     - client dependency cleanup (`guava-gwt`, browser APIs, transport shims)

## Non-goals

- Do not implement J2CL, JsInterop, Elemental2, or widget migrations in this task.
- Do not rewrite `WebClient`, `StageThree`, or the editor/gadget stack.
- Do not replace the GWT test harness here.
- Do not change production build defaults away from the current GWT toolchain.
- Do not try to make `.gwt.xml` or UiBinder disappear in this task.

### Task 1: Freeze The Baseline Inventory Inputs

**Files:**
- Read: `wave/build.gradle`
- Read: `docs/modernization-plan.md`
- Read: `docs/current-state.md`
- Create: `docs/j2cl-gwt3-inventory.md`
- Create: `docs/j2cl-gwt3-decision-memo.md`

- [ ] **Step 1: Record the current build and roadmap assumptions**

Capture:
- GWT compile tasks and hosted test tasks in `wave/build.gradle`
- Current dependency note on `guava-gwt`
- Existing Phase 8 notes in `docs/modernization-plan.md`

Run:
```bash
rg -n "compileGwt|gwtCodeServer|testGwt|testGwtHosted|guava-gwt|GWT 3|J2CL" wave/build.gradle docs/modernization-plan.md docs/current-state.md
```

Expected:
- Concrete references to the current GWT build/test toolchain and the existing Phase 8 planning note.

- [ ] **Step 2: Create the inventory document skeleton**

Sections required in `docs/j2cl-gwt3-inventory.md`:
- build and toolchain
- module graph
- entrypoints
- browser interop and JSNI
- UiBinder and widget composition
- test harness
- dependency blockers
- migration sequencing candidates

- [ ] **Step 3: Create the decision memo skeleton**

Sections required in `docs/j2cl-gwt3-decision-memo.md`:
- decision summary
- evidence snapshot
- blockers
- phased path
- recommended follow-on tasks
- explicit no-go items for the first migration wave

### Task 2: Inventory The GWT Build And Module Graph

**Files:**
- Read: `wave/build.gradle`
- Read: `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml`
- Read: `wave/src/main/resources/org/waveprotocol/wave/client/Client.gwt.xml`
- Read: all `wave/src/main/resources/**/*.gwt.xml`
- Modify: `docs/j2cl-gwt3-inventory.md`

- [ ] **Step 1: Enumerate the active build/test/tooling tasks**

Run:
```bash
rg -n "compileGwt|compileGwtDev|gwtCodeServer|gwtDev2|testGwt|testGwtHosted|gwt-user|gwt-dev|gwt-codeserver" wave/build.gradle
```

Expected:
- A list of the exact GWT build/test entrypoints that would need J2CL replacements or retirement.

- [ ] **Step 2: Quantify module sprawl**

Run:
```bash
fd --glob '*.gwt.xml' wave/src/main/resources wave/src/test -HI -a | wc -l
```

Fallback if `fd` is unavailable:
```bash
find wave/src/main/resources wave/src/test -name '*.gwt.xml' | wc -l
```

Expected:
- A numeric module count recorded in the inventory.

- [ ] **Step 3: Classify `.gwt.xml` runtime behavior**

Run:
```bash
rg -n '<replace-with|<generate-with|<super-source' wave/src/main/resources wave/src/test
```

Expected:
- A list of deferred-binding and module-selection patterns that a J2CL path must replace or retire.

- [ ] **Step 4: Record the top-level module chain in the inventory**

Include:
- the two top-level module files above
- what each inherits
- whether they represent product runtime, dev runtime, or test harness only

### Task 3: Inventory Browser Interop And JSNI

**Files:**
- Read: `wave/src/main/java/org/waveprotocol/wave/communication/gwt/**`
- Read: `wave/src/main/java/org/waveprotocol/wave/client/common/util/**`
- Read: `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/**`
- Read: `wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/**`
- Read: `wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/**`
- Read: `wave/src/main/java/org/waveprotocol/box/webclient/stat/gwtevent/**`
- Modify: `docs/j2cl-gwt3-inventory.md`

- [ ] **Step 1: Quantify the interop-heavy files and methods**

Run:
```bash
printf 'JSO-or-JSNI files: '; rg -l 'JavaScriptObject|/\*-\{' wave/src/main/java | wc -l
printf 'JSNI native methods: '; rg -o 'native [^\n]*?/\*-\{' wave/src/main/java | wc -l
```

Expected:
- Numeric counts recorded in the inventory.

- [ ] **Step 2: Group the interop work into migration clusters**

At minimum, classify:
- communication/json wrappers
- DOM/editor selection wrappers
- gadget RPC and gadget state
- webclient statistics/event bridging
- websocket/browser APIs
- html-template/caja integrations

- [ ] **Step 3: Mark high-risk clusters that probably need dedicated follow-on epics**

Criteria:
- direct JSNI/native method density
- `JavaScriptObject` inheritance
- browser-specific control flow
- external gadget/caja coupling

### Task 4: Inventory UiBinder, Widgets, And `GWT.create(...)`

**Files:**
- Read: `wave/src/main/java/org/waveprotocol/box/webclient/**`
- Read: `wave/src/main/java/org/waveprotocol/wave/client/widget/**`
- Read: `wave/src/main/resources/**/*.ui.xml`
- Modify: `docs/j2cl-gwt3-inventory.md`

- [ ] **Step 1: Quantify declarative UI surfaces**

Run:
```bash
printf 'UiBinder Java files: '; rg -l 'UiBinder|UiField|UiFactory|UiHandler|UiTemplate' wave/src/main/java | wc -l
printf 'UiBinder templates: '; fd --glob '*.ui.xml' wave/src/main/resources | wc -l
printf 'GWT.create callsites: '; rg -l 'GWT\.create\(' wave/src/main/java | wc -l
```

Fallback for `.ui.xml` count if `fd` is unavailable:
```bash
find wave/src/main/resources -name '*.ui.xml' | wc -l
```

Expected:
- Numeric counts recorded in the inventory.

- [ ] **Step 2: Split the UI surface into migration buckets**

At minimum, separate:
- application shell and inbox/search UI
- toolbar and popup widgets
- profile/error/loading/frame widgets
- gadget renderer UI
- attachment/image widgets
- editor examples/harness UI

- [ ] **Step 3: Identify which `GWT.create(...)` uses are resources/messages/binders versus harder runtime factories**

Record:
- binders
- messages/resources
- singleton/event factories
- deferred-selection abstractions

### Task 5: Inventory The Test Harness Debt

**Files:**
- Read: `wave/build.gradle`
- Read: `wave/src/test/java/**`
- Modify: `docs/j2cl-gwt3-inventory.md`

- [ ] **Step 1: Quantify hosted GWT test usage**

Run:
```bash
printf 'GWTTestCase tests: '; rg -l 'extends GWTTestCase|GWTTestCase' wave/src/test/java | wc -l
```

Expected:
- Numeric count recorded in the inventory.

- [ ] **Step 2: Record the test execution paths that depend on legacy GWT tooling**

Capture:
- `testGwt`
- `testGwtHosted`
- module-name-based test discovery
- HTMLUnit and legacy hosted runtime assumptions

- [ ] **Step 3: Classify test suites by likely migration strategy**

Buckets:
- pure logic tests that can move to plain JVM/unit tests
- browser semantics tests that need a browser runner
- editor/gadget/widget tests that may need staged replacement

### Task 6: Produce The Decision Memo And Follow-on Backlog

**Files:**
- Modify: `docs/j2cl-gwt3-inventory.md`
- Modify: `docs/j2cl-gwt3-decision-memo.md`
- Modify: `.beads/issues.jsonl`
- Modify: `docs/modernization-plan.md`
- Modify: `docs/current-state.md`

- [ ] **Step 1: Write the evidence-backed decision summary**

The memo must answer:
- Is a direct full-app J2CL migration the next move? If not, why not?
- What is the narrowest first migration wave that reduces risk?
- Which subsystems should be isolated before any compiler/runtime switch?

- [ ] **Step 2: Propose follow-on tasks in dependency order**

The expected artifact for this task is:
- concrete proposed task titles recorded in the decision memo and Beads comments
- not immediate creation of new Beads issues unless the user explicitly asks for backlog expansion in the same pass

At minimum, propose follow-on tasks for:
- module graph reduction
- client dependency cleanup (`guava-gwt`, GWT-only runtime libs)
- JsInterop/Elemental2 bridge pilot
- JSNI/`JavaScriptObject` elimination in one vertical slice
- GWT test harness replacement strategy
- UiBinder replacement strategy

- [ ] **Step 3: Update the modernization ledger and Beads task**

Record:
- final inventory and memo paths
- short blocker summary
- proposed follow-on task titles in dependency order

- [ ] **Step 4: Verify the documents are internally consistent**

Run:
```bash
rg -n "J2CL|GWT 3|guava-gwt|UiBinder|JavaScriptObject|JSNI|GWTTestCase" docs/j2cl-gwt3-inventory.md docs/j2cl-gwt3-decision-memo.md docs/modernization-plan.md docs/current-state.md
```

Expected:
- The same blocker categories appear consistently across the inventory, memo, and modernization ledger.

- [ ] **Step 5: Commit**

```bash
git add docs/j2cl-gwt3-inventory.md docs/j2cl-gwt3-decision-memo.md docs/modernization-plan.md docs/current-state.md .beads/issues.jsonl
git commit -m "Document J2CL inventory and decision memo"
```

## Verification Checklist

Before closing the task, verify all of these are true:

- [ ] The inventory doc includes measured counts and concrete file clusters.
- [ ] The decision memo states a go/no-go recommendation and explains why.
- [ ] The plan does not pretend migration work is complete.
- [ ] Follow-on tasks are sequenced by dependency instead of one giant migration bucket.
- [ ] Beads and `docs/modernization-plan.md` point at the same final artifacts.
