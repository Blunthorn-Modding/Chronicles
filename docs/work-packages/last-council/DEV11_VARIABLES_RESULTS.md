# dev.11 secret variables and unique assignment

The owner approved secret-local numeric ranges, explicit Own House / Other House variables, a single assignment of each secret per experience, identical resolved information in knowledge copies, and removal of source sample previews. Human treaty and complication group reviews remain deferred, not passed.

## Delivered behaviour

Secret authoring keeps Name/weight, Text, Applicable Houses and optional Number variables. Add/edit a number using its name and inclusive signed integer bounds. Names remain stable during range editing. Insert variable offers Own House, Other House and this secret's named ranges; it inserts at the current text selection and preserves the cursor when returning at the same editor dimensions. No fixed-string-variable editor or JSON sample preview remains. Catalogue Preview controls are removed. Event and Region previews are unchanged.

Core owns `NumberRange`, including uniform inclusive sampling across the full signed-int domain. Last Council owns range definitions, context and secret allocation. `${house.own}` is the original recipient House; `${house.other}` selects one other assigned House for this secret; `${number.capacity}` samples that secret's configured range. Repeated occurrences and every knowledge/physical copy use the same resolved text. Numbers and Other House are not rerolled on copy, reconnect or restart. A secret may remain unassigned; it cannot be assigned to multiple Houses. Knowledge exposure remains independently random and may have zero, one or several observers.

Weighted global ordering plus augmenting paths satisfies scopes and counts without letting a flexible House take a restricted House's only option. Source setup shows capacity errors. Impossible allocations fail before any papers are published; no silent count reduction or secret duplication occurs. Goal templates keep their separate per-House draw semantics.

## Compatibility and persistence

Reusable source schema 2 adds named number ranges; schema 1 remains readable. Empty number maps are omitted in canonical output so valid exact-2-MiB legacy content still exports within the same budget. Old house.title/other.title aliases and fixed facts remain readable. Legacy fixed values are local to their secret, with unambiguous cross-secret values retained for older goal references. Existing committed papers are never regenerated.

New private cast schema 2 retains consumed secret IDs; old schema 1 casts re-encode unchanged. Enrollment schema 2 preserves cumulative IDs across restart while reading old and mixed entries and retaining original baseline fingerprints. Late enrollment excludes used IDs and keeps all old papers intact. For old casts with no recorded IDs, all source templates eligible for any prior House are conservatively reserved: an unused shared template may be unavailable rather than risking a duplicate. The editor's post-issuance lock is unchanged.

Conditional legacy cross-secret/goal references can require a compatible combination. Only when the normal matching result cannot resolve those old references, a deterministic bounded search checks compatible alternatives with provider-coverage pruning. Its 20,000-branch limit fails explicitly, including on late enrollment; it never reports budget exhaustion as proof of impossibility or publishes a partial draw. Canonical secret-local number variables use the normal matching path. Complex old dependency sets may need simplification if that compatibility budget is exceeded.

## Evidence

- Full three-module Gradle tests/remaps passed (`../reports/dev11-complete-build.txt`), followed by final Last Council tests/client compilation/remap (`../reports/dev11-native-final-build.txt`). Totals: Core596, Last Council123 and Quiet Below242 =961 tests, zero failures/errors, seven existing platform skips. Tests cover full integer-range edges, strict schema 1/2 and exact-size/Unicode round trips, scope feasibility, unique IDs, local names, pinned House/number substitutions, zero/multiple knowledge observers, conservative old-cast handling, enrollment metadata restart and legacy dependency providers including low-weight cases.
- Final native editor initial/restart PASS: `../reports/council-editor-20261111T104249930528Z/report.json`. It creates a name-only House and ordinary secret, edits a range, exercises insertion at the caret and focus/cursor return, checks forced 320x180 number-form geometry plus normal layout, preserves multipart legacy data, issues two unique secrets with an actual 2-5 range result, and retains cast/data through restart. Existing nonoperator, concurrent stale-write, permission-revocation timeout and cast-lock checks also pass. Lead inspected the rendered compact number form.
- Native private-book initial/restart PASS: `../reports/council-papers-20261111T104533156264Z/report.json`. The fixture assigns Vale, ensures its numeric wheat secret is in play, verifies the actual book against its committed 2-5 result, excludes foreign secrets, checks full inventory and repeated-request conservation, and retains delivery receipts through restart.
- Python script suite:134 tests passed (`../reports/dev11-python-tests.txt`). No new dependencies or third-party assets were introduced.
- Native portable content/profile suite PASS: `../reports/council-content-20261111T104915624830Z/report.json`. Large schema-2 source content including ranges exports without private live state, imports fresh, survives edit/restart/re-export, preserves initialized missing-file refusal, keeps empty profiles empty and migrates the old profile file exactly once. Its existing Scenario-export review remains; source secret/goal sample previews are removed.
- Final Python suite passed again:134 tests (`../reports/dev11-python-final.txt`), and modified scripts passed `py_compile`. `git diff --check` and unchanged brief hash were verified.
- Matching dev.11 e4mc Host/Player packs were built with CRC, checksums and exact final mod-byte checks. The Host includes the fresh numeric wheat default; only unchanged old defaults in the stopped disposable pack-source fixture were refreshed. The played launcher world was not touched. No local review server/plain pack was created or left running. Receipt `../reports/council-e4mc-pack.json`.
- Removed 6 stopped disposable fixtures (1.04 GiB); reports and screenshots retained. Receipt `../reports/dev11-cleanup.json`.

Final SHA256: Host `e1d768b2131011b850cfcc6905c237c6d8efb935b3e1a22bda1497d2878a42bc`; Player `a0e49af5643d2a9763cfb1055d8287e7d55e44ad4b9486d144d191c0920b63c9`.

## Owner review: 11 November

The owner accepted the variable editor as cleaner and understandable. One polish item remains: remove the bottom-left `Field page 1/4` hint, which appears not to update. Bundle its removal with the next development milestone; retain useful save/error feedback and create no standalone review build for this change. This acceptance does not mark the deferred treaty/complication multiplayer tests passed.
