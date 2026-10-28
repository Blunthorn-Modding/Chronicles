# Outstanding work after the picker correction

28 October 2026. This rollup supersedes old "planned" and "review pending" wording in the historical backlog. The organizer confirms Event/flag functionality works; practical map setup and group testing are still to come. The dev64 correction removes duplicate picker navigation and centers text-only choices.

## Quiet Below delivery gates

1. **Handcrafted-map integration (M13 / QB-04, QB-10).** Bind the source city and Heart, starting/respawn locations, ship interaction and boarding Region. Configure chosen loot, books, ecology settings and optional saved Region states/events. The tools exist; the actual map configuration remains. No authored sculk routes or mandatory session calendar.
2. **Final content pass (QB-02, QB-05/06/07, QB-14).** Revisit the research journals together with character backgrounds/private lore, event text/sounds and discovery relationships. Settle what happened recently and what remains ambiguous. The researcher discovered the older city and experimented with the Forge/Altar; the researcher did not build the Ancient City. Current prose/defaults are provisional.
3. **Balance on that map (QB-08/09/12/13).** Tune spread rates/count amplification, vitality/stages, crop/healing benefits and contact costs, Forge availability/recipes, Altar sacrifices and Heart weakness/defence. Existing mechanics are implemented and individually tested; balance needs actual geography and players.
4. **Coherent group and operations review (QB-11 / M13).** Ordinary players and GMs should test discoveries, information boundaries, coexistence/containment/eradication and voluntary ship departure together. Check voice/audibility, late joining, reconnects and realistic long-running load/chunk behavior. Synthetic city-scale, interruption and recovery proofs already exist; they do not replace this playtest.

The organizer-requested three-hour normal vanilla-world soak completed with two native clients and a real Ancient City. Average 20.00 TPS, timing budgets and participant continuity passed. The original overall verdict is FAIL because a topology-availability counter reached 6.21% against its 5% budget; that metric also counts routine incremental extensions. Refine availability instrumentation and fix the completed-run client shutdown handshake before a conclusive rerun. See [results and limitations](PERFORMANCE_SOAK_RESULTS.md). This does not replace the handcrafted-map/group/voice gates.

## Deferred implementation, separate from those gates

- Generic entity/encounter-group authoring and event-driven spawning (the remaining CF-07 extension).
- Additional Event sources for role assignment and completed first entry. Player Roles themselves and role-issued book discovery already work.
- Maintenance for the new Event/source histories. They currently preserve evidence within fixed limits and refuse further work when full; automatic archival/compaction for repeated rearming is not implemented. The sculk journal's existing automatic maintenance is a separate, completed feature.
- Additional Altar concepts: emergency healing, equipment repair, cave sight and a harvest offering. Only the two lasting prayers are implemented; temporary/item sacrifices need separate transaction rules before coding.
- Dedicated Survival, Journey and hidden-faction engines. These are future Chronicle formats, outside the current Quiet Below completion focus. Voting is optional for hidden-faction games.

Polluted-water purification, recoverable sound traces and a Relic Shard use remain optional design candidates, not promised missing mechanics.

## Watch item

The old report of a selection outline sticking was not reproduced after the unified Region workflow, which the organizer subsequently confirmed works. It needs recurrence evidence before further repair; it is not an active blocker. The new picker defect has its own native forward/backward/search regression.
