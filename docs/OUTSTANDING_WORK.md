# Outstanding work

2 November dev66 feedback repairs are verified and ready for combined organizer review: retained encounter retry/skip and bounded clear Region placement; one five-prayer Altar screen, truthful refusal damage and shared 15-active-minute cooldown; acknowledged scenario exports and empty custom profiles for isolated authoring servers. See [results](DEV66_FEEDBACK_RESULTS.md), [combined review](DEV66_FEEDBACK_REVIEW.md) and [scenario workflow](SCENARIO_CONTENT_EXPORT.md). The map/content/group gates below remain separate.

1 November 2026. The deferred-tools implementation now provides encounter groups and Event spawning, role-assignment/entry Event sources, explicit Event-history archival, four additional Altar prayers, and reusable scenario-content export/build input. Role definitions and map destinations travel in the world. See [combined review](DEFERRED_TOOLS_REVIEW.md) and the latest [test evidence](TEST_PLAN.md). Human UI/balance review and the delivery gates below remain; this does not declare the scenario production-ready.

This rollup supersedes old "planned" and "review pending" wording in the historical backlog. The organizer confirmed the Event/flag functionality and dev64 picker correction; practical map setup and group testing are still to come.

## Quiet Below delivery gates

1. **Handcrafted-map integration (M13 / QB-04, QB-10).** Bind the source city and Heart, starting/respawn locations, ship interaction and boarding Region. Configure chosen loot, books, ecology settings and optional saved Region states/events. The tools exist; the actual map configuration remains. No authored sculk routes or mandatory session calendar.
2. **Final content pass (QB-02, QB-05/06/07, QB-14).** Revisit the research journals together with character backgrounds/private lore, event text/sounds and discovery relationships. Settle what happened recently and what remains ambiguous. The researcher discovered the older city and experimented with the Forge/Altar; the researcher did not build the Ancient City. Current prose/defaults are provisional.
3. **Balance on that map (QB-08/09/12/13).** Tune spread rates/count amplification, vitality/stages, crop/healing benefits and contact costs, Forge availability/recipes, Altar sacrifices and Heart weakness/defence. Existing mechanics are implemented and individually tested; balance needs actual geography and players.
4. **Coherent group and operations review (QB-11 / M13).** Ordinary players and GMs should test discoveries, information boundaries, coexistence/containment/eradication and voluntary ship departure together. Check voice/audibility, late joining, reconnects and realistic long-running load/chunk behavior. Synthetic city-scale, interruption and recovery proofs already exist; they do not replace this playtest.

The corrected 1 November three-hour measurement passed all original runtime limits: 20.00 TPS, 0.817% unexpected unavailability, advancing growth and both native completion acknowledgements. Its final overall result remains FAIL because maintenance exceeded the former 90-second shutdown limit. **That reproduced operations fault is now fixed and separately verified:** the retained 95,927-cell journal drained before normal world saving in about eight seconds, and a fresh reopen passed. Both normal and pre-stop profiler recordings are readable; original evidence and review artifacts remain unchanged. The supervisor now has a bounded 300-second saving stop, progress/timeout diagnostics and consistent final verdict fields. See [focused repair results](MAINTENANCE_SHUTDOWN_RESULTS.md), [corrected rerun results](SOAK_RERUN_RESULTS.md) and [original results](PERFORMANCE_SOAK_RESULTS.md). No further three-hour run was needed for this focused repair; handcrafted-map/group/voice gates remain.

## Deferred implementation, separate from those gates

- **M18 / CF24 bundled custom event sounds (added 1 November).** [Queued plan](CF24_BUNDLED_EVENT_SOUNDS.md): ship organizer-supplied audio inside the campaign mod and matching client pack, register sound IDs and use the existing Event picker/private preview. New audio requires a mod/pack update; no runtime uploader or server resource pack. Implementation and audible review remain open; this is separate from the delivered deferred tools.
- Automatic quiet-period scheduling for Event/source history archival remains a follow-up. The confirmed non-active Game setup action and `/chronicle maintenance events` now reclaim bounded live capacity while retaining immutable exact-retry evidence. They refuse unresolved operations. Temporary offerings also archive terminal claims automatically at capacity; the sculk journal's existing automatic maintenance remains separate.
- Dedicated Survival, Journey and hidden-faction engines. These are future Chronicle formats, outside the current Quiet Below completion focus. Voting is optional for hidden-faction games.

Polluted-water purification, recoverable sound traces and a Relic Shard use remain optional design candidates, not promised missing mechanics.

## Watch item

The old report of a selection outline sticking was not reproduced after the unified Region workflow, which the organizer subsequently confirmed works. It needs recurrence evidence before further repair; it is not an active blocker. The new picker defect has its own native forward/backward/search regression.
