# M12 / QB-13: weakening and destroying the Heart

> 23 October correction: [Physical Heart and lens](M12_PHYSICAL_HEART.md)
> supersedes the right-click confirmation and binary appearance described below.
> Weakness, terminal intent and bounded chunk-receipt cleanup remain. Core keeps
> its reusable confirmation service for other features; the Heart stops using it.

Implementation contract, 22 October 2026. The operator approved the initial rule: fewer living Heart-connected cells than the Established threshold for 60 active seconds. Both threshold and delay are configurable; unresolved or unloaded branches prevent vulnerability.

## Behavior and ownership

Quiet Below owns weakness, the physical Heart, terminal ecology state and terrain cleanup. Core supplies a private, one-use action confirmation and the existing durable offering-source closure API. No narrative event or numbered session gates this ending.

Game setup gains **Relic Heart** with weakness status, a threshold (default follows Established), delay, and cleanup progress. Only occupied ACTIVE, unheld play advances weakness or terrain cleanup. Known-safe pauses preserve weakness; uncertainty, a missing root, a stronger network or changing weakness rules resets it. Restart requires fresh connection proof and starts a new weakness wait. There is no wall-clock catch-up. Living counts use actual vitality, independently of extent hysteresis. Bounded metadata counting stops at the configured threshold; worst-case work is the existing finite owned-cell limit and must be measured on the city fixture.

Right-clicking the bound Heart opens an explicit permanent-destruction confirmation. Acceptance rechecks the original binding, world, reach, participation, Survival/Adventure, play state and current weakness. Ordinary breaking, explosions, pistons and world writes cannot accidentally destroy or move the bound Heart. A supplied Relic Heart block is available for new maps; existing reinforced-deepslate anchors remain compatible without automatic map or binding replacement. Unbound copies confer no authority. The supplied Heart uses a brighter emissive model when vulnerable (client appearance only, without feeding nearby sculk through extra block light); a few soul particles also identify vulnerable legacy anchors during active play. These are derived visuals; the server rechecks the actual rule on acceptance.

The durable terminal ecology record precedes source closure and all terrain changes. A terminal network cannot be enabled, rebound into life, expanded, or used for benefits, costs or stations. Core closes `quiet_below:network` idempotently, withdrawing both Altar bonuses and sacrifices, including offline characters on return. If closure fails, retain terminal intent, hold terrain cleanup and expose the failure; do not promise rollback or reopen the network.

## Terrain and persistence

The terminal record freezes current ownership, including detached branches, plus the bound Heart target. Grown cells restore their recorded original terrain. Indexed, explicitly adopted and manually connected sculk have an AIR cleanup baseline and are removed. Preserve unrelated player replacements. For changed veins, remove only recorded campaign faces and preserve other faces/fluid. Never restore inventory contents or spawn drops.

Cleanup is loaded-only, at most 512 inspected targets and four writes per tick. Unknown chunks stay pending and are never force-loaded. One persistent attachment per affected chunk records job/plan identity and completed/conflicting targets. Completion receipts and changed blocks share the same chunk save, preventing a crash from saving an external DONE cursor ahead of terrain. Corrupt or mismatched receipts stop deletion and retain evidence. No sculk block becomes an entity or block entity. Restart re-verifies naturally loaded chunk receipts instead of trusting a separately saved global completion count. Later edits at completed targets remain untouched.

The ecology journal receives a single terminal transition, not one entry per erased cell; the frozen cell plan stays in the journal/checkpoint. New Heart configuration and terminal fields require checkpoint format 5, with legacy formats 1–4 receiving defaults. Terminal evidence prohibits further mutable ecology records. Restoring an older version requires a stopped pre-upgrade world backup and matching jars, never just jar downgrade.

## Required evidence

- Pure clock boundaries, pause/hold/uncertainty, dynamic/fixed thresholds and configuration resets.
- Engine strict replay, terminal irreversibility, frozen terrain baselines and checkpoint round trips (including legacy formats).
- Chunk-marker strict parsing/checksum/job/plan validation and preservation of corrupt raw evidence; bounded cleanup and conflict/vein/fluid rules.
- Native two-player consent/token and last-moment gate failures; accidental Root mutations blocked, copied block powerless.
- Actual source closure, local effect/station withdrawal, online/offline sacrifices and bonuses removed; root/source/detached cleanup, natural unload/reload and restart.
- Crash/save ordering, preserved later edits, and a finite city-scale work/heap/TPS measurement.
- Original supplied Heart model/texture provenance and native render/UI inspection at GUI scale 2.

Keep the existing authenticated dev.30 review, pack, world, bindings and tuning unchanged while this milestone is implemented and tested in disposable worlds. Upgrade only after saving stop and verified backup. Feature readiness alone is not a request for user review.
