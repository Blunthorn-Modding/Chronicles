# CF19-CF21: Player Roles, Departures and Area changes UI design

**Status:** design-review artifact only. The paired [interactive mockup](design/roles-departures-transitions.html) does not change Minecraft runtime, configuration, worlds, or deployment.

## Product structure

The three tools are direct operator destinations, not Game setup children:

```text
Session / Players / Events
--------------------------
Chests / Loot Tables / Books & Lore / Regions / Locations
Player Roles / Departures / Area changes / Game setup
```

`Game setup` retains its accepted entries: Block limits, Enchantment caps, Ingredient enchanting, Lasting bargains, Heart weakness, Sculk ecology, Spread mode, Sculk vitality, Sculk ownership, Crop growth, Local healing, Local Hunger and Local Darkness. This focused mockup represents them as unchanged placeholders rather than implying that they disappeared.

Desktop remains left destination, middle record list, right selected description/actions. Compact layout drills into one pane at a time and never overlays the navigation. Clean shows the selected description, ordinary actions and the minimum Start/End availability needed to choose a valid action. Debug adds IDs, revisions, bounds, receipts and operation facts. Advanced destinations and Debug are GM-only; player self-choice stays under Players.

`Player Roles` is not the existing `AuthoringCatalog.Role` map-requirement term. `Regions` remains the one Core geometry registry. An Area change creates one ordinary Core Region at its first successful save; there is no template registry, transition registry, duplicate bounds model, or separate source/target definition.

## Player Roles

Definitions support one unique seat or a shared capacity. Capacity is derived from assignments, including offline and departed accounts. One account has at most one current role. A role definition contains:

- name and public description;
- player-selectable or GM-only availability;
- capacity;
- optional existing starting and respawn Points;
- normal item-stack entitlements; and
- references to existing named Books & Lore records.

Create/edit/delete requires non-active play with Editing. The editor uses actual sample item and book selectors; nested pickers retain the unsaved parent draft. Limits cannot be lowered below assignments and an assigned role cannot be deleted. Empty and last-record creation/deletion remains safe.

Assignment remains under `Players -> selected player`. Self-choice exists only before first start and lists selectable roles with a seat. GM assignment can occur during play when no operation hold conflicts. It has a separate confirmation that pins player, role, capacity and configuration state. Reassignment releases the old seat and changes future policy only: it does not teleport, respawn, replay a kit or repeat a private book.

The mockup does not claim to issue inventory. An actual first receipt of a role book counts as found and preserves first finder/time; content editing remains in Books & Lore.

## Departures

The direct `Departures` catalogue configures a one-use bound interaction with:

- one looked-at or existing Block-site anchor;
- one existing boarding Region;
- distinct first and point-of-no-return warnings; and
- an optional `Area change + desired Start/End state` link.

A missing/stale linked Area change or missing desired snapshot is unresolved and blocks departure; it is never treated as no link. Warning review in the catalogue is inert. The actual demo flow starts only from the outside-the-frame helm interaction.

The actor must be an online, enrolled, alive, non-Spectator, non-departed, non-editor participant aboard and near the exact bound block during ACTIVE unheld play. The roster contains only online eligible participants physically aboard. Offline and ashore accounts remain behind; there is no passenger veto.

The two confirmations are genuinely stacked. Cancel has initial focus; the first dialog is inert while the second is open; Escape closes only the top dialog and then the first. Open, Continue and final acceptance each revalidate live phase, hold, actor, exact roster, configuration, one-use state, linked Area change revision and desired snapshot revision.

Final acceptance completes the full linked-action preflight before mutation, then marks exactly the reviewed passengers departed and Spectator before applying the linked saved area state. A new durable apply operation is conceptually created even if that same saved state was applied earlier. The departure record becomes used once; the Area change does not. Non-roster non-Spectators in the area block the operation. If no enrolled non-departed account remains, including offline accounts, the durable campaign-concluded flag blocks ordinary start/resume/join/role-start until a separately designed reset. The mockup's Reset control is explicitly demo-only.

## Area changes

### One record, one bounds set, two saved states

An `AreaChange` owns one Core Region/bounds set plus independently saved `Start` and `End` snapshots. Both states use the same exact bounds and orientation. Either state may be missing, captured first, replaced later, or applied repeatedly. Each intentional application gets a new operation ID; capture and application history survive later captures/applications.

A saved state contains blocks, supported block entities and container contents. It excludes entities, players and Chronicle ledger identities. Application replaces current contents inside the bounds and intentionally discards overwritten container contents. Normal loaded-bounds, clear-target, managed-record and recovery checks remain required by implementation; the prototype only demonstrates their operator-facing outcomes.

### Create and initial in-world capture

`+ Create new area` requires non-active play with Editing. Its popup asks for a name and whether authoring begins with Start or End. Choosing either hides the Chronicle menu and enters a simulated world view:

1. A tagged temporary Debug shovel is selected in the main hand without overwriting or dropping the held item. If no safe hotbar/inventory slot exists, refuse that tool grant privately and keep command selection available. Only the exact draft-token tool is removed on completion/cancel/disconnect; stashed stale tools remain inert, and real shovels are untouched.
2. Private chat explains left-click for pos1 and right-click for pos2.
3. Existing `/chronicles author region pos1` and `/chronicles author region pos2` commands route to the active typed AreaDraft. The noun remains singular `region`.
4. The selection and private outline persist while the operator moves.
5. `/chronicle area change save` atomically creates the ordinary Core Region, publishes bounds and captures the selected Start/End state.
6. `/chronicle area change cancel` removes the temporary tool, outline and AreaDraft without publishing a record.

A failed save retains the typed draft, shovel, positions and outline for retry. It must not publish a partial Area change or Region. Draft exclusion is bidirectional: refuse AreaDraft creation during an ordinary RegionDraft, and refuse ordinary region create/edit through commands or menus while an AreaDraft is active. Preserve the previous ordinary selection/preview and restore it only if its identity is still current. Position commands route only to the active typed draft; stale tokens cannot mutate a later draft. Another operator's draft remains independent. The prototype explains this contract but does not simulate multiple operators or the entire RegionDraft service.

### Reopen, recapture and apply

Reopening a record offers one `Save current area state...` action. Its popup chooses Start or End, then confirms replacement/fill for the unchanged bounds. Recapture may run during ACTIVE play for an authorized operator when exact bounds are loaded and no conflicting hold exists; it is a read-only world capture under a temporary hold. A failed capture leaves both saved states and the retry draft intact.

`Apply Start...` and `Apply End...` are independently disabled when their state is missing. Manual application may run during ACTIVE play without Editing, or in non-active Editing. The confirmation warns that current blocks, supported block entities and container contents are replaced. Unloaded bounds, another hold, an occupied target, stale/missing state or simulated durable failure refuses without changing the Area change. Start and End remain repeatable, including after prior capture or application.

### Delete/retire

Delete requires non-active Editing and opens one popup with three explicit outcomes:

- Apply Start, then delete;
- Apply End, then delete; or
- Leave terrain unchanged, then delete.

Before confirmation, list the linked departures/events that retirement will disable, or explicitly state that there are none. Missing/stale states disable their apply-and-delete choices. A failed application retains the Area change. Leave unchanged performs no terrain write. A committed operation or pinned draft blocks deletion. Successful retirement makes linked departures/events explicitly unresolved/disabled; it never converts them to no link.

Replacing or retiring a Core Region is blocked while a committed operation or pinned typed draft uses it. Otherwise the Region change atomically marks dependent Area change captures stale so they cannot silently apply to different bounds.

## Guards shown by the mockup

| Action | Required state | Failure behavior |
| --- | --- | --- |
| Create Area change / delete | non-active + Editing | No draft/retirement is published |
| Issue selection shovel | safe free slot, exact tagged draft identity | Private refusal without overwrite/drop; command selection remains available |
| Save initial AreaDraft | both corners, loaded bounds, valid typed draft | Draft, tool, positions and outline remain |
| Recapture Start/End | authorized GM, loaded unchanged bounds, no conflicting hold | Existing snapshots and retry draft remain |
| Apply Start/End | saved current state, loaded clear bounds, no hold | Record and current-state marker remain |
| Begin/finalize departure | ACTIVE, eligible actor, unchanged roster/config/link | Fresh warning review required; nobody departs |
| Delete with applied state | non-active Editing and successful apply | Failure retains the Area change |

## Mockup test surface and limits

Outside-product controls exercise phase, GM/player view, all-remaining aboard, roster/actor races, operation hold, unloaded bounds, extra occupant, save failure, apply failure and local reset. They are not product controls.

The interactive HTML demonstrates direct navigation, role CRUD/assignment, one Area change catalogue, in-world typed bounds capture (actual left/right clicks on a simulated block grid or typed commands), persistent outlines, failure retention, Start/End recapture/apply/delete choices, linked departure preflight, two stacked warnings, Spectator departure and durable local conclusion. It performs no Minecraft write, inventory grant, Region mutation, persistence or network operation.

Runtime operation journals, supported block-entity allowlists, finite payload limits, Region dependency receipts and crash recovery remain implementation architecture work. The design makes their failure behavior visible without claiming they already exist.

## Verification of this revision

Root Edge CLI checks pass96 assertions with no JavaScript errors, covering preserved role/departure flows and the new direct-navigation/paired-area workflow. Six desktop/480px captures were visually inspected. Syntax, documentation links and diff whitespace pass; independent read-only design review is clear. Evidence: `../reports/area-changes-design/report.json`. The earlier56-check template/transition report is superseded for these changed flows. Organizer workflow review precedes native implementation.
