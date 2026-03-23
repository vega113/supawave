# Email Authentication Features Plan

## Overview

Add email-based authentication features to Apache Wave: email confirmation on registration,
password reset via email, and magic link login. All features are configurable and disabled
by default (except password reset).

## Architecture: MailProvider Interface

```
MailProvider (interface)
  +-- sendEmail(to, subject, htmlBody)

LoggingMailProvider (default, dev)
  - Logs email content to server log

ResendMailProvider
  - Sends via Resend API (https://api.resend.com/emails)
  - Requires core.resend_api_key config

MailModule (Guice)
  - Reads core.mail_provider config
  - "logging" -> LoggingMailProvider
  - "resend" -> ResendMailProvider
  - FQCN -> reflective instantiation (custom providers)
```

## Token Approach

Reuse existing JWT infrastructure (JwtKeyRing, RsaJwtIssuer, JwtClaims).

Add three new token types to `JwtTokenType`:
- `PASSWORD_RESET("password-reset")` -- 1 hour TTL
- `MAGIC_LINK("magic-link")` -- 10 min TTL
- `EMAIL_CONFIRM("email-confirm")` -- 24 hour TTL

Add one new audience to `JwtAudience`:
- `EMAIL("email")` -- used by all three token types

Create `EmailTokenIssuer` utility class that builds JwtClaims for each token type
using the shared JwtKeyRing, similar to BrowserSessionJwtIssuer.

## Email Confirmation Flow

1. User registers via `UserRegistrationServlet`
2. If `core.email_confirmation_enabled=true`:
   - Account is created with `emailConfirmed=false` field on HumanAccountData
   - EMAIL_CONFIRM JWT token generated with user address as subject
   - Email sent with link: `/auth/confirm-email?token=<jwt>`
   - Registration page shows "Check your email" message
3. If `core.email_confirmation_enabled=false` (default):
   - Normal registration, no change
4. `EmailConfirmServlet` handles GET `/auth/confirm-email?token=<jwt>`:
   - Validates JWT token (type=EMAIL_CONFIRM, not expired)
   - Sets `emailConfirmed=true` on account
   - Shows success page with link to login

### Account Pending State

Add `emailConfirmed` boolean to `HumanAccountData` interface and `HumanAccountDataImpl`.
Defaults to `true` for backward compatibility (existing accounts are confirmed).
When `email_confirmation_enabled=false`, accounts are created with `emailConfirmed=true`.

The login flow in `AuthenticationServlet` checks `emailConfirmed` before allowing login
when `email_confirmation_enabled=true`.

## Password Reset Flow

1. User clicks "Forgot password?" on login page
2. GET `/auth/password-reset` shows form requesting email/username
3. POST `/auth/password-reset` with username:
   - Looks up account, gets email (username@domain)
   - Generates PASSWORD_RESET JWT token
   - Sends email with link: `/auth/password-reset?token=<jwt>`
   - Shows "Check your email" message (always, even if account not found -- no enumeration)
4. GET `/auth/password-reset?token=<jwt>`:
   - Validates token
   - Shows new password form
5. POST `/auth/password-reset?token=<jwt>` with new password:
   - Validates token
   - Updates password digest on account
   - Shows success page with link to login

## Magic Link Flow

1. User clicks "Login with email" on login page (if `core.magic_link_enabled=true`)
2. GET `/auth/magic-link` shows form requesting email/username
3. POST `/auth/magic-link` with username:
   - Generates MAGIC_LINK JWT token
   - Sends email with link: `/auth/magic-link?token=<jwt>`
   - Shows "Check your email" message
4. GET `/auth/magic-link?token=<jwt>`:
   - Validates token
   - Creates session (same as normal login)
   - Issues browser session JWT cookie
   - Redirects to `/`

## New Servlets

| Path | Servlet | Method |
|---|---|---|
| `/auth/confirm-email` | `EmailConfirmServlet` | GET (confirm) |
| `/auth/password-reset` | `PasswordResetServlet` | GET (form/confirm), POST (request/update) |
| `/auth/magic-link` | `MagicLinkServlet` | GET (form/login), POST (request) |

## Config Changes (reference.conf)

```hocon
core {
  # ... existing ...

  # Mail provider: "logging" (default, dev), "resend", or FQCN
  mail_provider = "logging"

  # Resend API key (required when mail_provider = "resend")
  resend_api_key = ""

  # Email confirmation on registration
  email_confirmation_enabled = false

  # Password reset via email
  password_reset_enabled = true

  # Magic link login
  magic_link_enabled = false

  # Token expiry durations
  magic_link_expiry_seconds = 600
  password_reset_expiry_seconds = 3600
  email_confirm_expiry_seconds = 86400

  # From address for outgoing emails
  email_from_address = "noreply@wave.example.test"
}
```

## UI Pages (HtmlRenderer methods)

- `renderPasswordResetRequestPage(domain, message, responseType, analyticsAccount)` -- request form
- `renderPasswordResetFormPage(domain, token, message, responseType, analyticsAccount)` -- new password form
- `renderMagicLinkRequestPage(domain, message, responseType, analyticsAccount)` -- request form
- `renderEmailConfirmationPage(domain, message, responseType, analyticsAccount)` -- confirmation result
- `renderGenericMessagePage(domain, title, message, analyticsAccount)` -- reusable success/error page

Modify `renderAuthenticationPage` to add:
- "Forgot password?" link (when password_reset_enabled)
- "Login with email" link (when magic_link_enabled)

Modify `renderUserRegistrationPage` to show confirmation message when email confirmation active.

## Files to Create

- `wave/src/main/java/org/waveprotocol/box/server/mail/MailProvider.java`
- `wave/src/main/java/org/waveprotocol/box/server/mail/LoggingMailProvider.java`
- `wave/src/main/java/org/waveprotocol/box/server/mail/ResendMailProvider.java`
- `wave/src/main/java/org/waveprotocol/box/server/mail/MailModule.java`
- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/EmailTokenIssuer.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PasswordResetServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/MagicLinkServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java`

## Files to Modify

- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtTokenType.java` -- add 3 types
- `wave/src/main/java/org/waveprotocol/box/server/authentication/jwt/JwtAudience.java` -- add EMAIL
- `wave/src/main/java/org/waveprotocol/box/server/account/HumanAccountData.java` -- add emailConfirmed
- `wave/src/main/java/org/waveprotocol/box/server/account/HumanAccountDataImpl.java` -- implement emailConfirmed
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java` -- send confirm email
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java` -- links + confirm check
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` -- new render methods
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` -- register servlets + MailModule
- `wave/config/reference.conf` -- add mail config
