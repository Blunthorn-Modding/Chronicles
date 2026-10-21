# QB-08: dormant inventory and placement

The operator accepts dev.20 and requests that harvested sculk appear inactive in inventory, with placement deriving its appearance from the network. Implement on `fix/qb08-dormant-items`.

- Quiet Below supplies dormant inventory models for the five supported vanilla sculk items. Inventory has no network connection; all stacks, including existing stacks and creative stock, show the same static brown model. Keep vanilla item identities, stacking, loot tables, Silk Touch requirements and XP behavior. Do not carry health, ownership or summon capability in item components.
- In the configured campaign dimension, item placement starts unowned sculk brown, preserving vanilla shape, waterlogging and placement rules. Extending an owned vein preserves its current derived appearance; extending an unowned vein makes it dormant. Normalize the cosmetic property again after vanilla item block-state components are applied, so an item cannot pretend to be healthy. Outside the campaign, native world placement remains unchanged.
- Existing bounded physical adoption and appearance reconciliation then determine the placed block's network appearance during eligible play. Connected placements join as before; disconnected placements stay dormant. Paused/held play does not activate them. This does not introduce new vitality transfer, regeneration rates or Warden rules.
- No new blocks, block entities, entities, registry layout or journal schema. Existing dev.19/20 clients remain protocol-compatible but require refreshed assets for the dormant inventory models.

Verify actual survival harvesting with Silk Touch and without it, vanilla item/component/count behavior, all five placed types, disconnected inactivity, connected recoloring, multi-face/waterlogged placement and ordinary outside-campaign behavior. Check actual rendered inventory models. Build, scripting tests and relevant native ecology/appearance checks precede a saving stop, protected review backup/upgrade and matching client pack. Preserve the operator's map/tuning and production runtime; leave the review paused. Commit/push the scoped branch without merging main.

## Delivered for review

Dev.21 is installed in the preserved authenticated localhost:25585 review, PAUSED with the operator's map and tuning intact. Import the refreshed `../client/Chronicle-M9-1.21.10.mrpack`; dev.19/20 clients connect safely but show their old inventory art. The stopped world/config/auth and old jars have separate CRC backups. Production is unchanged.

195 Java / 28 scripting / 150 ecology-items-restart native / 61 appearance-legacy-compatibility checks pass. Native hotbar/held dormant models and disconnected brown placements were visually inspected. See TEST_PLAN for the failing pre-repair survival regression, exact commands/evidence, preservation checks and validation limits. The next gate is operator pickup/placement review; M9 itself remains in progress.
