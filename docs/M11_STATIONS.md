# M11 station implementation preparation

**Dev.31 update, 22 October:** Forge, the two lasting Altar trades, authoritative stats/food/HUD and physical Heart ending are implemented and technically verified. [Current bundled review](M12_REVIEW.md). Earlier preparation/status paragraphs are historical; temporary Altar proposals and gameplay balance are still follow-ups.

**22 October:** [dev30](CF12_LASTING_BARGAINS.md) implements/verifies/deploys the intrinsic Altar, two three-use lasting trades, Core ledger/stats/food and original editable HUD. [Current review](M11_REVIEW.md). Forge/caps remain installed. Temporary Altar options and physical Heart ending remain separate work; older preparation wording below is historical.

**Current checkpoint:** intrinsic Forge and shared ingredient enchanting are implemented, verified and live in dev.29; [contract](M11_FORGE.md), [review](M11_REVIEW.md). The preparation language below is historical for Forge. Altar is next: THREE of each lasting bargain per character, independent counts, surviving death/reconnect without reversal; eradication removes bonus and sacrifice together. Its durable transactions, stat projection and truthful expanded-food HUD still require implementation and native verification.

21 October 2026. M10 healing and sustained contact costs precede this slice. The operator specifies item-to-enchantment mappings (e.g.echo shard -> Sharpness), with level I defaults and configurable maxima owned by Core. They explicitly require caps everywhere, including vanilla enchanting/anvils/loot. [Permanent Core caps](CF11_ENCHANTMENT_CAPS.md) are implemented and natively verified in dev.28; reusable mappings and physical Forge gameplay follow. The Altar should offer voluntary sacrifices for bonuses; the [offering options](M11_ALTAR_OPTIONS.md) are accepted as the direction, with numerical balance/stacking still to specify; lasting bargains survive death/reconnect, have no player reversal, and lose both bonus/cost after Heart eradication. Altar gameplay is not implemented yet. Selected SpiffyHUD/FancyMenu HUD integration does not block independent Core enchanting work; [licensing/prototype contract](THIRD_PARTY_HUD.md).

## Asset readiness

Original user-owned exports remain in `../Blockbench`, untouched. Local JSON inspection finds native Java-block models, valid native rotation angles, and texture references needing namespaced remapping before import:

| Source | Elements | Model bounds | Texture source |
| --- | --- | --- | --- |
| RelicHeart.json |32|0..16 high; x0..16,z0.499..15.501|relicheart.png,64x64|
| purification_altar.json |132|x/z-3..19,y-0.001..19|purification_altar.png,128x128|
| purification_forge.json |115|x/z-3..19,y-0.001..19|purification_forge.png,128x128|
| purification_forge_kindling.json |115|same|purification_forge_kindling.png,128x128|
| purification_forge_active.json |115|same|purification_forge_active.png,128x128|

Heart export uses `block/texture`; Altar uses unnamespaced `purification_altar`; Forge variants reference their matching unnamespaced texture. Copy final exports/textures into the campaign resources with `quiet_below:block/...` references and a particle entry; CI/builds must not depend on the parent workspace assets. Preserve source models and attribution/provenance. The decorative overhang exceeds the base cube; verify real facing, neighboring placement, collision and held/inventory rendering rather than assuming the model bounds are the collision box. Heart's export has no display transforms; check native defaults visually.

## Shared and campaign responsibilities

Core retains generic location/anchor metadata and operator tools. Placed campaign stations should carry their function intrinsically; no mandatory manual registration of every Forge or Altar. A vanilla/custom decorative anchor may still be registered through Locations for campaign context. Any reusable placement-registration prompt belongs to Core, while station power/recipes and unique Heart rules belong to Quiet Below.

Forge power derives from actual adjacent connected living ecology and its configured extent, with loaded-only guarded checks. It must not become a relay/node that enables growth, acquire unlimited power from a decorative copy, or require a sculk route authored by the builder. Show distinct unpowered/ready/working states using the supplied variants. No global scan of all station positions or per-sculk block entity.

The old illustrative Efficiency III recipe is superseded. Forge ingredients now map to specific enchantments, default level I, respecting compatible targets and existing enchantments. Core owns reusable mapping/configuration and server-wide maximum-level enforcement, including generated loot; Quiet Below owns physical Forge power and campaign default mappings. Before code, define accepted tool components and preservation, exact inventory/XP mutation, rejection of existing enchantments, output delivery, Creative semantics, pause/power-loss interruption and duplicate/concurrent clicks. Prefer one server-confirmed transaction over an asynchronous process needing distributed crash recovery. Tests must include full inventory, components/durability/custom names, insufficient inputs/XP, stale state, double click, simultaneous users, interruption and restart; never silently consume without output.

The Altar is now a sacrifice-for-bonus station; its accepted options and proposed implementation order are in M11_ALTAR_OPTIONS.md. It should remain distinct from passive healing. Ancient Focus remains removed, and RelicShard remains optional with no mandatory Heart-destruction key.

## Heart and review boundaries

The unique Heart appearance can show network state, but weakening/destruction and terminal cleanup are M12. Preserve the pinned root and existing worlds; specify migration from the reinforced-deepslate test placeholder before replacement. Ordinary placing or copying a Heart model must not create a second root or bypass ending safeguards.

New block/item registries and models require a matching client pack and actual rendered review. Verify schema/reader rollback with a stopped backup and matching jars; no in-place review-world upgrade without saving shutdown and backup. Batch station rendering/recipe feedback where possible. The next stop is a real product/visual decision needed to continue, not a feature being ready by itself.
