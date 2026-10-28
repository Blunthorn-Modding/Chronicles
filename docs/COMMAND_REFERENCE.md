# Chronicle commands (dev60)

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
| `/chronicle setup` | Registered game configuration tools | Operator level 2 |
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

The Events menu provides Create/Edit with Basics, Message & sound, Trigger and Region changes. Shared selectors list registered sounds, current Regions, conditions and saved states. Private media previews need no complete trigger configuration and never apply Region changes. Manual and automatic runs share completion history. Recovery, uncertainty acknowledgement, configuration removal and deliberate rearming are available on the selected Event; see [execution and upgrade notes](CF22_IMPLEMENTATION.md). Command completion includes custom Event IDs as well as campaign defaults.

```
/chronicle events preview cue "quiet_below:city_presence"
/chronicle events check cue "quiet_below:city_presence"
/chronicle events run review cue "quiet_below:city_presence"
/chronicle events run confirm cue "quiet_below:city_presence"
/chronicle events flags get quiet_below:example
/chronicle events flags set quiet_below:example true
/chronicle setup check
/chronicle setup tool <registered-tool> <verb>
/chronicle game quiet-below stage status
```

`preview` samples message/sound privately; `check` evaluates the current audience, and `run review` / `run confirm` performs the reviewed real delivery. Use `action` instead of `cue` for registered state actions. Event bindings and clocks stay configurable in Events; play windows do not impose narrative stages.

## Retained diagnostic operations

These remain for existing saved data and recovery, not as duplicate editor pages:

- `debug legacy-chests`: capture/replace/list/show/bind/rebind/preview/apply for historical captured inventories. Current authoring uses typed tables and bound chests.
- `debug spawn-bindings`: bind/rebind/remove/list/inspect/preview/confirm for historical purpose bindings.
- `debug player-spawns`: arrival/respawn/arrival_off/respawn_off/resetarrival for retained automatic destination policies.
- `debug events`: raw registered state-event list/preview/trigger.
- `game quiet-below zone define` and `stage preview|apply|status|undo|resume`: retained scenario terrain-stage operations, with their original active-play and token guards.

The native subtype fixture checks the actual merged dispatcher, removed roots, operator/nonoperator gates and representative syntax, and exports its full executable command inventory to `canonical-command-tree.json` in its disposable test server. No test-only roots are enabled on the review server.
