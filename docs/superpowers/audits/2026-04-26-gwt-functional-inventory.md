# GWT functional inventory + Wavy design coverage matrix (2026-04-26)

Status: Final — input to F-2 / F-3 / F-4 acceptance criteria  
Source: live `https://supawave.ai/?view=gwt` accessibility tree + screen capture (signed-in account `vega@supawave.ai`)  
Ref notation: identifiers such as ref_1, ref_4, ref_28 are sequential accessibility-tree node IDs from the live session export; the full id → element mapping is in the exported accessibility node list captured alongside the screen capture.  
Companion: `2026-04-26-j2cl-gwt-parity-audit.md`

## Why this exists

The original parity matrix (`docs/j2cl-gwt-parity-matrix.md`) describes *behaviors* at the row level (e.g. R-3.1 "Open-wave rendering"). The 2026-04-26 audit found that even closed slices missed concrete affordances because nobody enumerated them. This inventory is the explicit list of every interactive GWT control, mapped to its functional purpose, the F-* issue that owns it in the new chain, and the corresponding wavy design surface where it lives.

The wavy design language may *render* these affordances differently — the contract is that every functional capability stays present and reachable.

## Legend

- **Surface** — where the control lives in the GWT UI today.
- **Affordance** — the user-facing function (button, menu, region).
- **Owner slice** — the new F-* issue that reproduces it.
- **Wavy design home** — where it lives in the new design (specific Stitch screen / Lit element / slot).
- **Status** — Present = covered by the design as-is; Restyled = restyled per wavy spec; New = additive beyond GWT (e.g. depth navigation); Plugin-slot = exposed via the F-0 plugin contract.

## A. Top chrome (header)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| A.1 | "SupaWave" brand link → landing | ref_1 | F-0 | Header band, signal-cyan accent dot before logotype | Restyled |
| A.2 | Locale picker (en/de/es/fr/ru/sl/zh_TW) | ref_28–36 | F-0 / F-2 | Header end region, compact globe glyph | Restyled |
| A.3 | "All changes saved" save state | ref_37 | F-4 | Header end, low-emphasis live status pill | Restyled |
| A.4 | "Online" connection state | ref_38 | F-4 | Header end, live-channel pill with pulsing cyan dot | Restyled |
| A.5 | Notifications bell (with unread dot) | (visible icon) | F-4 | Header end, stroke-only bell, violet unread dot | Restyled |
| A.6 | Inbox/mail icon | (visible) | F-2 | Header end, stroke-only mail glyph, jumps to inbox | Restyled |
| A.7 | User menu trigger (avatar + email) | ref_3, ref_39, ref_40 | F-0 | Header end, avatar chip; opens menu sheet | Restyled |
| A.8 | "Edit Profile" menu item | ref_42 | F-0 | User menu sheet | Present |
| A.9 | "Account Settings" menu item | ref_43 | F-0 | User menu sheet | Present |
| A.10 | "Robot & Data API" menu item | ref_45 | F-0 / **plugin foundation** | User menu sheet — also surfaced as "Plugins / Integrations" entry | Restyled (groundwork for plugin registry) |
| A.11 | "API Docs" menu item | ref_46 | F-0 | User menu sheet | Present |
| A.12 | "Version History" menu item | ref_48 | F-0 | User menu sheet | Present |
| A.13 | "What's New" / changelog link | ref_49 | F-0 | User menu sheet | Present |
| A.14 | "Contact Us" link | ref_50 | F-0 | User menu sheet | Present |
| A.15 | "Admin" link (gated) | ref_51 | F-0 | User menu sheet (visible when admin) | Present |
| A.16 | "Terms of Service" / "Privacy Policy" links | ref_53, ref_54 | F-0 | User menu sheet, Legal section | Present |
| A.17 | "Sign out" link | ref_55 | F-0 | User menu sheet, footer | Present |
| A.18 | Admin-only "Contact messages" link | ref_2 | F-0 | Header end (admin-gated) | Present |

## B. Search panel (left rail)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| B.1 | Search query textbox (`in:inbox` etc.) | ref_4 | F-2 | Left rail header, with waveform glyph | Restyled |
| B.2 | "Search help" trigger (`?`) → modal | ref_57 → ref_335 | F-2 | Inline ⓘ button → wavy modal | Restyled |
| B.3 | "New Wave" button (Shift+Cmd+O) | ref_58 | F-2 / F-3 | Header end button, primary cyan signal | Restyled |
| B.4 | "Manage saved searches" | ref_59 | F-2 | Saved-search row, edit affordance | Restyled |
| B.5 | Saved search: Inbox | ref_60 | F-2 | Folder rail, default-selected | Restyled |
| B.6 | Saved search: Mentions | ref_61 | F-2 | Folder rail, with violet unread dot | Restyled |
| B.7 | Saved search: Tasks | ref_62 | F-2 / F-3 | Folder rail, with amber pending count | Restyled |
| B.8 | Saved search: Public waves | ref_63 | F-2 | Folder rail | Restyled |
| B.9 | Saved search: Archive | ref_64 | F-2 | Folder rail | Restyled |
| B.10 | Saved search: Pinned waves | ref_65 | F-2 | Folder rail | Restyled |
| B.11 | "Refresh search results" | ref_66 | F-2 | Folder rail header | Restyled |
| B.12 | Result count "133 waves" | ref_67 | F-2 | Folder rail footer, low-emphasis | Restyled |
| B.13 | Per-digest avatar stack (multi-author) | ref_68, ref_73, ref_74, ref_84, ref_85, ref_91, ref_111, ref_112, ref_117, ref_118, ref_126, ref_127, ref_150, ref_151, ref_156, ref_157 | F-2 | Digest card top-left | Restyled |
| B.14 | Per-digest pinned indicator | (red pin glyph) | F-2 | Digest card top-right, cyan pin glyph | Restyled |
| B.15 | Per-digest title | ref_71, ref_77, ref_82, ref_88, ref_94, ref_99, … | F-2 | Digest card title | Restyled |
| B.16 | Per-digest snippet | ref_72, ref_78, ref_83, ref_89, ref_95, … | F-2 | Digest card body, multiline truncation | Restyled |
| B.17 | Per-digest msg count | ref_70, ref_76, ref_81, ref_87, ref_93, … | F-2 | Digest card footer, with unread badge when > 0 (signal-cyan pulse on change) | Restyled, F-4 owns the live unread badge |
| B.18 | Per-digest relative timestamp | ref_69, ref_75, ref_80, ref_86, ref_92, … | F-2 | Digest card footer, low-emphasis | Restyled |

## C. Search Help modal (filters + sort)

Triggered from B.2; the modal enumerates the search query language. Every filter/sort token must continue to parse and to be discoverable from the help modal.

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| C.1 | Filter: `in:inbox` | ref_341 | F-2 | Help modal, Filters table | Present |
| C.2 | Filter: `in:archive` | ref_343 | F-2 | Help modal | Present |
| C.3 | Filter: `in:all` | ref_345 | F-2 | Help modal | Present |
| C.4 | Filter: `in:pinned` | ref_347 | F-2 | Help modal | Present |
| C.5 | Filter: `with:user@domain` | ref_349 | F-2 | Help modal | Present |
| C.6 | Filter: `with:@` (public, shared domain) | ref_352 | F-2 | Help modal | Present |
| C.7 | Filter: `creator:user@domain` | ref_354 | F-2 | Help modal | Present |
| C.8 | Filter: `tag:name` | ref_357 | F-2 | Help modal | Present |
| C.9 | Filter: `unread:true` | ref_360 | F-2 / F-4 | Help modal | Present |
| C.10 | Filter: `title:text` | ref_362 | F-2 | Help modal | Present |
| C.11 | Filter: `content:text` | ref_365 | F-2 | Help modal | Present |
| C.12 | Filter: `mentions:me` | ref_368 | F-2 | Help modal | Present |
| C.13 | Filter: `tasks:all` | ref_370 | F-3 | Help modal | Present |
| C.14 | Filter: `tasks:me` | ref_372 | F-3 | Help modal | Present |
| C.15 | Filter: `tasks:user@domain` | ref_374 | F-3 | Help modal | Present |
| C.16 | Filter: free text (implicit `content:`) | ref_378 | F-2 | Help modal | Present |
| C.17 | Sort: `orderby:datedesc` (default) | ref_385 | F-2 | Help modal | Present |
| C.18 | Sort: `orderby:dateasc` | ref_387 | F-2 | Help modal | Present |
| C.19 | Sort: `orderby:createddesc` / `createdasc` | ref_389, ref_391 | F-2 | Help modal | Present |
| C.20 | Sort: `orderby:creatordesc` / `creatorasc` | ref_393, ref_395 | F-2 | Help modal | Present |
| C.21 | Combination examples table | ref_398 + chips | F-2 | Help modal | Present |
| C.22 | "Got it" dismiss | ref_336 | F-2 | Help modal close | Present |

## D. Wave panel header (top of opened wave)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| D.1 | Wave title (current open wave) | header text | F-2 | Wave panel header | Restyled |
| D.2 | Multi-author avatar stack | ref_5 | F-2 | Wave panel header, overlapping stack | Restyled |
| D.3 | "more" wave-actions menu | ref_211, ref_218 | F-2 | Header overflow menu (move/archive/mark-read/etc.) | Restyled |
| D.4 | "Add participant" button | ref_6, ref_10 | F-2 / F-3 | Header end, person-plus glyph | Restyled |
| D.5 | "New wave with current participants" | ref_7, ref_11 | F-2 / F-3 | Header overflow menu | Restyled |
| D.6 | "This wave is private. Click to make public" toggle | ref_8, ref_12 | F-2 / F-3 | Header end, lock/globe glyph | Restyled |
| D.7 | "Copy public link" | ref_212, ref_213 | F-2 | Header end, link glyph | Restyled |
| D.8 | "Wave is unlocked. Click to lock root blip" | ref_9, ref_13 | F-2 / F-3 | Header end, padlock glyph | Restyled |

## E. Wave panel navigation row (per-wave navigation toolbar)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| E.1 | "Recent" jump | ref_221 | F-2 | Nav toolbar, leading | Restyled |
| E.2 | "Next Unread" jump (blip-level) | ref_222 | F-2 / F-4 | Nav toolbar, primary cyan when unread present | Restyled |
| E.3 | "Previous" blip | ref_223 | F-2 | Nav toolbar | Restyled |
| E.4 | "Next" blip | ref_224 | F-2 | Nav toolbar | Restyled |
| E.5 | "Go to last message (End)" | ref_225 | F-2 | Nav toolbar trailing | Restyled |
| E.6 | "Prev @" mention | ref_226 | F-2 | Nav toolbar, violet accent | Restyled |
| E.7 | "Next @" mention | ref_227 | F-2 | Nav toolbar, violet accent | Restyled |
| E.8 | "To Archive" | ref_228 | F-2 | Nav toolbar | Restyled |
| E.9 | "Pin" wave | ref_229 | F-2 | Nav toolbar, pin glyph (cyan when pinned) | Restyled |
| E.10 | "Version History (H)" | ref_230 | F-2 | Nav toolbar, opens version-history overlay | Restyled |

## F. Per-blip controls (per-card)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| F.1 | Author avatar | ref_231, ref_240, ref_248, … | F-2 | Blip card header | Restyled |
| F.2 | Author display name | ref_237, ref_246, ref_254, … | F-2 | Blip card header | Restyled |
| F.3 | Full datetime tooltip on hover | ref_236, ref_245, ref_253, … | F-2 | Blip card header timestamp | Restyled |
| F.4 | "Reply" (inline reply at this blip) | ref_232, ref_241, ref_249, … | F-3 | Blip card per-blip toolbar (reveal on focus/hover) | Restyled |
| F.5 | "Edit" (rich-text edit mode) | ref_233, ref_242, ref_250, … | F-3 | Blip card per-blip toolbar | Restyled |
| F.6 | "Delete" | ref_234, ref_243, ref_251, … | F-3 | Blip card per-blip toolbar (with confirm) | Restyled |
| F.7 | "Link" (copy blip permalink) | ref_235, ref_244, ref_252, … | F-2 | Blip card per-blip toolbar | Restyled |
| F.8 | "Add reaction" | ref_15..ref_24 | F-3 | Blip card footer, reaction-chip rail | Restyled |
| F.9 | Existing reaction chips (e.g. `👍 1`) | (visible) | F-3 | Blip card footer, reaction chips with live count | Restyled |
| F.10 | Inline-reply chips below blip body | (visible) | F-2 | Blip card body, attached inline reply badges | Restyled |
| F.11 | Inline thread depth badge "△ N" (collapsed depth indicator) | (visible) | F-2 | **Replaced by depth-navigation pattern (G.1)** | New |
| F.12 | Per-blip plugin slot for gadget-equivalent content | n/a | F-2 / F-0 | `<slot name="blip-extension">` per blip card | Plugin-slot |

## G. Depth navigation (NEW — additive beyond GWT)

GWT clamps thread depth and shows a "△ N" collapsed badge for replies past the depth limit. The wavy design lifts the clamp by introducing a **drill-down navigation** pattern.

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| G.1 | Drill-into deep thread: clicking a "△ N" badge zooms the wave panel to that subthread; outer ancestor blips slide out and stack at the top as collapsed-context breadcrumb headers | n/a (new) | F-2 (new acceptance row R-3.7) | Wave panel; ancestors render as compact header strip; current depth blips render in full; immediate children inline | New |
| G.2 | "Up one level" navigation control | n/a (new) | F-2 | Wave panel header, leading; chevron-up + parent author label | New |
| G.3 | "Back to top of wave" jump | n/a (new) | F-2 | Wave panel header, leading | New |
| G.4 | Depth-aware URL state (deep-link to a specific subthread root) | n/a (new) | F-2 | URL `&depth=blip+id` param; survives reload and back/forward | New |
| G.5 | Keyboard: `[` / `]` to drill out / drill in along the focused path | n/a (new) | F-2 | Keyboard contract documented with E.* nav | New |
| G.6 | Live updates surface across depth levels: when a reply lands inside a subthread the user is currently focused below, a subtle "↑ 2 new replies above" pill appears in the breadcrumb context strip | n/a (new) | F-2 / F-4 | Breadcrumb context strip | New |

This is purely additive: it replaces the depth-clamp UI with infinite-depth support, but the data model (conversation tree) is unchanged. F-1 must be aware that the fragment-window contract has to support fragmenting *along the depth axis* (load the subthread under a chosen blip without loading sibling subthreads).

## H. Compose / edit mode toolbar (StageThree)

When a blip enters edit mode (or when the inline reply composer is active), the formatting toolbar replaces the wave nav row at the top of the wave panel.

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| H.1 | Bold (B) | (toolbar) | F-3 | Floating selection toolbar attached to active range | Restyled |
| H.2 | Italic (I) | (toolbar) | F-3 | same | Restyled |
| H.3 | Underline (U) | (toolbar) | F-3 | same | Restyled |
| H.4 | Strikethrough (S) | (toolbar) | F-3 | same | Restyled |
| H.5 | Superscript (X²) | (toolbar) | F-3 | same | Restyled |
| H.6 | Subscript (X₂) | (toolbar) | F-3 | same | Restyled |
| H.7 | Font family dropdown (A▾) | (toolbar) | F-3 | overflow menu within toolbar | Restyled |
| H.8 | Font size dropdown (T▾) | (toolbar) | F-3 | overflow menu within toolbar | Restyled |
| H.9 | Heading dropdown (H▾) | (toolbar) | F-3 | overflow menu within toolbar | Restyled |
| H.10 | Text color (A) | (toolbar) | F-3 | colorpicker popover | Restyled |
| H.11 | Highlight color (▢) | (toolbar) | F-3 | colorpicker popover | Restyled |
| H.12 | Align left / center / right | (toolbar) | F-3 | grouped segmented control | Restyled |
| H.13 | Bulleted list | (toolbar) | F-3 | list segmented control | Restyled |
| H.14 | Numbered list | (toolbar) | F-3 | list segmented control | Restyled |
| H.15 | Indent decrease / increase | (toolbar) | F-3 | indent segmented control | Restyled |
| H.16 | RTL toggle | (toolbar) | F-3 | format menu | Restyled |
| H.17 | Insert link | (toolbar) | F-3 | link affordance opens wavy modal | Restyled |
| H.18 | Remove link | (toolbar) | F-3 | link affordance | Restyled |
| H.19 | Attachment (paperclip) | (toolbar) | F-3 | drag-drop friendly button | Restyled |
| H.20 | Insert task / checkbox | (toolbar) | F-3 | inline task affordance | Restyled |
| H.21 | Mention (`@`) trigger | (typed in body) | F-3 | suggestion popover, violet accent | Restyled |
| H.22 | "Shift+Enter to finish, Esc to exit" hint | (footer) | F-3 | low-emphasis hint strip | Restyled |
| H.23 | Save indicator / draft state | (footer) | F-3 / F-4 | low-emphasis chip; ties to A.3 "All changes saved" | Restyled |
| H.24 | Compose plugin slot for compose extensions (poll, code-block, inline diagram) | n/a | F-3 / F-0 | `<slot name="compose-extension">` on the composer | Plugin-slot |

## I. Tags row (bottom of wave)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| I.1 | "Tags:" label | ref_214 | F-2 | Wave panel footer | Restyled |
| I.2 | Tag chips with × remove | (visible) | F-2 / F-3 | Tag chips in wave footer | Restyled |
| I.3 | "Add tag" button | ref_216, ref_219, ref_220 | F-3 | "+" pill that expands to textbox | Restyled |
| I.4 | "Add tags" textbox | ref_215 | F-3 | inline textbox with locale-aware suggestions | Restyled |
| I.5 | "Cancel tag entry" | ref_217 | F-3 | × beside textbox | Restyled |
| I.6 | "Show tags tray" / "Hide tags tray" toggle | ref_324, ref_325 | F-2 | mobile-friendly tray reveal | Restyled |

## J. Floating + accessory controls

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| J.1 | "Click here to reply" inline composer affordance at bottom of wave | ref_317 | F-3 | Bottom of conversation; expands inline composer | Restyled |
| J.2 | "Scroll to new messages" floating button | ref_319 | F-2 | Floating bottom-right pill, signal-cyan when unread present | Restyled |
| J.3 | "Hide wave controls" / "Show wave controls" | ref_321, ref_323 | F-2 | Visibility toggle for compact mode | Restyled |
| J.4 | Open / close navigation drawer | ref_25, ref_320 | F-2 | Mobile-friendly nav drawer | Restyled |
| J.5 | "Back to inbox" | ref_26 | F-2 | Header back affordance on mobile | Restyled |

## K. Version history / time slider

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| K.1 | Version history overlay (entered via E.10) | ref_412–414 | F-2 | Full-bleed overlay, dark wash | Restyled |
| K.2 | Time slider (range 0..N) | ref_408 | F-2 | Wavy slider with playhead, signal-cyan handle | Restyled |
| K.3 | "Show changes" toggle | ref_410, ref_411 | F-2 | Slider shelf | Restyled |
| K.4 | "Text only" toggle | ref_409 | F-2 | Slider shelf | Restyled |
| K.5 | "Restore" action | ref_412 | F-2 | Slider shelf, primary destructive — confirm dialog | Restyled |
| K.6 | "Exit" version history | ref_407 | F-2 | Overlay close | Restyled |

## L. User profile overlay (avatar click)

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| L.1 | Open user profile from blip avatar | (avatar click) | F-2 | Wavy modal | Restyled |
| L.2 | "Send Message" → start a 1:1 wave | ref_329 | F-3 | Profile modal | Restyled |
| L.3 | "Edit Profile" (own avatar) | ref_330 | F-0 | Profile modal | Restyled |
| L.4 | Close profile modal | ref_331 | F-0 | Modal close | Restyled |
| L.5 | "Previous" / "Next" participant | ref_332, ref_333 | F-2 | Profile modal participant nav | Restyled |

## M. Plugin / extensibility groundwork

| # | Affordance | Source ref | Owner slice | Wavy design home | Status |
| --- | --- | --- | --- | --- | --- |
| M.1 | Robot & Data API entry (A.10) | ref_45 | F-0 | User menu sheet, "Plugins / Integrations" group | Plugin-foundation |
| M.2 | Per-blip plugin slot (replaces legacy gadgets) | n/a | F-0 / F-2 | `<slot name="blip-extension">` per blip card | Plugin-slot |
| M.3 | Compose plugin slot | n/a | F-0 / F-3 | `<slot name="compose-extension">` on composer | Plugin-slot |
| M.4 | Right-rail plugin slot | n/a | F-0 / F-2 | `<slot name="rail-extension">` on right rail (assistant, tasks roll-up, integrations status) | Plugin-slot |
| M.5 | Toolbar plugin slot | n/a | F-0 / F-3 | `<slot name="toolbar-extension">` on edit toolbar | Plugin-slot |

## N. Coverage summary

- **Total enumerated affordances**: 145 (A:18, B:18, C:22, D:8, E:10, F:12, G:6, H:24, I:6, J:5, K:6, L:5, M:5)
- **Per-issue ownership** (excluding the F-0 design/plugin foundation, which underlies all):
  - F-2 read surface: 87 affordances (72 exclusive + 15 shared with F-3/F-4/F-0)
  - F-3 compose / edit / mentions / tasks / reactions / attachments / tags: 46 affordances (35 exclusive + 11 shared with F-2/F-4/F-0)
  - F-4 live read/unread + feature activation: cross-cuts E.2, B.17, A.3, A.4, A.5, H.23
- **New / additive (not in GWT today)**: G.1–G.6 depth-navigation pattern (6 affordances); F-2 takes on a new acceptance row **R-3.7** (depth-navigation drill-down).
- **Plugin slots**: 4 (M.2–M.5), all reserved by F-0; specific F-* issues mount the slot points but do not register plugins.

## O. Action items (this session)

1. ✅ Inventory committed (this file).
2. → Update F-2 (#1037) issue body to enumerate affordances under its ownership and to add a new acceptance row **R-3.7 Arbitrary-depth drill-down navigation** with G.1–G.6 as concrete affordances.
3. → Update F-3 (#1038) issue body to enumerate affordances under its ownership including the full edit-mode toolbar (H.1–H.24), tag affordances (I.1–I.6), and "Send Message" (L.2).
4. → Update F-2 (#1037) to reference C.* search-help and B.* saved-search affordances explicitly so reviewers cannot accept "practical search parity" without the help modal + saved-search rail.
5. → Update F-1 (#1036) data-path scope to confirm fragment-window contract supports fragmenting along the depth axis (G.1 prerequisite; see §G opening paragraph: "replaces the depth-clamp UI with infinite-depth support … load the subthread under a chosen blip without loading sibling subthreads").
6. → Update F-0 (#1035) to specify the plugin slot points as M.2–M.5 with the slot-context contracts described above.
7. → Generate Stitch screens for: Edit-mode toolbar in detail; Search-help modal + saved-search rail; Depth-navigation pattern.
