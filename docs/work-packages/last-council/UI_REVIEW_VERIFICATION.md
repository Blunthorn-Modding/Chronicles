# Council review corrections - 6 November 2026

Source `0.2.0-dev.4`, branch `feat/last-council`. This is a development review, not a new release or full scenario completion. The [design corrections](UI_REVIEW_CORRECTIONS.md) were prepared by the dedicated UI agent and reviewed by the lead against the owner's feedback. A separate read-only reviewer checked authority, private projection, cast publication and the motion gate; no material defect remained. Lead inspected the actual diff and native captures.

## Delivered behavior

- Explicit Chancellor office, independent from OP, with House/Chancellor exclusivity. OP house members receive no procedural authority. Office changes require non-active Editing and no open ballot; no office is inferred from legacy OP status.
- Motion lectern has visible rows and separate read-only details with eligible house actions only. Other houses use the same list/section presentation. The Chancellor opens separate proceedings with rebindable I or the existing command.
- All pending ordinary motions must finish or be deferred before Regency. Deferrals clear only their meeting and return next meeting; a void is unresolved. Regency is immediately above Close meeting, directly confirmed, and keeps active ballot details/Close/Void visible.
- Frozen public-card names and vanilla head rendering. Public Yes/No/Abstain outlines are green/red/amber; secret heads never disclose individual choice or turnout. Read-only observers have no disabled voting controls.
- First active play commits house papers automatically. The full draw is saved before publication; retry/restart uses that receipt, never rerolls. Failed publication holds play and offers explicit recovery. Existing manual casts remain respected.

## Storage and scope

`chancellor.json` is strict schema1. Proceedings writes schema3 and reads1/2; missing old deferral data is empty, never guessed from free-text notes. Private `automatic-cast.json` and `cast.json` stay world-owned, outside reusable content exports. Generic ballots/cards/draw helpers remain Core; office/cast/meeting policies remain Last Council. Matching client/server jars are required. The original brief, released alpha and unrelated acoustics work are preserved.

## Automated evidence

- Full offline Java build/test/remap:859 tests,852 passed,7 existing Windows privilege/symlink skips; no failures/errors. Focused Last Council test/remap/launch rebuild passed after final UI and active-ballot selection changes (54 tests).
- `python -m unittest discover -s scripts -p 'test_*.py'`:131 passed. Review/succession scripts compile.
- Native ballot suite: `../../../../reports/council-ballots-20261106T122312227046Z/report.json` PASS. Three actual clients verify OP-as-house refusal, physical lectern role separation, character-name capture, automatic private papers, public outlines, stable secret voter snapshots, immutable choices, Region/phase checks, reasoned void/new replacement and restart. Rebound ballot/proceedings keys respect containers, nested drafts, text input, confirmations and delayed replies. Normal and320x240 native captures inspected.
- Native succession suite: `../../../../reports/council-succession-20261106T122057250109Z/report.json` PASS. It exercises the actual Regency sidebar and confirmation, current ballot details/Close/Void, motion gate, same-meeting technical replacement, fixed slate/electorate, elimination, returned deferral next meeting, restart, crowning and later ordinary business.

Earlier retained failures were harness expectations: a deferred motion correctly returned next meeting, and selected rows gained a visible `>` prefix. Assertions were corrected to the intended behavior, then the full native suites passed. These are not hidden product failures.

The final ordinary-ballot suite was rerun after the direct Regency/active-detail correction and passed at the report above. A subsequent German key-label resource correction changes no executable classes; its jar was remapped and the review redeployed with matching hashes. Deployment evidence: `../../../../reports/council-dev4-deployment.json`.

Eight stopped disposable runtimes (seven native fixtures and the old dev3 review) were removed after retaining reports, freeing1.40GiB. Only the current dev4 review remains. Cleanup receipt: `../../../../reports/council-dev4-cleanup.json`.

## Review boundary

The corrected native screens and wording still need the owner's hands-on review. Random complication pools/real LC08 gate, treaty/war/rebellion/banquet play, physical house-paper delivery and other listed work packages remain pending. There is no new3hsoak, real group/voice validation, final map or release claim. See [review guide](REVIEW_COUNCIL.md) and the outstanding work packages.
