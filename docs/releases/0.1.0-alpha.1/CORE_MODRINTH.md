# Chronicle Core

Reusable tools for Minecraft roleplay campaigns, with an in-game operator editor.

Chronicle Core provides the shared foundation for campaigns such as **The Quiet
Below**. Configure your own map and content in game rather than rebuilding a
mod for each campaign.

- Manage active play, pauses and a global Building Mode.
- Create character cards and Player Roles with equipment, lore and spawn points.
- Select Regions with the authoring tool and save alternate terrain states.
- Bind Locations, chest loot and group departures.
- Write and distribute named lore books with campaign-wide discovery rules.
- Create Events with triggers, prerequisites, delays, messages, sounds,
  flag updates, encounters and saved Region changes.
- Export reusable scenario content while keeping map bindings and campaign
  progress with the world.

## Installation

Use **Minecraft Java 1.21.10**, **Fabric Loader 0.19.5+**, **Fabric API** and
**Java 21+**. Install Chronicle Core on the server and all players' clients.
Open the operator editor with **O** or `/chronicle`; editing requires operator
permission. A scenario mod supplies its own mechanics and starting content.

Simple Voice Chat is recommended for proximity roleplay. SpiffyHUD and
FancyMenu are optional client integrations; Core has a native fallback. Use
the authors' official downloads. None of these mods are bundled in this JAR.

## Alpha status

This is an early public alpha. The main tools are implemented and have
automated/native regression coverage; testing with a real campaign group
and production map is still needed. Back up your world and configuration
before upgrades. No prepared map is included.

Source and issues: https://github.com/TheOnlyAesir/Chronicles

MIT for original Chronicle code/assets. SnakeYAML Engine is included under
Apache-2.0; its license and the project NOTICE are packaged in the mod.
