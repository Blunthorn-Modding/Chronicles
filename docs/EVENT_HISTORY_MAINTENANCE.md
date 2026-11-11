# Event and source history maintenance

1 November 2026. This work package adds bounded, explicit capacity reclamation for the CF22/CF23 Event histories. Operators use **Game setup → Event history → Archive completed records**, or `/chronicle maintenance events` while play is non-active. The menu uses the existing Editing and confirmation guards; the command also supports an operator console. Pending recovery must be resolved first. If a full source ledger previously caused its recovery hold, reload the corrected/valid source evidence through its existing recovery control before maintenance; archiving never bypasses an uncertain source outcome.

## Safety contract

`EventHistoryMaintenance.maintain(server)` runs on the server thread and refuses ACTIVE play, operation holds, an unresolved Event execution or media phase, a pending source receipt, or an unresolved/unavailable Region batch. Each scan is bounded by the existing live limits: 1,024 source receipts, 256 flag receipts, 256 Region batches, 128 Events and 1,024 seen source IDs per Event.

Every retired record is published as immutable per-ID evidence before its live store is replaced. Publication is create-only and forced; an existing archive must have the expected kind, embedded logical ID, payload hash and exact payload. A crash or later save failure may therefore leave the same exact record both live and archived. That duplicate is safe and a retry verifies it. The reverse state—live evidence pruned before its archive exists—is not published.

Corrupt, externally replaced, differently identified or conflicting archives fail closed. Maintenance never deletes immutable execution archives, Region plans, snapshots or effects. It does not claim resilience to arbitrary disk or power failure beyond the repository's existing forced-file and atomic-replacement contract.

## Reclamation rules

- `chronicle/event-source-history/` receives terminal source records. PREPARED and uncertain records never retire. A COMMITTED record retires only after every currently configured matching system Event has persisted the exact ID in its seen facts or occurrence latch. Existing bounded CANCELLED eviction also archives before removal, so a cancelled ID cannot later be reused.
- After source live pruning succeeds, `event-progress.json` drops IDs absent from the retained live source ledger. Triggered state, the exact latched occurrence, actor, countdown start, Off versions and skipped state remain unchanged. This cleanup is independently retryable after restart and does not need to enumerate archive files.
- `chronicle/event-flag-history/` receives atomic flag outcome receipts only when the operation has a COMPLETE current execution or a COMPLETE immutable execution in the existing `chronicle/event-history/`. Applying an exact archived receipt is a no-op; the same UUID with different Event/fingerprint/changes is a conflict. Later GM flag changes are never overwritten by an old retry.
- `chronicle/event-batches/history/` receives only WORLD_COMMITTED journal entries with completed execution evidence. PREPARED, APPLYING, TERRAIN_SAVED and CONFLICT entries remain live. Status, source reconciliation, commit and resume consult the archive and the unchanged immutable plan, so an old operation cannot apply terrain again.

The existing Event execution JSON bytes and fingerprints, Event plan schema 1, source ledger schema 1, Event progress schema 1, campaign schema 2 and play schema 3 are unchanged. The new per-ID envelopes are additive. Downgrade still requires the stopped-world backup and matching older jars; older code does not know the new archives and must not be used to continue a maintained world.

## Failure and retry behavior

If maintenance stops after archive publication but before live replacement, run it again: exact duplicate publication is idempotent. If source pruning completed but progress cleanup did not, the next run trims seen IDs against the current live ledger before considering new candidates. A corrupt archive must be repaired from stopped-world evidence or backup; maintenance will not overwrite it. Partial success across source, flag and batch stores is conservative and rerunnable.

No source world, Event plan, snapshot, execution archive or player-facing history is removed by this operation. Per-ID archives trade additional filesystem entries for durable exact lookup and reclaimed bounded live capacity.

## Focused verification

The combined Core compilation and full `:core:test` suite passed in 1m7s; the retained log is `../reports/deferred-core-tests.log`. The later integrated build passed both module suites and remaps in 2m22s (`../reports/deferred-integrated-build-3.log`): 675 tests, 668 passed, seven Windows symlink-privilege skips.

The real-client role/history run `../reports/player-roles-20261101T153444612661Z` passed initial and restart phases. Actual role-assignment and first-entry Events completed, source/flag records retired, exact archived flag retries returned no-op, and a repeated maintenance run reclaimed zero additional records. Reopening the world retained flags, role/start markers and inventory behavior without another kit. This fixture has no Region batch; batch capacity, immutable plan and interruption behavior have separate store tests and existing runtime proofs.

Repeatable commands:

```powershell
./gradlew.bat --no-daemon :core:test --tests "io.github.chronicle.core.history.ImmutableHistoryArchiveTest" --tests "io.github.chronicle.core.progression.SourceLedgerTest" --tests "io.github.chronicle.core.campaign.CampaignServiceTest" --tests "io.github.chronicle.core.play.EventProgressStoreTest" --tests "io.github.chronicle.core.play.EventPlayStoreTest" --tests "io.github.chronicle.core.server.EventBatchFilesTest" --tests "io.github.chronicle.core.server.EventHistoryMaintenanceRulesTest"
./gradlew.bat --no-daemon :core:test
python -m unittest discover -s scripts -p "test_*.py" -v
git diff --check
```

The focused tests cover exact retry/conflict behavior, corrupt archive refusal, archive-before-prune failure, preservation of PREPARED records and latch/countdown pins, all-subscriber source safety, automatic cancelled-record eviction and reclamation beyond source/flag/batch live limits. The native fixture invokes the same maintenance service directly; its new production command and Game setup routing still need the final combined menu verification before delivery.
