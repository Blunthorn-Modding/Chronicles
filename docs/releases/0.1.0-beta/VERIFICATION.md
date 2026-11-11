# Beta package verification

Prepared on 11 November 2026 from **Chronicles**, using the existing verified Chronicle release baseline.

## This preparation pass

- Build: `gradlew.bat --offline --no-daemon -Pmod_version=0.1.0-beta build`, launched with `scripts/workspace.py`'s workspace-local Java/Gradle environment. **PASS**, 3m03s.
- Java: **780 tests, 773 passed, seven existing Windows symlink-privilege skips**, zero failures/errors.
- Python: `python -m unittest discover -s scripts -p "test_*.py" -v`: **131 passed**.
- Both beta JARs pass ZIP CRC, Minecraft/mod/version metadata, MIT LICENSE/NOTICE, bundled audio and vendor-isolation checks.
- Both baseline alpha JAR hashes match the original native-test artifact records. Comparing every uncompressed archive entry shows that **only fabric.mod.json differs, and only its version field changed to 0.1.0-beta**. All code, content, dependencies and notices are unchanged.
- The alpha-named upload folder was renamed to **0.1.0-beta**. Descriptions, summaries, separate changelogs, upload filenames, version metadata and SHA256SUMS now describe this beta package.
- No server deployment, world/config changes, client-pack refresh, hosted project creation or upload occurred.

The Gradle version override is intentional: the historical source default and initial-alpha preparation helper remain unchanged. The beta build command above produces the beta artifacts. The local recipe `reports/prepare-beta-publication.py`, relative to the workspace root, checks baseline equivalence before copying files and regenerating metadata/checksums.

## Existing native evidence

The original 4 November verification passed all six default-Event phases, including actual ACTIVE/restart promotion deferral and editing/saving an incomplete default through the native editor. The ordinary Events editor passed 40 checks at 320x240. These are **baseline native results**, not a new native run of the beta-labeled JARs; equivalence is established by the archive comparison above.

Retained reports, relative to the workspace root:

- `reports/beta-publication-build.log`
- `reports/beta-publication-python.log`
- `reports/beta-publication-validation.json`
- `reports/scenario-defaults-20261104T204641530535Z/report.json`
- `reports/event-editor-smoke-20261104T205100218008Z/report.json`
- `reports/initial-alpha-validation.json`

## Remaining delivery work

No handcrafted map is bundled. Map-specific integration, balance, discovery placement and group/voice playtesting remain outstanding. No new native client/server run or three-hour soak was performed for this version-only packaging pass. Publication remains **PENDING_OWNER_UPLOAD**.
