# M18 / CF24 - Bundled custom event sounds

4 November 2026. **Implemented in dev67; organizer audible review passed.** Eight organizer-selected Freesound clips are bundled with audited credits, strict assets/catalog validation, matching-client handshake and the existing Event picker/private preview. Native clients decoded all eight and verified private/live recipients; the organizer then confirmed the sounds are good. See [sources and licences](QUIET_BELOW_AUDIO_SOURCES.md), [setup guide](FINAL_DEFAULTS_GUIDE.md), and [results](FINAL_DEFAULTS_RESULTS.md). The original ordered design below remains the contract.

## Outcome and scope

The organizer can select and privately preview custom audio from the existing Event sound picker, then use it with the existing triggers and audiences. Effects, ambience, music and recorded dialogue may be supplied as assets. Every player receives the audio in the matching client mod/pack; no server resource pack or runtime download is required.

New or changed audio is a source/build change followed by distributing an updated mod/pack. An in-game uploader, runtime JAR modification, remote audio hosting, automatic downloads and MP3/WAV conversion are outside this milestone. Initial assets use Minecraft-compatible Ogg Vorbis; final recordings and their selection remain organizer-supplied content, not invented campaign requirements.

## Existing integration and ownership

- Core `CoreEventMenu.catalog` lists `Registries.SOUND_EVENT`; `EventRuntime.sound` rejects unknown IDs. Register bundled sounds during common initialization before catalogs are used, so selection and execution share the same stable IDs on server and client.
- Keep generic registration/validation support in Core where reusable. Quiet Below owns its audio, namespaced IDs, `sounds.json` and campaign defaults under `campaigns/quiet-below/src/main/resources/assets/quiet_below/`. Do not move campaign recordings into Core.
- Reuse the existing picker, private preview and Event delivery path. Preserve participant/Region audience rules, completion receipts, pause/hold behavior and uncertain-media recovery; registering or previewing a sound must not fire an Event.
- Portable scenario Event templates already contain a sound ID. Preserve that reference through export/import; audio stays a packaged build asset. Do not embed audio bytes, map state or file paths in scenario JSON or world histories.
- Common initialization must remain dedicated-server safe and never import client audio classes.

## Implementation slices

1. **Asset catalog and build checks.** Define the reviewed sound inventory with stable IDs, readable labels and asset paths. Record provenance/distribution rights for each supplied recording. Validate duplicate IDs, referenced files, Ogg Vorbis encoding and packaged metadata. Decide streaming for long recordings from the actual asset lengths and memory cost. Keep a small distributable test fixture independent of final campaign recordings.
2. **Registration and packaging.** Register the inventory on both sides, include definitions and audio in the campaign JAR, and include that JAR in the matching client pack. Verify the remapped artifact contains every declared sound and file. Reuse existing compatibility/version conventions; establish clear handling for missing or mismatched client assets before delivery.
3. **Event authoring and content integration.** Expose bundled sounds through the existing searchable picker and private preview, using readable names where the current catalog permits. Keep vanilla and other registered sounds available. Round-trip the selected stable ID through save/reopen, restart and portable scenario content. Assign actual campaign defaults only when the organizer has supplied the intended content; adding the catalog never enables Events automatically.
4. **Verification and operator handoff.** Exercise a bundled sound in a disposable matching-client world, then document asset preparation, rebuild/distribution and how to choose/preview it. Follow the existing dedicated UI design/review process if implementation needs layout changes. Serialize shared build directories and native test ports with the ongoing work.

These slices are small and should be implemented serially unless independent asset preparation warrants a separate owner. The lead owns registry/compatibility decisions and final integration.

## Compatibility and failure behavior

Keep IDs stable once referenced by saved Events; avoid silently renaming/removing them. Existing Events, vanilla sound selections, schemas and immutable historical encodings must remain readable. Missing definitions/assets must produce actionable validation or compatibility feedback rather than a successful-delivery claim. Server packet emission alone does not prove that a client decoded or audibly played a recording.

Audio does not rewrite receipts or retry completed/uncertain Events. A pack update requires matching client distribution. Dev67 uses the established stopped-world backup procedure and a separate fresh defaults review world; existing organizer profiles retain their content and progress.

## Acceptance and validation

- [x] A supplied bundled Ogg Vorbis sound has a stable registered ID and complete `sounds.json`/audio entries in the remapped campaign JAR and generated client pack.
- [x] The dedicated server starts without client-only class loading; matching clients resolve and decode the sound without missing-asset errors.
- [x] The existing Event picker finds the bundled sound, private preview reaches only the requesting GM, and saving/reopening/restarting retains the selection. Existing registered sounds still work.
- [x] Live manual and automatic Event paths deliver to the configured eligible audience and retain existing completion/recovery semantics. No registration, save or preview fires an Event.
- [x] Portable scenario export/import preserves the sound ID while excluding audio bytes and local paths. Existing scenario/world data stays compatible.
- [x] Focused asset/catalog tests, relevant Core/campaign tests and the full offline Gradle build pass. Native Event/editor checks ran sequentially in disposable worlds; see exact commands/results in `TEST_PLAN.md`.
- [x] The organizer confirmed the supplied sounds during the 4 November review. This is human acceptance, separate from the muted automated decoder checks.
- [x] Asset preparation, supported format, attribution, packaging and client-update instructions are documented. Final audio content and map/group review remain separate from code completion.

Implementation evidence is recorded in [the dev67 results](FINAL_DEFAULTS_RESULTS.md). The later organizer acceptance completes the initial audible review; map-specific/group audibility remains part of the eventual playtest.
