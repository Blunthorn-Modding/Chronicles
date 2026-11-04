# LC-09 — Integrated rehearsal, map gates and release

Status: planned. Depends on LC-01–06/08 and LC-07 if enabled. Brief: §§11–16.

## Outcome

Deliver a coherent one-evening political campaign with recoverable procedures and readable player/GM interfaces. A collection of working buttons is insufficient. The existing `0.1.0-alpha.1` Core/Quiet Below upload package remains untouched until deliberate new builds are ready.

## Work and evidence

1. Add a reusable `scripts/last_council_smoke.py` native runner with explicit named suites, bounded disposable worlds, real player/GM packets and compact reports. This runner is planned, not currently available.
   Include randomized card-bound handout generation, consistent cross-house knowledge and restart-safe private delivery, preserving old fixed assignments.
2. Drive the brief's §14 scenarios through real interfaces: sponsorship/agenda, public and secret votes, changing a vote, later-meeting succession, treaties, house/civil/foreign wars, rebellion/exile and formal reinstatement.
   Start gatherings through existing Event text/sound, test repeated calls with distinct receipts, and let the same GM/Chancellor sequence small motions before an optional ruler vote. No separate delegated Chancellor role or automated escalation.
3. Include two or more native ordinary players and a GM; add forged/stale request, disconnect, open-ballot restart, interrupted outcome, duplicate close/draw/signature and missing map binding checks. A secret-vote leak or changed closed tally blocks delivery.
4. Verify map/profile/export portability and Core-only/Quiet Below-only/Last Council-only/deliberate dual-mod isolation. Run relevant existing Core and Quiet Below regressions when shared services change.
5. Rehearse on the author's palace map with6–12 people: simultaneous proximity conversations, access paths, restrained UI/status messaging, understandable voting and meaningful rebel play. Sample counts are adjustable; tests must not hard-code8 as the only playable size.
6. Profile representative operation/UI/history loads. Use a long soak only for a demonstrated capacity/recovery risk; do not automatically create another three-hour run or preserve large worlds/profilers.
7. Review the ending report: enacted laws, permitted treaty/conflict history, rebellions and crowned/vacant/unresolved succession. Never invent a military winner or reveal private ballots.
8. Choose separate new release versions/dependencies, license/asset notices and Modrinth metadata. Keep original alpha artifacts/checksums intact; publication requires its own confirmed ownership/upload path.

## Release gates

- Domain, module and required native suites pass, with exact final-JAR hashes and honest limitations.
- UI flows have been designed/reviewed; participant and GM controls remain distinct and secret/private projections are verified server-side.
- Human map/group/voice rehearsal passed. Technical simulation does not substitute for negotiation quality or guard-boundary play.
- Migration/rollback and interrupted-operation recovery documented; no silent overwrite of existing worlds or political history.
- Current-version role/content/command/operator docs match the actual product; optional features are clearly optional.
- Successful test worlds are stopped and removed. Retain compact reports and only necessary screenshots; no persistent review server without a request.

## Requirements coverage

| Brief section | Primary packages |
|---|---|
|1 vision,2 setting/map | LC-01, LC-06, LC-08, LC-09 |
|3 characters/houses/candidates | LC-01, LC-03, LC-08 |
|4 meetings/agenda | LC-02, LC-03 |
|5 proposals/voting | LC-02, LC-05, LC-06 |
|6 succession | LC-03 |
|7 treaties | LC-04, LC-05 |
|8 conflict/rebellion/access | LC-05, LC-06 |
|9 banquet | LC-07 |
|10 complications | LC-08 |
|11 player/GM interactions | All feature packages; integrated LC-09 |
|12 configurable rules | LC-01–08; validation LC-09 |
|13 example cast/agenda | LC-08 content review |
|14 acceptance scenarios,15 rehearsal/ending | LC-09 plus each owning package |
|16 existing-project boundaries | CONTRACTS.md and all packages |
