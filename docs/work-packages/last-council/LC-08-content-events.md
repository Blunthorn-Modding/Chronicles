# LC-08 — Content, complications and existing Event integration

Status: planned. Depends on LC-01; integrate each delivered operation from LC-02–07. Brief: §§3, 10–13, 15–16.

## Outcome and ownership

Use existing character cards, Roles where needed, Books & Lore, loot, Events and flags to author the evening. LC-01 supplies the reusable character randomizer; this package supplies replayable Event pools and political-operation links. The Chancellor is the GM and controls discussion/voting pace; complication content and arrival are randomized. Core remains the reusable editor/executor; flags are saved state, not another Event/action engine.

The latest [random-complication clarification](RANDOM_COMPLICATIONS.md) supersedes manual crisis selection/timing: a configurable total is drawn from a reusable pool across the evening, surprising the GM too. Arrivals occur inside Council meetings and before that meeting's regency vote. Late arrival creates a Council recess while Core play remains active.

Complications belong in Events. A letter may be the text/communication delivered by a complication; Books & Lore applies only if a physical book item is wanted. These are content/media choices, not separate compulsory book mechanics. Delivering a physical book as an Event action is a planned explicit extension if needed, not a capability the current Event executor already has.

## Work

1. Author/review large pools of house information, secrets, **house goals** and knowledge-of-another-house templates. Personal goals are defined by players and are not generated or shown here. Keep the four categories distinct in presentation. Configure compatible selections, counts/weights/repetition and references; review generated casts rather than fixing every dossier in advance. Keep objectives narrative; opposing house goals need not be simultaneously achievable for everyone.
2. Draw actual facts first, then independently roll cross-house knowledge and render successful knowledge draws with LC-01. Some secrets must be able to remain unknown to every other house; owners receive no exposure marker/count. Use existing book/role/card assignment and delivery for these handouts, keeping secrets out of public cards. A physical letter/book may be used when appropriate, without requiring every narrative complication to become a book.
3. Supply the reusable Council-call Event and an authored pool of possible complications, such as testament, ultimatum, chalice, treasury or royal seal. The framework draws eligible complications and hidden Council arrival slots live; the GM does not select the actual crisis or prepare its timing each run. Ordinary discussion/motion order stays with the Chancellor. Every due complication arrives before its meeting's regency ballot, with a normal Council recess for late arrival and no forced political response.
4. Register source receipts for completed votes, treaty actions, conflict changes, rebellion/reinstatement and succession results. Events can update flags/read prerequisites or deliver narrative cues after those operations commit. Preview/retry/reconnect must not mint new political occurrences.
5. Reuse scenario export/import for portable pool/template definitions and deliberate political-rule extensions. Exclude committed draw seeds, actual private allocations, electorate choices, signatures, discoveries and campaign progress. Map Regions/Points stay with the map.

Implementation slices: **LC-08A** adds optional Core Event pools and durable validated draw/dispatch; **LC-08B** adds Last Council count/allocation, Council timing and the pre-regency/meeting-close recess gate, using the LC-02 progression contract; **LC-08C** supplies/reviews the content and existing political source/flag links. The ballot domain can be developed independently, but regency integration is not delivered until the LC-08B gate is enforced.

## Acceptance and verification

Fresh-map setup shows intended defaults and clear `!` diagnostics for missing bindings. Prepared-world and custom-profile round trips preserve appropriate definitions/progress without importing Quiet Below defaults. Private complications reach only their intended recipients; a timer reminder does not automatically convene a Council.

Verify configurable total across meetings, no out-of-Council delivery, hidden future selection/timing even in normal GM/debug interfaces, stable interrupted draws, first/last eligible meeting coverage and no per-meeting quota inflation. Every normal regency-opening path must resolve that meeting's due complication/recess first; later meetings' allocations do not block an earlier vote. A no-regency meeting resolves its due slots before closing.

Exactly-once sources across interruption/restart, preview without side effects, flags updated through Events and no secret-vote data in source/history payloads. A crisis cannot force a signature, change a closed ballot or add a candidate to a locked slate without a transparent reset.

Multiple draw seeds should exercise different valid casts without orphan knowledge, absent targets or private text in public projections. Changing a reusable pool later must not rewrite a committed cast. Replaying a prepared gathering at a later meeting uses a new call occurrence, not reset Event history or a new sound engine.

Include draws where zero, one and several observers know a secret, and verify that neither quotas nor balancing force total coverage. Test ordinary text/sound complications without any book definition. If a physical-delivery action is added, test that distinct action's consent/recipient/inventory/restart behavior separately.

## Review gate

Review character pools/rendered books/cross-house relationships after LC-01's generator is usable. Separately review complication-pool authoring and surprise arrival/recess flow; character cast preview must not reveal the live future complication draw. The brief's eight-house cast remains an example, not a fixed cast requirement. No invented rightful heir or preassigned ending.
