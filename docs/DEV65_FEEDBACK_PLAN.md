# Dev65 organizer feedback

2 November 2026. Work is sequential: retained encounter recovery and new Region placement, then Altar behavior/presentation, then scenario feedback/custom authoring. Each slice receives focused verification and independent review. One combined client/server handoff follows.

## Encounter recovery

The organizer's Event `chronicle_core:event_event_group_test` stopped at `ENCOUNTER_EFFECTS` when four blazes were planned at the bottom of `forge_trial_source`, inside its ground. No entity was claimed. The OPEN plan remained truthful, but review only exposed UNCERTAIN plans and explicit recovery did not clear the failure latch.

Retain every original UUID, plan hash and stage. A paused operator can inspect the retained plan, clear its exact spaces and retry after full preflight, or explicitly skip the remaining child spawns. Skipping an OPEN child uses a distinct CANCELLED terminal; accepting an uncertain result retains REVIEWED. Neither action removes entities, rewrites history or replays previous Region changes. Normal session/flag rules govern later outcomes. New Region plans must find bounded loaded clear spaces; existing plans never move to new positions.

Regression coverage must include blocked retry refusal, successful retry after clearing space, no paused spawns, confirmed skip, uncertain missing claims, restart/no child yet, no duplicate entities and archival of resolved evidence.

## Altar

The dedicated UI design was reviewed by the lead before implementation. Keep the slot-free vanilla-style popup. Show all five remaining prayers together; remove Quiet Harvest and the More prayers split. At minimum 320x240, use a 224x204 popup, five 200x18 choices, bounded feedback and Close. Keep generic offering pagination for other consumers.

Gameplay refusals remain clickable and produce ordinary two-HP damage plus a private cryptic explanation; stale packets, duplicate transport, paused play and missing workstations are not valid prayer attempts. Damage must not reduce maximum health or grant/pay a trade. A successful prayer starts the shared per-character Altar cooldown, default fifteen occupied active-play minutes, persisted across death/reconnect/restart and shared across Altars. Permanent prayers retain three-use limits. Temporary effects cannot be stacked by repeating before expiry. Quiet Harvest claims already paid must finish under their captured terms; new purchases disappear.

Consolidate Quiet Below's player-facing operator configuration into one Game setup entry, Altar offerings. Reusable Core offering services remain available to other content mods. Operator terms may be explicit; the player popup remains mysterious.

## Scenario content

Export needs immediate pending feedback, persistent explicit success/failure in the popup, the actual server-owned path and a clear next step. A successful response's new token is not a stale external change. No runtime-installed JAR is rewritten.

The organizer confirms an empty custom scenario in a separate authoring world/server, keeping the installed mechanics. Existing editors mutate live world/config services, so the new setup must have its own world and configuration; creating it never clears or switches the current scenario. A controlled authoring kit and startup profile select empty reusable defaults, while installed mechanics remain registered. The existing editors then work normally on that authoring server, and export uses its custom identity. Startup refuses missing required providers or an incompatible previously initialized world rather than replacing its catalogs. Map geometry remains world-owned. The UI must describe the separate startup step honestly; a create-only JSON button is insufficient.

The organizer cleanly stopped the original review server. A CRC-verified stopped backup is `../backups/server-20261102T085709263586Z.zip`; the original blocked Event/config/journal/log evidence is also retained in `../reports/dev65-feedback-original-20261102T084638Z`.

## Delivery

Preserve the organizer's review world and exact failure evidence before swapping JARs. Serialize native clients and Gradle tasks because their class directories are shared. Verify the resulting artifacts and client defaults, use the existing authenticated review endpoint, commit on the feature/fix branch and push. Final map/content/group balance and queued custom sounds remain separate work.
