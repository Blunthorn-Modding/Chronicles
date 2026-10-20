# Chronicle campaign formats

Design direction, updated 20 October 2026. The operator and co-organizer identified four formats for Chronicle. “Werewolf” means the wider family of hidden-faction games, including Vampire SMP-style experiences; voting and round structure are optional. Two additional candidates are proposed below at the operator's invitation. This is a planning taxonomy, not six implemented game modes or a request to build six engines now.

Use **campaign format** for the structure of an organized experience. Use **session** for one play meeting and **event/action** for something within it, such as a sound, discovery, horde arrival or vote. This avoids confusing a Werewolf game with the small named actions already registered through Core's `CampaignEvent` API.

## The four requested formats

| Format | Main driver | Session/world structure | What changes the outcome? |
| --- | --- | --- | --- |
| **Survival** | A group faces an external threat. | A threatened place or scenario; one or several sessions, as authored. | Preparation, cooperation, endurance, containment, defeating the threat or evacuation. |
| **Journey** | A group travels towards a destination. | Each session has its own contained chapter world and two or three authored end states. | The chapter's confirmed end state selects or changes the next world; other character decisions can also persist. |
| **Werewolf / hidden factions** | Secret faction membership, asymmetric knowledge and conflicting objectives shape play. | A persistent SMP-style experience such as Vampire SMP, or a compact game if its rules choose that structure. No required votes, rounds or session resets. | Discovery, concealment, alliances and game-specific actions/objectives; elimination and win rules only where the variant uses them. |
| **Situation** — proposed name for “Happening” | Characters share a place and respond when something changes there. | Usually a persistent setting; events develop it across sessions. | Character choices, relationships, investigation, interventions, accommodation or departure. |

The four are distinct organizing patterns. They can share ingredients: a Journey chapter can contain a survival scene, and a Situation can contain a mystery. The primary format describes what organizes the experience; it does not need to change whenever a scene uses another ingredient.

### Survival

Example: a settlement faces a zombie horde. The group can fortify, rescue people, investigate the source, contain an approach, hold out, or attempt an evacuation. Fighting every enemy is not the only possible response unless that particular scenario establishes it.

Authors must define the threat's rules, escalation, respite, available countermeasures and possible closing states. A horde can use timed waves, but “Survival” does not require every scenario to be a wave minigame. Character death, retreat, replacement characters and loss conditions need explicit scenario rules rather than assumptions inherited from another format.

Potential tools: authored threat/spawn regions, bounded escalation, phase timers, objectives, safe preparation periods and GM intervention. These are future requirements; current session controls do not amount to a horde director.

### Journey

The operator's defining structure is **one contained world per session, with two or three end states that influence the next world**. This is more specific than traveling through one persistent map.

For example, a chapter about a river crossing might end with:

| Chapter result | Next chapter consequence |
| --- | --- |
| Crossing secured | The group reaches the high-road world. |
| Crossing lost; marsh route chosen | The group enters the marsh-route world. |
| Bargain made for another passage | The group reaches a third prepared world with an obligation carried forward. |

These are illustrative branches, not campaign canon. Players need observable reasons for the transition; choosing a different label while loading an unchanged next world is insufficient. Not every choice requires a new map: an outcome may select a world variant, while smaller decisions alter its starting resources, relationships, access or conditions.

Before a chapter, its two or three supported end states and downstream worlds must be prepared. Unexpected player solutions need an explicit GM interpretation or a newly authored branch; do not secretly discard the solution to force a planned route. Earlier worlds and outcomes remain saved for inspection/recovery, even when play moves on.

Potential tools: chapter/world IDs, validated outcome-to-next-world bindings, a confirmed chapter close, and deliberate transfer rules for characters, inventory, health, resources and campaign flags. Choose carry-over rules in the scenario design; never silently reset or duplicate inventories. Loading the next world must be restart-safe and preserve the original maps. The current server/pilot scripts do **not** implement this chapter transition system.

### Werewolf

The name covers **Werewolf-like hidden-faction games**, including Vampire SMP-style persistent worlds. Hidden membership, asymmetric information, uncertainty and conflicting objectives define the family. A tabletop-style day/night round, accusation vote or elimination loop is not required. A compact voting game is one possible variant; a long-running SMP with secret factions and in-world actions is another.

Authors choose factions/roles, what each participant knows and may do, how membership changes, and reconnect/absence rules. They explicitly decide whether discussion phases, voting, elimination or victory checks belong in that game; none is a prerequisite for registering a hidden-faction campaign. Combat, conversion, spectators and dead-player communication are also variant decisions. Provide worthwhile participation after elimination where a chosen variant uses it.

Potential foundation: private faction/role assignment, controlled knowledge/messages and authorized game-specific actions. **Voting, phase timers, elimination and win checks are independently opt-in capabilities**, useful across formats when a game needs them. A hidden-faction game without voting must not load a vote engine, show a voting tab, or fail setup validation for missing voting rules. Secret membership must be stored/sent separately from public dossiers and public event history. A non-GM must not learn another faction/role through a profile packet, public status or an administrative debug view. This is a new privacy boundary to design and test, not a reuse of public profile fields.

The Quiet Below's rejection of compulsory vampire/human-style teams applies to **that campaign**. It is not a framework-wide ban on hidden teams, deception or elimination in a Werewolf scenario.

### Situation — “Happening”

Recommended working name: **Situation**. It describes the starting point without dictating a mystery, disaster, monster or adversarial group. “Happening” remains a valid working label until the organizers choose.

Characters have a place, relationships and existing lives. A development disrupts that setting. Players decide what to protect, change, accept, oppose or leave behind. There need not be one shared objective, a villain, fixed teams or a win screen.

**The Quiet Below is the current Situation campaign:** an unknown ancient city and expanding sculk create opportunities and costs for a community. Its departure option and lack of personal corruption remain intact. The goal is coherent situations and consequences, not quietly turning it into Werewolf with sculk-themed teams.

Potential tools: named places, discoveries, timed/local cues, persistent consequences, bounded environmental stages and GM pacing. The current prototype demonstrates some of these primitives; it does not yet provide the authored campaign described in [The Quiet Below](THE_QUIET_BELOW.md).

## Additional format candidates

These are recommendations for discussion, not additions to the committed implementation scope.

| Candidate | What makes it distinct | Example | Possible distinctive tooling |
| --- | --- | --- | --- |
| **Operation** | A group deliberately pursues a concrete objective through preparation and execution. The goal initiates play; an external threat need not arrive first. | Heist, rescue, sabotage, artifact recovery or a negotiated extraction. | Briefing, optional approaches, persistent preparation, objective state, alert/escalation and extraction/aftermath. |
| **Intrigue** | Negotiation and competing interests organize play. Players can know each other's positions; uncertainty does not depend on secret species or mandatory elimination. | Council deciding a settlement's future, a succession dispute or a contested agreement. | Public/private proposals, commitments, meetings, shared decisions and recorded consequences. |

An Operation can fail, succeed partially or change purpose through roleplay; do not reduce it to one mandatory stealth/combat solution. Intrigue should distinguish character negotiation from whatever formal decision procedure the organizers choose; adding a vote widget does not define the politics.

For now, treat **mystery/investigation, horror, comedy and faction conflict** as themes or secondary structures that can appear across formats. An investigation can happen during a Journey or Situation without needing a separate top-level engine. Revisit this only if a campaign needs a distinct organizing pattern or authoring workflow.

## Framework implications and scope

- Chronicle should support varied organized roleplay experiences. The Quiet Below is one campaign, not the definition of every campaign the framework can support.
- Keep sessions, permissions, persistence, public dossiers and audited named actions reusable. Journeys need chapter transitions; Werewolf needs private game state; Survival needs threat rules. Those do not belong inside Quiet Below's sculk package.
- Preserve the current Core + Quiet Below repository layout for now. Do not immediately create four/six empty mod modules, a universal rules editor or a mandatory format-enum-driven engine. Extract reusable services when an approved next campaign demonstrates the need.
- Competitive, hidden-role, combat and character-exit rules are scenario-specific. Do not globally impose Quiet Below's no-fixed-factions/no-required-combat policy on Werewolf or Survival, or import Werewolf elimination into Quiet Below.
- The operator authors production maps, including Journey chapter worlds. Generated test worlds are fixtures; a branching Journey does not authorize procedural generation of the production maps.
- Shared Core in-world selections, named regions/locations/block anchors, chest/spawn bindings, trigger configuration and restart-safe state are useful across formats. [The authoring plan](MAP_AUTHORING.md) defines the proposed contract; a world-import workflow is not a prerequisite. This taxonomy does not remove the existing pause on gameplay expansion pending design review.

The next active campaign remains The Quiet Below. Review its cause, benefit and event outline first; use the four-format direction to avoid accidental framework restrictions, not to begin parallel gameplay implementations.
