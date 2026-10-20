# In-world authoring with Chronicle Core

> CF-10 replaces the primary CF-09 workflow below. Press **O** or `/chronicles` for the Core menu. Enable editing while play is non-active. Regions: Create region, enter an ID, Continue in world, use `/chronicles author region pos1` and `pos2` at your feet (or add X Y Z), reopen Regions and save. Edit region loads both saved bounds; either position can be changed independently. Chests binds physical containers; Loot Tables edits distributions with Add item, independent percentage/counts and whole-table save. Locations separates Points and Block sites. Game setup assigns existing geometry to typed game requirements. Players owns participation, starting position and after-death destinations. Actual mod stations and their optional placement registration belong to the later station milestone. See [CF10_IMPLEMENTATION](CF10_IMPLEMENTATION.md) and [TEST_PLAN](TEST_PLAN.md). The CF-09 section below is historical compatibility documentation; its old commands remain available.

Updated 19 October 2026. The operator builds the map and defines its important places **inside that world**. The main workflow is select, name and bind; a world-copy/import pipeline is not a prerequisite. M6 implements geometry/selection, M7 adds typed loot and manual player moves, and CF-09 adds their shared screen. Trigger bindings remain later work. See [M6's exact commands and review checklist](M6_IMPLEMENTATION.md).

The operator accepts this authoring direction for now. For [Quiet Below](THE_QUIET_BELOW.md), register key locations such as the source city, Relic Heart and ancient stations. Autonomous growth discovers cave/mine geometry itself; no painted sculk trail, patch graph, or region for every crop field/hazard is required. The generic selection/binding tools below remain useful without making all of them mandatory campaign setup.

## Core authoring screen (CF-09)

Press **O** (rebindable), or run `/chronicle author`, with the matching client pack. The tabs are **Chests**, **Places**, **Roles**, **Spawns**, **Policies** and **Session**. Session phase and per-connection author mode are visible. Use **Mode on** at the bottom before editing; during ACTIVE, first pause using the Session tab. Author mode clears on reconnect and when play becomes ACTIVE. The screen never silently pauses play or enables author mode.

For chests, look at a single chest/barrel/shulker, choose a registered loot type with the Loot controls, then **Bind looked container**. Saved chests can be shown, retyped or unbound; destructive changes require confirmation. **Reload loot** reads `config/chronicle/chest-loot.json`. Managed fills replace all contents; keep personal storage separate. Probabilities remain JSON-configured.

For map sites, choose a registered **Role** and use **Corner 1/2** at your feet or **Target 1/2** for looked-at blocks. Close the screen to walk between corners; reopen to finish the selection and save. Location roles save your current position/facing, block roles bind the looked-at block. **Places** lists saved sites for show/replace/remove. **Spawns** chooses a purpose, saved location and online player, with explicit preview/confirm for an ACTIVE manual move. The **Policies** tab independently enables opt-in first arrival/death respawn per player; see [the policy guide](M7_AUTOMATIC_SPAWNS.md). No entity-group editor is included.

Commands below remain the advanced fallback, including custom IDs and `/chronicle author menu` / `/chronicle author chest types` for the old chat pickers. [CF09_AUTHORING_UI](CF09_AUTHORING_UI.md) describes the implementation and review gate. The operator accepted the screen; review now covers the new M7 policy flow together with the refresh fix.

## Available today

- Run a normal Minecraft 1.21.10 world with the matching Fabric/Core/campaign mods; existing session controls, freeze/status and dossiers work independently of the prototype map.
- A GM can define a cuboid of up to 64 loaded, supported natural blocks with `/quiet_below zone define <id> <from> <to>`, preview it and apply/undo the latest stage during ACTIVE. See [M4](M4_IMPLEMENTATION.md).
- Chronicle Core M6 adds per-player author mode, two-corner selection at feet/coordinates/targeted blocks, named regions, precise locations with facing, expected-type block anchors, list/show/validate/remove and explicit replace. Metadata persists with revisions and private previews. M7 adds typed loot and manual/opt-in automatic player spawns; entry/timer configuration remains M8. Saving geometry alone activates nothing.
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

### Implemented geometry commands (M6, refined in M7)

```text
/chronicle author mode on
/chronicle author select pos1
/chronicle author select pos2
/chronicle author region save ancient_city
/chronicle author location save arrivals here
/chronicle author block bind archive_chest target
/chronicle author block bind ancient_forge target
/chronicle author list
/chronicle author show ancient_city
/chronicle author validate
/chronicle author mode off
```

Save/replace/remove persist immediately; no separate save command is needed in M6. Author mode requires operator level 2 and a non-active session. `show` provides a persistent private outline; hide it with `/chronicle author preview hide`, clear the selection, leave author mode or disconnect. Movement does not hide it. Changing dimensions clears it; entering ACTIVE clears author previews. Install the matching M7 client pack. `select target1`/`target2` select a looked-at block within six blocks. A click wand is deferred to builder feedback.

### Registered choices and typed loot chests

`/chronicle author` offers clickable registered place roles. Click **Set** and submit the suggested command; **Replace** is explicit. Each submod supplies typed IDs, labels and descriptions through `ChronicleCore.authoring()` at initialization. Legacy custom place IDs remain readable. Namespaced strings need quotes when typed manually.

For loot containers, look at a single chest, barrel or shulker box, then use the **Loot chests** menu. Choose **Bind** for the type you want. One command records both the targeted container and its type, without first naming an anchor or capturing contents:

```text
/chronicle author mode on
/chronicle author chest
/chronicle author chest bind "quiet_below:settlement_supplies"
/chronicle author chest inspect
/chronicle author chest rebind "quiet_below:mine_cache"
/chronicle author chest unbind
/chronicle author chest validate
/chronicle author chest reload
```

Bind/rebind/unbind/reload require non-active author mode. Binding changes no items. **Bound loot containers are managed loot: each session-start or periodic fill replaces every existing item**, as requested. Use ordinary unbound chests for personal storage. Double chests and unresolved vanilla loot-table containers remain unsupported. Unbinding stops future management and preserves contents/history.

Each campaign registers defaults with `ChronicleCore.chestTypes().register(new LootType(...))`. Quiet Below supplies settlement, mine and ancient cache examples with advisory minimum counts. The menu and validator compare bound counts with recommendations; shortages do not block a session. The server owns editable `config/chronicle/chest-loot.json`, generated on first start. Example full type entry inside `types`:

```json
{
  "id": "quiet_below:settlement_supplies",
  "label": "Settlement supplies",
  "description": "Everyday supplies for this map.",
  "recommendedChests": 4,
  "refillMinutes": 15,
  "entries": [
    {"item": "minecraft:bread", "chancePercent": 60, "minCount": 2, "maxCount": 5},
    {"item": "minecraft:iron_ingot", "chancePercent": 25, "minCount": 1, "maxCount": 3}
  ]
}
```

The file root is `{"schemaVersion": 1, "types": [...]}`. Full entries override defaults by ID; additional server-defined types are allowed. Percentages roll independently once per entry: both, either or neither can appear. Quantities are uniformly selected between inclusive minimum/maximum. Optional `components` is a Minecraft item-component JSON object. Up to 27 bounded stacks fit the supported inventories; invalid IDs/components/counts reject the whole reload and leave the running configuration intact. `refillMinutes: 0` means new sessions only. Positive values count active occupied game time, pausing with the session and across downtime. A new session fills once; resuming/restarting does not create another start fill. Unloaded targets wait without forced chunks; only the latest due cycle is filled when available.

The scheduler journals the exact before/rolled-after inventories and uses the existing durable recovery controls. `/chronicle chest status` lists uncertain operations; `/chronicle chest recover <operation>` compares contents without blindly replaying or overwriting conflicts. See [the revised contract/review](M7_LOOT_TYPES.md). Existing captured profiles and their receipts remain available under `/chronicle author chest legacy ...`; they are a compatibility path, not a setup requirement.

Manual player moves remain separate:

```text
/chronicle author location save arrivals here
/chronicle spawn bind "chronicle_core:arrival" arrivals
/chronicle spawn inspect "chronicle_core:arrival"
/chronicle spawn preview "chronicle_core:arrival" PlayerName
/chronicle spawn confirm PlayerName
```

Saving a spawn binding changes no vanilla respawns. Runtime preview/confirm requires ACTIVE and validates loaded safe standing room. Automatic player policies are now a separate explicit opt-in step in **Policies**; merely binding a point still changes no respawn behavior. Entity encounter groups remain M7 follow-up; trigger bindings belong to M8.

### Chests and spawns must behave predictably

- Binding an existing chest records its location/type; it does not place or empty one. Capturing its contents creates a template without consuming or distributing those contents. Filling/resetting is a separate, previewable runtime action.
- Typed chests fill at each new session and optionally every configured active interval, replacing all contents by explicit operator choice. Legacy captured profiles retain their manual/once policies and empty-only behavior. Restart/retry must not duplicate loot. A missing/replaced chest waits for correction instead of silently creating a container.
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

## Acceptance criteria across M6â€“M8

- In one existing world, select two corners, save a named cuboid and see its outline without editing JSON, exporting a world or rebuilding the mod.
- Save an exact location/facing and bind an existing chest/custom station by targeting it. List/show/remove definitions; round-trip them across restart.
- Register/select a chest profile and spawn point/group in Core without importing Quiet Below classes. Rebinding geometry does not activate a story action by itself.
- Inspect locations in author mode without consuming discoveries. Reject unauthorized edits, cross-dimension selections, invalid schemas, broken references and stale target types.
- Separate harmless large-region registration from authorized block changes. A city-sized discovery region is allowed; it is not permission to transform every block or bypass M4's current stage limit.
- Specify/test inventory fill/reset and spawn execution separately: no loss/duplication on retry, no replacement of unapproved contents, safe spawn behavior and no automatic map reconstruction.
- Keep existing pause/freeze, public dossier privacy and conflict-preserving transformation behavior intact. Campaign effects stay dormant outside ACTIVE even though authoring remains available.

Backups and optional rehearsal copies remain operational tools, not the primary builder UX or a condition for defining a region. No new world-import feature is required for this slice. A general terrain editor and the campaign's autonomous growth engine remain separate work.
