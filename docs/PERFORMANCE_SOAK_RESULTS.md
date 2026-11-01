# Three-hour vanilla-world soak results

Measured 28 October 2026, 19:01:10 to 22:01:10 UTC (20:01 to 23:01 Berlin). Source commit `d1e9f5499d722770b4612979dfc92cdc5afada07`, two controlled native clients, normal vanilla Ancient City terrain, 2 GiB server heap. Evidence remains under `../reports/long-soak-20261028T185441855389Z`.

**Duration completed and server timing budgets passed. The original overall result is FAIL, because one ecology availability counter exceeded its predeclared budget. Do not replace that raw verdict with PASS.**

| Measurement | Result |
| --- | --- |
| Wall-clock measurement | 10,800 seconds, 216,001 ticks |
| Mean tick rate | 20.0001 TPS |
| Full-run callback p99 | 25.9741 ms; budget below 50 ms |
| Full-run ecology p99 | 11.1990 ms; budget below 25 ms |
| Lowest ten-second TPS sample | 17.646; hourly median 19.998 to 19.999 |
| Campaign-owned sculk | 83,459 to 97,351; net +13,892 |
| Growth attempts | 9,627 |
| Missed eligible participant ticks | Zero |
| Active ecology counter | 181,277 / 216,001 = 83.92%; at least 80% budget passed |
| Unexpected unavailable counter | 13,424 / 216,001 = 6.21%; at most 5% budget failed |
| Garbage-collection time growth | 27.516 seconds summed JVM collection time |
| End state | FINISHED, clean server save/stop, readable JFR, review artifacts unchanged |

## Availability counter needs a more precise meaning

The failing counter is not an exception or a server freeze. All 1,076 ten-second samples have an empty engine failure/hold string, active play, two eligible clients, and no maintenance in progress. Unresolved topology was sampled during both planned travel and ordinary operation. No measured-run server overload warning or ecology engine failure was recorded.

`EcologySoakTrial` counts a tick as unavailable from the engine's end-of-tick `view().unresolved()` value. `EcologyEngine.view()` marks any queued graph work as uncertain, including an incremental extension queued by a successful growth operation in that same tick. Existing `rooted()` and `cropsAvailable()` deliberately permit a certified incremental extension. The counter therefore also includes normal network bookkeeping; it cannot establish that all those ticks represent lost growth or withdrawn player benefits.

The recorded data does not distinguish incremental extension, a full graph rebuild, validation uncertainty and an actual blocked growth step. It is therefore insufficient to determine the exact portion of genuine additional downtime. Preserve the 6.21% failed result, add explicit reason/operation counters and measure again before treating it as a gameplay stall or accepting a changed threshold. The 90-second travel and 90-second post-return exemptions remain the original rules.

## Memory and profiling

The recording is readable (approximately 258 MiB). Sampled used heap peaked at approximately 1.89 GiB; JFR shows brief occupancy closer to the 2 GiB cap. Hourly median sampled heap was 1,049 / 1,018 / 1,036 MiB. Hourly median post-GC event samples were approximately 624 / 577 / 573 MiB, with minima 462 / 463 / 471 MiB. Those observations show no clear increasing retained-heap trend in this run; they do not prove absence of a leak or ample headroom for more players.

Additional analysis files are `gc-heap-summary-analysis.json`, `jfr-hot-methods.txt` and `jfr-allocation-by-class.txt` in the report directory. Profiling identifies block/chunk lookups and collection access among sampled hot methods, but this report does not attribute generic JVM/vanilla samples to a particular Chronicle feature without caller analysis.

## Client shutdown reporting defect

Both clients remained eligible throughout measurement. Their final files nevertheless say FAIL at 22:01:15 UTC, after the server had finished at 22:01:10 UTC. This is a supervisor lifecycle issue: `long_soak.py` waits for client completion acknowledgements only when the budget decision is PASS. For a completed run with a failed budget, it immediately stops the server. A client whose next ten-second status poll has not yet seen FINISHED then reports the intentional disconnect as a failure.

The smallest repair is to wait for native completion acknowledgement whenever the server has completed its measurement, independently of the performance verdict, while retaining the original failed budget decision. Add a regression for FINISHED plus a failed budget. No gameplay repair is justified by these shutdown files, and no repair or additional long test was performed during this results analysis.

## Scope and next work

This is useful three-hour stability/timing evidence for two automated players, native city growth and repeated chunk travel. It is not a large-player-group, voice, combat, handcrafted-map balance or final release certification. The isolated test processes have ended. The organizer's review server and released client pack were not upgraded.

Next: correct the harness availability classification and completed-run shutdown handshake, then run a focused comparison and, if needed, another long test. Handcrafted-map integration and real group testing remain separate gates.

On 1 November the organizer authorized these corrections and a rerun. Their implementation and validation are tracked in [SOAK_RERUN_PLAN.md](SOAK_RERUN_PLAN.md) and [the telemetry v2 definition](PERFORMANCE_SOAK.md). The October raw FAIL report and this historical analysis remain unchanged by the new definition.
