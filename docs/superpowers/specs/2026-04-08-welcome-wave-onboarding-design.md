# Welcome Wave Onboarding Design

**Date:** 2026-04-08  
**Branch:** welcome-wave-onboarding-20260408  
**Status:** Approved  
**Approach:** Guided field guide with collapsed inline detail blips

---

## Problem

New accounts currently receive a minimal one-blip welcome wave created by
[`WelcomeWaveCreator`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java).
It says almost nothing about what Wave is, what SupaWave adds, how to use core
features, or why the product is especially useful for complex communication
with AI agents.

This wastes a product-native onboarding surface. Wave is not just a place to
put text. It is a structured conversation/document medium with inline replies,
mentions, participants, search, public waves, user data wavelets, and robot
participants. The onboarding artifact should demonstrate those strengths rather
than describe them abstractly.

---

## Goal

Replace the current plain-text welcome wave with a structured, design-sensitive
field guide that:

1. explains what Wave is and why it is different
2. shows why Wave is unusually good for complex human + AI collaboration
3. teaches the first actions a new user should try
4. introduces public waves, search, mentions, pin, and archive
5. introduces the robot API and the SupaWave TypeScript example robot
   `gpt-bot-ts@supawave.ai`
6. explains the lineage from Google Wave to Apache Wave to SupaWave
7. links users to the canonical product and project references
8. uses inline reply threads as progressive disclosure, with advanced material
   hidden behind collapsed detail blips

The onboarding wave should remain readable even if collapse state is not
applied, but the intended default presentation is a concise main path with the
deeper details hidden initially.

---

## Verified Content Anchors

The authored content should rely on the following verified product/project
anchors:

- `https://supawave.ai/api-docs`
- `https://supawave.ai/public`
- `https://supawave.ai/contact`
- Grokipedia page for Google Wave:
  `https://grokipedia.com/page/Google_Wave`
- SupaWave changelog:
  `https://supawave.ai/changelog`
- incubator-wave GitHub mirror:
  `https://github.com/vega113/incubator-wave`
- TypeScript example robot repo:
  `https://github.com/vega113/gpt-bot-ts`

The copy should also include:

- the Firefly naming fact: the term "wave" comes from Firefly's term for an
  electronic communication
- issue-reporting contact:
  `vega@supawave.ai`
- the user-menu fallback:
  `Contact Us`

---

## Content Strategy

The welcome wave should behave like a hybrid between a quick start and a
reference guide:

- the main/root blip is short, hands-on, and human-first
- technical depth, history, and roadmap context live in inline reply threads
- the robot/API material is a separate section, not mixed into the top-level
  onboarding path
- the tone is a field guide, not glossy marketing copy

The voice should be welcoming but candid:

- tell users what is already strong
- tell users what is actively being modernized
- avoid pretending the system is finished or static
- explain why the product exists in terms of communication patterns, not just
  feature lists

The onboarding should be demonstrative where the medium allows it:

- links should be real link annotations, not just prose references
- mention guidance should show literal `@mention` usage
- inline detail threads should be actual inline reply threads, not merely text
  describing them
- public-wave guidance should point to the real `/public` directory

---

## User-Facing Structure

### Main Blip

The main blip should be authored as a readable sequence of short sections,
using line structure and link annotations rather than raw pasted URLs.

Recommended top-level flow:

1. **Title**
   `Welcome to SupaWave`
2. **What Wave is**
   A shared conversation + document where edits, replies, participants, and
   context stay together.
3. **Why Wave is different**
   Unlike email, chat, and docs, the conversation does not fragment into
   separate places.
4. **Why it works well with AI agents**
   Wave is especially good for long, branching, context-heavy collaboration
   because agents can operate inside the same persistent object as humans.
5. **Try this now**
   Create a wave, add participants, use search, and mention someone.
6. **Public waves, pin, and archive**
   Introduce discovery and inbox hygiene.
7. **Robots and Data API**
   Separate technical section anchored around `gpt-bot-ts@supawave.ai`.
8. **Active development**
   Modernization, roadmap direction, and how to report issues or follow
   changes.

Near the top, the main blip should explicitly tell users that advanced details
are tucked into collapsed inline blips and can be opened inline.

### Inline Detail Blips

The welcome wave should create inline reply threads anchored to specific
phrases or sentences in the main blip.

Required detail blips:

- **What “Wave” means**
  Firefly naming fact and why the metaphor matters
- **History**
  Google Wave -> Apache Wave -> SupaWave fork lineage
- **Robot/API detail**
  robot API, callback/data model, example TypeScript robot, and why robots fit
  naturally into Wave
- **Roadmap / modernization**
  active development, modernization, snapshots, tags, contacts, and future
  federation work
- **Using collapsed detail blips**
  tiny affordance explainer for users who have never seen inline threads before

These detail blips should be independently readable and should avoid duplicating
all of the main-path text.

---

## Content Requirements Mapping

The final authored welcome wave must cover all of these items:

- what Wave is
- the Firefly naming fact
- why Wave is unique
- why it is good for complex communication with AI agents
- robot API and example `gpt-bot-ts@supawave.ai`
- Google Wave -> Apache Wave -> SupaWave fork history
- links to Grokipedia, incubator-wave GitHub repo, and SupaWave changelog
- how to create a wave
- how to add participants
- how to use search
- public waves
- pin/archive
- mentions
- report issues via `vega@supawave.ai` or `Contact Us`
- active development / roadmap / modernization / snapshots / tags / contacts /
  future federation work

---

## Authoring Model

The feature should remain server-side and tied to the account-activation
lifecycle.

The existing call paths differ by deployment mode and the design must cover
both:

- [`UserRegistrationServlet`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java)
- [`EmailConfirmServlet`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java)
- [`WelcomeWaveCreator`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java)

Lifecycle requirement:

- when email confirmation is **disabled**, create the welcome wave immediately
  after account creation
- when email confirmation is **enabled**, create the welcome wave only after the
  account is successfully confirmed
- do not create duplicate welcome waves across those two modes

The implementation should keep the creation trigger tied to the moment an
account becomes usable, not merely the moment a row is first persisted.

The implementation should expand that seam into a structured authoring
pipeline:

1. create the new conversation wavelet
2. create the root blip
3. write the main path content in stable ordered sections
4. attach link annotations for product/project references
5. create inline reply threads for the advanced detail sections
6. seed collapsed thread state for those inline threads in the new user's UDW
7. submit the conversation wavelet deltas
8. submit the UDW deltas for collapse state

Submit-order requirement:

- the conversation wavelet must be authored first because the inline-thread ids
  do not exist until the conversation model creates them
- if conversation-wavelet creation fails, skip the UDW write entirely
- if the conversation write succeeds and the UDW write fails, keep the welcome
  wave and log the failure; the result is an expanded-but-usable field guide

Idempotency requirement:

- only one lifecycle path should invoke welcome-wave creation for a given
  account in a given deployment mode
- the confirmation-enabled path must not create a second welcome wave for an
  account that was already onboarded

The welcome-wave creation must remain non-blocking for registration:

- content/UDW failures are logged
- account creation still succeeds
- partial onboarding is preferable to registration failure

---

## Collapsed Inline Thread Behavior

Inline detail threads are a real Wave-native feature, but their collapsed or
expanded presentation is persisted in the user-data wavelet supplement, not in
the conversation wavelet itself.

That means the implementation must do two things:

1. create the inline reply threads in the conversation content
2. write `ThreadState.COLLAPSED` for those thread ids in the new user's UDW

The desired first-open experience is:

- root blip readable without visual overload
- advanced details present but hidden by default
- user can open them inline to explore deeper context

If writing collapse state fails, the conversation content should still be
created and remain understandable in expanded form.

---

## Links And References In The Authored Wave

The main path or detail blips should include annotated links to the full
mandatory reference set:

- `https://grokipedia.com/page/Google_Wave`
- `https://github.com/vega113/incubator-wave`
- `https://supawave.ai/changelog`
- `https://supawave.ai/api-docs`
- `https://supawave.ai/public`
- `https://supawave.ai/contact`
- `https://github.com/vega113/gpt-bot-ts`

Raw URLs should not be dumped into the copy when a short linked label would be
clearer.

---

## Implementation Shape

### Files

**Modify**

- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java)

**Create**

- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java`](wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java)
- [`wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java`](wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java)
- [`wave/config/changelog.d/2026-04-08-welcome-wave-onboarding.json`](wave/config/changelog.d/2026-04-08-welcome-wave-onboarding.json)

### Responsibilities

`WelcomeWaveCreator`

- orchestrates wave creation
- loads or creates the new user's UDW
- delegates authored copy generation and inline-thread creation
- persists collapsed thread state
- submits deltas

`WelcomeWaveContentBuilder`

- owns the authored copy
- owns section ordering
- owns link annotation helpers
- mutates the live conversation model supplied by the creator
- returns an authoring result that includes the created thread ids that should
  start collapsed and any important content ranges the creator may need

`WelcomeWaveCreatorTest`

- verifies structure and authoring behavior
- verifies thread creation and collapsed-state persistence
- guards against regressions back to a single plain-text blob

---

## Test Strategy

The test suite should verify behavior at the structure level rather than by
asserting one giant exact string.

The tests should prove:

1. a welcome wave is created for the new user
2. the root blip contains the expected top-level sections
3. each required content cluster is present:
   - what Wave is
   - why Wave is unique
   - why Wave is good for complex human + AI collaboration
   - create wave / add participants / search / public waves / pin / archive /
     mentions guidance
   - robot API plus `gpt-bot-ts@supawave.ai`
   - Firefly naming
   - Google Wave -> Apache Wave -> SupaWave lineage
   - issue-reporting contact plus `Contact Us`
   - active development / roadmap / modernization / snapshots / tags /
     contacts / future federation work
4. the mandatory links are present
5. inline reply threads are created for the advanced detail sections
6. the new user's UDW stores collapsed state for those thread ids
7. the confirmation-disabled registration path creates the welcome wave
8. the confirmation-enabled path creates the welcome wave on successful email
   confirmation
9. failures still do not escape welcome-wave creation
10. a UDW failure degrades to expanded content rather than no onboarding wave

Targeted verification should focus on:

- `WelcomeWaveCreatorTest`
- `UserRegistrationServletTest`
- `EmailConfirmServlet` coverage or a focused confirmation-flow test

---

## Non-Goals

This change does **not** include:

- localization of welcome-wave content
- a CMS or admin UI for editing onboarding copy
- changing the registration flow beyond the authored welcome-wave output
- broader search, mention, public-wave, or robot feature development
- changing how inline thread collapse UI works in the client

---

## Risks And Constraints

### Stable Anchor Placement

Inline reply threads are attached to document locations. The content builder
must create the main blip in a deterministic sequence so thread anchors are
attached to stable positions after the relevant text exists.

Anchor strategy requirement:

- do not derive anchors from ad hoc substring searches over mutable final copy
- instead, author the main blip as ordered blocks and capture exact anchor
  locations immediately after each target section or target phrase is inserted
- keep link annotation and inline-thread creation in a single authoring pass so
  later formatting changes do not silently shift offsets

### UDW Coupling

Collapsed state is per-user presentation state. It must be written to the
new user's UDW only, not the conversation wavelet.

### Content Honesty

The roadmap and modernization material should not imply that all modernization
work is complete. It should clearly separate current strengths from active work
such as snapshots, tags, contacts, and future federation work.

### Graceful Degradation

If collapse-state persistence fails, the authored wave should still work as an
expanded field guide.

### Mandatory Link Set

The authoritative mandatory link set for the authored wave is:

- `https://grokipedia.com/page/Google_Wave`
- `https://github.com/vega113/incubator-wave`
- `https://supawave.ai/changelog`
- `https://supawave.ai/api-docs`
- `https://supawave.ai/public`
- `https://supawave.ai/contact`
- `https://github.com/vega113/gpt-bot-ts`

---

## Acceptance Criteria

The design is complete when the implementation produces a welcome wave that:

- feels like a SupaWave-native artifact rather than a generic help text
- gives a new user an immediate first path through the product
- uses inline detail threads for advanced material
- starts those advanced threads collapsed for the recipient
- covers the full required content set without overloading the main blip
- remains safe and non-blocking during account registration
