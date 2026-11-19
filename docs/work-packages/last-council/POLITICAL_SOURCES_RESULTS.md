# Political Event sources ? development 19

Owner authorized these coding follow-ups on 19 November 2026. Branch: `feat/council-progression-sources`, from dev.18 `1577e3e`. The current source implementation extends the existing Events editor; it does not add another actions or flags engine.

## Available triggers

Choose a trigger in **Events ? create/edit ? When**, then its target and activity using the existing picker. Configure flag updates, text, sound or Region changes in the Event's existing outcome list.

| Trigger | Target | Activities |
|---|---|---|
| Council vote completed | Any Council vote, Regency, or a specific saved ordinary motion | Passed; Did not pass |
| Succession result | The election | Ruler elected; Candidate eliminated; Throne left vacant |
| Public treaty activity | Any public treaty | Signature recorded; Agreement sanctified; Agreement broken |
| Rebellion or reinstatement | Any House or one configured House | Rebellion declared; House joined rebellion; House reinstated |

Council called and Conflict changed retain their existing behavior. A voided ballot does not count as a completed vote. There is one vote occurrence per completed ballot, not one per voter. A multi-House reinstatement supplies one change per affected House; select a particular House or choose the Event's repetition policy accordingly. A saved motion's identity is selected from its public title; no ID typing is required.

Sources contain no player actor, individual secret choices, treaty terms or private agreement identities. Source-actor audience is therefore unavailable; use Participants or a Region. Private treaty history is filtered at the action's historical visibility, including automatic breaches during war declarations. Configure the Event before the political action: adding it later does not replay older decisions. No new source is created for previews, refused/no-op requests, loading old history or replaying an already committed operation.

## Recovery and compatibility

Last Council owns the political transitions and public source meanings. Chronicle Core retains source receipts, Event execution, prerequisites/timers and flag updates. No political or Core storage schema changes are required in this slice. Existing constructors/previews/replay use no observer; only real runtime authorities install hooks.

Observers run after candidate validation and before the authoritative mutation. Cross-store conflict and reinstatement operations plan their child sources before saving their parent intent. Normal child application reuses matching pending preparations, preserving the origin's flag snapshot. New attempts use fresh receipt IDs, so a deliberately cancelled failed preparation cannot permanently block a retry.

Sources resolve only after verified parent effects finish. Treaty/rebellion receipts pin SHA-256 hashes of the preceding journal history, rejecting a different valid prefix. Missing or uncertain outcomes never automatically turn into a completed or cancelled source. Restore/reload the parent through its existing recovery command first; exact committed evidence completes automatically. If the outcome cannot be proved, the existing audited Core uncertain-source review remains required. Guards skip source creation during startup or held recovery before reading unavailable play/source services. No older political history is backfilled on upgrade.

This preserves the existing limits: arbitrary simultaneous rollback of retained authorities is not certified, and operator acknowledgement remains an explicit audited decision. Keep matching retained journals when repairing a stopped world.

## Verification

- Full offline build: `../reports/political-followups-build2.log`, PASS. Final changed Last Council compile/tests/remaps/client launch: `../reports/political-followups-ready-build.log`, PASS. Java total **1,084**, **1,077 passed**, seven existing Windows privilege skips (Core629/6, Quiet Below242/1, Last Council213/0). Seven focused political tests cover pre-write observer refusal, post-write uncertainty/reload, private history, prefix replacement, replay/preview/no-op suppression, multi-House restoration and secret-safe Regency evidence.
- `python -m unittest discover -s scripts -p 'test_*.py'`: **146 PASS**, `../reports/political-followups-python.log`.
- Native sequential coordinator `python scripts/council_suite.py --suite succession --suite conflicts --suite rebellion`: **PASS**, `../reports/council-suite-20261119T170239890803Z/report.json`. All three suites kept exact final JAR hashes unchanged: secret elimination/coronation ? Event/flag across restart; public treaty signature/sanctification/breach ? Event/flag with private and automatic-private-breach suppression; ordinary votes and declaration/join/multi-House reinstatement ? Event/flag, including a real locked-journal parent recovery. Restart snapshots reject duplicate occurrences. The ACTIVE held-callback test passed with play/source services deliberately unavailable.
- Independent read-only review found and rechecked fixes for absent/changed baselines, explicit uncertainty and held-recovery guard ordering. Native `held_guard` removes play/source services while ACTIVE under a hold, restoring them in `finally`, to check the corrected dependency boundary.

## Remaining UI work

The candidate withdrawal, explicit unresolved-election end and meeting-capacity warning are a separate owner-review gate. [Election UI proposal](ELECTION_MANAGEMENT_UI.md) has dedicated design, lead layout refinement and twelve browser checks; native controls are not implemented until the owner approves the workflow. Existing dev.18 review packs remain the current delivered group-review build. No extra intermediate pack or dedicated server is provided in this slice.

Final narrative content, group/microphone acceptance, representative load and publication remain separate from this coding follow-up.

Implementation commit `1540699`. Overall receipt: `../reports/political-followups-validation.json`. All three stopped disposable worlds were removed after compact evidence was archived, freeing **674,801,834 bytes (about 644 MiB)**. Reports/logs/screenshots remain. All **178** source-review files, **15** prior artifacts and the original brief hash remain unchanged. No main merge, publication, dedicated review server or intermediate review pack is included.
