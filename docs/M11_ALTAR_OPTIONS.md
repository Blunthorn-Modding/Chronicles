# Altar offerings: options for review

21 October2026. The operator wants a place of voluntary sacrifice for a concrete bonus, including the example of an extra maximum heart with a larger hunger bar and more food required before natural regeneration. This is a candidate list for review, not installed gameplay or a selection of all options. Numbers are illustrative balance defaults.

| Option | Sacrifice / ongoing cost | Bonus | Why choose it |
| --- | --- | --- | --- |
| Extra heart | Hunger capacity rises by one full food icon; regeneration threshold rises with it | One extra maximum heart | The operator's example: more health with greater feeding requirements |
| Faster movement | One maximum heart while this bargain is held | A small permanent movement-speed increase, e.g.10% | Clear mobility versus survivability choice |
| Emergency healing | The donor loses two current hearts; cannot reduce the donor to death | Immediate healing shared among nearby other participants | A visible voluntary support role, distinct from passive healing |
| Repair gear | One maximum heart is unavailable for20 eligible minutes; one outstanding repair bargain at a time | Restore part of one held tool/armour piece's durability, e.g.25% | Pays vulnerability for preserving scarce equipment; creates no enchantments |
| Cave sight | Pay XP and accept greater food consumption for the buff's duration | A few minutes of Night Vision | Useful underground without changing species, identity or Warden warnings |
| Settlement harvest | Consume a meaningful communal food/material offering and accept faster local ecological expansion | A temporary stronger nearby crop bonus | Helps the group while strengthening the sculk it may later wish to contain |

Recommended first review candidates: Extra heart, Emergency healing and Repair gear. They have distinct understandable costs, clear interfaces and no invented prophecy, personality change or compulsory faction. Settlement harvest is a later option because tuning additional growth must preserve the existing physical frontier and barriers; it must not seed remote sculk or require authored routes.

## The hunger example

Translate one full heart/icon explicitly: health20->22 points (10->11 hearts), food20->22 points (10->11 icons), ordinary regeneration threshold18->20. The fast full-saturation regeneration path must use the new full-food condition too. A larger bar is also extra food storage; this alone does not prove greater ongoing upkeep. If the intended price is consistently higher food consumption, separately tune exhaustion from healing or food efficiency and show that cost. Do not silently replace the operator's visual-capacity idea with a hidden hunger debuff.

## Consent, stacking and ownership

Proposed guardrails: show the complete trade before acceptance; preview actual resulting stats; cap repeated purchases; support deliberate reversal and operator repair; never kill a donor, erase unrelated modifiers or silently apply bargains to nonparticipants. Personal changes should have readable icons/status and clear death/reconnect/pause/eradication rules before implementation. These are design candidates awaiting review, not approved permanent-stat or ending policy.

Core owns reusable player modifiers, persistence/synchronization, configurable stat bounds and offering/transaction primitives. Quiet Below owns the Altar's recipes, living-network power and costs. HUD dependencies are for rendering/layout/editing only. Hunger, regeneration, sacrifice and health calculations remain ours, per the operator's explicit clarification.

## HUD dependency research

Checked21 October2026 against official Modrinth release metadata and upstream documentation. No candidate is installed or executed by this research. Exact metadata is retained at `../reports/m11-hud-frameworks.json`.

- [OneConfig](https://modrinth.com/mod/oneconfig): developer HUD editor for movement/scaling/styling. Official exact1.21.10 Fabric artifact `OneConfig-1.21.10-fabric-1.4.4.jar`, released19 October2026. Dependencies include Fabric API, Fabric Language Kotlin and Compose Multiplatform. [Upstream](https://github.com/Polyfrost/OneConfig) confirms editor/API scope. Candidate for the mod the operator remembers; runtime compatibility with Chronicle has not been tested.
- [kUI](https://modrinth.com/mod/kui): shared custom-HUD registry plus editor for vanilla elements, including hunger/health. Filtered1.21.10 release is `1.1.0+mc1.21.8` (26 July2026); checksum-verified archive declares Minecraft `>=1.21.8 <1.21.11`, Java21+, client-only, Fabric API, and exports `dev.kui.api.KuiMod`. About80KiB. [Upstream guide](https://github.com/KalpeGames/kUI) supplies element/layout registration; custom licence permits unmodified redistribution but not distributed modified copies. A small candidate to prototype, not an assertion of runtime success.
- [DynamicHUD](https://modrinth.com/mod/dynamichud): a direct developer HUD/widget/editor library, MIT. Official exactFabric1.21.10 version query returned no releases, so do not install an adjacent-version jar as compatible.
- [owo-lib](https://modrinth.com/mod/owo-lib): MIT UI/layout library with exact1.21.10-tagged release `0.12.24+1.21.9`; a general component framework rather than the same turnkey player HUD editor. Fallback if the dedicated editor options fail the prototype.

Next integration check: isolated client-only prototype registering a Chronicle element with11+ food icons and extra hearts, dragging/scaling/window resize/GUIscale2, alongside existing status/actionbar and AppleSkin if present. Inspect the pinned source/dependency loading before executing; pin all jars/hashes and test on a disposable matching client. No gameplay-overhaul dependency is needed for this request. Server food mechanics stay separate from the HUD dependency and remain authoritative.
