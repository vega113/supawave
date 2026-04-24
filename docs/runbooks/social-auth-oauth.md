# Social Sign-In OAuth Setup

Status: Current
Owner: Project Maintainers
Updated: 2026-04-24

This runbook configures feature-flagged Google and GitHub sign-in/sign-up for
SupaWave.

Social auth does not replace Wave usernames. A first-time social user must still
choose a SupaWave username, which becomes their participant address such as
`yuri@supawave.ai`. Google or GitHub only verifies the provider identity and
email used to create or later sign in to that SupaWave account.

## Callback URLs

Use the production public base URL for the OAuth redirect/callback URLs:

```text
https://<public-supawave-host>/auth/social/callback/google
https://<public-supawave-host>/auth/social/callback/github
```

Set `WAVE_PUBLIC_URL=https://<public-supawave-host>` unless you set explicit
provider redirect URI env vars. Production callbacks must be HTTPS.

## Google

Official setup references:

- Google OpenID Connect: https://developers.google.com/identity/openid-connect/openid-connect
- Google OAuth web server flow: https://developers.google.com/identity/protocols/oauth2/web-server

Steps:

1. Open Google Cloud Console.
2. Select or create the project that owns SupaWave auth.
3. Configure the OAuth consent screen for the SupaWave domain.
4. Create OAuth client credentials with application type `Web application`.
5. Add the authorized redirect URI:

   ```text
   https://<public-supawave-host>/auth/social/callback/google
   ```

6. Record the client ID and client secret.
7. Configure the runtime:

   ```bash
   export WAVE_GOOGLE_OAUTH_CLIENT_ID='...apps.googleusercontent.com'
   export WAVE_GOOGLE_OAUTH_CLIENT_SECRET='...'
   export WAVE_GOOGLE_OAUTH_REDIRECT_URI='https://<public-supawave-host>/auth/social/callback/google'
   ```

The server requests `openid email profile`, validates the Google ID token
signature, issuer, audience, expiry, nonce, and requires `email_verified=true`.

## GitHub

Official setup references:

- Creating an OAuth App: https://docs.github.com/en/developers/apps/creating-an-oauth-app
- Authorizing OAuth Apps: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps
- User emails API: https://docs.github.com/en/rest/users/emails

Steps:

1. In GitHub, go to Developer settings -> OAuth Apps -> New OAuth App.
2. Set the application name to SupaWave or the deployment-specific name.
3. Set Homepage URL to the SupaWave public URL.
4. Set Authorization callback URL:

   ```text
   https://<public-supawave-host>/auth/social/callback/github
   ```

5. Register the app and generate a client secret.
6. Configure the runtime:

   ```bash
   export WAVE_GITHUB_OAUTH_CLIENT_ID='...'
   export WAVE_GITHUB_OAUTH_CLIENT_SECRET='...'
   export WAVE_GITHUB_OAUTH_REDIRECT_URI='https://<public-supawave-host>/auth/social/callback/github'
   ```

The server requests `read:user user:email`, uses PKCE S256, and requires a
verified primary email from GitHub. If GitHub has no verified primary email, the
user must verify one in GitHub before using social sign-in.

## Shared Config

Optional:

```bash
export WAVE_SOCIAL_AUTH_HTTP_TIMEOUT_SECONDS=10
```

Do not commit provider secrets. Configure them through the deployment secret
manager or process environment.

## Feature Flag

The OAuth buttons are hidden until the provider secrets are configured and the
`social-auth` feature flag is globally enabled.

Enable for global rollout:

```bash
scripts/feature-flag.sh set social-auth \
  "Enable Google and GitHub social sign-in and sign-up" \
  --enabled true
```

For a fresh environment, operators can seed the initial value through config:

```bash
export WAVE_SOCIAL_AUTH_ENABLED=true
```

If the flag already exists in the feature-flag store, the admin/API value is
preserved.

Per-user allowlist entries can test authenticated account-linking direct
`/auth/social/<provider>` links, but unauthenticated social sign-in and
first-time social signup require the flag to be globally enabled because the
SupaWave participant does not exist until the user chooses a username.

## Rollout Notes

- Keep `social-auth` disabled until every runtime has the new binary.
- File-backed deployments must do a full stop/start rollout before enabling the
  flag; mixed-version file-store rollout is not supported for social auth.
- Mongo-backed deployments run a Mongock migration that adds a unique index for
  provider/subject identity lookup.
- Existing password accounts are not silently auto-linked by email. A social
  provider email matching an existing account gets a generic failure page.
- OAuth start, callback, and username-completion endpoints have a small
  per-process rate limit keyed by `HttpServletRequest.getRemoteAddr()`. If the
  server sits behind a reverse proxy, configure the proxy/container so remote
  addresses reflect clients rather than a single load-balancer address.

## Troubleshooting

- No buttons on `/auth/signin`: confirm the global flag is enabled and the
  selected provider has both client ID and client secret.
- Provider says redirect URI mismatch: compare the configured provider callback
  with `WAVE_*_OAUTH_REDIRECT_URI` and `WAVE_PUBLIC_URL`.
- GitHub rejects login: verify the GitHub account has a primary, verified email.
- Google rejects login: verify the Google OAuth client ID matches the deployment
  config and the callback URL is registered on the web application client.
