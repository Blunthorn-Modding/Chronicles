# Ancient Forge - ritual review, dev.39

The operator approved the [design](M11_FORGE_RITUAL_DESIGN.md). This replaces the
old horizontal Target/Material/Result screen and recipe browser. The review
server is **127.0.0.1:25589**, initially **PAUSED**. Reimport/update the
[matching client pack](../../client/Chronicle-M9-1.21.10.mrpack). GUI scale 2 and
the narrator/startup/multiplayer defaults remain included. Deployment readiness
and exact automated evidence are recorded in the newest [TEST_PLAN](TEST_PLAN.md).

The supplied Forge is **10,65,23**, beside the accepted Altar. Materials are in
the barrel at **11,65,25**. Use `/tp @s 10.5 65 26.5` if needed. Preserve the same
world, tuning and accepted prayers. The exhausted support from the previous
review may need roughly 75 active seconds plus assessment time to recover;
[station support feeding](M11_STATION_FEEDING.md) fixes its previous self-sealing.

1. Join participation (`/chronicle join`), use Survival or Adventure, turn editing
   off and resume using Session. Right-click the Forge once the network settles.
2. Put either a sword or echo shards in the **top slot**, then click the Forge
   illustration. The slot clears and its opening stirs gently. Place the other
   item in the same top slot, then click the Forge again. After a short working
   animation the enchanted sword appears in the **bottom slot**. Collect it
   normally or with shift-click. Offering order should make no difference.
3. Check the anchored inventory tray, normal-sized items and tooltips at your
   normal window size. The ready Forge illustration is kindling, unavailable is
   dark, working is active. Offered items stay hidden inside it. There are no
   labels, power/status paragraphs or Recipes button.
4. Try amethyst -> Unbreaking I, prismarine -> Protection I, flint -> Power I,
   feather -> Feather Falling I, or a plain book. One configured ingredient is
   consumed; the remainder returns. Incompatible/already-maxed combinations
   return unchanged after the same brief ritual. Destructive salvage is pending
   a separate decision; future discovery books belong in loot.
5. Close after one offering or during work: all inputs must return once. Pause
   or disconnect the network before completion: input recovery must remain
   safe. After completion, a power loss must leave the owned result collectable.
   Closing with a result also recovers it; a full inventory may drop items at
   your feet. Removing/replacing the Forge closes the old interaction.
6. Outside the menu, the existing powered Forge model remains active and emits
   light between crafts. Half the Established threshold shows kindling; below
   that or disconnected/dormant is off. The model tracker can take up to 128
   ticks when full; the recipe always checks power synchronously.

Core owns [transactions and native inventory](CF11_FORGE_WORKSTATION.md);
Quiet Below supplies the original model-derived artwork and existing gate.
The native popup, slot and font components, Core tray sprite and three campaign
Forge GUI sprites can be replaced by resource packs. No third-party artwork is
bundled. Normal Minecraft persistence applies; this is not a crash-atomic ledger.

Please review the complete sequential interaction and appearance together. The
Altar remains accepted. Count-rate comparison, physical pickaxe Heart/lens and
configuration placement follow separately in [the review queue](POST_M12_REVIEW.md).
