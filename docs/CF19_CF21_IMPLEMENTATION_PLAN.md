# CF19–CF21: roles, departures and prepared-area changes

26 October 2026. New requested scope after accepted dev54. Branch `design/core-roles-departures-transitions`. This is an implementation contract and UI review package; Minecraft code, deployed worlds and the dev54 pack remain unchanged in this design pass. The organizer's standing UI workflow requires a dedicated design pass and lead review before implementation. The paired [UI design](CF19_CF21_UI_DESIGN.md) and [mockup](design/roles-departures-transitions.html) provide that review surface.

## Confirmed requirements

- **CF19, Player roles:** Core owns reusable definitions, selection/assignment, limits, starting and respawn locations, starting gear and private named-book entitlements. Roles support unique characters and shared backgrounds through a configurable limit. Quiet Below supplies its default definitions in YAML. Operators can create/edit definitions in the editor.
- **CF20, Departure points:** an exact bound block and an existing boarding region define an interaction. Quiet Below's only ship is single use. The actor receives two separately worded, stacked confirmations. Final acceptance takes all **online participants physically aboard**; offline characters are not selected from saved positions. Departed characters become **Spectators**, and participation cannot be restored by ordinary join, role selection or reconnect. If every remaining enrolled participant leaves, the **campaign** ends, not merely this meeting's play window.
- **CF21, Area templates/transitions:** capture a prepared region, then replace a matching target with that saved content. Include supported block entities and container contents; overwritten target contents are intentionally discarded. Do not copy players, entities or Chronicle transaction identities. Ship removal and sinkhole appearance are uses of this general tool.

No session-number timetable is implied. A sinkhole can be deliberately triggered during play or attached to an optional configured condition; there is no new mandatory “session one” event. The organizer still builds the map. Existing research-journal prose and story events remain frozen for the final content pass.

## Architecture and existing foundations

| Concern | Existing implementation to reuse | Necessary addition |
| --- | --- | --- |
| Geometry and binding | `AuthoringSite.Region`, `AuthoringSite.Anchor`, `AuthoringRuntime`, saved Points | Reference existing region/point/block IDs and revisions; no second geometry registry |
| Player membership | `PlayState`, `PlayService`, `PlayRuntime` | Durable departure/first-entry state; all membership paths consult it |
| Starting/respawn movement | `AutomaticPlayerSpawns`, `AutomaticSpawnService`, `SpawnAuthoring.safePolicyDestination`, `MenuPlayerDestinations` | Trusted role-policy resolution and a coordinated first-entry transaction; do not impersonate an author to call editor-only methods |
| Gear | `InventorySnapshot`, registry-aware item codecs, cap enforcement | Starter-kit entitlement, full-fit preflight, durable inventory grant and recovery |
| Books | `BookDefinition`, `BookDeliveryRuntime.stackJson`, discovery/keeping ledger, `BookGifts` patterns | Role grant uses that ledger and real inventory receipts; no second discovery system |
| Confirmations | `ActionConfirmations`, connection-bound tokens, native screens | Distinct first/final warning content and roster/configuration pins |
| World mutation | Quiet Below `StageService`/`StageStore` as a design reference | New Core template/transition service; legacy stages are limited to 64 natural-block-to-sculk changes and reject block entities |
| Operations | `RuntimeHolds`, exact pre/post evidence and atomic stores | Region reservations, bounded mutation work, transition/departure recovery before play resumes |
| Editor | `CoreMenuModel`, `CoreMenuServer` frames, native `CoreMenuScreen` forms | Game setup catalogs, Players assignment/choice, typed payloads, native selectors and confirmations |

`AuthoringCatalog.Role` is existing **map requirement** metadata. It is not a player role. New types use an explicit `PlayerRole` name and a separate catalog. Existing map uses retain compatibility and existing presentation; do not reintroduce a generic Roles tab under Locations.

Core contains no ship, island, sculk, sinkhole or ancient-character rules. Quiet Below registers content and world-change guards through typed extension points. It must not hide asynchronous block/inventory mutations inside the current flag-only campaign-event handler.

## CF19 — player roles

### Definition and configuration

Proposed authoritative file: `config/chronicle/player-roles.yaml`. Each campaign may register a bundled YAML resource, initially `data/quiet_below/chronicle/player-roles.yaml`. On first creation, materialize registered defaults; thereafter preserve the operator's file. Default upgrades must not overwrite edited roles, restore deleted roles or create assignments. Importing additional defaults is an explicit future/admin operation, not a silent reload effect.

The normalized definition contains stable ID, display name, public description, selectable versus GM-only state, capacity, optional revision-pinned starting/respawn Points, starting item definitions and named-book IDs. No raw world coordinates are required in the campaign's defaults. Point references are chosen in the editor against the current map; missing/stale points are shown as unresolved, never silently rebound. Blank points preserve the corresponding vanilla behavior.

The editor and YAML operate on the same validated catalog. Save by expected revision/fingerprint with an exact previous-file backup and atomic replacement. External edits, invalid references, duplicate IDs, failed writes and stale editor frames retain the last good catalog and return an actionable error. Manual YAML changes require explicit reload in non-active editing; changing a role never moves players or rewrites their inventories. UI saves may format YAML; comments are not promised to round-trip.

Use a data-only YAML parser, not a handwritten subset or Java object construction. The implementation spike will pin and test SnakeYAML Engine against Java 21/Fabric, preserving its license/notice in the artifact. Reject custom tags, duplicate/recursive keys, aliases/merge keys, multiple documents and environment/file includes. Bound bytes, nesting, nodes and string lengths before conversion. Published [Engine API](https://javadoc.io/doc/org.snakeyaml/snakeyaml-engine/3.1.1/) and [project source](https://github.com/snakeyaml/snakeyaml-engine) are the research references checked on 26 October; no dependency is installed or version locked by this design document.

Initial engineering targets: at most 128 roles, capacity 1–512 consistent with `PlayState.MAX_PARTICIPANTS`, and a kit that fits within 36 normal inventory slots when empty, including split stacks and books. Reuse existing identifier/item-component limits. Final limits require parser/item tests; do not publish unmeasured capacity promises.

### Assignment and lifecycle

- Definitions live under **Game setup → Player roles**. Actual GM assignment lives under **Players → selected player**. The player sees their own selectable-role chooser; GM-only definitions/private book details are not sent to other players. Roles do not automatically overwrite character cards or expose assignments publicly.
- One current role per account in this campaign. Capacity is derived from assignments, not a second mutable counter. Online and offline assignments reserve seats. Lowering a cap below assigned count, deleting an assigned role and racing for the final seat are rejected. Departed assignments remain historical/reserved until a GM explicitly clears the assignment; clearing a role does not clear departure.
- Self-choice is available before first active entry. GM reassignment can occur during play after confirmation, subject to capacity and no operation hold. Reassignment releases the old seat and updates future policy, without granting another kit, moving the player or resetting first-entry/book receipts. No multiple-character/archive engine is introduced here.
- Definition edits affect future unclaimed starts and future respawns after current validation; in-progress grants pin their exact definition revision. Changes do not replay a completed start. A point edited elsewhere remains stale until deliberately reselected.
- Existing per-player spawn settings are preserved as explicit overrides. Role defaults must be visibly distinguishable from a manual point or explicit Off. Extend effective-policy resolution; do not maintain two independent automatic teleport schedulers. Existing arrival receipts remain authoritative, and resetting a movement receipt never resets a kit grant.
- Offline assignment is limited to known accounts in the campaign/cache; no guessed name-to-UUID lookup or arbitrary unseen roster entry is required in v1. Applying spawn/kit effects waits for a real eligible online player.
- On upgrade, existing participants/arrival histories are marked already started without teleporting or granting items. Assigning a role to an accepted existing player changes future respawn policy only. New campaigns/players receive the first-entry workflow normally.

First entry means **entry into active participation**, not the first meeting, every login, every death or every session start. A new player who chooses a role while play is already active can start on the next eligible check; they do not need to wait for another meeting. Starting items are granted once per account/campaign. Respawn gear is not added implicitly; this request supplies an independent respawn destination.

### Coordinated first entry and private books

Preflight the resolved destination, current player/role/revisions, full item fit and all book definitions before changing anything. Reuse loaded-only safe destination validation: an unloaded, stale or hazardous destination defers entry with a clear remedy; it is not force-loaded or replaced with an invented point. No drops or inventory overwrites when supplies do not fit; keep an unclaimed entitlement pending and explain that room is needed. Existing player inventory/components are preserved.

A single first-entry coordinator owns movement and the kit. Persist the immutable operation and conservative claims before effects; acquire a Core operation hold, then perform the exact planned changes on the server thread, save player data, record actual book discovery and complete receipts. Do not run old arrival movement and a separate role-kit movement independently. Save failures before mutation issue nothing. Any uncertain post-claim result holds the operation for inventory/position inspection; it does not blindly replay or automatically reset a once claim. Recovery can recognize exact saved post-state, but ambiguous outcomes require deliberate GM resolution.

Role books are explicit personal entitlements, not percentage loot rolls. They use fresh Core-managed book provenance and can bypass automatic chest-spawn limits as deliberate GM grants already do. **Receiving the book counts as found**, consistent with the accepted inventory-receipt rule; first finder/time are preserved and any keep request completes. A shared role may give the same named introduction once to each entitled player. Reassignment does not repeat private books. Titles/content/rules stay in Books & Lore; roles store references only. This is a design assumption available for correction during review, not a new separate discovery policy.

## CF21 — captured templates and world transitions

### Storage and authoring

Keep separate **Area templates** and **Area transitions** Game setup entries. Templates are immutable captured payloads; transitions reference a particular template revision/hash and a target Region/revision. Existing Regions owns all bounds and the accepted pos1/pos2/Continue-in-world workflow. A changed region never silently resizes an existing template or active operation.

Capture requires non-active Editing, bounded fully loaded source chunks and a stable definition. Read world data only on the server thread with a per-tick budget, under an operation hold. Store relative block positions, a palette of registry block states and an audited block-entity payload. Validate capture consistency before publishing its immutable hash. No template ID appears as usable until its complete payload is durable. Source edits later do not change the capture. A new capture creates a new version; active receipts retain their original hash.

Proposed storage: `world/chronicle/area-templates/` immutable payloads plus an atomic catalog, and a separate transition-definition store. Do not copy Core metadata files, assignments, managed-instance UUIDs, loot/book transaction markers or ecology ownership from the source. Never deserialize arbitrary filesystem paths from a template.

Initial engineering targets, to validate before shipping: 262,144 cells, 512 blocks per axis, 64 intersected chunks, 64 MiB total decoded template data, bounded per-block-entity data and at most 64 templates/128 transitions. These are reject-before-allocation limits, not a reason to truncate a region or ignore unsupported blocks. A separate finite capacity rehearsal must determine practical heap, disk and tick budgets.

Version one transitions are **once per definition per campaign**. There is no automatic reset/repeat timer; a further deliberate placement requires a new definition and a fresh before-state review, retaining the original receipt. Version one preserves orientation and requires exact X/Y/Z dimensions. No scaling, mirroring, arbitrary schematic imports or entity copying. Start with audited vanilla container/sign support and explicit adapters for supported project blocks. Unsupported block-entity types and unresolved vanilla loot-table containers reject capture by name; capture must not roll/generate source loot. Serialized positions are rewritten relative to the actual target. Container inventories, including supported nested items, obey item/enchantment limits.

Core-managed lore provenance must not be cloned from a source inventory. Reject such captured content with a direction to use a named lore/chest definition instead; plain written-book content remains ordinary content. Quiet Below's Relic Heart is not cloneable. Station/sculk adapters validate quotas and normalize transient power/vitality without cloning campaign ownership or bypassing Heart protection.

### Applying a transition

An operator may apply a preconfigured transition during ACTIVE play without Editing. During preparation, explicit non-active Editing permits a reviewed paste. Definition/capture edits remain non-active only. The same execution service is called by departure or a separately configured world-action trigger; there is no hidden paste inside an audio preview.

Preflight all target chunks, dimensions, registry data, current bindings, expected before-state, quotas and campaign policy. V1 requires the target to be clear of **non-spectator players**; refuse/defer with an actionable message rather than enclosing or dropping players during a partly applied operation. Sinkholes can appear during active play when their area is clear. For ship removal, the already-committed passengers become Spectators before block replacement; any other non-spectator in the footprint prevents departure at preflight. Entities are not copied or relocated by v1.

The final review explicitly states that target blocks, block entities and container contents will be replaced, discarding old contents. Close all viewers of every affected supported container before the before-snapshot/reservation (reuse the existing chest-viewer closure pattern), or defer if closure cannot be established. Snapshot raw target block-entity NBT, including an unresolved vanilla loot-table ID/seed, without opening or rolling its inventory. Reject partial double chests or other supported multi-block structures crossing the reviewed bounds; do not mutate a neighboring container outside the target. Snapshot their exact before-state and the exact template after-state durably before effects. This explicit operation authorizes replacement of that reviewed target; it does not confer permission to overwrite subsequent unexpected edits.

Acquire a single operation hold/region reservation, then apply bounded writes on the main thread. Compare each current cell against its planned before/after state before proceeding. A known after-state is an idempotent completion, a known before-state may be applied, and any third state becomes a conflict hold. Receipt/checkpoint progress must not get ahead of durable chunk data. Save/flush affected chunks before completing the operation and releasing its hold. Never restore the entire region blindly on failure. Keep original evidence and an explicit recoverable operation status after interruption.

Capture/apply must not force-load chunks. An unloaded target defers; a real unload during mutation holds progress and awaits normal loading/revalidation. Startup installs holds for committed unfinished operations before gameplay, chest refill or book keeping runs. Validate bad/truncated journals and preserve them, without “repairing” evidence automatically.

### Cross-service integration is part of CF21

These are requirements, not optional post-release cleanup:

- Reconcile replaced/added supplied blocks through managed-instance reservations and confirmed world receipts. Preflight quotas using removals plus additions; failure cannot bypass unloaded-instance limits or free a reservation whose outcome is uncertain.
- Preserve sculk ownership journals through the scenario's normal observed-write hooks. Transitions may not erase/clone the Heart or silently regenerate an eradicated network. Refuse overlap with active ecology maintenance/eradication or another reserved operation. No campaign rules are hardcoded into Core.
- When a target contains a managed lore book, record its pending removal in the shared ledger under the transition operation, then confirm destruction only after durable target save. Keep original discovery history. Book keeping remains held until removal/provenance is resolved, so a crash cannot make a second “kept” copy while the first remains in a chest. This integration must precede enabling arbitrary managed-chest replacement.
- Existing target chest bindings remain explicit metadata and are revalidated against the resulting block; source bindings are never copied. If a container is deliberately removed, its binding retirement is an explicit operation effect with durable receipts. Other map-use/anchor references must remain valid or have an explicitly authorized retirement in the prepared operation; otherwise preflight refuses. The consumed departure may retire its own exact pinned helm consumer after the world receipt, retaining its location/used history. This is not permission to erase unrelated references or bypass managed-block reconciliation. Later ordinary refills use the existing scheduler/loot table, not a second template timer.
- World-change Events use a typed action operation and completion receipt. Existing message/sound cues remain unchanged. Preview outlines the target and describes the change without mutation; completion is recorded only after the world operation succeeds. No default timer, chapter or first-session schedule is registered.

## CF20 — irreversible group departures

### Binding and confirmations

Definitions contain an exact block Anchor, an existing boarding Region, localized first/final text, single-use policy and optional transition reference. Bind a looked-at vanilla block directly in the departure editor, or choose an existing Block site. The shared Locations registry receives the one anchor; it does not acquire duplicate limits/settings menus.

Only occupied ACTIVE, unheld play permits the actual interaction. The actor must be an enrolled, alive, non-departed, non-spectator, non-author participant inside the boarding region, near the exact valid block. The roster uses online eligible participants physically inside that region, including the actor. Game masters are not added merely because they have operator permission. Offline participants and players outside remain behind.

Stage one explains taking the only ship and names the current passengers. Continue opens the separate point-of-no-return dialog above the first. Cancel/Escape on the second returns to the first; underlying controls cannot be clicked or keyboard-activated. Cancel is focused initially. Final text must explain both individual departure and the campaign-ending case. No passenger-by-passenger veto or additional third dialog is added.

Server tokens are connection-bound, expiring, one-use and pin actor, exact passenger UUID set, block/region/configuration revisions, membership revision/predicted campaign-ending consequence and linked transition revision/hash. Revalidate phase, hold, range, actor, every passenger, roster equality and target preflight at both stages and commit. Any change requires a fresh first review, including a new boarder; do not silently take a different group. Concurrent final clicks resolve at most one operation. A preview cannot execute this flow's durable action. Consume only one main-hand interaction for a bound departure block; do not also open a vanilla container/station UI or open duplicate offers from the off hand. After a committed action changes the actor to Spectator or concludes play, the success response must not be replaced with a fresh ?not eligible? gate error.

### Commit, recovery and ending

Final acceptance first creates one durable departure intent containing the roster, spectator effects, prepared optional transition ID and `concludeCampaign` obligation computed from the fully revalidated membership/departure ledger. This is the irreversible boundary. Before that boundary, all linked-transition checks and reservations must succeed. Failure leaves everyone participating and the ship intact. Never let “missing transition” mean “no transition.”

After commitment, one coordinator owns the operation: hold play, persist departed status for the exact roster, suppress participation/automatic respawns/role starts, set Spectator, save player data, apply the staged ship transition, persist the pinned campaign-concluded outcome when required, confirm all receipts, then release only its own hold. The departure cannot become complete or release its hold while that conclusion obligation remains pending. A crash or disconnect after commit cannot undo departure or select new passengers. Effects still pending for a disconnected committed passenger are applied on reconnect before ordinary join/spawn logic. Interrupted/uncertain world changes retain the hold and exact operation; no new departure is permitted.

Membership APIs, `/chronicle join`, role assignment, manual participant toggles and automatic spawns consult durable departed status. Changing a gamemode manually is not permission to re-enroll a departed character. No normal UI reversal is provided; operator repair of corrupted/interrupted data is a separate recovery operation with evidence, not a gameplay “return” button.

After removing the committed passengers, evaluate **all enrolled non-departed participants**, including offline participants. Offline people left aboard were not transported and therefore still prevent an all-departed ending. If none remain, persist a campaign-concluded outcome and close play. `CampaignService.end()` alone is insufficient: it currently permits the next meeting to start. The new durable outcome must also gate start/resume/join/role starts after restart, while leaving non-active operator authoring available. Do not implement permanent game-over by abusing an unresolved-operation hold.

Departure does not destroy the Relic Heart, erase books/inventories, clear Altar receipts or pretend the ecology was eradicated. It ends participation; a whole-group exit concludes this campaign by departure. Introducing a replacement character or resetting a concluded campaign requires separate future design.

## Commands and UI authority

Typed native-menu requests are the primary interface. Suggested fallback groups are role listing/self-choice/GM assignment, template capture from a saved region, transition preview/apply/status/recovery, and departure status. Final command spelling follows the approved UI and existing `/chronicle`/`/chronicles` aliases. Registered choices must have server completion; ordinary players are never asked to invent a namespace or filesystem path.

All fallbacks invoke the same services, revision checks, confirmations and operation receipts. A command cannot bypass capacity, give another starting kit, replace an unreviewed region or clear departure/conclusion. `Preview` is read-only presentation; an apply requires explicit confirmation of the exact current plan. Do not expose an undocumented force-success or reset-history action to make tests pass.

## Quiet Below integration (QB14)

The scenario registers its YAML role defaults and its default ship wording/single-use spectator policy. Configure starting/respawn Points and book references through Core. Final named cast, private-character prose and balance remain part of the already deferred content pass; do not repurpose the five ancient-researcher recipe journals as every character's starting lore.

The map builder supplies key geometry only: desired role Points, ship helm, boarding region, ship footprint and the prepared/target sinkhole regions they choose to use. Capture an empty-water replacement for the ship with exact dimensions. These are explicit finite world changes; no sculk routes, focus blocks or spread painting are introduced. The ecology still discovers its own physical path.

## Implementation sequence and acceptance

| Slice | Result | Required verification |
| --- | --- | --- |
| Design gate | Coherent mockup plus this contract; confirmed decisions incorporated | Dedicated UI design, lead/independent review, browser interaction + desktop/480px visuals, organizer workflow review |
| CF19a | Safe YAML registration/config/catalog and role CRUD/assignment authority | Parser limits/tags/aliases/duplicates, file conflicts/failed saves, capacity/offline/last-seat/reassignment tests, no legacy auto-grant |
| CF19b | Coordinated first entry, effective spawn overrides, once kit/private books | Real first entry during active play, inventory full/pending, unsafe/unloaded/stale destinations, reconnect/death/reassignment, discovery/keeping consistency, save interruption/restart |
| CF21a | Immutable capture + separate transition definitions/typed UI | Exact blocks/NBT/container round-trip, missing/unsupported types, dimensions/load/cap limits, no source mutation or metadata/identity cloning |
| CF21b | Main-thread bounded paste, cross-service receipts and recovery | Fault injection before/after saves, open container viewers, raw unresolved target loot, chunk unload, intervening edits, managed limits/Heart/sculk hooks, lore removal/keeping, own-anchor retirement, cancelled/duplicate apply, restart mid-operation |
| CF20 | Two-stage ship interaction, durable group Spectator departure, campaign outcome | Actor/roster/config races, offline exclusion, double final click, missing/bad transition, spectators before ship removal, disconnect/restart partial effects and the gap after Spectator save but before conclusion, no rejoin, true all-departed closure |
| QB14 | YAML templates/default warnings and optional sinkhole/departure world-action links | Disposable complete role→entry→sinkhole→ship scene; no fixed meeting schedule, no game/instance-specific logic leaked into Core |
| Combined review | One matching client/server review for these related features | Full Java/Python checks, native ordinary-player/operator flows, exact world preservation/backup, independent review; one review client unless a real earlier decision blocks work |

Implement one dependency-ordered slice at a time and integrate/check it before the next. The ordering builds area transitions before the final linked ship action, so the irreversible departure never depends on an unimplemented paste. Do not stop for a merely ready internal slice; stop for the actual UI review gate, unresolved product decisions or a necessary combined gameplay review. Keep branches/commits/evidence and do not merge main automatically.

Native tests use disposable synthetic worlds, no authentication weakening or persistent forced chunks. Test both a ship carrying only some online participants and one carrying every remaining participant, with offline/outside exclusions. Add an explicit finite template-volume/block-entity/heap/tick rehearsal; the old 64-cell stage test and sculk-specific scale tests do not certify this new service. Preserve user worlds; completed disposable fixtures may be retired after stopped-path/lock checks with reports retained.

## Independent design review

The read-only architecture review identified three concrete gaps before implementation: active container viewers/raw unresolved target loot, the durable all-departed conclusion obligation, and intentional retirement of the ship's own consumed anchor. This revision incorporates all three, preserving existing receipts and requiring refusal for other unplanned binding changes. The dedicated UI design and lead browser/visual review now pass56 local checks with no JavaScript errors; the organizer workflow review remains the gate. This is not a claim of installed functionality.
