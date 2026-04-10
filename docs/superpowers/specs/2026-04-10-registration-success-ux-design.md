# Registration Success UX Design

## Context

Issue `#795` targets the server-rendered registration and activation journey:

- `POST /auth/register` currently re-renders the registration form and only reveals a small `sign in` link on success.
- `GET /auth/confirm-email` renders a minimal message-only confirmation page.
- `POST /auth/signin` treats "valid credentials, but email still unconfirmed" as a failed login and shows the message in the generic error slot.

This makes the next step unclear in both account modes:

- direct sign-in mode: the account is ready immediately, but the page still looks like a form that needs more work
- email-confirmation mode: the user needs to check email, but the page does not shift into an activation-oriented state

## Goals

- Replace the post-registration "message on top of the form" pattern with dedicated success and action-required states.
- Make the primary action match the actual account state.
- Keep the auth pages visually aligned with the current SupaWave auth shell and recent polished server-rendered pages.
- Avoid widening scope into account model changes, redirects, or new backend flows.

## Options Considered

### 1. Bigger inline banner on the existing registration form

Pros:
- lowest implementation cost
- minimal servlet change

Cons:
- still leaves the user on a form that no longer matches their state
- does not solve the "pending activation is styled as login failure" problem

### 2. PRG into dedicated auth-state surfaces inside the existing auth shell

Pros:
- clear state transition using the existing auth routes
- keeps current SupaWave auth branding and renderer structure
- lets direct sign-in, pending activation, and email-confirmed states each present the right CTA

Cons:
- requires some renderer branching and servlet response plumbing

### 3. Redirect-based flash flow to `/auth/signin`

Pros:
- one canonical post-registration destination
- conventional for direct-login products

Cons:
- requires redirect-state plumbing
- harder to make email-confirmation mode feel first-class without adding more route/state handling

## Recommendation

Use option 2.

The narrowest correct fix is to keep the existing auth shell and CSS foundation, then use PRG on top of the existing auth routes:

- direct registration redirects to `/auth/signin?registered=1`
- confirmation-required registration redirects to `/auth/register?check-email=1`
- unconfirmed sign-in renders a dedicated action-required state on the sign-in surface

This improves clarity without changing the account lifecycle or adding new standalone routes.

## Proposed UX

### Registration when email confirmation is disabled

Redirect to sign-in and render a prominent success state above the form:

- headline: account created
- supporting copy: the user can now sign in with the new account
- context chip: account ready / instant access
- keep the sign-in form directly available so the next action is immediate

### Registration when email confirmation is enabled

Redirect to a dedicated check-email page:

- headline: check your inbox
- supporting copy: we sent an activation link and sign-in will work after confirmation
- short next-step list:
  - open the activation email
  - click the confirmation link
  - return to sign in
- primary CTA: `Go to sign in`
- secondary CTA: `Create another account`

This keeps the user oriented even if they navigate to sign-in immediately afterward.

### Sign-in attempt for an unconfirmed account

Do not style this as a bad-credentials error.

Keep the sign-in form available, but replace the generic error treatment with an action-required notice:

- same authentication page shell
- non-error visual treatment
- copy based on resend result:
  - resend sent
  - resend throttled / failed
- supporting line that the account will unlock after email confirmation

This preserves the current resend behavior while making the state accurate.

### Email confirmation page

Upgrade the confirmation result page to match the new registration success language:

- success: confirmed and ready to sign in
- already confirmed: still ready to sign in
- invalid or expired: link problem, return to sign in

The CTA remains `Go to Sign In`, but the page should feel intentional rather than generic.

## Implementation Notes

- Keep logic in the Jakarta override copies only.
- Prefer renderer-level composition over route additions.
- Introduce a small auth-page state model rather than encoding more behavior in free-form strings.
- Reuse the visual language already present in `AUTH_CSS` and the polished robot success card rather than inventing a second design system.

## Non-Goals

- no automatic sign-in after registration
- no new resend endpoint
- no localization overhaul
- no session semantics changes
