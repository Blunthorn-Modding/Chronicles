# Temporary Ancient Altar offerings

## Contract

The Quiet Below Altar keeps its two existing lasting prayers unchanged and adds a second mysterious sheet:

* `Pray for swift mending`
* `Pray for clear sight`
* `Pray for a quiet harvest`
* `Pray for sheltering grace`

The first sheet retains the existing two prayer positions and labels. `More prayers...` opens a `224 x 184` second popup with four native 200 x 20 buttons at y48/74/100/126 and Back/Close at y154. The player sees no prices, durations, counts, diagnostics or numerical tooltips. Operator Game setup displays the configured terms.

Initial review defaults live in `config/chronicle/quiet-below-altar-offerings.json`; an existing valid file wins. Each offering has an independent enable switch. Invalid files and invalid reloads disable new acceptance of these four choices and are not silently rewritten. Already paid claims continue from their durably captured terms until expiry or source ending; a later config failure does not withdraw their prices or benefits.

| Prayer | Initial operator terms |
| --- | --- |
| Sheltering grace | donor loses 4 HP without dying; up to 4 HP is deterministically shared without overheal among other injured eligible participants within 8 blocks |
| Swift mending | the exact activating hand's damaged item regains `ceil(25% max durability)`; -2 maximum HP for 1,200 occupied ACTIVE seconds; one active repair per player |
| Clear sight | 3 XP levels; +25% exhaustion and campaign-owned Night Vision for 300 occupied ACTIVE seconds; one active sight per player |
| Quiet harvest | 16 bread; for 300 occupied ACTIVE seconds crops receive 200% local opportunity and ecology receives 150% local opportunity within 8 blocks of that Altar; one active harvest per station |

Timers use Core's shared occupied ACTIVE clock. Paused, held, empty and server-downtime intervals do not advance them. Other participants continuing real active play does advance an offline owner's timer. Repair/sight modifiers survive death and reconnect until their active-clock expiry.

## Durable transaction and recovery

Temporary operations are separate from lasting `offerings.json`. Core owns strict `world/chronicle/temporary-offerings.json`, its initialization marker, exact previous-byte backup, external-change detection, bounds and atomic publication. Terminal claims move to create-only `world/chronicle/temporary-offering-history` records during bounded automatic capacity cleanup and the shared non-active Event-history maintenance action. Exact transaction retry and Event-source verification consult both live and archived evidence, so cleanup does not permit replay or eventually lock a repeat user at the live-ledger bound. Old lasting schemas, receipts, counts and source tombstones are not migrated or reset.

The one-use station token and a fresh exact preview are checked before Core publishes `CLAIMED`. That durable claim snapshots the player, source, offering, station, active-clock bounds, modifiers and bounded campaign evidence. Only then may Quiet Below mutate health, held item damage, XP or bread. After the external mutation, Core publishes `COMPLETE`, projects ongoing modifiers, and completes the prayer Event-source receipt.

A crash or exception after `CLAIMED` is deliberately uncertain. It is never automatically retried, rewarded or refunded. The temporary-offering hold stops active-clock progression. An operator in non-active play may pin the oldest uncertain operation; Game setup shows its exact UUID, immutable claim digest and current state. After inspecting and manually correcting the player/world, a separately confirmed action revalidates those exact pins and records `REVIEWED`. Review cancels the matching prepared prayer Event source rather than claiming that the uncertain prayer succeeded; restart reconciliation performs that same exact cancellation if a crash separates the two publications. The review action itself changes no item, XP, health, bread, modifier or benefit. Unrelated operation holds block recovery; the temporary/source holds do not require Editing and do not block their own review.

Expiry and source closure are published before Core removes owned modifiers. Relic Heart eradication closes both lasting and temporary sources. It removes ongoing maximum-health/exhaustion/Night-Vision/harvest effects. Already-applied healing, repaired durability and paid XP/bread remain; attempting to reverse them would mutate unrelated later state. There is no player reversal.

## Vanilla and ecology boundaries

Swift mending changes only the damage component of the exact revalidated held stack. It does not copy, move, enchant or replace the item. A claim precedes the mutation, so death drops cannot create a second repair path.

Clear sight uses a transient owned vanilla Night Vision instance. It is excluded from player saves and reconstructed from Core authority. A foreign Night Vision instance displaces it and is never removed or overwritten; the Altar instance returns only if the foreign effect ends while the claim remains active.

Harvest never loads chunks, seeds remote sculk, changes barriers or raises shared terrain-write limits. Each Altar block entity owns a persistent random station UUID; replacing the block creates a new station and immediately makes the old claim's aura inapplicable. The crop hook shortens only cached crop cooldowns inside an active, loaded, identity-matching aura. The ecology hook supplies one extra physically rooted, loaded-only, exclusion-aware local opportunity at the configured fraction of normal base attempts. Multiple stations remain bounded and share the existing write budget.

## Verification boundary

Pure coverage owns strict codecs, legal one-way claim transitions, immutable terminal archives beyond 128 repeat uses, exact replay refusal, source closure, expiry, corrupt/external evidence and local crop bounds. `scripts/temporary_altar_smoke.py` is the guarded disposable native fixture; its server/client hooks do not register without `CHRONICLE_TEMPORARY_ALTAR_TRIAL` / `CHRONICLE_TEMPORARY_ALTAR_SMOKE`. Its focused full run passed real death/drop, same-server reconnect, downtime restart, pause/no-spend, active expiry, exact held components and repair rounding, XP/bread conservation, health/hunger isolation, deterministic healing, foreign/owned Night Vision, station replacement, and two-step uncertainty review. The additional real-block UI fixture passed compact/wide second-sheet rendering, normal Clear Sight transport and stale-token refusal. Lasting-prayer regression checks the original six purchases, caps, death/reconnect/restart and optional HUD integration. Heart eradication remains covered by the campaign eradication fixture. Exact combined build and native evidence is recorded in the latest test-plan entry.
