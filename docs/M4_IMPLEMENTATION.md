# M4 bounded environmental stages

Status: implemented campaign slice; runtime evidence is recorded in TEST_PLAN.md. M3's real-client content pipeline passed before this slice started.

The campaign owns this feature. Core retains pure flag events, session state and dossiers. A GM explicitly approves a small coordinate template; nothing grows automatically and the default world has no zones. Only passive vanilla sculk is placed, with no catalyst, sensor, shrieker, block entity or forced chunk loading.

## Commands and limits

Operator level 2 is required for all `/quiet_below` stage commands. `/quiet_below zone define <id> <from> <to>` captures at most 64 loaded positions in the caller's dimension. Every position must be inside the world height/border and contain an explicitly supported natural block: stone, deepslate, cobblestone, cobbled deepslate, tuff or calcite. Reject the whole request if any position fails, including block entities. Natural material is not proof of ownership: defining the coordinates is the GM's explicit approval of those exact positions.

`stage preview <zone>` lists exact coordinates and current/desired states, returning a deterministic 12-hex confirmation token. `stage apply <zone> <token>` requires ACTIVE, the same captured states, loaded chunks and matching token. At most eight positions are processed per server tick. Pause/end suspends work. `stage status` explains progress/conflicts; `stage resume` is explicit after restart; `stage undo` restores only the latest apply operation, including a partial/interrupted apply, and also requires ACTIVE. Undo the latest operation before defining/applying another stage. No arbitrary block replacement, overlapping concurrent jobs, or unattended retry. There are at most 32 zones and 256 retained audit entries.

## Durable intent and recovery

Store version-1 JSON (maximum 2 MiB) as `quiet-below/stages.json` under the world using atomic replacement and a previous-file `.bak`. Store namespaced state identifiers/properties, dimension, ordered positions, before/after states, operation UUID, actor UUID/name, timestamp and progress. The operation must exactly match its referenced zone's dimension and ordered capture. Persist the complete intent before the first world mutation and checkpoint progress after each tick batch. Fail closed on corrupt/future/inconsistent data.

Before applying or undoing, preflight all positions; each tick preflights remaining positions. A position may only contain its recorded before/after state; any other state is a conflict and stops work without overwriting a player edit. Never load/generate a missing chunk. Restart converts in-flight work to INTERRUPTED; it does not silently resume. Explicit resume preflights the entire template and revisits it from the beginning, safely skipping states already at the desired result and reconciling world/journal save order. A persistence failure after a world write reports RECOVERY_REQUIRED, retaining durable intent rather than claiming no change occurred. Completed stages are never reapplied merely because the world restarted. Backups remain required for crash recovery beyond this journal's guarantees.

The separate once-only Core event `quiet_below:mine_whisper` sets `quiet_below:fragment_revealed`, updating public marker observations and lens detail. It has no world side effects and no mandatory item gate.

## Acceptance evidence

Pure-Java fake-world tests cover limits, exact templates/token invalidation, authority/session gates, batch rate, pause/end, conflict stops, safe undo, strict persistence and interrupted recovery. Dedicated-server tests with matching Fabric clients exercise a small disposable zone, preview/apply, paused suspension, player-edit conflict, undo and restart. Inspect the staged world's actual saved states/audit. Preserve M1/M2/M3 regressions and never configure a stage in the operator's play world for testing.

M5 supplies a reproducible separate pilot location and session notes with multiple ways to discover clues. No forced story outcome, quest NPC or combat requirement is added.
