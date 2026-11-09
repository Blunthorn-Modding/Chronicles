# Chronicle commands (Core/Last Council0.2.0-dev.4)

Use **O** or `/chronicle` to reopen your last editor page in this connection. An explicit page command opens that page instead. Reconnecting resets navigation and Editing. Menu drafts and confirmations are never restored.

Only `/chronicle` is registered. `/chronicles`, `/quiet_below`, `/chronicle author`, `/chronicle area` and the old singular families are retired. Existing worlds, named records, history and JSON/YAML remain intact. Older milestone documents describe historical builds.

## Main families

| Command | Purpose | Access |
| --- | --- | --- |
| `/chronicle session` | Play controls and activity | Public status; operator level 2 for administration/history |
| `/chronicle players` | Participation, character cards, destinations and role assignment | Own participation/card public; administration requires operator level 2 |
| `/chronicle events` | Create/edit Events, preview media, configure triggers and saved Region changes, and deliberately run events | Operator level 2 |
| `/chronicle chests` | Physical bound containers and their table/direct-book settings | Operator level 2 |
| `/chronicle loot-tables` | Item and lore distributions | Operator level 2 |
| `/chronicle books` | Books & Lore, discovery records and deliberate delivery | Operator level 2 |
| `/chronicle regions` | Cuboids and optional Saved states | Operator level 2 |
| `/chronicle locations` | Points and Block sites, including Departure setup | Operator level 2 |
| `/chronicle roles` | Player Role definitions and starting supplies | Operator level 2 |
| `/chronicle encounters` | Encounter-group definitions and their map targets; saving never spawns entities | Operator level 2 |
| `/chronicle flags` | Progress flags and their current values | Operator level 2 |
| `/chronicle setup` | Registered game configuration tools | Operator level 2 |
| `/chronicle maintenance events` | Archive completed Event/source, encounter and temporary-offering records while retaining progress and exact retry evidence | Operator level 2; non-active play, no unresolved operations |
| `/chronicle editing on` or `off` | Server-owned per-connection Editing | Operator level 2, non-active play |
| `/chronicle debug ?` | Retained legacy operations and low-level inspection | Operator level 2 |
| `/chronicle game quiet-below ?` | Scenario-specific stage operations | Operator level 2 |

The UI owns forms, item components, book pages, departure warnings and multi-step confirmation. A family command opens the matching screen; it does not invent a second form workflow. Mutating commands keep the same session, Editing, loading, operation-hold and review guards as their shared services.

## Play and players

```
/chronicle session status
/chronicle session history
/chronicle session start
/chronicle session pause
/chronicle session resume
/chronicle session end
/chronicle session building on
/chronicle session building off
/chronicle players join
/chronicle players leave
/chronicle players card
/chronicle players card PlayerName
/chronicle players card set name Character Name
/chronicle players card set pronouns they/them
/chronicle players card set description A short description.
/chronicle players card clear pronouns
/chronicle players card clear description
/chronicle players add PlayerName
/chronicle players remove PlayerName
```

Building Mode is a global operator-controlled option while play is paused or stopped. It runs vanilla world simulation and permits building and chest use for everyone while player health, hunger and survival effects stay held. Chronicle play clocks and scenario progression stay paused. Turning it off freezes the world and closes open containers. Runtime holds suspend it; releasing a hold restores the choice in the same play window. A phase change or server restart clears it. Per-player Editing controls map metadata and grants no separate building bypass.

Player destination policies use `players starting-position set|disable|reset`, `players respawn set|disable`, and `players move review|confirm`. Use tab completion for each command's arguments. Assignment and first-entry recovery remain explicit; `/chronicle roles recovery entry PlayerName` compares evidence and never blindly repeats a kit.

## Regions and Locations

```
/chronicle editing on
/chronicle regions create Courtyard
/chronicle regions create "Ship deck" states start
/chronicle regions pos1
/chronicle regions pos2 40 70 20
/chronicle regions save
/chronicle regions edit courtyard
/chronicle regions preview courtyard
/chronicle regions list
/chronicle regions validate
/chronicle regions cancel
/chronicle editing preview hide
/chronicle locations points save harbor here
/chronicle locations points replace harbor here
/chronicle locations blocks bind helm target
/chronicle locations blocks replace helm target
/chronicle locations preview helm
/chronicle locations list
/chronicle locations validate
```

Create/edit uses the same temporary Debug shovel: left-click corner 1, right-click corner 2, then save or cancel. Names containing spaces are quoted. The server supplies safe IDs for newly named Regions; use the saved ID or tab completion afterwards. Editing a Region loads both corners. Changed bounds with saved states need `/chronicle regions save confirm` after the warning; changing the proposal invalidates that review.

Saved-state commands use the **Region ID**, without internal Area IDs or revision numbers:

```
/chronicle regions states capture review ship_deck end
/chronicle regions states capture confirm ship_deck end
/chronicle regions states apply review ship_deck start
/chronicle regions states apply confirm ship_deck start
/chronicle regions states remove review ship_deck unchanged
/chronicle regions states remove confirm ship_deck unchanged
/chronicle regions states recover ship_deck
```

Capture/apply/remove confirmations must follow a fresh review of that exact operation and state. Start/End templates and deletion history retain their existing storage. `regions remove <id>` and `locations remove <id>` remove the matching geometry kind only, subject to consumer guards. Departure remains a single Location-subtype popup; optional post-departure terrain belongs to its boarding Region's Saved states.

## Chests, loot, books and roles

```
/chronicle chests bind "quiet_below:settlement_supplies"
/chronicle chests inspect
/chronicle chests rebind "quiet_below:mine_cache"
/chronicle chests unbind
/chronicle chests validate
/chronicle chests status
/chronicle chests recover <operation-id>
/chronicle loot-tables list
/chronicle loot-tables inspect "quiet_below:mine_cache"
/chronicle loot-tables reload
/chronicle roles reload
```

In **Loot Tables**, choose **Create new loot table** above the list. Enter a name, optional ID and description. Create saves an empty table and opens its editor; add items/books with the shared popup and save those entries with **Save loot table**. An omitted ID is generated as `chronicle_core:loot_<name>`. Duplicate IDs are refused.

Bind/rebind uses the looked-at supported container. `chests inspect <binding-id>` opens the named entry; bare inspect reports the looked-at container. Edit distributions, direct-chest lore and role supplies in their screens. Every **Add item** / **Add lore book** opens the same searchable popup: choose an entry, then Add or Cancel. Role items include quantity. Direct-chest lore continues to the existing chance popup; table entries retain whole-table Save/Discard. Existing rich item components, percentages, count ranges and delivery rules are preserved.

## Events and game tools

Scenario defaults appear automatically under Events and Flags. An incomplete map-dependent Event shows `!` and a setup reason; preview still samples its message/sound privately. Command completion includes these unresolved defaults, and `events check cue` reports what is missing. Saved map configuration and progress take precedence over packaged defaults. See [automatic defaults](AUTOMATIC_SCENARIO_DEFAULTS.md).

The Events menu provides Create/Edit with **Basics → When → Requirements → Delay → What happens**. Choose a trigger, select any required flags, then add outcomes to one list: text, sound, Region saved states and flag updates. All required flags must be On. Silent Events that only update flags are supported. Private previews sample only text/sound; they never apply Region or flag changes. Command completion includes custom Event IDs and campaign defaults. See [progression and recovery notes](CF23_IMPLEMENTATION.md).

The top filters separate **Game Events** (including composed Events that change Regions) from **Region Changes** (direct saved Start/End state actions). **Flags** remains its own Advanced catalogue and manual override: creation requires non-active Editing and saves Off; Turn On/Off is an explicitly confirmed live control. Events set these saved campaign facts through their outcomes. No prerequisites means no flag gate.

**When** includes manual, active-time, Region entry and flags-ready triggers, plus registered gameplay sources. Pick a source and its target using the shared selectors: block interaction/open/place/break, book found/opened, departure completion, Region saved-state application, successful Altar prayers/Forge enchantments and Quiet Below ecology/Heart milestones. Book opened means opening a genuine managed book during gameplay; previews do not count. Activity is an optional additional filter. Source Events react to future qualifying actions after saving/rearming, not old history.

An optional delay counts occupied active play after the trigger and prerequisites are satisfied. Any required flag turning Off resets the full delay, even if it turns On again immediately. Pause, holds, empty eligible play and server downtime spend no time; saved progress survives restart. Old gated timers need an explicit edit/save because their historical unlock time was never recorded.

If a gameplay outcome is uncertain after interruption, the selected Event offers an exact source review. Read the recorded actor and evidence, then confirm whether the action happened or did not happen. This records progress without repeating the underlying action or giving items again. Recovery requires OP2, non-active play and no unrelated hold; it does not require Editing under its own recovery hold. Corrupt evidence may require stopped-world repair. Normal completed Events retain their existing deliberate rearm and recovery controls.

```
/chronicle events preview cue "quiet_below:city_presence"
/chronicle events check cue "quiet_below:city_presence"
/chronicle events run review cue "quiet_below:city_presence"
/chronicle events run confirm cue "quiet_below:city_presence"
/chronicle flags
/chronicle flags create quiet_below:example
/chronicle flags list
/chronicle flags get quiet_below:example
/chronicle flags set quiet_below:example true
/chronicle setup check
/chronicle setup tool <registered-tool> <verb>
/chronicle game quiet-below stage status
```

`preview` samples message/sound privately; `check` evaluates the current audience, and `run review` / `run confirm` performs the reviewed real delivery. Use `action` instead of `cue` for registered state actions. Event bindings and clocks stay configurable in Events; play windows do not impose narrative stages.

`flags create` refuses duplicates and requires an operator player in non-active Editing with no operation hold. `flags set` retains the existing active-play administrative upsert for scripts; it requires operator permission and no hold. The UI changes only existing flags. There is no duplicate `events flags` command family.

## Retained diagnostic operations

These remain for existing saved data and recovery, not as duplicate editor pages:

- `debug legacy-chests`: capture/replace/list/show/bind/rebind/preview/apply for historical captured inventories. Current authoring uses typed tables and bound chests.
- `debug spawn-bindings`: bind/rebind/remove/list/inspect/preview/confirm for historical purpose bindings.
- `debug player-spawns`: arrival/respawn/arrival_off/respawn_off/resetarrival for retained automatic destination policies.
- `debug events`: raw registered state-event list/preview/trigger.
- `game quiet-below zone define` and `stage preview|apply|status|undo|resume`: retained scenario terrain-stage operations, with their original active-play and token guards.

The native subtype fixture checks the actual merged dispatcher, removed roots, operator/nonoperator gates and representative syntax, and exports its full executable command inventory to `canonical-command-tree.json` in its disposable test server. No test-only roots are enabled on the review server.


## Last Council (selected scenario only)

| Command | Purpose | Access |
| --- | --- | --- |
| `/chronicle council` | Existing character card with My house / Other houses | Own card and own private papers; public house directory |
| `/chronicle council characters` | Council setup: house assignment, explicit Chancellor and papers, also available through Players | Operator level2; non-active Editing; house/cast setup locks after issuance, office changes refused during ballots |
| `/chronicle council proceedings` | Call/open meetings, agenda, individual ballots, regency, recess and archived results | Explicitly appointed Chancellor; most mutations active play |
| `/chronicle council vote` | Deliberately open the current ballot | Captured representative or read-only Chancellor; cast still needs eligibility and Region presence |
| `/chronicle council treaties recover` | Reload the exact saved treaty journal and lectern bindings after a storage hold | Operator level2; no consent is invented or ceremony replayed |

**I** is the default rebindable Open council proceedings key, available only to the appointed Chancellor. Operator permission alone grants no proceedings authority; the Chancellor cannot also own a house. The lectern has no procedural controls.

All pending ordinary motions must complete a vote or be explicitly deferred before Regency unlocks. Deferrals return next meeting. House papers issue automatically on first active play, using a durable receipt so restart/retry cannot reroll them.

**R** is the default configurable Open current ballot keyboard binding. A notice does not replace another screen. The hotkey suspends a pure editor without discarding its draft, closes containers normally, and ignores text entry/confirmations. Dismissal is not abstention. Each house's first accepted confirmed vote is final.

Council voting uses an existing Region subtype; motion proposals/sponsorship use an existing lectern Block-site subtype and physical proximity. These commands do not bypass the corresponding authoring, participation, phase or hold rules. A technical void needs a public reason; replacement is a new ballot. For regency, a void permits same-meeting replacement with the same slate; only a completed ballot consumes that meeting's regency slot. Closed outcomes are not reversible through Void. New political setup/reset/withdrawal controls and actual random complication integration remain pending.

Treaty ceremonies use a separate lectern Block-site subtype under Locations. House representatives create/sign drafts there; only the appointed Chancellor sanctifies while every party has that same lectern open. Signatures survive leaving/reconnecting, but changed terms require fresh consent. Private existence, terms, breaches and history are projected only to the parties and Chancellor. Active amendments, realm ratification and automatic political effects remain future work.
