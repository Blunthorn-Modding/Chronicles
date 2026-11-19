# LC06 rebellion and Region access - development 17

Status: implementation, independent source review and automated native verification complete; human multiplayer review remains pending. The owner approved the simplified workflow on 18 November 2026. Human multiplayer acceptance remains separate and unpassed.

## Behavior

My house has a separate Rebellions section. Its registry and the public `/chronicle council rebellions` command share the same presentation. A House can declare a public faction or join a selected faction after confirming the loss of its Council seat, voting, proposals/sponsorship, candidacy and palace access. It remains a participating House and keeps its papers. Ordinary House wars retain Council rights. There is no free Leave or no-Council reinstatement path. Public declarations link to the registry.

A declaration is refused during an open ballot, for the crowned House, without eligible participation, or before palace access has a safe outside Point. Current rebels are projected out of Council eligibility without rewriting the committed cast. A remaining succession candidate is withdrawn durably; reinstatement cannot return it to an already locked succession.

The existing motion draft adds Reinstate Houses and a named selection. The server fixes its threshold at two thirds of the electorate captured at opening. Failed or void votes do nothing. A passed result restores only the exact pinned memberships. Old proposals cannot restore a later rebellion. The existing saved CLOSE spans the Core ballot and exact reinstatement receipt before clearing pending; an interrupted operation resumes that same result through `/chronicle council recover`.

The Chancellor has one Allow visit action in proceedings. It selects a rebel House and grants one palace visit. The grant waits for entry, becomes Inside on entry and ends on exit. It never restores a Council vote. Grants belong to individual Houses and survive restarts; a saved Inside grant is ended if its representative returns at an outside position.

## Authoring and ownership

On the existing Regions page, enable Region access through Choose subtypes, then choose an existing outside Point. There is no separate palace registry or permission-level hierarchy. Core owns trusted access providers, world bindings, exact geometry references, safe exits and movement hooks. Last Council supplies the rebel eligibility and visit rules. Outside Points must be loaded, safe, in the same dimension, and outside every guarded Region. Linked bounds and Points cannot be silently changed or deleted. Stop play and enable Editing for configuration; active rebels prevent removing their access assignment.

Explicit Editing and global Building access are authoring exceptions, not Council eligibility. Creative and Spectator alone do not grant entry. Hooks cover movement, position commits, teleport transfers, admission/respawn, passenger seating and spectator cameras; individual native coverage is recorded below rather than inferred from hook presence.

Bindings live in world/chronicle/region-access.json with its initialized witness. Rebellion state lives in world/chronicle/last-council/rebellions.json with rebellions-initialized. These are map/world records, not reusable scenario defaults. Live file/witness mismatches and uncertain writes fail closed and hold play. Recover political files exactly before Council recovery; repair corrupted Core access definitions while stopped. Reusable content exports exclude live membership and receipts.

Proceedings schema 5 reads schemas 1-4 explicitly, preserving General and foreign motions, captured electorates and pending operations. Matching Core and Last Council dev.17 are required. Use a stopped pre-upgrade copy for rollback; do not downgrade journals in place. The Last Council minimum Core dependency is raised to dev.17. Released alpha artifacts, the source brief and played worlds are unchanged.

## Evidence

- Final offline build: `../reports/lc06-delivery-build.log`. 1,052 Java tests: 1,045 passed, seven existing skips (Core 604, Quiet Below 242, Last Council 206). The final migration tests isolate legacy foreign-field rejection and reject both null and non-null reinstatement fields in schema 4 without rewriting the source file.
- Python: `../reports/lc06-python-delivery-clean.log`, 145 tests passed, including nine native-coordinator checks. The earlier direct PowerShell redirect rendered Python's successful stderr output as NativeCommandError; the captured subprocess rerun returned zero. The earlier log is retained.
- New domain tests cover strict journals, idempotent operation IDs, factions, membership, per-House visits, state bounds/reserved resolution capacity and exact restoration receipts. Proceedings tests cover failed/void/passed results, publication uncertainty, finalization failure, actor mismatch, ABA and schema migration. Core tests cover geometry, corrupt/missing data and witnesses.
- Final rebellion native run: `../reports/council-rebellion-20261119T084839087658Z/report.json` PASS. Four actual clients, four phases and three saved restarts cover the existing Region subtype/Point setup, declaration/join and retained confirmation draft, public history and participation, actual walking towards the boundary, Creative teleport refusal, Spectator cameras, passenger seating/vehicle transfers, dimension commands, real death and safe respawn, proposal/sponsorship refusal during a visit, per-House WAITING/INSIDE/ENDED visits, failed/void/two-thirds reinstatement and a real Windows replacement-lock recovery. Saved Inside admission and an outside saved-playerdata mismatch are both tested. The lead inspected normal/compact embedded UI captures and corrected cramped layout.
- Sequential regression coordinator: `../reports/council-suite-20261119T085509989010Z/report.json` PASS. Foreign affairs, ballots, succession, conflicts and content each returned zero with a fresh PASS report and unchanged source-built JAR hashes. Its selected suite commands, reports and bounded logs remain in that directory. The final build then added a Point-label length bound and migration tests; no gameplay rule changed after those native runs.
- Independent read-only review found no remaining concrete blocker after witness, uncertain-write, provider, draft-retention and public-link fixes. This is source review, not additional runtime certification.

Intermediate failed reports remain unchanged. Early rebellion fixtures failed on navigation/compact viewport assumptions, an exit Point obstructed by their own boat, and a misspelled fixture action. A superseded two-phase PASS had a weak walking assertion; the final four-phase run uses actual movement. The first foreign regression stopped at a schema-4-only harness assertion after initial gameplay; correcting that harness to schema 5 produced the recorded successful restart run.

Reproduce the full build using the workspace toolchain wrapper:

```powershell
python -c "import sys;sys.path.insert(0,'scripts');import workspace as w;w.run([w.REPO/'gradlew.bat','--offline','--no-daemon','build',':campaigns:last-council:writeSmokeClientLaunch'])"
python -m unittest discover -s scripts -p test_*.py
python scripts/council_suite.py --suite rebellion
python scripts/council_suite.py --suite foreign --suite ballots --suite succession --suite conflicts --suite content
```

Serialize native suites and builds because clients share class directories and fixture ports. `--list` and no coordinator arguments only list available suites; they do not launch clients. Human group acceptance is never inferred from a technical PASS.

## Artifacts and cleanup

Matching dev.17 e4mc Host/Player packs pass CRC, exact final-JAR, GUI2/narrator/onboarding, Survival/Normal and private-progress checks. Their clean proceedings use schema 5 and discard live reinstatement targets. Region access metadata travels with maps; live rebellion/visit/receipt journals do not enter a clean review export. The stopped source review's 178 files and nine earlier artifacts remain unchanged. See `../reports/dev17-pack-validation.json` and the current e4mc README. No dedicated review server or plain/server pack was created.

Fourteen stopped generated fixtures were removed after resolved-path, fixture-marker, reparse-point and session-lock checks. `../reports/dev17-cleanup.json` records 2.58 GiB reclaimed. Reports/logs/screenshots remain; 950 compact journal/config/playerdata evidence files are retained in the CRC/hash-verified `../reports/lc06-stopped-journal-evidence.zip` (about 477 KiB). No played or source world was deleted.

## Remaining review gates

The 19 November [authority clarification](COUNCIL_AUTHORITY_AND_VOICE_CLARIFICATION.md) removes separate realm ratification from outstanding scope. Active amendments are optional future work, not a required completion gate. Human rebellion/visit behavior on the author's map, treaty/conflict/complication group acceptance, final content/balance, room-audio policy/integration and release rehearsal remain pending. Physical portal transit and maximum guard-count/performance loads were not separately certified. Direct cross-dimension commands exercise transfer guards but do not replace a physical portal test. No Modrinth publication, released-alpha replacement or main-branch merge is part of this delivery.
