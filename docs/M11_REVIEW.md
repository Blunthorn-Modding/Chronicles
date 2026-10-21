# Forge and enchantment-cap review

Dev.29,21 October2026. Authenticated review: **127.0.0.1:25588**, voice24468, paused. Existing test map and operator tuning are preserved. Import the refreshed `../client/Chronicle-M9-1.21.10.mrpack` into your launcher, or replace both Chronicle jars in the existing instance with matching dev.29 builds. The Forge introduces a block registry, so an old client is insufficient. GUI2/narrator-off/onboarding-off/multiplayer acknowledgement defaults remain included. Production25565 and prior review worlds are unchanged.

## Forge workflow

1. While paused, obtain an **Ancient Forge**, an iron sword and echo shards through Creative inventory or `/give`. Leave the Forge item in a hotbar slot for placement. Use ordinary connected sculk in the current test map; no Locations registration is required.
2. Place the Forge directly beside or above living sculk. It needs a living Heart-connected **Established or Extensive** network. Dormant, detached, replaced or unloaded contacts do not supply power.
3. Use Survival/Adventure, join participation with `/chronicle join`, and resume play through Session. Editing mode must be off. Creative can inspect the screen but cannot enchant.
4. Hold one iron sword in the main hand, echo shards in the offhand. Right-click the Forge. The screen shows the target, exact offering and **Sharpness I** result. Confirm Enchant; the same sword retains its name/damage/components and one shard is consumed. No XP is charged. A full inventory is fine.
5. Try an already-enchanted sword, a wrong ingredient or a cut connection. No valid improvement/power means no consumption. Closing, refreshing or repeated clicks must not duplicate a trade. Place/reconnect physical sculk to restore power naturally.

Default mappings: echo shard→SharpnessI, amethyst shard→UnbreakingI, prismarine shard→ProtectionI, flint→PowerI, feather→Feather FallingI. Compatible equipment and single plain/enchanted books are accepted; stacked targets are rejected. The recipe list is scrollable. Model states are idle, ready kindling, and brief working fire; no stored station inventory exists.

## Organizer controls

Game setup > **Enchantment caps** controls server-wide maxima and per-ID override reloads. The Quiet Below first-file default isI, including vanilla tables/anvils/loot and existing gear/books. Stored reductions are permanent: raising or disabling a cap never restores old levels. The stopped pre-dev29 backup is the rollback, together with matching old jars/configs.

Game setup > **Ingredient enchanting** reloads `config/chronicle/ingredient-enchantments.json` from this review runtime, not from a launcher or another server. It is a complete editable recipe list; see [the contract/schema](M11_FORGE.md). Edits affect new offers. Native compatibility and current caps still limit every grant.

Feedback can cover workshop wording/layout, supplied model orientation/scale, power readability and default recipes together. Altar bargains/HUD and Heart eradication are still separate implementation work. Confirmed Altar direction is up tothree of each lasting bargain, surviving death/reconnect without player reversal, with both sides removed on eradication.

## Preserved deployment

No players were online. A saving shutdown preceded CRC/hash-verified `../backups/costs-pre-dev29-20261021T202433874451Z.zip`. All55 protected world/config/auth files matched during jar replacement; seven startup-critical files and canonical server properties matched after startup. The live ecology journal has a native exclusive writer lock, so only its stopped preservation/backup hash is asserted. Current console47923; `../reports/qb12-forge-upgrade.json` records readiness and exact artifact/pack hashes. Classic pack SHA256: `35b79698e9eb04e258b5f4e852462fbb790ee53f689bc708c45247dced4aa212`. The separate HUD prototype is unchanged.
