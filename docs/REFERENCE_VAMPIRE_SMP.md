# Vampire SMP authoring reference

Inspected locally on 19 October 2026 at the operator's request. Source: `../VampireSMP-1.1.2.jar`, SHA-256 `d16b71a6d5d5464fe048239aa69dd0fe56f5c708551c81ee0c89ddc3b0a121fb`. The filename says 1.1.2; the embedded `plugin.yml` says version 1.0.0. These findings identify this exact supplied artifact, not an upstream release guarantee.

Method: read packaged `plugin.yml`/`config.yml` and inspect selected class signatures/bytecode with the portable JDK's `javap`. The Paper plugin was not installed, executed or copied into Chronicle. No behavior was runtime-tested.

## Observed authoring patterns

| Evidence in the supplied artifact | Relevant pattern |
| --- | --- |
| `CommandHandler.handleBeaconAdd` captures the player's location, accepts a site name/optional radius and saves it through `BeaconManager`. `BeaconSite` stores world name, coordinates and capture radius. | Stand at a meaningful place and register a named site; behavior subsequently resolves that site. |
| `PowCommand` admin help exposes add/remove/list tome-chest locations. `handleAddTomeChestCommand` captures the player's location; `ConfigManager` exposes persistent chest location management. | Builders register content points in-world rather than embedding map coordinates in source code. The add command can place a chest in this reference; Chronicle should separate binding from any explicit placement action. |
| `handleSetVampireSpawnCommand` accepts coordinates or the player's current location and persists it. | Capture a spawn point directly during map authoring. Chronicle should make the player/group and spawn purpose explicit rather than assume one global vampire role. |
| `ConfigManager.validateConfiguredLocations` and beacon list/info/validate/reload handlers exist. | Builders need inspection, validation and persistent configuration alongside registration. |

The inspected entry points demonstrate named points and radius-based beacon sites. A WorldEdit-style two-corner selector is the **operator's requested Chronicle workflow**, not a feature established by this inspection. The package also contains map-specific defaults and a force-load helper; neither is a behavior Chronicle should inherit from a reference about authoring UX.

## Application to Chronicle

Adopt the interaction pattern: build the place, select/register it in-game, bind a purpose, inspect and save. Put reusable regions, location/block anchors, chest/spawn configuration and authoring permissions in Chronicle Core. Keep sculk progression, forge recipes and campaign-specific effects in Quiet Below. Use Fabric-native implementation; this reference does not call for a Paper compatibility layer, a wholesale port, copied faction rules or its fixed map coordinates.

See the revised [in-world authoring plan](MAP_AUTHORING.md). No third-party source, plugin binary or new dependency is included in the repository by this documentation change.

## Chest-distribution reinspection - 19 October 2026

Rechecked the same supplied artifact after the operator rejected capture/template-first authoring. `CommandHandler.handleAddTomeChestCommand` calls `TomeDistributionManager.addTomeLocation` with the player location, saves the list through ConfigManager and places a chest at that location. `ConfigManager.addTomeChestLocation` rejects duplicate coordinates and saves `tome-chests.locations`. This supports the desired one-step location/purpose workflow rather than Chronicle's separate anchor/template prerequisites.

The packaged config has `tome-distribution-interval-minutes: 20`, a chest location list, and `cure_books_spawn_chance: 0.3`. `TomeDistributionManager.distributeTomes` returns unless the reference session state is active, clears registered tome chests, selects random locations/types, distributes tomes, adds enchanted books to remaining locations, then applies its cure-book chance. This is a scenario-specific distribution algorithm, not an existing generic percentage-per-entry profile editor. The findings come from config and `javap -c -p` bytecode, not from running the Paper plugin.

Chronicle adopts direct typed binding plus separately editable distributions. The operator explicitly confirms independent percentages, fills at a new session, optional periodic refills and replacement of **all** bound chest contents. Core supplies the registry/config/scheduler; campaigns supply defaults and recommendations. No reference binary, fixed coordinates, factions, forced loading or copied source is installed.
