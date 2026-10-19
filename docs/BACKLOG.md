# Chronicle work tracking

Updated 19 October 2026. Stable QB IDs are repository work items, not claims that GitHub Issues or a GitHub Projects board have been created. Branches/commits reference these IDs. Technical milestone history stays in [TEST_PLAN](TEST_PLAN.md).

## Current checkpoint

The operator tested the M1–M4 prototype and reports that its functions generally work. The narrative lacks a coherent purpose. **Gameplay expansion and production map generation are on hold while the campaign design is reviewed.** The agent-built pilot remains a test fixture; M5's production session is not complete.

| ID | Work item | Status | Depends on | Done when |
| --- | --- | --- | --- | --- |
| QB-01 | Capture the existing technical prototype in Git | Committed: `63c2e84` | — | `chore/prototype-baseline` preserves the previously uncommitted source, tools and evidence. |
| QB-02 | Campaign premise, benefits/costs and event outline | Draft for operator review | — | Review [THE_QUIET_BELOW](THE_QUIET_BELOW.md); settle cause, first benefit and intended mystery. No code inferred as canon. |
| QB-03 | Quiet Below bindings on shared authoring tools | Revised draft for review | QB-02, CF-05 | Map city/field/hazard/station meanings onto Core region/location/anchor IDs; no separate campaign editor or mandatory world-copy/import workflow. |
| QB-04 | Bind Quiet Below's named sites in-world | Planned | QB-03, CF-06 | Operator selects/binds the city, mine and effect sites in the existing world; geometry persists in Core without Java edits. |
| QB-05 | Campaign event delivery and observations | Planned | QB-02, QB-04 | Site-specific, readable cues with clear audience, prerequisites, manual preview/fire and persistent history; remove prototype lore from the production setup. |
| QB-06 | Active-play clock and scheduled first sound | Planned | QB-05 | E01 timer arms for a designated story session, pauses correctly, survives restart and shares completion with manual fire. |
| QB-07 | First-entry discovery triggers | Planned | QB-04, QB-05 | E03 fires once for eligible participants, excludes builder walks, handles simultaneous entry/restart and preserves information boundaries. |
| QB-08 | Connected spread, network power and interventions | Design required | QB-02, QB-03 | Define rooted connections, power thresholds, benefits/costs, retained-stage journals and selective undo; placed decorative sculk does not farm power. |
| QB-09 | First crop-growth benefit/cost slice | Proposed; awaiting review | QB-05, QB-08, CF-06 | Comparable planted plots show actual bonus crop ages/harvests; pause and severing stop bonus growth, with an explicit local cost and no structural simulation or personal corruption. |
| QB-10 | Author the production map's campaign setup | Waiting for map and tools | QB-04–QB-09, CF-07 | Configure sites, chests/spawns and behavior directly in the operator's world; validation/test mode does not require importing or rebuilding the map. |
| QB-11 | First coherent roleplay session | Planned | QB-10 | Human playtest finds the event chain understandable, disagreement voluntary, departure viable and GM controls usable without code changes. |
| QB-12 | Ancient station assets and mechanics | Design candidate | QB-02, QB-08, CF-06 | Review Focus/Altar/Forge roles and exports; specify a powered forge recipe, station states and reliable input/output handling before registration/crafting implementation. |

Work may be split further when the implementation specifications exist. A planned row is not permission to implement it immediately: the current request is design and repository discipline.

## Framework formats

The QB items stay specific to Quiet Below. The [format guide](CAMPAIGN_FORMATS.md) records the wider product direction without expanding the current gameplay scope.

| ID | Work item | Status | Done when |
| --- | --- | --- | --- |
| CF-01 | Define the four requested campaign formats | Documented; naming/additions for review | Survival, Journey, Werewolf and Situation/Happening have distinct drivers and session/world models; Quiet Below is classified as Situation. Operation/Intrigue remain optional candidates. |
| CF-02 | Journey chapter/outcome contract | Future design | Specify 2–3 end states per chapter, next-world selection/variants, carry-over and restart-safe transitions before implementation. |
| CF-03 | Werewolf variant and private-state contract | Future design | Select rules/pacing/roles, privacy boundaries, absence/elimination behavior and win conditions before building role/vote systems. |
| CF-04 | Survival scenario and threat contract | Future design | Define a concrete threat, escalation/counterplay and ending rules before building a general director. |
| CF-05 | Shared in-world authoring specification | Revised draft for operator review | [MAP_AUTHORING](MAP_AUTHORING.md) defines Core-owned two-corner regions, points/block anchors, chest/spawn bindings and campaign extension boundaries using the supplied Vampire SMP reference. |
| CF-06 | Core selection, region/location/anchor registry | Planned after specification review | In the existing world, builders select corners/targets/current position, save/list/inspect/edit definitions and restart without source edits or consuming story triggers. |
| CF-07 | Core chest profiles, spawn purposes and bindings | Planned after CF-05/CF-06 | Configure content templates and arrival/respawn points in-world; explicit activation and runtime actions preserve inventories, privacy and retry safety. |

## Review decisions

- [ ] Agree what changed shortly before the story and why growth now reaches the mine.
- [ ] Review crop growth → local regeneration → Ancient Forge as the capability progression, with concrete environmental costs at each stage.
- [ ] Agree how much truth about the phenomenon the GM knows and what stays ambiguous.
- [ ] Decide whether the marker/lens have a justified production role or stay technical props.
- [ ] Review Focus/Altar/Forge uses and the Core-owned selection/location/chest/spawn authoring workflow. A mundane alternative and a world import pipeline are not requirements.
- [ ] Set timing/audiences after group size, session length and map geography are known.

## Branch and commit practice

- Preserve the tested prototype as `chore/prototype-baseline`; its historical milestones were not committed individually, so do not fabricate an earlier development history.
- Use `docs/quiet-below-campaign-design` for this design reset. Future scoped branches should reference a work item, for example `feat/qb-04-map-sites` or `fix/qb-07-discovery-repeat`.
- Commit coherent increments with the problem/result and work ID. Review staged paths/diff; exclude tools, caches, runtime worlds, account credentials and generated test output from Git.
- Push scoped branches to the configured origin when working on requested GitHub changes. Review before merging; do not force-push, rewrite shared history or merge `main` merely to make it look current.
- Keep work item status, behavior docs and validation evidence synchronized. Do not mark a design proposal as implemented or a technically passing test as a coherent campaign.
- This backlog is the current durable work list. If a GitHub Issues/Projects board is adopted, keep the same QB IDs and link the real issues; do not invent hosted issue numbers.
