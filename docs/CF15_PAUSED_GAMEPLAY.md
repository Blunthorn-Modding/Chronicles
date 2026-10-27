# CF-15: paused player gameplay

**27 October dev60 update:** [session mode polish](SESSION_MODE_POLISH.md) supersedes the simulation and hold wording below. The operator explicitly chooses live vanilla simulation in global Building Mode, with player health/hunger held. Building permits chest/inventory map work; ordinary paused play rejects container opens and closes already-open containers. Survival guards remain separate from world simulation and synchronize their client presentation independently. Chronicle clocks and scenario progression remain paused. Temporary runtime holds suspend Building Mode and restore it when released in the same phase/session; phase changes and restart clear it. Use `/chronicle session building on|off`; the plural root below is historical.

**Confirmed global policy,24 October:** the operator selected everyone during an explicit global Building Mode. This supersedes the earlier temporary per-operator-author assumption below. Session > Play controls and `/chronicles session building on|off` control it for the whole server. Default off; memory-only; phase changes, holds, unavailable runtime and restart clear it. Authoring controls metadata only. All builders retain frozen health/hunger/effects/consumption/XP, with no automatic game-mode change. Native global-policy run144712 passed; earlier per-author evidence below remains historical. TEST_PLAN records the final combined evidence and deployment.

Implementation authorized 24 October by the operator's request to continue. This corrects the Core pause boundary; the stronger Heart burst and Block-limits placement follow sequentially. The four later UI polish items share one human review when implemented.

## Diagnosis

`SessionPresentation.apply` only sets vanilla `ServerTickManager` frozen. That deliberately retains player/network processing, which preserves requested mobility but leaves packet-driven placement/item use and ordinary player physiology running. Existing freeze checks covered simulation/status; they did not assert denial of these ordinary paused actions. The operator's reported placement, eating, damage and hunger changes are consistent with these unguarded vanilla paths.

The supplied VSMP artifact uses separate non-ACTIVE block/combat/hunger/crafting guards. Its admin-started Building Mode is global and unfreezes ticks for everyone; its inspected consumption handlers do not prove general vanilla eating cancellation. See the reference notes for exact class evidence. Chronicle should explicitly guard these actions rather than copy that state machine or install Paper code.

## Behavior

- ACTIVE with no runtime hold retains normal gameplay. Every other phase, unavailable campaign state and runtime holds freeze ordinary survival progression and world/inventory interactions.
- Players remain mobile and can edit character cards and use Core management screens. Administrative commands remain trusted explicit operations. Closing an already-open inventory/workstation remains allowed to return already-owned cursor/escrow items; vanilla may drop those returns if the inventory is full. This conservation cleanup grants no new crafting or item-use action.
- Confirmed build preference: an operator explicitly enables global Building Mode while play is non-active. Everyone then receives normal map interactions; metadata editing grants no separate building bypass. Holds, phase changes and restart clear Building Mode; no implicit creative/operator bypass.
- Everyone may perform intended map interactions while global Building Mode is on and simulation remains frozen. Their inventory/container management, drops and item pickup remain available for map work. Health, hunger, consumption, XP pickup and survival timers remain paused as well. Active play clears authoring through the existing service. Reconnect never silently restores editing privileges.
- Do not reset health/hunger on resume or heal/refill players as VSMP does. Preserve existing state and modifiers. Block damage/healing, hunger/exhaustion progression and status duration updates at their vanilla server seams. Stop in-flight consumptions; client predictions must be corrected.
- With Building Mode off, deny normal placements/breaking, entity/item use, crafting/container mutations, drops and pickups while frozen; use a shared Core server boundary rather than campaign-specific checks. Do not load chunks while rejecting predicted actions. Keep packet acknowledgement/threading correct.
- Preserve fire/air/fall counters against paused movement hazards without cancelling complete player ticks or rewriting trusted admin health/food state. No blanket invulnerability or ability flags saved to the player, so crash/restart cannot leave an ACTIVE player permanently privileged.

## Structure and verification

Core owns a pure phase policy, server API, precise player/hunger/network mixins and lifecycle upkeep. Existing simulation presentation uses the same freeze decision. No campaign schema change, no new dependency, no production-world rewrite. Quiet Below continues consuming normal Core state.

Root owns server API/policy/bootstrap, packet/interactions, hazard counters, native harness and docs. The physiology worker owns only new physiology mixins, narrow ExpandedHungerMixin changes and its registrations. An independent reviewer checks the integrated point before proceeding to the next correction.

Verify pure phase/hold/builder gates, mapped compilation, actual paused Survival placement/mining/food/damage/hunger/effects, in-flight use, mobility/card access, editor permission/revocation, resume/end/reconnect and stale/forged packets in an isolated loopback fixture. Confirm a bad/missing state fails closed. Later run affected native menu/managed-block regressions and full build. Deploy one matching bundle after the sequential corrections; preserve the existing review/auth configuration and verified stopped backup.

## Historical per-author checkpoint - 24 October (superseded by global policy)

`python scripts/workspace.py build` passed (26 seconds). `python scripts/pause_smoke.py` passed with matching Fabric client/server in isolated loopback fixture `../reports/pause-gameplay-20261024T133311511341Z`:

- actual horizontal movement with paused Survival placement/mining/food rejected; client inventory and predicted blocks corrected;
- health, food, saturation, exhaustion and a duration-600 effect unchanged; direct player and HungerManager exhaustion plus damage denied;
- explicit operator editing permits map placement while survival remains frozen; live de-op immediately revokes building;
- ACTIVE restores mining, placement and eating; pausing actual in-flight eating clears use without consuming food;
- ACTIVE runtime hold denies mining/damage, release resumes, and ENDED denies mining.

Native testing exposed Peaceful replenishment bypassing HungerManager.update; the separate ServerPlayerEntity.tickHunger path now freezes too. Earlier failed fixtures/reports are retained as diagnosis evidence, including the initial incompatible vanilla bot. Final harness uses the required matching Fabric client. Independent mapped source review found and verified fixes for stale asynchronous book/sign completions, paused cake/consumables, universal XP freezing, and effect-loop periodic sync. Closing a workstation remains a deliberate idempotent item-conservation exception.

Limits: no dedicated native async-text filtering delay, full-inventory paused close/drop, hostile malformed packet flood, paused reconnect/restart, or explicit fire/air/frost fixture in this run. These paths have source/policy checks; they are not claimed as native observations. This checkpoint predates the confirmed global Building Mode choice; the newer TEST_PLAN entry governs its final native verification and deployment.

Earlier combined regression24 October (before the global-policy answer): pause142902 repeats the gameplay checks and verifies exactly one ACTIVE actionbar after resume; menu142345 and managed143027 pass with the explicit paused-editor exception. Full dev.47 build396 Java/77 Python passes. See TEST_PLAN for deployment and remaining source-only limits.

Global-policy verification: `../reports/pause-gameplay-20261024T144712855698Z` PASS proves author-only placement denial, non-operator command rejection, ordinary non-author placement/mining during explicit global mode, frozen food/health/hunger/saturation/exhaustion/effect duration, mode-off revocation and resume/hold/end resets. No dedicated physical container/pickup/drop, fresh-join or restart proof is claimed.
