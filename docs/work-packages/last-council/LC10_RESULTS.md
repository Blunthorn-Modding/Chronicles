# LC10 automatic Voice rooms ? dev.18

The owner approved the workflow on 19 November 2026. Development branch: `feat/core-voice-rooms`, from `cb5e7f6`. This is an optional reusable Chronicle Core feature; Last Council supplies no separate voice rules. The original gameplay brief and released alpha artifacts remain unchanged.

## Delivered behavior

Select a saved Region, then **Choose subtypes > Voice room > Apply**. Chronicle automatically creates its hidden, isolated Simple Voice Chat group even before anyone enters. Everyone physically inside joins, including operators, Creative/Spectator players and nonparticipants. Leaving returns to proximity voice; an earlier manual group is not restored. Membership continues while Chronicle play is paused or Building Mode is enabled.

There is no group picker, ID entry or separate room catalogue. Cancel preserves the saved selection. Disable the subtype before changing/deleting bounds. Same-dimension overlaps are refused; adjacent rooms and different dimensions are allowed. Normal descriptions stay concise; Debug shows status and membership. Disabling removes only owned groups; unrelated groups remain intact. Restart reconstructs groups from map-owned definitions.

World data: `chronicle/voice-rooms.json` plus `voice-rooms.initialized`, pinning exact Region revisions. Portable map exports retain these definitions, excluding runtime group IDs/player membership. Corrupt/missing initialized data, uncertain writes or lost integration hold play and block speech authorization rather than claiming privacy is working. Stop the server before restoring the matching room files/Regions. No automatic recovery guesses at changed ownership or missing data.

## Dependency and license

The integration uses the public Simple Voice Chat API **2.6.20**, compile/test only, with the optional locked Minecraft **1.21.10 / SVC 2.6.22** runtime. [Exact dependency lock](../../../config/voice-room-dependencies.lock.json). No SDK or voice implementation is bundled into Chronicle JARs. The review packs reference the official Modrinth download. Core still launches without voice; enabling the subtype then reports the missing dependency. Incompatible installed older versions refuse load.

Enhanced Groups and Clocktower implementation/assets are neither required nor copied. Chronicle remains MIT; third-party licenses remain their own. The API adapter accounts for the pinned runtime's 16-character group-name limit and broken non-null missing-group wrapper. UUIDs, never truncated names, establish ownership.

## Verification

- Full offline `build :campaigns:last-council:writeSmokeClientLaunch`: PASS, `../reports/lc10-final-build.log`. Java: **1,077 total, 1,070 passed, seven existing Windows privilege skips** (Core629/6 skips, Quiet Below242/1 skip, Last Council206). Includes **25 focused voice tests**, all passed: strict data/witness/changed-byte handling, geometry/overlap, groups/retry/uncertainty/ownership, name truncation and pinned API lookup behavior.
- `python -m unittest discover -s scripts -p 'test_*.py'`: **146 PASS**, `../reports/lc10-final-python.log`.
- Initial corrected real-Fabric/SVC two-client trial: **PASS**, `../reports/voice-rooms-20261119T150442407546Z/report.json`. Three phases/two restarts: empty-room creation, actual API memberships across rooms/outside, unrelated-group preservation, teardown/reconstruction and client/server startup with SVC removed. Production sound/join/leave/create event handlers are driven with proxy events and real API connections, including delayed old-snapshot authorization and first-room enable races. This is not raw speech-packet injection or an audibility test.
- Final native checkbox and Council access regression: **PASS**, `../reports/council-suite-20261119T154247357466Z/report.json`, with exact final JAR hashes unchanged throughout. Command: `python scripts/council_suite.py --suite voice --suite rebellion`. Voice: three phases/two restarts/two clients, including the real shared picker. Council: four clients/four phases/three restarts, covering physical/Creative/mounted/camera/dimension/respawn access, one-visit lifetime and interrupted reinstatement recovery. No Gradle build ran concurrently with native clients/shared class directories.
- Independent source reviews found no remaining concrete blockers after corrections for lifecycle uncertainty, missing witnesses, movement invalidation, atomic adapter publication and stale snapshots. Latest SDK-delta review was read-only; test results come from the commands above.
- Wide/compact mockup checks and native subtype captures were inspected. Native controls reuse the existing shared popup. Compact capture includes transient vanilla login toasts; controls remain inside the frame and the actual checkbox actions passed.
- Matching dev.18 e4mc Host/Player packs: CRC, exact final JARs, official dependency pin, Survival/Normal world, client defaults and absence of player/private runtime files verified in `../reports/dev18-pack-validation.json`. All **178** source-review files and **12** older artifacts match their recorded hashes; the original brief SHA256 remains `df20a7c1a8ff73f81b9f7dc1265666b4131ce62b260b79904180ea32fca5a44a`.

Five earlier raw failed trials are retained: missing bot dependency, Fabric registry admission mismatch, mismatched content registries, the missing-group wrapper assumption, then the group-name truncation assumption. Assertions were corrected against the real pinned runtime; no registry/admission/privacy checks were disabled to obtain a pass.

## Review and limits

[Owner review guide](REVIEW_COUNCIL.md). Import matching fresh Host and Player packs; only Host opens the bundled world. Enable Voice room on the desired authored Regions. Microphone audibility/intelligibility and remote UDP delivery still require a real two-person test, including inside/outside both directions, adjacent rooms, rapid crossing/reconnect and an ordinary outside manual group. The e4mc game relay alone is **not a tested voice relay**; Simple Voice Chat needs its own working UDP connection.

This is isolated room-group audio, not physical wall/door sound propagation or low-pass muffling. A frame already authorized before a boundary crossing can arrive afterward; buffered/network audio cannot be recalled. No conversation recording, representative high-load benchmark, arbitrary disk/power-fault guarantee, real packet injection or human acceptance is claimed. Final palace-map/group rehearsal, other deferred multiplayer reviews and publication remain separate gates.

Council decisions already use ordinary binding motion/result records regardless of scope. House agreements remain separately signed and breakable; no separate realm ratification or mandatory treaty-amendment workflow was added.

## Delivery and retention

Implementation commit: `d833679`. Matching review packs are in `../client/Chronicle-Last-Council-0.2.0-dev.18-e4mc-{Host,Player}.mrpack`. The overall receipt is `../reports/dev18-validation.json`. No dedicated review server or plain pack was created. No main merge or Modrinth publication is included.

All eight owned disposable worlds were verified stopped and removed, freeing **1,559,650,666 bytes (about 1.45 GiB)**. Compact journal/config archives, raw failed and passed reports, server/client logs and useful screenshots remain. See `dev18-voice-cleanup.json` and `dev18-rebellion-cleanup.json` in reports. The source review, original brief, older packs and unrelated acoustics work remain untouched.
