# Mobile-Responsive Wave UI Design

**Date:** 2026-03-24
**Status:** Implementing

## Current State Analysis

### Problems
1. **No viewport meta tag** in `renderWaveClientPage()` -- mobile browsers render at desktop width and zoom out
2. **Fixed 400px left panel** via GWT `SplitLayoutPanel` with `<g:west size="400">` -- too wide for mobile
3. **Absolute-positioned `#app` container** (`top:41px; right:0; bottom:0; left:0`) -- no flexibility
4. **GWT inline styles** -- `DockLayoutPanel` and `SplitLayoutPanel` inject `position:absolute`, `left`, `width` as inline styles on child elements
5. **No CSS media queries** in the wave client page (only auth pages have a `@media (max-width:480px)` block)
6. **Small touch targets** -- menu options, toolbar icons, and reply box all lack touch-friendly sizing
7. **Topbar overflow** -- long usernames + domain + status indicators overflow on narrow screens

### Layout Architecture
- **Server-side:** `HtmlRenderer.renderWaveClientPage()` emits `<head>` with inline CSS + `<div id="app">` container
- **Client-side:** GWT `WebClient.ui.xml` builds a `DockLayoutPanel > SplitLayoutPanel` tree
  - `SplitLayoutPanel` puts the search panel (400px) on the west, wave frame in center
  - GWT injects inline `style="position:absolute; left:0; top:0; width:400px; ..."` on child elements
  - These inline styles require `!important` overrides in media queries

### Key DOM Structure (post-GWT render)
```
<div class="topbar">...</div>
<div id="app" style="position:absolute; top:41px; ...">
  <div style="position:absolute; ...">              <!-- DockLayoutPanel -->
    <div style="position:absolute; left:0; width:400px; ...">  <!-- west: search panel -->
      <div class="searchPanel">...</div>
    </div>
    <div style="position:absolute; left:410px; ...">            <!-- center: wave frame -->
      <div class="wavePanel">...</div>
    </div>
  </div>
</div>
```

## Responsive Breakpoints

| Breakpoint | Target | Layout |
|-----------|--------|--------|
| >= 1024px | Desktop | Two-panel, current layout unchanged |
| 768-1023px | Tablet | Two-panel, narrower search (300px) |
| < 768px | Mobile | Single-column, slide-in panel |

## Component Changes

### 1. Viewport Meta Tag
Add to `<head>` in `renderWaveClientPage()`:
```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
```

### 2. Top Bar (mobile)
- Hide logo text, show only icon
- Collapse status indicators into icons
- Add hamburger button (hidden on desktop)
- Add back button (hidden by default, shown when wave is open)
- User menu: show only avatar initial, not full address

### 3. Left Panel (Search/Inbox)
- On mobile: fixed overlay, off-screen left by default
- Slide in via CSS transform when hamburger is tapped
- Semi-transparent backdrop overlay to close
- Takes 85% viewport width when open

### 4. Wave Content (Right Panel)
- On mobile: takes full viewport width (override GWT's `left` positioning)
- Blip meta line wraps if needed
- Reply box full-width with larger tap target

### 5. Editor Toolbar
- Wrap toolbar buttons on mobile
- Increase button tap area to 44px minimum

### 6. Blip Actions
- Menu options get 44px min-height for touch
- Reduce blip padding from 4.5em left indent to 3em on mobile

## CSS Media Query Plan

All responsive CSS goes into the inline `<style>` block in `renderWaveClientPage()`.
This is necessary because GWT's CssResource obfuscates class names, but the outer
container structure uses known IDs (`#app`) and GWT's generated class patterns
(e.g., `gwt-SplitLayoutPanel-HDragger`).

The approach uses `!important` on mobile overrides to beat GWT's inline styles.

### Hamburger + Back Button JavaScript
Minimal vanilla JS in the page `<script>` block:
- Toggle `.mobile-panel-open` class on `<body>`
- Listen for GWT's hash-change to auto-close panel when wave is selected
- Back button removes the hash to return to inbox view

## Touch Interaction Patterns

- **44px minimum** touch targets for all interactive elements
- **300ms delay elimination** via `touch-action: manipulation`
- **Smooth panel slide** via CSS `transform: translateX()` with `transition`
- **Backdrop dismiss** -- tapping outside the slide panel closes it
- **No hover-only interactions** -- all hover states also respond to active/focus

## Accessibility

- Hamburger button has `aria-label="Open navigation"`
- Back button has `aria-label="Back to inbox"`
- Panel overlay has `aria-hidden` when closed
- Focus trap inside open panel
- Backdrop has `role="button"` and `aria-label="Close navigation"`
