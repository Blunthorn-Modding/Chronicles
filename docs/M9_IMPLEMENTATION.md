# M9 — Quiet Below ecology

Status: implementation in progress, first playable ecology review. QB-08/QB-09; this is an engineering checkpoint, never a story/session schedule.

## Authority and first review

The operator confirmed that **nothing is protected**: natural terrain, handcrafted buildings and new player placements follow the same physical spread rules. There are no protected-build flags or authored routes. Exposure feeds growth; there is no oxygen system. Native shrieker warnings, Warden summoning and despawning remain vanilla.

The first review delivers source indexing, durable ownership, connected surface growth, pruning/sealing and explicit controls in Game setup. Test it in a disposable cave before enabling any production network. Crop benefits/costs (M10), actual Heart/Altar/Forge blocks (M11), and Heart vulnerability/complete eradication (M12) remain separate. Early growth rates are review defaults.

## Ownership

Core owns the bounded generic Game setup tool registry, permissions and menu transport. Quiet Below owns indexing, block-state history, frontier traversal, native sculk attribution and ecology state. Existing bounded M4 stages retain their format and behavior.

Source city and Heart use existing Core map assignments, pinned to saved site IDs/revisions. Index only normally loaded chunks, in bounded slices; incomplete indexing cannot enable growth. Adopt existing source-city sculk automatically. Never silently adopt another city's sculk. Every grown block records its original state before changing terrain. Source-adopted features have no invented pre-city terrain.

Each adopted record explicitly declares `cleanup=air` and `adopted=true`; this is the proposed later removal baseline, not a claim that air existed before the city. That ending remains M12. Partial loss of owned vein faces records an observation retaining the original baseline. Player/foreign replacements lose ownership; a still-connected neighbor may later regrow there under the ordinary physical rules.

## Physical rules and work bounds

Growth follows reachable air along exposed surfaces. Eligible native substrate becomes sculk; other solid materials can support veins. No excavation, isolated remote patches, or distinction between authored and natural structures. A closed passage cannot supply a new air frontier on its far side. Pruned cells lose ownership immediately; replacing harvested sculk does not itself restore ownership/power. Detached remnants remain tracked for the later ending.

Only ACTIVE, occupied eligible play without a runtime hold advances ecology. Explicit indexing is metadata preparation and can run while paused. Pauses, empty servers and restarts accrue no catch-up. Never force-load chunks. Unloaded cells are unresolved, never evidence of weakness; the first review conservatively suspends expansion while connectivity is unresolved.

Use fixed per-tick scan/connectivity/write budgets and bounded cells/frontiers. Reaching a capacity or persistence fault holds work visibly. Changes use a durable append-only checksummed journal, not whole-network JSON rewrites. A torn, edited, unsupported or invalid journal is preserved and disables this service. Restart reconciliation observes actual blocks; it never resurrects a missing block from an intent record.

Review limits: source region <=16,777,216 blocks / 256 chunks; 256 scan positions plus <=64 adopted cells per preparation tick; 128 reconciliation and graph nodes per play tick; <=4 journaled world writes per tick, 32,768 owned positions and 131,072 discovered air cells. The append log is capped at 64 MiB / 150,000 records / 64 KiB per line. Capacity exhaustion holds growth; compaction is not implemented. On Windows the journal uses a no-delete-sharing handle plus an exclusive lock, creation-time/stat/tail checks; on providers with file keys it also checks file identity. Uncertain/torn writes preserve evidence and disable the service.

Initial rates: one surface change per 20 occupied ticks, shortened to 10 after a successful lit exposure (>7 light). No wall-clock catch-up. Measured connected size gives Rooted (<256), Established (256–2047) and Extensive (>=2048). Every 32 owned cells can offer a spaced native feature: sensor, established catalyst at a multiple of 96, extensive summoning shrieker at a multiple of 128. These are concrete test defaults, not final balance or a vitality/weakness formula. Features need an exposed owned sculk floor and spacing. Gradual detached decline, vitality recovery and configurable balance form the following M9 slice.

Stone seals the air path but may itself become sculk. If growth already exists beyond a wall, sever its actual living connections as well; closing air does not erase an existing branch. Non-substrate solids still support veins. This build conservatively drops ownership after foreign additions/replacement, rather than claiming player-placed material as campaign power.

## Native mechanics

Version-pinned hooks attribute owned catalyst descendants before writes and gate owned native intake/ticks while ecology is inactive. Unowned vanilla sculk remains vanilla. Native sensors/shriekers retain their behavior during play; grown extensive shriekers set `can_summon=true`. All native-hook behavior requires a real dedicated-server trial before production activation.

Queued owned shrieker warning responses are gated too, so an ACTIVE but empty play window cannot finish a delayed Warden response. The active warning/summon/despawn implementation is untouched. Owned catalyst writes into unloaded chunks are rejected before reading terrain.

The native manager also reads before attempting a write. Each queued cursor therefore needs its entire conservative +/-16-block chunk margin loaded before an owned catalyst ticks (at most 32 cursors x 9 cheap chunk checks). Pinned bytecode uses local 18-neighbor offsets and a largest direct horizontal scan of +/-4. Empty queues retain the normal fast path. No queue/charge is discarded by this loaded check. First activation certifies the complete ownership scan; additive growth extends certified connectivity without silencing existing native features, while real cuts and unknown cells require a bounded rebuild. Native cleanup and new growth share the four-write budget; budget exhaustion waits rather than becoming a permanent hold.

## Menu and persistence

`Game setup > Sculk ecology`: **Index source city**, **Enable growth**, **Disable growth**, **Refresh status**. Index/enable require non-active operator editing. Disable is available to operators during play. Status refresh is read-only; statistics do not invalidate controls on every growth step. Finite fallback: `/chronicles setup tool "quiet_below:ecology" index|enable|disable|refresh`.

Data: `<world>/quiet-below/ecology.jsonl`, strict schema-1 hash-chained envelopes with CONFIGURE / INDEX / INDEX_COMPLETE / MODE / GROW / REMOVE / OBSERVE payloads. Existing stores are unchanged. Source/Heart reassignment or revision changes hold the network rather than silently adopting new geometry. There is no destructive reset/reindex button. Restore a verified stopped-world backup for a discarded experiment. A failed world write durably disables growth when the journal remains writable; a crash before terrain persistence is reconciled conservatively without replaying the attempted mutation.

## Verification and review gate

Pure tests cover journal damage, replay, budgets, loaded-only reads, indexing, geometry, barriers, pruning, ownership and restart conflicts. Build both mods and matching clients. A fresh dedicated-server cave trial must verify growth, active/occupied gating, pause/hold/restart, native hooks and physical containment. Preserve production geometry and existing stores. Deploy only after saving stop, verified backup and artifact checks. Leave production ecology disabled for the operator's first ecology review.
