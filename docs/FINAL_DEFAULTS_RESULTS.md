# Dev67 final defaults and Event foundations

4 November 2026. Branch `feat/quiet-below-final-defaults`. This completes the deferred automatic history scheduler and bundled-audio implementation, then imports the organizer's final Word content. The [starting-content guide](FINAL_DEFAULTS_GUIDE.md) explains adoption and map setup; [audio provenance](QUIET_BELOW_AUDIO_SOURCES.md) records each recording and its licence.

## Delivered behavior

Quiet Below supplies six roles with legal starting stacks, nine named books, four editable loot tables and nine disabled Event templates. The source document is byte-identical to the supplied file: SHA256 `7e1536cb9e8a8b3506d6cc8514a9cc605002d390dd0be05b3c7eb8eee7076612`. Journal II uses the approved planks wording in generated content; the source document is unchanged. Map placement instructions remain organizer descriptions. No map coordinates, invented journal distribution or mandatory session timetable were added.

The Ancient City table is an independent-percentage approximation of Minecraft 1.21.10, with enchanted-book entries excluded. It retains plain books and native component data, omits random equipment enchantment functions and clamps potions to one legal item. Existing initialized configuration wins over packaged defaults. Empty custom profiles remain empty with mechanics installed. Survival/Silk Touch no longer drops placeable Altar/Forge items.

Core owns generic sound-catalog validation/handshake, Event recurrence, completion/fresh-entity sources, actor-only audiences and guarded automatic history scheduling. Quiet Below owns its eight Ogg recordings/credits, ecology source producers and final scenario content. Nothing downloads or streams from a website during play. All audio credits also travel inside the generated client pack.

Recurring Events claim exact source/configuration/recipients before media and archive completed executions before slot reuse. Once-per-player identity survives text edits and history archival. Repeated Events are immediate source-triggered text/sound only; terrain, encounters and flag changes remain once per campaign. Interrupted spawn metadata can be explicitly resolved alongside its parent Event hold without lifting that hold; unrelated holds and changed evidence remain blocking. Automatic archival waits for non-active play with zero online players and preserves immutable authority and campaign progress.

## Verification

The final machine receipt is `../reports/dev67-validation.json`. It records exact remapped JAR/pack hashes, test totals, authenticated server bindings and native proof directories. Automated clients are muted: successful Vorbis decoding and correct recipients do not establish audible quality or volume.

| Check | Evidence |
| --- | --- |
| Full offline build, Java regression suites and remaps | `gradlew.bat --offline --no-daemon build :campaigns:quiet-below:writeSmokeClientLaunch`; `../reports/final-defaults-release-build20.log`, PASS2m39s; 772 tests, 765 passed/seven existing Windows symlink skips |
| Final native fixture rebuild | `:core:remapJar :campaigns:quiet-below:remapJar :campaigns:quiet-below:writeSmokeClientLaunch`, `../reports/final-defaults-native-fixture-build21.log`, PASS30s; test-only failure injection correction after the full build |
| Python scripts and packaging regression | `python -m unittest discover -s scripts -p test_*.py`; 131 passed |
| Source document/resources | `python scripts/prepare_quiet_below_defaults.py check` |
| Audio source and remapped JAR | `python scripts/bundled_audio.py campaigns/quiet-below/src/main/resources campaigns/quiet-below/build/libs/quiet_below-0.1.0-dev.67.jar` |
| Native sound/font/recurrence/recovery/maintenance | `foundation_smoke.run(False)`; `../reports/foundation-20261104T085053897692Z/report.json`, PASS including saving stop/restart |
| Native Event editor at 320x240 and 640x360 | `event-editor-smoke-20261103T203703696005Z`, `event-editor-smoke-20261103T203819639618Z` |
| Five-phase empty custom profile lifecycle | `scenario-profile-20261103T203934743986Z` |
| Role/copied-map/export compatibility | `player-roles-20261103T204354248499Z` |

The native foundation proof decodes all eight clips, checks every final book page using Minecraft's font, captures real fresh Warden and Event-owned encounter spawns, verifies relative completion timing, private preview and first-player/repeated recipients, then exercises uncertain-media acknowledgement without replay and a full 256-claim queue with real GM flag recovery. Empty-server archival and a fresh restart preserve flags and first-player history. The final extension exercises source recovery under a retained parent hold and refusal under unrelated holds. Dedicated UI design and independent source review preceded acceptance; review findings were corrected and regression-tested.

Earlier failed reports remain preserved. They exposed an illegal default potion overstack (clamped to one), fixture flag creation during ACTIVE play (moved before start), a broad Forge picker query matching the new station provider (use exact fixture ID), and a fixture attempt to put a Warden into the deliberately curated encounter allowlist (use a real direct Warden and an allowed pig encounter). The production encounter allowlist was not broadened.

## Review and retained data

Use `../client/Chronicle-0.1.0-dev.67-1.21.10.mrpack`. The fresh authenticated, whitelisted review is **127.0.0.1:25591**, paused with stations and supplies near spawn. Existing review **25589** and custom authoring **25590** retain their content and progress. All three are included in the client presets; GUI scale 2, narrator/onboarding preferences and FancyMenu welcome suppression remain set.

Existing worlds/config/access files were backed up before JAR replacement and checked unchanged at the swap. Deployment receipts live outside Git under `../reports/`; backup archives remain under `../backups/`. The main review's pre-existing stale city-Region hold was retained rather than silently rebinding user map data. Use the fresh defaults world for this review. No main merge or Modrinth publication was performed.

The next organizer review is the sound selection/volume and how the default books, roles, loot and Event templates read in game. Handcrafted-map bindings, book discovery placement, sinkhole saved states, map-specific balance, group play and audible voice checks remain distinct [delivery gates](OUTSTANDING_WORK.md). No additional three-hour synthetic soak was required for these bounded additions.
