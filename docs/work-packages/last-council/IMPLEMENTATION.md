# Last Council implementation

Source development version: `0.2.0-dev.4`. Branch: `feat/last-council`.
The approved scene prototype is the native UI authority. The original event brief and released alpha artifacts remain unchanged.

## Current review corrections

The owner's 6 November feedback supersedes the earlier operator/Chancellor and manual-issuance assumptions below. Council setup has separate House, Chancellor and Papers tabs; only an explicitly appointed Chancellor controls proceedings, and cannot also own a house. Operators can otherwise play houses. The lectern has only house actions, while I opens the Chancellor's separate proceedings screen. Visible lists and read-only section cards replace motion/house pickers and black text boxes. Public heads use captured character-card names and choice outlines; secret heads expose no turnout. Pending ordinary motions must complete or be deferred before Regency; deferrals return next meeting. First active play automatically publishes a saved private draw through automatic-cast.json and cast.json.

See [correction design](UI_REVIEW_CORRECTIONS.md), [verification](UI_REVIEW_VERIFICATION.md) and [review guide](REVIEW_COUNCIL.md). Proceedings schema3 reads1/2 without inventing old deferrals. Existing casts remain fixed; absent office files appoint nobody. Core remains responsible for generic cards, ballots and drawing primitives; political office, House casting and meeting rules remain Last Council-owned. No released alpha or Quiet Below behavior is changed.

## Original foundation slice (historical)

LC-01A and the first LC-01C cast path are in implementation:

- New independent Fabric module; no dependency on Quiet Below.
- Existing character card remains the public identity and editor, including pronouns.
- Character / My house / Other houses presentation; own information, secrets, house goals and known-other-house secrets are distinct sections.
- GM assignment popup extends the existing Players detail. Houses have unique representatives and candidacy metadata.
- Configurable, bounded weighted secret/house-goal pools; actual assigned facts are generated before independent cross-house knowledge rolls. Zero observers is valid.
- GM preview/reroll followed by explicit commit. The committed world cast remains fixed across restart; ordinary requests cannot choose another private recipient or retrieve seed/exposure metadata.
- Generic literal-template, weighted-draw and atomic-file helpers belong to Core; Council facts, roster rules and presentation belong to Last Council.

## Setup for this development slice

Install matching Core and Last Council jars for Minecraft 1.21.10. With one scenario mod, that scenario is selected automatically. With several scenario mods on a dedicated server, explicitly choose one in `config/chronicle/scenario-selection.json`:

```json
{"schema":1,"scenarioId":"last_council:scenario"}
```

A world identity marker prevents switching an already identified world to another scenario. A client with several scenario mods may join a remote server without this server-side configuration. An integrated server needs its explicit selection before startup. An existing empty-custom-authoring profile is already an explicit selection and cannot be combined with this file.

The sample eight-house pool is intentionally illustrative, not final lore. On first startup it creates `config/chronicle/last-council/houses.json` (schema1). Edit only while stopped. Public house IDs, profile fields, candidate defaults, secret/goal weights and scope, draw counts and knowledge percentage live here. Empty custom-authoring starts with empty house content. The world owns `chronicle/last-council/roster.json` and `cast.json`; these private files must not be served as public assets or bundled in reusable definitions.

Open the existing Players menu, select a character and choose House & papers. `/chronicle council characters` is an operator fallback. Pause and enable Editing to assign houses and appoint a separate Chancellor. Private papers issue automatically when active play begins; early preview/commit remains optional. `/chronicle council` (or existing own-card access) opens the personal view. Saving a public character card never changes a house assignment.

## Still pending

This is not all of LC-01. Core pool authoring/export and recipient-specific physical book grants, their inventory-full/interruption recovery, and the polished full cast-preview workflow remain pending. Fixed Player Role books are unchanged. LC02 adds ballots, motion lecterns and proceedings as described below. Treaty, war, rebellion, banquet and complication gameplay remain pending. Voice/acoustics remains a separate review.

## Verification

Commands use `scripts/workspace.py`'s child-process-only Java/Gradle environment, with `--offline --no-daemon`:

- Full `:core:test :campaigns:last-council:test :campaigns:quiet-below:test` and module compilation/remaps passed: initial799 tests, zero failures/errors, seven existing Windows symlink skips. Final additions include cleanup-after-publication failure and independent knowledge/fact-goal cases; final full rerun passed in2m34s: 802 tests, 795 passed, 7 existing Windows symlink skips, no failures/errors.
- `python -m unittest discover -s scripts -p 'test_*.py'`:131 passed. An initial failure was the pinned third-party license file checked out with CRLF; `.gitattributes` now preserves its reviewed LF bytes without changing the reviewed hash or license text.
- `python scripts/last_council_smoke.py`: PASS at `../reports/last-council-20261105T193634428841Z/report.json`. Two actual Fabric clients, before/after server restart: non-operator rejection; unique house assignments; preview/commit; all four personal sections; exact recipient secrets/goals matched against trusted fixture; absence from public directory; real pronoun editor save and persistence; unchanged committed cast bytes; normal Players-menu extension; normal/compact native screenshots. Lead inspected frames. Earlier runs at192331 and193204 are supporting history, not final UI evidence.
- `python scripts/council_selection_smoke.py`: PASS at `../reports/council-selection-20261105T192842855369Z/report.json`. Core-only, QB-only, LC-only and both explicit selections start; selected LC's checked books/loot/events/roles contain no QB identifiers. Missing choice refuses before world creation. Mismatched existing world refuses while preserving its marker.
- Scenario matrix is server-only and used earlier matching logic before final private-draw/GUI polish. It reports the offline fixture's blocked Mojang public-key request and flat-generator settings message. No other logged errors/holds were found; direct RuntimeHolds inspection was not performed. Installing QB still registers its blocks, authoring tools and adapters: this verifies reusable-default separation, not complete mechanical isolation in a dual-mod client pack. Prefer Core+LC for this review slice.
- Independent source review checked private projection, roster uniqueness, permission/revision/token guards and stores. It found the post-publication cleanup failure, now fixed and fault-tested, and correctly identified physical delivery as unfinished.
- `git diff --check` passes. Original brief SHA256 remains `df20a7c1a8ff73f81b9f7dc1265666b4131ce62b260b79904180ea32fca5a44a`.

No released alpha artifact, production map or existing player world was changed. Successful disposable test roots are removed after verification; reports and screenshots remain. No long performance soak or human/group review is claimed. The owner has since confirmed audited void-and-replace for open ballots, configurable hotkey/command access and same-meeting regency replacement after a technical void.

Final cleanup removed10 stopped disposable fixtures (1.6GiB), preserving reports/screenshots; `../reports/last-council-foundation-cleanup.json` lists the exact roots. Unrelated acoustics work is unchanged.


## Council meetings and ballots (LC02)

The second slice adds the optional Core ballot engine and Last Council proceedings. Bind a Region's **Council voting** subtype and a lectern Block site's **Council motions** subtype through the existing editor. No alternate geometry registry or new editor tab is required.

The Chancellor uses `/chronicle council proceedings` to call the gathering, open a meeting, prepare/admit/defer motions, open and close individual ballots, recess and finish. Other eligible houses propose or sponsor at the physically bound lectern. Public motion reading is available near that lectern without a political seat, including author inspection in Building Mode; mutations require eligible active play. The normal paused-interaction gate still applies.

Opening captures the representatives then inside the voting Region. The roster, threshold and geometry stay fixed through disconnects and restarts. Each house's first confirmed choice is final. Secret open/void ballots expose no other choices or partial totals, even to the Chancellor; closed secret ballots expose aggregates only. Server authorization/presence checks apply equally to UI and packets. Default **R** (rebindable in Controls) or `/chronicle council vote` opens the current ballot; a notice does not replace a workstation screen. Dismissal never abstains.

Open ballots can be voided for a required public reason; the archive retains their original immutable choices. Replacement uses a new ID and electorate. A completed political result cannot be rolled back with this action. Core ballots and scenario proceedings have separate observed-byte journals; saved intents make opening/closing/void recovery reuse the same operation, rather than recapture the electorate. Failed persistence holds operations and exposes Recover. Recovery cannot dismiss an external edit.

The packaged Council-call Event uses the existing per-source-occurrence message/bell delivery. A new authorized call is a new occurrence; a retry/restart cannot replay the same one. Pending source cancellation uses proof from the fully loaded, unchanged producer journal. Ordinary meetings are independent of Chronicle play-session boundaries and technical pause.

Current omissions: actual hidden complication-pool timing/progression gate (LC08), succession integration (LC03), full native authoring of the two subtype bindings, physical treaty ceremony and later political systems. Native ballot fixtures inject valid authoring bindings, so their successful vote proof does not establish the subtype-picker flow. No multiplayer human review or long performance soak is claimed.

LC02 evidence is recorded in `BALLOTS_VERIFICATION.md`; initial LC01 evidence above remains historical. The new Core service has no global gameplay initializer, so unused campaigns do not gain ballot rules or menus.


## Succession (LC03 in progress)

The Chancellor's existing proceedings screen now has **Request regency vote** and public succession status. Candidate setup still uses the existing house-assignment popup before the first opening. Opening locks the initial slate; a completed ballot either crowns on majority, removes exactly one lowest candidate (persisted draw for a tie), or leaves the throne vacant after an unsuccessful two-candidate/confirmation vote. Elimination does not remove a Council vote. Later rounds need a later Council meeting. A technical void preserves the slate and permits a new same-meeting ballot; it never retallies or changes the old votes.

The scenario's election state shares the proceedings journal, now strict schema2 with schema1 migration. A result, including a drawn tied-last house, is committed in the pending-close intent before closing Core's ballot. Recovery applies that same result without another random draw. Chronological validation prevents revived/omitted candidates and outcomes after an election ends. All secret choices remain in Core's ledger; public result text contains only the constitutional outcome and any announced elimination draw.

The LC08 gate and other explicitly deferred LC03 items are listed in `LC-03-succession.md`; this is not a claim that the whole package or scenario is complete. Never expose reset/withdrawal/political rollback controls merely because pure domain methods exist.

The final source checks and native proofs for this slice are in [succession verification](SUCCESSION_VERIFICATION.md); the current human checklist is [Council review](REVIEW_COUNCIL.md).


## 8 November group-review corrections (dev.5)

Late enrollment uses a private, atomic world overlay containing membership and already-drawn papers, validated against unchanged original roster/cast/issuance files. Existing characters and knowledge do not reroll; the overlay survives restart and cannot add candidates after succession starts. Ballot screens share readable section builders, captured names and actual immutable attempt history, with bounded four-attempt history pages. Public choices and secret aggregates use Core's existing projections. Only Chancellor proceedings receive live anonymous secret submitted counts. Present eligible characters form the first Regency slate; subsequent rounds wait for all remaining characters. Absolute majority is unchanged.

Core owns the reusable text-focus guard and server-side ballot submission count. Last Council owns the House overlay, source catalogue, progression policy, permission-checked projections and Council UI. The read-only OP source catalogue is not the remaining pool-authoring/export milestone. Native evidence, review limitations and the current human checklist are linked from [group-review verification](GROUP_REVIEW_VERIFICATION.md).
