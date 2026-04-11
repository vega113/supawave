# Issue 822 Local Verification

Date: 2026-04-10
Issue: #822
Branch: `reaction-chip-anchor-20260410`
Worktree: `/Users/vega/devroot/worktrees/reaction-chip-anchor-20260410`

## Setup Notes

- Freshly rebased worktree did not yet have generated `wave/config/changelog.json`, so `python3 scripts/assemble-changelog.py` was run once before SBT tasks that require the generated resource.
- The repo SBT test compile still excludes `wave/client` tests from normal `testOnly` discovery, so the executable red/green seam for `ReactionRowRendererTest` used a manual `javac` + `org.junit.runner.JUnitCore` harness against the already-compiled target classes and cached JUnit jars.

## Discovery Check

Command:

```bash
sbt -Dsbt.supershell=false 'testOnly org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionRowRendererTest'
```

Result:

- `No tests to run for Test / testOnly`

## Red Phase

Command:

```bash
CP=target/scala-3.3.4/classes:.coursier-cache/https/repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar:.coursier-cache/https/repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
OUT=/tmp/reaction-row-renderer-test
rm -rf "$OUT"
mkdir -p "$OUT"
javac --release 17 -cp "$CP" -d "$OUT" \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtml.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlString.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/EscapeUtils.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlBuilder.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java \
  wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java
java -cp "$OUT:$CP" org.junit.runner.JUnitCore \
  org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionRowRendererTest
```

Result:

- `FAILURES!!!`
- `testRenderWrapsEmojiAndCountForBaselineStyling` failed because the renderer still emitted a raw emoji text node instead of a dedicated emoji span
- `testRenderPlacesAddButtonBeforeReactionChipsWhenEditable` failed because the add button still rendered after the dynamic chips

## Green Phase

Command:

```bash
CP=target/scala-3.3.4/classes:.coursier-cache/https/repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar:.coursier-cache/https/repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
OUT=/tmp/reaction-row-renderer-test
rm -rf "$OUT"
mkdir -p "$OUT"
javac --release 17 -cp "$CP" -d "$OUT" \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtml.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlString.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/EscapeUtils.java \
  wave/src/main/java/org/waveprotocol/wave/client/common/safehtml/SafeHtmlBuilder.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java \
  wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java
java -cp "$OUT:$CP" org.junit.runner.JUnitCore \
  org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionRowRendererTest
```

Result:

- `OK (6 tests)`

## CSS Regression

Command:

```bash
sbt -Dsbt.supershell=false 'testOnly org.waveprotocol.box.server.rpc.BlipCssLinkStyleTest'
```

Result:

- `Passed: Total 2, Failed 0, Errors 0, Passed 2`

## Changelog

Commands:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Result:

- `assembled 138 entries -> wave/config/changelog.json`
- `changelog validation passed`

## Local Server And Browser Verification

Commands:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
bash scripts/worktree-boot.sh --port 9902
PORT=9902 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/reaction-chip-anchor-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/reaction-chip-anchor-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9902 bash scripts/wave-smoke.sh check
PORT=9902 bash scripts/wave-smoke.sh stop
```

Results:

- file-store links created for `_accounts`, `_attachments`, and `_deltas`
- `worktree-boot.sh` completed successfully, including GWT compile and staged assets
- smoke check passed:
  - `ROOT_STATUS=200`
  - `HEALTH_STATUS=200`
  - `WEBCLIENT_STATUS=200`
- shutdown completed cleanly:
  - `Stopped server on port 9902`

Browser flow:

1. Opened `http://127.0.0.1:9902/auth/register`
2. Registered throwaway local account `rx822lane@local.net`
3. Signed in at `http://127.0.0.1:9902/auth/signin`
4. Opened the welcome wave at `#local.net/w+1i46c7czxlxuzA`
5. Clicked the root-blip `Add reaction` control and selected `👍`
6. Confirmed the row HTML rendered the add button first and the chip as `<span class="waveReactionEmoji">👍</span><span class="waveReactionCount">1</span>`
7. Captured the add-button box before and after toggling the unique reaction off:
   - before removal: `x=413`, `y=1357.484375`, `width=32`, `height=20`
   - after removal: `x=413`, `y=1357.484375`, `width=32`, `height=20`
8. Added `😂` again and captured the final row screenshot

Observed UI result:

- the add-reaction control remained visually anchored at the leading edge while a unique reaction was added and removed
- the compact row rendered as a stable `32x20` add pill followed by a `44x19` reaction chip
- the emoji/count pair in the chip read as a single aligned pill in the live UI screenshot (`reaction-row-after-laugh.png`)
