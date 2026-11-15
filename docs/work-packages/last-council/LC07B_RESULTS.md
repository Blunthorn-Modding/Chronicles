# LC07B ordinary food and escalating poisoning

Development version **0.2.0-dev.13** implements the owner's food and lethality revision. Dev.12 passed the owner's functional review. The new execution and balance require the next in-game review.

## Player behavior

- Hold an issued poison dose in the main hand and edible food in the offhand. One dose isolates one contaminated serving. If the offhand held a stack, its clean remainder replaces the spent dose in the main hand.
- Food keeps its normal item, display name, model, nutrition, eating effects and returned container. Normal hunger requirements apply. Existing banquet drinks remain consumable at full hunger.
- Default progression uses **2 active minutes** of incubation, **2 active minutes** of Nausea I and Slowness I, then **2 active minutes** with Poison I. Critical illness applies Poison II and half a heart of additional damage per active second. It persists until cured or fatal.
- A further exposure preserves an existing escalating illness's stage and remaining progression. It cannot postpone the danger by restarting incubation. One antidote cures every stage and clears owned symptoms.
- Offline time, technical pauses and Building Mode suspend the progression. The underlying illness survives reconnects and server restarts.
- Fatal Banquet damage permanently excludes the character. Respawn and reconnect enforce Spectator mode and remove participation. An unrelated death keeps its normal respawn behavior and retains the illness. This implements hardcore-style exclusion specifically for fatal poisoning.

The supported food scope is edible inventory items with vanilla food and consumable components. Placed cakes use a separate block interaction and are outside this held-food path. A contaminated serving has an opaque inventory identifier, which also keeps it separate from clean stacks. Its ordinary name/model/tooltip contains no poison label. Advanced component inspection and stack compatibility can distinguish the tracked serving.

## Ownership and compatibility

Last Council owns food contamination, stage policy, effects and exclusion. The existing Core inventory codec, pause rules and participation controls provide the shared mechanisms.

Private Banquet state uses schema 2. Schema-1 supply receipts, pending journal evidence and existing harmless illness remain readable. Existing harmless illness follows its recorded expiry. A new exposure can apply the escalating policy. Recovery accepts the verified original schema-1 ledger fingerprint and reconciles exact inventory evidence.

Settings use schema 2 with `enabled`, `onsetMinutes`, `symptomMinutes` and `poisonMinutes`. Fresh defaults remain disabled with 2/2/2-minute stages. Schema-1 custom duration is divided between the two symptom stages, preserving its total subject to the one-minute minimum per stage. The old default of 2-minute onset/8-minute duration maps to the newly approved 2/2/2 defaults. Opening migrated settings displays a notice until the operator saves them.

The existing Banquet settings popup contains the three timing fields. A dedicated UI agent proposed the compact layout and the lead approved it before implementation. Native packets carry the three values. The pending-request and stale-settings protections are retained.

Food consumption records durable intent before invoking vanilla eating once. It confirms the resulting inventory and actual saved player data before publishing exposure. A failed save retains exact recovery evidence. Recovery does not repeat the vanilla meal or its effects. Arbitrary power failures across player physiology and world files are outside this verification.

## Verification

Java verification passed **993 tests with seven existing skips**, for **986 passes** and zero failures/errors. Core contributes 596 tests, Last Council 155 and Quiet Below 242. The Python suite passed all **134** tests. Full and final incremental build logs are `../reports/dev13-build2.txt` and `../reports/dev13-build6.txt`.

The final two-client Survival test passed through two actual server restarts. It covered all **40 vanilla edible inventory item types**, one-item splitting, chest/drop transfers, vanilla food effects and remainders, a failed meal save and exact recovery, replay rejection, actual 32-tick eating, pause/Building/offline clocks, critical cure, fatal damage and permanent Spectator exclusion. Report and normal/compact captures are retained at `../reports/council-banquet-20261115T152747596273Z/`. Stage progression is accelerated through the guarded fixture for critical cure/death. The default 2/2/2-minute balance remains for owner review.

The native portable-content suite passed five client phases and three missing-file refusal checks. It verified changed stage settings through export/import/restart, empty profiles and legacy initialization. Evidence is retained at `../reports/council-content-20261115T153102563362Z/report.json`. Matching Host/Player pack CRC, jar bytes, hashes and configuration passed validation in `../reports/dev13-pack-validation.json`. Six stopped generated fixtures were removed with reports and screenshots retained. The cleanup receipt is `../reports/dev13-cleanup.json`.

The recorded commands are:

```text
gradlew.bat --offline --no-daemon :core:test :campaigns:last-council:test :campaigns:quiet-below:test :core:remapJar :campaigns:last-council:remapJar :campaigns:quiet-below:remapJar :campaigns:last-council:writeSmokeClientLaunch
gradlew.bat --offline --no-daemon :campaigns:last-council:test :campaigns:last-council:remapJar :campaigns:last-council:writeSmokeClientLaunch
python -m unittest discover -s scripts -p test_*.py
python scripts/council_banquet_smoke.py
python scripts/council_content_smoke.py
```

Independent review caught and resolved unrelated-death exclusion and discarded custom timing migration. The final scoped review found no further blocker. Earlier native attempts exposed Creative-mode fixture assumptions and a missing pause before the final restart's editor checks. Their reports are retained.

Human review should check how it feels to poison ordinary food, recognize worsening symptoms and obtain an antidote before death. Treaty and complication group reviews remain deferred.
