# Corrected three-hour soak results

1 November 2026, 09:24:58 to 12:24:58 UTC (10:24:58 to 13:24:58 Berlin). Diagnostic sources `f49f50d` and `d59df71`; handoff `12e4ec7`. Evidence: `../reports/long-soak-20261101T091854118022Z`.

**All runtime measurement budgets passed. The final overall result remains FAIL because the server did not finish shutdown before the supervisor's 90-second timeout and was killed.** The original report is preserved. Its nested `measurement.result` is PASS; `report.result` is the authoritative overall FAIL. The `outcome` field retains its earlier pre-cleanup PASS value and must not be used as the final verdict.

| Measurement | Result |
| --- | --- |
| Duration / ticks | 10,800 seconds / 216,001 ticks |
| Mean tick rate | 20.0001 TPS |
| Callback p99 | 17.7727 ms; limit below 50 ms |
| Ecology p99 | 8.1286 ms; limit below 25 ms |
| Actual active ecology work | 89.55%; minimum 80% |
| Unexpected unavailable work | 1,765 ticks / 0.817%; maximum 5% |
| Unavailable reasons outside travel/recovery allowance | 1,762 topology rebuild ticks, 3 extension-wait ticks |
| Native participant continuity | Zero missed eligible ticks; two participants throughout measurement |
| Campaign-owned sculk | 83,431 to 95,927; net +12,496 |
| Growth attempts | 9,691 |
| Native completion acknowledgements | Both PASS, acknowledged before server stop |
| Work-count consistency | PASS |
| Original final server/JFR handoff | FAIL: forced stop; normal `server.jfr` is empty |

The measured interval has 1,076 ten-second samples, all with active play, two eligible players, no maintenance in progress and an empty engine failure/hold string. The lowest ten-second TPS sample was 18.461; hourly medians were 20.000 / 19.999 / 19.998. Hourly median sampled heap was approximately 1,055 / 1,056 / 1,036 MiB, with a peak sampled used heap of about 1.89 GiB. These samples do not show a steadily increasing heap trend, but do not prove absence of leaks or group capacity.

## Previously reported issues

The corrected measurement retains 33,104 raw unresolved snapshots and 10,526 pending incremental-extension ticks separately from actual operations. Only 1,765 unavailable ticks fell outside the original travel/recovery allowance. This confirms that the old snapshot counter could not be treated as a direct measure of blocked gameplay; the original October FAIL remains unchanged, and it is not retroactively regraded.

Both native clients read FINISHED and exited successfully at 12:25:01.977 and 12:25:04.048 UTC. The corrected completion handshake succeeded. There are no post-stop false client failures in this run.

## New shutdown issue

After the last client disconnected, the campaign journal was approximately 27.76 MB. The automatic maintenance policy regards an empty server as quiet and can compact above 16 MiB when its growth/cooldown conditions are met. At 12:25:04 UTC a source snapshot was created; the prepared checkpoint was created at 12:25:09 and continued being written until 12:26:35.

The stop command was processed at 12:25:05. `QuietBelowEcology`'s SERVER_STOPPING callback joins an existing maintenance job before releasing the world. `fabric_smoke._stop`, reused by the supervisor, waits only 90 seconds, then kills the process. The log ends at `Waiting for ecology maintenance before releasing world ownership`; the normal final world-save/stop messages are absent.

Recovered JFR CPU samples cover 12:25:05 to 12:26:23 UTC and show the maintenance worker executing checkpoint writing, journal append/canonicalization and replay. This is evidence of ongoing work during most of the wait, rather than a wholly idle worker. It does not establish the exact time needed to finish or rule out every possible later failure. The retained source journal and its snapshot have identical SHA-256 `599e570eb8db8547707c786b7a3aa27562d2456d588bb2d73428f499ab4af835`; the prepared candidate was not promoted. This is not a certification of the unsaved Minecraft terrain after the forced stop.

This is a post-measurement maintenance/shutdown coordination issue, not a runtime TPS failure or the already-fixed client acknowledgement problem. The original `error: null` describes the measurement loop; `shutdownError` and the final FAIL still indicate a failed handoff.

## Profiling recovery

The normal dumponexit recording is zero bytes because the VM was killed. A separate `recovered-complete-server.jfr` was assembled from 20 closed repository fragments and successfully read with the JDK `jfr summary` tool. One open/locked final fragment was excluded; raw fragments and the original report remain untouched.

The recovered file is 254,608,689 bytes and covers 09:26:02 to 12:26:23 UTC. It therefore covers most of the measured run plus most of the shutdown wait, with the first approximately 64 measurement seconds absent from retained fragments. It is partial recovery, not successful original finalization. Evidence: `jfr-recovery.json`, `recovered-complete-jfr-summary.txt`, `shutdown-profile-analysis.json`, and `terminal-execution-samples.json` in the report directory.

## Next work

Reproduce shutdown with a disposable copy of the retained city-scale journal, then address maintenance drain/throughput and the supervisor's bounded stop strategy. Preserve transaction verification and world ownership; do not cancel/promote a checkpoint blindly or merely relabel the run PASS. Capture a profiler dump and progress/thread evidence before any forced stop, and make all final outcome fields agree after cleanup.

A focused shutdown regression can exercise the retained size without another three-hour growth run immediately. No gameplay repair, additional test run or shutdown code change was performed during this check. All four isolated processes have ended; the review installation and pack were not deployed or modified. Keep the world, snapshot and prepared candidate for the focused diagnosis. Real group, voice and handcrafted-map testing remain separate gates.
