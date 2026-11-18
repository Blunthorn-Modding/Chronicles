# LC05A house conflicts — dev.15

The owner-approved physical challenge, public conflict register and voluntary peace/ceasefire flow are implemented. Human multiplayer acceptance remains deferred with the existing treaty and complication group reviews. LC05B foreign diplomacy is a separate UI proposal; rebellion/access, active treaty amendments and realm ratification remain later work.

## Behavior and ownership

An eligible house representative obtains a **Challenge token** using `/chronicle council challenge-token`, uses it on an actual nearby representative, and confirms a public reason. The server rechecks the held item, original connections and houses, reach, line of sight, participation and active play. The declaration is unilateral. The token is not consumed. Its model references Minecraft's nautilus-shell texture; no third-party image is copied.

The clickable public notice or `/chronicle council conflicts` opens a compact list/detail register. Supporters may join, change sides or withdraw with confirmation. Founders stay on their original sides. **Game setup → Conflicts** configures the Civil war label threshold (default four houses, allowed two through sixteen); it does not change privileges, resources or combat.

The existing treaty form now has General, Alliance, Non-aggression, Mutual defense, Trade / rights, Peace and Ceasefire purposes. Peace/Ceasefire select a conflict and automatically require every current house. Each signs the exact document revision; the separately assigned Chancellor sanctifies with all parties at the same lectern. Membership changes require renewed exact consent. Peace records Ended; Ceasefire records Paused. Renewed hostilities after either agreement require another ordinary physical declaration, producing a new conflict ID and preserving prior history. There is no resume control.

Declaring against another party to an active typed Non-aggression pact records its breach. An unrelated public pact does not cause a warning or breach. Private agreements remain private in warnings, lists, history, packets and source activity. Mutual-defense partners receive private notifications and choose whether to support; they are never enlisted automatically. War does not remove Council or palace privileges or enable PvP.

All political rules, journals, token and screens belong to **Last Council**. Core's existing session, compact UI, portable-content, atomic-file and Event-source helpers are reused; Core and Quiet Below gameplay code are unchanged. The new public `last_council:conflict_changed` source can update Core flags through normal Events. Its agreement activity is neutrally named `status_changed`, without a private treaty ID or type.

## Persistence and recovery

World authority lives under `<world>/chronicle/last-council/`: strict `conflicts.json`, `conflict-operations.json` and `conflicts-initialized`, alongside the existing treaty journal. Treaty schema 2 reads schema 1 as General while preserving terms, signatures and history. Conflict history is bounded to 64 records, 128 transitions per record and 8 MiB; actual encoded transitions are checked before durable coordination begins.

The cross-journal intent pins exact parties, signed terms, revisions and breach IDs. Partial/uncertain writes hold play and redact political projections. After preserving the files, an operator uses `/chronicle council conflicts recover` to reload verified journals, finish the same saved operation and reconcile its Core source receipts. Recovery does not invent new signatures or change the intended outcome. Initialized missing/corrupt files are preserved/refused rather than silently reset.

Reusable `civilWarThreshold` belongs in `config/chronicle/last-council-conflicts.json`, or the active profile's content directory. It exports as `last_council:conflicts`. World wars, private agreements, player assignments and progress do not enter scenario defaults. Use matching dev.15 clients. Downgrading the new political journals to an older mod is unsupported; preserve a stopped pre-upgrade copy if rollback is needed.

## Verified evidence

- `lc05a-native-build6.log`: all **170 Last Council Java tests** pass, plus client compilation, remapping and smoke launch generation. This includes legacy migration, malformed journals, stale consent, real 8 MiB preflight and the 256 KiB public-projection budget.
- `council-conflicts-20261117T163157741016Z/report.json`: **PASS**, four real native clients, three phases and two restarts. The run uses real entity/block interactions and native controls. It covers reach/held-token refusal, actual Windows replacement failure after a private breach, held/redacted views, one-command exact recovery, Core source Events/flags, supporter/Civil war, compact required-house form, stale Chancellor refusal, peace, saved-intent recovery, ceasefire and new declarations after both agreement types.
- `lc05a-full-build.log`: full offline three-module build **PASS**, 1,008 Java tests total: 1,001 passed and seven existing skips. `lc05a-python-pack-final.log`: **134 Python tests pass**.
- `council-treaties-20261117T163548594161Z/report.json`: **PASS**, existing four-client general/private treaty ceremony across restart, absence refusal, retained signatures/drafts, stale signing and outsider privacy.
- `council-content-20261117T164004824380Z/report.json`: **PASS**, five native export/import/restart/empty/legacy phases and three missing-file refusals. The conflict threshold survives profile import and stopped configuration edits. The first export run failed an outdated three-section harness expectation; it now explicitly checks four sections and the exact conflict settings.
- `dev15-pack-validation.json`: matching e4mc Host/Player archives pass CRC, exact-jar/default/private-progress checks; all 55 source-world files and three dev.14 artifacts remain unchanged. No dedicated review server or plain/server pack was created.
- Independent scoped source review found and rechecked fixes for stale Chancellor consent, unchecked partial-write holds, strict intent decoding, actual-size preflight, private projections/activity and derivative source recovery. Final recheck found no new concrete blocker.

Earlier native attempts are retained as failed diagnostic runs; they are not counted as passes. Their harness issues were fixed before the complete sixth run. This evidence does not certify arbitrary power loss, full-world maximum-volume performance, unseen multiplayer behavior or human roleplay balance.

Ten stopped, marker-verified generated fixtures were removed after saving compact journal evidence in `../reports/lc05a-stopped-journal-evidence.zip`; logs/screenshots remain. The exact cleanup receipt is `../reports/dev15-cleanup.json`. The prepared source map, played worlds and unrelated acoustics work were preserved.

## Combined human review

Use a fresh dev.15 e4mc Host import and matching Player imports. No dedicated review server is required. Assign a separate Chancellor and at least two participating houses; three houses also permit supporter actions. In active play:

1. Obtain the token, approach the other representative, review a public reason and declare. Confirm that the target is not asked for permission and the public register stays readable.
2. With a third house, join/change/withdraw. Confirm founders cannot leave. Inspect the configurable Civil war threshold while non-active Editing.
3. Create typed peace/ceasefire at the treaty lectern. Check required houses, exact signatures, separate Chancellor witnessing and private/public visibility.
4. Declare anew after an agreement and confirm the old record remains. Check existing general/private treaty ceremony and complications with the group when available.

Do not edit or delete journals to simulate recovery in a played world. The automated disposable fixture already covers the interrupted-write path.
