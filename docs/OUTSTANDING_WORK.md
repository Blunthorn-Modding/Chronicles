# Outstanding work

**4 November initial alpha:** both focused native default-Event follow-ups now pass. The checks exposed and fixed default renaming generating a replacement ID; supplied template IDs are now retained. Initial MIT alpha release files, notices, project descriptions and upload settings are prepared. The owner chose manual Modrinth project creation/upload, so publication remains an owner action rather than a claimed completed upload. All review servers and the approved old generated worlds/backups were removed; see [storage policy](WORKSPACE_STORAGE.md).

**4 November dev69:** removed the obsolete Readiness Check and Mine Whisper actions from normal Quiet Below startup. They were old prototypes, separate from the nine authored Events. Regression equivalents use test-only IDs and strictly isolated fixtures. Saved history is retained. Main Quiet Below mechanics have no newly identified implementation gap; the remaining delivery work is below.

**4 November dev68:** the organizer accepted the default content and audible sounds. Default Events/flags now load automatically; incomplete map-dependent entries show `!`. Prepared worlds preserve their Event bindings and progress. Reusable content/settings remain in the mod/server configuration; no configuration relocation is part of this change. See [automatic defaults](AUTOMATIC_SCENARIO_DEFAULTS.md).

**4 November dev67:** the final Word content is imported as six roles, nine books, nine disabled Event templates and four editable loot tables. Automatic quiet-period history archival and bundled custom audio are implemented; first-player/recurring cues and relative completion timers are verified. Native editor, custom authoring and copied-map/export checks pass. See [current guide](FINAL_DEFAULTS_GUIDE.md) and [results](FINAL_DEFAULTS_RESULTS.md). Human audio/content review and the map/group gates remain.

3 November: the organizer **passed the dev66 combined review**, confirming recovery and encounter spawning, the Altar, custom authoring, export and import. The export refusal wording is corrected to distinguish paused/stopped play from enabled Editing. Manual scenario switching through server/configuration setup is accepted. See [results](DEV66_FEEDBACK_RESULTS.md) and [scenario workflow](SCENARIO_CONTENT_EXPORT.md). The map/content/group gates below remain separate.

The library tools and main Quiet Below mechanics are implemented and reviewed. Role definitions and map destinations travel in the world; reusable content can be exported separately. See the latest [test evidence](TEST_PLAN.md). The delivery gates below still require the actual map, final content and players.

This rollup supersedes old "planned" and "review pending" wording in the historical backlog. The organizer confirmed the Event/flag functionality and dev64 picker correction; practical map setup and group testing are still to come.

## Quiet Below delivery gates

1. **Handcrafted-map integration (M13 / QB-04, QB-10).** Bind the source city and Heart, starting/respawn locations, ship interaction and boarding Region. Configure chosen loot, books, ecology settings and optional saved Region states/events. The tools exist; the actual map configuration remains. No authored sculk routes or mandatory session calendar.
2. **Content review on the map (QB-02, QB-05/06/07, QB-14).** The organizer's final journals, roles/private lore and Event text/sounds are now packaged. Review discovery placement, sound volume/feel and the editable loot percentages in the handcrafted map. The researcher discovered the older city and experimented with the Forge/Altar; the researcher did not build the Ancient City.
3. **Balance on that map (QB-08/09/12/13).** Tune spread rates/count amplification, vitality/stages, crop/healing benefits and contact costs, Forge availability/recipes, Altar sacrifices and Heart weakness/defence. Existing mechanics are implemented and individually tested; balance needs actual geography and players.
4. **Coherent group and operations review (QB-11 / M13).** Ordinary players and GMs should test discoveries, information boundaries, coexistence/containment/eradication and voluntary ship departure together. Check voice/audibility, late joining, reconnects and realistic long-running load/chunk behavior. Synthetic city-scale, interruption and recovery proofs already exist; they do not replace this playtest.

The corrected 1 November three-hour measurement passed all original runtime limits: 20.00 TPS, 0.817% unexpected unavailability, advancing growth and both native completion acknowledgements. Its final overall result remains FAIL because maintenance exceeded the former 90-second shutdown limit. **That reproduced operations fault is now fixed and separately verified:** the retained 95,927-cell journal drained before normal world saving in about eight seconds, and a fresh reopen passed. Both normal and pre-stop profiler recordings are readable; original evidence and review artifacts remain unchanged. The supervisor now has a bounded 300-second saving stop, progress/timeout diagnostics and consistent final verdict fields. See [focused repair results](MAINTENANCE_SHUTDOWN_RESULTS.md), [corrected rerun results](SOAK_RERUN_RESULTS.md) and [original results](PERFORMANCE_SOAK_RESULTS.md). No further three-hour run was needed for this focused repair; handcrafted-map/group/voice gates remain.

## Deferred implementation, separate from those gates

- **Modrinth upload by the owner:** preparation is complete for `0.1.0-alpha.1`; create the two projects and upload the JARs using [the prepared guide](releases/0.1.0-alpha.1/PUBLISHING.md). The alpha explicitly discloses the remaining map/group testing. No project or version has been uploaded by the agent.
- **M18 / CF24:** implementation and the organizer's initial audible review are complete. Map/group audibility remains part of the eventual playtest. New audio remains a mod/pack update; no runtime uploader or server resource pack.
- Automatic quiet-period Event/source history archival is implemented. Its default waits 60 consecutive non-active seconds with zero online players and at least 300 seconds between attempts. Manual maintenance remains available; both preserve exact archives and refuse unresolved operations. The sculk journal's maintenance remains separate.
- Dedicated Survival, Journey and hidden-faction engines. These are future Chronicle formats, outside the current Quiet Below completion focus. Voting is optional for hidden-faction games.

Polluted-water purification, recoverable sound traces and a Relic Shard use remain optional design candidates, not promised missing mechanics.

## Watch item

The old report of a selection outline sticking was not reproduced after the unified Region workflow, which the organizer subsequently confirmed works. It needs recurrence evidence before further repair; it is not an active blocker. The new picker defect has its own native forward/backward/search regression.
