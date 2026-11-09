# LC01 reusable House content: dev.8 results

The owner explicitly deferred treaty and complication human group acceptance to the next review cycle. Neither has passed; neither blocks this independent implementation. Source branch `feat/last-council`, version `0.2.0-dev.8`.

## Implemented

Scenario export/import now carries reusable House profiles, secret templates and fact defaults, house goals, scope/weights/counts and knowledge probability in `last_council:houses`. Core owns the optional namespaced-section mechanism; Last Council owns its schema and validation. Core's per-section maximum is2MiB to match the existing House-file limit. The16MiB whole-file budget,16-section limit and strict JSON/depth safeguards remain. Export review calls these **Scenario sections** rather than only settings.

Capture verifies the loaded authoritative House file unchanged and serializes only reusable `CouncilContent`. It excludes committed cast/seed/papers/knowledge allocations, roster, office, proceedings, treaties and private complication progress. The nonsemantic source-file note is omitted as an empty constant; a valid exact2MiB input still exports within the same limit. Deterministic encoding refuses lossy Unicode round trips and avoids unnecessary HTML escaping.

Normal scenarios retain `config/chronicle/last-council/houses.json`. A custom authoring profile uses `config/chronicle/profile-content/last-council/houses.json`. Fresh imported content initializes once; fresh empty authoring remains empty even with the scenario mod installed. Prior dev.7 profiles migrate once via a provider-local bounded marker: retain an existing valid profile file, otherwise copy the exact validated legacy normal file, or initialize empty if neither exists. The legacy source is preserved. With the marker present, missing/malformed content refuses rather than reseeding from packaged/imported defaults. World ownership of live state and map setup is unchanged.

## Verification

- Full `:core:test :campaigns:last-council:test :campaigns:quiet-below:test` plus all three remaps and Last Council native launch manifest, using the workspace child-process Java/Gradle environment with `--offline --no-daemon`: PASS2m44s.921Java tests =914passed,7existing platform skips, zero failures/errors. Report `../reports/lc01-portable-final-build.txt`.
- `python -m unittest discover -s scripts -p 'test_*.py'`:134PASS. Report `../reports/lc01-python-tests.txt`.
- `python scripts/council_content_smoke.py`: final actual native suite PASS `../reports/council-content-20261109T161651724944Z/report.json`. It exports >64KiB reusable House content while a different committed private cast/seed/roster and complication plan exist, verifies those live files unchanged/excluded, imports fresh, edits/restarts/re-exports without reseeding, checks expected startup failure for deleted initialized House/settings files, verifies empty custom authoring and exact old-profile legacy migration/source preservation. Five native client phases and two expected negative boots. Earlier complete checkpoint also passed160558356954Z before the minimal-note/UI-label polish.
- Independent read-only migration/privacy review found no concrete issue. Lead inspected diff and added exact2MiB, multibyte/aggregate/depth and lossy-Unicode checks. New static prototype JavaScript syntax/IDs and local HTTP200 were checked; browser visual/native-editor validation is not claimed.

## Next workflow review

[Source-content editor prototype](../../design/last-council-content.html) and [control specification](LC01_CONTENT_UI_DESIGN.md) are design-only, awaiting the owner's solo workflow review. Existing native Source catalogue is still read-only; this change does not claim an implemented editor. The proposed cast-commit editing lock also needs that decision. Export stays in the existing global Scenario content flow, with no duplicate per-catalogue exporter.

[Physical handout notes](LC01_HANDOUT_DELIVERY_NOTES.md) record delivery/recovery seams and identity/catalogue constraints. The automatic-versus-requested delivery preference is pending; no private book grants are claimed. No new multiplayer test or separate server/plain client pack is needed for these decisions. Existing dev.7 Host/Player group-review artifacts remain available for the deferred cycle; this source-only continuation does not replace the played launcher world or deploy another review server. The source brief, released alpha and unrelated acoustics are unchanged.

After verification, six stopped disposable profile-test worlds were removed (0.98GiB); compact reports/screenshots remain. Receipt: `../reports/lc01-portable-cleanup.json`. No test client or Minecraft review server remains running.
