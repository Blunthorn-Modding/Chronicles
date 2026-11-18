# LC05B implementation — foreign affairs

The owner approved the [dedicated UI proposal](LC05B_UI_DESIGN.md) on 18 November 2026. Both foreign war and foreign peace require two-thirds of the electorate captured at opening. This slice follows the verified dev.15 checkpoint on `feat/last-council-foreign-affairs`. It adds named realms and typed ordinary motions; it does not change voluntary house treaties or succession.

## Boundaries and storage

Last Council owns a separate reusable realm catalogue, exported through the existing ScenarioContent extension mechanism. Operator/non-active Editing controls creation, name/description editing and confirmed deletion; IDs are generated. Do not reuse the House source's post-paper editing lock. Referenced IDs cannot be deleted. Fresh realms start Not at war. Their diplomatic state and chronological ballot-effect receipts live in the world, never in reusable defaults. Empty custom profiles start with an empty realm catalogue.

Add strict bounded `ForeignRealmService` and `ForeignAffairsService`. The latter stores exact ballot/motion/realm/expected-revision/kind/title effects and derives each realm's state by replay. An identical ballot effect is idempotent; a changed identity or stale realm revision refuses without overwriting later decisions. Use observed-byte verification and preserve uncertain/corrupt files for exact recovery. Bound catalogue size, public packets, history and encoded journals explicitly.

## Council integration

Extend immutable Motion with an optional typed foreign subject (kind, realm ID, expected diplomatic revision); General and Regency have no subject. Foreign kinds enforce TWO_THIRDS on the server. Proceedings schema 4 explicitly migrates schemas 1–3 to General while preserving every prior motion, attempt, pending operation and succession result. Preserve subject metadata through all motion copies and validate it against the associated immutable ballot snapshot.

Add a narrow effect adapter to ProceedingsService. New proposal/opening validates realm existence and exact revision. During its existing saved CLOSE recovery: close the same Core ballot, apply the exact foreign effect only if Yes passed, then clear the Proceedings pending operation. A crash after either write retries the same receipt. Failed/void ballots change no diplomatic state. Startup/recovery checks the relation between passed typed ballots and world receipts; missing, mismatched or orphan outcomes hold instead of silently reseeding or manufacturing a decision. An unavailable/stale realm preserves the proposal and requires explicit review, never a forced outcome button. The existing operator recovery path reloads all required authorities before releasing holds.

Extend bounded request/detail projections with category and eligible realm choices. Preserve secret-vote privacy and automatic ballot updates. The approved motion form conditionally shows the realm selector; the normal manual agenda/open/defer/close flow remains. Public Conflicts gains House conflicts / Foreign affairs filters with the existing small list/detail frame. Catalogue editing uses focused existing popups; no extra personal-menu tab.

## Dependency order and ownership

1. Bounded pure catalogue/world domain and focused tests: one independent worker owns only the new `foreign` classes/tests.
2. Root owns typed Motion/schema migration, durable Proceedings adapter, runtime authority and protocol contracts.
3. Dedicated UI worker implements the approved screens after root fixes the protocol contract; it owns only designated client classes. Root reviews normal/compact captures.
4. Independent read-only review checks migration, authority, private votes and cross-store recovery before handoff.
5. Root integrates and verifies full diff, focused/module/full tests, native passing/failed/void/restart/recovery and profile export/import. Serialize Gradle and native class/port use. Ship matching e4mc packs only after checks pass.

## Required evidence

Cover generated IDs and CAS/delete guards; strict malformed/oversized/uncertain journals; legacy General/pending migrations; exact six-of-eight passage/five-of-eight failure for both kinds; captured electorate and server threshold enforcement; unchanged General/Regency/house agreement behavior; duplicate close/restart and partial outcome writes; ABA revision refusal; public histories and secret privacy; profile/export/empty/missing-file behavior; shared UI normal/compact geometry. Retain compact reports and delete stopped disposable worlds after evidence extraction. Human multi-player roleplay acceptance remains a separate group review, not a substitute for these checks.

No external dependencies, source-brief edits, military simulation, PvP/map/resource changes, ruler privileges, active treaty amendments or realm treaty ratification are included. Use a stopped pre-upgrade copy for rollback; older clients/journals are not a supported downgrade.
