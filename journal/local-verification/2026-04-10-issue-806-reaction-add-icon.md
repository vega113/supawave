# Issue 806 Local Verification

Date: 2026-04-10
Issue: #806
Branch: `reactions-add-icon-20260410`
Worktree: `/Users/vega/devroot/worktrees/reactions-add-icon-20260410`

## Test Harness Note

The repo SBT test compile excludes `wave/client` tests from normal `testOnly` discovery, so the executable red/green seam for `ReactionRowRendererTest` used a manual `javac` + `org.junit.runner.JUnitCore` harness against `wave/Test/fullClasspath`.

## Red Phase

Command:

```bash
FULL_CP=$(sbt -Dsbt.supershell=false "show wave / Test / fullClasspath" | perl -ne 'while(/Attributed\(([^)]+)\)/g){ push @a, $1 } END { print join(":", @a) }')
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

Result:

- `FAILURES!!!`
- `testRenderUsesReactionSpecificAddIconWhenEditable` failed because the renderer still emitted the generic `+` button with no icon-specific/accessibility markup

## Green Phase

Command:

```bash
FULL_CP=$(sbt -Dsbt.supershell=false "show wave / Test / fullClasspath" | perl -ne 'while(/Attributed\(([^)]+)\)/g){ push @a, $1 } END { print join(":", @a) }')
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

Result:

- `OK (4 tests)`

## Changelog

Commands:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Result:

- `assembled 134 entries -> wave/config/changelog.json`
- `changelog validation passed`

## Local Server And Browser Verification

Commands:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/reactions-add-icon-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/reactions-add-icon-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
PORT=9900 bash scripts/wave-smoke.sh stop
```

Results:

- file-store links created for `_accounts`, `_attachments`, and `_deltas`
- `worktree-boot.sh` completed successfully, including GWT compile and staged assets
- smoke check passed:
  - `ROOT_STATUS=200`
  - `HEALTH_STATUS=200`
  - `WEBCLIENT_STATUS=200`
- shutdown completed cleanly:
  - `Stopped server on port 9900`

Browser flow:

1. Opened `http://localhost:9900/auth/register`
2. Registered throwaway local account `rx806lane@local.net`
3. Signed in at `http://localhost:9900/auth/signin`
4. Opened the welcome wave at `#local.net/w+uo4rne1ce8i4A`
5. Located the updated `Add reaction` control beneath the blip
6. Confirmed the affordance rendered as a reaction-specific icon, not a generic text plus
7. Confirmed the button retained the existing reaction-chip footprint and styling
8. Clicked the control and confirmed the emoji picker popup opened with the expected options

Observed UI result:

- the add-reaction button rendered as a compact blue `32x20` pill with inline SVG smile-plus iconography
- the control read clearly as a reaction action in context
- no additional CSS adjustment was needed after live inspection
