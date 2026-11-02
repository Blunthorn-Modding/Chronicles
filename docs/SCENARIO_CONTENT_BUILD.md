# Scenario content build input

This page describes raw content exports for the next mod build. A **custom runtime profile** is a separate typed envelope installed on a dedicated server as `config/chronicle/scenario-profile.json`; see [custom authoring](SCENARIO_CONTENT_EXPORT.md). Do not pass a runtime profile to the mod-build importer.

Scenario content is a reusable build input. It contains roles, books, loot tables, encounter-group compositions, and disabled Event templates. Event encounter targets remain symbolic map binding names only; it never contains Regions, Locations, chest bindings, map settings, coordinates, dimensions, player data, session progress, receipts, snapshots, or a runtime Event schedule.

Validate a captured editor export before review:

```powershell
python scripts/scenario_content.py validate 'E:\exports\quiet-below.json'
```

After review, copy the same export into the Quiet Below source resource for the next normal mod build:

```powershell
python scripts/scenario_content.py import 'E:\exports\quiet-below.json'
```

The import task requires an absolute input path, runs offline, strictly decodes bounded UTF-8 Scenario JSON, canonical-round-trips it, and writes only `campaigns/quiet-below/src/main/resources/data/quiet_below/chronicle/scenario-content.json`. It refuses symlinks, invalid input, and an invalid existing resource; it uses a forced atomic replacement for a valid reviewed resource. The resource is source-controlled, so no `.bak` is created.

Equivalent Gradle commands are:

```powershell
.\gradlew.bat --offline --no-daemon :campaigns:quiet-below:importScenarioContent -PscenarioContentInput='E:\exports\quiet-below.json' -PscenarioContentValidateOnly=true
.\gradlew.bat --offline --no-daemon :campaigns:quiet-below:importScenarioContent -PscenarioContentInput='E:\exports\quiet-below.json'
```

At startup, Quiet Below calls `QuietBelowScenarioDefaults.loadOptional()` and, when present, registers its definitions before the Core catalogues freeze. The absent resource keeps the existing Quiet Below defaults. A packaged resource supplies the reusable defaults; existing world/operator overrides remain authoritative. Event templates appear in the Events template picker and require explicit adoption, target-map review and enabling. Loading the resource never applies map geometry or enables an Event.

Event flag IDs and logical target names remain references. Create any missing flags on the target map and review sources, prerequisites, Region states and encounter destinations before enabling a template. Current flag values and other campaign progress are excluded from the export.

Regions, Locations, chest bindings, saved Region states and role destinations travel with the world. In particular, role overrides now live in `world/chronicle/player-roles.yaml`; the old server-level role YAML is a read-only bootstrap source for an uninitialized world. Copy the complete stopped world when moving a map. See [role map storage](ROLE_MAP_STORAGE.md).
