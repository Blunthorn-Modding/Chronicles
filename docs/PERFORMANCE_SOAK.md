# Three-hour vanilla-world soak

This is an isolated performance test, not a review-server upgrade or a final-map/group certification. The organizer requested about three hours on 28 October 2026. Its artifacts and world are disposable after analysis; preserve evidence until then.

## Workload

- Minecraft Java 1.21.10/Fabric, normal vanilla Overworld generation, structures enabled, Normal difficulty, seed `quiet-below-city-soak-v1`.
- The existing native-city fixture locates an actual Ancient City and inventories its native sculk. It binds one reinforced-deepslate fixture Heart; it does not build spread routes or a synthetic chamber.
- Two real modded clients, `ChronicleSoakA` and `ChronicleSoakB`, participate in an active Chronicle session. The first remains near the city; the second moves among surface waypoints every 30 seconds. Both leave the city for 90 seconds every 20 minutes, then return, exercising ordinary chunk unload/reload without force-load tickets.
- Controlled Survival accounts are fed, invulnerable and allowed to fly. Natural terrain, mobs, daylight and ordinary world simulation remain enabled. This does not test human combat, voice or a large player group.
- Preparation uses Building Mode with view/simulation distances 3/3 for the existing unload/indexing proof. Strictly paused simulation cannot exercise ordinary world chunk unloading. Starting the active session clears Building Mode; measurement restores 20 TPS, view distance 12 and simulation distance 8. The three-hour wall clock starts only after source indexing, occupied active play and resolved connected ecology are certified and both clients are eligible.
- Server maximum heap 2 GiB; each client maximum heap 2 GiB, 640x480 window and 30 FPS limit. These processes share the host with the untouched organizer review server.

## Run and output

Build with the repository workspace Java/cache environment, serially with other native/Gradle jobs:

```powershell
python -u -c "import sys;sys.path.insert(0,'scripts');import workspace as w;w.run([w.REPO/'gradlew.bat','--no-daemon',':core:remapJar',':campaigns:quiet-below:remapJar',':campaigns:quiet-below:writeSmokeClientLaunch'])"
python -m unittest discover -s scripts -p 'test_*.py'
python -u scripts/long_soak.py --seconds 10800
```

For an unattended run, launch that Python command with a hidden detached `Start-Process` and redirected stdout/stderr. Keep the computer awake. The supervisor owns and stops only the processes it launches. TCP 25575 and UDP 24455 are exclusive to this test; do not start other native suites on those ports while it runs.

`../reports/long-soak-latest.json` points to the active/latest run. Each run has a timestamped `../reports/long-soak-*` directory and separate timestamped `../test-runs/*` world. The supervisor snapshots mutable project client classes/resources and launch configuration, and records exact server artifact hashes. Dependency caches remain shared: do not clean them during a run.

Output includes:

- `controller-status.json`: preparation/running/shutdown/final state and owned PIDs, world, artifact hashes, actual start and due times.
- `status.json` and `telemetry.csv`: ten-second samples of ticks/TPS, callback and ecology duration, heap, GC, CPU, chunks/entities, connected/owned/frontier sculk, growth attempts, journal size and eligibility/hold state. Telemetry v2 also reports actual tick work, cumulative work/reason counts and pending incremental extensions.
- `summary.json`: bounded full-run timing percentiles, GC/memory and ecology coverage totals.
- `client-*-status.json` and `client-*.json`: native connection heartbeats and final completion acknowledgement.
- `server.log`, client logs, bounded `server.jfr` recording (512 MiB / three hours), `jfr-summary.txt`, and `report.json` with the final decision.

Normal completion saves and stops the test server and closes its two clients. To cancel, create an empty `stop.request` in that run's report directory; this also performs a saving stop and preserves evidence. Preparation/server/client failures end the run explicitly. A first preparation attempt on this host exposed an unwritable default JFR temp location; subsequent runs use their own workspace temp and JFR repository. Another preparation exposed a driver retry gap during normal automatic journal compaction; the driver now waits for that exact transient maintenance response, within bounded deadlines. Runtime telemetry reads the existing engine safely while maintenance holds writes. Cancelled/failed preparation evidence is retained separately and does not count toward the three hours.

## Interpretation

Elapsed duration is separate from performance success. A performance PASS requires all of:

- completed requested wall-clock duration, observed mean TPS at least 19;
- full-run callback p99 below 50 ms and ecology p99 below 25 ms;
- both eligible clients throughout measured ticks;
- actual active ecology work during at least 80% of measured ticks;
- unexpected ecology holds during at most 5% of ticks (planned unload and 90-second post-return recovery are exempt);
- new growth attempts and an observed ownership increase;
- no recorded engine failure, both clients acknowledging completion, clean server shutdown, readable JFR, and unchanged review-server artifacts.

Telemetry v2 also requires internally consistent operation counts. The original 80%/5% thresholds, timing limits and travel/recovery exemption are unchanged. A completed measurement waits up to 25 seconds for both native clients to acknowledge FINISHED and exit successfully, even when a performance budget failed. Client success never changes a failed performance verdict into PASS. Missing, corrupt, wrong-shaped or unsuccessful acknowledgements fail a successful result while still allowing server cleanup.

## Telemetry v2, 1 November correction

The engine records the branch actually reached in `tick()`, without changing its work order, rules, budgets or durable state. `COUNTDOWN`, `GROWTH_ATTEMPT`, `CONNECTION_ADOPTED`, `WRITE_BUDGET` and `HEART_DEFENCE` record active ecology work. Unknown topology, full rebuilds, actual extension waits, disconnection, skipped/paused/disabled ticks and maintenance have distinct inactive labels. For example, a successful growth attempt that queues a new connection at the end of its tick remains a growth attempt; a later tick that actually returns while waiting on graph work is counted separately.

JSON `tickWorkCounts` accounts for every measured tick; `unexpectedTickWorkCounts` accounts for unavailable ticks outside the existing exemptions. `incrementalExtensionPendingTicks` and the original `unresolvedTicks` show pending end-of-tick graph work independently of actual operations. The CSV includes the current `tickWork` and cumulative activity, unavailability and extension counts. `telemetryVersion: 2` distinguishes the corrected definition. The supervisor rejects missing/inconsistent v2 counts; old v1 reports retain their original interpretation and are never retroactively regraded.

The observer captures maintenance at START as well as END. A tick where maintenance finishes cannot inherit the last active engine label. These diagnostic fields are ephemeral and do not add blocks/entities, change the journal schema, or alter gameplay decisions. Plan and acceptance checks: [SOAK_RERUN_PLAN.md](SOAK_RERUN_PLAN.md).

Startup also uses the engine's existing gameplay readiness proof. A newly queued, certified incremental extension is allowed; a genuine temporary rebuild responds `SOAK_WAITING` within the driver's existing timeout. Invalid configuration, indexing, enable/health or ended-state preconditions remain errors.

JFR/profile instrumentation adds overhead. The callback span runs from the Fabric start hook to this observer after ecology's end hook; it is not a claim to measure every possible later callback. Heap samples alone cannot establish a leak. An unexpected hold or a failed budget requires analysis of CSV/JFR/logs; three hours elapsing is insufficient evidence that the mod passed.

Build and scripting checks passed before launch. Startup evidence is recorded separately in `../reports/long-soak-launch.json` and the run directory. The 28 October run has finished; see [the results analysis](PERFORMANCE_SOAK_RESULTS.md) for its original FAIL verdict and the counter/shutdown limitations.

## Completed run, 28 October 2026

Reports: `../reports/long-soak-20261028T185441855389Z`; former controller PID 53992. Measurement ran **19:01:10 to 22:01:10 UTC / 20:01 to 23:01 Berlin**, followed by a clean saving stop. All isolated processes have ended. The complete native index, participant continuity and timing checks passed. **The overall report is FAIL:** the unexpected-unavailability counter was 6.21%, above its 5% budget. That counter also includes normal graph extensions; client shutdown files have a separate supervisor acknowledgement race. See [the results analysis](PERFORMANCE_SOAK_RESULTS.md). The original verdict remains unchanged; the 1 November correction and fresh rerun are tracked separately.

## Running rerun, 1 November 2026

Diagnostic source commits `f49f50d` and `d59df71`, branch `fix/soak-accounting-and-shutdown`. Measurement started at **09:24:58 UTC / 10:24:58 Berlin** and is due to end at **12:24:58 UTC / 13:24:58 Berlin**. Controller PID 27248, server 14728, native clients 22064 and 33132. Reports: `../reports/long-soak-20261101T091854118022Z`; latest pointer `../reports/long-soak-latest.json`. New native v2 samples show two eligible clients, advancing ticks/growth, complete actual-work counts and zero unexpected unavailable ticks at startup. **The three-hour result remains pending.** Startup evidence: `../reports/soak-rerun-validation.json` and the run's `startup-verification.json`.

The same seed, world generation, clients, distances, heaps, profiling and limits were verified against October's manifest. The preceding 60-second native preflight verified the fixes and clean completion on a failed timing verdict; it did not pass every performance budget. See [the verification record](SOAK_RERUN_PLAN.md). Leave the PC awake and do not run competing native/Gradle workloads or clean caches while measurement is running. Automatic saving stop and cancellation work as documented above. No review installation or client-pack update was performed.
