# Dev.14 Banquet feedback

The owner accepted dev.13 food poisoning and requested item-named feedback, loot-table supplies, a poison death message and a hunger investigation.

## Delivered changes

Poisoning displays `You poison the {item name}`, using the target's normal display name. Fatal Banquet damage uses its own damage type and localized `was killed by poison` message. The message keeps the perpetrator private.

In **Advanced > Loot Tables > Add item**, select **Poison dose** or **Antidote** and set the normal chance/count fields. Banquet drinks also support this path. Counts above one use separate slots with distinct supply identities.

Core provides a reusable loot-item factory registration. Factories prepare identities after book placement establishes final slots. After the exact chest intent is saved, the scenario saves its supply records before Core applies the inventory. Applied-fill recovery repeats authorization idempotently. Last Council registers its factories when its mechanics are active and owns the private supply ledger.

## Hunger findings

Core's active-Survival defaults match vanilla. Food capacity defaults to 20, the exhaustion multiplier defaults to 1, and the regeneration thresholds resolve to vanilla's 20/18 values. Banquet food delegates nutrition and saturation to vanilla eating. Pause, Building Mode and runtime holds intentionally freeze hunger.

The previous dev.13 Host pack's exported `level.dat` contained `GameType=1` (Creative) and `Difficulty=0` (Peaceful). Creative skips normal hunger depletion, and Peaceful replenishes hunger. Dev.14 exports the review world with `GameType=0` (Survival) and `Difficulty=2` (Normal). The pack builder checks those values in the finished archive.

The actual played world may have been changed after import. For an existing review world, `/difficulty normal` and `/gamemode survival` select the intended test conditions. Play must also be active with Building Mode off.

Local Minecraft 1.21.10 bytecode confirms ordinary walking and crouching add zero travel exhaustion. Standing adds none, and saturation is consumed before visible food points. Quiet roleplay can therefore take a long time to require food under vanilla rules. This update changes the review-world defaults and leaves the active-play hunger rate at vanilla values.

Relevant implementation is in `ExpandedHungerMixin`, `FoodCapacity`, `OfferingAttributes`, `SessionGameplay`, `BanquetRuntime.finishFood` and `scripts/council_lan_pack.py`. Offering modifiers can increase exhaustion, with a default multiplier of one.

## Verification

Full and focused offline builds passed, including 993 Java tests with seven existing skips (986 passes). All 134 Python tests passed. Build logs are retained at `../reports/dev14-build1.txt` and `dev14-build2.txt`.

The two-client native run at `../reports/council-banquet-20261117T084149881521Z/` exercises a real typed-chest fill with three poison doses and two antidotes, uses those supplies, checks exact item-named client feedback and verifies the actual poison death chat text. It also retains the food, save-failure recovery, clock, antidote and restart checks from dev.13. The complete run passed both restarts. Pack CRC, exact mod jar bytes and Survival/Normal NBT defaults passed validation in `../reports/dev14-pack-validation.json`. The stopped generated fixture was removed with compact reports retained in `../reports/dev14-cleanup.json`.

Independent review caught final-slot identity ordering and inactive-scenario registration issues. Both were corrected and the focused re-review was clear.
