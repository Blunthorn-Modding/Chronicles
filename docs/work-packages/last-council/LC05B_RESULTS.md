# LC05B foreign affairs — dev.16

The approved realm editor and typed Council motion flow are implemented and automatically verified. Both foreign war and foreign peace require **two-thirds of the captured electorate**. General motions, Regency and voluntary house agreements retain their existing rules. Matching e4mc packs are ready; human group acceptance is not claimed.

## Organizer workflow

While play is non-active and Editing is enabled, **Game setup → Foreign realms** opens the reusable catalogue. Create a realm using its name and description; IDs are generated. An unused realm can be deleted after confirmation. Motions or diplomatic history prevent deleting a referenced realm. Catalogue editing remains available after House papers are issued; it does not share the House source's editing lock.

At the ordinary motion lectern, a house proposes General, Declare foreign war or Make peace. The Chancellor can prepare those same kinds through proceedings. A foreign motion selects an eligible realm and pins its diplomatic revision. Its two-thirds threshold is enforced by the server. The live ballot identifies the foreign action and realm. The normal sponsorship, agenda, open/defer/close/void and secret/public vote behavior remain in use.

Passing a foreign motion changes its public diplomatic record once. Failed and void votes change nothing. A stale motion refuses to open instead of overwriting a newer decision, including war followed by peace. Drafts retain refused input; **Refresh realms** updates the choice without discarding text. There is no political-state override button, army, resource, map or PvP effect.

The existing public Conflicts screen switches between **House conflicts / Foreign affairs**. Foreign records show the realm description, At war / Not at war and a chronological history. Long histories use real Earlier / Later controls, 32 entries per page. There is no new personal-menu tab. House peace/ceasefire still require every current house to sign and the Chancellor to witness at the treaty lectern; Council foreign policy cannot replace that consent.

## Ownership, persistence and recovery

This is Last Council behavior. Core's existing ballots, file safety, portable-content and UI helpers are reused without changing Core or Quiet Below gameplay code. New realms start Not at war; no fictional example realm is added as canon or default content.

Reusable definitions live in `config/chronicle/last-council-foreign-realms.json` (or the active profile-content directory), with its `.initialized` marker. Export/import uses `last_council:foreign_realms`. It contains definitions, not diplomatic progress. Empty custom profiles start with an empty catalogue. World effects live in `<world>/chronicle/last-council/foreign-affairs.json`, guarded by `foreign-initialized`.

Proceedings schema 4 reads schemas 1–3 as General motions, retaining their electorate snapshots, pending operations, votes, deferrals and succession. A typed motion stores its immutable action, realm and expected revision. Inside the existing saved CLOSE operation, Core closes the same ballot, the exact passing foreign effect is written, then Proceedings clears its pending operation. Duplicate recovery checks the original receipt; it cannot overwrite a later peace or invent votes. Missing/orphan/mismatched outcomes hold rather than silently reseed. A saved close actor must match the already-closed Core ballot's finisher.

After preserving/restoring exact files, an operator can run **`/chronicle council recover`**, even if startup failed before creating a proceedings runtime. It reloads all authorities and completes the saved intent. Recovery reports a remaining hold if another Chronicle operation still blocks play. It does not bypass consent, recapture an electorate or choose a different result. Matching dev.16 clients are required; use a stopped pre-upgrade copy for rollback rather than downgrading political journals in place.

## Evidence

- `lc05b-native-build2.log`: **191 Last Council Java tests pass**, client compile/remap and launch generation pass. Tests include strict catalogues, real file conflicts, byte budgets, two-thirds passage/failure, three legacy schema migrations with pending opening, before/after-effect write failures, finalization/restart, actor mismatch and missing/orphan receipt refusal.
- `council-foreign-20261118T085625428742Z/report.json`: **PASS**, four actual clients across three phases and two restarts. Real native creation/no-op saves, forged update refusal, typed motion form, two-of-three war and peace, failed/void peace, stale ABA proposal, referenced delete refusal, real Windows file-lock failure/held projections/operator recovery, already-applied CLOSE restart and missing initialized journal restoration are covered.
- The first native attempt is **FAIL**, from a harness assumption that Prepare motion was directly visible in compact navigation. The driver was corrected before the complete second run; it is not counted as a pass.
- `lc05b-full-build.log`: full offline three-module build **PASS**: 1,029 Java tests, 1,022 passed and seven existing skips. `lc05b-python-first.log`: **134 Python tests pass**.
- `council-foreign-layout-20261118T090448981509Z/report.json`: **PASS**, focused latest-build native checks after the counter-spacing fix. Normal/compact fields, footer, validation and retained realm refresh are bounded; the lead inspected the captures. Vanilla unsigned-chat toasts in these offline fixtures are separate from the Chronicle screen.
- `council-content-20261118T090740522165Z/report.json`: **PASS**, five export/import/restart/empty/legacy phases and four initialized-missing-file refusals, including the new foreign catalogue. Reusable definitions survive stopped edits; an empty profile inherits no realm defaults.
- Native regressions **PASS**: existing ballots `council-ballots-20261118T091253279586Z`, succession `council-succession-20261118T091624106454Z`, and three-phase house conflicts/typed peace/ceasefire `council-conflicts-20261118T091831172330Z`.
- `dev16-pack-validation.json`: matching Host/Player archives pass CRC, exact dev.16 JAR bytes, GUI2/narrator/onboarding/multiplayer defaults, Survival/Normal and private-progress exclusion. All 55 prepared source-world files and six dev.14/dev.15 artifacts remain unchanged. No dedicated review server or plain/server pack was created.
- Independent scoped source review found and rechecked the close-actor and startup-recovery fixes. Its final recheck found no concrete blocker in catalogue authorization, acknowledgements, history bounds, forced threshold, privacy or recovery readiness.

These checks do not certify arbitrary power loss, full-world maximum-volume performance, human roleplay balance or previously deferred treaty/complication group acceptance. Source-brief text, played worlds, the prepared source map and prior review packs remain outside this change.

Eight stopped, marker-verified generated fixtures were removed after retaining compact journal evidence in `../reports/lc05b-stopped-journal-evidence.zip`. Logs and screenshots remain; exact targets and sizes are in `../reports/dev16-cleanup.json`.

## Combined review

Use fresh matching dev.16 e4mc Host/Player imports. No dedicated local review server is required. Create a named external realm in non-active Editing, then use the existing Chancellor and participating House setup. Open a foreign war motion, check the selected realm and two-thirds rule, vote and inspect the public record. Try failed/void peace and a passed peace; confirm old house conflicts and voluntary treaty flows still feel separate. Revisit the retained/refreshed draft in compact GUI scale 2. The earlier treaty/complication group reviews can be bundled with this review when enough testers are available.
