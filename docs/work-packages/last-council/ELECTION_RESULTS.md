# Election controls — dev.20

Implementation follows the owner-approved [workflow](ELECTION_MANAGEMENT_UI.md), on `feat/council-election-management` from `18953d4`.

## Behavior

- **My house → Election:** a participating remaining candidate can withdraw their own candidacy between ballots, with confirmation and a public reason. Their House keeps its Council seat and vote. An open ballot or same-meeting void replacement blocks withdrawal. One candidate still needs the existing later confirmation ballot; removing the last candidate leaves the throne vacant.
- **Chancellor proceedings → Election:** only the assigned Chancellor can end a contested election unresolved, with confirmation and a public reason. No ballot may be open. This is permitted after a same-meeting void. Public history and ordinary Council business remain available; there is no reset or deposition action.
- Both paths share the existing panel and reason-dialog components, automatic state updates and paged public history. The Chancellor alone sees an advisory warning when declared remaining meetings may not accommodate the worst-case number of Regency ballots. It never blocks a vote.
- Explicit voluntary withdrawals and unresolved endings supply the existing public `Succession result` Event source (`withdrawn` / `unresolved`). The final withdrawal also supplies `vacant`. They carry no private choices or papers. System withdrawals from rebellion retain their existing separate source.

## Persistence and upgrade

Proceedings now writes **schema 6**, reading schemas 1–5 without rewriting history. Explicit withdrawal/end operations retain their identity, actor, public reason and meeting. Legacy unresolved endings are explicitly marked as lacking an audit; migration never invents an actor or reason. Strict readers reject missing current fields, altered chronology and new fields smuggled into older schemas.

The server derives House ownership and Chancellor authority, rechecks revision and active-play permissions, and invalidates sessions when the underlying authority reloads. Refused or delayed submissions preserve the typed reason. Automatic refresh does not resubmit mutations. Public source preparation precedes the authoritative write; uncertain outcomes use existing audited Core recovery and do not infer cancellation from missing proof.

Stop and back up a world before upgrading. Older builds cannot read a newly saved schema-6 proceedings journal; rollback requires restoring the matching stopped-world backup and matching mods together. Map bindings and scenario configuration keep their established storage boundary.

## Verification status

Full offline build and module/client tests pass: **1,094 Java tests, 1,087 passed and seven existing Windows privilege skips**; **147 Python checks passed**. Focused tests cover audit identity/idempotence, migration, chronology, source uncertainty, meeting warnings and payload bounds. Independent read-only review found no production blocker.

The new four-client native suite passed both initial play and restart (`reports/council-election-20261120T073027606413Z/report.json`). It exercises real House/Chancellor screens, cancellation and required reasons, preserved input on a paused-play refusal, forged House END and stale/open/void mutation guards, seventeen void-history entries across two pages, public Event-to-flag delivery, automatic state updates and restart without source replay. Two voluntary withdrawals leave one contested candidate; ending unresolved preserves history and permits an ordinary motion on which both withdrawn candidates still vote. Normal and compact captures were inspected.

Two earlier harness failures are retained: selecting an already-selected tab, and bypassing client reply correlation on a successful mutation. The final runner uses real navigation and correlated success requests; raw requests are reserved for refusal checks. The compact reason label was adjusted after visual inspection. The final coordinator passed all three election/succession/foreign suites, seven phases and four restarts (`reports/council-suite-20261120T073027470169Z/report.json`), without changing the built artifacts. Foreign recovery includes a real replacement failure and missing-journal restore; election-specific uncertain-save behavior is covered by the domain tests, rather than a native crash injection. Native actor reappointment and delayed-reply injection were not separately exercised.

Matching dev.20 Host/Player packs pass archive integrity, exact-JAR, official optional voice-pin, client-default and private-progress exclusion checks (`reports/dev20-pack-validation.json`). The 178-file source world, 15 older artifacts and original brief hash remain unchanged. All five stopped generated fixtures (including the two harness failures) were removed after compact journal archival: 0.94 GiB reclaimed; raw failure/success reports, logs and captures remain. See `reports/dev20-validation.json` and `reports/dev20-cleanup.json`.

Commands: workspace-toolchain `gradlew.bat --offline --no-daemon build :campaigns:last-council:writeSmokeClientLaunch`; `python -m unittest discover -s scripts -p "test_*.py" -v`; `python scripts/council_suite.py --suite election --suite succession --suite foreign`; `python scripts/council_lan_pack.py --current-build --voice`; `git diff --check`. No dedicated server, main merge or publication was performed.

Final map, group roleplay, microphone/remote voice transport and representative load acceptance remain separate. Treaty amendments are optional follow-up scope; no separate Council ratification system is required.
