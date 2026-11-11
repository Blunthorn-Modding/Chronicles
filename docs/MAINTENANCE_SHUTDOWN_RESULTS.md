# Captured-journal shutdown regression

1 November 2026, branch `fix/maintenance-shutdown-drain`. This focused regression follows the [corrected three-hour soak](SOAK_RERUN_RESULTS.md); it does not regrade that run or repeat its multiplayer workload.

**PASS:** maintenance of the retained 95,927-cell journal drained before normal world saving, and a fresh server reopened the copied world with all 95,927 recorded cells. Both server processes exited normally in approximately 8 seconds after stop was sent.

## Repair

Ordinary live-journal appends still force each record to disk. Only a new, private `.prepared` automatic-compaction candidate batches those forces until close. Close must successfully force the candidate before independent replay/hash/state verification and any atomic promotion. Existing targets are refused; a failed force cannot promote the candidate. Source locks, identity checks, output limits, archival and rollback remain in place. Offline manual maintenance is unchanged.

Shutdown declares the runtime stopping before draining its worker, prevents new maintenance from starting, and reports the current maintenance stage every ten seconds while waiting. It never cancels an in-flight transaction or releases world ownership underneath it.

The long-soak supervisor now takes an explicit pre-stop JFR snapshot, allows up to 300 seconds for a graceful stop with progress updates, and captures profile/thread diagnostics before killing its own process if that deadline expires. A diagnostic command reporting success is insufficient unless its expected recording actually exists and is nonempty. Final `result` and `outcome` are calculated after cleanup and agree. The shared short-smoke timeout is unchanged; a backup recording never substitutes for a clean stop and readable normal recording in a passing soak.

## Native evidence

Reports: `../reports/maintenance-shutdown-20261101T143727105349Z`.
Disposable world: `../test-runs/20261101T143727105349Z`.
Command: `python -u scripts/maintenance_shutdown_smoke.py` after building the two server jars with the workspace Java/cache environment.

The harness copies the retained November world and checks every original file hash. It uses the normal automatic-maintenance policy and actual journal, not a smaller synthetic checkpoint. A guarded native fixture holds a real worker before its work starts, waits until the production stopping callback is draining it, then releases it. A callback after the production drain proves maintenance finished before vanilla final saving. A separate JVM then verifies fresh replay and saves/stops again.

| Check | Result |
| --- | --- |
| Captured source journal | 27,756,679 bytes; 95,927 cells |
| Stop during held production maintenance | Clean exit code 0 in 8.0195 seconds |
| Fresh reopen and second saving stop | 95,927 cells; clean exit code 0 in 8.0197 seconds |
| Production drain proof | Worker completed before final vanilla save in both phases |
| Normal profiler recordings | Both readable with JDK `jfr summary` |
| Explicit pre-stop snapshots | Both present and readable with JDK `jfr summary` |
| Original source world and raw soak report | All checked hashes unchanged |
| Review server jars and client pack | Checked hashes unchanged; no deployment |

The source journal SHA-256 is `599e570eb8db8547707c786b7a3aa27562d2456d588bb2d73428f499ab4af835`. In this case compaction correctly kept the source: the full checkpoint did not meet the minimum saving within its output budget. Before/after journal size is identical. The successful result proves quick, safe completion of this maintenance attempt, not a size reduction or promotion of the old orphan candidate.

The first focused attempt, `../reports/maintenance-shutdown-20261101T143329894004Z`, saved cleanly in about 9 seconds but failed the profiler-backup check because Windows diagnostic filename quoting was wrong. That evidence is retained. The quoting and nonempty-file checks were corrected before the final passing attempt; both recordings were actually read in the final test.

## Verification and limits

- Quiet Below Java suite: 233 tests, 232 passed, one existing Windows symlink-privilege skip, zero failures/errors. Both server remap tasks passed (`../reports/maintenance-drain-build.log`).
- New candidate tests cover one force at close, exact replay, create-only/suffix guards and injected force failure with released file ownership. Existing compaction verification/rollback tests passed.
- Full scripting suite: `python -m unittest discover -s scripts -p 'test_*.py'`, 114 passed. This includes bounded shutdown, diagnostic failure, progress-write failure and final outcome consistency checks.
- Independent read-only storage/shutdown review found no material issue. `git diff --check` and Python compilation passed.

The earlier three-hour run retains runtime PASS / overall FAIL. This is a separate focused operational regression, not another three-hour performance result, arbitrary power-loss guarantee or final group/map certification. Both isolated JVMs ended; no review-server or pack update was made. Retain the original captured world for repeatable regression. Handcrafted-map integration, final content/balance and group/voice testing remain outstanding.
