# The Quiet Below: M5 pilot runbook

> **M5 group playtest pending.** M3/M4 are implemented and automatically rehearsed. The pilot tools prepare a separate authenticated world and verify a disposable copy; this runbook supplies the human session plan. Keep the ordinary play world separate from the prototype site.

## Pilot promise

This is a small settlement and disused mine for one Session 0 and five flexible sessions. The pressure is a changing seam beneath the mine, not a quest chain. Players may protect the settlement, investigate, study the phenomenon, profit from it, leave, cooperate, or invent another response. No prompt requires PvP, an NPC, combat, a Warden, or a particular character.

Use the original clue text and character hooks in [`campaigns/quiet-below/scenario`](../campaigns/quiet-below/scenario/README.md). The Echo Lens and Resonance Marker are `quiet_below:echo_lens` and `quiet_below:resonance_marker`; their active-session messages are server-authoritative and wrap in chat. Use the matching M4 client pack. Join the prepared pilot at `127.0.0.1:25585`; the ordinary play world remains at port 25565.

## Separate trial-site manifest

Create a new local trial world or a fresh copy restored into a new directory. Coordinates in this document are **absolute Overworld block coordinates**. For a flat disposable fixture, use `(0, 64, 0)` as the named site centre only; it is not an offset. Keep construction inside `x=-48..48`, `y=52..80`, `z=-48..48`. This is a compact build, deliberately small enough to inspect before every session.

| Area | Exact absolute bounds | Vanilla-style materials and purpose |
| --- | --- | --- |
| Common and notice board | `x=-6..6, y=64..68, z=-4..5`; board face is `x=-2..2, y=65..67, z=5`; entrance is `x=4, y=65..66, z=5` | Spruce logs/planks, cobblestone foundation, lanterns, barrels. Place the three public clue copies and character-hook sign on this accessible south-facing board. |
| Three cottages | `x=-31..-17`, `x=-5..9`, `x=18..32`; each `y=64..71, z=17..31` | Floor `y=64`, walls `y=65..69`, roof `y=70..71`. Leave two-block door openings at `(-24,65..66,17)`, `(2,65..66,17)`, `(25,65..66,17)`. They are social spaces, not assigned player homes. |
| Storehouse/workshop | `x=17..33, y=64..72, z=-7..8` | Stone-brick base, spruce, crafting/smithing/stonecutting stations. Place the unlocked clue barrel at `(24,66,1)`, a crafting table and spare Lens and Marker recipe ingredients. Give builders and traders a reason to be present. |
| Well and path | well centre `(0,64,13)`; paths within `x=-34..34, y=64, z=5..18` | Cobblestone, gravel, coarse dirt, water. Leave clear walking routes between all buildings and every listed door. |
| Mine yard and entrance | `x=-9..10, y=63..68, z=-32..-18` | Deepslate/cobblestone retaining wall, oak supports, rails only as scenery. Clear `y=64..68` above the `y=63` yard floor so the terrain's grass does not bury it. The two-wide stair begins at `x=-1..0, y=63, z=-32` and descends north. |
| Listening chamber | Outer stone/deepslate envelope: `x=-14..9, y=54..62, z=-44..-26`. Carve main interior `x=-7..2, y=55..60, z=-42..-28` above floor `y=54`; keep roof `y=61..62`. Carve west passage `x=-13..-8, y=55..56, z=-37..-35`, also above floor `y=54`. | This yields a two-high-or-more traversable room and one route to the surface: the south stair. The west passage is an optional reading nook, not an exit. Keep the east mass solid for the survey seam. |
| Stair and west passage clearance | For `k=0..8`, set stair floor at `x=-1..0, y=63-k, z=-32-k`; leave air at the same `x/z` for `y=64-k..66-k`. The west nook contains Clue 3 near the lectern at `(-12,55,-36)`. | The ninth step ends at `x=-1..0, y=55, z=-40`, opening into the main interior. Three clear blocks allow the player's head to cross into the next step before falling; the real-client survival walk checks this. |
| Marker alcove | Place the marker in the interior's eastmost cell at `(2,57,-37)`, attached to the solid `x=3` east wall and facing west; put Clue 1 on a nearby shelf at `(1,57,-37)`. | Stone/deepslate backing, one `quiet_below:resonance_marker`, no container or stage position. |
| Survey seam template | exactly `x=3..6, y=55..58, z=-36..-33` | **64 positions**: a 4 × 4 × 4 deepslate mass in the solid east wall, visible from the main interior at `x=2`. No containers, tile entities, decoration, player builds, or marker blocks in this cuboid. It is reserved for a future M4 stage test. |

The survey seam calculation is `4 × 4 × 4 = 64`. It is inside the chamber's solid east wall, outside the carved interior, marker and clue shelf, and inside the site bounds. Public signs stand at `(-2,65,6)`, `(0,65,6)`, `(2,65,6)` in front of the common wall, with glowing text; the mine has restrained lantern lighting. The generated site uses concise sign versions of the longer written clues below.

When a stage would serve the scene, stand near the loaded chamber and use `/quiet_below zone define survey_seam 3 55 -36 6 58 -33`, then `/quiet_below stage preview survey_seam`. Inspect its exact positions; during ACTIVE, apply using the returned token. `stage undo` restores the latest stage. The independent `quiet_below:mine_whisper` event updates public marker clues without changing blocks. See [M4 guarantees](M4_IMPLEMENTATION.md). The prototype has no zones until a GM defines one.

### Build and safety checklist

1. Create the separate world/copy and record its folder, seed, game version, mod jar hashes, site centre, and builder in the session log.
2. Build only the listed site areas; keep the rest of the valley ordinary and explorable. Do not pre-place sculk, catalyst, sensor, shrieker, or automated growth.
3. Place at least two physical copies of each written clue: a public copy at the notice board and an environmental copy at its listed discovery place. Put a plain book-and-quill or signed book version where the group can read it aloud.
4. Place one Lens and one Marker only after verifying the installed build contains their M3 IDs. The Lens recipe uses copper ingots, glass panes, and an amethyst shard; the Marker recipe uses stone bricks, calcite, and an amethyst shard. Spare vanilla ingredients belong in the workshop, never as a required reward.
5. Before every session, stop/save, take a dated backup of this trial world, confirm no active M4 job exists, check `/chronicle session status`, and confirm the group has matching client mods.
6. Use session start/pause/end as the implemented server commands document them. Outside ACTIVE, expect simulation to be frozen while players remain able to move, talk and edit cards.

## Session facilitation

Each session is a pressure to introduce when it fits, not a timetable. Begin by restating what the group chose last time, invite a brief in-character scene, and end with a visible question the players can carry away. A GM may skip, combine, or move any prompt. Record decisions and discovered clues in the shared log; do not mark a route as success or failure.

| Beat | Pressure and optional GM prompt | Information routes |
| --- | --- | --- |
| Session 0 — arrival | Let players create public cards, establish what they value in the settlement, and walk the mine yard. Ask: “What would make this place worth staying for?” | Character hooks, notice-board copy of Clue 1, a shared tour of the mine entrance. |
| Session 1 — settlement | A familiar tool or wall seems to carry a faint vibration. Ask who wants to check the old workings and who wants to keep the settlement steady. | Marker observation, Clue 1 in the alcove, testimony from any player who explored the mine. |
| Session 2 — signs | Two interpretations emerge: a recent disturbance or an old pattern waking. Ask what evidence the group trusts and what it is willing to protect. | Echo Lens reading on the Marker during ACTIVE, Clue 2 in the workshop, Clue 2 copied at the board. |
| Session 3 — consequences | Reflect a player decision in the prepared seam or in the settlement's preparations. If M4 exists and was rehearsed, a GM may use the separately previewed bounded zone; otherwise use a written report, sound, changed light, or new book. | Clue 3, discussion of the first two clues, visible but non-destructive preparation at the mine yard. |
| Session 4 — discovery | Reveal that the phenomenon responds to attention, extraction, sheltering, or another player-created theory. Ask what responsibility follows from that discovery. | The chamber, all three clues, private conversations, a jointly written notice or map. |
| Session 5 — resolution | Ask the community to make and live with a concrete choice: contain, study/coexist, leave, protect a different value, or propose a new path. Make the final visible world change reflect that choice only after discussion. | Every prior clue remains readable; no outcome needs a fight or vote threshold. |

If discussion stalls, offer a situation rather than an answer: a support beam needs repair, a resident wants the mine sealed, someone has found useful ore, or the marker gives the same unsettling clue to two different people. Ask players what their characters do. Do not have a GM character solve the mystery.

## Playtest checklist

- [ ] Two real Fabric clients join the isolated pilot with the same mod versions; both can read the status and dossiers.
- [ ] The group can find the core premise from the board, mine, or another player without a Lens or a single assigned investigator.
- [ ] During an ACTIVE session, Marker and Lens behavior is visible and readable; outside ACTIVE, dormant feedback is understandable.
- [ ] The site supports at least one social scene, one practical/building scene, and one exploration scene without PvP or combat.
- [ ] A player can miss any one clue and still explain the core question from the remaining routes.
- [ ] If M4 is installed, preview, apply, pause/stop, conflict handling, undo and recovery have all passed in a disposable rehearsal before the pilot; otherwise no world transformation is used.
- [ ] The GM can start, pause and close a session without editing code or improvising destructive world commands.
- [ ] After Session 1, collect a short debrief: what invited roleplay, what was unclear, who lacked a way in, and what should change before Session 2.

Human judgment remains required: whether the clues feel intriguing, voice chat works for the group, the map is comfortable to navigate, and players feel invited rather than directed cannot be established by automated checks.
