# 9 November implementation and verification

Source `0.2.0-dev.7`, feature branch `feat/last-council`. The owner explicitly deferred the human multiplayer complication/recess/Regency review until more testers are available. That review is still outstanding; it does not block these independent changes.

## Pool editor frame

The Event-pool editor now uses the normal Chronicle menu frame and its content/footer geometry. Bottom actions stay within the grey frame at compact, normal and wide/short sizes. Existing create/edit/picker/save/delete and competing-editor behavior is retained.

Evidence: `../reports/council-ballots-20261109T085344744618Z/report.json` PASS, with actual native compact/normal/wide-short frames and panel-relative widget checks. The full three-client restart suite also retained ballot privacy, automatic progress, late enrollment, settings, pool dispatch and media no-replay checks. Screenshots were inspected; this is automated evidence, not the deferred human complication acceptance.

## Portable reusable content

Core ScenarioContent schema5 adds bounded reusable Event-pool definitions and optional namespaced mechanic-settings sections. Schemas1-4 remain readable. Core owns the pool model/validation and optional adapter lifecycle; Last Council registers its own reusable complication settings. Pool references must resolve to exported manual, once-only Events. Capture rejects explicitly disabled referenced Events rather than silently enabling them on import.

Scenario export reviews the pool and mechanic-settings counts before writing the acknowledged content. It captures the editable configuration, not the private running schedule. Fresh custom profiles initialize these sections once; legacy initialized profiles preserve their existing catalogs or initialize absent new sections empty/disabled. After initialization, missing files refuse startup instead of silently reseeding packaged defaults. A missing mechanic-settings provider also refuses the operation. Normal stopped configuration edits remain authoritative.

No map coordinates, participants, private draw identity, selected Events/recipients, allocation, clock, electorate or discoveries are added to reusable content. Map setup and live progress retain their existing world ownership.

Evidence: `../reports/council-content-20261109T090549570350Z/report.json` PASS. Real native export imported into a fresh custom authoring profile with exact weights/settings; deliberately different private schedule data was excluded. Stopped edits survived an actual restart and re-export. Removing initialized Council settings caused the expected startup refusal without recreating the file. The initial failed harness attempt looked in the wrong export directory; the source export itself had succeeded, and the harness was corrected before this complete rerun.

## Treaty ceremony

The owner approved [the treaty prototype](LC04_UI_DESIGN.md) and its privacy/retention policies. The initial house-agreement ceremony is implemented in Last Council, using a separate bound lectern and compact list/detail forms. Named parties create and individually confirm exact terms. The separate appointed Chancellor sanctifies only when every signed party has this same lectern open. Changed draft content clears all signatures; unchanged signatures survive leaving and reconnecting. Public records are available at the lectern; private existence, terms, breach and history are sent only to the parties and Chancellor. Earlier private revisions retain their original audiences even if a later draft becomes public.

Consent is world-owned metadata in `chronicle/last-council/treaties.json`; map bindings are in `treaty-lecterns.json`. Neither is reusable scenario content, and clean review exports retain only the binding. Atomic observed-byte writes, bounded strict replay and exact operator reload preserve uncertain/corrupt data. No operator force-sign or Chancellor house participation is offered. The physical session revalidates connection, anchor, range and current role; only token-correlated client acknowledgement of an accepted opening counts toward attendance. Explicit close releases attendance immediately. Per-viewer projection revisions do not reveal unrelated private mutations. Refused draft submissions preserve the form. History retains the full allowed Unicode terms.

An independent source review found premature opening attendance and truncated maximum-length history. Both were corrected and re-reviewed without further concrete findings. The baseline native four-client run passed, then the corrected READY-handshake version passed `../reports/council-treaties-20261109T151039882915Z/report.json`: private multiline creation, refused form preservation, individual signatures, absent sanctification refusal, changed/stale consent, actual server restart, one-time sanctification, breach, forged private selection/sign refusal, hidden outsider projection counters and a public second agreement. Compact and normal screenshots were inspected. A final small layout pass removes unused one-page history controls and reclaims compact list space; the final native four-client restart rerun also passed: `../reports/council-treaties-20261109T151650750456Z/report.json`. Final compact and normal frames were inspected. Realm ratification, active amendments, peace/war effects and other end conditions remain future work; this slice does not settle them.

## Verification commands

All Gradle commands use `scripts/workspace.py` child-process Java/Gradle environment with `--offline --no-daemon`.

- Full Core, Quiet Below and Last Council tests/compilation/remaps before the treaty additions: PASS, 904 Java tests, seven existing platform skips, zero failures/errors. Report `../reports/nov09-full-build.txt`.
- `python -m unittest discover -s scripts -p 'test_*.py'`: 133 PASS at the export/layout checkpoint. Report `../reports/nov09-python-tests.txt`.
- `python scripts/council_ballots_smoke.py`: PASS native panel geometry and three-client restart regression, report above.
- `python scripts/council_content_smoke.py`: PASS actual export/import/restart and initialized-file failure path, report above.

Combined full tests/remaps passed in2m32s (`../reports/nov09-combined-build.txt`), followed by the targeted handshake/history build (`../reports/nov09-treaty-ack-build.txt`). Current Java totals are912, with905passed and seven existing platform skips, zero failures/errors. Python134PASS (`../reports/nov09-python-final.txt`), including review-export separation of map binding from private consent. Final layout build passed (`../reports/nov09-treaty-layout-build.txt`). Review delivery is matching e4mc Host/Player packs only, without a separate plain/local-server pack. The existing played launcher world and unrelated acoustic investigation are preserved. The source brief and released alpha artifacts are unchanged.

## Delivery

Matching `Chronicle-Last-Council-0.2.0-dev.7-e4mc-Host.mrpack` and `...-Player.mrpack` are in `../client`, with a fresh world containing distinct bound motion and treaty lecterns. Pack CRC, exact mod bytes, absence of private cast/treaty progress, and the cached e4mc MIT license are checked by the builder. Receipt: `../reports/council-e4mc-pack.json`. Only Host/Player packs are delivered; no plain pack or running review server is required. The old played launcher profile is untouched.

An initial world-staging attempt used the menu smoke's fixed voice-port readiness check against the different review endpoint; it timed out despite successful startup and stopped cleanly. The corrected staging check used this fixture's startup, generated both lecterns, removed forced chunks, saved/stopped cleanly and built both packs. Logs: `../reports/nov09-packs-final.txt`, `../reports/nov09-pack-world-final.log`. This was a staging harness mistake, not a gameplay failure.

Human treaty presentation/ceremony acceptance is the next review before agreement consequences are integrated into conflict mechanics. Complication/recess/Regency group acceptance remains explicitly deferred. Realm ratification, active amendments, conflicts/rebellion, physical character handouts and banquet/acoustics retain their work-package boundaries; no full LC04/LC08 or release acceptance is claimed.

After verification, seven stopped disposable test worlds and the superseded dev.6 review source were removed (1.39GiB); compact reports/screenshots and the current pack source were retained. Receipt: `../reports/nov09-cleanup.json`. No test clients or review server remain running.

Owner follow-up: treaty multiplayer/UI ceremony acceptance is now explicitly deferred to the next review cycle as well. Neither this nor the complication group review is passed or a blocker to independent development. See [LC01 portability continuation](LC01_PORTABILITY_PLAN.md) for dev.8 work.
