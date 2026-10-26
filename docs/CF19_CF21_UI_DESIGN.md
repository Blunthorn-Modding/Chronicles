# CF19–CF21: Player roles, departures and area-transition UI design

**Status:** dedicated design and lead/browser review complete; awaiting organizer workflow review. Design only. This document and the paired [interactive mockup](design/roles-departures-transitions.html) define an operator-facing Core UI before any Minecraft implementation. They do not authorize a runtime/schema change.

## Purpose and ownership

These three generic library tools let a campaign package provide a starting situation without turning sessions into story chapters or making a bespoke command vocabulary for every campaign.

| Tool | Core owns | Campaign package owns |
| --- | --- | --- |
| Player roles | Definitions, capacity, assignment records, arrival/respawn destinations, one-time grants and generic selection/GM UI | Default role definitions and any campaign meaning attached to them |
| Departure points | Bound interaction, aboard-region evaluation, two confirmations, durable departed status and optional transition call | The campaign's departure wording and why departure matters |
| Area templates / transitions | Captured terrain payload, target validation, durable replace operation and recovery | Which prepared regions are captured, where they are applied, and event/departure links |

`Player roles` is deliberately not the old `AuthoringCatalog.Role` term. The latter describes a map requirement such as “a region” or “a block site”; it must not be reused for a player character/background. Regions and Locations remain their existing geometry registries.

The scenario package may ship editable defaults in its declared configuration format (the organizer requested YAML for Quiet Below). The UI consumes the same normalized role definition whether it came from package defaults or a GM-created definition. The exact loader/parser is an implementation decision, not a UI reason to duplicate the editor.

## Navigation

The existing left navigation remains unchanged:

```text
Session / Players / Events
--------------------------
Chests / Loot Tables / Books & Lore / Regions / Locations / Game setup
```

`Game setup` gains four sibling items:

1. **Player roles** — starts, respawns, one-time supplies and private named books.
2. **Departure points** — bound departures for an online onboard group.
3. **Area templates** — captured prepared source regions.
4. **Area transitions** — saved template-to-target replacement definitions.

`Block limits` remains a separate existing Game setup item. It is not shown again in Locations or beside every placed block. `Regions` continues to create/edit bounds with `author region pos1`/`pos2`; `Locations` continues to register Points and Block sites. A departure chooses from those existing concepts instead of inventing another geometry workflow.

The focused mockup also keeps the existing Game setup registry visible as unchanged placeholders: **Block limits, Enchantment caps, Ingredient enchanting, Lasting bargains, Heart weakness, Sculk ecology, Spread mode, Sculk vitality, Sculk ownership, Crop growth, Local healing, Local Hunger, and Local Darkness**. It does not recreate those accepted editors, but must not imply they were removed. The middle catalogue scrolls independently while the left navigation stays in place.

Desktop remains **left subject → middle item → right detail/actions**. At the established compact breakpoint, navigation drills into a single list then a single detail pane with a Back button. The left navigation is never overlaid or squeezed under a detail pane. The mockup demonstrates both layouts at its responsive breakpoints.

Clean mode shows a selected item’s short purpose and its ordinary actions. Debug, beside Editing in the top-right header, adds IDs, receipt/revision and bounds diagnostics; it adds no extra mutation capability.

The **Player roles** middle pane is the canonical role-definition list. It has a breadcrumb back to the Game setup catalogue; it does not repeat that list in the detail pane. Assignment and self-choice belong to **Players → selected player**, where the affected person and their current role are unambiguous.

## Player roles

### Definition editor

`Game setup → Player roles → Create role` opens one native-style popup with the following server-validated draft:

| Field | Behaviour |
| --- | --- |
| Name / public description | Required name; player-visible explanation, not private lore prose. |
| Availability | **Players may choose** or **GM assignment only**. |
| Player limit | At least one. A role can be a unique character (limit 1) or shared background (higher limit). |
| Starting point | Optional existing **Locations → Points** selection. It is applied on the player’s next first arrival, not when the definition is saved. |
| Respawn point | Optional existing Point independently used after death. Blank preserves vanilla respawn behaviour. |
| Starting items | Add/Edit/Remove normal item stacks. The default grant is once per player for the whole campaign. |
| Private named books | Add/remove existing Books & Lore titles only. Its title/content/rule editor remains exclusively in Books & Lore. |

The mockup labels example roles as examples. It deliberately invents no Quiet Below canon character or lore prose.

### Assignment and grants

The role editor is author-only: play must be non-active and Editing enabled. Assignment is separate:

- A player self-choice flow appears under that player before first start, lists only selectable, non-full roles and rechecks capacity atomically at confirmation.
- A GM assignment flow is also under the selected player, names capacity, and may occur during play under the campaign’s GM authority.
- Reassignment never silently teleports a player, respawns them, or repeats a starting kit. Existing player state is preserved. The new first-arrival/respawn setting applies only at its normal future hook.
- A durable per-player grant receipt prevents duplicate items/books across reconnects, duplicate assignment, and retries. An implementation must choose a conservative full-inventory outcome (recommended: leave the grant pending with an explicit player/GM message; do not drop partial items into the world).

The mockup shows the invariant (“issued once; no kit replay”) and locally simulates capacity/assignment. Capacity includes every assigned account, including offline and departed accounts. The item-stack and named-book controls select actual sample records and retain the parent role draft while their nested pickers are open. The mockup does not pretend to deliver a real item.

A private role book counts as campaign-found on its first actual inventory receipt, preserving the accepted first finder/time. Selecting the entitlement does not itself claim receipt. Book content and ordinary discovery history remain owned by Books & Lore.

## Departure points

### Authoring

`Game setup → Departure points → Edit departure` opens a compact popup:

- bind the exact vanilla/other looked-at block directly, or select an existing **Locations → Block sites** entry;
- select an existing saved **Regions** entry as the aboard Region;
- edit the first warning and the separate point-of-no-return warning;
- set its single-use state (the current Quiet Below ship is single use);
- optionally link a configured Area transition, such as replacing the ship area after departure.

This is an authoring operation and therefore requires non-active play and Editing. The special block itself may be campaign-provided or vanilla. Binding does not create a second special-block registry.

### Player flow

The actual block interaction is available only while play is ACTIVE and the actor is online and physically within the aboard Region. It does not require Editing. Game setup offers configuration only; its **PREVIEW** can never execute a departure. It must show two real, stacked dialogs:

```text
Leave by ship?                                 Point of no return
<first campaign warning>                       <second campaign warning>
Online and aboard: Mira, Arun                  Leaving: Mira, Arun
[Cancel] [Continue]                            [Cancel] [Leave island]
```

The first dialog names exactly the eligible roster: **online, non-departed participants physically aboard**. Offline players with a saved position on the ship and online players outside it are left behind. The actor must be in that roster. There is no individual passenger veto.

The roster/configuration revision is snapshotted at the first dialog. If a player boards/leaves, the binding/region/warning changes, or any other relevant configuration changes before the final click, the final click refuses and requires a fresh first review. This prevents a stale confirmation from carrying someone new away.

On the final action, Core rechecks ACTIVE/no-hold, actor/aboard membership, single-use/configuration/roster revision, and a linked transition preflight. It atomically records the departure receipt, marks every displayed passenger durably departed, and makes them Spectators before a linked ship replacement runs. A departed player is not forcibly disconnected and this slice does not create a new-character engine. A used single-use departure cannot resolve twice. If no enrolled participant remains — including offline enrolled people in the count until they depart — the campaign ends durably. It is not merely a play-window finish or a narrative session reset.

## Area templates and transitions

These are intentionally two separate catalogues. A template is reusable captured content; a transition is a specific intended placement. Combining them would repeat the Chests/Loot Tables confusion where a reusable definition and physical target were difficult to distinguish.

### Template capture

`Game setup → Area templates → Capture template` selects an existing prepared source Region. The source must be bounded, loaded, and valid before capture. Capture stores:

- every block state;
- supported block-entity state; and
- container inventory contents.

It does **not** capture entities, players, or Chronicle ledger identities. It does not force-load chunks. Capture is durable: a future transition uses the captured payload, not a newly read copy of the source world.

### Transition definition and use

`Game setup → Area transitions → Edit transition` chooses one captured template and one existing target Region. Version one requires matching X/Y/Z size and orientation: no stretch, rotation or mirroring. A transition can be applied by a manual GM action, optionally by a linked departure, or by a future registered event; it does not have a session timetable.

Before a manual apply, a confirmation names template and target and warns that the template replaces target blocks, block entities, and container contents. Replaced target contents are intentionally discarded, as confirmed by the organizer. A size mismatch, unloaded/bad target, source-capture absence, use already consumed, conflict, or any non-Spectator player in the target must show a concise actionable refusal, never silently “work.” A configured manual apply is allowed during ACTIVE play without Editing; preparation/test application while non-active requires Editing.

The actual implementation needs a durable operation journal/checkpoint and recoverable status following the existing terrain-operation safety model. It must validate/release any managed special-block records around the replacement; the UI does not bypass block limits or campaign ownership.

## Guards and concise failure text

| Action | Required state | Example refusal |
| --- | --- | --- |
| Define/capture/edit role, departure, template or transition | non-active play + Editing | “Pause play and enable Editing to change this setup.” |
| Assign a role | valid player/role authority, capacity for additions | “This role is already full.” |
| Role kit | first eligible grant and room/pending delivery policy | “Starting items are waiting; no items were dropped.” |
| Begin departure | ACTIVE; actor online/aboard; unused point | “You must be aboard to leave.” |
| Final departure | unchanged roster/configuration snapshot | “The aboard group changed. Review the warning again.” |
| Capture template | bounded, loaded source region | “Load the full source Region before capturing it.” |
| Apply transition | valid captured payload and exact-size, loaded target | “Target is 12 × 12 × 12; this template is 16 × 12 × 16.” |

## Mockup test surface

The paired HTML is deliberately local-only. Its outside-the-product controls exercise phase, GM/player view, roster/actor changes, operation hold, unloaded/mismatched/occupied-target failures, and reset state. Editing and Debug remain in the product header. In the product frame it demonstrates:

- role draft/save/cancel, existing-point pickers, actual sample item-stack and named-book selectors, capacity assignment and no-kit-replay wording;
- canonical role-definition list in Game setup versus assignment/self-choice in Players; author-only role/departure/template/transition changes versus ACTIVE player departure;
- real first and second stacked departure dialogs, roster-staleness refusal, durable local used state, spectator simulation, and the all-remaining-participants end case;
- template capture, selected-template target-size refusal, occupied-target defer, and paste confirmation with container-content replacement warning; and
- Clean/Debug information separation and compact drill-in layout.

The sample controls are not part of the eventual Minecraft UI and make no claims about server authority, persistence, item delivery, or world mutation.

## Implementation questions intentionally held for the architecture slice

1. The package/default YAML resource loader and migration path, because current Core configuration is JSON-oriented.
2. The exact durable role-grant transaction and full-inventory handling; no partial-drop fallback should be introduced.
3. Transition payload limits, block-entity serialization allowlist, conflict/recovery UX, and how linked departure + paste commits atomically.
4. Delete/archive policy for durable captured templates, transitions and departures. The mockup deliberately leaves deletion out rather than implying irreversible history can vanish.

These are implementation architecture decisions, not reasons to add opaque controls to the operator-facing UI.

## Lead verification ? 26 October

The rebuilt prototype passes56 Edge interaction checks with zero JavaScript runtime errors. Root inspected desktop and480px captures, corrected demo-control overlap, made the top confirmation focus its own Cancel, kept keyboard focus inside the visible dialog and prevented the player preview from exposing private setup/other-player roles. The role editor now starts at Name rather than scrolling to its first action button. Syntax and whitespace checks pass. Exact report, script and four reviewed captures: `../reports/roles-departures-design/` (screenshots under `output/playwright/`). The report pins the final HTML SHA256.

Checks include real item/count and named-book selection, draft preservation, claimed/offline capacity, release on reassignment/no kit replay, actual self-choice and GM-only role exclusion, generic role/departure/transition creation, immutable capture, size/occupancy/load/hold preflight, ACTIVE operator apply, two inert stacked warnings, Escape/focus/roster races, partial Spectator departure/offline preservation and whole-group campaign conclusion/resume refusal. Existing setup names are retained as explicit read-only placeholders; this focused prototype does not reproduce every accepted existing editor.

This verifies a local UI model. It does not verify YAML parsing, Minecraft permissions/network secrecy, actual item grants, copied NBT, world mutations, save/crash recovery or campaign persistence. Those have explicit implementation/native acceptance slices in [the contract](CF19_CF21_IMPLEMENTATION_PLAN.md). Reloading/resetting the prototype starts its sample data again; that is not a product reversal action. Initial QA locator/Windows command-shim errors were harness issues, corrected before the passing run.

GM role assignment now has a separate confirmation after the role picker. It pins player/role/configuration/capacity state, rechecks on final accept and leaves the old assignment untouched on cancel or stale state. Availability is deliberately only selectable versus GM-only in v1; no extra Disabled policy is implied. Area transitions are once per definition per campaign, consistent with the implementation contract.
