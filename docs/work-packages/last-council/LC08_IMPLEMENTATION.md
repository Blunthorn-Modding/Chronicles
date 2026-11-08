# LC08 Event pools and Council complications - dev.6

Workflow approved by the owner on 8 November 2026. Native implementation is ready for bundled functional review after the checks below. This is a development build, not a new public release.

## Delivered behavior

Core owns reusable weighted Event pools, their operator editor, and a separate private world draw journal. Use **Events > Event pools**, create a pool, add existing Events with the shared picker, change positive weights and save. This initial version supports enabled manual once-only Events with zero delay. A given Event cannot be drawn again through a second pool. Empty/exhausted pools fail explicitly.

Selection, immutable Event configuration, recipients, session and operation identity commit before dispatch through the existing Event executor. Retrying or restarting retains the draw. Editing weights or deleting a reusable pool never changes a saved selection. Changed Event configuration/audience or missing prerequisites require exact recovery; they never trigger a replacement draw. Completion is distinguished from uncertain media. Explicit Event reset retains archived pool completion and cannot replay its effects.

Last Council owns hidden meeting allocation and pacing. **Game setup > Council complications** configures the pool, one evening total, eligible numbered meetings, arrival window and minimum spacing. Defaults are two interruptions over meetings 1/2/3, targets 5-25 active Council minutes after opening, five-minute spacing. Zero disables them. The initial five possible narrative Events are editable examples; they invent no heir or compulsory political outcome.

Occupied active discussion and Council recess advance the clock. Gathering, between-meeting time, technical pause, Building Mode, empty play and holds do not. Graceful shutdown checkpoints the last interval; a restart never counts downtime. The underlying shared clock commits before dependent schedule checkpoints.

Ordinary ballots are preserved. Timed arrivals wait for a free ballot boundary. A Regency or meeting-close request with outstanding current-meeting obligations durably recesses Council and invokes one exact Event before permitting that boundary. Core play stays ACTIVE. The Chancellor must explicitly resume and request again; minimum spacing still applies. Later meetings' obligations do not block an earlier meeting.

At the first Council opening, ordinary configuration is fixed, including a zero-budget decision. Enabling the feature later cannot allocate interruptions into past meetings. Operators can pause, enable Editing, remove future opportunities and confirm redistribution. Current/claimed obligations remain fixed. With no future opportunity, a required reason records the undelivered remainder. Exact claimed deliveries must first be reconciled. Generic recovery and uncertain-media acknowledgement preserve the chosen invocation without exposing future content.

Normal player, Chancellor and Debug projections never include future allocation, selected Event, seed or countdown. Revealed private Region labels and body remain visible only to saved recipients. The source catalogue lists possible definitions, not the current draw. Public Council narration also reaches the appointed non-House Chancellor after the reveal commits.

The live ballot loses the requested explanatory cards, retaining heads and actual choices/confirmation. Every successful meeting opening and closure sends player chat once; state polling and refused duplicate actions do not repeat it.

## Authority and storage

- Reusable pools: server `config/chronicle/event-pools.json`, or the existing custom-authoring profile's `profile-content/event-pools.json`.
- Reusable Council settings: `config/chronicle/last-council-complications.json`, or the profile-content equivalent.
- Private world authority: `world/chronicle/event-pools.json` and `world/chronicle/last-council/complications.json`.
- Existing Event configuration, Region bindings and execution/history retain their established storage and authority.

Catalogue changes require an OP, current connection, non-active Editing and no operation hold. Revisions reject competing saves. A rejected stale editor is locked until reopened, so a second click cannot silently overwrite the other operator. Opening replies are bound to their request and originating screen, cancelled by navigation/removal/disconnect. Ordinary players cannot fetch administrative settings. Recovery can operate during an exact technical hold without requiring Editing first.

Do not copy the private journals into reusable defaults. They contain campaign progress and private recipients. This slice preserves the established map/config split.

## Verification

The offline three-module test/remap build passes: 897 tests reported, seven existing skips, zero failures (Core 575, Last Council 80, Quiet Below 242). Python scripts pass 133 tests. Evidence: `../reports/lc08-unit-summary.json`, `lc08-final-build.txt`, `lc08-final-council-build.txt` and `lc08-python-tests.txt`.

Initial integrated three-client native run: `../reports/council-ballots-20261108T140111366395Z/report.json`, PASS. It created/edited/deleted a pool, verified approved settings and normal/compact screenshots, denied ordinary-player setup access, exercised actual text/flag execution, archived completion after reset, and delivered a retained pending selection after an actual server restart without media replay. Live public/secret ballots and automatic updates remain covered.

Initial four-client complication run: `../reports/council-complications-20261108T140503161763Z/report.json`, PASS. Two actual Events gate Regency and meeting closure independently; explicit resume and duplicate refusal conserve reveals. Real restart retains the fixed electorate and succession slate. All four clients verify meeting announcements exactly once and no complication replay after restart.

Focused tests cover weighted boundaries, exhaustion, canonical encoding, external file changes, save failures before/after publication, exact invocation/source/audience status, private history projections, allocation into first/last opportunities, pause/re-anchor clocks, spacing, stable claimed recovery, redistribution and exceptional end accounting. The independent source review found stale editing, delayed opening, private-label projection, gathering-clock and transition/recovery issues; these were corrected before handoff.

Final three-client run: `../reports/council-ballots-20261108T190733677717Z/report.json`, PASS. In addition to the initial coverage, two genuinely operator-authorized clients compete to save the same pool; the winning edit survives and the rejected editor cannot retry until reopening. Delayed opening replies for both editors are ignored after navigation, even when returning to the same parent screen. An intermediate attempt was explicitly aborted because its second account lacked OP permission; the fixture was corrected rather than weakening product checks.

Final four-client run: `../reports/council-complications-20261108T191107131166Z/report.json`, PASS. It additionally asserts Core's saved phase remains ACTIVE during the real complication recess. Both boundary gates, duplicate requests, public narration to all four players, secret-ballot privacy, fixed succession and actual restart remain green.

Commands: `python scripts/council_ballots_smoke.py`; `CHRONICLE_COMPLICATION_GATE_SMOKE=1 python scripts/council_succession_smoke.py` (environment set only for that child process); `python -m unittest discover -s scripts -p "test_*.py" -v`. Java tests/remaps use the workspace's Java21 environment and offline Gradle. `git diff --check`, local LC08 documentation links and source-brief SHA256 also pass. The original brief hash is unchanged.

Core/Quiet Below native menu regression: `../reports/core-menu-smoke-20261108T191701644746Z/report.json`, PASS. The full 110-check operator flow, ordinary-player denial/private-preview isolation, four screen sizes and actual restart/loot persistence pass. Its initial attempt referenced retired `city_presence`/`distant_stir` Events; the test now uses the current City Discovery and Whisper from Below definitions/messages. Retired production Events were not restored.

A final disabled-evening hardening commits zero-budget settings too, with a focused durable-reload test. `lc08-disabled-evening-build.txt` passes. The matching final three-client run `../reports/council-ballots-20261108T192308134157Z/report.json` passes and verifies the retained empty schedule after actual restart. All earlier pool-editor, competing-save, delayed-response and ballot checks remain green. Ten stopped disposable test worlds were removed (1.8 GiB); reports/screenshots are retained in `../reports`, with the deletion inventory in `lc08-cleanup.json`.

## Remaining boundaries

LC08C still needs reusable pool/settings integration with ScenarioContent export/import, the final narrative pool and additional political source/flag links. Character-template authoring/physical handout delivery are LC01B/C, separate from Event pools. Recurring/source-driven Event entries are deliberately unsupported initially. Existing recurring Council-call Events are unchanged.

Treaties, conflicts/rebellion, banquet and room acoustics remain their own work packages. The initial complication narratives are review material rather than final canon. No public release, map/group acceptance or full LC08 completion is claimed. Human review should judge native controls, readability and the pacing of interruption/recess/resume as one bundle.

## Review artifacts

Matching dev.6 plain, e4mc Host and e4mc Player packs are prepared in `../client`; pack CRC, mod bytes, exported world payloads and cached e4mc license are verified by the pack builders. The Host includes a fresh review world with the approved default settings and no committed cast/draws; Player contains no world. The old played launcher profile is untouched. The authenticated loopback alternative is `127.0.0.1:25589`; manifest is `../reports/council-review-latest.json`. Only the current review is retained; the superseded stopped dev.5 review (159 MiB) was removed. [REVIEW_COUNCIL.md](REVIEW_COUNCIL.md) gives the bundled steps.
