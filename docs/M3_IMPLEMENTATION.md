# M3 content pipeline

## Scope

Add two small original campaign assets on the existing Minecraft 1.21.10/Fabric/Java 21 pins: `quiet_below:echo_lens` and `quiet_below:resonance_marker`. Keep common registration and interactions in the campaign mod; models, textures, recipes, loot and English names are campaign resources. Core stays campaign-independent. No additional third-party mod or account service is required.

The marker is a placeable, mineable stone-like block, with no block entity, automatic ticking or spreading. Empty-hand interaction offers a small observational clue. Using the lens on it during an active session provides more detail and restrained vanilla sound/particle feedback. Outside ACTIVE, story interactions report that the session is dormant without changing flags, blocks or inventory. Clue reads do not consume the lens or award combat bonuses. Server code chooses the result; clients cannot submit clue text or target coordinates through a custom payload.

Provide creative-tab entries and ordinary survival crafting recipes using vanilla materials. Mining with a pickaxe drops one marker. The marker is a thin relief panel that faces the player on floor placement or follows the clicked wall. It occupies the rear 3.5/16 of a block and has no ticking/block entity. No external texture pack is required.

## Assets and crafting

Clues and dormant feedback appear in normal chat, where long text wraps and can be read again. Session status retains the actionbar above the hotbar; long observations do not replace or overflow it.

The operator supplied `../Blockbench/ancient_symbol.bbmodel`, its exported JSON and 64×64 PNG. M3 reuses the texture and all 36 model elements/UVs as the Resonance Marker, changing resource identifiers and providing facing blockstates/item definitions. The source folder remains untouched. The original export's SHA-256 is `E7DF1DAFA946BC5922FF38A796BB2591397495783A7E51F79F03237408423A6A`; the PNG's is `29DB6377AC4E2AC4EE2A13BAC466ECEB112E0EE0CAA3B3C4CFC8E9B24EAEEA6E`. Reference assets in that folder were not imported. Larger focus/altar/forge/relic designs remain available for a future mechanic. No distribution license is inferred from their presence.

The Echo Lens has an original transparent 16×16 copper/amethyst texture generated reproducibly by `campaigns/quiet-below/tools/generate_m3_textures.py`. English names/clues are in the campaign's `en_us.json`.

Crafting patterns (spaces are empty): lens ` C /GAG/ C ` uses copper ingots C, glass panes G and an amethyst shard A. Marker `SCS/CAC/SCS` uses stone bricks S, calcite C and an amethyst shard A. Each recipe yields one item. Both appear in creative tabs and can be given by their namespaced IDs. Right-click the marker with an empty hand for an observation, or with the lens for more detail, while a session is active.

## Verification

1. Build/remap both mods and run existing campaign/profile tests.
2. Check item definitions, blockstates/models, loot, recipes, texture references and localization, including the pinned 1.21.10 resource layout.
3. Start a dedicated server with both mods and voice chat. Exercise placement/mining and lens/marker interactions with a real Fabric development client, including inactive/active states and server-owned results.
4. Inspect rendered item/block screenshots and check for missing-model/texture errors. Use a separate disposable world, preserving the play world and account permissions.
5. Preserve existing session/profile regression coverage. If custom registries exceed Mineflayer's capabilities, use real Fabric clients and fail explicitly rather than dropping packets or inventing registry entries.
6. Before live deployment, stop/save, back up and archive prior jars; generate the matching client pack and restart the preserved play world.

Only after M3 passes, implement M4's bounded, auditable transformation preview/trigger/undo. M5 supplies pilot setup and session notes. Human playtest feel and voice audio remain distinct from automated rendering and protocol evidence.
