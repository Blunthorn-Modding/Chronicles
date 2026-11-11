# Chronicle Core 0.1.0-beta

Initial public beta for Minecraft Java 1.21.10 on Fabric. This changelog describes the included features; it does not assume a previous public release.

- Session start, pause, resume and end controls, world-simulation freezing outside active play, and a global Building Mode.
- Public character cards, configurable Player Roles, starting equipment and spawn policies.
- An in-game operator editor, opened with **O** or **/chronicle**.
- Regions, saved terrain states, Locations, block sites and group departures.
- Editable loot tables, chest bindings, named lore books and discovery tracking.
- Events with triggers, prerequisites, delays, private previews, messages, audio, flag outcomes, encounters and Region changes.
- Reusable scenario-content export/import, with map bindings and live progress retained in the world.
- Automatic initialization of scenario defaults and setup diagnostics for incomplete Events.
- Incomplete default Events retain their IDs when renamed or saved. New custom Events still generate IDs from their names until an operator chooses an ID.
- Default-Event promotion is deferred during active play, including across server restarts.

Requires Fabric Loader 0.19.5+, Fabric API and Java 21+, installed on the server and every client. Core can be used independently; The Quiet Below is a separate optional campaign mod.

No map is bundled. Production-map and real-group testing remain outstanding. Back up worlds and configuration before upgrading; stored schema migrations can be permanent.

Original code/assets are MIT. Included SnakeYAML Engine retains Apache-2.0 notices.
