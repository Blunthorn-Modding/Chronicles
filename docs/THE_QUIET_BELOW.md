# The Quiet Below — campaign outline

Design draft, 19 October 2026. **Current campaign direction; implementation follows the engineering milestones.** The operator has tested the prototype and confirmed that its functions generally work; its fiction and event sequence did not feel coherent. This outline replaces the prototype's narrative assumptions. Existing code remains a technical experiment until it serves this design.

**Campaign format:** Situation (working name for the organizers' “Happening”). See [Chronicle's formats](CAMPAIGN_FORMATS.md). No fixed factions, personal corruption or forced stay are campaign decisions, not restrictions on other formats.

## The experience

A community discovers an ancient city beneath its familiar surroundings. Sculk crawls out through the existing caves and mine passages, seeking air and sunlight. As it grows, its capabilities and environmental costs increase. Characters decide what they value, what they are willing to change, and whether they want to stay.

The central question is: **what does this community do when something it cannot fully understand starts becoming part of its home?** The mod creates evidence, consequences and opportunities to act. It does not decide the answer.

### Direction established by the operator

- The operator handcrafts the production map. Agent-built worlds are disposable technical fixtures.
- An initially undiscovered ancient city is the source. Reusing the vanilla structure is welcome.
- Sculk spreads autonomously along the map's actual cave/mine geometry toward the surface or other ancient sites, consuming air/sunlight. No authored patch graph, painted trail, sequence of growth zones or per-expansion GM approval is required.
- The map builder registers key locations, not the path the sculk must take. Shared Chronicle Core tools own region/location/block/chest/spawn authoring; Quiet Below owns this ecology.
- Benefits are usable Minecraft effects. A benefit can exist long before anyone discovers it: learning about crop growth in Session 3 is valid. The simulation does not wait for a reveal scene.
- Visible growth places sculk, catalysts and sensors; at an extensive stage it can also produce shriekers. **There is no Ancient Focus relay or progression block.**
- The **Relic Heart** is the heart of this network. Weaken the sculk enough and it becomes destroyable; destroying it removes the whole campaign network, not just the nearest branch.
- Characters can contain, eradicate, study or live alongside the phenomenon, leave, or invent another response. No mandatory factions, personal corruption, involuntary personality changes, required betrayal or supernatural border.
- Manual GM cues, optional timed cues and first-entry discovery remain useful. They support events; they do not unlock growth or dictate player choices.

The inspirations named in conversation are tonal references, not lore to copy. Specific mechanisms below are proposals implementing this direction, not already installed behavior.

## A working explanation

**Proposal, not settled canon:** recent mine work opened a passage into a long-isolated environment around the city and its Relic Heart. The sculk now has access to new air and, eventually, daylight. The campaign need not assign blame to a player. Its origin can remain uncertain without making its behavior arbitrary or establishing a speaking, morally judging villain.

**Confirmed interpretation of “consumes air/sunlight”:** growth feeds on exposure to open cave space and light, advancing over the surfaces around those spaces and eventually shading/occupying them. No oxygen depletion or suffocation simulation. The exact growth rates/weights for air access, skylight and artificial light need a technical trial; do not silently substitute sunlight killing the sculk. It must be capable of starting in the dark city.

| Rule | What players observe | Consequence |
| --- | --- | --- |
| Growth has a physical frontier from the city. | Traces extend along an existing fissure, cave wall or mine passage before reaching somewhere farther away. | No isolated patch appears in a house simply for drama. |
| Terrain and access matter. | Opening a passage gives it an opportunity; pruning growth and closing an approach can hold it back. | Players act on the world rather than completing an authored route puzzle. |
| A larger, healthier network can do more. | Crops respond near living growth; established sites can heal or power the forge. | Limiting expansion limits capabilities as well as costs. |
| Occupation has environmental costs. | Ground is covered, dense passages become hazardous, and later shriekers may appear. | Costs affect places and resources, not character identity. |
| The Heart sustains the whole phenomenon. | Its visible condition changes as the network weakens or recovers. | Containment is reversible; destroying a sufficiently weakened Heart is a real eradication ending. |

No mine-support physics or mandatory mundane substitute is needed. Benefits may be exclusive and visibly supernatural; refusal, containment and departure remain valid.

## Autonomous growth and intervention

The implementation must discover where growth can go from actual blocks, not from a builder-supplied graph. Proposed first model: a bounded frontier spreads over eligible exposed surfaces next to existing growth, exploring branching cave/mine passages. Cave openings, exposed air, light and nearby registered ancient sites can influence growth, but an attractor cannot teleport sculk across intervening rock. No automatic tunnel excavation is proposed. Registering another ancient site is optional context, not a required waypoint in a route.

Rates and substrate rules need balancing. Configure them once for the campaign, with optional protected areas and a clear material policy; **do not require approving every patch or marking every cave, farm or future growth cell**. Ordinary eligible terrain can change without a GM click. Containers, inventories, custom stations and valuable constructions need an explicit protection policy. A material allowlist alone cannot reliably distinguish natural stone from a player-built stone house: record later building activity and define treatment of pre-existing builds before enabling growth. Registering a city region alone does not arm world mutation.

Pruning the actual frontier, severing living connections, and sealing an opening with unsuitable barrier materials should change growth under consistent physical rules. A connection includes the network's occupied surfaces/veins, not merely one visually prominent block. A complete barrier across one passage works there; a branching cave may give the sculk another way around. Do not bypass a successful barrier by spawning a new patch on its far side. Air/light changes can affect vitality; exact rules must be legible enough to test.

Proposed power model: retained campaign-owned living growth, its connection to the Heart and its exposure/resources determine capacity and vitality. This is observed runtime topology, never an authored patch graph or a count of every sculk block on the server. Placed/replaced sculk joins through continuous physical contact with the Heart or connected living growth, as confirmed in the 20 October review. Remote disconnected patches remain separate; no independent Heart or catalyst range is created. Detached growth loses its ongoing benefits and cannot start an independent Heart; the operator confirms gradual vitality loss while all dormant blocks remain in place. Detached remnants still belong to the network for final removal. A restored physical sculk bridge reconnects a dormant branch; [M9b](M9_VITALITY.md) supplies configurable review defaults, exposure-fed recovery even from zero, and ready-cell extent thresholds.

Growth, network updates and services run only during ACTIVE, occupied play; pause, no participants and server downtime do not accumulate catch-up expansion. Per-tick work and chunk access must be bounded. Persist frontiers, owned features, connectivity observations, vitality and pending work. An unloaded branch is unresolved, not dead or disconnected: never use unloading as a shortcut to weaken the Heart. No forced chunk loading or new terrain generation just to advance the ecology. Loading order must not strand a branch permanently or cause an unbounded burst.

### Visible development

| Network condition | Growth and capability | Cost |
| --- | --- | --- |
| **Rooted** | Surface growth crawls out from the city. Nearby eligible planted crops receive additional normal growth-age increments. | Sculk occupies actual eligible terrain; keeping the benefit tolerates a living foothold. |
| **Established** | Wider connected growth supports catalysts/sensors and local recovery; a connected Ancient Altar can offer a readable healing place. | Denser growth occupies passages and can produce short local Hunger effects. |
| **Extensive** | A sufficiently powered, physically connected Ancient Forge supports defined enchanted-equipment recipes. Shriekers may emerge in dense growth. | Larger footprint, more reactive terrain and possible local Darkness pulses make wider growth consequential. |

These labels describe ecological conditions, not sessions or GM unlocks. Catalysts and sensors can appear as growth develops; their density/timing and the extensive threshold for shriekers remain balance proposals. Growth itself is the evidence of power; there is no mandatory indicator block. Threshold transitions should follow measured conditions and recover/decline consistently, without flickering on every tiny change.

The connected ecology is the only campaign growth system: catalysts do not consume deaths/XP or supply upkeep. Suppress native catalyst spread throughout the configured campaign dimension, including not-yet-adopted catalysts. Existing sculk can be explicitly adopted through a saved region; optional world-wide cleanup removes non-campaign sculk as chunks normally load. Sensors/shriekers remain tracked, functioning features with vanilla active detection/warning behavior. **Confirmed Warden policy:** grown shriekers may summon Wardens under standard vanilla conditions; use vanilla warnings and despawning, without a bespoke warning or encounter system. Set up generated shriekers to support that behavior, rather than assuming every placed shrieker can summon. This creates emergent hazards, not a compulsory boss encounter for discovery or Heart destruction. Existing city hazards also need rehearsal and a viable retreat route.

## Benefits and their discovery

Proposed common thread: the living growth carries energy that stimulates plants and recovery and can be channelled by ancient equipment. Its exact source remains a lore decision.

**First implementation candidate: accelerated crop growth near living sculk.** Automatically derive the effect's reach from nearby powered growth. A builder does not bind every farm to a patch. Eligible planted crops receive bounded bonus age increments under the agreed ordinary crop conditions, producing normal harvests; do not globally change random-tick speed, place/harvest crops, or accelerate arbitrary blocks. Range, rate and crop set need balancing. A comparable unaffected plot is a useful test fixture, not mandatory production setup.

The crop effect starts when its ecological conditions are true. If nobody plants nearby or notices until Session 3, nothing was missed and no GM reveal is required. The same principle applies to later recovery and station power: capability and player knowledge are separate.

Regeneration, Hunger and Darkness are proposed short location-based effects derived from nearby living growth and density; exact ranges need specification. A named altar can focus recovery without making every beneficial/hazardous area a manually authored region. Stop refreshing effects outside their conditions. No exposure/corruption meter, species conversion or involuntary personality change. Healing already received remains; finished enchanted gear stays enchanted after disconnection or eradication. Ongoing services stop when power is lost.

All bonus growth, campaign effect refreshes and crafting require ACTIVE, occupied play. Specify pause behavior for already-applied campaign effects without clearing unrelated vanilla potion effects. Test enter/leave, pause/resume and reconnect. Costs must follow actual local ecology, not destroy an unrelated house because a threshold changed. Successful containment can preserve a useful smaller network; an unavoidable final catastrophe is not required.

Other candidates remain optional: water purification needs an actual polluted-water condition, visible consequences and bucket/bottle rules; it is not a vanilla property to claim already exists. Recoverable sound traces can aid investigation without becoming prophecy or recordings of player conversations. An exclusive forge recipe is legitimate but must not be necessary for escape or the only viable resolution.

## Ancient blocks as usable places

The operator's existing Blockbench designs provide the following candidates. Source filenames record provenance, not settled lore. Original files remain untouched.

| Existing design | Campaign role | Interaction to specify |
| --- | --- | --- |
| `RelicHeart` | **The unique living heart of the campaign network, situated in the ancient city.** | Shows network vitality and whether destruction is possible. When sufficiently weakened, ordinary deliberate destruction can end the whole network; no required forge weapon or reagent grind. |
| `purification_altar` | Ancient Altar, potentially a local healing/refining site. | Choose its initial function and show whether actual living growth powers it. “Purification” does not mean curing corrupted players. |
| `purification_forge` plus kindling/active variants | Ancient Forge producing defined enchanted gear while powered. | Readable idle/kindling/working states, explicit inputs/output, server-confirmed inventory transaction. |
| `RelicShard` | Optional future material; no established requirement. | Check export readiness and define a useful role before adding a collectible. It is not a mandatory Heart-destruction key. |

The **Ancient Focus is removed from the campaign design**. Do not register its model merely to reuse it, or replace the removed authored-route system with a chain of relay blocks.

Illustrative forge recipe, **not a balance commitment**: an unenchanted iron pickaxe, two amethyst shards and three XP levels produce an Efficiency III iron pickaxe while the forge is powered. Decide prior enchantments, item components, repairs and recipes before implementation. Core binds a station's location; Quiet Below determines physical connection, power and recipes. A decorative model copy must not create unlimited powered stations. Pause/restart/double-click handling must neither consume inputs without output nor duplicate output; M4's block undo is not an inventory transaction system.

## A removal ending: weaken and destroy the Relic Heart

Eradication must be mechanically achievable, not just a GM description of containment. Proposed sequence:

1. **Learn the relationship.** City evidence, observations and readable changes in the Heart show that the network sustains it. No lens, marker, fixed discovery session or chosen investigator is required.
2. **Weaken the network.** Remove living growth, cut branches and restrict the access that sustains it. Reduced rooted capacity and vitality make the Heart vulnerable. Use a defined, inspectable threshold and a stable vulnerability window; exact numbers and recovery delay need balancing. Sealing one passage or unloading chunks must not falsely count an otherwise powerful network as gone.
3. **Destroy the vulnerable Heart.** Give clear feedback when it resists or is vulnerable, with a deliberate ordinary interaction once eligible. Revalidate weakness server-side at destruction; simultaneous players cannot trigger the ending twice. Breaking early, explosions, pistons, mining/placing a copy or command-side removal must not bypass or silently lose the unique root. Specify supported handling/recovery before implementation. No compulsory Warden fight or exclusive enchanted weapon.
4. **The entire campaign network dies.** Growth and benefits stop immediately and permanently. Remove its sculk, veins, catalysts, sensors and shriekers, including the city's adopted initial growth and disconnected remnants. This is an eradication ending; it must not leave an active colony elsewhere or restart after a reboot. Already earned crops, healing and enchanted gear remain.

A functioning design also needs a precise cleanup contract. “All sculk” means **this campaign's whole network**, including its source, not a global command deleting every unrelated ancient city or player collection:

- At setup, the named source-city region is used to automatically inventory/adopt its existing sculk features as the initial network, with an inspectable summary. This is a one-time baseline, not a painted growth route. Additional sites join through actual spread under explicit ownership rules. Baseline indexing is bounded and resumes as chunks become available; do not arm growth or Heart vulnerability checks on a silently incomplete source inventory.
- Journal every campaign conversion and descendant feature, including explicit region adoption and physically connected existing sculk. Keep ownership after a branch is severed. A pre-existing/placed sculk cluster outside the source joins only through actual physical contact with the Heart-connected network; proximity alone does not claim it. Record the newly joined cells and their explicit cleanup baseline before publishing ownership.
- Restore mod-converted terrain from recorded before-states when the expected campaign state is still present. Existing city sculk has no known earlier block: specify removal to air for adopted standalone sculk/features, and removal of adopted vein faces while preserving their supports. Do not invent a city restoration history or regenerate the city.
- Cleanup is durable, bounded and restart-safe. Unloaded affected chunks remain queued until normally loaded, without forced loading. Loading one after the ending must prevent its owned sensors/catalysts/shriekers from reactivating before cleanup; campaign services stay off. Pause suspends physical cleanup; pending blocks are inert while the world is frozen. An ending record distinguishes Heart destroyed, cleanup pending and cleanup complete; report remaining chunks/conflicts rather than falsely claiming all terrain is already cleared.
- Later player edits/inventories take precedence over blind restoration. Do not overwrite a house built over an earlier conversion or confiscate harvested items to satisfy a count. Conflicting positions require inspection; remaining live owned growth still needs removal or explicit resolution before declaring cleanup complete. Unrelated sculk and carried blocks do not become a new root.

These are requirements for future implementation, not capabilities of the current 64-block stage journal. A dedicated growth/ownership and cleanup design is necessary; raising M4's limit is insufficient.

## What people do during play

Players have homes, shared projects, relationships and reasons to care before encountering the phenomenon. Session 0 establishes those connections. Ordinary scenes remain possible alongside exploration, experiments, trade, disagreement, care and departure. A farmer might welcome faster crops while opposing shriekers near home; a historian might preserve the city but support limiting its spread. Positions emerge from interests and can change; they are not selectable teams.

Leaving is valid. Do not teleport a departing character back, disable their account or send a revenge event. A player can take a break, return, or introduce another character by agreement. Current dossiers support one card per account; a multiple-character/archive system is not implied. The map needs an ordinary believable exit even if the group keeps play centred on the settlement.

## Trigger behavior the mod would need

Manual, timer and region cues share named events, prerequisites, audience, audit and completion records. Keep these delivery records separate from ecological state: manually firing a sound cannot conjure a powered forge or falsely mark the Heart destroyed. Testing a hypothetical observation is explicit test mode, not a canonical state transition.

- Optional configured timers use ACTIVE, occupied participant time. Pause, inactive state, downtime, empty servers and builder walks do not advance them. Persist progress and avoid restart catch-up bursts. No first-session timer or reveal schedule is configured by default.
- Entry discovery observes real participants in a validated Core region. Proposed default: a participant already inside when armed counts on the next eligible check. Simultaneous arrivals produce one group discovery; personal knowledge and repeat ambience are separate.
- Each optional cue declares its audience, such as the discovering group near the city. A recap for late joiners must not reveal undiscovered coordinates or replay global events.
- GM arm/disarm, inspect, hold/release and manual fire apply to cues; ecology has a separately explicit enable/hold control. Enabling the reviewed ecology rules authorizes their ongoing bounded work, not a sequence of manual patch approvals. Existing M4 scripted stages retain their own preview/approval rules.
- A crash can make sound delivery uncertain: expose that state for a deliberate GM decision rather than promising exactly-once audio. World mutations and the Heart ending need durable, recoverable records, not the same delivery guarantee as a sound.

## What the handcrafted map needs from us

Keep the accepted [Core authoring workflow](MAP_AUTHORING.md): select/name a region, stand at or target an exact location, bind a purpose, inspect/validate/save in the existing world. No import, Java edits or source rebuild to move a site.

For Quiet Below, register the **source city and Relic Heart**, relevant discovery/cue locations, and any ancient altar/forge or other important sites the builder wants. Configure ecology rules and optional protections, then validate/enable them. The mod inventories the city and discovers spread geometry itself. Do not ask the builder to place growth trails, connect patch nodes, mark every crop field/hazard or script a successful containment route. Generic Core regions remain available for campaigns that need them.

The map remains the operator's creative work. Do not generate its settlement, rebuild its city or impose test-world coordinates. Prototype Resonance Marker/Echo Lens/signs remain technical props unless separately justified; their suggestion of an intelligence reacting to kind words is not canon, and neither is required for discovery or resolution.

## Remaining design choices

The autonomous direction, removal of the Ancient Focus and the Heart eradication option are operator requirements. The following details remain proposals to settle before implementation:

1. Cause of the current awakening and how much truth the GM knows; no sentient/corrupting entity is assumed.
2. Final balance for air/light feeding, rates and capacity/vitality thresholds. The operator confirms no protected builds, physical reconnection and retained dormant blocks; M9b supplies review defaults for vitality loss/recovery.
3. Crop/recovery/forge balance and costs; catalyst/sensor timing. Generated-shrieker Warden summoning, warnings and despawning follow vanilla mechanics as confirmed by the operator.
4. Weakness feedback/window, Heart destruction handling, initial/encountered sculk ownership and conflict-safe total cleanup. Validate the technical feasibility against pinned Minecraft 1.21.10 before coding the ecology.

See [implementation milestones](DEVELOPMENT_MILESTONES.md) and [tracked work](BACKLOG.md) for delivery order. Milestones describe engineering, not sessions or story beats. Installed Core authoring/cues and Quiet Below surface/vitality capabilities are tracked in TEST_PLAN. Crop/healing/forge services and the Heart ending remain following implementation milestones.
