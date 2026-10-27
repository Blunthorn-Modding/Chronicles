# CF22 paused implementation checkpoint

27 October 2026. The organizer explicitly paused implementation for today. Resume only when asked.

The approved Events workflow is implemented in the working tree on `feat/core-event-region-actions`, following pushed design checkpoint `481df9e`. This is an incomplete implementation checkpoint, not a release. The accepted dev60 review server and client pack are unchanged.

## Current source

Core contains typed Event definitions, independent audience/trigger/Region pins, strict schema-2 PlayStore with schema-1 reads, immutable execution archives, one dispatcher for new Events and legacy cues, aggregate saved-Region batches, and the native four-page editor with shared selectors. Menu protocol is v8 and source version is dev61; do not install it until verified. Quiet Below remains the source of campaign defaults.

Independent review inspected models, persistence, aggregate batches and runtime. Fixed findings include the final-save/metadata gap, replay uncertainty re-holding the world, actual default configuration removal, pending/uncertain edit protection, and draft media preview requiring an unrelated Region configuration.

## Evidence and remaining work

- First coherent `:core:compileClientJava :core:test` with the play package, EventBatchFilesTest and EventEditorFormTest passed in 40 seconds. The earlier partial-menu compile was not a coherent checkpoint and failed on incomplete Context fields.
- Subsequent full `build :campaigns:quiet-below:writeSmokeClientLaunch` failed on three checked CommandSyntaxException paths in the new EventActionsTrial test harness. Those errors and two refusal-test false positives were corrected. No final compilation ran after these corrections and final menu/codec-test tweaks.
- `git diff --check` passes. Pure tests exercise strict persistence/history migration and bounded records, but do not establish native transaction or UI behavior.
- New `scripts/event_editor_smoke.py` and `scripts/events_smoke.py` have not run. Bootstrap registration is present. The editor harness uses the gated `prepare_ui` fixture and normal menu packets. The action harness includes paired Regions/NBT/chest/managed/lore, refusal cases, timer/manual gates, runner-controlled abrupt first-child termination, restart recovery, and media-claim uncertainty.
- The maximum-volume fixture currently rehearses exact 262144-cell / 64-chunk combined before-plus-after preflight only. It does not apply the full-size transaction. Do not claim full-volume paste readiness from it.
- Native wide/square/320x240 coverage, stale/authority checks, existing menu/Area/departure/cue regressions and final independent integrated review still need completion. A simulated network RuntimeException has source review but no injected native regression yet.
- No live deployment, new matching client pack, milestone completion or main merge occurred.

## Resume order

1. Recompile the final source and run the full configured build/test checks. Inspect the exact errors and preserve the accepted server.
2. Run native runners serially; all Gradle/native jobs share class directories and test ports 25575/24455. Correct harness issues without bypassing gameplay gates, and inspect actual captures.
3. Complete required interruption/conflict/volume evidence and proportional existing regressions. Update documentation to actual results and have the integrated diff reviewed.
4. Only after checks pass: save-stop the accepted review server, verify a backup, deploy matching server/client artifacts and prepare one combined organizer review. Do not erase the accepted world/config.

Live baseline: authenticated 127.0.0.1:25589, voice24469, `../reviews/contact/20261025T115145205840Z`, wrapper PTY58527 (recheck on resume), dev60 pack `../client/Chronicle-M9-1.21.10.mrpack`. Latest live receipt `../reports/combined-dev60-deployment.json`. External implementation logs: `../reports/cf22-compile.log` and `../reports/cf22-build.log`; PowerShell redirection may encode UTF-16. Mockup HTTP console19722 serves `docs/design` at port8925.
