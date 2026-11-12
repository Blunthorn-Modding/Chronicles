# LC-07 — Optional banquet intrigue

Status: optional mechanic implemented on dev.12 after owner workflow/policy approval; owner native presentation/tuning review pending. See [implementation and verification](LC07_IMPLEMENTATION.md). Depends on LC-01 and existing Core item/clock/effect helpers. Brief: §9, §12, §14.

## Outcome and ownership

Last Council supplies drinks consumable at full hunger, limited poison doses, delayed non-lethal symptoms and antidote. Reuse suitable Core mechanisms; do not build a compulsory thirst/survival system or expose poison status to uninformed players.

This is a new gameplay mechanic, independent of Player Roles, starting gear and loot tables. Those may optionally place supplies on an authored map, but are not its implementation or prerequisites. The relevant existing foundations are vanilla item/effect APIs and the framework's pause/active-clock behavior.

## Work

1. Review symptom/delay/duration/stock/antidote settings and die/reconnect semantics before implementing effects. Poisoned and ordinary drinks have identical normal appearance, labels and tooltips.
2. Implement explicit contamination of a particular drink, exact item consumption and antidote use, with replay-safe receipts where inventory/state interact.
3. Persist pending symptoms and expiry using active-play clocks. Core technical pause and Building Mode semantics remain consistent; reconnect must not duplicate a dose or silently clear illness.
4. Restrict perpetrator evidence to private moderation. Choose effects that cannot themselves kill or automatically suppress voting, proposals or other political rights.
5. Make the entire poisoning capability disableable without invalidating Council setup or requiring a physician role.

## Acceptance and verification

Native full-hunger drink use, delayed onset, antidote before/after onset, low-health non-lethality, pause/resume, death/reconnect policy and duplicate/stale item requests. Normal packets/tooltips/list pickers and public history must not reveal contamination or perpetrator. Existing inventory conservation and enchantment-cap behavior remain intact.

## Review gate

Review the in-game presentation and effect tuning together with the banquet scene. Poison is optional; no political delivery gate should depend on enabling it. Confirm any new assets/audio provenance before bundling.
