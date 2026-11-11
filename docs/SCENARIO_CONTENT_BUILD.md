# Scenario content build input

This page describes raw content exports for the next mod build. A **custom runtime profile** is a separate typed envelope installed on a dedicated server as `config/chronicle/scenario-profile.json`; see [custom authoring](SCENARIO_CONTENT_EXPORT.md). Do not pass a runtime profile to the mod-build importer.

Scenario content is a reusable build input. It contains roles, books, loot tables, encounter-group compositions, initial flag definitions and Event templates. Event encounter targets remain symbolic map binding names only; it never contains Regions, Locations, chest bindings, map settings, coordinates, dimensions, player data, session progress, receipts, snapshots, or a runtime Event schedule.

Dev68 writes schema4 with initial flag definitions; schemas1–3 remain readable. Dev67's occurrence policies retain their existing meanings. Exported flags always start Off, independently of the live campaign's values. Recurring templates support immediate source-triggered text/sound, including a triggering-player audience. Claims, discoveries and per-player history stay in world progress and are never exported. Bundled audio remains a mod asset referenced by sound ID. See [automatic defaults](AUTOMATIC_SCENARIO_DEFAULTS.md) and [the final Quiet Below defaults](FINAL_DEFAULTS_GUIDE.md).

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

At startup, Quiet Below calls `QuietBelowScenarioDefaults.loadOptional()` and registers packaged definitions before the Core catalogues freeze. The absent resource keeps the earlier fallback content. Core automatically lists scenario Events and declares missing flags. Ready defaults become ordinary enabled Events; incomplete map-dependent defaults show `!` with a setup reason in the main Events list and use the existing editor. No import or adoption step is required. Loading or previewing content does not apply terrain or fire an Event. Saved configurations, flag values and completion history take precedence; deleted defaults are not restored on restart.

Declared flags and flags referenced by default prerequisites/outcomes are initialized only when missing. An Off flag is valid progression; setup warnings identify incomplete linked updaters. Logical target names remain map references: configure Regions, saved states and encounter destinations as needed. Current flag values and other campaign progress are excluded from the export.

Regions, Locations, chest bindings, saved Region states and role destinations travel with the world. In particular, role overrides now live in `world/chronicle/player-roles.yaml`; the old server-level role YAML is a read-only bootstrap source for an uninitialized world. Copy the complete stopped world when moving a map. See [role map storage](ROLE_MAP_STORAGE.md).

The storage split is unchanged: reusable content/settings come from the scenario mod with server configuration overrides. To distribute reusable edits, export them and include them in the scenario mod; copying a world does not bundle server-specific configuration. A prepared world plus its matching prepared mod supplies the two parts of the setup.
