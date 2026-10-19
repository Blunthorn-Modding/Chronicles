# M7 follow-up: opt-in player spawn policies

19 October 2026. The operator accepts the Core screen and requests continued implementation, with its periodic flicker fixed in the next review build. This slice adds automatic player arrival/respawn; entity encounter groups remain a separate M7 sub-slice. No campaign encounters, ecology, or scheduled narrative are introduced.

## Behavior and ownership

Core stores explicit per-player UUID policies. A GM in non-active author mode chooses a saved, revision-pinned spawn purpose and enables **first arrival once** and/or **death respawn** independently. Both default off; saving a location or manual binding activates neither. The shared screen manages these policies; commands remain recovery/advanced fallback.

First arrival is considered only on joining ACTIVE play or a transition into ACTIVE, never on every tick/reconnect after its first attempt. Live, non-spectator, non-author players are eligible. Each attempt is claimed durably before moving, and records MOVED or FAILED afterward. A crash after the claim leaves an explicit uncertain CLAIMED record and never retries automatically. Invalid/stale/unsafe/unloaded destinations record failure and leave the player in place. A GM may explicitly reset the claim while non-active; reset does not move anyone immediately. This favors avoiding duplicate forced moves over silently retrying an uncertain teleport.

Death respawn applies only immediately after a real death respawn during ACTIVE, without a runtime hold, and only to an explicitly assigned eligible player. Revalidate the exact binding, loaded support/headroom/border/hazards at execution. Unsafe/stale/unavailable policy leaves the vanilla respawn result intact. Preserve bed/anchor/world spawn settings, inventory rules and facing semantics; no new chunk loading, delayed respawn queue or map search. Reconnect, dimension return and End exit do not count as deaths.

Players may run `/chronicle leave` to remove their own automatic policies; arrival history remains. Leaving must not teleport them, announce private membership, or re-enroll them later. GM reassignment is explicit. No boundary or location traps departing characters.

## Data and failure contract

New independent `<world>/chronicle/automatic-spawns.json` schema 1: revision, bounded UUID policy map containing nullable pinned SpawnBinding values, and bounded arrival claim history with purpose/status/reason. Strict duplicate/unknown/future-schema rejection, durable atomic writes, previous-file backup and external-edit detection follow existing stores. No migration or rewrite of existing authoring/spawn/chest/session data. Save before publishing memory. Failed/corrupt storage disables automation and preserves evidence; existing manual controls continue.

Server adapters own join/session-transition/death callbacks, eligibility, loaded-world validation and teleport. UI requests remain operator checked, token/frame checked and finite; include automation revision in the frame. Screen refresh should preserve widgets/hover/focus and avoid disabling/recreating controls for unchanged background polling.

## Acceptance and review

Pure policy/store tests cover independent policies, pinned references, once claims/restart/reset/leave, bounds, corrupt/external/failing writes and save-before-publication. Full build and workspace tests. Real disposable Fabric tests cover opt-in first arrival, unsafe/stale fallback, ordinary-player edit rejection, self leave, actual death respawn, pause/reconnect/restart behavior and unchanged vanilla spawn state. UI regression asserts stable widgets over multiple unchanged polling cycles. Preserve prior loot/session/dossier/content checks. Stop/back up before deployment; human review covers automatic arrival/death workflow and the flicker fix together.

## Current builder workflow

Use the matching `Chronicle-M7-spawns-1.21.10.mrpack`. In non-active author mode, save a location with **Roles** (or choose an existing location), then use **Spawns** to bind an arrival/respawn purpose to it. These remain harmless definitions.

Open **Policies**, choose a saved purpose, return and select the exact online player. **First arrival once** and **Death respawn** each ask for confirmation. **Arrival off** / **Respawn off** disable them independently. **Reset arrival** explicitly removes an attempt record, including a failed or uncertain attempt; enabling a policy alone does not reset history. Offline saved accounts remain selectable by UUID. All policy data is private to operators.

Keep the screen open across several two-second refreshes to check the flicker fix. Start ACTIVE play to test first arrival, walk away, reconnect and check you are not pulled back. Test a configured death respawn and an obstructed target; the latter retains vanilla respawn. `/chronicle leave` removes only your own policies, retains history and moves nobody.

Advanced fallback: `/chronicle spawn automatic arrival <player> "<purpose>"`, `respawn`, `arrival_off`, `respawn_off`, and `resetarrival <player>`. Edits require non-active author mode. GUI selection also supports saved offline accounts; command player arguments use online names.

A hold at a join/ACTIVE edge skips that attempt rather than queueing a delayed teleport. Releasing a hold is not a new arrival event. Movement receipts describe server execution; position persistence still follows Minecraft's normal player saving, and an interrupted move never automatically retries. Corrupt state or failed writes disable automation while manual spawn tools remain usable. Repair/restore the file while stopped and restart before trying again.

Entity encounter groups, automatic scenario transitions, general participation/active-clock services and observations remain separate M7/M8 work. No production-map policy is enabled by this update.

Delivered as dev.10: 98 Java tests, 17 scripting tests and the full two-client/restart/content Fabric run pass. Small/large screens and the new policy screen were inspected. The play server and matching pack are updated after backup and preservation checks; [TEST_PLAN](TEST_PLAN.md) records exact commands, evidence and remaining human checks. Review the automatic policy workflow and the minor flicker correction together.
