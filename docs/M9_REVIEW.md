# Surface ecology review

Use **dev.16** with Minecraft Java **1.21.10**. Import `../client/Chronicle-M9-1.21.10.mrpack`, or replace both Chronicle jars in your existing matching Fabric profile. The dev.16 pack adds exposed-corner recovery and saved spread-speed controls. The existing dev.14 client also supports these generic tool controls and private outlines.

Join **`localhost:25585`** for the disposable ecology map. `localhost:25565` remains your usual map, with ecology unconfigured/disabled. Both servers keep authentication and the whitelist; SchinderRenge remains an operator.

## Start the experiment

1. Enter the lit viewing gallery beside the glass. Your previous review geometry/history is preserved. In **Game setup > Sculk ecology**, choose **Show root**, then close the menu. The temporary Heart at **0, 64, 1** has a private outline; **Hide root** removes it.
2. Press **O**, open **Players**, select your account and **Add participation**. `/chronicles join` is the shortcut.
3. Open **Session**, select the play controls and **Resume play**.
4. Open **Game setup > Sculk ecology** for status and **Refresh status**. The existing review network is indexed and enabled. Freshly prepared copies are indexed with growth disabled: while paused, enable editing and choose Enable growth, then resume play. Session pause freezes it; **Disable growth** stops this campaign's ecology while retaining terrain/history.

The glass is a physical boundary, not a protected region. Open it or another passage to allow a real new route. Stone can become sculk; wood and other non-substrate solids can carry veins. Try pruning a branch and sealing its passage. Remove all living surface connections when cutting a branch; a wall does not erase a branch that is already connected beyond it. A detached branch stays dormant. Place sculk across the cut to reconnect it: it joins through physical contact with the Heart or living network during occupied active play. There is no catalyst range or expansion-node chain. Refresh status shows connected/detached counts and the current reason growth is waiting. Your previously replaced bridge is left intact and should reconnect naturally on resumed eligible play.

Review whether the visible progression and effects of opening/sealing/pruning feel coherent. Rates and tier thresholds are technical defaults. The reinforced-deepslate source anchor is temporary: actual Heart/Forge/Altar blocks, crop benefits/costs and the Heart ending are following milestones. M9 still needs gradual vitality/decline, broader balance configuration, large-city balance/soak and a complete vanilla Warden rehearsal before production activation.

## Spread speed and exposed corners

In **Game setup > Sculk ecology**, pause play and enable editing to use **Slower**, **Faster** or **Reset speed**. The saved range is **0.25x to 16x**, with **1x** as default. The menu shows the dark/bright attempt intervals at 20 TPS. Pause/empty play does not build a backlog, and changing the pace does not remove the work/write limits. These are growth attempts; sealed or blocked surfaces may progress more slowly. Speed is retained after restart.

Existing sculk discovers its missing exposed faces when eligible play resumes. Your current test structure and ownership remain intact; the corner fix needs no reset/reindex. Try the same outer edge, as well as top/underside surfaces. Sculk should wrap exposed surfaces without excavating enclosed stone. [Repair specification](M9_SURFACE_SPEED.md).

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
