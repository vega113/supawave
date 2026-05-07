# Issue 1205: J2CL Wave Blip UI Parity

## Goal

Fix the J2CL read surface regressions reported on 2026-05-07:

- Clicking a blip should focus it and mark it read without making the page jump.
- Unread blips should be visually distinguishable in the blip body, not only by nav-row counts.
- Missing author metadata should not render avatar initials as `?`.
- Inline-thread controls should only be visible when they are the real toggle affordance, with clear labels/tooltips.
- Replies at the inline-depth limit should become regular sibling replies instead of failing server validation.

## Plan

1. Add focused regression coverage around click-to-read, thread toggle labels/visibility, and reply depth fallback.
2. Change the read renderer click path to focus with `preventScroll` and keep immediate mark-read behavior.
3. Update thread toggle rendering to hide parent-owned duplicate buttons and use chevron glyphs plus matching `title`/`aria-label` on visible controls.
4. Update `<wave-blip>` styling so unread blips receive a stronger body highlight, and replace `?` avatar fallback with a stable unknown-user label.
5. Extend conversation-manifest parsing and write-session metadata with a post-blip sibling insert position/depth, then use it to emit regular replies when the target blip is already at the inline reply limit.
6. Add a changelog fragment and run narrow J2CL tests plus local sanity before PR.

## Acceptance

- Clicking an unread rendered blip removes the unread marker and fires mark-read immediately.
- Visible inline-thread buttons show `▾`/`▸`, expose tooltips, and do not bubble into blip navigation.
- Parent-owned inline-thread duplicate buttons are hidden from view and accessibility.
- Missing-author avatars no longer show `?`.
- At depth 5 and above, reply deltas insert a sibling `<blip>` after the target blip rather than a nested `<thread>`.
