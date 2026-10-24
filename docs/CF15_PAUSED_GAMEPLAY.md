# CF-15: paused player gameplay

Implementation authorized 24 October by the operator's request to continue. This corrects the Core pause boundary; the stronger Heart burst and Block-limits placement follow sequentially. The four later UI polish items share one human review when implemented.

## Diagnosis

`SessionPresentation.apply` only sets vanilla `ServerTickManager` frozen. That deliberately retains player/network processing, which preserves requested mobility but leaves packet-driven placement/item use and ordinary player physiology running. Existing freeze checks covered simulation/status; they did not assert denial of these ordinary paused actions. The operator's reported placement, eating, damage and hunger changes are consistent with these unguarded vanilla paths.

The supplied VSMP artifact uses separate non-ACTIVE block/combat/hunger/crafting guards. Its admin-started Building Mode is global and unfreezes ticks for everyone; its inspected consumption handlers do not prove general vanilla eating cancellation. See the reference notes for exact class evidence. Chronicle should explicitly guard these actions rather than copy that state machine or install Paper code.

## Behavior

- ACTIVE with no runtime hold retains normal gameplay. Every other phase, unavailable campaign state and runtime holds freeze ordinary survival progression and world/inventory interactions.
- Players remain mobile and can edit character cards and use Core management screens. Administrative commands remain trusted explicit operations. Closing an already-open inventory/workstation remains allowed to return already-owned cursor/escrow items; vanilla may drop those returns if the inventory is full. This conservation cleanup grants no new crafting or item-use action.
- Initial build preference: use the existing non-active operator author flag as the map-work exception, rechecking operator permission on every action. No implicit creative/operator bypass. A user question about global versus per-editor building is pending; this is the stated recommended assumption and can be changed before handoff.
- Builders may perform intended map interactions while simulation remains frozen. Their inventory/container management, drops and item pickup remain available for map work. Health, hunger, consumption, XP pickup and survival timers remain paused as well. Active play clears authoring through the existing service. Reconnect never silently restores editing privileges.
- Do not reset health/hunger on resume or heal/refill players as VSMP does. Preserve existing state and modifiers. Block damage/healing, hunger/exhaustion progression and status duration updates at their vanilla server seams. Stop in-flight consumptions; client predictions must be corrected.
- Deny normal placements/breaking, entity/item use, crafting/container mutations, drops and pickups while frozen; use a shared Core server boundary rather than campaign-specific checks. Do not load chunks while rejecting predicted actions. Keep packet acknowledgement/threading correct.
- Preserve fire/air/fall counters against paused movement hazards without cancelling complete player ticks or rewriting trusted admin health/food state. No blanket invulnerability or ability flags saved to the player, so crash/restart cannot leave an ACTIVE player permanently privileged.

## Structure and verification

Core owns a pure phase policy, server API, precise player/hunger/network mixins and lifecycle upkeep. Existing simulation presentation uses the same freeze decision. No campaign schema change, no new dependency, no production-world rewrite. Quiet Below continues consuming normal Core state.

Root owns server API/policy/bootstrap, packet/interactions, hazard counters, native harness and docs. The physiology worker owns only new physiology mixins, narrow ExpandedHungerMixin changes and its registrations. An independent reviewer checks the integrated point before proceeding to the next correction.

Verify pure phase/hold/builder gates, mapped compilation, actual paused Survival placement/mining/food/damage/hunger/effects, in-flight use, mobility/card access, editor permission/revocation, resume/end/reconnect and stale/forged packets in an isolated loopback fixture. Confirm a bad/missing state fails closed. Later run affected native menu/managed-block regressions and full build. Deploy one matching bundle after the sequential corrections; preserve the existing review/auth configuration and verified stopped backup.

## Verified checkpoint - 24 October

`python scripts/workspace.py build` passed (26 seconds). `python scripts/pause_smoke.py` passed with matching Fabric client/server in isolated loopback fixture `../reports/pause-gameplay-20261024T133311511341Z`:

- actual horizontal movement with paused Survival placement/mining/food rejected; client inventory and predicted blocks corrected;
- health, food, saturation, exhaustion and a duration-600 effect unchanged; direct player and HungerManager exhaustion plus damage denied;
- explicit operator editing permits map placement while survival remains frozen; live de-op immediately revokes building;
- ACTIVE restores mining, placement and eating; pausing actual in-flight eating clears use without consuming food;
- ACTIVE runtime hold denies mining/damage, release resumes, and ENDED denies mining.

Native testing exposed Peaceful replenishment bypassing HungerManager.update; the separate ServerPlayerEntity.tickHunger path now freezes too. Earlier failed fixtures/reports are retained as diagnosis evidence, including the initial incompatible vanilla bot. Final harness uses the required matching Fabric client. Independent mapped source review found and verified fixes for stale asynchronous book/sign completions, paused cake/consumables, universal XP freezing, and effect-loop periodic sync. Closing a workstation remains a deliberate idempotent item-conservation exception.

Limits: no dedicated native async-text filtering delay, full-inventory paused close/drop, hostile malformed packet flood, paused reconnect/restart, or explicit fire/air/frost fixture in this run. These paths have source/policy checks; they are not claimed as native observations. Existing menu/managed-block regressions follow with the combined bundle. No deployment yet; dev.46 auth review stays unchanged until all corrections are verified.
