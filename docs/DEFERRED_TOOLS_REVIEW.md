# Combined deferred-tools review

This review covers the new encounter editor, scenario-content export and extra Altar prayers together. The automated storage, restart and recovery checks are recorded in the test plan; the remaining human review concerns clarity, feel and balance. Final map, journal and character content remain organizer-supplied.

The dev65 review uses `127.0.0.1:25589` and the matching updated client pack. It starts paused. Join participation under Players and resume play under Session to use the prayers; paused play deliberately blocks workstation/container interaction. The Altar is at `2 65 3`, the Forge at `6 65 3`, and a supplies chest at `4 65 -3` contains bread, XP bottles and damaged equipment. This is a disposable test plot. The older review world is retained.

## Editor

Use the matching client pack and open `/chronicle` or **O**.

- **Advanced → Encounter groups**: create a group, add permitted entity types and counts, and choose an existing Point or Region. Saving a group never spawns it. In an Event, **What happens → Add action → Spawn encounter group** attaches it. Private preview plays media only. Choose a clear, loaded destination; larger mobs need more space. `/chronicle encounters` opens the same page.
- **Events → When**: `Role assigned` observes a real changed assignment during active play; choosing the same role does nothing. `Role first entry completed` observes the completed once-only starting gear/lore/destination operation. Neither trigger grants a role or starting kit itself.
- **Game setup → Event history**: when play is non-active and operations are resolved, the confirmed archive action frees live history capacity. Exact retry evidence and progress remain. `/chronicle maintenance events` invokes the same maintenance service.

Interrupted encounter effects retain their exact planned UUIDs and stages. Inspect and correct the world before explicitly accepting an uncertain result. Acceptance preserves that result; it never recreates missing entities or finishes the remaining spawns.

## Altar

The first sheet keeps the two accepted lasting prayers. **More prayers…** opens four additional mysterious prayers without inventory slots or public numerical prices.

For a practical review, bring damaged equipment in the activating hand, at least three XP levels and sixteen bread. A second injured participant is needed to test shared healing. Initial organizer terms are documented in [temporary Altar offerings](TEMPORARY_ALTAR_OFFERINGS.md); they can be tuned in the server configuration. These are provisional balance defaults.

Repair and cave-sight sacrifices survive death and reconnecting until occupied active-play expiry. Pause, empty-server time and downtime do not spend their timers. Ending the network removes ongoing benefits and sacrifices; consumed offerings and completed repairs/healing remain.

## Reusable scenario content

Edit roles, books, loot tables, Events and encounter compositions in their existing editors. Pause play, enable Editing and choose **Scenario content…** in the header. Review the counts and export. The server returns a controlled file path for the next build; it does not modify the installed mod.

Use the [build/import workflow](SCENARIO_CONTENT_BUILD.md) to validate that file and package it in the next Quiet Below build. Imported Event templates require explicit adoption and target-map review before enabling. Installed scenario defaults do not overwrite existing operator/world overrides.

Regions, Locations, chest bindings, saved Region states and role destinations stay with the complete world folder. Role definitions and their map destinations now live in `world/chronicle/player-roles.yaml`; the former global YAML is only a guarded first-world bootstrap input. See [map storage](ROLE_MAP_STORAGE.md). Export excludes map geometry, settings, player assignments, discoveries, histories and live progress.
