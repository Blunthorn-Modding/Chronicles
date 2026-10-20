# Chronicle work tracking

Updated 19 October 2026. Stable QB IDs are repository work items, not claims that GitHub Issues or a GitHub Projects board have been created. Branches/commits reference these IDs. Technical milestone history stays in [TEST_PLAN](TEST_PLAN.md).

## Current checkpoint

Latest feedback rejects the accumulated menu after M8 testing. **The approved CF-10 native slice is deployed as dev.12 for operator usability review before ecology work.** [The redesign](CORE_UI_REDESIGN.md) contains the current-control audit, left/middle/right mockup, terminology, complete planned-library placement, state-sync diagnosis and staged migration. See [the implementation scope](CF10_IMPLEMENTATION.md); existing services and data remain compatible.

The operator authorizes implementation after designing [M6–M13](DEVELOPMENT_MILESTONES.md), continuing until a necessary product decision or hands-on review. These are engineering milestones, never a session/event schedule. The agent-built pilot remains a test fixture; M5's production session is not complete. The operator handcrafts the production map.

| ID | Work item | Status | Depends on | Done when |
| --- | --- | --- | --- | --- |
| QB-01 | Capture the existing technical prototype in Git | Committed: `63c2e84` | — | `chore/prototype-baseline` preserves the previously uncommitted source, tools and evidence. |
| QB-02 | Campaign premise, benefits/costs and emergent play | Revised draft for operator review | — | [THE_QUIET_BELOW](THE_QUIET_BELOW.md) reflects autonomous terrain-led growth, discovery at any time and Heart eradication; settle remaining ecology/lore details. No code inferred as canon. |
| QB-03 | Quiet Below bindings on shared authoring tools | Key-location direction accepted; specification needed | QB-02, CF-05 | Map source city/Heart, discovery locations and optional ancient stations onto Core IDs; no authored growth paths, per-field registration or separate campaign editor. |
| QB-04 | Bind Quiet Below's named sites in-world | Planned | QB-03, CF-06 | Operator selects/binds key sites in the existing world; geometry persists in Core without Java edits. No sculk painting or patch-graph setup. |
| QB-05 | Campaign event delivery and observations | M8 verified; hands-on review pending | QB-02, QB-04 | Site-specific, readable cues with clear audience, prerequisites, manual preview/fire and persistent history; remove prototype lore from the production setup. |
| QB-06 | Optional active-play cue timing | M8 verified; opt-in only; hands-on review pending | QB-05, CF-08 | A configured timer pauses correctly, survives restart and shares completion with manual fire. No scheduled first sound, session-specific reveal or default event outline. |
| QB-07 | Optional first-entry discovery observations | M8 verified; hands-on review pending | QB-04, QB-05, CF-08 | Configured discovery fires once for eligible participants, excludes author walks, handles simultaneous entry/restart and preserves information boundaries. |
| QB-08 | Autonomous sculk ecology, network power and interventions | Direction established; technical specification required | QB-02, QB-03 | Trial pinned 1.21.10 growth hooks/ownership, air/light feeding, actual cave-surface traversal, barriers/branch decline, power and work budgets. Automatically index source sculk and retain descendant journals. Track catalysts/sensors and extensive-stage shriekers with explicit Warden policy; no authored routes or per-step approval. |
| QB-09 | First crop-growth benefit/cost slice | Proposed; awaiting balance review | QB-08 | Derive reach from living growth, not a bound farm. Comparable planted plots show bonus crop ages/harvests; late discovery changes no unlock, and pause/severing stop the bonus. Local costs, no structural simulation or personal corruption. |
| QB-10 | Author the production map's campaign setup | Waiting for map and tools | QB-04–QB-09, QB-13; CF-07 if used | Configure key sites, optional chests/spawns and ecology/protection rules directly in the operator's world; no importing/rebuilding map or placing growth routes. |
| QB-11 | First coherent roleplay session | Planned | QB-10 | Human playtest finds the event chain understandable, disagreement voluntary, departure viable and GM controls usable without code changes. |
| QB-12 | Ancient station assets and mechanics | Design candidate | QB-02, QB-08, CF-06 | Review Altar/Forge roles and exports, powered forge recipe, states and reliable inventory handling. Ancient Focus is removed; no relay chain gates growth. Heart work is QB-13. |
| QB-13 | Weakened Relic Heart and complete eradication | Required ending; technical specification needed | QB-04, QB-08 | Readable, validated weakness/destruction; stop growth/services permanently and remove the entire owned network, including source and detached features. Bounded restart-safe cleanup handles unloaded chunks, player edits and conflicts; no false completion, regrowth or unrelated sculk deletion. Test destruction bypasses/concurrency and initial-state removal policy. |

Work follows the milestone dependencies and review gates. The latest instruction authorizes implementation; unresolved product choices block their dependent slice only, not independent Core work. Never infer a narrative sequence from these IDs.

## Framework formats

The QB items stay specific to Quiet Below. The [format guide](CAMPAIGN_FORMATS.md) records the wider product direction without expanding the current gameplay scope.

| ID | Work item | Status | Done when |
| --- | --- | --- | --- |
| CF-01 | Define the four requested campaign formats | Documented; naming/additions for review | Survival, Journey, Werewolf and Situation/Happening have distinct drivers and session/world models; Quiet Below is classified as Situation. Operation/Intrigue remain optional candidates. |
| CF-02 | Journey chapter/outcome contract | Future design | Specify 2–3 end states per chapter, next-world selection/variants, carry-over and restart-safe transitions before implementation. |
| CF-03 | Hidden-faction / Werewolf variant and private-state contract | Future design | Specify private membership/knowledge/actions and game-specific pacing, including Vampire SMP-style persistent play. Voting, phases, elimination and win checks are optional capabilities; choose only those the game needs. |
| CF-04 | Survival scenario and threat contract | Future design | Define a concrete threat, escalation/counterplay and ending rules before building a general director. |
| CF-05 | Shared in-world authoring specification | Direction accepted for now; implementation contract pending | [MAP_AUTHORING](MAP_AUTHORING.md) defines Core-owned two-corner regions, points/block anchors, chest/spawn bindings and campaign extension boundaries using the supplied Vampire SMP reference. Quiet Below setup requires key sites, not spread paths. |
| CF-06 | Core selection, region/location/anchor registry | Operator accepted M6/M7 refinements | Persistent outline and mod-registered chat choices confirmed in operator testing. |
| CF-07 | Core chest and spawn authoring | Typed loot/manual/automatic player policies accepted; entity groups pending | Operator rejects capture-first chest UX; [typed loot contract](M7_LOOT_TYPES.md) provides direct type binding, campaign defaults/server JSON, independent chances, session/timed full replacements and count recommendations. Manual safe player moves remain; [automatic player policies](M7_AUTOMATIC_SPAWNS.md) add independent opt-in first arrival/death respawn, durable once claims and voluntary leave. Entity groups remain pending. |
| CF-08 | Shared participation, active clock and opt-in conditions | M8 verified; hands-on review pending | Core supplies author exclusion, eligibility, persistent ACTIVE occupied time, entry/manual/condition cue delivery and completion/audience contracts. Quiet Below chooses observations without a mandatory schedule. |
| CF-09 | Proper Core authoring UI | Operator accepted; periodic refresh fix verified with M7 policies | [Core screen](CF09_AUTHORING_UI.md) replaces the primary chat workflow for typed chests, saved places, registered roles, manual spawn controls and sessions. Shows mode/phase and prerequisite remedies; reuses validated services with bounded private snapshots and stale-request rejection. Commands remain advanced fallback; probability editing remains JSON and custom IDs remain commands. Review practical screen flow before expanding authoring. |
| CF-10 | Coherent Core menu and command redesign | Native slice verified/deployed; operator review pending | [CORE_UI_REDESIGN](CORE_UI_REDESIGN.md): vertical Session/Players/Events and Advanced setup, contextual item/actions, definition preview before setup, explicit server editing status, current/future function inventory and compatible commands/migration before implementation. |

## Review decisions

CF-10, revised after 20 October review: separate Chests/Loot Tables with Add item, independent entry/count editing and removal; rename Starting position; command-selected region creation and single-position updates; Continue in world activates the draft/outline automatically; separate Points/Block sites views with distinct descriptions; optional mod-block placement registration with block-owned functionality; no narrative session preview/recovery; one event preview including private playback. Quiet Below requirements are distinct from future-format references; Werewolf means hidden-faction games, never compulsory votes/rounds. The operator approved the revised mockup and authorized implementation. Earlier screen-layout acceptance is superseded; service-level verification remains valid.

- [ ] Agree what changed shortly before the story and why growth now reaches the mine.
- [ ] Balance crop growth, local regeneration and Ancient Forge capability thresholds/costs against actual ecology, without session/reveal gates.
- [ ] Specify air/light feeding, substrates/protection, connection/vitality rules, catalysts/sensors and extensive-stage shriekers. Exposure feeding/no oxygen system and vanilla Warden behavior are confirmed.
- [ ] Specify Heart weakness/destruction and full-network removal, including automatically adopted source sculk, detached branches and restart/conflict handling.
- [ ] Agree how much truth about the phenomenon the GM knows and what stays ambiguous.
- [ ] Decide whether the marker/lens have a justified production role or stay technical props.
- [ ] Review Altar/Forge details; retain the accepted Core authoring direction. Ancient Focus and authored spread routes are removed; a mundane alternative and a world import pipeline are not requirements.
- [ ] Set timing/audiences after group size, session length and map geography are known.

## Branch and commit practice

- Preserve the tested prototype as `chore/prototype-baseline`; its historical milestones were not committed individually, so do not fabricate an earlier development history.
- Use `docs/quiet-below-campaign-design` for this design reset. Future scoped branches should reference a work item, for example `feat/qb-04-map-sites` or `fix/qb-07-discovery-repeat`.
- Commit coherent increments with the problem/result and work ID. Review staged paths/diff; exclude tools, caches, runtime worlds, account credentials and generated test output from Git.
- Push scoped branches to the configured origin when working on requested GitHub changes. Review before merging; do not force-push, rewrite shared history or merge `main` merely to make it look current.
- Keep work item status, behavior docs and validation evidence synchronized. Do not mark a design proposal as implemented or a technically passing test as a coherent campaign.
- This backlog is the current durable work list. If a GitHub Issues/Projects board is adopted, keep the same QB IDs and link the real issues; do not invent hosted issue numbers.
