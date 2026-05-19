# Issue 1293: Remove Duplicate J2CL Sign Out

## Investigation

- The Lit `<wavy-header>` renders signed-in compact controls as locale, save status, network status, notifications, inbox, and the user-menu pill.
- The user-menu dropdown already contains the sign-out link and uses the `return-target` attribute to preserve the J2CL return route.
- The Jakarta SSR root shell emits that same `<wavy-header>` light DOM, including the user-menu sign-out link, then appends a second top-level `slot="actions-signed-in"` sign-out anchor after the header actions.
- That second top-level anchor is stale from before the user-menu dropdown shipped and can crowd the compact topbar next to the Admin action.

## Plan

1. Add a focused SSR regression test proving signed-in J2CL root markup has no top-level sign-out anchor in the shell-header actions slot.
2. Keep the user-menu sign-out link and route-sync contract intact.
3. Remove the stale top-level sign-out anchor from the signed-in J2CL root shell SSR path.
4. Update the changelog because this changes visible J2CL topbar behavior.
5. Run focused Lit/Java tests, changelog validation, diff checks, and a browser-level sanity check for visible controls.

## Self-Review Of Plan

- Scope stays on J2CL compact root topbar markup; it does not alter sign-out servlet behavior or GWT topbar behavior.
- The Admin menu item remains available inside the user menu and the existing top-level Admin link is left alone so the hidden-button symptom is addressed without removing unrelated affordances.
- Verification needs both SSR coverage and Lit coverage because the duplicate exists pre-upgrade while the working user-menu behavior lives in the upgraded element.
