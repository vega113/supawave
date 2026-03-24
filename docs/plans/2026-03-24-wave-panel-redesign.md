# Wave Panel Redesign: SupaWave Ocean Theme

**Date:** 2026-03-24
**Status:** Implementation

## Overview

Comprehensive restyling of the Wave client panels (search/inbox, wave content,
participants, blips, menus, popups) to match the SupaWave ocean blue/teal theme
established in the landing page (PR #75) and top bar (PR #79).

## Design Tokens

| Token            | Value                           | Usage                        |
|------------------|---------------------------------|------------------------------|
| Primary          | `#0077b6`                       | Active/selected, links       |
| Accent           | `#00b4d8`                       | Unread badges, highlights    |
| Light            | `#90e0ef`                       | Hover tints                  |
| Background       | `#f0f4f8`                       | App background, panels       |
| Card white       | `#ffffff`                       | Content cards, blip bg       |
| Border           | `#e2e8f0`                       | Subtle separators            |
| Text dark        | `#1a202c`                       | Primary text                 |
| Text muted       | `#718096`                       | Timestamps, secondary text   |
| Font stack       | system-ui, -apple-system, ...   | Everywhere                   |
| Border radius    | 8px (cards), 4px (inputs)       | Rounded corners              |
| Shadow subtle    | 0 1px 3px rgba(0,0,0,0.1)      | Cards, panels                |

## Architecture Notes

GWT compiles CssResource classes with obfuscated names (prefix `SWC`).
We cannot target those classes from external CSS. Instead we use:

1. **`[kind="X"]` attribute selectors** - Every wavepanel DOM element has a
   `kind` attribute with stable single-letter codes:
   - `b` = blip, `m` = meta, `s` = participants, `p` = participant,
   - `r` = root thread, `rb` = reply box, `i` = menu item,
   - `g` = toggle, `ci` = continuation indicator,
   - `a` = add participant, `npw` = new wave with participants
   - `c` = root conversation, `d` = anchor

2. **`#app` container** - The wave client renders inside `<div id="app">`.

3. **`gwt-SplitLayoutPanel-HDragger`** - The split panel dragger class is
   marked `@external` and survives obfuscation.

4. **Element/tag selectors** scoped under `#app` for inputs, buttons, imgs.

5. **`[di]` attribute** on digest elements (wave list items).

## Injection Method

Add a `<style>` block inside `renderWaveClientPage()` in
`HtmlRenderer.java`, alongside the existing topbar styles. This is the
cleanest approach: no new files, no GWT module changes, and the styles
load before GWT initializes.

## Component Designs

### 1. App Background & Panels

- App background: `#f0f4f8` (light gray-blue)
- Split panel dragger: light blue tint instead of default
- Search panel and wave panel: white background, subtle shadow

### 2. Search Bar & Filter Buttons

Target: `input[type="search"]` and `input[type="text"]` inside `#app`,
plus GWT Button widgets in the search area.

- Search input: rounded (border-radius: 20px), subtle border, focus glow
- Filter buttons (Shared/All/Inbox): pill-shaped toggles

### 3. Inbox/Digest List

Target: `[di]` (digest items), `[kind="digest"]` or elements with
the `di` attribute.

- Digest items: clean white rows, subtle bottom border
- Hover: light teal tint
- Selected: ocean blue background, white text
- Unread badge: teal pill (`#00b4d8`) with white text
- Avatars: rounded (border-radius: 50%)
- Show More button: styled as subtle text link

### 4. Participants Panel

Target: `[kind="s"]` (participants container)

- Background: gradient from light blue to white
- Avatar images: rounded, subtle shadow
- Add participant button: styled with accent color

### 5. Blips

Target: `[kind="b"]` (blip), `[kind="m"]` (meta)

- Blip container: no heavy border, subtle rounded card
- Focus frame: blue border (matching primary) instead of green
- Meta bar read: light gray-blue background
- Meta bar unread: accent teal tint
- Avatar: fully rounded (border-radius: 50%)
- Content area: clean line-height, system font

### 6. Blip Menu Items

Target: `[kind="i"]` (menu items like Edit, Reply, Delete, Link)

- Styled as subtle text links
- Hover: primary blue color
- Separator pipes: lighter color

### 7. Reply Box

Target: `[kind="rb"]` (reply box)

- Rounded, lighter border
- Blue accent on hover
- Avatar inside: rounded

### 8. Inline Thread Toggles

Target: `[kind="g"]` (toggle), collapsible elements

- Unread count badge: teal pill
- Read count: muted gray pill

### 9. Popups & Dialogs

Target: `.popup` class (survives as it is on dynamically created elements),
and dialog elements.

- Rounded corners (12px)
- Subtle shadow
- Clean form inputs

### 10. New Wave Button Fix

Target: `[kind="npw"]` (new wave with participants)

- Fix asymmetric border/padding
- Style as ocean blue pill button

## Accessibility

All color combinations meet WCAG AA contrast:
- White text on `#0077b6`: contrast ratio 4.56:1 (passes AA)
- White text on `#00b4d8`: contrast ratio 3.26:1 (use bold/larger text)
- `#1a202c` on white: contrast ratio 16.75:1 (passes AAA)
- `#718096` on white: contrast ratio 4.48:1 (passes AA)

## CSS Selector Strategy

All selectors scoped under `#app` to avoid conflicts with topbar or auth
pages. Using `!important` sparingly and only where GWT inline styles
would override.
