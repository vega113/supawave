# Compact Inline Blip Layout — Implementation Plan

**Issue:** [#918](https://github.com/vega113/supawave/issues/918)
**Branch:** `compact-inline-blips`
**Date:** 2026-04-19

## Problem

Inline blips at nesting depths 0–2 waste horizontal space via accumulated padding, margins, and avatar sizing. Each nesting level adds ~52px of indent (`.replies` margin 1em + padding 8px, `.meta` padding-left 3.75em minus `.chrome` pull-back -2em). After 3 levels, content is cramped. Depth ≥ 3 already uses slide-nav (PR #913), so this work targets only depths 0, 1, and 2.

## Approach

Feature-flagged CSS-only compaction. A root class `.compact-inline-blips` is added to `<body>` when the flag is active. All new CSS rules are scoped under that class combined with existing `[data-depth="N"]` attribute selectors emitted by `CollapsibleBuilder.java`.

### Key invariants
- Root blips (not inside any `[data-depth]` container) are untouched
- Depth ≥ 3 slide-nav CSS (`.slide-nav-active`, `.slide-nav-hidden`, breadcrumb) is not modified
- Flag off = zero visual change (all rules scoped under `.compact-inline-blips`)
- No Java builder changes needed — `data-depth` is already emitted

## CSS Cascading Strategy

Selectors like `.compact-inline-blips [data-depth="0"] .meta` match the meta of all blips inside a depth-0 container (including deeper nested blips). Rules are ordered depth 0 → 1 → 2; deeper rules override shallower ones via source order at equal specificity.

**Critical: depth 3+ reset rules.** Because `[data-depth]` is on the inline-thread container that wraps the entire nested subtree, depth-2 rules would cascade into depth-3+ content without explicit resets. Unlike `ThreadNavigation.css` (which defines explicit depth 3+ rules for border-left), this plan MUST add reset rules that restore depth-3+ blips to baseline values. This prevents compaction from leaking into slide-nav content.

## File Changes

### 1. `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`

**Add** `@external .compact-inline-blips;` declaration near the existing `@external` blocks.

**Add** depth-scoped compact rules at the end of the file:

#### Depth 0 (first inline nesting level)

| Property | Current | Compact | Savings |
|---|---|---|---|
| `.meta` padding-left | 3.75em | 2.75em | ~16px |
| `.avatar` width/height | 28px | 24px | 4px |
| `.avatar` margin-left | -3em | -2.25em | (tracks padding) |
| `.replies` margin-left | 1em | 0.5em | ~8px |
| `.replies` padding-left | 8px | 4px | 4px |
| `.privateReplies` margin-left | 1em | 0.5em | ~8px |
| `.privateReplies` padding-left | 8px | 4px | 4px |

#### Depth 1

| Property | Current | Compact | Savings |
|---|---|---|---|
| `.meta` padding-left | 3.75em | 2.25em | ~24px |
| `.avatar` width/height | 28px | 22px | 6px |
| `.avatar` margin-left | -3em | -1.75em | (tracks padding) |
| `.replies` margin-left | 1em | 0.25em | ~12px |
| `.replies` padding-left | 8px | 2px | 6px |
| `.privateReplies` margin-left | 1em | 0.25em | ~12px |
| `.privateReplies` padding-left | 8px | 2px | 6px |

#### Depth 2 (last inline level before slide-nav)

| Property | Current | Compact | Savings |
|---|---|---|---|
| `.meta` padding-left | 3.75em | 1.75em | ~32px |
| `.avatar` width/height | 28px | 20px | 8px |
| `.avatar` margin-left | -3em | -1.25em | (tracks padding) |
| `.replies` margin-left | 1em | 0 | ~16px |
| `.replies` padding-left | 8px | 0 | 8px |
| `.privateReplies` margin-left | 1em | 0 | ~16px |
| `.privateReplies` padding-left | 8px | 0 | 8px |

**Cumulative savings at depth 2:** ~28px (depth 0) + ~42px (depth 1) + ~56px (depth 2) ≈ **126px** of horizontal space recovered.

#### Depth 3+ reset (prevent cascade leak into slide-nav)

Explicit reset rules restore baseline values so depth-2 compaction does not leak:

```css
.compact-inline-blips [data-depth="3"] .meta,
.compact-inline-blips [data-depth="4"] .meta,
.compact-inline-blips [data-depth="5"] .meta {
  padding-left: 3.75em;
}
.compact-inline-blips [data-depth="3"] .avatar,
.compact-inline-blips [data-depth="4"] .avatar,
.compact-inline-blips [data-depth="5"] .avatar {
  width: 28px; height: 28px; margin-left: -3em;
}
.compact-inline-blips [data-depth="3"] .replies,
.compact-inline-blips [data-depth="3"] .privateReplies,
.compact-inline-blips [data-depth="4"] .replies,
.compact-inline-blips [data-depth="4"] .privateReplies,
.compact-inline-blips [data-depth="5"] .replies,
.compact-inline-blips [data-depth="5"] .privateReplies {
  margin-left: 1em; padding-left: 8px;
}
```

These selectors have the same specificity as depth 0–2 rules but appear later in source order, so they win for depth 3+ content.

### 2. `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Collapsible.css`

**Add** `@external .compact-inline-blips;` declaration.

**Add** compact `.chrome` margin rules to reduce vertical spacing at deeper depths:

```css
.compact-inline-blips [data-depth="1"] .chrome {
  margin: 0.75em 0.5em 0.75em -2em;
}
.compact-inline-blips [data-depth="2"] .chrome {
  margin: 0.5em 0.25em 0.5em -2em;
}
```

Note: negative left margin (`-2em`) is preserved — it pulls the chrome back into the parent's margin, which is essential for visual nesting. Only top/bottom/right margins are reduced.

### 3. `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`

**Add** flag registration in the static initializer, after the existing flags (~line 43):

```java
defaults.add(new FeatureFlag("compact-inline-blips", "Compact inline blip layout at nesting depth", false, Collections.emptyMap()));
```

### 4. `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

**Add** feature-flag CSS class toggle in the `execute()` method, near the existing `hasFeature("new-blip-indicator")` check (~line 685):

```java
if (Session.get().hasFeature("compact-inline-blips")) {
    Document.get().getBody().addClassName("compact-inline-blips");
}
```

Required import: `com.google.gwt.dom.client.Document` (likely already imported).

### 5. Mobile `@media` overrides

**Add** to Blip.css, after the desktop compact rules, a mobile media query block with more aggressive compaction:

```css
@media (max-width: 768px) {
  .compact-inline-blips [data-depth="0"] .meta { padding-left: 2.25em; }
  .compact-inline-blips [data-depth="0"] .avatar {
    width: 22px; height: 22px; margin-left: -1.75em;
  }
  .compact-inline-blips [data-depth="1"] .meta { padding-left: 1.75em; }
  .compact-inline-blips [data-depth="1"] .avatar {
    width: 20px; height: 20px; margin-left: -1.25em;
  }
  .compact-inline-blips [data-depth="2"] .meta { padding-left: 1.25em; }
  .compact-inline-blips [data-depth="2"] .avatar {
    width: 22px; height: 22px; margin-left: -0.85em;
  }
  .compact-inline-blips [data-depth="0"] .replies,
  .compact-inline-blips [data-depth="0"] .privateReplies {
    margin-left: 0.25em; padding-left: 2px;
  }
  .compact-inline-blips [data-depth="1"] .replies,
  .compact-inline-blips [data-depth="1"] .privateReplies {
    margin-left: 0; padding-left: 0;
  }
  .compact-inline-blips [data-depth="2"] .replies,
  .compact-inline-blips [data-depth="2"] .privateReplies {
    margin-left: 0; padding-left: 0;
  }
}
```

### 6. Feature flag & changelog

```bash
scripts/feature-flag.sh set compact-inline-blips \
  "Compact inline blip layout to reduce wasted space at nesting depth" \
  --allowed vega@supawave.ai
```

Changelog fragment: `wave/config/changelog.d/2026-04-19-compact-inline-blips.json`

## GWT CssResource considerations

- `.compact-inline-blips` is added to `<body>` as a literal string, not through CssResource. Must be declared `@external .compact-inline-blips;` in both Blip.css and Collapsible.css so GWT doesn't try to obfuscate it.
- `[data-depth="N"]` is an attribute selector — no obfuscation concern.
- `.meta`, `.avatar`, `.replies`, `.privateReplies` are CssResource classes in Blip.css — they're properly resolved when used in compound selectors within the same CssResource file.
- `.chrome` is a CssResource class in Collapsible.css — same applies there.

## What NOT to change

- `ThreadNavigation.css` — depth border colors, slide-nav transitions, breadcrumb, mobile touch targets
- `CollapsibleBuilder.java` — already emits `data-depth`, no changes needed
- Any depth ≥ 3 behavior — slide-nav is independent (explicit reset rules prevent cascade leak)
- Root blip layout — no `[data-depth]` ancestor, so no rules match
- `.blip` padding (focus frame) — must remain 3px
- `.contentContainer` padding — content readability preserved
- `ReplyBox.css` / `ContinuationIndicator.css` — not compacted in this pass, but QA must verify they look acceptable alongside compacted blips

## Verification steps

1. `sbt wave/compile` — Java compilation
2. `sbt compileGwt` — GWT CSS compilation (validates CssResource syntax, @external declarations)
3. Local server QA:
   - Start server (`sbt prepareServerConfig run`)
   - Register fresh user, create wave, add inline replies 4 levels deep
   - Verify depths 0–2 have visibly more content room
   - Verify root blips unchanged
   - Verify depth ≥ 3 triggers slide-nav as before and uses baseline spacing (no compaction leak)
   - Verify reply box and continuation indicators look acceptable alongside compacted blips
   - Screenshot evidence at each depth
4. Feature flag toggle: verify flag-off shows no visual change

## Risk assessment

**Low risk.** All changes are:
- Primarily CSS (only Java change is a body-class toggle + flag registration)
- Feature-flagged (disabled by default, allowed only for `vega@supawave.ai`)
- Scoped under `.compact-inline-blips` (zero impact when flag is off)
- Non-breaking (no changes to existing selectors or properties)
- Depth 3+ explicitly reset to baseline values (no cascade leak into slide-nav)
