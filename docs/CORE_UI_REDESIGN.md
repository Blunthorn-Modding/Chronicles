# Chronicle menu redesign — design review

19 October 2026. CF-10. **Proposal and interactive mockup, not a deployed UI change.** The operator rejects the accumulated menu structure and requests a full library UI/command design before more implementation. This supersedes earlier screen-layout acceptance; it does not undo tested services or authorize a world-data reset. Branch: `design/core-menu-rework`.

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
Regions          |                                  |
Locations        |                                  |
Game setup       |                                  |
────────────────────────────────────────────────────────────────────────────────
Server connection / result of last action                         Close menu
```

There is no Roles, Spawns, Policies or Observations top-level tab. `Locations` contains oriented points and exact block sites, clearly distinguished. `Game setup` maps the active game's registered requirements to those saved regions/locations. It is not another geometry list. The menu remains a Minecraft screen: native font, rectangular controls, short forms, keyboard support and game-visible outlines. The browser mockup demonstrates structure and interaction, not final Minecraft pixel rendering.

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
| **Session → Current session** | State, active play time, eligible player count, why the world is frozen; **Start session**, **Pause session**, **Resume session**, **End session** as applicable | Start reviews session number and automatic chest refills; end reviews stopping game activity. Pause/resume are immediate acknowledged commands. No silent pause to enable edits |
| Session → Activity | **Session**, **Events**, **Player moves**, **Loot fills** filters; selected record shows result, actor, session and time | GM-only audit. Existing ledgers remain distinct sources; an aggregate view must not invent a global ordering for tick-only records |
| Session → Checks & recovery | Saved-reference validation, unloaded targets, incomplete inventory/terrain work; **Check again**, **Inspect operation**, **Recover selected operation** when a handler supports it | Recovery describes the handler's before/after/conflict result. Never a general “retry everything” or “clear error” button. Existing server backup commands are an operations task, not a pretend live backup button |
| **Players → selected player → Participation** | Online/participating/eligible shown separately; **Add to session**, **Remove from session** | Removing participation alone does not silently change spawn settings. Own **Leave Chronicle** removes participation and automatic moves; confirmation explains this. No op/whitelist editing in this menu |
| Players → Character card | **Read character card**, own **Edit my card** | Only existing public card fields. GM cannot edit someone else's card through a convenience UI without a separately approved capability |
| Players → Movement & respawn | **Move player…**, **First arrival**, **After death**, **Arrival history…** | Move chooses an existing location then shows a read-only safety/audience preview and a **Move [name]** review. Arrival/death are independent explicit switches with destination pickers, off by default; keep vanilla respawn settings. Reset under history becomes **Allow another arrival attempt…**, explaining the next join/session may move them |
| Players → Group selection (later) | Select players, **Apply participation…**, **Set destination…**, **Review affected players** | Show exact membership and per-player failures; no implicit all-player policy. Group/private-team source must be permission-filtered |
| **Events → selected event → Overview** | Type, purpose, registered game, public text/sound or effect description, setup status; **Preview event** | Read-only content/effect summary works before configuring a region, starting a session or enrolling anyone. It does not evaluate or execute a mutating callback |
| Events → Preview sheet | Text/effect summary; **Hear sample — only me** if audio exists; **Check live audience** | Sample requires an op player, sends privately to that player, works paused/unconfigured and records no completion. No global broadcast, timer reset or game-state changes. Live audience is a separate read-only calculation, showing missing setup, phase, flags, hold, exact recipients and safety checks |
| Events → Run | **Run event…** → exact effect/audience review → **Run [event name]** | ACTIVE only, no unresolved hold, valid saved references, satisfied prerequisites, live eligible audience where applicable. Shared manual/automatic completion. Expired/changed previews require review again. A game-state-only action needs no fake participant audience |
| Events → Automatic trigger | **Edit trigger…** form: Manually / On entering a region / After active play time / When a condition is true; **Save trigger** or **Replace trigger…** | These are radio choices, not four unexplained save buttons. Only Entry requires a trigger region. Timer has a visible “seconds of active play” label and scope; condition has a condition selector. Saved once-only history is never implicitly cleared by replacement |
| Events → Audience & requirements | All active participants / Participants in region when the handler supports that audience; **Choose region…**, **Add prerequisite…**, **Save configuration** | An audience region and an entry-trigger region are distinct concepts. Regions are shown only when needed. Current M8 uses one region for both: the first adapter must visibly label that constraint, not pretend two independent regions are supported |
| Events → Delivery history | Attempt status and recipients; **Allow event again…**; **Disable automatic trigger…** under trigger settings | Reset explicitly clears current completion and restarts timer delay; preserves audit of the reset in the proposed facade. CLAIMED is “Delivery uncertain — not retried”, never “not run”. Disabling automation must be distinguished from manually running the event |
| Events → Advanced configuration | **Remove event configuration…** | Preserves the registered definition and delivery history, removes the binding and prevents real execution until configured again. This is today's `OBS_OFF` behavior; it is not merely switching an automatic trigger to Manual |
| Events → Conditions | Registered/current true/false conditions, their meaning and references; **Set true…**, **Set false…**, **Create condition…** in advanced details | Use existing campaign-state permission/phase rules first. A future non-active configuration-time definition is separate from mutating a live flag; do not silently weaken the current ACTIVE-only flag rule |
| **Chests → Bound chests** | Selected container type, coordinates, refill schedule and history; **Show chest**, **Change loot type…**, **Unbind chest…** | Outlining changes no contents. Retype/unbind names the exact container and retains fill history |
| Chests → Add chest | **Bind looked-at chest…** → locked target summary + type selector → **Bind [type] chest** | One flow; no named block-anchor prerequisite or capture-first template. If the target moves/changes, refuse and ask to target it again |
| Chests → Loot types | Recommended/bound count, independent item percentages and counts, refill settings; **Reload loot configuration** now; **Edit loot type…**, **Add loot entry**, **Save loot type…** later | Every scheduled fill replaces all contents; warning next to schedule/save. Probability editor keeps independent rolls and full item components. Reload/save validates a candidate atomically; no reroll/reset of receipts. JSON remains supported |
| **Regions → saved regions** | Name, dimension, corners, size and “Used by”; **Show region**, **Edit bounds…**, **Delete region…** | Explicit reference-impact review on replacement/deletion. A moved region never silently relocates event or campaign behavior |
| Regions → Create region | **First corner: my feet / looked block**, **Second corner: my feet / looked block**, **Name**, **Save region**, **Clear selection**, **Hide outline** | Selection belongs to the server/current connection; clear only this draft, not saved regions. `Continue in world` closes the menu and leaves the private outline/selection available |
| **Locations → Points** | Oriented saved positions; **Save my position…**, **Show location**, **Update to my position…**, **Delete location…** | Position includes dimension, precise coordinates and facing. Saving a location never teleports players or enables automatic arrival |
| Locations → Block sites | Expected-type block positions; **Bind looked-at block…**, **Show block site**, **Replace block site…**, **Delete block site…** | Separate exact block primitive for Heart/Forge etc.; not a player spawn. Chests retain their simpler direct workflow |
| **Game setup → selected game's requirements** | Registered Source city, Relic Heart, Arrival point etc.; each row says **Region / Point / Block site**, saved assignment and consumers; **Choose existing…**, **Create & assign…**, **Show assigned site**, **Change assignment…**, **Check setup** | Core supplies the forms and typed selectors; game defines meaning. Required/advisory counts must be explicitly registered; today's catalog does not know “required”. Saving assignments enables no ecology, events or teleporting by itself |
| Game setup → Game tools (later) | Registered extension panels for game state/validation and permitted actions, plus navigation to relevant generic editors | No sculk topology, crop or Heart logic in Core. Quiet Below can add its ecology panel here without introducing a library Ecology tab |

“Show” always means a private in-world outline or read-only information; **Move player** means teleport. Avoid “Preview player”, which sounds like inspecting a character card. `Replace`, `Reset`, `Bind`, `Off` and `Purpose` are replaced with the labels above unless a detailed inspector is being used.

These tables specify the **proposed** facade. Definition-only previews, caller-only audio samples, the combined activity view and generic recovery inspectors are new work, not existing M8 buttons. Eligible participants means enrolled, online, alive, non-spectator and outside editing mode. Core arrival/respawn purposes remain reusable library concepts; a game's additional required/advisory and consumer metadata must be explicitly registered, not inferred from today's catalog.

## Editing state and trustworthy feedback

The code audit confirms mode is server-owned (`AuthoringRuntime.authors`), not a client preference. It is never saved to disk, and disconnect/ACTIVE clears it. `AuthoringUi` derives its snapshot from that state; `AuthoringScreen` polls every two seconds. The report establishes confusing/stale presentation, **not a reproduced persistence defect**. Files: `core/server/AuthoringRuntime.java`, `core/server/AuthoringUi.java`, `core/client/AuthoringScreen.java`, `core/client/AuthoringUiClient.java` under the normal Java package roots.

Proposed header: **Session: Paused · World: frozen · Editing: Off** plus **Enable editing**. Enabling says “Only your connection; ends when play starts or you disconnect.” Only the server acknowledgement changes that status. Save locations and enrollment are persistent and get a separate **Saved** acknowledgement. Inputs/selection/unsaved drafts are explicitly temporary; display-only tab preferences may be local.

During ACTIVE, replace a disabled mode toggle with visible “Pause the session before editing” and **Go to Session**. Starting/resuming play pushes Editing: Off to every open operator menu; a plain start outside the menu has the same result. Snapshot contains explicit allowed actions/reasons, service health, mode membership, session editability and fresh server revision. Push relevant changes immediately, retain background refresh for recovery, and label disconnected/timed-out views **Connection lost — changes disabled**. Clear pending confirmation on authority/selection changes. Preserve focus/text on clock-only updates. Do not re-open closed screens or silently discard dirty edits when a picker or background update arrives.

## Preview and event adapter boundary

The current cue Preview calls `PlayRuntime.preview`, which requires ACTIVE, a saved binding, a satisfied prerequisite and eligible recipients. Region-audience definitions also need a region. That is a **live execution preview**, not a content preview; the old label is wrong. The new Preview event works on a definition, even when its setup is incomplete. The live check explains “Choose an audience region” or “No participating players inside Mine entrance”, not a generic invalid-configuration error.

Unify the **presentation**, not the unsafe side effects. Core event adapters advertise: stable qualified ID, game/label/description, type, supported audience/trigger options, declared required inputs, read-only preview, optional private sample, validation and execution/ledger handler. Existing `CampaignEvents` and `ObservationCatalog` remain distinct typed services behind adapters; state flags must not gain implicit inventory/world effects. Persist old IDs and source ledger identity; same-label actions do not share completion unless a deliberate mapping is reviewed. Existing M8 region coupling must remain explicit until a schema migration supports independent trigger/audience regions.

New **Disable automatic trigger** keeps a valid Manual binding and its audience/prerequisites; old `PlayService.off` instead removes that binding entirely. Implement these as distinct actions. Likewise direct point selection in Players must not silently rebind a shared spawn purpose for other players: retain existing purpose IDs/pins in Details, validate affected references, and use the existing purpose mapping or a new private binding deliberately. The main editor hides jargon, not dependencies or migration effects.

## Future library functions: placement without fake availability

| Planned capability | Home | Scope/status |
| --- | --- | --- |
| Entity groups, waves, safe entity spawn areas, counts, manual/condition start, cleanup/history | Events → Encounters / encounter configuration | Pending M7 follow-up; region/point selectors reused, effect handler/journal explicitly required |
| Native loot probability/count/component editor and per-type refill schedule | Chests → Loot types | Proposed UI over existing JSON contract; not implemented |
| Custom saved IDs, friendly names, duplicate/reference checks | Regions / Locations / Chests as appropriate | New editor support; immutable stable IDs, display names separate, no silent ID rename |
| Registered station/Heart/source requirements and campaign settings | Game setup; links into Regions/Locations | Current requirements exist; richer assignments/extension panels are proposed |
| Clock, reusable conditions/triggers, audience selection, manual/automatic once history | Events; clock shown in Session | Existing M8 foundation; expose capabilities per event type, no mandatory schedule |
| Operation recovery and validation, audit, unavailable services | Session → Checks & recovery / Activity | Existing partial command services; unified readable inspection is new |
| Player groups and bulk assignments | Players → Groups / review selected players | Future generic selection, not automatic factions or enrollment |
| Secret character roles/teams, elimination and private messages | Players → Private game data | CF-03 Werewolf direction only; own protected payloads/store; hidden even from public dossier serializers |
| Discussion phases, voting, win checks | Events → game-specific round actions; Session → game phase if supported | CF-03 scenario rules unsettled. Capability extension, no empty always-visible controls |
| Journey chapters, 2–3 outcomes, next-world validation, confirmed transfer/carry-over | Session → Chapters & outcomes; Game setup → Chapter links; Players → transfer preview | CF-02 future design; no world loader/transition engine exists. Restore-safe world and inventory transactions must precede activation |
| Survival objectives, threat escalation and respite | Events → encounter actions; Session → registered objectives | CF-04 future design, scenario supplies rules, Core does not invent a horde director |
| Operation objectives/extraction or Intrigue proposals/decisions | Registered Session / Events panels, only if an approved game needs them | Optional format candidates, not committed engines or immediate new tabs |
| Quiet Below growth, benefits/costs, stations, Heart eradication | Quiet Below Game tools registered into Game setup; human play effects remain in-world | M9–M12 campaign-owned. No authored routes or per-field growth-region editor in Core |
| Existing prototype staged terrain preview/apply/undo and operation journal | Quiet Below Game tools, with its unresolved operations linked from Session → Checks & recovery | Existing M4 bounded tool; keep its journal/recovery intact. It is not the autonomous M9 ecology solution |
| Backups/server lifecycle/authentication | Operator workspace scripts/runbook | Keep outside normal player/game UI; no fake “safe live backup”, op promotion or firewall controls |

Future capabilities appear as a separate **Planned tools** reference in the mockup; they are not dead buttons in the final running game. A real game only registers available panels. This covers current roadmap placement without promising that all formats already work or starting their implementation.

## Command proposal

**Proposed, not installed.** `/chronicle` or `/chronicle menu` opens the right screen for the caller. Operators get management; ordinary players get their own participation/card and public status only. O does the same. Names mirror left navigation. Bare category names open its page; explicit verbs perform actions. Every selector completes known IDs/players, and errors give an exact next command. Commands and widgets call the same typed service actions, not one another's output text. Console receives textual results; self-only sampling needs a player. Live mutations use a short-lived per-actor confirmation token returned by preview; a confirm token is single-use and binds source action/target/config/session/audience. Merely opening a menu never returns a mutating token.

```text
/chronicle [menu]                              # open appropriate screen
/chronicle status
/chronicle edit on|off                         # temporary server editing permission
/chronicle join
/chronicle leave                               # self: participation + automatic moves

/chronicle session [status]
/chronicle session start preview [number]      # returns start/refill review token
/chronicle session confirm <token>             # explicit reviewed action
/chronicle session pause|resume
/chronicle session end preview                 # returns end-session review token
/chronicle session history [limit]
/chronicle session check
/chronicle session recovery list|inspect <id>
/chronicle session recovery preview <id>
/chronicle session recovery confirm <token>    # only handler-supported reconciliation

/chronicle players [list]
/chronicle players inspect <player>
/chronicle players add|remove <player>          # participation only, non-active editing
/chronicle players card <player>               # public fields
/chronicle players move preview <player> <point>
/chronicle players move confirm <token>
/chronicle players arrival set <player> <point>
/chronicle players arrival disable <player>
/chronicle players arrival history <player>
/chronicle players arrival reset preview <player>
/chronicle players arrival reset confirm <token>
/chronicle players respawn set <player> <point>
/chronicle players respawn disable <player>

/chronicle events [list]
/chronicle events inspect <event>
/chronicle events preview <event>               # content/effects, no setup required
/chronicle events sample <event>                # supported audio/text, only the caller
/chronicle events check <event>                 # live readiness/audience, read-only
/chronicle events run preview <event>           # live review token
/chronicle events run confirm <token>
/chronicle events trigger manual <event>
/chronicle events trigger entry <event> <region>
/chronicle events trigger timer <event> <seconds>
/chronicle events trigger condition <event> <flag>
/chronicle events trigger disable <event>
/chronicle events audience all <event>
/chronicle events audience region <event> <region>
/chronicle events require set <event> <flag>
/chronicle events require clear <event>
/chronicle events config preview <event>        # review complete draft + current revision
/chronicle events config confirm <token>        # save one atomic configuration
/chronicle events config discard <event>        # discard only this caller's draft
/chronicle events config remove preview <event> # remove binding, retain definition/history
/chronicle events history <event>
/chronicle events reset preview <event>
/chronicle events reset confirm <token>
/chronicle events conditions list|get <flag>
/chronicle events conditions set <flag> true|false

/chronicle chests [list]
/chronicle chests bind <type>                   # looked container, preview locked target
/chronicle chests inspect|show <chest>
/chronicle chests change-type <chest> <type>    # returns target/effect review token
/chronicle chests unbind <chest>                # returns review token
/chronicle chests confirm <token>
/chronicle chests loot list|inspect <type>
/chronicle chests loot reload

/chronicle regions [list]
/chronicle regions corner 1|2 feet|target
/chronicle regions selection show|clear
/chronicle regions create <id>
/chronicle regions show|inspect <id>
/chronicle regions update <id>                  # returns reference-impact review token
/chronicle regions delete <id>                  # returns reference-impact review token
/chronicle regions confirm <token>

/chronicle locations [list]
/chronicle locations point create <id> here
/chronicle locations block create <id> target
/chronicle locations show|inspect <id>
/chronicle locations update <id> here|target    # kind-preserving reviewed replacement
/chronicle locations delete <id>                # reviewed reference impact
/chronicle locations confirm <token>

/chronicle setup [list]
/chronicle setup inspect <requirement>
/chronicle setup assign <requirement> <saved-site>  # validates required geometry
/chronicle setup clear <requirement>            # reviews dependent consumers
/chronicle setup confirm <token>               # required when replacing/clearing
/chronicle setup check
```

New creation is explicit; changing existing configuration uses reviewed replacement. Editing settings requires non-active server editing permission; execution requires the appropriate live phase. `/chronicle events trigger ...`, `audience ...` and `require ...` update the caller's draft; `config preview` then `config confirm` saves the entire configuration atomically against the pinned revision. Report coupling conflicts; never silently replace a required audience region. Handler support is checked for every command; a state-only event must reject unsupported sound/audience/trigger operations clearly. Plural category names distinguish the new surface from legacy singular commands.

Existing singular `/chronicle event`, `/chronicle spawn`, `/chronicle author ...`, `/chronicle flag ...` and `/chronicle profile ...` remain documented compatibility routes during migration. Existing immediate `/chronicle session start [number]` and `/chronicle session end` keep their meaning; the new explicit `start preview` / `end preview` branches provide the reviewed path without reinterpreting them. Until the implementation slices land, O and `/chronicle author` retain their current operator-only behavior; the ordinary-player menu is proposed. New screen entry aliases may redirect; legacy chest capture/recovery remains under its explicit legacy path. Typed loot distributions continue to use JSON until the editor exists. Advanced console automation keeps explicit old APIs; deprecation/removal needs a later reviewed change. Future group/encounter/chapter/role command branches must follow this grammar but are not specified as callable now while their rules remain unsettled.

## Migration and implementation slices

1. **Approve vocabulary/layout and workflows.** This deliverable; no Java, packet or world changes. Review Events Preview versus Run and Locations versus Game setup in the mockup.
2. **Server view/action contract and sync.** Replace overloaded Entry/Snapshot strings with typed page detail/capabilities/reasons; stable object selection and confirmation handles, server mode push, stale/disconnected states. Existing services/schemas remain authoritative. Tests cover two GMs, command-driven mode changes, stale responses, reconnect and no preview mutation.
3. **Navigation and context editors.** Vertical Session/Players/Events/Advanced shell, persistent selection, contextual pickers/forms and reference impact; small-screen drilldown. Move player policies into the player detail. Do not ship a new frame around the old unlabeled button grid.
4. **Events facade and real previews.** Integrate cue/state-action read adapters without merging ledgers; definition-only preview and private sample, explained readiness and reviewed run; conditional region fields and units. Add persistent reset audit rather than silently destroying operator history. New service endpoints need permissions/rate limits and no public secret leakage.
5. **Map setup and commands.** New typed assignment index references existing saved geometry. Today's registered role IDs ARE site IDs: adopt those saved sites as initial assignments without renaming/copying coordinates, preserving old spawn/observation references. Preview migration, back up stopped world, refuse ambiguous collisions and retain legacy read/commands. Add new plural command aliases and deliberate confirmation flows; do not force a token retrofit onto legacy automation in the same step.
6. **Future capabilities as needed.** Native loot editor, encounters, groups and game extensions each deliver their own service/persistence tests; design placement is not implementation. Journey/private roles still require their own product contracts.

Verification: inspect each proposed action for one obvious purpose, consistent name, context and visible reason when unavailable. Mockup must navigate all seven destinations, keep selections, open/cancel contextual forms, preview a region cue with no region, reject its actual run until configured, keep live gates, demonstrate confirmation and compact drilldown, and show the future placement reference without pretending to execute unbuilt features. Later Java implementation must add real-client tests at 320×240, 480×270 and 640×360 logical sizes, keyboard-only navigation, no hover-only instructions, real mode changes from direct legacy commands/other GMs/reconnect, race/stale/IO failures, role-site migration collisions/refusal, privacy and prior M1–M8 regressions. Inspecting/definition-previewing, choosing a trigger, assigning/showing a site and viewing Game setup must never move players, change flags, refill chests, fire a cue, arm ecology or mutate terrain. Browser layout tests do not prove native Minecraft rendering or service synchronization.

## Review focus for tomorrow

Does the left → middle → right structure fit your workflow? Are **Locations** and **Game setup** a clear separation? Can you find a player's spawn settings without knowing “policy”? Can you preview a message/sound before defining its trigger? Is the distinction between **Preview**, **Check live audience** and **Run event** clear? These are the remaining design review questions, not requests to repeat the old menu test.
