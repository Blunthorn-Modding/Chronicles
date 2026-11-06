# Council ballot verification

LC02, development source `0.2.0-dev.2`, branch `feat/last-council`. No release or human group review is claimed. Original brief and alpha artifacts remain unchanged.

## Checks

- `:core:test :campaigns:last-council:test` through the workspace Java/Gradle environment, `--offline --no-daemon`: PASS. Core563 tests, zero failures/errors, six existing Windows symlink skips. LC02 contributes twelve proceedings/store tests on top of the ten foundation tests; the later pure succession calculation has five separately tested cases and is not claimed as integrated succession.
- Last Council/Core compilation and remapping, plus `:campaigns:last-council:writeSmokeClientLaunch`: PASS. The exact final LC02 access code passed the native runner below. The unused CouncilActionScreen class was removed afterward; no behavior depended on it.
- `python -m unittest discover -s scripts -p 'test_*.py'`:131PASS on6November.
- `python scripts/council_ballots_smoke.py`: PASS, `../reports/council-ballots-20261106T071319924443Z/report.json`. Three actual Fabric clients, two server processes with a real saved-world restart. Public two-house ballot, fixed majority2, named public choices and closed tally; secret projection for both houses and GM; first confirmed choice immutable; outside-Region and paused casting refused; returning representative accepted; required public void reason and unchanged archived choices; replacement new ID and one-house electorate; fixed replacement survives restart, excludes the late representative and passes after its captured representative votes.
- Two distinct calls deliver exactly one default message apiece to both participants and do not replay after restart. No resetting of Event history.
- Actual R-to-G rebinding, native key dispatch, real Yes/confirmation buttons and own-choice acknowledgement. Inventory stays open on ballot signal (including vanilla's automatic Creative-inventory replacement). Hotkey ignores focused text and confirmations, suspends a nested draft and restores its unsent text. An injected delayed opening response is rejected while a different screen is current. This injection tests the same production response guard without relying on nondeterministic network timing.
- Native compact/normal proceedings and ballot frames captured under that report's `screenshots`; lead inspected the compact layout and ballot. Vanilla first-join chat/social toasts overlap parts of these quick-run frames; they are not Chronicle layout elements.
- Independent read-only Core/proceedings/privacy/recovery review and a separate access review. The latter found the delayed-opening race and nested-parent close problem; request/context guards and editor suspension fix them. Native proof covers those corrections.

Earlier native reports on5November and6November at070646 are supporting history. Two expanded-hotkey attempts failed because their assertion recognized only Survival inventory; logging showed CreativeInventoryScreen. The final assertion accepts both normal vanilla inventory screens. No product change was made to suppress that behavior.

## Boundaries

The native fixture injects valid existing Region/Anchor bindings; it does not prove authoring the two subtypes through the editor. Pure failure tests exercise interrupted opening/closing/void, malformed/external journal edits, prepared-call proof and idempotent replay, but a process kill at every disk-write instruction was not performed. Secret raw votes stay server-side in Core's ledger; only explicit BallotView projections cross the network. Open and void secret ballots hide all totals and other choices, including from the GM.

LC08's actual hidden complication pool/gate, native proposal drafting/sponsorship, succession and other political systems remain their own slices. Quiet Below's242 tests (one existing Windows skip) were last run with the foundation; the next all-module regression run will cover the combined development source. No new3-hour soak, map/group playtest, production-world deployment or Modrinth publication.
