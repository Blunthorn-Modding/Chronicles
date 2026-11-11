# Chronicle 0.1.0-alpha.1

First public alpha for Minecraft Java 1.21.10, Fabric Loader 0.19.5 or later,
Fabric API 0.138.4+1.21.10 and Java 21 or later. Install the same versions of
Chronicle Core and The Quiet Below on the server and every client.

Chronicle Core provides session and Building Mode controls, character cards,
Player Roles and starting equipment, Regions and saved terrain states,
Locations and departures, configurable chest loot, Books & Lore, Events and
flags, encounters and reusable scenario-content export/import. The operator
editor opens with **O** or `/chronicle`.

The Quiet Below adds a Heart-connected sculk ecology, visible vitality states,
configurable growth and contact benefits/costs, the Ancient Forge and Altar,
and a destructible Relic Heart with a last stand. Defaults include six roles,
nine books, nine authored Events and four editable loot tables. Events and
flags appear on fresh maps; map-dependent Events show `!` until configured.
Prepared maps retain their saved bindings and progress. The obsolete
Readiness Check and Mine Whisper prototype actions are removed.

The two remaining default-Event checks now cover promotion being deferred
during ACTIVE play across a real server restart and editing/saving an
incomplete default through the ordinary native editor, including persistence
after restart.

Renaming an incomplete default in the Events editor now preserves its supplied
ID. New custom Events still generate IDs from their names until the operator
explicitly chooses an ID.

Original Chronicle code and assets are MIT. Bundled audio, the included
SnakeYAML Engine library and Minecraft-derived artwork retain their own
notices; see LICENSE, NOTICE and the audio credits inside the mod.

This is an alpha: the handcrafted campaign map is not included. Map-specific
balance, discovery placement and group/voice playtesting remain outstanding.
Dedicated Survival, Journey and hidden-faction engines are future work.
Back up existing worlds and server configuration before upgrading; stored
schema migrations and configured enchantment-cap reductions can be permanent.
