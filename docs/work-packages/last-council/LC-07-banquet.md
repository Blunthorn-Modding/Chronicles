# LC-07 — Optional banquet intrigue

Status: dev.12 passed owner functional review. The approved dev.13 revision adds ordinary food, escalating poisoning and fatal character exclusion. See [the revision plan](LC07B_FOOD_ESCALATION_PLAN.md) and [dev.13 results](LC07B_RESULTS.md). Depends on LC-01 and existing Core item/clock/effect helpers. Brief: §9, §12, §14.

## Outcome and ownership

Last Council supplies drinks consumable at full hunger, limited poison doses and a full-cure antidote. A dose can contaminate one edible item from a held stack. Illness progresses from incubation to mild symptoms, Poison I and critical Poison II with lethal damage. The owner approved this lethal revision after reviewing dev.12.

This is a new gameplay mechanic, independent of Player Roles, starting gear and loot tables. Those may optionally place supplies on an authored map, but are not its implementation or prerequisites. The relevant existing foundations are vanilla item/effect APIs and the framework's pause/active-clock behavior.

## Work

1. Review symptom/delay/duration/stock/antidote settings and die/reconnect semantics before implementing effects. Poisoned and ordinary drinks have identical normal appearance, labels and tooltips.
2. Implement explicit contamination of a particular drink, exact item consumption and antidote use, with replay-safe receipts where inventory/state interact.
3. Persist pending symptoms and expiry using active-play clocks. Core technical pause and Building Mode semantics remain consistent; reconnect must not duplicate a dose or silently clear illness.
4. Restrict perpetrator evidence to private moderation. Illness progresses during eligible active play and critical poisoning can kill. Its fatal hit durably excludes the character and enforces Spectator mode. Earlier illness stages preserve political participation.
5. Make the entire poisoning capability disableable without invalidating Council setup or requiring a physician role.

## Acceptance and verification

Native full-hunger drink use, ordinary food consumption, single-portion handling, container returns, delayed escalation, antidote at every stage, critical death, pause/resume, reconnect and duplicate/stale item requests. Public item names, models and tooltips retain their ordinary appearance. Private records retain contamination and perpetrator evidence. Validate inventory conservation and legacy data migration.

## Review gate

Review the in-game presentation and effect tuning together with the banquet scene. Poison is optional; no political delivery gate should depend on enabling it. Confirm any new assets/audio provenance before bundling.
