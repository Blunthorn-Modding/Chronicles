# Local healing review

Use **dev.24**, Minecraft Java **1.21.10**, and the refreshed `../client/Chronicle-M9-1.21.10.mrpack`. Import/update your client profile to receive the visible list navigation controls. No protocol, registry, asset or appearance-layout change was made; older dev.22 clients are compatible by the unchanged contract, without a separate old-binary rehearsal. This review follows accepted crop growth and the operator's explicit choice of passive healing.

Join **localhost:25587**. This is a fresh authenticated, whitelisted bench at `../reviews/recovery/20261021T110337094048Z`, initially PAUSED. SchinderRenge remains op level4. Existing ecology25585, crop25586 and production worlds are unchanged. The floor is a test platform, not proposed map scenery.

1. Use Survival, join in **Players** (or `/chronicles join`), then **Session > Resume play**. Editing must be off. Starting beside the signed **NEAR SCULK** pad, injured players receive up to **half a heart every ten eligible seconds**. Creative/spectator/author/nonparticipants do not qualify.
2. Ordinary food-based regeneration still works, so it can mask this small bonus. For a clean comparison, first lower food below18. As operator, an optional quick setup is `/effect give @s minecraft:hunger 4 100 true`, wait for that temporary effect to finish, then `/damage @s 8 minecraft:generic`. Do not repeat damage at low health. The feature itself neither applies effects nor edits hunger.
3. Compare the living pad near **2, 65, 2** with the **CONTROL** at **14, 64, 2**. Reach is four blocks in 3D from a living owned sculk block. The control may cease to be distant if new growth reaches it. Standing among more sculk does not multiply the bonus.
4. For a physical cut, remove **-3, 64, 0**, the sole contact to the glass-caged test Heart at **-4, 64, 0**. Nearby blocks remain, but healing stops. Replace that contact and let the network reconnect; the healing wait starts fresh. Pausing and leaving reach also discard accumulated credit. Offline time grants nothing.
5. Pause, enable editing, then **Game setup > Local healing**. Review the explanation and controls. Enable/disable and Faster/Slower/Reset select 2, 5, 10, 20 or 40 eligible seconds; Reset returns enabled/10s. Small screens expose up/down list controls. Disable is also available to operators during active play; rate edits require non-active editing.

This benefit needs an **Established or Extensive** network and a physically rooted source at **25%+ vitality**. The small bench starts with 67 owned source cells and deliberately lowered extent thresholds (Established64, Extensive512), rather than changing the production default256/2048. The source and old control blocks remain ordinary terrain. Fully healthy players hold no credit; larger groups are served in turn and may wait longer than the selected minimum. No Altar, reveal event, authored benefit region or new entity/block entity is involved.

Please review whether healing is noticeable/useful, the default pace and four-block reach feel appropriate, and the menu plus cut/reconnection behavior are clear. Costs, stations and the Heart ending remain later slices. [Implementation contract](M10_PASSIVE_RECOVERY.md), [maintenance and full-city limitation](M9_JOURNAL_MAINTENANCE.md), [verification](TEST_PLAN.md).

`python scripts/recovery_review.py prepare` accepts only a passing healing+restart report and a stopped PAUSED snapshot with default healing/cleanup off. It creates a new review directory; `python scripts/recovery_review.py start` checks its manifest, jar hashes, loopback binding, authentication and whitelist before starting TCP25587 / voice UDP24467. Console `stop` saves normally. New RECOVERY_RULES and checkpoint records require the matching reader: rollback restores a stopped backup and matching jars together.

# Accepted crop bench (dev.22)

Use **dev.22**, Minecraft Java **1.21.10**, with the refreshed `../client/Chronicle-M9-1.21.10.mrpack` (the existing filename is retained). Dev.21 clients also remain compatible: no asset, registry or protocol-layout changes. The first crop-growth slice is described in [M10a](M10_CROP_GROWTH.md).

## Comparison world

Join **localhost:25586** for the separate crop bench. The existing ecology map at **localhost:25585** is preserved; production on 25565 is unchanged. Both review worlds use authentication and the whitelist; SchinderRenge remains an operator. The crop bench starts paused, with ordinary random growth restored to the vanilla default and the extra crop interval set to ten eligible seconds.

1. Add yourself to participation in **Players**, or use `/chronicles join`.
2. Open **Session > Resume play**. Editing mode must be off for your account to count as a participant.
3. Compare the four crops in the signed **NEAR SCULK** row at x=1 with the **CONTROL** row at x=13. Both receive ordinary Minecraft growth; only the near row gets the extra age increments. The source is beside the near row, rooted at the temporary Heart **-4, 64, 0**. There is no farm-region binding or reveal event.
4. Pause play, enable editing, and open **Game setup > Crop growth**. Review the reach, conditions, counts and rate controls. **Faster/Slower crop growth** selects 2, 5, 10, 20 or 40 eligible seconds; this is a minimum interval, not a promise for an arbitrarily large farm. **Reset crop tuning** restores enabled/ten seconds and restarts discovery/cooldowns. Disabling the bonus preserves crop ages and the sculk network.
5. For a complete cut, remove the sculk at **-3, 64, 0**, the Heart's only contact. Existing crop growth stays, but the bonus stops. Ordinary Minecraft growth continues. Replace the contact and allow the network to recertify/recover; the bonus resumes. Removing/replanting crops is ordinary Minecraft farming. Newly planted crops wait a fresh interval.

The fixed reach is **four blocks in three dimensions**, measured from owned, rooted sculk at 25% or more vitality. More nearby sculk does not multiply the bonus. Wheat, carrots, potatoes and beetroot must have valid farmland and native growth light (9+). Mature crops, other plants and containers are unaffected. There is no auto harvest, extra loot, crop entity or global tick-speed change. Bounded discovery may take a pass to find an existing field. Pause, empty play, disabled/held ecology, missing Heart and unresolved topology stop the bonus; time offline does not accumulate bonuses.

Review whether the bonus is noticeable, its default pace feels useful, and the physical reach and cut/reconnection behavior make sense. The floor is ordinary terrain and can eventually carry spreading sculk; a control plot ceases to be a control if living growth reaches within four blocks of it.

The operator accepted this crop slice. Passive healing is now implemented separately above; Hunger/Darkness, stations and Heart eradication remain later work. Offline journal compaction is implemented, while full native-city capacity/load-order soak remains required before prolonged production use. New saved `CROP_RULES` records require dev.22; restore the stopped backup and old jars together to roll back.

## Local operation

`python scripts/crop_review.py prepare` accepts only a successful crop+restart native report and a stopped, paused snapshot at default crop tuning with cleanup off. It creates a fresh world under `../reviews/crops`; it never overwrites the ecology review or production map. `python scripts/crop_review.py start` checks authentication, bindings, saved manifest and jar hashes before starting loopback TCP25586/voice UDP24466. Keep console input available and use `stop` for a saving shutdown. Use `python scripts/ecology_review.py start` for the preserved ecology review instead.
