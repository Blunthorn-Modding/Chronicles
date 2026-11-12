# LC07B: ordinary food and escalating poisoning

## Owner feedback, 12 November 2026

The owner confirms dev.12 works functionally but changes the desired execution: poison should apply to ordinary Minecraft food, symptoms should intensify, and untreated poisoning should eventually be lethal. This supersedes the non-lethal outcome for new poisoning. It does not yet confirm exact timing, per-dose stack scope or post-death behavior; those questions are pending.

No dev.12 runtime behavior or review pack has been changed for this feedback yet.

## Proposed player flow

Use an issued poison dose in the main hand with food in the offhand. The food retains its ordinary item type, model, name, tooltip, nutrition, vanilla consumption effects and container remainder. Eating rather than merely carrying it causes exposure. Existing banquet drinks and the full-cure antidote remain supported. Contamination and perpetrator evidence remain private to the server/assigned Chancellor.

Pending owner choice: one item or the entire held stack per dose. Do not silently poison an entire stack or discard its remaining items. Stack splitting, merging, transfers, container storage, dropped items and replayed consumption must conserve the number of contaminated portions. No public poison flag or effect component may be added to food.

Proposed configurable progression, awaiting the timing answer:

1. Incubation: 2 eligible active minutes without symptoms.
2. Mild symptoms: 2 eligible active minutes of Nausea I and Slowness I.
3. Poisoned: 2 eligible active minutes, additionally applying vanilla Poison I.
4. Critical: Poison II and periodic lethal damage until cured or the chosen death outcome.

Vanilla 1.21.10 `PoisonStatusEffect.applyUpdateEffect` was inspected in the locally mapped Minecraft jar: it damages only above 1 health point. Poison II alone therefore cannot implement death. Additional damage belongs only to the critical stage, respects Core pause/Building protections and must not be implemented by globally changing vanilla Poison.

One antidote clears every pending/active banquet stage and only owned effects. Repeated exposure must never postpone onset, downgrade the current stage or grant a recovery window. It may extend poisoning in accordance with the chosen refreshed-illness policy. Offline time, technical pauses and Building Mode remain excluded; reconnecting must not cure it. The owner is deciding whether death clears illness or retains a non-lethal remainder to avoid repeated respawn deaths.

## Ownership and implementation slices

1. **Last Council domain/config:** replace the single harmless expiry with explicit bounded stages and eligible-active elapsed time. Extend `BanquetConfig`, `BanquetState`, `BanquetTransitions`, strict `BanquetStore`/`BanquetJournal` codecs and their tests. Preserve schema-1 receipts and pending recovery evidence; do not reinterpret an existing non-lethal exposure as an immediately lethal condition. Define an explicit conservative legacy-illness migration before coding it.
2. **Last Council food bridge:** generalize `BanquetRuntime.contaminate` beyond issued drinks. Inspect the actual 1.21.10 item-consumption completion and container-remainder paths before selecting a narrow mixin. Ordinary food with the food component is the initial coverage inventory; cake/placed-cake slices require an explicit supported path rather than an unnoticed exception. Keep normal food behavior unchanged outside tracked exposure. Reuse Core `InventorySnapshots` and existing exact journal recovery, with durable intent before inventory/state mutation. Do not roll back unrelated vanilla effects or replay a completed meal on recovery.
3. **Last Council illness runtime:** own Nausea, Slowness and Poison using the existing foreign-effect ownership rules. Persist stage/progress and the lethal cadence; never apply damage while paused, Building, offline, dead or otherwise ineligible. Reconcile cure/death and pending journal state before the next damage tick. Keep political participation/voting rules independent.
4. **Settings/UI:** extend the existing compact Banquet settings popup with clearly named stage timings rather than another Game setup entry or unrelated menu. Obtain a dedicated UI design/review before implementing a changed native form. Keep settings in reusable scenario content; exposure, attribution and consumed portions remain world-owned and excluded from exports.

Core should gain no Last Council poison policy. Add a reusable primitive only if inspection identifies a concrete framework need. No change to Quiet Below, ordinary vanilla Poison, generic loot tables or all-food eating restrictions is authorized by this feedback.

## Required verification

- Domain boundaries: every stage transition, delayed onset, repeat exposure, cure, critical cadence, no spontaneous harmless expiry, paused/offline clocks and chosen death outcome.
- Codec compatibility: dev.12 state/settings/journal fixtures, malformed bounds, retained pending evidence, export/import, restart and empty custom profiles.
- Native actual eating: representative stackable foods, bowls/stews, golden apples with existing effects, poisonous vanilla food and the chosen cake handling. Confirm nutrition, returned containers and consumption quantities. Exercise split/merge/chest/drop/transfer paths and copied/stale tokens.
- Actual health effects: Poison I/II, increasing symptoms, critical death on untreated illness, full antidote cure immediately before the next lethal tick, no respawn loop, foreign effects preserved, and no damage during pause/Building/offline.
- Failure paths: interrupted saves, exact before/after/mixed inventory recovery and duplicate meal/request delivery without extra consumption or renewed exposure.
- Native compact/normal settings frames and retained no-flicker/stale-save guards, followed by matching e4mc Host/Player packs only. No new review server or plain/server pack.

Treaty/complication group reviews remain deferred. The current source brief, played worlds and unrelated acoustics work remain untouched.
