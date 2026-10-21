# QB-09 short local ecological costs

21 October 2026. The operator explicitly approves brief local Hunger in dense Established sculk and Darkness pulses in Extensive sculk, with in-game tuning. This follows accepted crops and implemented passive healing; reviewable features do not stop independent development.

## Behavior and ownership

Quiet Below owns the mechanism, native effect adapter, saved tuning and diagnostics. Core supplies only its existing participant eligibility and generic Game setup tools. No authored hazard regions, reveal/session boundary, corruption meter, persistent character changes, new entities or block entities.

A review default defines dense as eight distinct owned, physically rooted, loaded living cells (25%+ vitality) within Euclidean radius four of the participant's block position. The network must be resolved and Established or Extensive. Hunger I lasts five seconds; Extensive also permits Darkness for four seconds. Each effect has an independent fresh forty-eligible-second interval. First entry waits the entire interval. Large groups are served in turn and may wait longer. Leaving, cuts, weak/unknown sources, pause/holds, spectator/creative/author status, leaving participation, disconnect or restart discard credit. No catch-up or source stacking. Cached source proofs are rechecked every tick; replacement sources require fresh credit if that proof fails.

Game setup exposes separate Local Hunger and Local Darkness tools with enable/disable, faster/slower (10/20/40/60/120 seconds), restore defaults, density/range/duration and actual running/held conditions. Both default enabled at40s. These are review defaults, not settled balance.

Never overwrite, extend, shorten or remove another source's status effects. Native application skips a player already carrying that effect; skipped attempts still consume the interval. Do not manually clear effects on exit/pause: an already delivered pulse naturally expires within its short duration. Final application rechecks player identity, eligibility, actual position, density, extent, play and saved rules. Each effect is amplifier0 with no ambient/particle spam. Ordinary vanilla warnings and Warden Darkness remain vanilla.

## Bounded work and persistence

At most256 eligible players; one round-robin player search and at most two effect attempts per tick. Eight cached metadata-only witnesses per tracked player plus at most257 search positions and257 final guard positions per effect, bounded by2,819 positions/tick when both apply. No full network copy, world scan or chunk tickets. Fail closed on participant capacity/native exception; keep ecology running and show the held reason. Tuning clears derived cooldowns.

Add strict COST_RULES records and checkpoint format3 with both rules; older format1/2 defaults these rules. Preserve existing journal/JSON/capacity validation and exact maintenance equivalence. No Core wire/registry/block-state/asset changes; new saved records require the matching reader. Rollback restores a stopped backup and its jars together. Preserve prior human review worlds and packs.

## Verification and delivery

Pure tests: exact delay/interval, density and radius, global extent, proof loss/re-entry, eligible clock/pause, player rotation, no stacking, both independent effects, capacity/failure and hard work bounds. Strict persistence/checkpoint legacy/default/nondefault/corruption coverage. Native disposable survival participant checks actual short effects, distant/creative/nonparticipant/pause/cut exclusion, foreign-effect preservation, menu tuning and restart/default restoration. Rendered tool navigation should remain usable at small/large viewports. Run full Java/Python checks and relevant native ecology regressions sequentially on shared ports. Prepare an authenticated separate or safely stopped-and-backed-up combined review bench only after verification; bundle healing/cost feedback. Document exact evidence and remaining balance questions without claiming M9/M10 complete.

Verified delivery:243 Java tests/52 Python tests,503 baseline native assertions and104 focused cost/restart assertions pass. See TEST_PLAN for exact commands, retained development failures, scope and finite-test limits. The separate authenticated combined bench is running PAUSED on25588 at `../reviews/costs/20261021T131343506328Z`; default40s costs and10s healing, cleanup off. Pack26 is verified and the old pack backed up. Balance feedback remains open, and does not stop independent work. Station product choices are pending separately in M11_STATIONS.md.
