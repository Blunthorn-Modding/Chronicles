# Altar offerings: accepted direction

21 October2026. The operator wants a place of voluntary sacrifice for a concrete bonus, including the example of an extra maximum heart with a larger hunger bar and more food required before natural regeneration. The operator accepts the following options as the design direction. They are not installed gameplay; numbers remain illustrative balance defaults, and acceptance of the options does not settle every stacking, reversal or ending rule.

| Option | Sacrifice / ongoing cost | Bonus | Why choose it |
| --- | --- | --- | --- |
| Extra heart | Hunger capacity rises by one full food icon; regeneration threshold rises with it | One extra maximum heart | The operator's example: more health with greater feeding requirements |
| Faster movement | One maximum heart while this bargain is held | A small permanent movement-speed increase, e.g.10% | Clear mobility versus survivability choice |
| Emergency healing | The donor loses two current hearts; cannot reduce the donor to death | Immediate healing shared among nearby other participants | A visible voluntary support role, distinct from passive healing |
| Repair gear | One maximum heart is unavailable for20 eligible minutes; one outstanding repair bargain at a time | Restore part of one held tool/armour piece's durability, e.g.25% | Pays vulnerability for preserving scarce equipment; creates no enchantments |
| Cave sight | Pay XP and accept greater food consumption for the buff's duration | A few minutes of Night Vision | Useful underground without changing species, identity or Warden warnings |
| Settlement harvest | Consume a meaningful communal food/material offering and accept faster local ecological expansion | A temporary stronger nearby crop bonus | Helps the group while strengthening the sculk it may later wish to contain |

Recommended first implementation slice: Extra heart, Emergency healing and Repair gear. They have distinct understandable costs, clear interfaces and no invented prophecy, personality change or compulsory faction. Settlement harvest is a later option because tuning additional growth must preserve the existing physical frontier and barriers; it must not seed remote sculk or require authored routes.

## The hunger example

Translate one full heart/icon explicitly: health20->22 points (10->11 hearts), food20->22 points (10->11 icons), ordinary regeneration threshold18->20. The fast full-saturation regeneration path must use the new full-food condition too. A larger bar is also extra food storage; this alone does not prove greater ongoing upkeep. If the intended price is consistently higher food consumption, separately tune exhaustion from healing or food efficiency and show that cost. Do not silently replace the operator's visual-capacity idea with a hidden hunger debuff.

## Consent, stacking and ownership

Guardrails: show the complete trade before acceptance; preview actual resulting stats; cap repeated purchases; never kill a donor, erase unrelated modifiers or silently apply bargains to nonparticipants. Personal changes need readable icons/status and clear pause/eradication rules. **Confirmed lifecycle,21 October:** lasting bargains survive death and reconnecting, with no player reversal. Completing Relic Heart eradication removes both their bonuses and sacrifices. An operator repair route may correct bugs, not offer a normal refund. Duration-limited offerings retain their own expiry. Stacking, exact balance and transaction details still need the implementation contract.

Core owns reusable player modifiers, persistence/synchronization, configurable stat bounds and offering/transaction primitives. Quiet Below owns the Altar's recipes, living-network power and costs. HUD dependencies are for rendering/layout/editing only. Hunger, regeneration, sacrifice and health calculations remain ours, per the operator's explicit clarification.

## HUD dependency research

Checked21 October2026 against official Modrinth release metadata and upstream documentation. The initial alternatives search installed/executed no candidates; the later selected SpiffyHUD/FancyMenu prototype is tracked separately in [the dependency contract](THIRD_PARTY_HUD.md). Exact metadata is retained at `../reports/m11-hud-frameworks.json`.

- [OneConfig](https://modrinth.com/mod/oneconfig): rejected by the operator because of its appearance. Exact1.21.10 Fabric release exists, but do not select or install it for this work.
- [kUI](https://modrinth.com/mod/kui): deprioritized because the operator is concerned about maturity and maintenance. Previous archive inspection is retained as evidence, not a dependency decision. A recent release alone cannot establish long-term maintenance quality.
- [DynamicHUD](https://modrinth.com/mod/dynamichud): a direct developer HUD/widget/editor library, MIT. Official exactFabric1.21.10 version query returned no releases, so do not install an adjacent-version jar as compatible.
- [SpiffyHUD](https://modrinth.com/mod/spiffyhud) with [FancyMenu](https://modrinth.com/mod/fancymenu): strongest visual-editor candidate from the new search. Official Fabric1.21.10 releases `3.1.0-1.21.10-fabric` (22 November2025) and `3.8.2-1.21.10-fabric` (8 December2025); project metadata records updates in September2026. The [element documentation](https://github.com/Keksuccino/SpiffyHUD/wiki/Elements) includes configurable heart and food-icon bars. The [mod-HUD integration page](https://github.com/Keksuccino/SpiffyHUD/wiki/Customize-Mod-HUD-Elements) warns that arbitrary mod overlays generally cannot be moved/deleted directly. Extra maximum food capacity must therefore be proven against the pinned implementation; changing icons-per-row is not proof of a dynamic maximum. Inspect its dependency graph and DSMSL redistribution terms before a pack change. No Chronicle runtime compatibility claim.
- [owo-lib](https://modrinth.com/mod/owo-lib): MIT UI/layout library with exact1.21.10-tagged release `0.12.24+1.21.9`; the [component framework](https://docs.wispforest.io/owo/ui/) gives us control over native-looking presentation. Its [draggable containers](https://docs.wispforest.io/owo/ui/utility-components) are useful building blocks, not a complete persistent player HUD editor. A developer-framework alternative with more integration work.
- [CustomHud](https://modrinth.com/mod/customhud): MIT, established variable/profile-based text HUD; Fabric1.21.10 release `4.1.3+1.21.9` (14 October2025). Configurable placement, variables and conditionals are useful, but a weaker fit for a visual editor and vanilla-style extra food icons. This is a different workflow, not a like-for-like editor substitute.

New exact-version/project metadata is retained at `../reports/m11-hud-alternatives.json`. Recommendation is a bounded SpiffyHUD/FancyMenu visual prototype, with owo-lib as the developer-framework alternative. The operator subsequently selects SpiffyHUD/FancyMenu for prototyping, with explicit license adherence. See [the dependency contract](THIRD_PARTY_HUD.md) for official reference-only packaging and pinned releases; this does not install the Altar or expanded food mechanics. Neither option changes the Forge requirement: Core-owned item/enchantment mappings and universal caps can proceed independently of HUD selection.

Following integration: an original Chronicle element with11+ food icons and extra hearts, dragging/scaling/window resize/GUIscale2, alongside existing status/actionbar and AppleSkin if present. Inspect the pinned source/dependency loading before executing; pin all jars/hashes and test on a disposable matching client. No gameplay-overhaul dependency is needed for this request. Server food mechanics stay separate from the HUD dependency and remain authoritative.
