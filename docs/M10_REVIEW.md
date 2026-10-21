# Crop benefit review

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

This is the first benefit only. Local recovery/Hunger/Darkness, stations and Heart eradication remain later slices. M9's larger native-city/load-order soak and journal compaction remain required before prolonged production use. New saved `CROP_RULES` records require dev.22; restore the stopped backup and old jars together to roll back.

## Local operation

`python scripts/crop_review.py prepare` accepts only a successful crop+restart native report and a stopped, paused snapshot at default crop tuning with cleanup off. It creates a fresh world under `../reviews/crops`; it never overwrites the ecology review or production map. `python scripts/crop_review.py start` checks authentication, bindings, saved manifest and jar hashes before starting loopback TCP25586/voice UDP24466. Keep console input available and use `stop` for a saving shutdown. Use `python scripts/ecology_review.py start` for the preserved ecology review instead.
