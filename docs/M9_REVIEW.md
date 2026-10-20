# Surface ecology review

Use **dev.14** with Minecraft Java **1.21.10**. Import `../client/Chronicle-M9-1.21.10.mrpack`, or replace both Chronicle jars in your existing matching Fabric profile. This build adds a menu action, so update both client mods.

Join **`localhost:25585`** for the disposable ecology map. `localhost:25565` remains your usual map, with ecology unconfigured/disabled. Both servers keep authentication and the whitelist; SchinderRenge remains an operator.

## Start the experiment

1. Enter the lit viewing gallery beside the glass. The copied test corridor already contains some living growth; the world starts paused.
2. Press **O**, open **Players**, select your account and **Add participation**. `/chronicles join` is the shortcut.
3. Open **Session**, select the play controls and **Resume play**.
4. Open **Game setup > Sculk ecology** for status and **Refresh status**. The network is already indexed and enabled in this disposable copy. Session pause freezes it; **Disable growth** stops this campaign's ecology while retaining terrain/history.

The glass is a physical boundary, not a protected region. Open it or another passage to allow a real new route. Stone can become sculk; wood and other non-substrate solids can carry veins. Try pruning a branch and sealing its passage. Remove all living surface connections when cutting a branch; a wall does not erase a branch that is already connected beyond it. Keep the source connected if you want to observe continued expansion.

Review whether the visible progression and effects of opening/sealing/pruning feel coherent. Rates and tier thresholds are technical defaults. The reinforced-deepslate source anchor is temporary: actual Heart/Forge/Altar blocks, crop benefits/costs and the Heart ending are following milestones. M9 still needs gradual vitality/decline, configuration, large-city balance/soak and a complete vanilla Warden rehearsal before production activation.

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
