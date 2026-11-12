# LC07: optional banquet

The owner approved the UI workflow and illness policies on 11 November 2026. Development version **0.2.0-dev.12** implements the optional banquet in Last Council. Native presentation and tuning await owner review; the separately deferred treaty and complication group reviews remain unpassed.

## Playing and configuration

**Game setup -> Banquet** opens the supply/settings controls. Settings require operator permission, Editing and non-active play. **Give supplies** selects an online participant, quantities and a separate confirmation. Supplies are individual, finite items; normal `/give`, starting-gear entries and loot entries do not create valid ledger-backed supplies. An operator can issue stock during setup, then move those items into containers during Building Mode.

Use a poison dose in the main hand with a banquet drink in the offhand. Drink normally, including at full hunger. Contamination changes no client-visible item component, model, label or tooltip. Applying another dose to an already contaminated drink spends that dose with the same visible outcome, preventing a free contamination test; private history retains the original attribution and subsequent doses.

Default poisoning is **disabled**. When enabled, symptoms begin after **2 active minutes** and last **8 active minutes**: vanilla **Nausea I** and **Slowness I**, with no damage or political-rights changes. A further poisoned drink refreshes duration while preserving active symptoms or the earlier pending onset. One antidote clears pending or active banquet illness. Other sources' status effects are preserved.

Illness survives death, respawn, reconnect and server restart. Each affected player's clock advances only during their eligible active participation. Offline time, technical pause and Building Mode do not count. Disabling poisoning suppresses new illness, suspends retained illness clocks and removes owned symptoms; re-enabling resumes the retained state. Ordinary drinks and antidotes remain usable during active participation.

Reusable settings live in `config/chronicle/last-council-banquet.json`, or `config/chronicle/profile-content/last-council-banquet.json` for an authoring profile:

```json
{"schema":1,"enabled":false,"onsetMinutes":2,"durationMinutes":8}
```

Both timings accept whole minutes from 1 to 60. Settings export/import with scenario content. An older initialized profile gains disabled settings once; its marker then makes missing settings an error instead of silently reseeding them. Empty profiles remain empty and poisoning stays disabled.

Only the explicitly assigned Chancellor receives the private moderation log. An operator playing a House receives no log entries. The Chancellor may use **Private log** in the banquet controls when also an operator, or `/chronicle banquet log`. No additional political or personal-menu tab is introduced.

## Ownership, persistence and recovery

Last Council owns items, timing policy, symptoms, settings and private records. Core exposes its existing registry-aware inventory codec through `InventorySnapshots`; it gains no political or poisoning policy. Council scenes share the existing frame, with a bounded-size overload for the compact banquet dialogs. Vanilla item-model references support texture packs without redistributing Minecraft assets or adding a dependency.

The world retains `chronicle/last-council/banquet.json`, `banquet-inventory.json` and `banquet-world-id`. Keep these with the whole world when moving a prepared map containing issued supplies. They are excluded from reusable scenario exports and fresh review snapshots.

Each operation records exact before/after **43-slot** inventory evidence, a ledger fingerprint and the proposed ledger. After applying the inventory, the server saves player data and reads the actual compressed player file back, comparing inventory and equipment before committing the ledger and terminal receipt. Same-request retries authenticate the original actor, recipient and contents. One held operation blocks further banquet changes and clock progression.

Recovery cancels exact before evidence, commits exact after evidence only against the matching before/after ledger, and leaves mixed evidence untouched and held. Online recovery inspects changed evidence; `/chronicle banquet recover` lets an operator retry after restoration. It never forces a chosen outcome or replaces unrelated inventory contents. Missing initialized files, malformed JSON or external edits preserve retained evidence and hold progression.

An uncertain gift can be closed and reopened. Its request survives reconnects within the running client and is bound to the originating world; another world cannot reuse it. A completed receipt bypasses mutation rate limiting for prompt reconciliation. Full client-process restart does not retain that local pending request: inspect the recipient/server outcome before deliberately issuing a new gift.

Illness checkpoints occur every 20 server ticks and before inventory operations or shutdown. An abrupt process loss can repeat less than one second of earned illness time. File publication/readback and conservative recovery are not an arbitrary disk/power-fault guarantee.

## Verification

- Full build: Core **596**, Last Council **149**, Quiet Below **242** tests; zero failures/errors and **7 existing skips**. Total **987**, of which **980 passed**. All matching remapped jars and the native client launch manifest built. Logs: `../reports/dev12-complete-build.txt`, `dev12-stale-settings-build.txt`.
- Python scripts: **134 tests PASS**; modified runners compile. Final packaging checks are recorded with the pack receipt.
- Final native two-client initial/restart suite **PASS**: `../reports/council-banquet-20261112T124546411293Z/report.json`. It exercises real settings/gift controls, offhand poisoning, full-hunger use, actual 32-tick client drinking, refresh/cure, foreign-effect preservation, failed player-data writes, exact before/after/mixed recovery, a dropped committed gift reply followed by actual disconnect/reconnect and same-nonce retry, real active/pause/Building clocks, death/respawn, occupied-play offline clocks and actual server restart. Pending requests leave existing buttons enabled without flicker; a rejected stale save reloads current server values before another edit. Incubation is accelerated through the guarded fixture for the symptom checks; the default 2/8-minute balance is not certified by that acceleration. Prepared-intent reload tests are not hard process-kill or arbitrary power-fault tests.
- Native portable-content suite **PASS**: `../reports/council-content-20261111T154321387304Z/report.json`. It verifies three content extensions, changed banquet settings through export/import/restart, exclusion of private stock/casts/schedules, empty-profile suppression, one-time legacy initialization and missing initialized-file refusal.
- Independent read-only reviews found and resolved foreign-effect ownership, saved-state consistency, recovery bookkeeping and reconnect/UI pending-state issues. Normal/compact native captures were inspected; the designer then proposed 480x180 dialogs to remove unused space, approved by the lead before implementation. The private log retains its larger scrollable frame.

Earlier disposable runs caught missing House setup in the fixture, the actual 43-slot Minecraft layout, an over-eager retry rate limit and a CRLF error in a generated identity fixture. Their reports remain as diagnostic evidence. Source brief and unrelated acoustics work are unchanged. No review server or local-server pack is required; delivery remains matching e4mc Host/Player `.mrpack` files. Archive contents, matching jar bytes and hashes are recorded in `../reports/dev12-pack-validation.json`; cleanup retains reports and screenshots in `../reports/dev12-cleanup.json`. The Host review configuration enables poisoning for testing; fresh installations still default to disabled.

Owner review should check the compact controls, physical supply flow, symptoms and timing. This does not mark the deferred treaty/complication group reviews, handcrafted map, political conflict/rebellion or release gates complete.
