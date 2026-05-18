Plan for issue #1259

Root cause:
- The J2CL root shell renders the right-side compact controls through <wavy-header> inside shell-header.
- The <wavy-header> user button only emits wavy-user-menu-requested; no root-shell listener consumes it, so clicking the avatar has no visible effect.
- Compact mode styles the user control as a square icon tile, while GWT uses the shared topbar user-menu-toggle/avatar/dropdown contract.

Implementation plan:
1. Add failing Lit tests for compact <wavy-header>: clicking the user control opens a dropdown menu, toggles aria-expanded, closes on Escape/outside click, and exposes GWT-style account/sign-out menu links.
2. Update j2cl/lit/src/elements/wavy-header.js narrowly:
   - Add open/close state and document/Escape handling.
   - Render a GWT-style compact user menu dropdown with account/product/legal/sign-out links.
   - Restyle compact user control to match the GWT topbar pill/avatar rather than the square icon tile.
3. Update the Jakarta HtmlRenderer SSR light DOM for <wavy-header> so the fallback markup includes the same menu and attributes before Lit upgrades.
4. Mirror necessary pre-upgrade CSS in shell-tokens.css.
5. Add a changelog fragment and run focused Lit/server renderer tests plus local smoke if feasible.

Self-review checkpoints:
- Keep the fix in the J2CL topbar seam only; do not change unrelated wave panel chrome.
- Preserve existing Admin and Sign out top-level links.
- Keep hrefs local/context-aware and escaped.
- Verify with tests before PR.
