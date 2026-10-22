# Ancient Forge workstation review - dev.36

Join **127.0.0.1:25589** with the updated [client pack](../../client/Chronicle-M9-1.21.10.mrpack). Reimport/update it in your launcher: the Forge now uses a new native screen handler. GUI scale2 and the narrator/startup/multiplayer defaults remain included.

The supplied Forge is **10,65,23**, beside the accepted Altar. A materials barrel is **11,65,25**. Use `/tp @s 10.5 65 26.5` if needed. The same test world, tuning and accepted prayers are retained; the server starts PAUSED.

1. Join participation (`/chronicle join`), use Survival or Adventure, turn editing off and resume using Session. Give the living network a moment to settle, then right-click the Forge.
2. Put a sword in the upper input and an echo shard in the lower input. The right slot previews the same sword with Sharpness I. Hover the result to inspect it, then take it. One shard is consumed, with no XP price. Try shift-clicking inputs/result and closing with unused material still staged.
3. Open **Recipes** to see the material, amount and resulting enchantment. Back/Escape returns to the same workstation and retains staged inputs. Check readability and ordinary item tooltips with your resource pack.
4. Try the other supplied targets/materials: amethyst -> Unbreaking I, prismarine -> Protection I, flint -> Power I, feather -> Feather Falling I. Plain books also work. Incompatible/already enchanted targets consume nothing.
5. Pause or sever the marked network connection at -3,64,0 while the Forge is open: output should disappear and real inputs remain recoverable. Resume/reconnect restores power after the network settles. Creative/nonparticipation/editing/distance cannot bypass the gate. Removing/replacing the Forge closes the old interaction.

Please review the **Forge layout, material discovery and actual input/result handling**. The Altar remains accepted. The size-rate comparison, physical pickaxe Heart/lens changes and configuration placement follow separately in [the review queue](POST_M12_REVIEW.md).

The reusable Core [transaction contract](CF11_FORGE_WORKSTATION.md) owns temporary slots, recipes, caps and exact output delivery. Quiet Below owns intrinsic block identity/power and original models. Closing/disconnecting uses native inventory return/drop rules; a full inventory may drop unused staged inputs at your feet. Ordinary vanilla persistence applies; there is no separate crash-atomic item ledger.
