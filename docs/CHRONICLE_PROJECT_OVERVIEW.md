# Chronicle

> Current campaign direction: [The Quiet Below](THE_QUIET_BELOW.md), [handcrafted-map authoring](MAP_AUTHORING.md) and [tracked work](BACKLOG.md), updated after operator feedback on 19 October 2026. This original brief remains historical context. The operator now handcrafts the production map; prototype marker/lens fiction and the generated pilot do not define the campaign. New narrative/gameplay implementation waits for design review.
## Minecraft Roleplay Campaign Framework — Project Overview & Codex Handoff

**Status:** Proposed direction / implementation starting point  
**Document version:** 0.1 — 18 October 2026  
**First campaign (working title):** *The Quiet Below*  
**Platform:** Minecraft Java Edition, Fabric  
**Primary audience:** A small, invited group playing scheduled roleplay sessions

> **One-sentence vision:** Chronicle enables groups to tell collaborative, character-driven stories inside Minecraft, using modest custom mechanics and an evolving world to create roleplay opportunities—not quests, NPCs, or PvP objectives.

---

## 1. Project vision

Chronicle is a long-term project to support **finite, session-based Minecraft roleplay campaigns** with a central premise, a developing narrative, and outcomes shaped by players. It should feel closer to a tabletop roleplaying campaign taking place in a shared Minecraft world than to an MMORPG or competitive SMP.

Each campaign gives every player a reason to engage with the same overarching situation, while allowing characters to pursue their own goals, relationships, beliefs, and conflicts. The setting presents problems and opportunities; the **players decide what their characters do**. Story events should create conversations, investigations, trade-offs, arguments, cooperation, and consequences.

The eventual product is not one universal, all-powerful mod. It is a **small reusable library mod** accompanied by **individual campaign mods** with their own content, mechanics, assets, and narrative premises. The library grows only when repeated campaigns reveal genuinely reusable functionality.

### Success looks like

- Players remember **character choices and shared scenes**, not only objectives they completed.
- Every character has meaningful routes into the main story without having to fight or possess a unique “chosen one” ability.
- Sessions provide structure, while unscripted interactions remain the main attraction.
- The world visibly responds to decisions, without the GM having to execute dozens of manual commands.
- A second campaign can reuse meaningful infrastructure without inheriting the first campaign's setting.

## 2. Creative principles and explicit non-goals

| Principle | What it means in practice |
|---|---|
| **Roleplay first** | Mechanics create situations that prompt interaction rather than replacing interaction. |
| **Shared overarching narrative** | A common central conflict develops across sessions; there is no mandatory single protagonist. |
| **Player agency** | Events can be prepared, but solutions, alliances, and endings are not predetermined. |
| **PvP is optional** | Combat can occur naturally, but no plot objective, progression gate, or victory condition should require PvP skill. |
| **No NPC-driven questing** | Narrative information comes from the environment, player conversations, books, artifacts, discoveries, and triggered events. |
| **Vanilla-friendly** | The world should still feel like Minecraft. New mechanics are restrained, thematically meaningful, and easy to understand. |
| **Session-based** | Major narrative changes can be controlled during scheduled play; ordinary building/social time remains possible outside sessions. |
| **Player inclusion** | Different characters can contribute through conversation, investigation, exploration, creativity, diplomacy, or practical work. |

**Not goals for the first trial:** MMO classes, leveling, grind, quest boards, scripted NPC dialogue, mandatory boss fights, competitive elimination, a sophisticated branching-story editor, an AI game master, or an exhaustive player relationship simulator.

**No secret-identity requirement:** Private motivations can exist, but secret roles, betrayal, and deception are not structural prerequisites.

## 3. Software structure and responsibility boundaries

### 3.1 Chronicle Core — reusable library mod

**Purpose:** Support multiple campaigns with a small set of reliable services.

For the first trial, it owns:

1. **Session lifecycle:** Start, pause, resume, end, and inspect the current numbered session; block automated campaign progression while inactive or paused.
2. **Persistent campaign state:** Save story flags, event completion, session state, and permitted player profile data across restarts.
3. **Event registration and invocation:** Campaign mods register named, validated actions; authorized GMs can list and trigger them. Keep the first API narrow.
4. **Permissions and auditability:** Operator/GM access checks; event history showing who triggered what, when, and with which parameters.
5. **Character dossier:** One lightweight, reusable client GUI for displaying and editing a character's public roleplay profile. Other players can view the public summary.
6. **Client/server synchronization:** Server-authoritative profile edits, event notifications, and other UI data; clean separation of client-only rendering code.

**The core should not contain sculk, the pilot's lore, a fixed calendar of events, campaign-specific item models, or a generic visual scripting engine.**

### 3.2 The Quiet Below — first campaign mod

**Purpose:** Deliver a complete, playable first story using Core.

It owns:

- The settlement/mine premise, event catalog, stage progression, world-region settings, and environmental clues.
- A **controlled sculk/corruption mechanic**, initially specific to this campaign.
- A small number of **custom blocks, items, textures, sounds/particle cues**, and scenario-specific interactions.
- GM-facing campaign commands/configuration, and written material explaining each session's narrative pressure (not dictating player responses).

If a second campaign needs bounded world transformation, the appropriate part of that system can later move into Core as a general-purpose API. **Do not generalize the sculk implementation prematurely.**

### 3.3 Distribution and dependencies

The technical target is **Minecraft Java 1.21.10, Fabric, Java 21**. The user selected this version on 18 October 2026, superseding the brief's provisional 1.21.1 target without changing the gameplay scope. Pin a mutually compatible Fabric Loader, Fabric API, Loom, and mappings set rather than copying versions blindly from documentation for newer Minecraft releases.

Because the pilot includes **new block/item assets and custom client screens**, assume **both server and players install Core and the campaign mod**. Fabric API is required as appropriate. Existing **Simple Voice Chat** remains a separate, independently installed mod; its APIs are not required by Chronicle 0.1.

A single Gradle multi-project repository is the preferred starting point. Use separate client and common/server source sets where supported by the selected Loom version, so dedicated-server launch never loads client rendering classes.

## 4. First playable campaign: The Quiet Below

### Premise

A small settlement stands near an abandoned mine. Strange growth begins appearing beneath the valley. Sounds emerge from empty passages; familiar places change. Nobody knows whether the phenomenon is dangerous, valuable, alive, or misunderstood.

Characters may want to protect the settlement, investigate the mystery, preserve the mine, profit from it, escape, or negotiate a compromise. None of those approaches should be labeled the “correct” way to play.

### Campaign format

- **Suggested trial:** Session 0 for setup/characters, followed by **five main sessions**. These are planning beats, not hard-coded timers.
- **GM approach:** Start with one GM (who may also roleplay a character) controlling the pace of events. Allow a hybrid approach later. Fully GM-less operation is a future experiment, not an MVP dependency.
- **Setting:** A compact, prepared Minecraft valley/settlement with an abandoned mine and a few designated locations. A handcrafted map is optional; the first trial should work with a prepared vanilla-style world.
- **Conflict:** The spread and its meaning, the town's response, and the relationships and disagreements arising from it.
- **No required combat:** The Warden, if present at all, is environmental atmosphere or an avoidable hazard—not an intended final boss.

### Suggested five-session arc (flexible)

| Session | Narrative pressure | Example world event |
|---|---|---|
| **1 — Settlement** | Establish characters and community priorities. | First unusual trace found near an old tunnel. |
| **2 — Signs** | Multiple explanations emerge; interests diverge. | New traces, sounds, or fragments appear in a bounded area. |
| **3 — Consequences** | The community's earlier decisions become visible. | A selected corruption zone changes, or a clue is revealed. |
| **4 — The Discovery** | A major revelation raises the stakes. | Access to a new location or artifact becomes possible. |
| **5 — Resolution** | The group must live with its decisions. | The ending changes the world in a way that reflects player choices. |

Possible broad outcomes include **containment**, **coexistence/study**, **abandonment**, or an entirely player-invented solution. These are GM preparation categories, **not menu choices or enforced endings**. Any event may be moved, skipped, or adapted in response to play.

### Ensuring everyone can participate

Important information should have **multiple entry points**: e.g., an item can reveal a trace, an environmental change can be observed by anyone, and a written clue can prompt debate. Avoid relying on one player's presence, a combat specialty, or a single hidden character backstory to advance the main plot.

## 5. Content and mechanics for version 0.1

**Custom content is included in the trial.** The constraint is to make a few polished examples rather than build a large content pack.

| Feature | Version 0.1 acceptance scope | Module |
|---|---|---|
| Session controls | Start / pause / resume / end / status, persisted across restart | Core |
| Campaign flags | Named flags and event-completion records persisted per world | Core |
| Event system | Register, list, preview where practical, and trigger named campaign events with permission checks and logs | Core + campaign |
| Character dossier GUI | Public in-character name, optional pronouns, short public description, edited by owner and viewable by others | Core (client UI + server data) |
| Custom item | **Echo Lens** (working name), an investigative item providing a short clue/feedback when used on an appropriate story object | Campaign |
| Custom block | **Resonance Marker** (working name), a textured, placeable clue-bearing block with a simple interaction/state | Campaign |
| Original assets | Textures/models, item/block names, localization, simple particles/sounds as needed | Campaign |
| Sculk event stages | GM-controlled, **bounded** visual changes to designated zones, with explicit allowed block changes and rate limiting | Campaign |
| Story record | Basic chronological GM-event log; optionally visible in the character GUI as a small shared “Chronicle” tab if inexpensive | Core |
| World safety | Defaults that preserve builds, containers, and non-target regions; backups and a tested way to undo a scripted change | Campaign + documentation |

The **Echo Lens** and **Resonance Marker** are prototypes, not mandatory final fiction. Their purpose is to validate the full pipeline: registering content, rendering textures, synchronizing interactions, and delivering a small piece of roleplay-relevant information. They should create questions and discoveries rather than combat bonuses.

### Character dossier: intentionally modest

The dossier is a **public-facing character card**, not a replacement for a detailed external character sheet. It shows who a character appears to be and provides conversational hooks. Players retain full control of their private motivations and backstories outside the mod.

- Editable fields: display name, optional pronouns, and a short description (bounded length).
- Viewing: self and other players' **public** fields; no GM-only secrets transmitted to ordinary clients.
- Open access: a keybind or command; if a keybind is used, provide a command fallback.
- Server validation: authorize editing, sanitize/limit text, persist by player UUID, and send changes only through defined packets.
- Accessibility: readable layout, keyboard navigation, and screen-size resilience.

An elaborate relationship map, avatar editor, private-message system, and secret inventory are explicitly deferred.

### Controlled corruption/sculk: safe by design

The first system is **staged environmental change**, not uncontrolled simulation. A GM prepares bounded zones and triggers stages. Within an active zone, the mod can place sculk/decorative markers on an explicit allowlist of safe positions or safe natural blocks.

Required safeguards:

1. Never modify positions outside configured zones; default to no zones and no automatic growth.
2. Do not replace inventories, block entities, bedrock, structures, or player-created builds. Because “player-created” cannot always be inferred reliably, prefer **pre-approved coordinates/placement templates** in the first iteration.
3. Rate-limit placement work on the server tick and do not force-load large areas or generate new chunks.
4. Provide a **dry-run/preview report** listing the affected positions/count before a GM triggers a transformative stage, where feasible.
5. Log changed positions and previous states for a basic **undo** of scripted stages, and instruct GMs to back up the world before sessions.
6. Pause/stop prevents further *automated* campaign mutations; vanilla Minecraft itself continues normally.

If a requested growth algorithm conflicts with these guarantees, choose a smaller scripted-placement approach for version 0.1.

## 6. Typical play and GM workflow

**Before the season:** Install Fabric mods, distribute the identical compatible mod set to players, prepare the world and locations, configure stage regions, and make a backup. Players create their detailed characters and fill out their short in-game dossiers.

**Before a session:** GM checks the event list and event prerequisites, decides which developments might be appropriate, and confirms a backup exists.

**During a session:** GM starts or resumes the session. Characters interact freely. When dramatically appropriate, GM triggers a story event; the mod updates the world and persists the event. Players choose how to respond. The GM does not need to move every player along a prewritten questline.

**After a session:** GM pauses or ends the session, reviews the event log, notes character decisions for the next session, and backs up the world.

**Between sessions:** Per the operator's update on 19 October 2026, world simulation freezes whenever no session is active (including paused/ended). Players remain mobile and can chat, use commands and edit character cards. Use vanilla tick-freeze semantics; this is not build protection. Display the current session number/phase above the hotbar, including the frozen status. The server stays online.

### Proposed GM command surface (names are provisional)

```text
/chronicle session status
/chronicle session start [number]
/chronicle session pause
/chronicle session resume
/chronicle session end
/chronicle event list
/chronicle event preview <event-id>
/chronicle event trigger <event-id>
/chronicle event history
/chronicle profile open [player]
```

Only authorized operators/GMs can modify sessions or trigger events. Ordinary players can access permitted profile views/edits. **Commands are acceptable for GM tooling in the pilot; a polished GM control GUI is a later enhancement**, distinct from the player-facing dossier that *is* in scope.

## 7. Technical design guidance for Codex

These are implementation preferences, not a demand for a large framework on day one.

- **Repository:** Gradle multi-project with `core/`, `campaigns/quiet-below/`, and `docs/`. Build two independent distributable mod jars; the campaign declares a hard dependency on Core.
- **Language/runtime:** Java 21 for Minecraft 1.21.10. Use stable, compatible Fabric Loader, Fabric API, Loom, and mappings versions pinned in build configuration. Keep version-dependent code in appropriate modules.
- **Mod IDs:** Suggested `chronicle_core` and `quiet_below`; use proper namespaces for registries and networking identifiers. Exact Java package prefix can be selected when initializing the repository.
- **Separation of concerns:** Common/server code for events, persistence, registry data, permission checks, and gameplay; client source sets for custom screens, rendering, and keybinds.
- **Persistence:** Server-owned world data with a versioned schema. UUID-keyed player profiles. Save session state, event history, stage progress, and rollback information needed for scripted changes. Handle restarts gracefully.
- **Packets:** Only send data the client is authorized to see; validate and constrain all client-to-server requests. The server makes final decisions about state changes.
- **Event API:** Start with event IDs, simple metadata (description, prerequisites, once-only flag), and a handler. Campaigns register implementations. Prefer straightforward Java handlers plus configuration over a visual scripting editor.
- **Configuration:** Region coordinates, stage definitions, enabled events, safety limits, and optional text should be externally configurable/data-driven where practical. Do not implement a universal scripting language in the pilot.
- **Compatibility:** Verify standalone dedicated server startup and simultaneous use with Simple Voice Chat. No hard dependency on voice-chat APIs.
- **Documentation:** Provide build instructions, client/server installation, commands, example configuration, safe test instructions, and clear backup/restore procedure.

### Illustrative repository shape

```text
chronicle/
├── README.md
├── settings.gradle
├── build.gradle
├── gradle.properties
├── core/
│   ├── build.gradle
│   └── src/                         # common + client sources/resources
├── campaigns/
│   └── quiet-below/
│       ├── build.gradle
│       └── src/                     # gameplay, assets, campaign UI if needed
└── docs/
    ├── PROJECT_OVERVIEW.md
    ├── DECISIONS.md
    ├── CAMPAIGN_01.md
    └── TEST_PLAN.md
```

Use official **documentation for the pinned Minecraft version**. Fabric's generic docs may refer to substantially newer versions whose mappings and rendering APIs differ.

## 8. Implementation sequence (vertical slices)

| Milestone | Goal | Exit condition |
|---|---|---|
| **M0 — Bootstrap** | Establish the multi-module Fabric workspace and module dependency. | Both jars build; a dedicated server starts with both installed; a client connects. |
| **M1 — Sessions + persistence** | Core state, commands, event registration, permission checks, simulation freeze and hotbar status. | State survives restart; events are auditable; simulation freezes outside ACTIVE while players remain mobile; the hotbar displays current status. |
| **M2 — Character GUI** | First full client/server UI loop. | A player edits a public card; another can view it; all changes remain after restart. |
| **M3 — Content pipeline** | First custom block, item, textures, registration, and interaction. | Assets render correctly on a client; interactions are authoritative and usable. |
| **M4 — Campaign event** | One safe corruption stage plus a clue event. | GM can preview/trigger it inside a zone, see results, and undo the changes. |
| **M5 — First playable session** | Prepared valley, event setup, roleplay instructions, and test run. | The group can finish a session without manual code changes or an accidental PvP objective. |

**Rule:** Complete and test one vertical slice before broadening the engine. Do not build M4, M5, and a future campaign editor in the same initial coding task.

## 9. First-trial acceptance criteria

Treat version 0.1 as playable only when all of the following are true:

- [ ] Fabric server and matching clients start reliably with Core + The Quiet Below and the existing voice-chat setup.
- [ ] Two people can join at once; no client-only class is loaded on the dedicated server.
- [ ] GM session commands work and reject unauthorized use.
- [ ] Session number/state and completed events persist after stop/restart.
- [ ] Player character cards are editable, publicly viewable, permission-checked, and persistent.
- [ ] One custom textured block and one custom textured item appear and work in survival/creative testing.
- [ ] One event produces a player-visible clue that naturally invites discussion rather than requiring combat.
- [ ] One sculk/corruption stage changes only approved positions and supports a tested recovery/undo process.
- [ ] Nothing progresses automatically while sessions are paused or inactive.
- [ ] The GM can configure a small pilot region and run the first session using written setup steps.
- [ ] No storyline path depends on defeating another player, an NPC, or a Warden.

## 10. Risks and scope management

**Complexity of custom rendering:** Assets and GUIs mean clients must install the right mods. Keep the first assets simple (e.g., 16×16 textures, vanilla-like block models, a standard Screen-based GUI). Avoid custom animated models until the pipeline is proven.

**World damage:** Corruption must never become an indiscriminate griefing mechanic. Begin with approved positions, bounded stages, backups, and rollback rather than “smart” automatic replacement of arbitrary blocks.

**Over-engineering the library:** Resist designing a general-purpose event language or dozens of extension points. Build one real campaign first; extract reusable code only after a second use case demonstrates the boundary.

**Narrative bottlenecks:** No single player, ability, clue, or combat challenge should be required for the plot to proceed. GM prep provides multiple approaches and fallbacks.

**Too much work for the GM:** Favor simple commands, predefined stage packs, logs, and dependable state. Each mechanic must measurably reduce prep or create stronger scenes.

**Scope growth:** New campaign mechanics are allowed, but any addition should answer: *What player interaction does it enable, and is it worth the maintenance cost?*

## 11. Long-term direction (not MVP commitments)

After a successful first trial, potentially add:

- Reusable bounded environmental transformation and reversible world-event helpers.
- More polished GM event-control GUI, staging tools, scene controls, or timeline scheduling.
- Configurable campaign packs, scenario data schemas, and authoring utilities.
- Optional GM-less/hybrid event direction based on world state and group decisions.
- Campaign-specific magic, ailments/corruption, puzzles, custom blocks/items, and advanced visuals.
- Stronger roleplay tools (shared journals, letters, rumors, scene markers) if playtests demonstrate value.
- A second, **very different** campaign (e.g., civic/political drama or a journey) to prove Core does not depend on supernatural content.

The end goal is **a small library of reliable narrative mechanics plus distinct, creative campaign mods**—not a single mod that tries to write the story for the players.

## 12. Decisions and open questions

### Working decisions

- Fabric rather than Paper for the first implementation.
- Two mods from the start: reusable Core and one campaign mod.
- Custom blocks/items/textures and a character GUI are **in scope** for version 0.1.
- No NPCs; no mandatory PvP; no MMORPG quest/leveling focus.
- Five-session pilot, adjustable based on playtest feedback.
- GM-led first; later assess hybrid/GM-less modes.
- Target 1.21.10 / Java 21 for the prototype; the user selected it on 18 October 2026 after compatibility pins were identified.

### Open decisions — document before hard-coding

- Actual group size and typical length of a session.
- Exact existing Minecraft/Fabric/voice-chat versions and other installed mods.
- Final project name, Java package namespace, artwork style, and distribution/license plans.
- Whether profiles represent **player accounts** or one selectable character per player initially. Default to one character per player for 0.1, but avoid making future migration impossible.
- Whether the first world is handcrafted or prepared from an existing seed.
- The precise narrative clues and ending possibilities (GM authoring task, not a programming dependency).

None of these should block creating a compiling project skeleton.

---

## 13. Ready-to-paste Codex kickoff instruction

> You are implementing **Chronicle**, a Minecraft Fabric project described in `docs/PROJECT_OVERVIEW.md`. Read that document before making design decisions. This is a roleplay framework, **not** an MMORPG, PvP minigame, NPC quest mod, or complete automated narrative engine.
>
> **Your first task is M0 only:** Create a Java 21 / Minecraft 1.21.10 Fabric multi-project Gradle repository with two independently built mods: `chronicle_core` and `quiet_below`. The campaign must declare a hard dependency on the core. Use compatible pinned Fabric Loader, Fabric API, mappings, and Loom versions for the target Minecraft release; verify them rather than inventing version strings. Separate common/server and client-only sources cleanly. Add minimal entrypoints and logs that prove both mods initialize. Add a simple `/chronicle status` command with appropriate server-side registration to verify wiring; it may report a placeholder inactive session state. Ensure the dedicated-server build does not reference client-only rendering classes.
>
> Include working Gradle wrapper files, `README.md` instructions for build/run/client-server installation, a `docs/DECISIONS.md` recording choices and risks, and a minimal smoke-test checklist. Run the available build/test commands, fix failures, and report exact results. Do **not** implement the character editor, corruption system, or custom assets in M0. Keep the architecture open for them and propose the smallest next task (M1) once M0 passes.

## Reference documentation

- [Fabric getting started for Minecraft 1.21.10](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.10/develop/getting-started/creating-a-project.md)
- [Fabric 1.21.10 development environment / Java 21](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.10/develop/getting-started/setting-up-a-development-environment.md)
- [Fabric Loom: subprojects and client/common source sets](https://docs.fabricmc.net/develop/loom/)
- [Fabric 1.21.10 custom screens](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.10/develop/rendering/gui/custom-screens.md)
- [Fabric 1.21.10 custom block states](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.10/develop/blocks/blockstates.md)
- [Fabric 1.21.10 custom data components](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.10/develop/items/custom-data-components.md)

**Interpretation note:** This is a project brief and proposed implementation plan, not a promise that all features already exist. Exact technical APIs, dependency versions, and implementation choices must be validated against the pinned Minecraft version during development.
