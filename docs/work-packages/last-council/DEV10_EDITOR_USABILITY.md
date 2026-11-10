# dev.10 source-editor usability corrections

The owner confirmed that requesting physical House papers works, and that the overall UI, goals and knowledge rules work. House creation failed because the form required a namespaced internal identifier. The secret form exposed unexplained fact keys and did not make their optional nature clear. Earlier automated coverage entered valid technical identifiers and fact keys directly, so it did not exercise the ordinary authoring path.

House creation now asks for the House name. The server creates a unique namespaced ID in the draft and also supplies one when a new House save has no ID. That identity survives edits to the name and remains the same in assignments, template scopes and exports. Existing Houses keep their existing IDs; no content migration or cast reset occurs.

Normal secrets need only their name, text and applicable Houses; weight defaults to one. Name and Secret text replace technical field labels. Optional reusable template values are labelled explicitly and explained. Add value supplies an unused `fact.valueN` reference automatically and asks only for its value. Editing an existing value preserves its reference, including bundled or imported fact names. No fact-key input is required. The underlying strict configuration schema and source-editing lock are unchanged.

Verification passed:

- `:campaigns:last-council:test` and all three matching remaps plus `:campaigns:last-council:writeSmokeClientLaunch`, using the workspace Java/Gradle environment with `--offline --no-daemon`: 110 tests passed, no failures/errors/skips. Report `../reports/dev10-source-editor-build.txt`. Focused regressions cover generated/stable House identity, plain secrets with no facts, and automatic references which avoid existing keys.
- `python scripts/council_editor_smoke.py`: real three-client initial/restart PASS, `../reports/council-editor-20261110T194126596255Z/report.json`. The native forms create a House by name only, create a plain secret without opening optional values, and add an advanced value without a key input. Assignment of the generated House, multipart save/preview, nonoperator denial, competing stale writes, revoked-permission timeout/draft resume, cast lock and unchanged cast/content after restart also passed.
- `python -m unittest discover -s scripts -p 'test_*.py'`: 134 passed, `../reports/dev10-python-tests.txt`. `git diff --check` passed; original brief hash remains unchanged.
- Matching dev.10 e4mc Host/Player packs built with archive CRC, hashes and exact final mod payloads verified. Receipt `../reports/council-e4mc-pack.json`. No local review server/plain pack was created; the played launcher world and unrelated acoustics remain untouched.
- Removed the one stopped disposable native fixture (0.18 GiB); reports/screenshots retained. Receipt `../reports/dev10-cleanup.json`.

Development sequence clarification: dev.8 contained reusable House-content export/import and profile isolation, committed as `8eadfdc`. It had no separate review pack. Those changes were included in the dev.9 review pack alongside source editing and requested copies. Dev.10 contains this focused feedback fix.

Pack SHA256: Host `007ad5b2596a846156f4ecf0937d06359d33fb54a731a58405d30642752efc09`; Player `3681a116bebeb9ee78190180eba6a68b0b5308a2a1d04296f5c5b12444af9491`.
