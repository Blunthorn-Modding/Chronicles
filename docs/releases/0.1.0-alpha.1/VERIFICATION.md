# Alpha verification

4 November 2026. No production schema/protocol or server gameplay change in
this release-preparation pass. The Event editor fix preserves supplied
default/template IDs during renaming, while retaining automatic ID generation
for new custom Events and honoring a manually chosen ID.

- Full offline build: **780 Java tests, 773 passed, seven existing Windows
  symlink-privilege skips**, zero failures/errors; build passed in 3m33s.
- Python discovery: **131 passed**.
- Final remap after the client identity fix: **PASS**, 36 seconds.
- Native defaults: **all six phases passed**, using the exact packaged JARs.
  The proof checks fresh/default visibility and private preview, a world-only
  copy with saved progress, ACTIVE deferral before and after an actual server
  restart, non-active promotion with Region pins, editing/saving an incomplete
  default through real native controls under the original ID, and preservation
  of that saved fingerprint after another actual restart.
- Ordinary native Events editor: **40 checks passed** at logical 320x240,
  including new-Event ID generation and manually chosen ID preservation.
- Independent read-only source review: clear after correcting template-ID
  regeneration. The regression was reproduced before the fix. An earlier
  harness failure caused by selecting the wrong copied-world phase was also
  corrected; neither earlier failed run is presented as a passing proof.
- Both release JARs pass CRC, metadata, project-license/NOTICE and existing
  bundled-library/audio/vendor-isolation checks. The client pack retains
  official third-party references, initial preferences and no retired server
  addresses. The original Word source hash is unchanged.

Compact evidence is retained under `../reports` relative to the repository:
`initial-alpha-build.log`, `initial-alpha-python.log`,
`initial-alpha-remap3.log`, `initial-alpha-native-final.json`,
`scenario-defaults-20261104T204641530535Z/report.json`,
`event-editor-smoke-20261104T205100218008Z/report.json` and
`initial-alpha-validation.json`.

Review/test worlds and old backups were removed with the owner's approval;
the compact reports remain. No new review server or three-hour soak was run.
The owner will upload the release manually. No Modrinth project/version is
claimed as published or approved. Handcrafted-map integration, map-specific
balance and group/voice playtesting remain separate delivery gates.
