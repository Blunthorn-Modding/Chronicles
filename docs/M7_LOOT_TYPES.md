# M7 revision: typed loot chests

Operator feedback, 19 October 2026: bounding outlines and registered chat choices are accepted. Capturing an inventory and separately binding an anchor/template is rejected as the main loot workflow. Core owns chest types and distributions; campaigns register default types, loot chances and recommended counts, with server JSON overrides.

Confirmed choices: independent chance per entry; fill on each new session, with optional refill every X active minutes; **replace all contents of bound loot chests** at each scheduled fill. These are managed loot containers, not player storage. No mandatory event timetable or ecology is introduced.

## Contract

Look at a supported container, choose a registered type in `/chronicle author chest`, and submit its one-step bind command. One atomic definition stores dimension/block/type/expected block; no named anchor/template prerequisites. Rebind/unbind/inspect target actions are explicit. Default type choices and tab completion come from each submod's startup registration; an advisory bound/recommended count appears for each type.

`config/chronicle/chest-loot.json` is generated on first server start. Each full type definition has an ID, label/description, recommendedChests, refillMinutes (0 disables periodic refill) and entries with item, chancePercent, minCount/maxCount and optional components. Chances are independent percentages, not normalized weights; each selected entry creates one bounded stack in a shuffled slot. All misses legitimately produce an empty chest and consume that scheduled fill. Configuration supports full type overrides and additional server-defined types; unknown/invalid items/components or malformed JSON fail validation. A non-active operator reload validates the whole candidate before publication and never resets receipts or rerolls chests.

A new ACTIVE session starts cycle 0. Resume/restart does not start a new cycle. Periodic time advances only while ACTIVE, occupied and free of runtime holds, excluding downtime/pause/empty time. Per-type intervals advance one generation when due; skip missed generations rather than burst refills. Queued fills process at most four loaded containers per second. Unloaded targets wait until loaded without forcing chunks; missing/replaced/unsupported targets are reported and left unchanged. When an unloaded chest returns, only the latest due generation is used.

Core persists an active clock, per-type cycle/due time and completion receipts with the existing inventory journal. Randomness is stable for a physical chest/session/cycle; preview/restart cannot reroll it. Every batch saves intent/before/rolled-after before replacement, closes viewers, flushes world saves, then commits receipts. Pending operations hold native simulation and use before/after conflict-preserving recovery. An all-empty/no-change roll is still a valid completed cycle. Keep only the latest typed receipt per physical chest; retain legacy once-policy receipts. Retyping/unbinding/rebinding the same physical chest does not reset its current cycle receipt.

Read schema 1 chest data and preserve all legacy templates/bindings/pending evidence; new writes use schema 2 with a valid previous-file backup. Keep the original workflow under `author chest legacy` for compatibility and recovery, outside the main picker. Preserve existing map sites/session/auth/config, stop/backup before deployment, and use a scoped feature branch.

## Verification and review

Pure checks: probability boundaries/independence/counts/reproducibility; strict JSON/default/override behavior; schema-1 migration; cycle/session/interval/pause/restart; replacement/nonempty/all-miss receipt handling; save failure and recovery. Real Fabric checks: non-op/ACTIVE edit denial, direct target binding and type choices/recommendations, server JSON overrides, session-start replacement, timer pause/resume, restart without duplicate refill, component preservation, unbind/retype identity and legacy recovery. Full build, scripting tests, diff review, isolated graphical tests and matching deployed pack precede human workflow review. Production map contents are never fixtures.

## Builder review

Install `../client/Chronicle-M7-loot-1.21.10.mrpack` and join localhost. During a non-active session:

1. Run `/chronicle author mode on`, look at a single chest/barrel/shulker, then `/chronicle author chest`. Click **Bind** for the desired type and submit. No name, anchor or captured inventory is needed.
2. Inspect/retype/unbind the target from that menu. `validate` shows the bound/recommended totals. The menu is paginated for larger catalogs.
3. Edit `../server/config/chronicle/chest-loot.json` and run `/chronicle author chest reload`. For a quick test, give bread 100%, iron 0%, and set refillMinutes to 1. Defaults use 0 (session-start only); these are starting suggestions, not settled campaign balance.
4. Start a new session and inspect the chest. Add a recognizable item, wait for the interval, and verify the entire inventory is replaced. Pause the session to check the timer stops; resume to continue the remaining time. Retyping does not immediately refill.

Legacy captured definitions are preserved, and personal/unbound containers are unaffected. A managed container's old contents are intentionally replaced at its scheduled fill. Future entity groups/automatic respawn and M8 conditions remain separate work; this review concerns the corrected chest model.

The active clock is checkpointed before each refill and on a clean stop, plus periodically while running. A crash with no refill may lose up to a minute of uncheckpointed elapsed time, delaying the next fill; saved receipts prevent duplicate generations. Rollback to the old binary requires the matching stopped backup because schema 2 is not readable by the previous build.

Delivery: implemented as `0.1.0-dev.8`, built and deployed to localhost after 80 Java tests, 17 scripting tests and isolated full/focused Fabric checks passed. [TEST_PLAN](TEST_PLAN.md) records evidence and the stopped backup. The next review is the corrected type picker and JSON distributions, before further M7 expansion.
