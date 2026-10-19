# CF-09: Core authoring interface

Current design checkpoint: the operator rejects the accumulated interface after M8 and requests [CF-10's full menu/command redesign](CORE_UI_REDESIGN.md). This file describes the deployed first screen, not the proposed replacement. No new UI has been installed as part of the design pass.

19 October 2026. Implement the operator's requested proper in-game UI before extending chat authoring. Core owns the screen and protocol; campaigns supply the existing registered roles and loot defaults. Reuse current services and persistence, with no map migration or new story mechanics.

## First coherent screen

O (rebindable) or `/chronicle author` opens the Core authoring screen. `/chronicle author chest` opens its chest tab; `/chronicle spawn` opens spawn controls. Matching client/server required. Chat commands remain an advanced fallback, including explicit chat menus.

Show the session phase, author mode, current selection/target and actionable reasons for disabled controls. Offer Enable/Disable author mode. Do not silently pause a story session or enable editing. The Chest tab picks a registered loot type and binds the looked-at container, lists saved typed chests, and offers inspect/outline/retype/unbind and JSON reload. Retype/unbind require confirmation. A persistent warning identifies managed loot containers whose scheduled fills replace all contents. Loot probability editing remains JSON in this slice.

Places lists existing regions/locations/block anchors with show/replace/remove. Registered roles are a separate paginated picker with kind-appropriate save/replace controls. Select corners at feet or looked blocks, close the screen to walk between corners, then reopen without losing the selection. Existing custom IDs remain usable. Spawn controls pick registered/saved purposes, a saved location and a named online player; preserve preview/confirm for runtime moves. Session controls expose start/pause/resume/end, with confirmation and refill warning on start. The initial CF-09 slice introduced no automatic respawn/entity groups; the M7 extension below adds explicit player policies.

## Trust and failure boundaries

Use bounded typed C2S actions and private S2C pages, never arbitrary client command strings or trusted client coordinates. Operator permission is checked on every request. The server derives actor/world/target and delegates to allowlisted existing operations or Core services. Per-connection opaque snapshot tokens pin permission/session/mode, geometry/binding/config revisions and target; reject stale mutations and return refreshed state. Disconnect/restart clears UI context. Rate-limit requests, bound page/list/text sizes, and preserve the existing journal/holds. Client hides/disables unsafe actions but server checks remain authoritative. UI feedback stays in the screen; no growing chat transcript. Timeouts permit refresh and do not imply a failed save is safe to repeat.

## Verification

Model bounds and serialization tests; full build and scripting regression checks; isolated real Fabric GM/non-op requests, mode guidance, actual widget interactions, one-step chest binding, stale-target/config rejection, active edit rejection, geometry selection and spawn preview/confirm. Test the UI at small and large resolutions and inspect screenshots for readable layout. Preserve prior session/dossier/loot/recovery smoke checks. Stop/backup before deployment, verify preserved state and authenticated localhost startup, then request human screen-flow review. Document undelivered controls explicitly rather than claiming all of CF-09 complete.

## Builder review

Import the matching `../client/Chronicle-M8-1.21.10.mrpack` and join localhost. Open with O; use **Mode on** while the session is non-active. Look at a container before opening, cycle the registered Loot choices, then **Bind looked container**. Close the screen to walk or aim somewhere else, then reopen/Refresh. Select saved rows for outline/retype/unbind; confirmation is explicit. Hover the information or feedback lines to read their full text at small window sizes.

For regions, select a **Role**, set the first corner, close/walk/reopen and set the second, then save. Saved places remain in **Places**. Existing custom names are retained; creating new custom IDs uses advanced commands in this slice. **Spawns** chooses a purpose, location and online player; runtime movement still needs a safe preview and explicit confirmation. **Session** never silently changes phase to permit edits.

Review readability at your normal GUI scale, one-step chest binding and changing types, region selection across movement, and manual spawn controls if used. The operator accepted the screen and reported periodic flicker; the background-refresh fix is bundled with the next M7 review. Automated widget checks additionally verify stable keyboard focus/widget identity across unchanged polling. Evidence and deployment preservation belong in [TEST_PLAN](TEST_PLAN.md).

**Policies** and its paginated purpose picker configure opt-in automatic player spawns; see [M7_AUTOMATIC_SPAWNS](M7_AUTOMATIC_SPAWNS.md). **Session > Participants/Observations** adds [M8's membership and optional cues](M8_IMPLEMENTATION.md). Clock-only polling/checkpoints preserve widgets, focus and typed input; changes to permissions, saved bindings or audiences still refresh controls.
