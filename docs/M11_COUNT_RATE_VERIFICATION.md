# Count-based spread verification - dev.41

The operator accepted dev.40 on 23 October, including enclosed connected sculk
remaining alive. This is point 4 of POST_M12_REVIEW; finish and independently
review it before physical Heart/configuration corrections. Deliver one combined
review client after subsequent independent work, not an intermediate client.

Keep the saved FIXED/SCULK_COUNT formula, amplification choices and 16x cap.
Expose the actual conservatively discounted counted-cell value used by the
formula, rather than the possibly stale raw census. Add an ephemeral attempt
counter for diagnosis, explicitly since engine load; no persistence/wire schema,
extra scan, chunk ticket, write allowance or gameplay clock change. An attempt
is not guaranteed to find a surface or produce a block.

A guarded disposable native fixture has a small source connected to a roughly
4,096-cell reservoir by a removable three-block bridge over a real air gap.
Naturally loaded chunks contain both. Use the same engine, Heart, base rate,
night lighting and physical growth rules. Compare 400 actual server ticks each
of small/fixed, small/count, large/fixed and large/count at +100% per 1,000 so the
effect is unambiguous. Record real attempt deltas, actual successful cell growth,
counts/effective percent/quantized intervals and timing. Verify dormant detached
reservoir cells do not count; fixed mode remains independent of size. Cut and
reconnect through the existing physical graph, never fabricated metadata.

Add focused cadence/diagnostic regressions, build/module checks, and an actual
client/server rehearsal with visible Game setup > Spread mode facts. Leave a
clear labelled review fixture with a cuttable bridge, then pause it. Keep normal
campaign defaults distinct from deliberately amplified test settings. Reuse the
existing authenticated review address only after the remaining sequential
corrections and verification; save-stop/CRC backup first. Existing human world
and accepted dev.40 pack stay running while implementation is in progress.

Core remains unchanged: this campaign-specific measurement, rate policy and
fixture belong in Quiet Below. The generic operator UI renders existing facts.
Record failed attempts and exact evidence; independently review source and
measurements before marking this correction ready.

## Verification complete

Focused `EcologySpreadTest` passes five cases, including the exact conservative
count after a confirmed loss and diagnostic attempt cadence/reload behavior.
Independent source review is clear. A preflight client inspection caught a
teleport feedback-handler mismatch before any measurement; the redundant
teleports were removed and camera positioning remains server-owned. A later
staging attempt hit the workspace disk limit before launching gameplay. Seven
explicitly retired completed runtimes were removed after path/reparse/process
and saved-stop evidence checks; their reports and the old unexplained dev.39
reproduction remain. See `../reports/count-rate-disk-cleanup.json`.
`python scripts/spread_smoke.py` passes in
`../reports/spread-smoke-20261023T150631390393Z/report.json`: 18 initial and
seven same-world restart checks, with three inspected screenshots and a saved
`spread-measurement.json` outside the disposable world. Each sample measures
400 actual server ticks:

| Sample | Living count | Effective percent | Interval ticks | Attempts | New cells |
| --- | ---: | ---: | ---: | ---: | ---: |
| Small / fixed | 35 | 100 | 20 | 20 | 1 |
| Small / count | 36 | 103 | 19 | 21 | 5 |
| Large / fixed | 4145 | 100 | 20 | 20 | 11 |
| Large / count | 4156 | 515 | 4 | 100 | 64 |

The fixed samples retain identical attempt cadence; the large connected count
sample visibly amplifies it. The three-block bridge reconnects the real bank,
while the disconnected bank is excluded from the small-network count. Actual
block yield varies with available surfaces and is not promised by the rate.
The first full passing run used an overly tight camera; the final run above
corrects that and independently clears both the matrix and visible diagnostics.

`python scripts/workspace.py build` passes: 178 Core + 174 Quiet Below tests,
zero failures or skips. The Python suite has 77 passing cases. The retained
`../test-runs/20261023T150631390393Z` fixture is paused and available for the
combined review preparation. Existing authenticated dev.40 remains untouched.
Point 4 is independently verified, awaiting the combined human review; physical
Heart implementation may now proceed.
