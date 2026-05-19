# Issue 1280: Align reply and reaction badges in blip footer

## Root cause
J2CL renders the same-level reply continuation trigger in a zero-height row after `<wavy-blip-card>`. Reactions are slotted inside that card, so once a reaction row exists the trigger's fixed negative offset is measured from after the reactions and visually lands on top of emoji badges. GWT already uses a hover-revealed continuation overlay, but its reaction row still has extra footer margin/button height that makes reactions read as a separate lower row.

## Implementation plan
1. Move the J2CL continuation row inside `<wavy-blip-card>` immediately after the blip body and before the reactions slot, preserving the existing event and zero-height behavior.
2. Compact J2CL reaction controls to the same footer height as the reply overlay, without changing reaction event names or payloads.
3. Compact the GWT reaction row/button CSS to match the same footer level while leaving `ContinuationIndicator.css` behavior intact.
4. Add focused Lit regression coverage for continuation-before-reactions and compact reaction controls. Add/update source-level Java/CSS coverage where practical.
5. Run focused J2CL and Java tests, validate changelog, push PR, and monitor until merged.

## Acceptance checks
- Reply continuation is anchored to the blip body bottom border, not to the post-reaction footer.
- Reply continuation does not overlap reaction buttons.
- Reply continuation does not reserve horizontal space next to reactions.
- Reaction controls use compact footer geometry in both J2CL and GWT.
