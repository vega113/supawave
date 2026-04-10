# Registration Success UX — Design Spec

**Date:** 2026-04-10  
**Issue:** #795  
**Status:** Approved

---

## Problem

After successful registration the user stays on the registration form with only a small
"Please sign in." link appended. This is confusing and not industry-standard:

- The registration form remains visible with its fields filled-in.
- The call-to-action ("sign in") is a small inline hyperlink.
- The email-confirmation path gives no dedicated guidance.

---

## Goals

- Post-registration flow is clearly terminated (form goes away).
- Next action is obvious and prominent.
- Email-confirmation path gets a dedicated "Check your inbox" page.
- No new HTTP routes or servlets required.

---

## Two Post-Registration Paths

### Path A: Direct login (email confirmation disabled)

**Flow:** user submits form → success → `302 /auth/signin?registered=1`

Sign-in `doGet` reads the `registered` query param.  If `1`, it passes message
`"Account created! Sign in to get started."` with `responseType = SUCCESS` to
`renderAuthenticationPage`.

The sign-in page adds a **green success banner** _above_ the form (new `div#successBanner`).
JS shows it for `RESPONSE_STATUS_SUCCESS`, keeps `messageLbl` inside the form for errors only.

### Path B: Email confirmation required

**Flow:** user submits form → success → `302 /auth/register?check-email=1`

Registration `doGet` reads `check-email` param. If `1`, it calls a new
`writeCheckEmailPage` helper which renders `HtmlRenderer.renderCheckEmailPage(domain, analyticsAccount)`.

The "Check your inbox" page:
- Heading: **Check your inbox**
- Body: "We've sent a confirmation email. Click the link inside to activate your account."
- Footer link: "Back to sign in →"
- Shares the same SupaWave auth CSS / branding as other auth pages.

### Error path (unchanged)

Validation or server errors re-render the registration form with an inline error message.

---

## Changes Required

| File | Change |
|---|---|
| `UserRegistrationServlet.java` | `doPost`: on success, `sendRedirect` instead of re-rendering. `doGet`: check `check-email=1` param. New `writeCheckEmailPage`. |
| `AuthenticationServlet.java` | `doGet`: check `registered=1` param; pass SUCCESS message. |
| `HtmlRenderer.java` | `renderAuthenticationPage`: add `div#successBanner` above form; JS handles SUCCESS. Add `renderCheckEmailPage` method. |

---

## Out of Scope

- Auto-login after registration (separate security discussion).
- Resend-confirmation-email UI on the check-email page.
- Internationalization / i18n.
