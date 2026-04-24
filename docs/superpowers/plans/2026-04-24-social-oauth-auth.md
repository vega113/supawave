# Feature-Flagged Social Sign-In Plan

Issue: https://github.com/vega113/supawave/issues/995
Branch: `codex/social-oauth-auth-20260424`
Worktree: `/Users/vega/devroot/worktrees/codex-social-oauth-auth-20260424`
Date: 2026-04-24

## Goal

Add Google and GitHub sign-in/sign-up support so operators can enable it by adding OAuth secrets and turning on a feature flag. First-time social users must still choose a SupaWave username; the provider identity only proves email/profile ownership and links to the chosen Wave participant.

## Current Seams

- Runtime-active auth servlets are Jakarta overrides:
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Account data is persisted through `HumanAccountData`, `HumanAccountDataImpl`, proto serialization, `FileAccountStore`, and `Mongo4AccountStore`.
- Feature flags live in `FeatureFlagService` and `KnownFeatureFlags`; unauthenticated pages currently need a global-style visibility decision before a participant exists.
- `PublicBaseUrlResolver` already derives a public base URL from `core.public_url` or frontend address. OAuth callback URLs should prefer explicit configured redirect URIs and otherwise use that resolver.
- `SocialAuthServlet` must live under `wave/src/jakarta-overrides/java/...` beside the active auth servlets.
- First-user owner assignment, timestamps, welcome-wave creation, analytics, and email-confirmation defaults currently live inside `UserRegistrationServlet`; move this into a shared `AccountCreationSupport` helper and use it from password and social registration paths.

## Implementation

1. Add account-level social identity data.
   - Create `SocialIdentity` under `org.waveprotocol.box.server.account`.
   - Fields: `provider`, `subject`, `email`, `displayName`, `linkedAtMillis`.
   - Add `getSocialIdentities`, `setSocialIdentities`, and `addOrReplaceSocialIdentity` to `HumanAccountData` and `HumanAccountDataImpl`.
   - Update proto schema and serializer with a repeated `ProtoSocialIdentity` field.
   - Update file and Mongo stores to preserve identities and add lookup by `(provider, subject)`.
   - Add `AccountStore.getAccountBySocialIdentity(provider, subject)`; the default implementation may scan with a warning for non-Mongo stores, while Mongo must use an indexed lookup.
   - Add `AccountStore.linkSocialIdentity(participantId, identity)` so social-link writes can be field-targeted where the backend supports it.
   - Add a Mongo Mongock change unit for a unique partial compound multikey index:
     - keys: `human.socialIdentities.provider: 1`, `human.socialIdentities.subject: 1`
     - name: `human_social_identity_provider_subject_unique`
     - `unique(true)`, `background(true)`
     - `partialFilterExpression` requiring both `human.socialIdentities.provider` and `human.socialIdentities.subject` to exist
   - `Mongo4AccountStore.getAccountBySocialIdentity` must query with `$elemMatch`.
   - `Mongo4AccountStore.linkSocialIdentity` must use field-level update semantics, not a full account read-modify-write.

2. Add the feature flag.
   - Register `social-auth` in `KnownFeatureFlags`, default disabled.
   - Add `FeatureFlagService.isGloballyEnabled(flagName)` or equivalent snapshot helper for unauthenticated UI; contract: it returns only the flag's global `enabled` field and must return false when the flag only has allowed-user entries.
   - Render social buttons on the sign-in page only when `social-auth` is globally enabled and the provider is configured. Per-user allowlist rollout can still be tested through direct `/auth/social/<provider>` links, but the generic unauthenticated sign-in page will not advertise social login until global rollout is on.
   - Enforce `FeatureFlagService.isEnabled("social-auth", participantId)` after a provider callback resolves a participant:
     - existing linked account: check the linked account id
     - existing unlinked password account with matching email: do not auto-link; show a safe account-exists message telling the user to sign in with password until an authenticated account-linking flow exists
     - new social sign-up: require `social-auth` to be globally enabled because no account exists yet

3. Add OAuth provider support.
   - New package `org.waveprotocol.box.server.authentication.oauth`.
   - Support Google OpenID Connect with `openid email profile`.
   - Support GitHub OAuth with `read:user user:email`, fetching the verified primary email from `/user/emails`.
   - Do not persist access tokens, refresh tokens, ID tokens, or raw provider responses.
   - Use authorization-code PKCE (`S256`) for Google and GitHub. Store `code_verifier` in the server session and submit it during token exchange.
   - Generate a Google OIDC `nonce` alongside `state`, store it in the server session, and verify it against the ID token nonce claim.
   - Verify Google ID tokens locally:
     - parse JWS header/payload
     - require `alg=RS256`
     - fetch/cache Google JWKS and verify signature by `kid`
     - validate issuer, audience/client id, expiry, and nonce
     - allowed issuers: `https://accounts.google.com` and `accounts.google.com`
     - accept scalar `aud` only when it equals the configured Google client id exactly
     - accept array `aud` only when it contains the configured client id and `azp` equals the configured client id
     - allow at most 60 seconds of clock skew for `exp` and `iat`; reject `iat` values more than 60 seconds in the future
     - use the ID token claims as source of truth for `sub`, `email`, `name`, and `email_verified`
   - Require Google `email_verified=true`.
   - GitHub is OAuth 2.0, not OIDC: no ID token and no nonce validation on that path.
   - Require PKCE S256 on GitHub OAuth Apps too; fail closed if the provider rejects the PKCE-protected authorization-code exchange.
   - Require GitHub verified primary email from `/user/emails`; primary without verified is not enough. If no verified primary email is returned, reject with a generic provider-verification message that directs the user to verify their GitHub email without revealing local account existence.
   - Provider HTTP hardening:
     - strict platform TLS only; no custom trust manager
     - do not follow redirects on token/userinfo/JWKS endpoints
     - enforce connect/read timeout config
     - bound response-size reads
     - JWKS cache must be bounded with a fixed TTL and enforce a minimum refresh interval on unknown-`kid` misses
   - Config keys in `reference.conf`:
     - `core.social_auth.google.client_id`, `client_secret`, `redirect_uri`
     - `core.social_auth.github.client_id`, `client_secret`, `redirect_uri`
     - env overrides for the same secrets/redirects
     - provider HTTP timeout
   - Treat a provider as available only when client id and secret are configured.

4. Add `SocialAuthServlet`.
   - Register `/auth/social/*` in `ServerMain`.
   - `GET /auth/social/google` and `/auth/social/github`:
     - require `social-auth` global enable or an already authenticated participant with the flag enabled
     - require provider config
     - generate cryptographic `state`, `nonce` where applicable, and PKCE verifier with `SecureRandom`
     - save pending state, nonce/verifier, created-at timestamp, one-shot CSRF token, and sanitized local `r` redirect in the server session
     - redirect to the provider authorization endpoint
   - `GET /auth/social/callback/<provider>`:
     - validate and clear state
     - exchange code for profile
     - require provider subject; require verified email for account creation/linking
     - find existing linked identity and sign it in if allowed/active
     - if the request started from an already-authenticated account, link the provider only after provider email verification and only when the verified provider email matches that account's confirmed email, then keep the user signed in
     - if an already-authenticated user's provider email does not match their confirmed account email, reject the link and preserve the existing session
     - otherwise, if the provider email matches an existing unlinked account, do not auto-link; render a generic social-auth failure page instead
     - otherwise rotate the HTTP session id, store a small pending profile in session, and render username-completion page
     - clear `state`, `nonce`, `code_verifier`, and pending profile on every terminal callback outcome so state is one-shot even after failures
   - `POST /auth/social/complete`:
     - require a pending profile in session
     - require the one-shot CSRF token from the pending profile
     - reject expired pending profiles; default TTL 10 minutes
     - require registration enabled
     - validate username through `RegistrationSupport.checkNewUsername`
     - require `social-auth` globally enabled
     - create a no-password human account with verified email, social identity, timestamps, first-user owner role, analytics, and welcome wave
     - rotate the HTTP session id, clear the pending profile, then sign in with `SessionManager` and browser-session JWT cookie
   - Preserve existing suspended-account and email-confirmation blocking behavior.
   - Keep local redirect validation aligned with `AuthenticationServlet`; extract a small shared `AuthRedirects` helper rather than duplicating the whole redirect parser.
   - Use the same `AuthRedirects` helper on social entry and callback; reject open-redirect probes before storing `r` in the session.
   - Add session-id rotation to the existing password sign-in path too, via a shared helper, so password and social sign-in do not diverge.
   - Never log provider authorization codes, state values, tokens, raw responses, or authorization headers.
   - Add a small in-memory rate limiter for callback/complete failures and new social-account creation keyed by client IP and provider subject. Before a provider subject is known, fall back to `client IP + provider + callback stage` so invalid-state, invalid-code, JWKS, and oversized-response probing is bounded. Keep it conservative and testable; document that it is per-JVM/per-process and production edge rate limiting can layer on top.

5. Update server-rendered auth UI.
   - Add a `SocialProviderLink` DTO for the renderer.
   - Extend `renderAuthenticationPage` with an overload that accepts provider links; existing callers keep the empty-provider behavior.
   - Render Google/GitHub buttons only when configured and feature-flag-visible.
   - Add `renderSocialUsernamePage` for first-time social sign-up. It must clearly require a SupaWave username and show the provider/email context.
   - Add `renderSocialFailurePage` for provider verification failures and the account-exists path. The page must not echo the probed provider email, local participant id, or matched provider name in a way that reveals whether a local account exists.

6. Add operator documentation.
   - Add `docs/runbooks/social-auth-oauth.md`.
   - Include Google Cloud OAuth client setup, GitHub OAuth App setup, callback URLs, required scopes, environment variables, feature-flag rollout commands, and troubleshooting.
   - Explicit environment variables:
     - `WAVE_GOOGLE_OAUTH_CLIENT_ID`
     - `WAVE_GOOGLE_OAUTH_CLIENT_SECRET`
     - `WAVE_GOOGLE_OAUTH_REDIRECT_URI`
     - `WAVE_GITHUB_OAUTH_CLIENT_ID`
     - `WAVE_GITHUB_OAUTH_CLIENT_SECRET`
     - `WAVE_GITHUB_OAUTH_REDIRECT_URI`
     - `WAVE_SOCIAL_AUTH_HTTP_TIMEOUT_SECONDS`
   - Explicitly state that first-time social users still choose a SupaWave username.
   - Link to official Google and GitHub docs used for the setup steps.

7. Add changelog.
   - Add a changelog fragment under `wave/config/changelog.d/`.
   - Run the changelog assemble/validate workflow before PR.

## Tests First

Add focused failing tests before implementation. The final implementation keeps
the test scope focused on the behavior that can be exercised without live
provider credentials:

- `FeatureFlagServiceTest`
  - `social-auth` is known and disabled by default.
  - unauthenticated/global helper is true only for global enable.
  - allowlist populated with global off still makes unauthenticated/global helper false.
  - participant-specific allowlist still works for existing linked-account callbacks.
- `AccountStoreTestBase`
  - social identity round-trips and can be found by `(provider, subject)`.
  - replacing an account removes stale social identity lookup mappings.
  - existing mixed-case email addresses are found case-insensitively before
    social signup can claim the same provider email.
- `ProtoAccountDataSerializerTest`
  - human account social identities survive serialization/deserialization.
- `HtmlRendererAuthStateTest`
  - sign-in page omits social buttons by default.
  - sign-in page renders configured social links.
  - username-completion page requires a SupaWave username.
- `SocialAuthServletTest`
  - launch is forbidden when flag/config is missing.
  - callback rejects invalid state.
  - callback state is one-shot and cleared on failure.
  - username completion rejects invalid CSRF token.
  - callback success that leads to username completion rotates the HTTP session id before pending profile storage.
  - unmatched provider profile requires username completion.
  - username completion creates a no-password account with chosen Wave participant and linked social identity.
  - matching existing unlinked email does not auto-link and renders a generic failure page.
  - rate limiter applies to repeated callback attempts before provider subject is known.
- `GoogleIdTokenVerifierTest`
  - accepts valid RS256 ID token with matching issuer, audience, expiry, and nonce.
  - rejects wrong signature, wrong audience, expired token, wrong nonce, and `email_verified=false`.
  - accepts token within the configured clock-skew window and rejects beyond it.
  - accepts array `aud` only when it contains the client id and `azp` equals the client id.
  - rejects `iat` more than 60 seconds in the future.
- `SocialAuthServiceTest`
  - rejects GitHub primary email when `verified=false`.
  - includes PKCE verifier during token exchange.
  - wraps malformed provider JSON as a social-auth failure rather than a server error.
- Mongo persistence is covered by compile-time integration with the existing
  `Mongo4AccountStore` and Mongock migration wiring in this PR. Mixed-version
  old-binary preservation is explicitly not promised for file storage; the
  rollout notes below require a full runtime rollout before enabling the flag.
- `AuthenticationServletTest`
  - password sign-in rotates the HTTP session id after successful login.

## Verification

Run at least:

```bash
sbt -batch "testOnly org.waveprotocol.box.server.persistence.memory.FeatureFlagServiceTest org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializerTest org.waveprotocol.box.server.persistence.memory.AccountStoreTest org.waveprotocol.box.server.persistence.file.AccountStoreTest org.waveprotocol.box.server.rpc.HtmlRendererAuthStateTest org.waveprotocol.box.server.rpc.SocialAuthServletTest"
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
sbt -batch wave/compile
sbt -batch compileGwt
```

Then run a local server sanity check from the worktree. Because this is auth UI work, verify:

- `/auth/signin` renders without social buttons when `social-auth` is off or provider secrets are absent.
- With a local config override that enables `social-auth` and supplies dummy provider ids/secrets, `/auth/signin` renders Google and GitHub entry links.
- `/auth/social/google` refuses to proceed if the flag is disabled.

Integration test, not manual local sanity:

- A mocked-provider callback test exercises `/auth/social/callback/<provider>` end-to-end without external network calls.

## Operational Notes

- Keep `social-auth` disabled until the new version is fully rolled out. Older binaries do not know about social identity fields and may drop them if they deserialize accounts into the old model and rewrite them. The new Mongo social-link path uses field-targeted updates to avoid dropping fields from new binaries, but it cannot protect writes from old binaries.
- File-store operators must complete a full stop/start rollout to the new binary before enabling `social-auth`; file storage has no field-targeted update primitive, so mixed-version file-backed deployments are not supported for social-auth enablement.
- Provider client secrets must be supplied through environment/config, not committed.
- OAuth callbacks must use configured HTTPS public URLs in production.
- Provider authorization codes, state values, tokens, authorization headers, and raw responses must not be logged or persisted.
- Per-user social-auth rollout is useful for authenticated account-linking
  direct-link tests, but unauthenticated social sign-in and first-time social
  signup require global enable because the chosen account does not exist before
  completion.
- The in-memory rate limiter is per-process and is not a cluster-wide abuse boundary.

## Self-Review

- The plan uses Jakarta override files for runtime auth paths.
- The social sign-up path still requires `RegistrationSupport.checkNewUsername`, so users choose a SupaWave participant.
- The feature flag gates both UI discovery and account-level authorization.
- The persistence plan covers file, proto, and Mongo-backed runtime paths.
- The docs plan includes acquisition of Google/GitHub secrets and operator rollout.
- The verification plan includes focused tests, compile, GWT compile, changelog validation, and local auth UI sanity.
