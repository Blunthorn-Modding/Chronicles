# Chronicle menu redesign — design review

**22 October workstation convention:** use standard pack-overridable Minecraft GUI sprites, native font and native widgets wherever practical. Include player inventory only when it serves the interaction (such as actual Forge inputs); the Altar prayer popup has no inventory. Do not disclose campaign mysteries through raw gate diagnostics, stat worksheets, tooltips or narration. See [Altar prayer design](M11_ALTAR_PRAYERS.md). This convention does not implement the queued Forge redesign.

Approved 20 October 2026. CF-10. The operator approved this design and requested implementation. Branch `feat/cf10-core-menu` follows the approved mockup commit `e10f9d3`. [CF10_IMPLEMENTATION](CF10_IMPLEMENTATION.md) records the actual native slice and remaining campaign work; [TEST_PLAN](TEST_PLAN.md) records validation. This document remains the full design contract, including future capabilities that are not installed by this slice.

## The organizing rule

**Choose a subject on the left, an item in the middle, then inspect or act on that item on the right.** During play use Session, Players and Events. Map setup lives in a separate Advanced configuration group. A player, chest, event or location stays selected while its details change. Picking a region opens a contextual chooser and returns to the same event, rather than becoming another top-level page.

```text
Chronicle                     Session 2 · Paused     Editing: off · Enable editing
────────────────────────────────────────────────────────────────────────────────
Session          | Items / context                  | Selected item / actions
Players          |                                  |
Events           |                                  |
                 |                                  |
ADVANCED         |                                  |
Chests           |                                  |
Loot Tables      |                                  |
Regions          |                                  |
Locations        |                                  |
Game setup       |                                  |
────────────────────────────────────────────────────────────────────────────────
Server connection / result of last action                         Close menu
```

There is no Roles, Spawns, Policies or Observations top-level tab. **Chests** manages physical containers and their bindings; **Loot Tables** manages reusable item distributions, counts and refill settings. A chest links to its selected table without embedding the table editor. `Locations` contains oriented points and exact block sites, clearly distinguished. `Game setup` contains only the active game's key-site assignments and supported tools. It is not another geometry list or a catalogue of unbuilt formats. The menu remains a Minecraft screen: native font, rectangular controls, short forms, keyboard support and game-visible outlines. The browser mockup demonstrates structure and interaction, not final Minecraft pixel rendering.

## Sessions are play windows, not story boundaries

For Quiet Below and the general library, a session means a scheduled window in which simulation and automation may run. Pause/finalize freezes that activity, while the same world and campaign continue next time. It must not reset ecology, flags, event completion, starting-position claims or continuing active-time triggers. Existing M8 cue completion and its clock already live across session numbers; preserve that behavior. The previously requested start-of-session loot refill is an explicit configurable exception. Repeating a cue or resetting campaign state remains a separate deliberate action.

Remove **session preview** and **session recovery** from the proposed normal UI/commands. The earlier “preview” meant reviewing the effects of Start/End, not simulating the next session; use ordinary **Start play** / **Finish for now** with a concise effects confirmation where appropriate. Technical recovery means reconciling an interrupted chest/terrain operation after a write failure or crash, not restoring a narrative session. Existing service-specific recovery commands and journals stay available to operators; surface a contextual fault only when one exists. Do not make it a permanent Session tab. Journey may later explicitly opt into chapter/world boundaries and outcomes; other formats do not inherit them.

## What the old words actually mean

| Current term | Actual implementation | New presentation |
| --- | --- | --- |
| Places | All saved cuboid regions, oriented point locations and expected-type block anchors mixed in one list | Regions; Locations → Points / Block sites |
| Roles | `AuthoringCatalog.Role`: a mod registers an ID, label and required geometry kind. Current saving creates a site directly under that ID | Game setup → named requirements, e.g. Source city (region), Relic Heart (block site). **Never call these roles** |
| Spawns | A reusable purpose points at a saved oriented location, then a separate action moves a selected player | Players → Movement & respawn; saved coordinates are in Locations |
| Policies | Per-player first-arrival/death-respawn settings and retained arrival attempts | Players → selected player → Movement & respawn |
| Observations | `ObservationCatalog` public text/sound cues with one-time completion, audience and optional trigger bindings | Events → type “Message & sound” |
| Existing `/chronicle event` | `CampaignEvent` callbacks return campaign flag changes and completion/audit messages; these are not the M8 cue delivery service | Events → type “Game action”, using a distinct adapter |
| Flag | A named true/false game condition | Events → Conditions; human label/description when registered, technical ID in Details |
| Author mode | Server-owned temporary editing permission for this player connection while the session is non-active | “Editing: on/off”, separate from session phase and participation |

An event is a **discrete registered action within a game**. The Quiet Below is the game/campaign; “City vibration” is an event containing a public message and sound. Autonomous sculk growth is a running campaign system, not an event the GM must repeatedly fire. Real secret character roles, if a Werewolf game later registers them, belong under Players → Private game data; they never share the map-setup name.

## Navigation and every action's destination

The tables below are the button contract, including submenus. Labels are verbs plus objects. Navigation never saves configuration, starts play or moves a player. Read-only screens need no editing mode. A disabled mutating action displays its exact unmet requirement beside it; tooltips are supplementary. `Confirm` is not a generic second press of a changing button: a review sheet names the action, target and effect, with **Cancel** and a specific confirmation button.

| Section / middle list | Right-hand content and controls | Meaning and guard |
| --- | --- | --- |
| **Session → Play controls** | State, active play time, eligible player count, why the world is frozen; **Start play**, **Pause play**, **Resume play**, **Finish for now** as applicable | Start explains configured chest refills; finish stops simulation/automation without a story reset. No session preview, chapter boundary or implicit replay. Pause/resume are acknowledged actions. No silent pause to enable edits |
| Session → Activity | **Session**, **Events**, **Player moves**, **Loot fills** filters; selected record shows result, actor, session and time | GM-only audit. Existing ledgers remain distinct sources; an aggregate view must not invent a global ordering for tick-only records |
| Contextual service fault, only when present | Explain an interrupted chest/terrain action and link to its existing service-specific inspection/reconciliation tools | This is technical operation recovery, not session recovery. No permanent recovery menu, blind retry, narrative rewind or automatic overwrite. Backups remain workspace operations |
| **Players → selected player → Participation** | Online/participating/eligible shown separately; **Add to session**, **Remove from session** | Removing participation alone does not silently change spawn settings. Own **Leave Chronicle** removes participation and automatic moves; confirmation explains this. No op/whitelist editing in this menu |
| Players → Character card | **Read character card**, own **Edit my card** | Only existing public card fields. GM cannot edit someone else's card through a convenience UI without a separately approved capability |
| Players → Movement & respawn | **Move player…**, **Starting position**, **After death**, **Starting position history…** | Move chooses a location and reviews safety before **Move [name]**. Starting position means “Place this player here when they first enter play”; one attempt across the campaign, not each session/reconnect. Starting/death settings are independent, default off; preserve vanilla respawn. History may offer deliberate **Allow another placement attempt…**, never an implicit repeat |
| Players → Group selection (later) | Select players, **Apply participation…**, **Set destination…**, **Review affected players** | Show exact membership and per-player failures; no implicit all-player policy. Group/private-team source must be permission-filtered |
| **Events → selected event → Overview** | Type, purpose, registered game, public text/sound or effect description, setup status; **Preview event** | Read-only content/effect summary works before configuring a region, starting a session or enrolling anyone. It does not evaluate or execute a mutating callback |
| Events → Preview sheet | Text/effect summary and **Play preview — only me** if audio exists | One preview workflow: inspect the content and optionally hear it privately. No separate Sample action/command. Works paused/unconfigured; records no completion, changes no flags/timers, broadcasts nothing. Previewing a game-state/world action describes its effects without invoking its callback. Live audience checking remains a separate read-only readiness calculation |
| Events → Run | **Run event…** → exact effect/audience review → **Run [event name]** | ACTIVE only, no unresolved hold, valid saved references, satisfied prerequisites, live eligible audience where applicable. Shared manual/automatic completion. Expired/changed previews require review again. A game-state-only action needs no fake participant audience |
| Events → Automatic trigger | **Edit trigger…** form: Manually / On entering a region / After active play time / When a condition is true; **Save trigger** or **Replace trigger…** | These are radio choices, not four unexplained save buttons. Only Entry requires a trigger region. Timer has a visible “seconds of active play” label and scope; condition has a condition selector. Saved once-only history is never implicitly cleared by replacement |
| Events → Audience & requirements | All active participants / Participants in region when the handler supports that audience; **Choose region…**, **Add prerequisite…**, **Save configuration** | An audience region and an entry-trigger region are distinct concepts. Regions are shown only when needed. Current M8 uses one region for both: the first adapter must visibly label that constraint, not pretend two independent regions are supported |
| Events → Delivery history | Attempt status and recipients; **Allow event again…**; **Disable automatic trigger…** under trigger settings | Reset explicitly clears current completion and restarts timer delay; preserves audit of the reset in the proposed facade. CLAIMED is “Delivery uncertain — not retried”, never “not run”. Disabling automation must be distinguished from manually running the event |
| Events → Advanced configuration | **Remove event configuration…** | Preserves the registered definition and delivery history, removes the binding and prevents real execution until configured again. This is today's `OBS_OFF` behavior; it is not merely switching an automatic trigger to Manual |
| Events → Conditions | Registered/current true/false conditions, their meaning and references; **Set true…**, **Set false…**, **Create condition…** in advanced details | Use existing campaign-state permission/phase rules first. A future non-active configuration-time definition is separate from mutating a live flag; do not silently weaken the current ACTIVE-only flag rule |
| **Chests → Bound chests** | Selected table, coordinates, read-only refill summary and history; **Show chest**, **Open loot table**, **Change loot table…**, **Unbind chest…** | Outlining changes no contents. Retype/unbind names the exact container and retains fill history. Open loot table navigates to its distinct Advanced page |
| Chests → Add chest | **Bind looked-at chest…** → locked target summary + type selector → **Bind [type] chest** | One flow; no named block-anchor prerequisite or capture-first template. If the target moves/changes, refuse and ask to target it again |
| **Loot Tables → selected table** | List of item entries with item name, independent chance and min/max count; **Add item…**, **Remove item**, **Save loot table…**; recommended/bound counts, refill settings and **Reload loot configuration** | Add opens searchable server-registry selection (including installed mod items), chance and count range. New entries remain in this table's draft; individual rows can be adjusted or removed. Validate every entry/count/stack limit and schedule, then atomically save the whole table; no immediate chest fill or receipt reset. Preserve item components and JSON support. Mockup picker has a small labelled sample catalogue, not the live registry |
| **Regions → saved regions** | Name, dimension, both positions, size and “Used by”; **Show region**, **Edit region…**, **Delete region…** | Edit loads a server-owned draft from saved bounds. Update just Position 1 or Position 2, using coordinates in the editor or its command in-world. Keep the other unchanged. Review references, replace the same ID and increment revision; no duplicate region or silent consumer rebind |
| Regions → Create region | Instructions for `/chronicles author region pos1` and `pos2`, selected positions, **Continue in world**, **Name**, **Create region** | Stand at each desired position and run its command, then return to save in the UI. No raycast/look targeting for region selection. Both positions and same dimension required. Draft/outline survive closing the menu; clear only this connection's draft. Commands may accept explicit integer coordinates as an advanced alternative |
| **Locations → Points** | Top **Points / Block sites** selector; this view lists only oriented saved positions. **Save my position…**, **Show location**, **Update to my position…**, **Delete location…** | Description: “A saved position and facing for player destinations. No block is required.” Includes dimension, precise coordinates and facing. Saving never teleports players or enables a starting position. Keep selection separately for each kind |
| Locations → Block sites | Same top selector, separate list and actions: **Bind looked-at block…**, **Show block site**, **Replace block site…**, **Delete location…** | Description: “A reference to one exact block instance and its expected type. Used for stations, landmarks or interactions.” Manual fallback for vanilla/other blocks or existing instances. Not a player destination. Mod blocks own behavior; a site records only the reference. Authoring placement may offer registration. Chests keep their direct flow |
| **Game setup → selected game's requirements** | Quiet Below source city, unique Heart and registered stations; typed assignments, actual placed instances and consumers; **Choose existing…**, **Show assigned site**, **Change assignment…**, **Check setup** | Core supplies selectors; campaign declares which sites are mandatory, optional or multi-instance. Starting positions are optional under Players, not a required Quiet Below site. Registration alone does not arm ecology or create block functionality |
| Game setup → Game tools (later) | Registered extension panels for game state/validation and permitted actions, plus navigation to relevant generic editors | No sculk topology, crop or Heart logic in Core. Quiet Below can add its ecology panel here without introducing a library Ecology tab |

“Show” always means a private in-world outline or read-only information; **Move player** means teleport. Avoid “Preview player”, which sounds like inspecting a character card. `Replace`, `Reset`, `Bind`, `Off` and `Purpose` are replaced with the labels above unless a detailed inspector is being used.

These tables specify the **proposed** facade. Definition-only/private playback previews and the combined activity view are new work, not existing M8 buttons. Eligible participants means enrolled, online, alive, non-spectator and outside editing mode. Existing internal arrival/respawn purposes remain reusable library concepts; “Starting position” is the new display name, not a persisted-ID migration. Required/advisory/consumer metadata must be explicitly registered, not inferred from today's catalog.

## Region editing and block-owned functionality

**Region flow:** Two steps: **Select positions**, then **Review and save**. **Continue in world** actively invokes the server region-edit action (equivalent to `/chronicles author region edit [id]`) for this draft, requests its private outline and closes only after server acknowledgement. The operator does not type a separate edit command. For an existing region, use the current draft including unsaved coordinate changes; do not reload saved bounds and lose the draft. For new creation, the no-ID form activates the new draft without creating geometry. With no positions, show selection instructions; with one, show its corner; with both, show the bounding box. Never invent bounds to draw an empty draft.

Stand at Position 1, run `/chronicles author region pos1`, stand at Position 2, run `pos2`, and reopen Regions to name/save. A command before opening Create may seed the same draft. Selection is server-owned/private per connection; each command replaces only its own slot. Reject cross-dimension selection. Create stays disabled until both positions are valid. The outline remains while walking. Unavailable authority/IO errors leave the menu open with an exact reason; do not silently pause play. Mockup “simulate actions” controls and its schematic outline are test scenery, not proposed Minecraft menu buttons or a native rendering test.

**Edit flow:** Edit region (or `/chronicles author region edit <id>`) loads its two saved positions and revision. The current store keeps normalized min/max, not original click order: label these loaded bounds Position 1 and Position 2 without claiming to recover old selection order. Edit either coordinate triple in the UI or return to the world and run only the relevant pos command. Keep the other triple. Normalize for validation/persistence, preserve the stable ID, review old/new bounds and consumers, and reject concurrent changes rather than overwriting. Bound consumers pin revisions today: explicitly revalidate/rebind affected consumers, never silently follow moved geometry. Source-city changes must respect future ecology ownership/cleanup constraints; a geometry edit must not relocate an active network. Cancelling changes no saved geometry; disconnect/ACTIVE invalidates editing authority and unsaved drafts rather than leaking them to another player.

**Mod-block flow:** The campaign supplies an actual Forge/Altar/Heart block and its interactions, recipes, power checks and local state. Merely placing a reference must never be how a generic Core editor creates that functionality. Core can provide an optional placement-registration hook: during non-active operator authoring, the server recognizes a participating block type, records its exact placed instance and offers **Register this Forge?** / **Link this Heart?**, with expected type and intended use. Cancel leaves the block placed. Repeat ordinary station placements may be discovered automatically without mandatory registration; only a genuinely named/unique campaign link needs selection. Existing worlds can be scanned/inspected locally or registered manually; do not force-load every chunk or require re-placing blocks. Outside authoring there is no registration popup on normal player placement, no implicit activation of campaign progression and no operator snapshot sent to players.

Registration rechecks permissions, dimension, loaded position, type, instance identity and current assignment revision. Multiple stations are allowed when the game supports them. A unique Heart conflict must offer an explicit reviewed replacement, never steal the link. Breaking/replacing/moving a registered instance invalidates or updates its reference through campaign-owned lifecycle handling; same-type replacement must not inherit unique Heart identity/history accidentally. Changing the Heart must not bypass weakness/destruction rules. Core owns generic prompts/references; Quiet Below owns station behavior, Heart uniqueness, network attachment and validation. The current mod only registers *site requirements* for Forge/Altar/Heart; the actual blocks and placement hooks remain M12/CF-10 implementation work, not installed mechanics.

## Editing state and trustworthy feedback

The code audit confirms mode is server-owned (`AuthoringRuntime.authors`), not a client preference. It is never saved to disk, and disconnect/ACTIVE clears it. `AuthoringUi` derives its snapshot from that state; `AuthoringScreen` polls every two seconds. The report establishes confusing/stale presentation, **not a reproduced persistence defect**. Files: `core/server/AuthoringRuntime.java`, `core/server/AuthoringUi.java`, `core/client/AuthoringScreen.java`, `core/client/AuthoringUiClient.java` under the normal Java package roots.

Proposed header: **Session: Paused · World: frozen · Editing: Off** plus **Enable editing**. Enabling says “Only your connection; ends when play starts or you disconnect.” Only the server acknowledgement changes that status. Save locations and enrollment are persistent and get a separate **Saved** acknowledgement. Inputs/selection/unsaved drafts are explicitly temporary; display-only tab preferences may be local.

During ACTIVE, replace a disabled mode toggle with visible “Pause the session before editing” and **Go to Session**. Starting/resuming play pushes Editing: Off to every open operator menu; a plain start outside the menu has the same result. Snapshot contains explicit allowed actions/reasons, service health, mode membership, session editability and fresh server revision. Push relevant changes immediately, retain background refresh for recovery, and label disconnected/timed-out views **Connection lost — changes disabled**. Clear pending confirmation on authority/selection changes. Preserve focus/text on clock-only updates. Do not re-open closed screens or silently discard dirty edits when a picker or background update arrives.

## Preview and event adapter boundary

The current cue Preview calls `PlayRuntime.preview`, which requires ACTIVE, a saved binding, a satisfied prerequisite and eligible recipients. Region-audience definitions also need a region. That is a **live execution preview**, not a content preview; the old label is wrong. The new Preview event works on a definition, even when its setup is incomplete. The live check explains “Choose an audience region” or “No participating players inside Mine entrance”, not a generic invalid-configuration error.

Unify the **presentation**, not the unsafe side effects. Core event adapters advertise: stable qualified ID, game/label/description, type, supported audience/trigger options, declared required inputs, read-only preview with optional private media playback, validation and execution/ledger handler. **Preview** is one user-facing operation; sound playback is an opt-in inside it, not a separate Sample command or a real event execution. Existing `CampaignEvents` and `ObservationCatalog` remain distinct typed services behind adapters; state flags must not gain implicit inventory/world effects. Persist old IDs and source ledger identity; same-label actions do not share completion unless a deliberate mapping is reviewed. Existing M8 region coupling must remain explicit until a schema migration supports independent trigger/audience regions.

New **Disable automatic trigger** keeps a valid Manual binding and its audience/prerequisites; old `PlayService.off` instead removes that binding entirely. Implement these as distinct actions. Likewise direct point selection in Players must not silently rebind a shared spawn purpose for other players: retain existing purpose IDs/pins in Details, validate affected references, and use the existing purpose mapping or a new private binding deliberately. The main editor hides jargon, not dependencies or migration effects.

## Future library functions: placement without fake availability

| Planned capability | Home | Scope/status |
| --- | --- | --- |
| Entity groups, waves, safe entity spawn areas, counts, manual/condition start, cleanup/history | Events → Encounters / encounter configuration | Pending M7 follow-up; region/point selectors reused, effect handler/journal explicitly required |
| Native loot probability/count/component editor and per-table refill schedule | Loot Tables | Proposed UI over existing loot-type JSON contract; useful to Quiet Below, not implemented |
| Custom saved IDs, friendly names, duplicate/reference checks | Regions / Locations / Chests as appropriate | New editor support; immutable stable IDs, display names separate, no silent ID rename |
| Registered station/Heart/source requirements and campaign settings | Game setup; links into Regions/Locations | Current requirements exist; richer assignments/extension panels are proposed |
| Clock, reusable conditions/triggers, audience selection, manual/automatic once history | Events; clock shown in Session | Existing M8 foundation; expose capabilities per event type, no mandatory schedule |
| Operation reconciliation, validation, audit, unavailable services | Contextual affected-service fault; existing advanced service commands; Activity | Technical failures only, unrelated to narrative sessions. No generic Session recovery page or rewind |
| Player groups and bulk assignments | Players → Groups / review selected players | Future generic selection, not automatic factions or enrollment |
| Secret character roles/teams, elimination and private messages | Players → Private game data | CF-03 Werewolf direction only; own protected payloads/store; hidden even from public dossier serializers |
| Optional discussion phases, voting, elimination, win checks | Registered Events/Session panels only when that game opts in | Reusable independent capabilities, never prerequisites of Werewolf/hidden-faction games. A Vampire SMP-style game may have none of these. No dead controls or missing-vote setup errors |
| Journey chapters, 2–3 outcomes, next-world validation, confirmed transfer/carry-over | Session → Chapters & outcomes; Game setup → Chapter links; Players → transfer preview | CF-02 future design; no world loader/transition engine exists. Restore-safe world and inventory transactions must precede activation |
| Survival objectives, threat escalation and respite | Events → encounter actions; Session → registered objectives | CF-04 future design, scenario supplies rules, Core does not invent a horde director |
| Operation objectives/extraction or Intrigue proposals/decisions | Registered Session / Events panels, only if an approved game needs them | Optional format candidates, not committed engines or immediate new tabs |
| Quiet Below growth, benefits/costs, stations, Heart eradication | Quiet Below Game tools registered into Game setup; human play effects remain in-world | M9–M12 campaign-owned. No authored routes or per-field growth-region editor in Core |
| Existing prototype staged terrain preview/apply/undo and operation journal | Explicit Quiet Below legacy tools; contextual fault links to their handler | Existing M4 bounded tool; retain journal/recovery. It is not the autonomous M9 ecology solution or a narrative session rewind |
| Backups/server lifecycle/authentication | Operator workspace scripts/runbook | Keep outside normal player/game UI; no fake “safe live backup”, op promotion or firewall controls |

For **Quiet Below**, the relevant setup is source-city geometry, the unique Heart and actual station instances, plus optional loot, player starting/respawn points and event triggers. Its later game tools concern network status/controls (M9–M10), benefits/costs (M11) and station/Heart checks (M12). None imposes authored spread routes, scheduled discoveries or a session outline. The native loot editor and selection/registration improvements are useful library work for this event.

Hidden-faction games (including Vampire SMP), optional voting, Journey chapters/world transfers, Survival objectives/directors and generic encounter waves are **future format references**, not dependencies for Quiet Below. The mockup labels that separate design-only list **Other formats · design reference**; it is omitted from a shipped menu. A real game registers only its available panels. The placement matrix above preserves future scope without presenting those tools as current game setup requirements.

## Command proposal

**Proposed, not installed.** `/chronicles` or `/chronicles menu` opens the appropriate screen. Use the plural root consistently, including the operator-requested `/chronicles author region pos1` / `pos2`. Existing `/chronicle` commands remain compatible. Operators get management; ordinary players get their own participation/card and public status only. O does the same. Bare category names open the page; explicit verbs perform actions. Selectors complete known IDs/players and errors give the exact next step. Commands and widgets call the same typed services. Console receives text; private playback requires a player. Reviewed mutations return a short-lived single-use per-actor token bound to action/target/config/current authority/audience. Opening a menu or content preview performs no mutation. Use **review** for an effects confirmation and **preview** only for event content; there is no session preview/recovery command.

```text
/chronicles [menu]                              # open appropriate screen
/chronicles status
/chronicles edit on|off                         # temporary server editing permission
/chronicles join
/chronicles leave                               # self: participation + automatic moves

/chronicles session [status]
/chronicles session start [number]              # begin play; report configured refill effects
/chronicles session pause|resume
/chronicles session end                         # finish for now, retain continuing game state
/chronicles session history [limit]

/chronicles players [list]
/chronicles players inspect <player>
/chronicles players add|remove <player>          # participation only, non-active editing
/chronicles players card <player>               # public fields
/chronicles players move review <player> <point>
/chronicles players move confirm <token>
/chronicles players starting-position set <player> <point>
/chronicles players starting-position disable <player>
/chronicles players starting-position history <player>
/chronicles players starting-position reset review <player>
/chronicles players starting-position reset confirm <token>
/chronicles players respawn set <player> <point>
/chronicles players respawn disable <player>

/chronicles events [list]
/chronicles events inspect <event>
/chronicles events preview <event>               # content + optional private playback, no setup
/chronicles events check <event>                 # live readiness/audience, read-only
/chronicles events run review <event>            # live review token; not content preview
/chronicles events run confirm <token>
/chronicles events trigger manual <event>
/chronicles events trigger entry <event> <region>
/chronicles events trigger timer <event> <seconds>
/chronicles events trigger condition <event> <flag>
/chronicles events trigger disable <event>
/chronicles events audience all <event>
/chronicles events audience region <event> <region>
/chronicles events require set <event> <flag>
/chronicles events require clear <event>
/chronicles events config review <event>         # review complete draft + current revision
/chronicles events config confirm <token>        # save one atomic configuration
/chronicles events config discard <event>        # discard only this caller's draft
/chronicles events config remove review <event>  # remove binding, retain definition/history
/chronicles events history <event>
/chronicles events reset review <event>
/chronicles events reset confirm <token>
/chronicles events conditions list|get <flag>
/chronicles events conditions set <flag> true|false

/chronicles chests [list]
/chronicles chests bind <type>                   # looked container, preview locked target
/chronicles chests inspect|show <chest>
/chronicles chests change-type <chest> <type>    # returns target/effect review token
/chronicles chests unbind <chest>                # returns review token
/chronicles chests confirm <token>
/chronicles loot-tables [list]
/chronicles loot-tables inspect <table>
/chronicles loot-tables reload
/chronicles loot-tables edit <table>              # proposed native editor; JSON remains supported

/chronicles author region [list]                # Regions menu
/chronicles author region pos1 [<x> <y> <z>]     # own feet by default; replace only slot 1
/chronicles author region pos2 [<x> <y> <z>]     # own feet by default; replace only slot 2
/chronicles author region selection show|clear
/chronicles author region create <id>            # save selected positions; UI is primary flow
/chronicles author region edit [id]              # activate outline; ID loads or resumes that draft
/chronicles author region show|inspect <id>
/chronicles author region save review            # same ID; review bounds + dependent references
/chronicles author region delete <id>            # reference-impact review token
/chronicles author region confirm <token>

/chronicles locations [list]
/chronicles locations point create <id> here
/chronicles locations block create <id> target
/chronicles locations show|inspect <id>
/chronicles locations update <id> here|target    # kind-preserving reviewed replacement
/chronicles locations delete <id>                # reviewed reference impact
/chronicles locations confirm <token>

/chronicles setup [list]
/chronicles setup inspect <requirement>
/chronicles setup assign <requirement> <saved-site>  # validates required geometry
/chronicles setup clear <requirement>            # reviews dependent consumers
/chronicles setup confirm <token>               # required when replacing/clearing
/chronicles setup check
```

New creation is explicit; changing existing configuration uses reviewed replacement. Editing requires non-active server permission; execution requires the appropriate live phase. `/chronicles events trigger ...`, `audience ...` and `require ...` update the caller's draft; `config review` / `config confirm` atomically save against the pinned revision. Report coupling conflicts; never silently replace an audience region. Unsupported operations fail clearly. `events preview` opens one content/private-playback flow in a client; console receives the description and cannot play sound. No separate `events sample` command. Region editing is kind-preserving, command-selected and revision-checked, with no raycast selection.

Existing `/chronicle event`, `/chronicle spawn`, `/chronicle author ...`, `/chronicle flag ...`, `/chronicle profile ...` and immediate session start/end keep their current meaning. The proposed plural root is an alias/new facade, not a forced rename or a claim it is installed. Until implementation, O and `/chronicle author` remain operator-only. Legacy chest/terrain operation recovery stays at its explicit service path; there is no narrative recovery or session reset facade. Typed loot distributions keep JSON support and persisted loot-type IDs. Future group/encounter/chapter/role branches follow the grammar only after their rules are defined.

## Migration and implementation slices

1. **Approve vocabulary/layout and workflows.** This deliverable; no Java, packet or world changes. Review Events Preview versus Run and Locations versus Game setup in the mockup.
2. **Server view/action contract and sync.** Replace overloaded Entry/Snapshot strings with typed page detail/capabilities/reasons; stable object selection and confirmation handles, server mode push, stale/disconnected states. Existing services/schemas remain authoritative. Tests cover two GMs, command-driven mode changes, stale responses, reconnect and no preview mutation.
3. **Navigation and context editors.** Vertical Session/Players/Events/Advanced shell, persistent selection, contextual pickers/forms and reference impact; small-screen drilldown. Move player policies into the player detail. Do not ship a new frame around the old unlabeled button grid.
4. **Events facade and real previews.** Integrate cue/state-action adapters without merging ledgers; one content/private-playback preview, readiness and reviewed run, conditional fields/units. Preserve completion and active time across play windows. Add reset audit rather than silently destroying history. New endpoints need permissions/rate limits and no public secret leakage.
5. **Map setup and commands.** Add command-selected region drafts and single-position updates, separate Loot Tables, the plural command alias and a typed assignment index. Today's registered role IDs ARE site IDs: adopt those saved sites without renaming/copying geometry, preserving spawn/observation references. Review migration, back up stopped world, refuse collisions and retain legacy commands. Core placement-registration hooks are generic; actual Forge/Altar/Heart blocks and lifecycle identity arrive with M12. No menu-only substitute for block functionality.
6. **Future capabilities as needed.** Native loot editor, encounters, groups and game extensions each deliver their own service/persistence tests; design placement is not implementation. Journey/private roles still require their own product contracts.

Latest mockup acceptance additions: add/search/cancel/remove loot items and adjust every independent chance/count range; validate and save the complete table without filling a chest. Keep a bounded item list and scrollable native detail pane for large tables. Continue in world must resume this exact draft after acknowledgement and request its outline, preserving any single-position edit. Switch Points/Block sites without mixing their lists, descriptions or remembered selections. Hidden-faction games must remain valid with no voting/phase engine or setup requirements.

Verification: inspect every action for one obvious purpose, consistent name/context and visible disabled reason. Navigate all eight destinations; distinguish containers/tables; create a region from pos1/pos2 through a close/reopen draft; edit one position without changing the other or duplicating the region; cancel/accept a placed-mod-block prompt; preview a cue with no region; keep actual live gates and independent player/event state. Demonstrate confirmations and compact drilldown. Future references must be separate from this game's needs. Later native-client tests at 320×240, 480×270 and 640×360 logical sizes must cover keyboard navigation, sync/races/reconnect, draft authority/dimensions/bounds, stable IDs/stale consumers, block identity/removal/duplicate Heart/permissions, privacy and prior M1–M8 regressions. Inspecting, previewing or assigning sites never moves players, refills chests, fires events or arms ecology. Browser tests prove mock interaction/layout only.

## Review focus after 20 October feedback

Review the revised region create/edit workflow, separate Chests/Loot Tables and optional block-registration prompt. Starting position is renamed; event preview includes private playback. Session controls govern a continuing world, and the design reference for other formats is explicitly outside Quiet Below's requirements. This remains a mockup review, not a request to repeat the old running-menu test.
