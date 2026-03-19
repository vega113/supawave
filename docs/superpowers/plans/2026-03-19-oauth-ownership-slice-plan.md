# OAuth Ownership Slice Plan

Task: `incubator-wave-modernization.3`
Branch: `oauth-ownership`
Worktree: `/Users/vega/devroot/worktrees/incubator-wave/oauth-ownership`

## Goal
Close the remaining OAuth ownership debt left open after the merged library-upgrades slice by either internalizing the required legacy OAuth implementation or removing the external ownership model if it is provably unused.

## Scope
- Verify the actual `net.oauth` surface still exercised by the server/runtime path.
- Prove whether `-PexcludeLegacyOAuth=true` now removes the external dependencies cleanly on compile and runtime classpaths.
- Decide the narrowest safe ownership path:
  - repo-local ownership of the still-required OAuth code, or
  - explicit removal of dead OAuth-backed endpoints if usage is gone.
- Keep the result traceable under `incubator-wave-modernization.3` rather than inventing a new modernization task.

## Immediate checks
- `./gradlew -q :wave:dependencyInsight --dependency oauth --configuration compileClasspath`
- `./gradlew -q :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath`
- `./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration compileClasspath`
- `./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath`
- `rg -n 'net\.oauth|OAuthServiceImpl|OpenSocialHttp(Client|Message|ResponseMessage)|DataApiOAuthServlet|oauth-provider|oauth-consumer' wave/src/main/java wave/src/jakarta-overrides/java wave/src/test/java`

## Deliverables
- A narrowed OAuth decision recorded in docs/Beads.
- If code is still required, a follow-up implementation slice that removes external repository dependence while preserving behavior.
- If code is unused, a removal slice with targeted verification.
