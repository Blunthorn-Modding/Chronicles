# Forge and enchantment-cap review

**Current Altar workflow:** [dev.33 workstation review](M11_ALTAR_REVIEW.md) supersedes the Altar screen/deployment instructions below. The old scrollable form and consent checkbox are historical. Forge remains unchanged pending its separate workstation redesign.

**Superseded deployment details:** [Dev.31 bundled review](M12_REVIEW.md) is current, including the physical Heart ending. The Forge/Altar workflows below still apply; dev.30 pack hashes and console IDs are historical.

## Current Altar bundle: dev.30,22 October

Join **127.0.0.1:25588** with the refreshed `../client/Chronicle-M9-1.21.10.mrpack`. Both Chronicle jars must matchdev30: the Altar block and tracked food attribute require a matching client. The pack now also installs the four approved client HUD dependencies from official Modrinth references; none of their jars/assets/code are embedded. GUI2/narrator-off/onboarding-off/multiplayer acknowledgement defaults remain. Existing map/tuning/authentication and SchinderRenge op4 are preserved; currently PAUSED. Older dev29 workflow below still applies to Forge.

1. While paused, use Creative to obtain/place an **Ancient Altar** directly beside living sculk in the existing Established/Extensive network. No Locations registration is necessary. No Altar was placed automatically in your map.
2. Return to Survival/Adventure, join participation, turn editing off and resume. Right-click the Altar. Read the choice, complete sacrifice/bonus and actual resulting stats in the scrollable panel. Check **I accept the permanent trade**, then **Accept bargain**. Refresh never accepts a bargain.
3. **Extra heart:** one maximum heart plus one extra food icon per purchase; natural regeneration needs the larger threshold. **Faster movement:** +10% base walking speed and one maximum heart sacrificed per purchase. Each can be bought three times independently. All three of both yield ten maximum hearts,13 food icons and+30% base walking speed. Buying does not fill new health/food. These are initial balance defaults for hands-on review.
4. Death and reconnect preserve both sides and counts. There is **no player reversal**. Review purchases are real lasting changes; use a stopped pre-purchase world/jars backup for a full rollback. Dev.31's physical Heart ending removes both sides via the Core source-closure primitive; see the current bundled guide above.
5. At26 food capacity the original native fallback shows13 icons over two rows. SpiffyHUD's editor can add **Chronicle expanded food**, move it and save the layout. This element replaces the fallback while rendering. Native hearts already reflect maximum health. At ordinary20 food, vanilla's bar is used. The editor does not own food/gameplay logic. Arbitrary element scaling and every other HUD mod are outside verified coverage.

Try the powered/unpowered wording, consent readability, original supplied Altar model and hunger/health/movement tradeoffs together. A paused/cut/distant/replaced station, Creative, editing or nonparticipant cannot accept a bargain. Your existing Forge recipes, caps, contact costs, healing and ecology controls remain available.

The complete stopped backup is `../backups/costs-pre-dev30-20261022T072130965571Z.zip`; its CRC and all57 protected file hashes passed. Seven startup-critical files are unchanged, status handshake is1.21.10/protocol773, and console4526 remains available. Pack SHA256 `6e80ad4038929f5b59b44138d8e2a37fa70a1906c2fe0db6fe3c8eb9670bbf6a`. [Machine-readable deployment evidence](../../reports/qb12-altar-upgrade.json). Prior production/review worlds are untouched.

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
