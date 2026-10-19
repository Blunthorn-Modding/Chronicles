# The Quiet Below — campaign outline

Design draft, 19 October 2026. **For discussion before further gameplay implementation.** The operator has tested the prototype and confirmed that its functions generally work; its fiction and event sequence did not feel coherent. This outline replaces the prototype's narrative assumptions. Existing code remains a technical experiment until it serves this design.

**Campaign format:** Situation (working name for the organizers' “Happening”). See [Chronicle's formats](CAMPAIGN_FORMATS.md). The no-fixed-factions, no-personal-corruption and voluntary-departure decisions below belong to The Quiet Below; they do not prohibit hidden-role Werewolf scenarios elsewhere in the framework.

## The experience

A community discovers an ancient city beneath its familiar surroundings. Sculk begins moving out of that buried environment into the places above. It offers something useful as well as something unsettling. Characters must decide what they value, what they are willing to change, and whether they want to stay.

The central question is: **what does this community do when something it cannot fully understand starts becoming part of its home?** The mod creates evidence, consequences and opportunities to act. It does not decide the answer.

### Direction established by the operator

- The operator builds the campaign map by hand. Agent-built worlds are disposable technical fixtures, not the proposed production map or its visual standard.
- Use an initially undiscovered ancient city and sculk as the backdrop. Reusing the recognizable vanilla structure is welcome; an entirely custom replacement is unnecessary.
- Something changes now, and sculk spreads outward in a way that feels connected to the place.
- Give sculk credible benefits and costs, so reasonable characters can disagree about its future.
- Benefits must have concrete Minecraft behavior. As the connected network spreads, it becomes more capable and its environmental costs increase. No structural-physics simulation or mandatory mundane substitute is part of this direction.
- Important regions, block anchors, chests and spawn locations are authored in-world with shared Chronicle Core tools. Quiet Below consumes those definitions; a map-import workflow is not the primary authoring experience.
- No mandatory vampire/human-style split, infected/uninfected teams, personal corruption meter, involuntary personality changes or required betrayal.
- Characters can contain, remove, study or live alongside the phenomenon, or choose another response. A character can leave. No supernatural border, unavoidable curse or punishment exists to keep them in the scenario.
- Keep manual GM events; add optional timed and discovery triggers that create situations without choosing the players' actions.

The inspirations named in conversation are tonal references, not a specification to copy. No assumption about a particular series' lore is needed for this draft.

## A working explanation that connects the events

**Proposal, not settled canon:** recent work in the mine has opened a passage through a long-isolated part of the ancient city's environment. A living sculk network is establishing itself along that new connection. The opening happened shortly before play; the campaign does not need to assign blame to a player.

It responds to changes in its surroundings and can carry repeated sounds through connected growth. This does not establish a speaking intelligence, moral judgment or a villain. Its exact origin can remain uncertain, but the GM needs consistent rules for what it does. These are proposed campaign rules, not claims about vanilla Minecraft's lore or growth simulation.

| Rule | What the players can observe | Design consequence |
| --- | --- | --- |
| Growth follows connected routes from the city. | New traces appear along a fissure, tunnel or adjoining seam before appearing farther away. | No unrelated sculk patch simply appears in someone's house for drama. |
| A bounded intervention changes the next local development. | Removing a connecting trace or building a barrier holds a tested approach; opening another route gives it somewhere else to go. | Containment produces understandable evidence. It need not secretly make things worse. |
| Connected spread increases what the network can do. | An affected plot grows crops unusually quickly; a larger network supports a healing site or powers an ancient station. | The benefit is observable game behavior, and limiting growth means accepting a limit on capabilities. |
| Increasing occupation also increases environmental costs. | Approved ground is lost to growth; dense areas later produce local Hunger/Darkness effects or interfere with an authored farming area. | Costs affect places, resources and access. They do not assign corrupted identities or control character choices. |
| Intervention is local and imperfectly understood, but consistent. | Similar small experiments have comparable results; later clues can refine an earlier theory. | Mystery comes from incomplete evidence, not arbitrary rule changes. |

The previous mine-stabilization proposal is withdrawn. Minecraft does not need a structural-support simulation for this campaign, and merely describing a stable mine does not deliver a useful game mechanic. Benefits should be visibly supernatural and actually usable. They may offer something ordinary methods cannot reproduce; a mundane alternative is not a design requirement. Characters still retain the option to refuse a benefit, contain the network or leave.

## Power, benefits and costs

Proposed common thread for the fiction: the connected growth carries an unfamiliar energy. At its living edges this stimulates crops and recovery; the city's ancient stations can channel it into equipment enchantments. The source and full nature of that energy remain a GM design decision. This gives the effects a relationship without introducing structural physics or treating every new benefit as an unrelated magical incident.

**Recommended first demonstration: accelerated plant growth.** Bind a small farm region to a connected sculk patch. During ACTIVE play, give eligible planted crops additional bounded growth pulses, advancing their normal growth ages rather than changing the world's random-tick rate. A nearby ordinary plot provides a visible comparison: the affected wheat matures earlier and produces normal harvestable crops. Do not need a new food economy, polluted-water system or a physics model to demonstrate the first unnatural benefit.

For a rehearsal, plant the same crops under comparable ordinary conditions in the affected and control plots; show that growth differs, that pausing stops bonus pulses, and that severing the local connection stops the bonus. The extra growth rate, affected crop set and pulse budget need balancing. It does not place/harvest crops for players, accelerate arbitrary blocks or run through unloaded terrain.

### Proposed progression — place-based, not character levels

| Network condition | Concrete new capability | Associated cost to reveal |
| --- | --- | --- |
| **Rooted:** a small connected foothold reaches a prepared site. | Additional normal growth-age increments in an authored crop plot. | New sculk visibly occupies approved surrounding ground. Keeping the benefit means tolerating that foothold; an early intervention can hold it here. |
| **Established:** more of the prepared connected route is active. | An authored refuge/altar region can refresh a short Regeneration effect while a player is inside. It improves ordinary health recovery without changing identity, faction or maximum health. | Dense, separately bound overgrowth areas can apply a short Hunger effect while occupied. Lost ground and the awkward passage are real costs; signs/particles and an initial mild pulse make them observable before escalation. |
| **Extensive:** the network reaches and powers an ancient station. | The Ancient Forge enables defined enchanted-equipment recipes; stronger or broader site effects become possible if deliberately authored. | More approved territory is occupied, and named high-density approaches can produce short Darkness pulses. The group must decide whether wider access to the network's capabilities is worth its larger footprint and hazards. |

These are proposed campaign capabilities and costs, not implemented effects or a mandatory sequence. Regeneration, Hunger and Darkness are examples of familiar Minecraft effects used as temporary location-based conditions. Stop refreshing them when the player leaves the relevant region; there is no lasting exposure meter, infection, species conversion or involuntary personality change. Healing already received is not revoked when a player leaves.

Growth pulses, effect refreshes and crafting require ACTIVE. Specify how already-applied campaign effects suspend on pause so a short lingering regeneration does not quietly keep the campaign service running; do not clear unrelated vanilla potion effects to accomplish this. Test enter/leave, pause/resume and reconnect explicitly.

Define power from the living network rooted in the city and the state of its authored connected patches. Do **not** count all sculk blocks on the server: mined/re-placed sculk carpets and unrelated ancient-city decorations must not farm power. Author thresholds against the prepared patch graph, with visible milestones and consistent connection rules. Exact thresholds and physical connection checks require their own specification. An unloaded connection is unknown, not proof it has been cut; do not force-load the world to resolve it.

Disconnecting a branch removes its ongoing services and can lower available capacity; reconnecting a valid route can restore them. Finished enchanted gear stays enchanted. The consequence is loss of future station access, not a hidden punishment attached to a character or item when its owner leaves. Costs must also follow known locality/power rules: reconnecting one patch must not suddenly destroy an unrelated house.

The first cost can be small and the later costs more severe, but the system must not have an indefinitely beneficial state followed by an arbitrary unavoidable catastrophe. Show clues to the relationship between footprint, capability and danger, and let successful containment hold a useful smaller network. No territorial conversion or crop damage is authorized merely by selecting a large effect region; destructive targets require explicit authored approvals and reversible-stage rules.

### Other benefit candidates

- **Water purification** remains a valid possible later capability. It needs a defined polluted-water condition, visible feedback, a real harmful effect and rules for buckets/bottles/transport so ordinary water cannot accidentally become toxic or bypass the mechanic. It is extra game content, not something to claim vanilla water already provides. Plant growth is the proposed simpler first slice, not a rejection of purification.
- **Recoverable sound traces** can support investigation later, but should not replace practical benefits or become unlimited prophecy/recordings of player conversations.
- A rare or exclusive forge recipe can be a legitimate reason to preserve the network. It need not have an ordinary substitute, provided the campaign does not make that recipe mandatory for escape, essential information or the only viable ending.

## Ancient blocks as usable places

The operator's existing Blockbench designs can support this progression, rather than adding unrelated props. Names and mechanics below are proposals; the source filenames are asset provenance, not settled lore.

| Existing design | Proposed campaign role | Interaction to specify |
| --- | --- | --- |
| `ancient_focus` | A visible local relay/indicator of network connection and available capability. | Read its current connection/power state; it is not a compulsory key that gates all discoveries. |
| `purification_altar` | An Ancient Altar used for a bounded refuge or a later material/purification process. | Bind its effect region and choose one initial function. “Purification” does not imply curing corrupted players. |
| `purification_forge` plus kindling/active variants | **Ancient Forge:** a powered workshop that creates defined enchanted gear. | Clearly indicate idle, kindling and working state; accept explicit materials/gear, show the offered output, and consume inputs only on a valid server-confirmed operation. |
| `RelicHeart` / `RelicShard` | Possible network focus or forge reagent if a concrete use is approved. | Inspect export readiness and define acquisition/use before registering a collectible or starting a grind loop. |

Illustrative forge recipe, **not a balance commitment**: an unenchanted iron pickaxe, two amethyst shards and three XP levels produce an Efficiency III iron pickaxe while the forge is powered and ACTIVE. A later recipe could offer a rarer combination. This is an actual equipment change with visible input/output, not a promise that a newly accessible tunnel is valuable. Decide rules for prior enchantments, item components, repairs and recipes before implementation.

Bind the station block and its area with Chronicle Core authoring. Quiet Below owns what network state powers it and which recipes it offers. Placing a decorative copy of the model must not create an unlimited powered forge everywhere. Cutting power stops new crafting; pause/restart/double-click handling must neither consume inputs without output nor duplicate output. Station inventory actions need their own transaction design rather than pretending M4's block-only undo restores inventories.

## What people do during play

Players have places to live and work, shared projects, relationships and reasons to care about the community before they encounter the phenomenon. Session 0 establishes those connections. The opening of Session 1 leaves room for ordinary character scenes before introducing the first disturbance.

The repeatable pattern is: encounter a change, compare observations, try an intervention, see its consequence, and decide what to do next. People can build, explore, document, trade, argue, look after each other or leave. No character has to become the designated investigator.

Positions emerge from interests, and can change. A builder may welcome a stable passage but reject growth near homes. A historian may want the city preserved while opposing extraction. Someone who initially advocates removal may later accept a small protected patch. These are not selectable teams with exclusive abilities.

Leaving is a valid character decision. Resolve a departure scene if wanted; do not teleport the character back, disable their account or apply a revenge event. The player can take a break or, by agreement, introduce another character or return later. A return is an invitation, never an obligation. Current dossiers support one card per account; a formal multiple-character/archive system is not implied or required now. The campaign map must leave a believable ordinary route out, even if the group agrees that play stays centred on the settlement.

## Proposed event outline

The IDs and triggers below are **design names, not implemented commands**. Sessions are flexible beats, not a five-session railroad. Discovery or a convincing player intervention can move a beat earlier, postpone it, or make it unnecessary.

| Event | Cause and proposed trigger | What happens | What it gives the players |
| --- | --- | --- | --- |
| QB-E01: The first low sound | Example default: 30 minutes of active, occupied play in the first story session; also available manually. Recent opening of the deep passage is the cause. | Everyone in the campaign audience hears a restrained distant sound from the mine direction. People near it hear a clearer local version. A short accessible text cue accompanies it. No blocks change. | A shared incident people can discuss. They can investigate now, dismiss it or finish their current scene. |
| QB-E02: A trace with a direction | First investigation of a mapped mine threshold after E01, or manual introduction during an earlier investigation. | A small pre-approved trace connects the disturbed mine to a deeper route. There is visible physical evidence and a plain-language observation. | A lead that can be followed and checked, not a cryptic line unrelated to the landscape. |
| QB-E03: The buried city | First eligible player enters the map's named city-discovery region. Can occur before E01/E02. | Nearby explorers receive a brief environmental cue and a place to observe the city. Record discovery once. Do not announce a precise hidden location to everyone else. | Recognition that the traces have a source and scale. Discoverers choose what to tell the settlement. No forced teleport, fight or sealed exit. |
| QB-E04: Something worth keeping | A connected patch reaches the bound farm; the GM enables the prepared first capability when its evidence can be observed. | Eligible planted crops receive bonus growth pulses; players can compare them with a nearby control plot and harvest the result. | A concrete unnatural benefit they can test and use, with no support-physics fiction required. |
| QB-E05: More capable, more costly | Players preserve or allow further connected spread; the GM approves the next authored network stage. | A refuge/altar or, later, the forge becomes usable. More approved ground is occupied and the corresponding bound hazard begins with a readable mild effect. | A real trade-off between food/healing/equipment access and a network with a larger footprint and stronger environmental costs. |
| QB-E06: An intervention has a result | Players act on a connecting route or propose containment; the GM/validated connection rules apply the prepared local outcome. | Cutting a branch stops its bonus growth or station access; successful containment holds a smaller benefit/cost state; reconnecting a valid route restores the corresponding service. | Repeatable evidence that interventions matter. Already healed players and completed gear are not retroactively punished. |
| QB-E07: The community's decision | Player-led decision or accumulated conduct; GM selects only the world response needed to represent it. | Hold a limited beneficial patch, accept wider growth for advanced stations, remove a branch to protect land, undertake removal, close the mine, depart, or combine these. | A visible consequence and closing scenes consistent with what characters actually did. No ending menu, score or required boss. |

E01 is the proposed first-session disturbance; E03 is the proposed first-entry trigger. Neither should depend on a player holding the existing lens or finding the existing marker. If the city is discovered early, acknowledge that discovery and retire or adapt obsolete introductory cues. Do not replay a “nobody knows what is below” beat after players have already walked through it.

Vanilla city hazards are a map-authoring choice. Rehearsal must establish sensor/shrieker behavior and a usable observation/retreat route, so discovering the city does not accidentally become a mandatory Warden encounter. Important evidence also needs a route that does not require combat.

### A possible five-session rhythm

1. **A place to care about:** ordinary community scenes, first sound, first trace. End with a question, not a required descent.
2. **The place below:** exploration and city discovery if the group pursues it; bring back physical observations, drawings or accounts.
3. **An attractive possibility:** demonstrate the benefit, give it time to matter, then expose a limited cost.
4. **What are we willing to change?** experiments, competing plans, departures and commitments. Make small consequences legible.
5. **Living with the decision:** implement the response the group earned through its actions and play its consequences. A mixed or unresolved outcome is acceptable.

This rhythm can contract, expand or change completely. The GM does not block the city until Session 2 or make every player agree before someone can act. A character's departure can occur in any session.

## Trigger behavior the mod would need

Manual, timed and region triggers should call the same named campaign event and share its prerequisites, once-only record and audit. An operator can preview why it is ready, what audience will receive it, and which world changes are proposed. A timer must not become a second invisible GM.

- Proposed clock: accumulate time only while the campaign is ACTIVE and at least one participant is online. Pause, inactive state, server downtime and empty-server time do not advance it. The first-session timer binds to a designated story session; technical tests and Session 0 do not arm it by accident. Persist elapsed time and completion; do not fire missed cues in a burst on restart.
- Region discovery comes from the server observing a real participant crossing a validated authored region. Builder/GM walkthroughs do not count unless explicitly enabled. “Already inside when armed” needs an explicit policy; proposed default is to count that participant on the next eligible check, avoiding a forced exit/re-entry.
- One group-level first-discovery event fires once, even if several players enter together. Record the discoverer/time for GM history. Personal knowledge and any later repeat ambience are separate from that global completion flag.
- E01's shared sound targets online campaign participants across the authored campaign area; attenuation/direction is presentation, not a demand that everyone stand at the mine. E03's cue targets the discovering group in/near the city. Late joiners can obtain an observed-event recap without replaying the global incident.
- Initial automatic events are non-destructive sounds, text or observations. A trigger may propose/queue a world stage; existing preview/approval safeguards still govern its application. No hidden growth in homes, forced chunk loads or automatic Warden spawning.
- GM controls need arm/disarm, inspect, hold/release and manual fire. Manual fire consumes the same once-only event, so the scheduled version does not repeat it. Resetting discoveries/timers is explicit and limited to test copies or a deliberate new campaign instance.
- Define restart-safe outcome recording before coding. If a crash makes sound delivery uncertain, show an “outcome uncertain” GM entry and require a deliberate decision rather than blindly replaying destructive work. Do not promise exactly-once delivery of sound across a crash.

## Spread that looks natural and remains authorable

Natural-looking spread and uncontrolled world simulation are different requirements. The first campaign can show connected, uneven growth with authored patches, variable delays and small cues. Its cause and spatial continuity make it feel natural; it does not need an unbounded growth algorithm.

Author a graph of small patches: city edge → fissure → old workings → settlement approach, with optional side routes and containment boundaries. Every proposed expansion must have a valid connected predecessor and fit the current intervention state. GM discretion controls pacing and consequences. Authored static city sculk is backdrop; it is not something the mod should try to capture or undo wholesale.

The current M4 prototype handles at most 64 safe natural-block positions and retains only the latest operation until undo. That proves one reversible stage, **not** a multi-stage spread network, crop-growth service or powered station. Keeping multiple accepted patches, tracking power/dependencies and preserving later player edits need a separate design and implementation slice. Do not raise the limit or quietly remove undo restrictions to approximate this outline. Large Core regions define where an effect is eligible; they do not authorize enumerating/mutating every block in the cuboid.

Any plan for “containment works” must define the actual evidence/authoring check. A player placing arbitrary blocks is not already an implemented detector. First trials can use a declared player plan, GM confirmation and a prepared outcome; more physical detection can follow if it improves play.

## What happens to the marker, lens and prototype clues?

The current Resonance Marker, Echo Lens, mine_whisper flag and clue signs demonstrate registration, interaction and delivery. Their names, claims and placement do not establish campaign canon. The existing signs' hints of an intelligence reacting to kind words are especially **not** a settled explanation.

Recommended disposition: leave those assets in the technical fixture for now; do not place them in the handcrafted map merely to use the implementation. If testing later shows a need, the marker could be an ordinary modern survey plate and the lens a way to compare local sound traces. Each would need a defined maker, purpose, acquisition route and understandable result. Neither may be the sole route to essential information. The reused Blockbench artwork can be kept, reassigned or omitted independently of those decisions.

## What the handcrafted map needs from us

The map is the operator's creative work. The mod must adapt to its saved world, dimensions and chosen ancient city; it must not generate the production settlement, rebuild the city, or impose the test world's coordinates.

Needed workflow: work in the map, select two corners for a region, stand at or target an exact location, save a name, then bind an event, chest profile, spawn purpose, station or local effect through shared Chronicle Core tools. Quiet Below references those IDs for city discovery, power and effects. Save/inspect/test directly in-world; backups and optional rehearsal copies are separate operational tools, not the authoring experience. No Java edits or source rebuild should be required to move or configure a site.

See [map-authoring tools](MAP_AUTHORING.md) for the planned workflow and the limits of today's commands. See [tracked work](BACKLOG.md) for the dependency order. **Next work is design review and a map-authoring specification, not more isolated supernatural interactions.**

## Decisions to settle before gameplay implementation

1. Is the mine connection a suitable cause for the current change, or should it be a collapse, water movement or a different authored incident?
2. Is accelerated crop growth the right first demonstration, followed by local regeneration and a powered Ancient Forge? Choose the initial environmental cost and progression thresholds; a mundane alternative is not required.
3. Should the truth remain an ecological mystery throughout the pilot, or is there a specific deeper revelation the operator wants? No sentient/corrupting entity is assumed here.

The exact city/field/refuge/forge locations and event timings are configured through authoring. Group size/session length will tune the rhythm. Mechanics, recipes, costs and thresholds above remain proposals for review, not hidden implementation decisions.
