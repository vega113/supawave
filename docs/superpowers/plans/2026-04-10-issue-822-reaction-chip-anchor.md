# Issue 822 Reaction Chip Anchor Polish Plan

> **For agentic workers:** Keep this fix narrow. Use checkbox (`- [ ]`) steps for tracking and preserve the existing Wave reactions interaction model.

**Goal:** Polish the reactions row so each chip reads as a compact, deliberate pill, with the emoji and count aligned on the same visual baseline and the add-reaction affordance staying anchored instead of jumping when reactions are added or removed.

**Architecture:** Keep the fix inside the existing renderer/CSS seam. `ReactionRowRenderer` will emit explicit emoji/count spans and render the add button in a stable leading slot. `Blip.css` will convert the row to a compact flex layout and align chip internals intentionally, without changing reaction data semantics, picker behavior, or controller event handling.

**Tech Stack:** Java/GWT renderer code, CSS resource in `Blip.css`, focused JUnit renderer tests via the local `javac` + `JUnitCore` harness, local Wave browser verification.

---

## Context Snapshot

- Issue: `#822`
- Existing reactions plans:
  - `docs/superpowers/plans/2026-04-10-issue-798-reactions.md`
  - `docs/superpowers/plans/2026-04-10-issue-806-reaction-add-icon.md`
- Narrow seam:
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java`
  - `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`
  - `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java`
- Root cause:
  - the chip currently renders the emoji as a raw text node plus a count span, while CSS centers the whole button, so the emoji/counter pair lacks a reliable baseline contract
  - the add button is rendered after the dynamic reaction chips, so its visual position changes whenever a chip appears or disappears
- Constraints:
  - preserve the current compact Wave pill styling
  - keep reaction controller logic and picker behavior unchanged
  - avoid widening the row into a toolbar-like full-width layout
- Test harness note:
  - `ReactionRowRendererTest` runs through the same focused `javac` + `JUnitCore` harness used for issue `#806`

## Acceptance Criteria

- [ ] Reaction chips render emoji and count in separate elements that CSS can baseline-align deliberately.
- [ ] The add-reaction control stays visually anchored at the leading edge of the row instead of moving when a user toggles a unique reaction on/off.
- [ ] The row remains compact and aligned with the existing Wave visual language.
- [ ] The live UI is verified in a browser with real reactions on a local server.
- [ ] App-affecting changes include a changelog fragment and regenerated `wave/config/changelog.json`.

## Task 1: Lock The Renderer Contract First

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java`

- [ ] Add a failing test that asserts editable rows render the add button before the first chip.
- [ ] Add a failing test that asserts each reaction chip wraps the emoji in `waveReactionEmoji` and the count in `waveReactionCount`.
- [ ] Verify the tests fail for the expected reason against the current renderer markup.

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

Expected before implementation:
- FAIL because the renderer still emits the add button after chips and does not emit a dedicated emoji span

## Task 2: Implement The Narrow Layout Fix

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`
- Modify only if CSS contract coverage needs it: `wave/src/test/java/org/waveprotocol/box/server/rpc/BlipCssLinkStyleTest.java`

- [ ] Render the add button before the reaction chips so the control is anchored in a stable leading slot.
- [ ] Wrap the emoji glyph in a dedicated `waveReactionEmoji` span.
- [ ] Convert the row to a small flex layout with gap spacing instead of relying on trailing margins.
- [ ] Align chip contents with explicit line-height/baseline rules so emoji and count sit cleanly together.
- [ ] Keep the add button styling compact and visually consistent with the existing reaction chips.

Expected implementation shape:
```java
appendAddButton(html, blipId);
appendReactionChip(...);
```

```java
html.appendHtmlConstant("<span class=\"waveReactionEmoji\">");
html.appendEscaped(emoji);
html.appendHtmlConstant("</span><span class=\"waveReactionCount\">");
```

```css
.reactions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35em;
}

.reactions .waveReactionChip {
  align-items: baseline;
  line-height: 1;
}
```

## Task 3: Verify, Changelog, And Issue Evidence

**Files:**
- Add: `wave/config/changelog.d/2026-04-10-issue-822-reaction-chip-anchor.json`
- Regenerate: `wave/config/changelog.json`
- Add: `journal/local-verification/2026-04-10-issue-822-reaction-chip-anchor.md`

- [ ] Re-run the focused renderer test harness and record the green result.
- [ ] Run the CSS regression test if the CSS contract test needs updating.
- [ ] Regenerate and validate changelog output.
- [ ] Run the local server sanity path and verify the reactions row in a browser with real reactions.
- [ ] Record commands, results, and browser observations in the journal file and issue `#822`.

Verification commands:
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
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
bash scripts/worktree-boot.sh --port 9902
PORT=9902 bash scripts/wave-smoke.sh check
```

Browser flow:
- open `http://127.0.0.1:9902/`
- sign in with the shared local account if needed
- open a wave with existing reactions or add reactions to a test blip
- verify the add button stays visually fixed while toggling a unique user reaction on and off
- verify emoji/count alignment reads as a single compact pill instead of a vertically misaligned pair
