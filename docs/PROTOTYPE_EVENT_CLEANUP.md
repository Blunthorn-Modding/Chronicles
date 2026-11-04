# Dev69 prototype Event cleanup

4 November 2026. Both unwanted rows were `CampaignEvent` registrations in Quiet Below's common initializer: `quiet_below:readiness_check` and `quiet_below:mine_whisper`. They were not library defaults and were separate from the nine authored scenario Events. Their normal registrations are removed; the author-supplied Whisper from Below Event is unchanged.

Historical flags/completions are retained so loading existing worlds does not discard progress or audit history. Removing a registration removes its menu/command-catalogue action; it does not delete player-authored definitions or unrelated data.

The existing Fabric regression's once-only/persistence example is now `test:readiness_check`, registered only for its verified disposable fixture. The bounded-stage regression uses `test:marker_clue`, likewise restricted to an explicit marker, timestamped test root, actual server-root ownership and offline loopback binding. Production startup enables neither fixture. The library's generic Event support is retained.

Verification:

- Full offline build/remaps PASS2m58s:780 Java tests,773 passed/seven existing Windows symlink skips. Final fixture-guard rebuild PASS29s. Logs: `../reports/remove-prototype-events-build1.log`, `remove-prototype-events-build2.log`.
- Python regression discovery131PASS: `../reports/dev69-python.log`.
- Normal fresh/copy native test PASS `../reports/scenario-defaults-20261104T180550573004Z/report.json`, including an explicit assertion that the retired and test-only action IDs are absent, plus the existing nine-default/flag/Region/outcome/copy checks.
- Isolated positive/negative fixture checks PASS `../reports/dev69-fixture-events/report.json`: test IDs register in their own roots; inherited test environments on another world refuse startup before readiness. The full old Fabric/stage interaction suites were not rerun; their references compile and their test-event startup paths were exercised separately.
- Independent source review clear after tightening the regression fixture's registration gate. No gameplay/schema change was introduced.

The review update uses a saving stop and verified backups. Final JAR/pack hashes, server status and preservation checks are recorded in `../reports/dev69-validation.json` and `dev69-review-deployment.json`. See [remaining work](OUTSTANDING_WORK.md) for map integration, balance, group playtesting, two narrow native coverage follow-ups and release preparation. Other scenario-format engines remain future work outside the current Quiet Below focus.
