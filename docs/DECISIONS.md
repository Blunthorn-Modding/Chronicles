# Bootstrap decisions — 18 October 2026

## Visible sculk vitality - 20 October 2026

The operator requests readable world feedback using the original texture patterns with dull blue, faded blue/brown and fully brown variants beside healthy sculk. [Dev.19 contract](M9_VITALITY_VISUALS.md) uses a four-value property on the five native sculk block types, preserving geometry, native properties and existing block entities. No new entities/block entities or per-block ticking renderer. Color is a bounded projection of durable vitality/connectivity/exposure, excluded from logical ownership/journal encoding. Old worlds/journals default the omitted cosmetic property to healthy, then reconcile during eligible play. Unknown evidence retains the last appearance. Default bands are healthy feeding roots at 75%+, dull at 25%+ or freshly dormant, faded below 25%, brown at zero; recovery reverses them. These are review choices, not settled balance.

Adding native states changes numeric network palettes, so both peers acknowledge the campaign appearance layout during configuration, before PLAY. The dev.19 review requires its matching client; keep the older profile for unchanged production dev.14. Pinned 1.21.10 disk serialization repacks runtime palettes into local semantic entries, so it does not persist global raw state IDs. Rollback still restores the stopped world and jars together. Assets are deterministic palette derivatives of pinned native pixels; regeneration/provenance is in `testing/assets/README.md`.

## Dormant vitality and recovery - 20 October 2026

The operator accepts organic spread and authorizes continued implementation. On the explicit decay choice, **lose vitality but keep dormant blocks** is confirmed. Physical disconnection stops growth/native responses immediately; the new [M9b slice](M9_VITALITY.md) retains every block and permits recovery from zero after reconnection/exposure. No terrain withering, catalyst upkeep, oxygen system or permanent-death deadline is introduced. Rates, extent thresholds and the fixed 25% ready threshold are technical review defaults. Loss/recovery rates and paired extent thresholds are configurable per world through the existing generic Game setup UI. Core remains free of campaign ecology state.

## Branching growth and initial client preferences - 20 October 2026

The operator accepts corner traversal and speed controls, reporting that the new traversal paints straight rows. [Organic-growth repair](M9_ORGANIC_SPREAD.md) varies frontier and direction choices inside the existing work bounds; physical connection, sealed barriers and recorded ownership stay authoritative. Existing terrain is retained. Future client packs carry GUI scale 2, narrator off, accessibility onboarding off and multiplayer warning acknowledged as initial options. These are profile defaults, not enforced server/mod settings.

## Exposed-corner review and adjustable spread speed - 20 October 2026

The operator accepts identification and root disconnection/reconnection, reporting one remaining exposed-edge stall and requesting an in-game pace setting. [QB-08 surface/speed repair](M9_SURFACE_SPEED.md) preserves physical connections and sealed interiors while discovering locally reachable exposed sides of grown/existing blocks. Pace belongs to Quiet Below, using Core's existing generic tool UI: Slower / Faster / Reset speed, 0.25x to 16x, server-saved per world, default 1x. Values describe bounded growth-attempt intervals rather than guaranteed block throughput. This is a review repair and partial configuration delivery, not completion of M9 vitality/decline or later campaign mechanics.

## One growth system and explicit ownership maintenance - 20 October 2026

The operator confirms ecology-only campaign growth: no death/XP fuel or native catalyst spread. Suppress native catalyst intake/ticks in the configured campaign dimension, including catalysts awaiting ownership/cleanup. Add explicit adoption of existing sculk through a saved region, with disconnected patches remaining dormant. The operator chooses optional persistent world-wide cleanup of non-campaign sculk as chunks normally load. Current ownership is pinned to the Heart/source dimension, so that is the cleanup scope; other dimensions are unchanged. Cleanup is off by default, follows occupied ACTIVE play, and requires a separate confirmed action after adopting regions to preserve. Core supplies saved regions/assignment and generic controls; the campaign owns scanning, ownership and deletion. [Contract](M9_ROOT_RECONNECTION.md).

## Physical sculk reconnection - 20 October 2026

The operator confirms that placed sculk joins the network when physically connected to the Heart or living sculk. A complete cut makes a detached branch dormant; restoring continuous contact wakes it. Retain one Heart and surface connectivity, without catalyst ranges, expansion nodes or node-regeneration chains. This supersedes dev.14's ownership exclusion for replaced sculk. Provide an operator root locator and explicit growth/connection status. See [review repair](M9_ROOT_RECONNECTION.md).

## Ecology protection policy - 20 October 2026

The operator answered **"Nothing is protected."** Natural terrain, the handcrafted map and new player placements use the same material/surface rules. Do not add building immunity, placement tracking or protected-region requirements. Exposed native substrate can become sculk; other solid materials can support veins. An actual closed passage blocks new air-frontier discovery; an already living branch must also be physically severed. This confirms protection policy, not balance/vitality thresholds. [M9 implementation](M9_IMPLEMENTATION.md) defines the first review slice and remaining M9 work.

## Hidden factions and actionable authoring mockup — 20 October 2026

The operator clarifies that Werewolf is the family of hidden-faction games, including Vampire SMP, not an obligatory tabletop round/vote structure. Preserve private membership/knowledge/actions as the foundation; voting, phases, elimination and win rules are separate opt-in tools. [Campaign formats](CAMPAIGN_FORMATS.md), CF-03 and the UI future-reference copy now reflect this.

Further CF-10 feedback adds searchable **Add item** with independent chance and count range, individual entry editing/removal and whole-table save; separate **Points / Block sites** selectors with kind-specific descriptions; and an active **Continue in world** transition that resumes this region draft, requests its private outline and closes after server acknowledgement. Keep unsaved corner edits and show existing bounds immediately; new drafts gain the box as positions are selected. Mockup simulation is not a real registry, Minecraft render or deployed command change.

## Core menu review corrections — 20 October 2026

The operator requests updates to [CF-10](CORE_UI_REDESIGN.md), still as a mockup/design change. Rename Arrival to **Starting position**; split Advanced **Chests** (physical bindings) from **Loot Tables** (reusable distributions/refill settings). Region creation instructs two standing-position commands, `/chronicles author region pos1` and `pos2`, then saves in the UI. Existing-region editing loads both saved positions and can replace either alone, preserving ID and reviewing references. No looked-at region selection.

Actual mod blocks own their station interactions and state. Placing a supporting block in operator authoring may prompt to register/link that instance; station references are optional unless a specific campaign genuinely requires a unique link (Heart). Keep manual looked-block binding for vanilla/other sites. These blocks/hooks are future work, not current deployed functionality. Quiet Below's source/Heart/station/network/benefit tools belong to its setup; Werewolf/Journey/encounter features remain explicitly separate future references.

Sessions are windows of active play, not narrative chapter/reset boundaries except when a future format explicitly chooses that model. Remove session preview/recovery from the proposed normal UI/commands. Technical recovery remains an interrupted-operation concern in existing service-specific tools. Event text and optional private sound playback are one Preview workflow, with no separate Sample command. Keep live audience/readiness checks and confirmed execution distinct. Preserve current services, persisted IDs and legacy commands; no implementation/deployment is requested by this review update.

## Full Core menu redesign requested - 19 October 2026

After M8 testing, the operator rejects the accumulated menu as disconnected and asks for a design pass/mockup and clearer commands before more implementation. Left tabs should run vertically: Session, Players, Events, then Advanced Chests, Regions and Locations (old “Roles” is unclear). [CF-10](CORE_UI_REDESIGN.md) proposes contextual lists/actions and a distinct Game setup assignment page, consolidates movement settings under Players, unifies event presentation through typed adapters, separates content preview from live execution and covers future library tools. Game setup and the new preview/menu/command facade are proposals, not deployed capabilities. Code tracing confirms author mode is server-owned/transient with up to two-second display lag, not a proven client-side persistence defect. Human review of this design replaces the previous “continue to M9 after M8” UI checkpoint.

## Shared participation and optional observations - 19 October 2026

The operator accepts the automatic player-policy slice and asks to continue until hands-on review is needed. M8 follows the existing Core/campaign boundary: explicit membership, an ACTIVE occupied/unheld tick clock, finite opt-in conditions, private audience delivery and durable shared manual/automatic claims belong in Core. Quiet Below registers cue definitions; registration creates no bindings, scheduled story or ecology effects. First eligible presence includes a participant already inside an armed region. Clock-only updates, including durable clock checkpoints, preserve UI controls and typed input. Exact binding/definition/session/audience preview checks and uncertain CLAIMED history prevent automatic retries. `/chronicle leave` removes moving policies first, then participation; either persistence failure is reported. See [M8 implementation and review workflow](M8_IMPLEMENTATION.md). This is an implementation choice grounded in the existing design, not new canonical lore or settlement of the later ecological rules.

## Automatic player spawn policies and accepted Core screen - 19 October 2026

The operator accepts the Core screen, reports minor periodic flicker and asks for its fix bundled with the next feature review. Background refreshes must preserve unchanged widgets and hover/focus. Continue M7 with explicit per-player first-arrival/death-respawn policies over pinned saved purposes, default off, without changing bed/anchor settings or trapping departing characters. First arrival records a durable claim before moving and never automatically retries uncertainty; explicit reset requires non-active author mode. Players may leave their own policies. Entity encounter groups and shared M8 conditions remain separate. [M7_AUTOMATIC_SPAWNS](M7_AUTOMATIC_SPAWNS.md) specifies behavior and remaining human checks.

## Core authoring UI feedback - 19 October 2026

The operator reports the growing chat interface is hard to read and requests a proper UI for library/generic tools, including selecting chests and their types. Record this as CF-09, the next authoring priority, with commands retained as an advanced fallback. Reuse Core's registered catalogs/services rather than introducing campaign-specific screens for generic tools. Show author-mode/session state and actionable prerequisite feedback. The reported chest-edit rejection was resolved by enabling per-connection author mode; this was a discoverability problem, not authorization to remove the non-active authoring requirement. No runtime change or server restart is needed for this documentation update.

Implementation follow-up: CF-09 adds one Core client screen for chests, places, registered roles, manual spawn controls and session actions, backed by finite server-validated actions and private bounded snapshots. O and the bare author/chest/spawn commands open the screen; explicit chat menus and advanced commands remain available. Editing still requires non-active author mode. Geometry and content persistence are unchanged. Loot distributions remain server JSON, custom site IDs remain advanced commands, and entity groups/automatic respawn remain future work. Human review concerns readability and practical screen flow before authoring expansion.

## Typed loot chest revision - 19 October 2026

Operator confirms the persistent outline/chat-role menu and rejects captured templates/separate anchor assignment as the main chest workflow. Core must bind a targeted chest and its registered type in one step. Campaigns register default type distributions and recommended minimum counts; editable server JSON can override/add types. The operator confirms independent per-entry percentages, filling at each new session plus an optional interval every X minutes, and replacing ALL contents at a scheduled fill. These are managed loot containers; legacy captured data/recovery stays compatible, without being a prerequisite. M7_LOOT_TYPES.md replaces the previous chest UX contract. The Vampire SMP artifact was reinspected for its location list/distribution split, not installed or copied.

## M6 feedback and M7 first slice - 19 October 2026

The operator tested M6 and accepted selection geometry, requesting an outline that remains visible while walking and submod-registered IDs selectable from a menu. Replace the five-second timeout with explicit clearing; private previews remain dimension/connection scoped and author previews clear on ACTIVE. Core exposes a typed startup catalog; each campaign registers its own location roles. The first interface is clickable chat plus completion, with Set/Replace commands suggested for explicit submission. Legacy flat IDs stay readable; namespaced command strings are quoted. The preview channel is versioned so an older client receives the matching-pack notice instead of silently retaining the old timeout.

Continue CF-07 with Core-owned empty-only chest templates/fill receipts and manual named-player spawn moves. Preserve item components, reject unsupported/lazy containers, pin references and journal before mutation. Hold native simulation immediately during uncertain fills, flush saves before receipts, compare before/after for recovery, and preserve conflicts. Normal hopper/piston/explosion ticking stops during a hold; deliberate operator/plugin world edits remain an out-of-band bypass and can require stopped manual conflict resolution. Spawn metadata does not change vanilla respawns. Entity groups and automatic arrival/death-respawn are explicit M7 follow-up, before treating CF-07 as complete. Human review of the content/move workflow is the next gate; this does not enable sculk ecology.

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
