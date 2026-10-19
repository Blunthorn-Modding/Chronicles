# In-world authoring with Chronicle Core

Revised design draft, 19 October 2026. The operator builds the map and defines its important places **inside that world**. The main workflow is select, name and bind; a world-copy/import pipeline is not a prerequisite. This is a specification proposal, not installed commands.

The operator accepts this authoring direction for now. For [Quiet Below](THE_QUIET_BELOW.md), register key locations such as the source city, Relic Heart and ancient stations. Autonomous growth discovers cave/mine geometry itself; no painted sculk trail, patch graph, or region for every crop field/hazard is required. The generic selection/binding tools below remain useful without making all of them mandatory campaign setup.

## Available today

- Run a normal Minecraft 1.21.10 world with the matching Fabric/Core/campaign mods; existing session controls, freeze/status and dossiers work independently of the prototype map.
- A GM can define a cuboid of up to 64 loaded, supported natural blocks with `/quiet_below zone define <id> <from> <to>`, preview it and apply/undo the latest stage during ACTIVE. See [M4](M4_IMPLEMENTATION.md).
- Chronicle Core does not yet have an in-world region/anchor editor, generic chest/spawn bindings or entry/timer configuration. The current campaign cuboid command is a narrow transformation prototype, not the shared authoring system.
- `pilot-prepare`, `pilot-smoke` and `pilot-promote` concern the generated technical fixture. They are not the production authoring workflow and must not rebuild or replace the operator's map.

## Reference and ownership

The supplied Vampire SMP plugin demonstrates registering meaningful locations through in-game admin commands and saving them for later behavior. See [the inspection record](REFERENCE_VAMPIRE_SMP.md). Chronicle should follow that usable interaction pattern and add the requested two-corner region selection. It remains a Fabric project; no Paper plugin installation or dependency is needed.

| Chronicle Core owns | Quiet Below owns |
| --- | --- |
| Builder selection tool/commands, named cuboids, points and block anchors, dimensions and orientation, list/show/edit/remove, validation, persistence and permissions | What a city, Heart, ancient station or optional campaign region means |
| Reusable chest content profiles, explicit fill/reset policies, spawn points/groups and generic event/trigger binding services | Plant growth/regen rules, network power, environmental costs and Ancient Forge recipes |
| Builder previews, draft edits, configuration revisions, stable IDs and reference checks | Autonomous growth rules, observed network topology, Heart resolution and narrative cues |

Core definitions must work for Journey worlds, Survival spawn areas, Werewolf locations and Situation campaigns. Campaigns reference those definitions by ID rather than inventing their own coordinate editor. Keep sculk ecology out of Core; moving reusable authoring into Core does not move campaign transformation logic there.

## The intended builder workflow

1. **Build in the world.** Install the mods for the intended map as usual. Enter author mode while story progression is inactive. Building and registering locations do not require exporting the world or starting another server.
2. **Select an area.** Set corner 1 and corner 2 with a selection tool or commands. Show a builder-only outline and dimensions. Both corners must be in the same dimension. Save the cuboid under a meaningful ID such as `ancient_city` or `first_garden`; the region is metadata, not a WorldEdit fill operation.
3. **Register exact locations.** Stand at an arrival/respawn point to save position and facing. Target a chest, forge or altar block to bind its exact block coordinate and expected type. Selection corners, entity spawn positions and block anchors are different primitives; do not round them all into one ambiguous location type.
4. **Assign a purpose.** Bind the city region to a first-entry event, a block anchor to a chest profile or forge behavior, and a point to a named spawn group. Campaigns can also bind regional effects when appropriate; Quiet Below derives crop effects from living growth without requiring field registration. Reusing one region for several purposes is explicit; the geometry itself does not imply behavior.
5. **Configure contents and spawn policy.** Capture/edit a reusable chest template, choose an explicit refill policy, and preview the target inventory action. Set which group uses an arrival/respawn point and when it applies. Saving a point does not immediately teleport anyone or override all vanilla respawns.
6. **Inspect, validate and save.** List configured things, jump to/show one as a builder, check bounds/references/types and save the draft. A separate apply/arm step activates behavior. Configuration survives restart; it does not recreate buildings or overwrite inventories on every server start.
7. **Test without consuming the story.** Builder walks do not fire discoveries. An explicit test mode can simulate cues and compare effects in bounded test areas. Moving/deleting a region or anchor invalidates affected armed bindings and pending approvals; do not silently retarget a queued effect.

### Illustrative commands — proposed, not implemented

```text
/chronicle author mode on
/chronicle author select pos1
/chronicle author select pos2
/chronicle author region save ancient_city
/chronicle author location save arrivals here
/chronicle author block bind archive_chest target
/chronicle author block bind ancient_forge target
/chronicle author chest profile capture archive_stock target
/chronicle author chest assign archive_chest archive_stock
/chronicle author spawn assign arrivals participants arrival
/chronicle author trigger enter ancient_city quiet_below:city_discovery
/chronicle author show ancient_city
/chronicle author validate
/chronicle author save
```

The selection tool should offer the same functions without repeatedly typing coordinates. Detailed syntax, permission levels and edit/apply confirmation belong in the first implementation specification. The namespaces above illustrate ownership, not an existing event API.

### Chests and spawns must behave predictably

- Binding an existing chest records its location/type; it does not place or empty one. Capturing its contents creates a template without consuming or distributing those contents. Filling/resetting is a separate, previewable runtime action.
- Initial chest policies should include manual fill and an explicit once-per-campaign/session option. Define whether only empty slots are filled; replacing player contents requires a deliberate reset policy. Restart/retry must not duplicate loot. A missing/replaced chest disables the binding instead of silently creating a new container.
- Location points retain dimension, precise position and yaw/pitch. Spawn groups can reference one or more points or an explicitly selected spawn area. Validate loaded safe standing positions/headroom when actually spawning; no global search or forced loading is implied by registration.
- Arrival, death respawn and scenario transition are separate spawn purposes. No Quiet Below location may trap a departing character by teleporting them back. Campaign-specific teams/private roles decide group membership without exposing them in public dossiers or public authoring previews.
- The same named points/regions can support entity-spawn bindings. Which entities, counts and escalation rules belong to the scenario's spawn handler; merely saving an area must not start an encounter or spawn animals automatically.

## Minimum authoring data

| Definition | Required information |
| --- | --- |
| Core authoring scope | Schema, world/campaign configuration ID, revision; separate from runtime completion/history |
| Core region | Stable ID, dimension, two normalized block corners, name, builder preview; membership checks without scanning every contained block |
| Core location / block anchor | Point with orientation, or exact block position with expected type and revision |
| Core chest / spawn binding | Profile/group reference, target IDs, explicit fill/respawn policy and validation state |
| Core event/trigger binding | Source region/point, registered action ID, audience, prerequisites, once-only policy and armed/test state |
| Campaign extension | Referenced Core IDs plus ecology/Heart rules, effect parameters, observations or recipes; observed growth topology is runtime data |
| Runtime progress | Clock, completed events, network state, inventory-action records and world-change journals; not the authoring definitions |

This is a proposed data contract, not a schema to implement without review. Core's current pure flag-event callbacks must not acquire hidden inventory/world side effects: generic delivery, chest/spawn actions and campaign effects need explicit services with their own persistence/validation contracts.

## Acceptance criteria for the first authoring slice

- In one existing world, select two corners, save a named cuboid and see its outline without editing JSON, exporting a world or rebuilding the mod.
- Save an exact location/facing and bind an existing chest/custom station by targeting it. List/show/remove definitions; round-trip them across restart.
- Register/select a chest profile and spawn point/group in Core without importing Quiet Below classes. Rebinding geometry does not activate a story action by itself.
- Inspect locations in author mode without consuming discoveries. Reject unauthorized edits, cross-dimension selections, invalid schemas, broken references and stale target types.
- Separate harmless large-region registration from authorized block changes. A city-sized discovery region is allowed; it is not permission to transform every block or bypass M4's current stage limit.
- Specify/test inventory fill/reset and spawn execution separately: no loss/duplication on retry, no replacement of unapproved contents, safe spawn behavior and no automatic map reconstruction.
- Keep existing pause/freeze, public dossier privacy and conflict-preserving transformation behavior intact. Campaign effects stay dormant outside ACTIVE even though authoring remains available.

Backups and optional rehearsal copies remain operational tools, not the primary builder UX or a condition for defining a region. No new world-import feature is required for this slice. A general terrain editor and the campaign's autonomous growth engine remain separate work.
