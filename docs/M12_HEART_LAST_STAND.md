# Heart last stand


## Next implementation: stronger final burst

Operator feedback, 23 October, for implementation on 24 October: dev.46 works, but a single new vein is too weak. Replace the small final growth attempt with a visibly explosive burst covering **at least a 5x5 area** where suitable surfaces exist. Use sculk blocks or veins according to the available blocks and geometry, spreading outward from the Heart rather than placing disconnected decoration.

Acceptance criteria:

- An open fixture with sufficient eligible terrain visibly receives at least a 5x5 patch in the final attempt; a single seed or a count of unsuccessful attempts does not satisfy this.
- The burst adapts to actual surfaces and available substrate. Retain connected growth, special-block exclusions, journaled restoration, loaded-only access and bounded work. Do not manufacture floating support or cut through a sealed solid shell to meet the area target.
- Deliver the growth rapidly enough to read as one forceful last stand; it may use a short bounded queue rather than an unbounded synchronous write.
- Keep the existing sixty-second weakness rule, one direct Warden and persisted ten occupied ACTIVE-minute cooldown. Test reconnect/recut suppression and restart alongside the larger burst.

Implementation resumed24 October on `fix/qb13-final-growth-burst`, after the independently reviewed and native-verified Core pause correction. Engine work uses a short local loaded-only surface queue, shared four-write budget and journaled outcomes. Root native proof checks all24 eligible columns of the5x5 surface around the Heart (the Heart occupies the center). Existing Warden/cooldown/restart proof remains required. Verified24 October: `python scripts/workspace.py build` passes in28 seconds (391 Java tests, including14 focused Heart cases after the shared-budget correction). `python scripts/heart_defiance_smoke.py` passes initial and saved restart in `../reports/heart-defiance-20261024T134933908514Z`: all24 eligible surface columns in the5x5 patch are actual matching journal/world cells, exactly one direct Warden appears, physical reconnect/recut has no second growth or creature, and the receipt survives restart. Root inspected the captured world image. The native run preceded the final no-budget early-return; the added80-tick saturation regression and full build verify that narrow correction. Independent source review is clear. No deployment yet.

The burst now reads a fixed loaded-only5x7x5 neighborhood, floods only reachable air/owned-vein surfaces from open immediate Heart neighbors, and processes at most32 surfaces/four journaled terrain writes per work tick for at most40 work ticks. Busy ticks with all shared writes occupied defer the queue without spending its work window. Ordinary growth yields during the burst. Substrate converts to sculk; supported veins bootstrap continuity where needed. Every write rechecks actual loaded neighbors and excluded backing. An entirely sealed immediate shell prevents the attempt, including a one-block shell with air outside.

Pause suspends the transient queue; recovery completes this same authorized attempt. Invalid root, failure, mode-off, eradication, certified cuts or unresolved topology cancel it. Restart does not replay a pending queue; the existing durable cooldown receipt suppresses another last stand. No new journal or checkpoint schema is introduced. Earlier focused-suite cleanup failures were Windows temporary-file failures; the final escalated full build passes them as well.

The pulse, Warden and cooldown contract below remains in effect; the former single-cell growth description is superseded by the burst above.

The Heart retains its normal 60 occupied ACTIVE seconds of weakening below the Established threshold. Zero connected sculk still requires the full wait. Incomplete or unloaded proof resets that wait; known pauses preserve it and restart requires a fresh wait. See [the saved-world diagnosis](M12_HEART_DIAGNOSIS.md).

During an eligible weakening phase, the Heart gives local sculk-soul particles and catalyst-like pulses. At five sixths of the wait (50 seconds by default), it makes one bounded final growth attempt and directly summons one Warden if a loaded, clear column with solid footing is available. It never loads chunks, replaces obstacles to fit the creature or retries failed spawning. Peaceful prevents the summon. The Warden has ordinary vanilla behavior and despawning; this event does not advance player warning counters. Campaign shriekers retain their existing vanilla warning rules.

An empty network may seed one exposed substrate or supported vein directly beside the Heart. A living network uses the existing bounded surface grower. Both use existing exclusions, capacity, per-tick write limits and journal-before-change restoration records. A sealed solid shell prevents extension. Adding a few cells below the weakness threshold does not restart the weakening wait merely because the Heart attempted growth.

The whole phase shares a persisted cooldown, initially **600 occupied ACTIVE seconds**, claimed before its first pulse. Reconnecting and cutting again cannot replay any part of the phase during that cooldown. A weakening cycle that starts inside the cooldown remains suppressed for its whole wait, even if the cooldown expires partway through it. Pauses and downtime do not count. Conservative clock rollback may lengthen the wait; it cannot shorten it.

Operators can tune future claims in Heart weakness settings: 60, 300, 600, 1800 or 3600 seconds. Changing this setting does not rewrite the expiry of a receipt already issued. The separate strict, atomic `world/quiet-below/heart-defiance.json` pins the receipt to the exact campaign source, Heart and position. Missing state starts with defaults. Invalid, mismatched or uncertain state suppresses optional last stands and logs a diagnostic while normal vulnerability remains available. Cosmetic or spawn exceptions suppress the remaining effects of that phase without holding the ordinary weakness timer. An actual ecology journal/write failure retains the existing ecology safety behavior.

## Verification

Focused engine/store tests cover the real empty-network wait, milestone crossings, one final bounded effort, exclusions, shared write budgets, cooldown persistence and expiry, strict corrupt-state preservation and optional-effect exception containment. Fault tests compose the store/effect seams with the independent vulnerability engine; they do not inject faults into a running Minecraft server.

`python scripts/workspace.py build` passes (40 seconds; 372 Java tests, no failures or errors). All 77 Python tests pass. `git diff --check` is clear.

`python scripts/heart_defiance_smoke.py` passes: `../reports/heart-defiance-20261023T180513491261Z/report.json`, with initial and restart client reports and a screenshot inspected by the lead. It builds matching jars and stages a fresh offline Normal-difficulty test server. Actual occupied time and player-loaded chunks establish a protected 256-cell source. A physical cut reaches tick1007 with one root-adjacent journaled seed, one direct Warden and receipt1. Reconnecting restores protection; recutting reaches tick1007 with zero living cells, no new growth and the same receipt. The first Warden despawns normally. A saved restart retains the receipt and suppresses replay. No clock fast-forward, forced chunks or modification of the authenticated review world is involved.

Earlier failed disposable runs exposed stale test artifacts and a premature topology assertion. Those driver issues were corrected; their saved/stopped runtime copies were retired after exact path, process and world-lock checks, while reports remain available. No gameplay workaround was introduced for them.

The additional dev.44 exact saved-world reproduction also passes: `../reports/heart-repro-20261023T180907993477Z/report.json`. It observes the real 60-second wait with the new last stand present, then mines the vulnerable Heart in Survival and receives the local cryptic cue. Every original source-world file is unchanged. Independent source/runtime review found no material issue; native evidence checks direct spawning and duplication, while warning-manager isolation is established by source review rather than a dedicated native counter assertion.

The authenticated review server and client pack remain on dev.43 until the remaining Regions and automatic supplied-block registration corrections are ready for one combined review.
