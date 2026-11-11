# LC07A implementation and review boundary

11 November 2026. The optional banquet's policy-independent foundation is implemented. **LC07 is not delivered gameplay.** Its new UI and illness policy still need owner review before native controls, items or effects are wired in. Treaty and complication multiplayer reviews remain deferred and unpassed.

## Implemented

- Last Council private immutable supply/illness ledger with bounded retained operation receipts; no Core gameplay changes. Individual drink, poison and antidote tokens support pure issuance, contamination, consumption and cure transitions. Exact replays leave state unchanged; altered request payloads, stale revisions, spent tokens and exhausted capacity refuse changes.
- Illness onset and duration use supplied ticks and caller-selected clock eligibility. No timing defaults, effects, death/offline policy, participation changes or player inventory operations are installed. The transition API supports the proposed cure and repeat alternatives without selecting live policy.
- Strict schema-1 storage uses Core AtomicDataFile: bounded bytes/records, canonical UUIDs, exact shapes/types, duplicate-key and UTF-8 rejection, supply/receipt/exposure consistency, external-edit refusal and save-before-memory publication. Full permitted token/receipt capacity round-trips below the 8 MiB file bound. Tokens and receipts are retained rather than evicted. This is an atomic ledger file, **not** an atomic Minecraft inventory transaction or an arbitrary disk-fault guarantee.
- Removed the source editor's idle `Field page` hint. Its actual save, timeout, refusal, stale-draft and connection feedback remains.
- Dedicated UI design agent produced a lead-reviewed [mockup](../../design/last-council-banquet.html). Proposed Game setup settings, confirmed gifts and separately restricted moderation are review-only; see [design notes](LC07_UI_DESIGN.md).

## Evidence

Command, through the configured Java toolchain:

```text
python -c "import sys;sys.path.insert(0,'scripts');import workspace as w;w.run([w.REPO/'gradlew.bat','--offline','--no-daemon',':campaigns:last-council:test',':campaigns:last-council:clientClasses'])"
```

PASS: **134 Last Council tests**, zero failures/errors/skips, including eleven new banquet domain/store tests; client compilation passed. Log: `../reports/lc07a-build.txt`. An initial compile found a BOM in one newly authored Java file; it was removed before the passing run.

Local Edge prototype check: **12 checks PASS**, no JavaScript exceptions. Normal 1280x900 and compact 640x480 viewport captures inspected; the 550x336 compact frame contains its scrollable content and footer. Reproducible script/captures: ignored `output/playwright/banquet-smoke.js`, `banquet-normal.png`, `banquet-compact.png`. This is not native access enforcement or gameplay verification.

Independent read-only review caught invalid saved illness timers exceeding recorded exposure; constructor validation and malformed-codec regressions were added. Re-review found no remaining concrete findings in scope, including retained source-editor feedback. Lead also inspected AtomicDataFile's existing stale checks and create-only/atomic-replacement publication.

`git diff --check` passes. Source brief SHA256 remains `df20a7c1a8ff73f81b9f7dc1265666b4131ce62b260b79904180ea32fca5a44a`. Existing cast, map, profiles and unrelated acoustics files are unchanged.

## Next slice after owner review

Confirm the UI and symptom/timing, death/offline, cure/repeat policies. Then implement finite physical supplies with identical normal drink presentation, actual full-hunger use and offhand contamination, recoverable inventory/ledger transactions, session-aware clocks/effects, guarded native settings/gifting/moderation and reusable settings export. Verify exact inventory/restart/failure behavior and privacy through native clients before delivery.

No native banquet test, world, server or new review pack was created. Existing dev.11 Host/Player packs and version remain unchanged; the footer fix is in source for the next bundled build. No new dependency, assets/audio, release or merge is involved.
