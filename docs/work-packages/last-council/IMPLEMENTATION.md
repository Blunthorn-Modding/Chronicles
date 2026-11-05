# Last Council implementation

Source development version: `0.2.0-dev.1`. Branch: `feat/last-council`.
The approved scene prototype is the native UI authority. The original event brief and released alpha artifacts remain unchanged.

## Current slice

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

Open the existing Players menu, select a character and choose House & papers. `/chronicle council characters` is an operator fallback. Pause and enable Editing to assign houses and commit papers. `/chronicle council` (or existing own-card access) opens the personal view. Saving a public character card never changes a house assignment.

## Still pending

This is not all of LC-01. Core pool authoring/export and recipient-specific physical book grants, their inventory-full/interruption recovery, and the polished full cast-preview workflow remain pending. Fixed Player Role books are unchanged. No ballot, motion lectern, treaty, war, rebellion, banquet or complication gameplay is claimed yet. Voice/acoustics remains a separate review.

## Verification

Commands use `scripts/workspace.py`'s child-process-only Java/Gradle environment, with `--offline --no-daemon`:

- Full `:core:test :campaigns:last-council:test :campaigns:quiet-below:test` and module compilation/remaps passed: initial799 tests, zero failures/errors, seven existing Windows symlink skips. Final additions include cleanup-after-publication failure and independent knowledge/fact-goal cases; final full rerun passed in2m34s: 802 tests, 795 passed, 7 existing Windows symlink skips, no failures/errors.
- `python -m unittest discover -s scripts -p 'test_*.py'`:131 passed. An initial failure was the pinned third-party license file checked out with CRLF; `.gitattributes` now preserves its reviewed LF bytes without changing the reviewed hash or license text.
- `python scripts/last_council_smoke.py`: PASS at `../reports/last-council-20261105T193634428841Z/report.json`. Two actual Fabric clients, before/after server restart: non-operator rejection; unique house assignments; preview/commit; all four personal sections; exact recipient secrets/goals matched against trusted fixture; absence from public directory; real pronoun editor save and persistence; unchanged committed cast bytes; normal Players-menu extension; normal/compact native screenshots. Lead inspected frames. Earlier runs at192331 and193204 are supporting history, not final UI evidence.
- `python scripts/council_selection_smoke.py`: PASS at `../reports/council-selection-20261105T192842855369Z/report.json`. Core-only, QB-only, LC-only and both explicit selections start; selected LC's checked books/loot/events/roles contain no QB identifiers. Missing choice refuses before world creation. Mismatched existing world refuses while preserving its marker.
- Scenario matrix is server-only and used earlier matching logic before final private-draw/GUI polish. It reports the offline fixture's blocked Mojang public-key request and flat-generator settings message. No other logged errors/holds were found; direct RuntimeHolds inspection was not performed. Installing QB still registers its blocks, authoring tools and adapters: this verifies reusable-default separation, not complete mechanical isolation in a dual-mod client pack. Prefer Core+LC for this review slice.
- Independent source review checked private projection, roster uniqueness, permission/revision/token guards and stores. It found the post-publication cleanup failure, now fixed and fault-tested, and correctly identified physical delivery as unfinished.
- `git diff --check` passes. Original brief SHA256 remains `df20a7c1a8ff73f81b9f7dc1265666b4131ce62b260b79904180ea32fca5a44a`.

No released alpha artifact, production map or existing player world was changed. Successful disposable test roots are removed after verification; reports and screenshots remain. No long performance soak or human/group review is claimed. Ballot recovery has a pending owner decision about an audited void-and-replace action; do not silently choose its policy.

Final cleanup removed10 stopped disposable fixtures (1.6GiB), preserving reports/screenshots; `../reports/last-council-foundation-cleanup.json` lists the exact roots. Unrelated acoustics work is unchanged.
