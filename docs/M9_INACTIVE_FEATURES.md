# QB-08: inactive native features and low-vitality readability

Implementation authorized 21 October 2026 on `fix/qb08-inactive-features`; this supersedes the previous notes-only turn. Quiet Below owns all behavior below. Core's generic setup UI remains unchanged.

Diagnosis: catalysts are managed across the configured Heart dimension, but sensor/shrieker guards check ownership first, allowing unowned or pending-adoption features to respond. Vanilla vibration ticking emits particles before the existing final callback gate. Already-active sensor power/phase can also outlast a cut. Existing tests mainly exercised already-owned callbacks starting in the inactive phase. Separately, the old fading full-sculk texture contains only nine cool pixels and no strong blue flecks; it is almost brown already. This reproduces a palette readability defect, but does not establish the exact health of the operator's previously observed block.

Contract:

- Manage sensors and shriekers in the same configured dimension as catalysts. Only owned, rooted, sufficiently vital features during eligible active play can respond. Unconfigured worlds/other dimensions retain vanilla behavior; active campaign warnings/summoning/despawn remain vanilla.
- Gate intake, final queued delivery, direct sensor activation and shrieker warning paths. Discard pending vibration data/selection before native ticking can emit particles; do not store a delayed response for reconnection. No new block/entity registrations or block entities.
- Quiet already-active native sensor/shrieker phases with at most 16 direct settling writes per server tick, independent of the unchanged four geometry writes and sixteen cosmetic writes. Sensor redstone/comparator queries return zero immediately while inactive; normal neighbor updates settle affected circuits. Native block entities do their existing loaded-only ticks; no full-world scan. Settling preserves waterlogging, summon capability and cosmetic state. Failure holds the ecology rather than repeatedly retrying a failed write.
- Keep four existing visual states and layout version 1. Improve fading contrast by retaining clear static blue flecks on the brown body, preserving native pixels/alpha. Exact-zero stays brown. No journal schema, terrain reset or balance/rate change.
- Add read-only per-target vitality feedback to the existing vitality tool, distinguishing exact positive values from zero and showing expected versus displayed appearance. Check ray chunks before raycasting; no forced loads or leaked player information.

Verification: retain a failing native unowned-feature regression and failing palette contrast check before repair. Verify owned/unowned, direct/queued, active/detached/low-health gates, phase clearing, output suppression, bounded settling, outside-campaign behavior and native object/property preservation. Use known positive-below-25% and exact-zero native states and inspect an updated gallery. Repeat real decline/recovery, restart, migration/compatibility and vanilla Warden lifecycle. Build/unit/assets/diff checks precede protected deployment.

Preserve the authenticated review's current map/tuning through saving stop, CRC backup and in-place dev.20 upgrade; generate the new client pack and leave paused. Production remains unchanged. Update operator/review/backlog/test records, commit/push this branch, no main merge. Rollback restores stopped backup and jars together. Larger city/load-order soak, compaction, benefits/stations/Heart ending remain separate work.

## Delivered for review

Dev.20 is installed in the preserved authenticated localhost:25585 review, PAUSED with growth on, cleanup off, saved 2x speed and 1-minute loss/recovery tuning. All 54 protected files and eight critical startup hashes are verified; source, installed jars and client pack match. Production remains unchanged and offline. The initial ACTIVE stopped review was backed up before recovery/pause; the paused upgrade has its own CRC backup.

195 Java / 27 scripting tests, a full 292-check ecology run plus final 90-check vitality and 75-check initial repeats, 61 appearance/legacy/compatibility and 106 aggregate Warden checks pass. See TEST_PLAN for exact evidence, retained negative regressions, final hardening and validation limits. The blue-fleck versus brown screenshots and per-cell positive/zero readout were verified. The next gate is operator review, not M9 completion.
