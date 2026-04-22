# J2CL/Lit Implementation Workflow

Status: Proposed
Updated: 2026-04-22
Owner: Project Maintainers
Review cadence: on-change
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Task issue: [#958](https://github.com/vega113/supawave/issues/958)
Related: [#954](https://github.com/vega113/supawave/issues/954), merged PR [#956](https://github.com/vega113/supawave/pull/956)

## 1. Decision Summary

Recommendation:

- keep **code and behavior specs** as the source of truth for parity
- use **Google Stitch** as the primary design/mock/prototype accelerator
- use **image-generation models** only for visual direction, moodboards, and style experiments
- implement approved slices in **Lit components on top of J2CL-owned runtime/state**

This means the workflow is:

1. extract the current GWT behavior and acceptance rules
2. write the slice spec and parity checklist
3. explore the visual/system direction
4. use Stitch to turn that direction into structured screens, variants, flows, and design rules
5. implement the approved slice in Lit
6. verify behavior against the current GWT baseline in the browser

The important consequence is:

**Neither Stitch nor image generation should be treated as the source of truth for behavior parity.**

## 2. What Problem This Solves

The merged J2CL parity architecture memo answered the framework question: J2CL for runtime/state/transport, Lit for UI composition, Java-rendered first paint, and viewport-scoped loading for large waves.

What was still missing is the implementation workflow question:

- how should the team actually design the new UI?
- where should Stitch fit?
- where should image generation fit?
- how can the UI become more modern and more "Wavy" without silently dropping StageOne/StageTwo/StageThree-era behavior?

This doc answers that execution question.

## 3. Source Of Truth

The source of truth for any parity slice must be, in order:

1. **current repo behavior**
   - current GWT code
   - current local browser behavior
   - current J2CL seams already merged
2. **slice spec / acceptance matrix**
   - what the slice must preserve
   - what is allowed to change visually
   - what is intentionally out of scope
3. **approved design system + mock/prototype artifacts**
   - Stitch screens
   - design tokens
   - design notes / Stitch `DESIGN.md` exports / related design exports

That order matters.

If a mock conflicts with the spec or with the observed current behavior, the mock is wrong.

If an image looks good but breaks parity, the image is wrong.

If a generated screen omits an interaction, the interaction is still required unless the slice spec explicitly removed it.

## 4. Why Stitch Should Be The Main Design Accelerator

Google positions Stitch as an AI-native design canvas for creating and iterating on high-fidelity UI from natural language, with infinite-canvas ideation, design-system exchange via `DESIGN.md`, interactive flow preview, and MCP/SDK connectivity into other tools ([Google Stitch announcement](https://blog.google/innovation-and-ai/models-and-research/google-labs/stitch-ai-ui-design/)).

That positioning matches what this repo needs better than image-first workflows do.

In the local toolchain available here, Stitch can already:

- create projects
- generate screens from text
- edit existing screens
- generate variants
- create and apply design systems

Those capabilities make it a good fit for:

- shell/layout exploration
- component/system variants
- responsive structure
- screen-to-screen flow exploration
- design-system capture and reuse

They do **not** make it the right source of truth for:

- Wave behavioral parity
- route/state semantics
- fragment-loading logic
- StageTwo reconnect/live-update rules
- StageThree compose/edit behavior

So the right role for Stitch is:

**primary visual and prototype accelerator, secondary to the slice spec.**

## 5. Why Image Generation Should Be Secondary

OpenAI’s current image-generation tooling is strong for generation and editing, including iterative edits and high-fidelity preservation when editing existing images ([OpenAI image generation guide](https://platform.openai.com/docs/guides/images/image-generation)).

But the same official guide also calls out limitations that matter a lot for UI work:

- precise text rendering can still be unreliable
- recurring visual consistency can drift
- composition control in layout-sensitive outputs can still be imperfect

Those are acceptable weaknesses for:

- moodboards
- visual language exploration
- icon/background/art direction
- “what should this product feel like?” iterations

They are bad weaknesses for:

- exact screen specs
- responsive layout decisions
- component spacing/token rules
- stateful multi-screen parity work
- any UI where tiny placement or consistency drift matters

So the right role for image generation is:

**visual direction only, not structural UI specification.**

## 6. Recommended Tool Roles

### 6.1 Code / Spec

Use for:

- behavior inventory
- parity checklist
- route/state semantics
- loading/error/reconnect requirements
- feature flags
- acceptance criteria

Do not replace with generated visuals.

### 6.2 Stitch

Use for:

- layout exploration
- shell modernization
- component families and variants
- responsive breakpoints and screen composition
- design-system capture
- prototype flow review with stakeholders

Do not use it as the final authority on behavior.

### 6.3 Image Generation

Use for:

- moodboards
- color/material references
- atmosphere and “Wavy” style exploration
- one-off art direction references
- iconography/background treatment experiments

Do not use it as the final authority on screens, spacing, or interactions.

## 7. The Recommended Workflow

### Step 0: Extract The Current Behavior

Before any visual work starts, extract a slice-level parity inventory from the current product:

- what the user can do now
- what states exist
- what loading/error/reconnect behavior exists
- what keyboard/focus/toolbar behavior exists
- what route/history behavior exists
- what server/client seams are active

Artifacts:

- short behavior checklist
- browser notes
- file-path references to the current implementation

This is the stage where the team decides what must stay identical, what can be visually modernized, and what is intentionally deferred.

### Step 1: Write The Slice Spec

For each future J2CL/Lit slice, write:

- user-facing goal
- in-scope behavior
- out-of-scope behavior
- parity requirements
- allowed visual modernization
- verification commands and browser proof expectations

This spec is what keeps generated visuals from rewriting product behavior by accident.

### Step 2: Define The Visual Direction

Before detailed screens, pick the visual direction:

- typography
- palette
- density
- radius/shape language
- icon style
- motion personality
- what “Wavy” means for this slice

This is the best place to use image generation:

- generate 2-4 moodboards or visual directions
- explore materials, gradients, atmosphere, icon feel, illustration/background treatment
- reject anything that is pretty but structurally wrong for a Wave app

Deliverable:

- one approved visual direction

### Step 3: Use Stitch For Structured UI Exploration

Once the direction is chosen, move into Stitch.

Use Stitch to generate:

- shell concepts
- search/read/compose layouts
- toolbar and panel variants
- mobile/desktop structure
- flow-specific next screens
- design-system definitions that can be exported/reused

Prompt Stitch with:

- the slice spec
- parity constraints
- screenshots or descriptions of the current GWT surface
- the approved visual direction
- explicit statements about what behavior must remain visible/reachable

The most important rule:

**ask Stitch to modernize presentation, not to invent product behavior.**

Good prompt shape:

- “Keep the current inbox/search/open-wave flow and selected-wave + compose affordances, but modernize the shell and spacing”
- “Preserve the reply/thread navigation affordances and state visibility, but update the chrome and component language”

Bad prompt shape:

- “Redesign this to feel modern”

That kind of prompt invites accidental behavior loss.

### Step 4: Build The Implementation Packet

Before Lit coding starts, freeze an implementation packet:

- slice spec
- parity checklist
- approved Stitch screens / variants
- design tokens / design-system rules
- notes on what changed visually and what did not change behaviorally
- component inventory
- state/event notes for the slice

This packet is what the Lit implementation should follow.

The coder should not be reverse-engineering product requirements from a screenshot.

### Step 5: Implement In Lit On Top Of J2CL

Implementation rule:

- **J2CL owns state and behavior**
- **Lit owns composition and rendering**

That means:

- route/history logic stays out of Lit
- transport/reconnect stays out of Lit
- fragment-window logic stays out of Lit
- parity-critical state machines stay out of Lit

Lit components receive state and events through narrow contracts and render the UI.

### Step 6: Browser Parity Verification

Every slice must then prove:

- the new Lit surface matches the intended behavior
- the old GWT baseline is still met for that slice
- the modernization did not delete or bury required interactions

Verification should compare:

- current GWT behavior
- new J2CL/Lit behavior
- approved slice spec

Not:

- current implementation vs the prettiest mock

## 8. How To Modernize Without Breaking Parity

The safest rule is:

**visual modernization and behavioral change must be separated unless the slice spec explicitly combines them.**

Until a slice is explicitly promoted through the existing J2CL/GWT coexistence controls, it should remain staged behind the current bootstrap/feature-flag path so the legacy GWT surface stays available as the rollback baseline.

### Safe To Modernize Early

- typography
- spacing
- elevation/surfaces
- color and material system
- shell chrome
- card styling
- button treatment
- icons
- animation polish

### Should Stay Stable Until Explicitly Reworked

- route structure
- selected-wave semantics
- reply/thread navigation behavior
- loading/error/reconnect states
- keyboard focus behavior
- toolbar affordance reachability
- fragment-loading rules
- compose/write semantics

This lets the UI become more distinct and more “Wavy” while keeping the functional contract intact.

## 9. Suggested Acceptance Gates For Future Slices

Every J2CL/Lit slice should have these gates:

1. **Behavior inventory exists**
2. **Slice spec exists**
3. **Approved visual direction exists**
4. **Approved Stitch screens/variants exist**
5. **Implementation packet exists**
6. **Lit implementation exists**
7. **Browser parity verification passes**
8. **Keyboard/focus/accessibility parity checks for the slice pass**

If any of those is missing, the slice is not ready.

## 10. Bottom Line

If the question is “Should we use Stitch or image generation?” the answer is:

- **Use Stitch as the main structured design tool**
- **Use image generation only for visual direction**
- **Use code + specs as the behavioral authority**

So the real workflow is not:

“generate a pretty mock and implement it”

It is:

“extract the current behavior, define the slice, choose the visual direction, let Stitch explore structured UI options, then implement the approved packet in Lit and verify parity in the browser.”

That is the highest-signal path to a J2CL/Lit UI that is both modern and trustworthy.
