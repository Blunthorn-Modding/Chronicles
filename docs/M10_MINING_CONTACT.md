# Mining contact continuity and special-block exclusions - dev.35

The operator accepts the dev.34 Altar presentation and prayer behavior. This follow-up addresses nearby mining resetting contact effects and sculk covering special stations. Forge UI and the remaining sequential corrections stay separate.

## Unrelated mining

Previously every owned-cell removal/vein-face loss incremented the topology generation and made the entire old connection graph unusable. The contact-cost service interpreted that temporary graph check as no living footing, clearing effects and the initial wait even when the player remained connected to the Heart.

The engine now attempts a conservative local substitution proof before invalidating a certified component. In a 3x3x3 metadata neighborhood, every former connected neighbor of the changed cell must still reach every other neighbor without using the removed cell. A previously adjacent virtual Heart is a required terminal; an observed vein with fewer faces is itself a required terminal. This proves old paths can be replaced with valid detours. A nonconnecting leaf/detached-cell removal also cannot disconnect the remaining certified component.

The proof uses at most27 metadata vertices and729 pair checks per edit, no terrain reads, player path searches, chunk tickets or new entities. It accepts fully certified topology and the existing certified additive-extension state. Partial full rebuilds, uncertainty, unloads, failures, missing roots and restart replay cannot obtain a fresh certificate this way. On success only the removed node leaves the certified component, so unchanged living contact retains the same exposure and effect instances. On failure the existing bounded full recertification and fail-closed behavior remain. In particular a genuine bridge cut does not grant effects to the disconnected side. A wider alternate path outside this neighborhood can still require a conservative full recheck; this is not a general dynamic-connectivity index.

Each connected removal also subtracts one from the last completed ready-cell assessment until a fresh bounded vitality pass completes. This is conservative even if the removed cell was already below the ready threshold. Effective Established/Extensive eligibility applies the existing hysteresis to that lower count, preventing harmless topology edits from retaining a stage after crossing its exit threshold. New growth remains uncounted until assessed. After restart, no old saved extent authorizes healing/costs until the first fresh complete non-unknown vitality assessment. No timer, effect ownership, sole-contact, persisted ecology schema or accepted Altar receipt changes.

## Exclusion list

Quiet Below supplies the ordinary block tag **quiet_below:sculk_growth_excluded**, containing Ancient Altar, Ancient Forge and Relic Heart. Campaign growth neither replaces a tagged backing block nor adds a vein face on it. The native descendant authorization path applies the same target/new-face rules, while unchanged native feature phases remain untouched. Ordinary stone and wooden buildings remain unprotected.

A map/server datapack can extend the list at `data/quiet_below/tags/block/sculk_growth_excluded.json`:

```json
{"replace": false, "values": ["minecraft:chest", "example:my_special_block"]}
```

List only registered block IDs. Standard datapack reload semantics apply. This controls future campaign growth; it does not scan/delete pre-existing veins, forbid deliberate player placement or alter vanilla attachment rules outside the campaign growth engine. Existing unwanted faces can be removed normally. The disposable review bench is cleaned locally during upgrade.

## Verification

The new dense-network mining regression first failed against the previous implementation while the real-bridge negative passed. Pure engine checks now cover distant still-connected cells without terrain reads, bridge detachment, unload uncertainty, safe and unsafe vein-face loss, extent exit thresholds, and replacement/face exclusion with ordinary surfaces still growing. The real Minecraft cost fixture breaks an unrelated block through native Survival interaction and asserts both contact proof and exact effect identity after later ticks. The same lifecycle suite covers actual walk/jump/vein contact, clean pillars, cuts/reconnections, pauses, foreign effects, death and restart. Native adapter checks resolve all three default tag entries and verify ordinary stone is not excluded. Exact results and deployment are recorded in TEST_PLAN.
