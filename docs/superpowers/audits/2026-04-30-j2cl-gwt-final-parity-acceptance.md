# J2CL/GWT Final Parity Acceptance Audit (2026-04-30)

Status: Ready for PR review
Audit issue: [#1159](https://github.com/vega113/supawave/issues/1159)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Audited branch: `codex/issue-1159-final-parity-audit-20260430`
Audited base: `origin/main` at `2b2a083d1`
Local server: `http://127.0.0.1:9959`

## Verdict

No remaining concrete J2CL/GWT parity implementation gap was found in the
current audited gates. The closed F-slice, J-UI, visual-polish, G-PORT,
follow-up, viewport, and bootstrap-cleanup issues have merged PR evidence, and
the current local `main` state passes the SBT/J2CL, viewport-window, smoke, and
browser parity harnesses listed below.

This audit supports closing the stale implementation umbrellas #1078, #1098,
and #1109 after the audit PR merges. It does not support retiring the legacy
GWT route or flipping production defaults by itself: `/?view=gwt` remains the
rollback/baseline route, and any future default-root or retirement work should
be opened as a separate rollout issue with operator acceptance.

No new `agent-task` gap issue was created by this audit.

## Method

- Queried live GitHub state with `gh issue list --label j2cl-parity`.
- Resolved closed issue to PR evidence via `closedByPullRequestsReferences`
  because `gh pr list --search 'label:j2cl-parity'` returned no PRs.
- Ran SBT-only Java/J2CL verification from the #1159 worktree.
- Ran the committed Playwright J2CL/GWT parity harness against one local server.
- Compared J2CL and GWT browser evidence from the same Playwright reading-flow
  run.
- Attempted Claude Opus review with fallback disabled. Opus was quota-blocked,
  so this audit does not claim external Claude approval.

## Tracker Inventory

| Tracker | Current state | Audit disposition | Evidence |
| --- | --- | --- | --- |
| #904 | Open parent tracker with stale pending list | Keep as historical parent/rollout tracker, but update it to point at this audit and remove stale pending child lists | This audit plus merged PR #1158/current #1159 |
| #1078 | Open J-UI umbrella | Close after this PR merges | All J-UI leaves #1079-#1086 are closed and mapped to merged PRs |
| #1098 | Open visual-polish umbrella | Close after this PR merges | All V leaves #1099-#1103 are closed and mapped to merged PRs |
| #1109 | Open G-PORT 1:1 parity umbrella | Close after this PR merges | All G-PORT leaves #1110-#1118 and follow-ups #1121/#1125/#1128/#1133 are closed and mapped to merged PRs |
| #1159 | Open audit lane | Close after this audit PR merges and tracker comments are posted | Plan commit `5b13345d0`, this audit, and verification table below |

## Closed Slice Evidence

| Area | Issue | PR | Merge commit | Acceptance evidence | Audit result |
| --- | --- | --- | --- | --- | --- |
| F-1 viewport data path | #1036 | #1040 | `86ea6b440d8298262649865a770eaaa97d2b50eb` | Viewport-scoped J2CL read-surface data path | PASS in focused viewport SBT tests and current harness |
| F-2 StageOne read surface | #1037 | #1059 | `dc8ee6a3f0acaa0ceb841e333905970dcb8338eb` | Threaded blips, focus, collapse, read/unread integration | PASS in reading, keyboard, visual, and viewport gates |
| F-3 StageThree compose/edit | #1038 | #1077 | `62e57276cb431da2ff570a08fc2277ff382e5805` | Inline rich editor, mentions, tasks, reactions/attachments closeout | PASS in inline reply, mention, task, and visual gates |
| F-4 read/unread live state | #1039 | #1065 | `395786a62c0a6594d7dd0619ed9d9953e803105d` | Feature activation visibility and live state | PASS in current rail/read harness coverage |
| F-4 read/unread supplement op | #1056 | #1064 | `31a5b7a5857f21f964909a73af084d42fa99bfc9` | markBlipRead supplement op and live unread decrement | PASS in task/read-state covered flows |
| F-2 chrome correction | #1060 | #1061 | `1e9622e79ea5fa326cba71a0ab6cba46456448ef` | Removed duplicate legacy layout from live UI | PASS in screenshots and visual parity harness |
| J-UI wave list | #1079 | #1088 | `f6e0120787d8a204031021c7f8b5e7f3305aa32b` | Search rail renders wave list | PASS in harness search/smoke gates |
| J-UI folder/filter chips | #1080 | #1093 | `65ce7d3cc5c22226fc6130c9e7a2cbe7caf47050` | Filter switching functional | PASS in harness search gate |
| J-UI new wave | #1081 | #1090 | `db8406b2e64efa4b5c4a2b6e4a6359024aec4044` | Create flow present | PASS through parity authoring flows |
| J-UI threaded reading | #1082 | #1089 | `27a8010cfebb837d81fc3a1ee5eac809a35e6511` | Open-wave threaded view | PASS in `wave-reading-parity.spec.ts` |
| J-UI rich composer | #1083 | #1095 | `139581d15522082d81d81f174696a8ee435a2e26` | Inline composer and toolbar | PASS in `inline-reply-parity.spec.ts` |
| J-UI task toggle | #1084 | #1097 | `6c675fef7d8b9935d9416117185f20d93b7dfebb` | Per-blip task/done state | PASS in `tasks-parity.spec.ts` |
| J-UI unread cue | #1085 | #1094 | `0aef6079b261ad8796a9f3a558abd28a59269d4b` | Read-state hooks | PASS through current rail/read harness coverage |
| J-UI server first paint | #1086 | #1096 | `f432d40f9c225276c73505e285b69ba153376a65` | Server-first selected wave | PASS in smoke and viewport server tests |
| Visual shell chrome | #1099 | #1104 | `d5e7836cda53ac6958510a8b41e4a62cac9a5de2` | Shell rebuilt to product chrome | PASS in visual parity harness |
| Visual product strings | #1100 | #1106 | `0a2d157d672469a0192e058affa6267e36c3dab9` | Developer strings stripped | PASS by current screenshots and harness |
| Visual toolbar icons | #1101 | #1105 | `1177a9dd666915a7f91f7cd5bc03229e9f54bef7` | Toolbar icon parity | PASS in visual/composer gates |
| Visual per-blip chrome | #1102 | #1107 | `240f9150fba04ffae27f45ceea1f0b99d74a563c` | Author/avatar/timestamp/focus | PASS in reading/visual gates |
| Visual density | #1103 | #1108 | `30efe60482bba4301f77456831cf1814b333c569` | Rail/panel spacing tuned | PASS in visual parity harness |
| G-PORT harness foundation | #1110 | #1119 | `9a64b6cc392b5391147db698d6a8882e63fa2d7f` | Playwright harness exists | PASS: 21 tests ran |
| G-PORT search panel | #1111 | #1120 | `cacffb534686759031dbd501e42c8e17612fa72c` | GWT digest-card clone | PASS in search gate |
| G-PORT wave reading | #1112 | #1124 | `198c5bb49fb7374341a049e01c792b76ab303119` | Threaded reading parity | PASS in reading gate |
| G-PORT inline reply | #1113 | #1122 | `058616923510971692484a12d613538023119014` | Inline reply and toolbar | PASS in inline reply gate |
| G-PORT mention autocomplete | #1114 | #1138 | `30975eb4e46592dfe020fc699d5218b5a2410fca` | Mention popover parity | PASS in mention gates |
| G-PORT tasks | #1115 | #1131 | `92e49aaad18d0b54d8672a2f679b94067eddebf5` | Task widget parity | PASS in task gates |
| G-PORT keyboard shortcuts | #1116 | #1126 | `f52d74e5ea31cf571cec3ceb69ef5c5bde1ba0a7` | Key handler parity | PASS in keyboard gates |
| G-PORT top actions | #1117 | #1130 | `5239310e68a9427a416ddc39cc60e34618407c80` | Pin/archive/version actions | PASS in action gates |
| G-PORT visual parity | #1118 | #1139 | `9009a3e940081d194234d019df0fa3ff143014d2` | Visual diff budget | PASS in visual gates |
| Focus follow-up | #1133 | #1134 | `98e6151ef9c769aa5438092f3725bfb8236b7406` | Repeated `j` advances focus | PASS in keyboard gate |
| Participant timing follow-up | #1128 | #1135 | `a25e35e994744866a406f1bb8ac430eb99d33cd4` | J2CL reply participant timing | PASS in mention/reply gates |
| Mention keyboard follow-up | #1125 | #1136 | `0572e2f592a71c439b215927a3b18b69b3f34268` | Arrow-key popover parity | PASS in keyboard/mention gates |
| GWT inline reply follow-up | #1121 | #1137 | `ac033b14d6d715dab0665887ee85472984f1468f` | GWT baseline automated | PASS in inline reply gate |
| Participant/action follow-up | #1074 | #1148 | `e7de8e1b8d95160b3869eb33a4cafd2b5050f4bc` | Header parity actions | PASS in action gates |
| Color picker follow-up | #1073 | #1150 | `b8daac534c409840a467ff29ea0e5f8aa7e576c3` | Text/highlight color picker | Covered by toolbar/status docs; no new gap found |
| Viewport CPU follow-up | #1041 | #1151 | `d28050823be33b80dbe810f38972b892e210d6d9` | Windowed traversal filter | PASS in focused viewport SBT tests |
| Attachment filter follow-up | #1091 | #1156 | `73948bda9af21cf8a3b96c68e634bfb50cf450aa` | `has:attachment` server-side filter | PASS in search gate and merged issue evidence |
| From filter follow-up | #1092 | #1157 | `67207336453ad64a5384786dfcb53a75228c6dd7` | `from:` server-side filter | PASS in search gate and merged issue evidence |
| Bootstrap JSON cleanup | #978 | #1158 | `2b2a083d194dcc6f6a7e980927960357835b905a` | Removed J2CL root HTML bootstrap fallback | PASS in smoke, SBT compile/J2CL gate, and merged PR checks |

## Verification

| Command or check | Result | Notes |
| --- | --- | --- |
| `sbt --batch compile j2clSearchTest` | PASS | Completed successfully at 2026-05-01 00:41 Asia/Jerusalem (2026-04-30 21:41 UTC); only existing deprecation warnings were emitted |
| `cd wave/src/e2e/j2cl-gwt-parity && npm ci && npx tsc --noEmit` | PASS | `npm ci` added 10 packages from lockfile; `0` vulnerabilities; TypeScript exited 0 |
| `python3 scripts/assemble-changelog.py` | PASS | Required once because `wave/config/changelog.json` was absent in the fresh worktree; generated file matched tracked/ignored state with no git diff |
| `bash scripts/worktree-boot.sh --port 9959` | PASS after changelog assembly | GWT assets and Lit shell staged successfully |
| `PORT=9959 bash scripts/wave-smoke.sh check` | PASS | `ROOT_STATUS=200`, `GWT_VIEW_STATUS=200`, `ROOT_GWT=present`, `HEALTH_STATUS=200`, `LANDING_STATUS=200`, `J2CL_ROOT_STATUS=200`, `J2CL_ROOT_SHELL=present`, `J2CL_INDEX_STATUS=200`, `SIDECAR_STATUS=200`, `WEBCLIENT_STATUS=200` |
| `WAVE_E2E_BASE_URL=http://127.0.0.1:9959 npx playwright test --project=chromium` | PASS | `21 passed (2.4m)` |
| `sbt --batch 'Test / testOnly org.waveprotocol.wave.client.render.ReductionBasedRendererFilterTest org.waveprotocol.box.server.rpc.render.WaveContentRendererWindowTest org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRendererWindowTest org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest'` | PASS | `31` tests, `0` failures, covering viewport hints, snapshot windowing, and renderer traversal filtering |
| `sbt --batch 'JakartaTest / testOnly org.waveprotocol.box.server.rpc.J2clStageThreeComposeS3ParityTest org.waveprotocol.box.server.rpc.J2clStageThreeFinalParityTest'` | PASS | `15` total, `14` passed, `1` assumption-skipped chained suite; rollup prints R-5.1 through R-5.7 covered, including R-5.5 reactions and R-5.6 attachments |
| Browser: `/?view=j2cl-root` | PASS | J2CL shell rendered and the Playwright suite captured the J2CL reading-flow screenshot |
| Browser: `/?view=gwt` | PASS | Legacy GWT bootstrap rendered and the Playwright suite captured the matching GWT reading-flow screenshot |
| Claude Opus review | BLOCKED | `You've hit your limit · resets May 2 at 9pm (Asia/Jerusalem)` with fallback disabled |

## Browser Evidence

- J2CL reading parity screenshot:
  [`docs/superpowers/screenshots/issue-1159/wave-reading-parity-j2cl.png`](../screenshots/issue-1159/wave-reading-parity-j2cl.png)
- GWT reading parity screenshot:
  [`docs/superpowers/screenshots/issue-1159/wave-reading-parity-gwt.png`](../screenshots/issue-1159/wave-reading-parity-gwt.png)

## Daily Flow Audit

| Flow | J2CL result | GWT result | Verdict | Evidence |
| --- | --- | --- | --- | --- |
| Search panel and inbox digest | Search rail renders digest cards and refresh/search affordances | GWT digest rail remains reachable through `?view=gwt` | PASS | `search-panel-parity.spec.ts` |
| Open selected wave and read visible blips | Threaded blip cards render with author/chrome/focus hooks | GWT wave panel renders the same test wave | PASS | `wave-reading-parity.spec.ts` and screenshots |
| Viewport-scoped rendering for large waves | Server and RPC viewport-window tests clamp visible fragments and avoid whole-wave traversal | Legacy whole-wave path remains intact for GWT | PASS | Focused 31-test viewport SBT command |
| Keyboard navigation with `j`/`k` and focus frame | `j`/`k` move J2CL blip focus; repeated `j` regression covered | GWT baseline covered | PASS | `keyboard-shortcuts-parity.spec.ts` |
| Inline reply and submit | J2CL sends bold-applied inline reply | GWT sends equivalent reply | PASS | `inline-reply-parity.spec.ts` |
| Rich toolbar applies formatting | Composer toolbar applies bold in committed reply path | GWT baseline reply path covered | PASS | `inline-reply-parity.spec.ts`, visual composer gate |
| Mention autocomplete | J2CL popover opens, keyboard selects a participant, submit persists | GWT mention popover baseline covered | PASS | `mention-autocomplete-parity.spec.ts` and keyboard popover test |
| Task toggle and done state | J2CL task toggle flips and persists `data-task-completed` | GWT insert-task/done/reload baseline covered | PASS | `tasks-parity.spec.ts` |
| Wave actions and top-level chrome | J2CL pin/unpin, archive/restore, version history pass | GWT action baseline covered | PASS | `wave-actions-parity.spec.ts` |
| Profile/version/admin overlays | Version history path covered; admin/profile chrome remains reachable | GWT baseline remains available | PASS for version/action scope | `wave-actions-parity.spec.ts`; no new gap found |
| Reactions | Reaction primitives, event wiring, telemetry, active-user state, and read-renderer mounting are present | GWT remains baseline route | PASS for current source/Jakarta parity scope | `J2clStageThreeComposeS3ParityTest` and F-3 rollup |
| Attachments and attachment filter | J2CL attachment parity rows and `has:attachment` filter shipped | GWT remains baseline route | PASS for current covered scope | `J2clStageThreeFinalParityTest`, #1091/#1156 evidence, and search gate |

## Remaining Gaps

No remaining implementation gap was found by the current audit gates.

Two boundaries remain explicit:

- Production rollout/default-root cutover is not part of this audit. GWT stays
  available through `/?view=gwt`, and any default switch needs a separate
  operator rollout issue.
- Legacy GWT retirement is not part of this audit. Removing the GWT route or
  packaging should remain blocked until a later rollback/soak decision.

## Tracker Actions

After this audit PR merges:

1. Post the audit link and verification summary to #904.
2. Close #1078 as completed by #1079-#1086 plus follow-ups.
3. Close #1098 as completed by #1099-#1103.
4. Close #1109 as completed by #1110-#1118 plus #1121/#1125/#1128/#1133.
5. Close #1159.
6. Leave #904 open only if maintainers still want a future rollout/default-root
   tracker. Otherwise close it as superseded by this audit and require any
   future cutover/retirement work to open a new concrete issue.
