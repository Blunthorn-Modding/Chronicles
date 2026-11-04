# LC-08 — Content, complications and existing Event integration

Status: planned. Depends on LC-01; integrate each delivered operation from LC-02–07. Brief: §§3, 10–13, 15–16.

## Outcome and ownership

Use existing character cards, Roles where needed, Books & Lore, loot, Events and flags to author the evening. LC-01 supplies the reusable randomizer; this package supplies the replayable content pools and political-operation links. The Chancellor is the GM and chooses the narrative/agenda pace. Core remains the reusable editor/executor; flags are saved state, not another Event/action engine.

## Work

1. Author/review large pools of house information, secrets, goals and knowledge-of-another-house templates. Configure compatible selections, counts/weights/repetition and references; review generated casts, rather than fixing every character's dossier in advance. Keep objectives narrative; opposing goals create play and do not need to be simultaneously achievable for everyone.
2. Draw actual facts first, then resolve cross-house knowledge from those facts and render recipient-specific books with LC-01. Use existing book/role/card assignment and delivery; keep secrets out of public cards and do not build another dossier/lore catalogue. Fixed letters/books remain supported alongside the pools.
3. Prepare the reusable Council-call text/sound Event and optional host-triggered complications: testament, ultimatum, chalice, treasury or royal seal. The Event calls people together; the GM then selects motions and opens/closes ballots one by one, finishing with succession when relevant. Use existing text/sound/lore and explicitly reviewed political actions; no fixed escalation script or forced response.
4. Register source receipts for completed votes, treaty actions, conflict changes, rebellion/reinstatement and succession results. Events can update flags/read prerequisites or deliver narrative cues after those operations commit. Preview/retry/reconnect must not mint new political occurrences.
5. Reuse scenario export/import for portable pool/template definitions and deliberate political-rule extensions. Exclude committed draw seeds, actual private allocations, electorate choices, signatures, discoveries and campaign progress. Map Regions/Points stay with the map.

## Acceptance and verification

Fresh-map setup shows intended defaults and clear `!` diagnostics for missing bindings. Prepared-world and custom-profile round trips preserve appropriate definitions/progress without importing Quiet Below defaults. Private complications reach only their intended recipients; a timer reminder does not automatically convene a Council.

Exactly-once sources across interruption/restart, preview without side effects, flags updated through Events and no secret-vote data in source/history payloads. A crisis cannot force a signature, change a closed ballot or add a candidate to a locked slate without a transparent reset.

Multiple draw seeds should exercise different valid casts without orphan knowledge, absent targets or private text in public projections. Changing a reusable pool later must not rewrite a committed cast. Replaying a prepared gathering at a later meeting uses a new call occurrence, not reset Event history or a new sound engine.

## Review gate

Review the pools, rendered book samples, cross-house relationships and GM preparation experience after LC-01's draft generator is usable. The brief's eight-house cast remains an example, not a fixed cast requirement. No invented rightful heir or preassigned ending.
