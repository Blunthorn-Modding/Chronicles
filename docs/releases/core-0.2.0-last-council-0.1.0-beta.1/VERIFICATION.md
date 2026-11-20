# Verification — 20 November 2026

## Release artifacts

Minecraft Java **1.21.10**, Fabric Loader **0.19.5**, Fabric API
**0.138.4+1.21.10**, Java **21**. Both mods are required on client and server.

- Chronicle Core: `chronicle_core-0.2.0.jar`.
- The Last Council: `last_council-0.1.0-beta.1.jar`, requiring Core `>=0.2.0`.
- Backward-compatibility target: the unchanged, previously delivered
  `quiet_below-0.1.0-beta.jar`, SHA-256
  `cec92b8752a651b3cf101eb54b257143020b28ac0978a9e63daaf5b6b0c1d89a`.

Core and Council compiled code/assets match the verified dev.20 review JARs
entry for entry. Only `fabric.mod.json` differs: versions, the Council's minimum
Core dependency, and repository contact URLs. The release does not change the
shared development version or rebuild the old Quiet Below distribution.

## Automated checks

| Check | Result |
|---|---|
| Offline release build of all three modules | PASS |
| Java tests | 1,087 passed; 7 existing Windows filesystem-privilege skips; 0 failures |
| Python workspace/harness tests | 149 passed |
| Old Quiet Below symbolic linkage against Core 0.2.0 | 282 consumer classes, 388 Core member references resolved |
| Release/dev.20 entry comparison | Only release metadata differs |
| JAR integrity, target/dependency metadata and project notices | Checked when preparing the upload directory |

Build command is in `README.md`. The first 1 GiB build exhausted its Gradle
heap; the serial 2 GiB retry passed. This was a build-resource issue, not a
failing product test. Java counts are Core 629, Quiet Below 242 and Council 223,
including the seven skips. Python command:

```text
python -m unittest discover -s scripts -p "test_*.py" -v
```

## Quiet Below compatibility

The dedicated servers loaded the actual new Core release JAR and the original
Quiet Below release JAR. Native Fabric clients used the same production inputs
through Fabric's ordinary runtime remapping; current Core/Quiet Below class
directories and direct SnakeYAML classpath entries were removed. This tests
the released implementations, including Core's bundled dependency, rather than
silently substituting current campaign sources. It is not an installed
Minecraft Launcher or authenticated-account test.

Passing checks include:

- Ecology: actual growth/settings controls, vitality loss and dormant terrain,
  visual states, sensor behavior, physical reconnection and recovery. A real
  paused restart preserved campaign state and the exact ecology journal without
  catch-up. There were 166 client/state checks across these phases.
- Forge: two native clients, real single-item input/offer/result controls,
  timed enchantment, preserved item components, wrong-pair consumption, capped
  equipment return, reversed ingredients, power loss, full-inventory recovery,
  resource-pack rendering, and disconnect/restart escrow recovery.
- Altar: two native clients, private prayer/aftermath behavior, repeated lasting
  bargains, death persistence, optional-HUD reconnect, saved restart, corruption
  guards and network-ending removal. The Forge/Altar run passed 254 client steps.

Run commands:

```text
python scripts/release_compat_smoke.py --section ecology
python scripts/release_compat_smoke.py --section forge --section altar
```

The checked-in runner passed eight further full-inventory recovery repetitions
and a real Forge restart in a separate final run.
It creates only disposable loopback fixtures and never builds or publishes.
Its small diagnostic/driver JARs are test-only and are excluded from the
release package. Per-fixture provenance records original release hashes and
the client's exact classpath.

### Legacy probe limits

Earlier failed attempts remain in the local reports. An old ecology probe
assumed every navigation tab was visible at a small viewport; another tried to
overwrite the protected Heart during restart. The former uses a larger test
viewport; the latter is replaced by a non-destructive persisted-state restart.

The old Forge `transactions` server command opens containers while paused
without Building Mode. The **entire legacy transaction-matrix command is
omitted**, including its other assertions; it is not counted as passed.
The independent native UI/escrow/restart flows above still execute. Paused
container protection was not weakened.

One earlier Forge fixture lost its handler during the full-inventory case.
The unchanged-artifact traced rerun passed, with an active campaign, no runtime
hold and a present Forge gate throughout the ritual. The same relevant Core
screen/session/runtime classes are byte-identical to the previous release.
The evidence does not establish a product regression or a unique cause; the
failure is retained rather than silently discarded. All eight additional
full-inventory recovery repetitions passed without changing the production
JARs; no speculative product fix was applied.

## Council evidence and human acceptance

The code-identical dev.20 delivery passed the native election, succession and
foreign-affairs coordinator: four clients, seven phases and four server
restarts, including real controls, privacy/forgery/staleness guards, reason and
history persistence, Event/flag connections, and journal recovery. Earlier
milestone receipts cover the other delivered Council systems. Relabelling the
release metadata does not constitute a new multiplayer acceptance test.

`REVIEW_CHECKLIST.md` deliberately leaves all human checks unchecked. Final
content, your handcrafted map, group pacing, real microphones/remote voice UDP
and representative player load remain unaccepted. No three-hour soak was
repeated for this metadata release. No Modrinth publication, main merge,
server exposure or new review server was performed.

## Local evidence retained

Evidence is under the workspace's `reports` directory, outside Git:

- `core020-council-beta-build2.log`, `core020-council-beta-python-final.log`.
- `core020-quiet-below-linkage.json` and
  `core020-council-beta-byte-equivalence.json`.
- Ecology phases in `release-compat-20261120T133319390079Z` (later unrelated
  legacy Forge-matrix failure makes that aggregate report FAIL; ecology passed).
- Forge/Altar PASS: `release-compat-20261120T135537694148Z`.
- Forge repeated-recovery/restart PASS: `release-compat-20261120T140447738231Z`.
- Previous-Core control PASS: `release-forge-baseline-20261120T135040409715Z`.
- Council dev.20 PASS: `council-suite-20261120T073027470169Z`.
- Final artifact/protection/cleanup receipts prefixed `core020-release-`.

Original release files, existing review packs and the source review world are
protected by before/after hashes. Stopped generated worlds are removed only
after compact configuration/journal/provenance archives are retained; reports,
logs and screenshots remain available.
