# Bootstrap decisions — 18 October 2026

## Engineering milestones and implementation authorization — 19 October 2026

Follow-up answers confirm exposure-fed growth without oxygen depletion/suffocation. Grown shriekers support vanilla Warden summoning, vanilla warnings and vanilla despawning; do not add a custom warning/encounter system. M9's pinned-version trial must establish the correct generated-shrieker state and ownership/pause integration while preserving that vanilla behavior.

The operator removes the event outline and rejects assigning developments to sessions. Remove that outline and its default first-session timer; shared timers/entry/manual observations remain opt-in tools, never required reveals or ecology unlocks. `DEVELOPMENT_MILESTONES.md` specifies M6–M13 as engineering deliveries with Core/campaign ownership, dependencies, tests and concrete review gates. Existing M0–M5 retain their historical meaning. The operator now authorizes implementation after planning, continuing until a necessary question or hands-on review; the earlier blanket implementation hold is superseded. Unanswered ecology choices do not block independent Core authoring work.

## Autonomous ecology and Relic Heart ending — 19 October 2026

The operator accepts the Core map-authoring direction for now but rejects prepared sculk routes/patches and session-paced benefit reveals. Sculk must grow from the ancient city along actual caves/mine passages toward the surface or other ancient sites, consuming air/sunlight. Register only key locations; do not require painting sculk, connecting authored nodes or approving every expansion. A benefit operates when the ecology supports it, even if players only discover it several sessions later. This supersedes earlier prepared-route, bound-farm and five-session-rhythm proposals.

The Ancient Focus role is removed. Living growth supplies visible evidence through sculk, catalysts and sensors; extensive growth may produce shriekers. The Relic Heart becomes the unique network root: weaken the network sufficiently, then destroy the Heart to eradicate all of its sculk, including initial source growth and detached branches. Containment, coexistence and departure remain alternatives. No personal corruption or mandatory boss fight follows from this change.

`THE_QUIET_BELOW.md` proposes surface-frontier growth, runtime power/vitality, automatic source indexing, tracked descendant features and durable cleanup that preserves later player edits. Air/light feeding details, growth budgets/material protection, catalyst interactions, shrieker Warden policy, Heart vulnerability and source/cleanup ownership need a pinned-version technical specification. These proposals are not new vanilla claims or implemented behavior. M4's latest-operation 64-block journal is insufficient for an autonomous network or complete eradication. QB-08/QB-13 track that work; the accepted shared Core editor is not replaced or broadened into a terrain editor.

The entries below record earlier decisions; their authored-route/Focus assumptions are superseded by this entry and the current campaign outline. This revision edits documentation only.

## Concrete benefits and Core authoring revision — 19 October 2026

The operator rejected the proposed mine-stabilization benefit: no structural-physics model should be introduced to justify it. Benefits need usable Minecraft behavior and may be exclusive; an ordinary substitute is not mandatory. The revised proposal is bonus crop growth first, then local regeneration and powered Ancient Forge recipes as the rooted network expands. Costs also escalate through approved territorial change and temporary environmental hazards, never personal corruption. The supplied Focus/Altar/Forge/Relic assets are candidates for defined functions, not merely props; no new assets or mechanics were registered in this revision.

The operator also rejected a copy/import-first authoring workflow. Shared two-corner regions, points/block anchors, chest content profiles and spawn bindings belong in Chronicle Core and are configured in the current world. Quiet Below owns sculk power/effects/recipes and references Core IDs. Backups/rehearsal copies remain optional operational tools, not prerequisites to defining a site. This explicit reusable requirement supersedes the earlier suggestion to defer all authoring infrastructure to the campaign.

The supplied `VampireSMP-1.1.2.jar` was inspected as data/bytecode, not installed or run. Named beacon sites, chest locations, saved respawn positions and validation handlers inform the authoring UX; two-corner selection is the operator's WorldEdit-style requirement. Record exact evidence/limitations in `REFERENCE_VAMPIRE_SMP.md`; do not port faction rules, hard-coded map coordinates or force-loading behavior. `MAP_AUTHORING.md` and CF-05–CF-07 replace the prior world-import-first plan.

## Campaign formats — 19 October 2026

The operator and co-organizer identified four experience formats: Survival (group against an external threat), Journey (one contained world per session, with two or three end states influencing the next world), Werewolf (themed social deduction), and Happening (characters in a place responding to change). `CAMPAIGN_FORMATS.md` proposes Situation as the fourth name and classifies Quiet Below there. At the operator's invitation, Operation and Intrigue are additional candidates; they are not approved implementation work.

Use format for the overall experience and event/action for an individual triggerable incident. Quiet Below's refusal of compulsory factions/personal corruption does not prohibit hidden teams or elimination in a separate Werewolf scenario. Keep the existing two-module layout until concrete reuse justifies another campaign/service; do not build four mode engines now. Private Werewolf state and Journey outcome/world transitions are distinct future requirements that existing public dossiers/session flags do not already satisfy.

## Campaign design reset — 19 October 2026

The operator tested the prototype and reports that the functions generally work; the event/marker fiction feels disconnected. Stop expanding gameplay until its purpose is defined. The operator handcrafts the production map; agent worlds remain technical fixtures. The intended backdrop is an initially unknown ancient city, with connected sculk spread offering benefits and costs. Characters may remove, contain, study, coexist, depart or invent other responses. No required faction split, personal corruption or forced stay is part of the direction. Timed first-session cues and first-entry discovery are desired future tools, while manual GM controls remain.

`THE_QUIET_BELOW.md` proposes a causal event outline and explicitly separates established direction from unapproved cause/benefit/lore. `MAP_AUTHORING.md` identifies tools needed to adapt the mod to the operator's world without code edits. `BACKLOG.md` tracks QB work items and dependencies. Existing M3/M4 features prove technical capability; they are not proof of a coherent campaign or of proposed scheduling/region/multi-stage support.

The operator requested branches, change tracking and commits. Capture prior uncommitted work once as a tested-prototype baseline, then use scoped branches and coherent commits. Keep runtime data out of Git; publish requested GitHub work on branches and keep merging main a separate decision. Do not invent earlier milestone commits or hosted issue IDs.

## Scope

M0 established local operations; M1 adds sessions/persistence, flags/events/audit, simulation freezing and hotbar status; M2 adds public character cards; M3 adds investigative content; M4 adds campaign-local bounded stages. M5 has a separate prototype site and session material pending group playtest. The original brief is preserved with explicit operator policy updates. Core and campaign build separate jars; campaign has a hard Core dependency. Package prefix: `io.github.chronicle`. License/distribution remains open; no license was invented.

## Verified pins

| Component | Pin | Source |
|---|---|---|
| Minecraft | 1.21.10 | Mojang via Fabric/Loom |
| Java | Temurin 21, exact artifact/hash in lock | Adoptium API/release |
| Fabric Loader | 0.19.5 | Fabric metadata, stable |
| Fabric installer | 1.1.2 | Fabric Maven, SHA-256 |
| Yarn | 1.21.10+build.3 | Fabric metadata/Maven |
| Loom | 1.11.8 | Fabric Maven |
| Gradle | 8.14.3 | Official distribution, SHA-256 |
| Fabric API | 0.138.4+1.21.10 | Modrinth tV4Gc0Zo, SHA-512 |
| Simple Voice Chat | fabric-1.21.10-2.6.22 | Modrinth 9074luJy, SHA-512 |
| Mineflayer | 4.39.0 | npm; transitive versions/integrity in package-lock.json |

Loom is pinned to a Java 21/Gradle 8 generation instead of copying newer Minecraft docs. Common/client source sets enforce compilation boundaries; dedicated startup checks runtime boundaries. Campaign client run includes Core's source sets. Only the two mods apply Loom; `campaigns` is an aggregator.

Third-party mods execute code with JVM/operator permissions. This setup installs only Fabric API and Simple Voice Chat from their Modrinth projects. Java/Gradle/Fabric also execute vendor code. npm lifecycle scripts are disabled (`--ignore-scripts`); Mineflayer runtime/dependencies execute during tests. No third-party agent/MCP server, hosted AI service, account token extraction or global launcher install is needed.

## Operations

Runtime data stays in the parent directory. Setup is repeatable, hash-checked and preserves existing config. A maintainer changing pins must deliberately migrate installed Java/runtime/mods; setup is not a blind upgrade tool. Portable setup supports Windows x64; mod source is cross-platform. The user selected 1.21.10 on 18 October 2026, superseding the provisional 1.21.1 target. The old server was stopped, backed up, and archived before upgrading a world copy; an upgraded world must not be run on an older runtime. After validation, the user explicitly requested wiping all retired 1.21.1 installations. Its runtime, old test/restored worlds, packs, backups, version-specific caches, and superseded Gradle 8.12 tools were removed on 18 October 2026. The live 1.21.10 server, its world and backups, Java, shared assets/dependencies, and unrelated projects were retained. Cleanup evidence is in `../reports/cleanup-1.21.1.json`.

Play server: loopback, online authentication, whitelist, no RCON/query/PvP. Test server: fresh isolated world, loopback, offline identities, separate ports. Never expose the test fixture. Voice server initialization does not prove microphone/audio behavior.

Downloads use HTTPS, bounded retries/timeouts, expected hashes and allowed redirect hosts. Public Modrinth API v2 reads/downloads need no token. Lock refresh uses a descriptive User-Agent and refuses newly required mod dependencies for review. Normal setup uses the lock rather than "latest". Fabric installer/Loom resolve official libraries/game data themselves, so the top-level lock does not hash every transitive Gradle/game artifact. npm lock records test dependency integrity.

The wrapper was generated from verified Gradle. Its distribution URL HEAD probe and Java HTTPS redirect download failed here. Generation used `--no-validate-url`; setup/build seed the wrapper cache from setup's verified archive, and the wrapper still checks its SHA-256. Actual build/runtime results belong in TEST_PLAN.md.

The historical 1.21.1 Mineflayer packet workaround was removed for the 1.21.10 migration. `npm --prefix testing/minecraft-bot test` exercises strict parser behavior: truncated or unconsumed packets fail instead of being silently logged and dropped. Mineflayer 4.39.0 remains pinned; its bundled data aliases 1.21.10 to 1.21.9 protocol 773. Mojang's downloaded 1.21.10 `server.jar/version.json` confirms protocol 773 and Java 21, and the strict live test passed against a server advertising exactly 1.21.10. No custom packet overrides are used. Reassess the alias when updating the test dependency; future custom mod registries or packets can require a real Fabric test client.

Setup records the installed runtime in `.chronicle-runtime.json` and refuses to reuse a different Minecraft version or recorded Loader version. Start/deploy/verification check the runtime; deploy/pack additionally reject Chronicle jars built for another Minecraft target. Setup/start/deploy/verification reject differently named versions of managed Fabric API or voice-chat jars instead of loading duplicates or deleting files. Unrelated mod files are preserved. Installer reruns when the launcher, server jar, or locked Loader jar is missing.

## Sources checked

- [Fabric Loom source sets and multi-project dependencies](https://docs.fabricmc.net/develop/loom/)
- [Fabric's 1.21.9/1.21.10 toolchain guidance](https://fabricmc.net/2025/09/23/1219.html)
- [Fabric server installer](https://fabricmc.net/use/server/)
- [Fabric loader metadata](https://meta.fabricmc.net/v2/versions/loader/1.21.10)
- [Yarn metadata](https://meta.fabricmc.net/v2/versions/yarn/1.21.10)
- [Modrinth API](https://docs.modrinth.com/api/)
- [Fabric API release](https://modrinth.com/mod/fabric-api/version/tV4Gc0Zo)
- [Simple Voice Chat release](https://modrinth.com/plugin/simple-voice-chat/version/9074luJy)
- [Mineflayer](https://github.com/PrismarineJS/mineflayer)
- [Adoptium API](https://api.adoptium.net/)

## Next slice

M1 uses immutable pure-Java state behind server-thread commands. State is persisted atomically before publishing successful mutations, with a previous-file backup and explicit schema/bounds validation. Invalid data disables campaign commands without resetting the file. All administrative commands require operator level 2; status remains public. Event handlers return planned flag updates only, and cannot perform M1 world transformations. The harmless Quiet Below readiness event exercises registration and once-only completion. See [M1 specification](M1_IMPLEMENTATION.md).

The smoke harness stages new build artifacts before deployment, gives GM privileges only to its disposable offline test account, and verifies persistence across two dedicated-server launches. Deploy archives previous Chronicle jars before replacement and preserves unrelated mods. The user confirmed a successful authenticated 1.21.10 connection; microphone/audio remains a manual check.

On 19 October 2026 the operator changed the M1 policy: native world simulation freezes whenever campaign state is not ACTIVE, while player movement, chat, interactions and dossier editing remain available. Server tick-manager freezing avoids changing gamerules or entity attributes. The session owns the frozen setting, including reapplication after restart; tick rate is preserved. A periodic/join/transition actionbar shows only session number, phase and frozen status. Invalid/unavailable campaign state freezes simulation without resetting the saved file.

M2 supplies one public character dossier per UUID, bounded plain text, owner-only changes and optimistic revisions. JSON is stored separately per UUID with strict schema/owner validation, atomic replacement and prior-file backup. Save packets omit target UUID and derive identity from the connection; public requests are restricted to online targets and replies go only to the requester. Request/save throttles bound server-thread disk work. A standard Fabric screen and K keybind provide editing/read-only views; commands provide a text fallback. English is the first supported UI locale. The native Screen background lifecycle and explicit ARGB text colors follow the pinned 1.21.10 APIs rather than older examples. See [M2 implementation and checks](M2_IMPLEMENTATION.md).

M3 reuses the operator's ancient-symbol Blockbench geometry/texture for the Resonance Marker, with the original source folder untouched and provenance hashes recorded in M3_IMPLEMENTATION.md. The Echo Lens uses a small reproducible original texture. Modern item definitions, recipes, loot and pickaxe tags follow the pinned game. Clues use server-chosen chat text so they wrap and do not displace session status. No reference assets or invented distribution license were imported.

Custom registries made the strict Mineflayer fixture incompatible with M3. Default smoke now uses matching Fabric development clients with explicit opt-in state machines/results and fixed loopback guards. Pinned Loom RunConfig exports launch arguments, then independent Java children avoid Gradle runClient lock contention. No packet skipping or invented registry IDs were used. Historical Node/parser checks remain separate. Licensed accounts, microphones and group roleplay remain human checks.

M4 remains campaign-local: exact GM-approved loaded templates, passive sculk only, 64-position/8-per-tick bounds, explicit previews/tokens, strict atomic journals and latest-operation undo. Stored operation dimension/entries must exactly match their zone capture. Restart interrupts jobs; explicit resume reconciles all before/after states from the start, while third-state edits halt work. Partial apply can be safely undone. The mine_whisper event only changes a flag/public clue; Core pure event handlers acquire no world-writing API. No generic campaign editor or automatic spread was added.

M5 uses fresh isolated authenticated pilot roots on TCP25585/UDP24465, copied invitation/GM permissions, a separate manual one-shot datapack and normal client chunk loading for construction. A real-client check validates native sign text, a survival stair walk and rendered screenshots in a disposable copy. Only verified site data can be promoted into a virgin pilot; test player identities/cards are excluded. The ordinary play world remains separate. The build is a prototype blockout; the operator/group must judge navigation, clue feel and voice audio before M5 is complete. No AI game master or hosted agent service is required.
