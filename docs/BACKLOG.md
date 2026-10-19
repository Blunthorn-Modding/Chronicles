# Chronicle work tracking

Updated 19 October 2026. Stable QB IDs are repository work items, not claims that GitHub Issues or a GitHub Projects board have been created. Branches/commits reference these IDs. Technical milestone history stays in [TEST_PLAN](TEST_PLAN.md).

## Current checkpoint

The operator tested the M1–M4 prototype and reports that its functions generally work. The narrative lacks a coherent purpose. **Gameplay expansion and production map generation are on hold while the campaign design is reviewed.** The agent-built pilot remains a test fixture; M5's production session is not complete.

| ID | Work item | Status | Depends on | Done when |
| --- | --- | --- | --- | --- |
| QB-01 | Capture the existing technical prototype in Git | Committed: `63c2e84` | — | `chore/prototype-baseline` preserves the previously uncommitted source, tools and evidence. |
| QB-02 | Campaign premise, benefits/costs and event outline | Draft for operator review | — | Review [THE_QUIET_BELOW](THE_QUIET_BELOW.md); settle cause, first benefit and intended mystery. No code inferred as canon. |
| QB-03 | Handcrafted-map authoring specification | Outline drafted; implementation blocked on review | QB-02 | Specify selection/configuration UX, persistence/activation, builder mode and validated world-copy import against [MAP_AUTHORING](MAP_AUTHORING.md). |
| QB-04 | Safe world-copy import and named sites | Planned | QB-03 | Operator can bind the intended map/city/mine and restart without source edits or altering original worlds. |
| QB-05 | Campaign event delivery and observations | Planned | QB-02, QB-04 | Site-specific, readable cues with clear audience, prerequisites, manual preview/fire and persistent history; remove prototype lore from the production setup. |
| QB-06 | Active-play clock and scheduled first sound | Planned | QB-05 | E01 timer arms for a designated story session, pauses correctly, survives restart and shares completion with manual fire. |
| QB-07 | First-entry discovery triggers | Planned | QB-04, QB-05 | E03 fires once for eligible participants, excludes builder walks, handles simultaneous entry/restart and preserves information boundaries. |
| QB-08 | Connected multi-patch spread and interventions | Design required | QB-02, QB-03 | Define dependencies, approvals, retained-stage journals and selective undo before implementing a prepared spread route and a meaningful containment result. |
| QB-09 | One benefit/cost vertical slice on a rehearsal map | Planned | QB-05, QB-08 | Players can observe the agreed benefit, identify a cost and try at least two credible responses without a forced faction or personal corruption. |
| QB-10 | Bind the operator's handcrafted map | Waiting for map and authoring tools | QB-04–QB-09 | Validate/rehearse on a copy, preserve map ownership/art direction, approve activation and take a recovery snapshot. |
| QB-11 | First coherent roleplay session | Planned | QB-10 | Human playtest finds the event chain understandable, disagreement voluntary, departure viable and GM controls usable without code changes. |

Work may be split further when the implementation specifications exist. A planned row is not permission to implement it immediately: the current request is design and repository discipline.

## Framework formats

The QB items stay specific to Quiet Below. The [format guide](CAMPAIGN_FORMATS.md) records the wider product direction without expanding the current gameplay scope.

| ID | Work item | Status | Done when |
| --- | --- | --- | --- |
| CF-01 | Define the four requested campaign formats | Documented; naming/additions for review | Survival, Journey, Werewolf and Situation/Happening have distinct drivers and session/world models; Quiet Below is classified as Situation. Operation/Intrigue remain optional candidates. |
| CF-02 | Journey chapter/outcome contract | Future design | Specify 2–3 end states per chapter, next-world selection/variants, carry-over and restart-safe transitions before implementation. |
| CF-03 | Werewolf variant and private-state contract | Future design | Select rules/pacing/roles, privacy boundaries, absence/elimination behavior and win conditions before building role/vote systems. |
| CF-04 | Survival scenario and threat contract | Future design | Define a concrete threat, escalation/counterplay and ending rules before building a general director. |

## Review decisions

- [ ] Agree what changed shortly before the story and why growth now reaches the mine.
- [ ] Select the first concrete benefit and the location/stake that makes it desirable.
- [ ] Agree how much truth about the phenomenon the GM knows and what stays ambiguous.
- [ ] Decide whether the marker/lens have a justified production role or stay technical props.
- [ ] Set timing/audiences after group size, session length and map geography are known.

## Branch and commit practice

- Preserve the tested prototype as `chore/prototype-baseline`; its historical milestones were not committed individually, so do not fabricate an earlier development history.
- Use `docs/quiet-below-campaign-design` for this design reset. Future scoped branches should reference a work item, for example `feat/qb-04-map-sites` or `fix/qb-07-discovery-repeat`.
- Commit coherent increments with the problem/result and work ID. Review staged paths/diff; exclude tools, caches, runtime worlds, account credentials and generated test output from Git.
- Push scoped branches to the configured origin when working on requested GitHub changes. Review before merging; do not force-push, rewrite shared history or merge `main` merely to make it look current.
- Keep work item status, behavior docs and validation evidence synchronized. Do not mark a design proposal as implemented or a technically passing test as a coherent campaign.
- This backlog is the current durable work list. If a GitHub Issues/Projects board is adopted, keep the same QB IDs and link the real issues; do not invent hosted issue numbers.
