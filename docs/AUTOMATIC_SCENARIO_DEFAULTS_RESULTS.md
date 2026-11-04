# Dev68 automatic scenario defaults

4 November 2026. Branch `feat/world-ready-scenario-defaults`. The organizer accepted dev67's content and sounds, then requested automatic default Event/flag loading for both fresh and prepared maps. The [storage and behavior contract](AUTOMATIC_SCENARIO_DEFAULTS.md) and [current setup guide](FINAL_DEFAULTS_GUIDE.md) describe the resulting workflow.

## Result

Fresh normal scenario worlds list all nine Quiet Below Events directly under Events and initialize four progression flags. Six Events are immediately ready; the sinkhole End-state target, city Region and dependent awakening remain visible setup drafts until mapped. These rows show `!` and a specific reason, including with Debug Off. Private preview works before map setup. Flags show linked updater problems; a normal Off value and manual-only flags are valid.

Ready defaults use the existing Event executor. Never-configured drafts can become ready when their expected map bindings are supplied during non-active, unheld play. Saving or deleting uses the existing editor/action flow. Atomic seen/draft markers prevent restart from recreating deliberately deleted Events. Saved configurations, source progression, queued occurrence identity, actor history and flag values take precedence. Only newly installed Events are armed. Existing Events are not silently upgraded to new default text, triggers or flag outcomes.

The configured-map test captures a real one-block End state, binds the city, executes the Sinkhole Event and verifies its terrain and flag outcome. A second server receives only that stopped world and the identical mod JARs, with fresh server configuration. It retains the exact Event fingerprints/pins, armed time, completion UUID, flag value and deleted-Event marker; the original stopped source hashes remain unchanged.

Core owns registration/initialization, persistence, diagnostics, exports and shared UI/commands. Quiet Below owns the four initial flag definitions and their narrative Event outcomes. Reusable content and settings were not moved: mod defaults and server overrides retain their existing homes. Map setup/live Events/flags remain world-owned. Reusable edits intended for distribution still use export and inclusion in the matching scenario mod. Custom-profile activation remains its accepted separate, manual backend workflow.

## Verification

| Check | Evidence |
| --- | --- |
| Full offline build/remaps | `gradlew.bat --offline --no-daemon build :campaigns:quiet-below:writeSmokeClientLaunch`, PASS2m47s; `../reports/scenario-defaults-release-build5.log` |
| Java | Core538 + QuietBelow242 =780 total;773 passed,7 existing Windows symlink skips; zero failures/errors |
| Python | `python -m unittest discover -s scripts -p test_*.py`,131 passed |
| New native fresh/copy workflow | `scenario_defaults_smoke.run(False)`, `scenario-defaults-20261104T165028848982Z/report.json`, PASS |
| Empty/custom profiles, edit/export/import/restart | `scenario_profile_smoke.run(False)`, `scenario-profile-20261104T165238718376Z/report.json`, PASS5phases |
| Native Event editor320x240 | `event_editor_smoke.run(False)`, `event-editor-smoke-20261104T165733452777Z/report.json`, PASS; frame inspected |
| Existing recurrence/recovery/history/audio | `foundation_smoke.run(False)`, `foundation-20261104T165901067741Z/report.json`, PASS including clean restart |
| Generator/document | `python scripts/prepare_quiet_below_defaults.py check`; original Word SHA256 unchanged |
| Audio/JAR/pack/auth/config preservation | `../reports/dev68-validation.json`, PASS |

The suite controller and exact report paths are recorded in `../reports/dev68-native-suite.json`. It compares final JAR bytes against both the copied-world and foundation proof artifacts. The earlier prototype copy proof is retained. Build2's two failures were assertions expecting PlayStore schema3 output; the tests now expect4 and still require unchanged exact legacy Event encoding. All original compatibility checks remain.

The new fixture opens the real list/detail and invokes private preview through normal transport, including a check that preview changes neither executions nor flags. Its map creation and Event configuration are trusted fixture APIs. The separate editor suite validates normal create/save and selectors; saving an incomplete default draft is not directly driven by the new fixture. Held promotion is tested natively, while the explicit ACTIVE guard is source-reviewed. The actual handcrafted-map/group/voice/balance gates remain outstanding. No additional three-hour soak was needed for this bounded setup/persistence change.

## Review deployment

All three authenticated, whitelisted servers run dev68 with SchinderRenge OP4: defaults **127.0.0.1:25591**, existing review **25589**, custom authoring **25590**. Use `../client/Chronicle-0.1.0-dev.68-1.21.10.mrpack`; all three presets, GUI2, narrator/onboarding settings, FancyMenu welcome suppression and licence notices are verified. The existing defaults-review world already contains nine saved Events, which are preserved rather than replaced by the new template versions.

Clean saving stops and CRC-verified backups precede the JAR swap. Main91/authoring82/defaults75 protected files were byte-identical at replacement. Post-start verification additionally confirms every existing server configuration file and role file is unchanged, all existing Event records and flag values remain, and Quiet Below defaults do not leak into the custom profile. The existing source-city assignment issue in the older main review is retained rather than silently rebinding user data.

Play state and scenario content now write schema4; earlier formats remain readable. Rollback to older mod code requires the corresponding stopped-world backup. Backup/install references, final hashes and server PIDs live in `../reports/dev68-review-deployment.json` and `dev68-validation.json`. The previous dev67 pack remains available. No main merge or Modrinth publication was performed. The unrelated Last Council brief was left untouched.
