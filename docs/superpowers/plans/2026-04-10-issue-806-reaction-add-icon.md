# Issue 806 Reaction Add Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic `+` add-reaction affordance with a clearer reaction-specific icon that matches Wave/SupaWave’s icon language and still reads cleanly in the live UI.

**Architecture:** Keep the change narrow: `ReactionRowRenderer` remains the only source of add-button markup, while the existing `ReactionController` click handling and picker popup stay untouched. Swap the literal `+` for a compact inline SVG reaction icon, add explicit accessibility text, and make only the smallest CSS adjustment needed after real browser verification.

**Tech Stack:** Java/GWT renderer code, CSS resource in `Blip.css`, JUnit/SBT, local Wave staged server/browser verification.

---

## Context Snapshot

- Issue: `#806`
- Existing reaction feature plan: `docs/superpowers/plans/2026-04-10-issue-798-reactions.md`
- Narrow seam:
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java`
  - `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java`
  - `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`
- Existing behavior:
  - editable rows append `<button ...>+</button>`
  - `ReactionController` already routes `data-reaction-add="true"` clicks to `ReactionPickerPopup`
- Test harness note:
  - the repo SBT test compile excludes `wave/client` tests from normal `testOnly` discovery, so the executable red/green seam for `ReactionRowRendererTest` is a manual `javac` + `org.junit.runner.JUnitCore` harness using `wave/Test/fullClasspath`
- Non-goals:
  - do not change picker behavior, reaction chip behavior, or reaction data semantics
  - do not introduce a larger icon system or shared helper just for this issue

## Acceptance Criteria

- [ ] The add-reaction affordance is clearly reaction-related, not a generic plus.
- [ ] The icon visually fits the existing toolbar/icon language through compact current-color SVG styling.
- [ ] The button keeps the existing chip-shaped footprint and remains readable.
- [ ] The live UI has been verified in a real browser against a local server.
- [ ] App-affecting changes include a changelog fragment and regenerated `wave/config/changelog.json`.

## Task 1: Lock The Renderer Contract First

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java`

- [ ] **Step 1: Write the failing test for the new add button contract**

```java
public void testRenderUsesReactionSpecificAddIconWhenEditable() {
  SafeHtml html = ReactionRowRenderer.render(
      "b+blip1",
      Collections.<ReactionDocument.Reaction>emptyList(),
      "alice@example.com",
      true);

  String output = html.asString();
  assertTrue(output.contains("data-reaction-add=\"true\""));
  assertTrue(output.contains("aria-label=\"Add reaction\""));
  assertTrue(output.contains("waveReactionAddIcon"));
  assertFalse(output.contains(">+</button>"));
}
```

- [ ] **Step 2: Run the focused test to verify the failure is real**

Run:
```bash
FULL_CP=$(sbt -Dsbt.supershell=false "show wave / Test / fullClasspath" | \
  perl -ne 'while(/Attributed\(([^)]+)\)/g){ push @a, $1 } END { print join(":", @a) }')
OUT=/tmp/reaction-row-renderer-test
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -cp "$FULL_CP" -d "$OUT" \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtml.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlString.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/EscapeUtils.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlBuilder.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java \
  wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java
java -cp "$OUT:$FULL_CP" org.junit.runner.JUnitCore \
  org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionRowRendererTest
```

Expected:
- FAIL because the current renderer still emits a literal `+` and no icon/accessibility markup

## Task 2: Implement The Narrow Renderer/CSS Fix

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java`
- Modify only if needed after browser check: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`
- Modify only if CSS changes: `wave/src/test/java/org/waveprotocol/box/server/rpc/BlipCssLinkStyleTest.java`

- [ ] **Step 1: Replace the literal plus with a compact reaction SVG**

```java
private static final String ADD_ICON_HTML =
    "<span class=\"waveReactionAddIcon\" aria-hidden=\"true\">"
        + "<svg ...>"
        + "...</svg>"
        + "</span>";
```

```java
html.appendHtmlConstant(
    "<button type=\"button\" class=\"" + ADD_CLASS + "\" data-reaction-add=\"true\" "
        + "data-reaction-blip-id=\"" + EscapeUtils.htmlEscape(blipId) + "\" "
        + "aria-label=\"Add reaction\" title=\"Add reaction\">");
html.appendHtmlConstant(ADD_ICON_HTML);
html.appendHtmlConstant("</button>");
```

- [ ] **Step 2: Keep CSS changes minimal and only for visual fit**

If the browser pass shows the inline SVG is misaligned, add only a small selector such as:

```css
.reactions .waveReactionAddIcon,
.reactions .waveReactionAddIcon svg {
  display: block;
  width: 14px;
  height: 14px;
}
```

- [ ] **Step 3: Re-run the focused tests**

Run:
```bash
FULL_CP=$(sbt -Dsbt.supershell=false "show wave / Test / fullClasspath" | \
  perl -ne 'while(/Attributed\(([^)]+)\)/g){ push @a, $1 } END { print join(":", @a) }')
OUT=/tmp/reaction-row-renderer-test
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -cp "$FULL_CP" -d "$OUT" \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtml.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlString.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/EscapeUtils.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlBuilder.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java \
  wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java
java -cp "$OUT:$FULL_CP" org.junit.runner.JUnitCore \
  org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionRowRendererTest
```

Expected:
- PASS with the new icon-specific markup

If CSS changed, also run:
```bash
sbt "testOnly org.waveprotocol.box.server.rpc.BlipCssLinkStyleTest"
```

Expected:
- PASS with the new CSS contract assertions

## Task 3: Changelog And Real-UI Verification

**Files:**
- Add: `wave/config/changelog.d/2026-04-10-issue-806-reaction-add-icon.json`
- Regenerate: `wave/config/changelog.json`
- Add: `journal/local-verification/2026-04-10-issue-806-reaction-add-icon.md`

- [ ] **Step 1: Add the changelog fragment**

```json
{
  "releaseId": "2026-04-10-issue-806-reaction-add-icon",
  "title": "Use a clearer add-reaction icon",
  "summary": "Replaces the generic plus reaction button with a reaction-specific icon.",
  "type": "fix"
}
```

- [ ] **Step 2: Regenerate and validate changelog output**

Run:
```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

Expected:
- assemble completes and `wave/config/changelog.json` updates
- validate exits 0

- [ ] **Step 3: Run local server + browser verification**

Run:
```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='-Dwave.config.name=wave -Djava.net.preferIPv4Stack=true' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
PORT=9900 bash scripts/wave-smoke.sh stop
```

Browser flow:
- open `http://localhost:9900/`
- sign in with the shared local test account if needed
- open a wave with reactions enabled
- confirm the add button reads as a reaction action, not a generic plus
- click it and confirm the picker still opens and the icon remains visually cohesive beside reaction chips

- [ ] **Step 4: Record evidence and prepare PR**

Record in:
- `journal/local-verification/2026-04-10-issue-806-reaction-add-icon.md`
- issue `#806`

Include:
- exact test commands and results
- exact local server commands and result
- browser URL and observed UI result
- commit SHA(s)
- review findings and resolutions
