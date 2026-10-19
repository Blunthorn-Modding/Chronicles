# Handcrafted map integration

Planning draft, 19 October 2026. The operator authors the production map. Agent-built maps are technical fixtures. This document defines tools still needed; it does not advertise unimplemented commands.

## Available today

- Run a normal Minecraft 1.21.10 world with the matching Fabric/Core/campaign mods; existing session controls, freeze/status and dossiers work independently of the prototype map.
- A GM can define a cuboid of up to 64 loaded, supported natural blocks with `/quiet_below zone define <id> <from> <to>`, preview it and apply/undo the latest stage during ACTIVE. See [M4](M4_IMPLEMENTATION.md).
- No general map-import tool, landmark/region editor, timed/entry triggers, multi-stage spread network or authored event binding exists yet. The current marker observations are global hard-coded prototype text, not per-location authoring.
- `pilot-prepare`, `pilot-smoke` and `pilot-promote` concern the generated technical fixture. They are **not** a production-map import workflow; do not use the build function to “adapt” the operator's world.

## The intended builder workflow

1. **Bring in a copy.** Stop the source world; retain an untouched export. A future import tool copies it to a fresh workspace destination, checks game/data version and required content, and creates an import report. It does not overwrite either the source or the existing play world. An upgraded rehearsal must not be reopened in an older game version.
2. **Establish a campaign instance.** Associate a stable map/configuration ID with this copy. Keep story/session progress separate from authored definitions, so a rehearsal can start fresh without deleting the map or dossiers indiscriminately. Replacing an authored definition must not silently reset a live campaign.
3. **Choose the ancient city.** The builder can use a generated city or place/adapt the vanilla structure in the handcrafted landscape. The mod takes a selected site as input; it does not pick another city, regenerate terrain or require a custom city replacement. Preserve an ordinary route out of the settlement.
4. **Name places in-game.** Select regions/points for the settlement, mine entrance, first trace, city boundary, benefit site, first cost and optional departure route. Store dimension and exact bounds; show outlines/labels to the builder only. The in-game authoring tool should capture selections without requiring hand-edited coordinates.
5. **Approve connected growth patches.** Select only positions allowed to change; preview exact before/after states and counts. Record protected builds and connections between patches. A decorative city full of existing sculk is a backdrop, not a request to replace or journal the entire structure.
6. **Bind event definitions.** Choose an event's location, cue, recipients, readable observation, prerequisites and optional proposed stage. Configure E01's active-play delay and E03's discovery region. An item interaction must identify the local observation/site; it must not reveal the same arbitrary sentence everywhere.
7. **Validate and rehearse.** Produce actionable errors for missing sites, wrong dimensions, unsupported blocks, unconnected growth, unreachable prerequisites, missing cue assets or conflicting bindings. Walk the map in builder mode without consuming discoveries. Arm a fresh rehearsal instance and exercise the real player paths.
8. **Activate deliberately.** Save/export a versioned configuration, back up the stopped play world, review changes and explicitly activate. The activation report records world/config versions and a recovery point. Disable test authoring/automatic fixtures. No map rebuild happens on server start.

## Minimum authoring data

| Definition | Required information |
| --- | --- |
| Campaign/map binding | Schema, map ID, campaign instance, compatible game/mod versions, configuration revision |
| Landmark/region | Stable ID, dimension, point or bounds, display name, purpose, builder-only preview |
| Growth patch | Stable ID, exact eligible coordinates/states, connected predecessor(s), permitted outcome, protected boundaries, approval/undo record |
| Observation | Stable ID, site binding, plain readable text, discovery prerequisites, optional additional tool reading, alternate access route |
| Trigger/event | Stable ID, prerequisites, manual/timer/entry source, target event, audience, once-only policy, armed/held state |
| Runtime progress | Clock, completed/discovered events, intervention state and journals; stored separately from definitions |

This is a proposed contract, not a JSON schema to implement without review. Keep campaign ecology in Quiet Below. Extract a Core API only when reuse is demonstrated. Proposed CLI/UI names belong in the authoring specification rather than pretending they are current commands.

## Acceptance criteria for the first authoring slice

- Import an operator-supplied stopped world copy without modifying its source or any existing server world; report incompatibilities instead of replacing missing blocks.
- Bind the city and mine using in-game selections, save, restart and confirm exact dimension/bounds round-trip.
- Inspect and walk regions as a builder without firing story events; change into participant rehearsal explicitly.
- Reject malformed/future schemas, missing references, unsupported targets and unsafe stage selections. A moved region must invalidate affected approvals rather than retargeting a queued write.
- Verify cue audiences with two test clients, including one outside the discovery region. A discovery does not reveal the city's coordinates to uninvolved characters.
- Retain existing pause/freeze, permissions, per-world persistence and conflict-preserving world-change checks.
- Move the same event setup to a different rehearsal map through configuration, without a source edit/build and without importing another world's campaign progress.

Coordinate limits, protected-region editing, configuration migration and exact operator commands need an implementation specification before coding. A large visual editor, general quest engine, schematic generator and global free-growing sculk simulation are outside this first slice.
