# Surface ecology review

Use **dev.21** with Minecraft Java **1.21.10**. Import `../client/Chronicle-M9-1.21.10.mrpack`, or replace both Chronicle jars in a matching Fabric profile. This update makes sculk inventory and newly placed disconnected blocks visibly dormant. It retains the dev.20 inactive-feature gates and readable fading/zero palettes. New profiles retain GUI scale 2, narrator off, no accessibility onboarding and multiplayer acknowledgement. Layout version remains 1: dev.19/20 clients connect but require refreshed assets. Older clients are refused. Keep the earlier profile for unchanged dev.14 production.

Join **`localhost:25585`** for the disposable ecology map. `localhost:25565` remains your usual map, with ecology unconfigured/disabled. Both servers keep authentication and the whitelist; SchinderRenge remains an operator.

## Start the experiment

1. Enter the lit viewing gallery beside the glass. Your previous review geometry/history is preserved. In **Game setup > Sculk ecology**, choose **Show root**, then close the menu. The temporary Heart at **0, 64, 1** has a private outline; **Hide root** removes it.
2. Press **O**, open **Players**, select your account and **Add participation**. `/chronicles join` is the shortcut.
3. Open **Session**, select the play controls and **Resume play**.
4. Open **Game setup > Sculk ecology** for status and **Refresh status**. The existing review network is indexed and enabled. Freshly prepared copies are indexed with growth disabled: while paused, enable editing and choose Enable growth, then resume play. Session pause freezes it; **Disable growth** stops this campaign's ecology while retaining terrain/history.

The glass is a physical boundary, not a protected region. Open it or another passage to allow a real new route. Stone can become sculk; wood and other non-substrate solids can carry veins. Try pruning a branch and sealing its passage. Remove all living surface connections when cutting a branch; a wall does not erase a branch that is already connected beyond it. A detached branch stays dormant. Place sculk across the cut to reconnect it: it joins through physical contact with the Heart or living network during occupied active play. There is no catalyst range or expansion-node chain. Refresh status shows connected/detached counts and the current reason growth is waiting. Your previously replaced bridge is left intact and should reconnect naturally on resumed eligible play.

Review whether the visible progression and effects of opening/sealing/pruning feel coherent. Rates and tier thresholds are technical defaults. The reinforced-deepslate source anchor is temporary: actual Heart/Forge/Altar blocks, crop benefits/costs and the Heart ending are following milestones. M9b adds vitality loss/recovery and extent tuning; final balance, native large-city/load-order soak and production activation remain review work. Benefits, stations and the Heart ending are later milestones.

## Spread speed and exposed corners

In **Game setup > Sculk ecology**, pause play and enable editing to use **Slower**, **Faster** or **Reset speed**. The saved range is **0.25x to 16x**, with **1x** as default. The menu shows the dark/bright attempt intervals at 20 TPS. Pause/empty play does not build a backlog, and changing the pace does not remove the work/write limits. These are growth attempts; sealed or blocked surfaces may progress more slowly. Speed is retained after restart.

Existing sculk discovers its missing exposed faces when eligible play resumes. Your current test structure and ownership remain intact; the corner fix needs no reset/reindex. Try the same outer edge, as well as top/underside surfaces. Sculk should wrap exposed surfaces without excavating enclosed stone. New growth should form uneven branching patches rather than painting one complete line at a time. Existing lines are preserved and can thicken naturally; the repair does not redraw your build. [Repair specification](M9_SURFACE_SPEED.md).

## Vitality and recovery

**Game setup > Sculk vitality** shows average rooted/detached vitality, ready cells, feeding/sealed/unknown exposures and extent. A cut immediately makes a branch dormant. Its vitality falls, but its blocks and ownership remain; physical reconnection allows recovery even from zero. Rooted sculk fully enclosed by solids also loses vitality. Ordinary air exposure feeds recovery, twice as quickly above light level 7. There is no oxygen/airflow simulation.

Local growth and native sensor/shrieker responses require a physical Heart connection and at least **25% vitality**. New/legacy cells begin at 100%. For a quick review, pause play, enable editing and use Faster vitality loss / Faster recovery to select **1 minute** full loss/dark recovery (bright recovery 30 seconds). At default settings full loss takes 10 eligible minutes, full dark recovery 5 minutes. Only occupied ACTIVE, enabled, unheld/resolved play counts; downtime and unloaded-network uncertainty add no progress. Saved time is checkpointed every 10 seconds and on pause/hold/stop. Counts come from bounded assessments, so large networks can take several ticks to refresh.

Lower/higher extent thresholds select paired Established/Extensive counts, default **256 / 2048 ready rooted cells**. A 12.5% exit buffer keeps tiny changes from repeatedly switching extent. Unknown assessments retain the last known extent and hold advanced feature generation. Reset vitality tuning restores 10-minute loss / 5-minute dark recovery / default thresholds, without changing spread speed. No Heart vulnerability or benefit effects are claimed yet.

Try a clean cut, let the detached vitality fall, then replace the physical bridge and watch recovery/native responses return. Existing sculk is retained throughout. Return review tuning to your preferred settings afterward; the server preserves them.

### Read vitality in the world

- **Glistening blue:** feeding, Heart-connected sculk at least 75% vital. Uses the original animated textures.
- **Dull blue:** static, reduced vitality or a dormant/sealed connection, with at least 25% remaining. A fresh cut becomes dull while still retaining high vitality.
- **Brown with clear blue flecks:** positive vitality below 25%; local growth/native responses are disabled. The remaining static blue flecks distinguish it from exact zero.
- **Fully brown:** zero vitality. Blocks remain and can recover after physical reconnection and exposure.

The same colors apply to full sculk, veins, catalysts, sensors and shriekers. Native geometry/properties remain intact; the variants add no entities or block entities. Recovery moves back through the colors. Only occupied ACTIVE enabled/resolved play updates them, in bounded batches; a paused map retains its last appearance. Allow the vitality checkpoint interval (up to 10 seconds) and a short scan delay. Large patches can take several passes. Unknown/unloaded evidence keeps the previous color rather than claiming death. The Sculk vitality menu also explains the legend.

For a specific block, look at it within six blocks before opening **Game setup > Sculk vitality**. **Looked sculk** shows its position, vitality percentage, explicit positive/zero status, connection, displayed color and expected color. **Refresh status** rereads it using your current facing; it does not change health or load chunks. Very small positive values read `<0.01% (positive)` rather than rounding to zero. Unowned sculk is identified separately, without an invented health value.

In the configured Heart dimension, ordinary sensors and shriekers remain inert until campaign-owned, rooted and at least 25% vital during eligible play. Unowned/pending-adoption features cannot use vanilla behavior to bypass this. Inactive vibration queues are discarded before new particles/delivery; direct activation and warnings are blocked. Sensor output is zero immediately. Lingering native active phases and their old activation timers settle in bounded batches on native loaded feature ticks; fully paused simulation stays frozen until ticking resumes. Already-emitted particles can finish their existing flight. Active campaign warnings, Warden summoning and natural despawn still use vanilla mechanics. No new entities or block entities are added.

## Harvesting and replacing sculk

All five native sculk items show static brown inventory art, including existing stacks and creative stock. Items have no network connection; brown inventory art is not stored health. Survival loot/Silk Touch requirements, item IDs, components and stacking remain vanilla. A harvested shrieker does not retain summon capability.

In the campaign dimension, place a block away from living sculk: it should be brown immediately and sensors/shriekers should remain silent. Place it against the Heart or living network, then resume occupied active play: bounded adoption and color reconciliation select the appropriate appearance and native response. Paused play keeps new placements dormant. Extending an owned vein preserves its current band; unowned vein extensions become dormant, preserving face geometry and waterlogging. Existing vitality/adoption rates are unchanged; this does not transfer the old block's health through inventory. Outside the campaign, world placement remains vanilla.

## Ownership tools

Campaign catalysts no longer use deaths/XP to spread or sustain growth. This applies throughout the configured Heart dimension, so an unowned catalyst cannot create a second growth source while waiting for adoption/cleanup.

To preserve existing sculk elsewhere, pause play and enable editing. Create/select a saved region through Core's **Regions** page, assign it to **Game setup > Sculk takeover region**, then open **Sculk ownership > Take over region**. Keep its chunks normally loaded until status reports completion. Detached adopted patches stay dormant until physically linked. You can stop a takeover, retaining cells already adopted, or assign another region and repeat.

**Delete non-campaign sculk** enables optional persistent cleanup throughout the Heart's dimension. Adopt sculk to preserve first. It deletes non-campaign sculk, veins, catalysts, sensors and shriekers during occupied active play, including later-loaded chunks/new stray placements. It is off in your review world. **Stop sculk cleanup** stops future deletions; it does not undo prior deletions. Other dimensions remain unchanged. Cleanup waits for unfinished takeover work and never force-loads chunks.

## Local operation

From `Chronicles`:

```powershell
python scripts/ecology_review.py start
```

To create another separate review from the latest passing rehearsal:

```powershell
python scripts/workspace.py ecology-smoke
python scripts/ecology_review.py prepare
python scripts/ecology_review.py start
```

After a newly prepared review starts, its console can run `function chronicle_ecology_review:prepare` **once** to add the viewing gallery and set the safe spawn. Its chunks must be loaded by a nearby player. For headless test-map construction only, temporarily run `forceload add -16 -16 31 15`, allow loading, run the function, then `forceload remove -16 -16 31 15` and verify `forceload query` is empty. The prepared review has already completed this step. Ecology itself never requests those loads. There is no load/tick function. `../reports/ecology-review-latest.json` identifies the current review runtime. The helper refuses failed rehearsals, unsafe paths, changed artifacts and altered authentication/bind settings. Never run that gallery function in the production map.

Stop through the server console's `stop` command before backing up or replacing jars. A new review uses a new directory; it does not reset either existing world.

Vitality values and readiness advance at shared checkpoints (at most 10 eligible seconds apart); the displayed averages also wait for a bounded assessment. Physical cuts still stop activity immediately. At the fastest review rates, allow that checkpoint interval when timing recovery.

## Next crop review

The operator accepts the dev.21 pickup/placement repair. Dev.22 adds [automatic crop growth](M10_REVIEW.md), with a separate signed comparison bench at localhost:25586. The existing ecology map on 25585 is retained. Dev.21 clients remain compatible because assets/registry/network layout are unchanged; the refreshed pack is available at the existing filename. Larger M9 city/load-order soak and compaction remain tracked before prolonged production use.
