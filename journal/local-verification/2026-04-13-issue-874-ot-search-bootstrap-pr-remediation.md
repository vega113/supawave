Worktree: /Users/vega/devroot/worktrees/vega113-incubator-wave-pr-874-monitor
Branch: monitor/vega113-incubator-wave/pr-874
PR: https://github.com/vega113/supawave/pull/874

Verification:
- `sbt 'testOnly org.waveprotocol.box.server.frontend.WaveClientRpcImplTest org.waveprotocol.box.search.SearchPresenterLoadingStateTest org.waveprotocol.box.webclient.search.SearchPresenterTest'`
  - Result: passed
- `python3 scripts/assemble-changelog.py`
  - Result: `assembled 173 entries -> wave/config/changelog.json`
- `python3 scripts/validate-changelog.py`
  - Result: `changelog validation passed`
- `sbt compile && sbt test`
  - Result: passed (`Total 2271, Failed 0, Errors 0, Passed 2271`)

Review remediation summary:
- Reject OT search bootstrap opens when the supplied `waveId` does not match the computed synthetic search wave for the query.
- Preserve search results after OT bootstrap failure only when they belong to the current query; clear stale results from previous queries.
- Remove the dead `otSearchFallbackEnabled` parameter from `SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts(...)`.
- Revalidated the generated changelog workflow with assemble + validate instead of removing the assembled file, which this repo expects to be regenerated.
