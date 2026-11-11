# Deferred authoring UI design

Status: dedicated design reviewed by the lead before implementation. The decisions below describe the design baseline; the implementation refinements in this note supersede earlier alternatives. Native verification is recorded separately.

Implementation refinements: Encounter groups use their own Advanced page, preserving the accepted Events Game Events / Region changes filters. Member editing uses an allowlisted entity picker and bounded counts; arbitrary NBT is not supported. The Altar second sheet uses More prayers..., four prayer buttons and Back/Close. Scenario capture includes roles, books, loot tables, Event templates and encounter compositions; map and Altar settings remain outside this export. Export uses a controlled server path and exact preview digest, then the offline build/import tool. Event history now has an explicit confirmed Game setup action and operator command; pending/uncertain work is retained.

## Scope and content boundary

Deferred native-editor work covers reusable encounter groups and an Event spawn action; Event sources for role assignment and `RoleFirstEntry COMPLETE`; bounded history status; additional Quiet Below Altar offers; and portable Scenario-content export.

| Map-owned, stays with the map/world | Scenario-mod-owned, portable content |
| --- | --- |
| Regions and saved Region states; Points and Block sites; chest bindings; map/game settings; placed station references; coordinates, dimensions, geometry, revisions, runtime receipts and operation history | reusable role definitions/supplies/lore; books; loot tables; Event templates; encounter-group templates; permitted Altar default configuration |

Map setup stays in Core's existing map pages and is independent of a scenario mod. It is not a preset or export target. Portable content may retain a declared logical map-binding name, but never a world-specific ID, coordinates, dimension, UUID, revision, state payload, geometry, or runtime data. On a target map, normal Region/Location editing binds each name before the default can be enabled.

## Placement and navigation

Preserve the existing three-column Core editor: navigation left, list centre, details/actions right; create controls above lists. Do not add duplicate Region, Location, map-preset, or narrative-scheduling flows.

* **Encounter groups** is an Advanced page. It owns group definitions; the existing Event editor alone attaches spawning.
* **Player Roles** remains the role-definition owner. Events reuse its role picker.
* **Regions**, **Locations**, **Chests**, saved states and **Game setup** stay map-oriented and never mention export.
* When Quiet Below advertises portable content, the header gains a compact, explicit **Scenario content…** action beside the Clean/Debug and Editing controls, subject to current compact-header overflow rules. It opens the popup below; it is not an implicit mode/target switch.

Clean/Debug stays display-only. Opening Scenario content changes no live data.

## Scenario content capture and export

`Scenario content…` opens one server-supplied native popup, available to an authorized operator under existing non-active Editing/no-hold/token/revision gates. It captures the **current effective reusable definitions** from their ordinary editors—not merely installed defaults—so currently saved roles, lore, loot, Events, encounters and permitted Altar defaults are included. Live assignments, histories and state are not.

```
Scenario content
Capture current reusable content for the next build.
Map setup stays with this world.

Roles 4 · Lore 6 · Loot 3 · Events 8 · Encounters 2
Map data excluded: Regions, Locations, Chests, saved states, settings.

[Export content…]                         [Close]
```

The deterministic counts come from the server snapshot at popup open/refresh. **Export content…** opens a review: “Export current reusable Scenario content for the next build. This does not alter this map, the installed mod, or live play.” The export destination is server-admin-controlled or selected through an existing secure server workflow; no native-client path field exists. Preflight failure disables Export with the exact reason; Close remains usable.

The output is a build input, never a runtime restore. It excludes UUIDs, player identities, active clocks/session values, receipts, role/Event histories, world path, map data, jar hashes, and live Altar exhaustion/consumption. Roles retain reusable supplies/lore but export starting/respawn bindings as explicitly **unbound**; live roles are unchanged. Events and encounter groups may export declared logical binding names only.

Imported Event templates start **Disabled — review map bindings**. They cannot arm automatically or create a calendar. An author binds names through ordinary map UI, reviews the Event, then deliberately enables it. Imported unbound role destinations similarly remain inactive. Export never substitutes a Region/Location or takes coordinate snapshots.

This popup owns no duplicate four-page Scenario editor or local content draft. Existing editors remain the sole reusable-content editor. On stale/revoked/active/disconnected state, Export locks with the server reason while Close remains usable; failed export retains the popup and feedback; late replies never reopen it.

## Encounter groups and Event spawning

**Advanced → Encounter groups** is an authoring list, not an in-world combat control. **Create encounter group…** appears above the centre list. Clean view shows concise label/description; Debug may show already-authorized non-secret facts.

The native **Encounter group** popup uses `popup/background`, native font, 18 px buttons and 22 px pitch. It has `Basics 1/3 → Members 2/3 → Spawn 3/3` with the existing 26 px arrows.

* **Basics:** name, generated/editable creation ID, optional organizer description; edit IDs remain immutable.
* **Members:** **Add entity…** above up to four rows opens `CatalogAddPopup`, backed by the server's allowed-entity catalog with existing search/pages/disabled reasons. Each row has summary, **Edit**, **Remove**. Edit supplies count and only server-advertised bounded entity configuration/NBT. There is no free-form command or promise that arbitrary NBT is valid. The server contract later defines allowlists, bounds, forbidden data and safety.
* **Spawn:** explicitly choose Region or Location, never both implicitly. Both reuse existing typed Core pickers; there are no coordinates, raycasts or duplicate geometry workflows. An absent compatible target disables its choice with the server reason. Export converts a selected target only to its declared logical binding name.

Save validates nonblank ID/name, at least one member, supported bounded member fields, duplicate policy and valid required target. It never spawns. Cancel writes nothing; rejected saves retain local draft and server reason.

In existing Event **What happens**, `Add action…` gains **Spawn encounter group**. It is not a category, trigger, timer, automatic schedule or runtime button:

```
Spawn encounter group
Encounter group: Choose…
Spawn target: Use group target / Choose Region… / Choose Location…
[Done] [Cancel]
```

Group and map choices reuse their existing pickers. `Use group target` is enabled only for a compatible target. The action remains local until Event **Save edits**; it never pre-spawns/tests/schedules/clears/despawns. Private Event preview remains text/sound-only and expressly excludes spawning. Queueing, safe placement, limits, cleanup, retries, receipts and history are server work outside this design.

## Role Event sources and histories

**When** remains one registered-source picker with:

* `Role assigned` → `Role: Choose…` (existing role-definition picker);
* `RoleFirstEntry complete` → `Role: Choose…` (existing role-definition picker).

`RoleFirstEntry complete` means the role's first-entry workflow completed its gear, lore and destination work; it is not Region entry, which already has its own source. Switching sources retains input until intentional compatible selection; unavailable pins remain visible/removable. Sources do not create/assign roles, create map data, claim players or make a schedule.

History maintenance is a future bounded operator/Debug status surface, never a player story authoring flow. It exposes no automatic rearming, no silent deletion, and no exported player identity. Capacity blocks show the authoritative reason. Archive/compact/purge needs a separate durable review, retention policy and receipt.

## Additional Ancient Altar offers

The player-facing Altar remains the existing compact inventory-free mysterious prayer UI. It gains no worksheets, prices, counts, slots, numeric values, explanatory tooltips or public diagnostics. Eligible extra buttons use only cryptic labels:

* `Pray for swift mending`
* `Pray for clear sight`
* `Pray for a quiet harvest`
* `Pray for sheltering grace`

The campaign-private mapping to repair/sight/harvest/emergency concepts stays hidden. Existing pending/unavailable/exhausted behavior applies without explaining why. Since the 224×132 screen has two offer rows, implementation must receive a separate geometry review; a second identical offer sheet entered through `Seek another answer…` is the safe option. Never squeeze six choices into clipped rows.

Any later GM configuration belongs in Quiet Below Game setup and Debug/operator context. It may show terms only after transaction/security review, never player-facing or as live exported exhaustion. No offer is implied until mechanics, exploit prevention, receipt/rollback and player copy are separately approved.

## Geometry and checks

Use established popup geometry: `w = min(440, max(240, width - 24))`, centred, `y = max(6, (height - 240)/2 + 6)`: 296 px wide at logical 320×240 and 440 px at 640×360. Retain 240 px panel height, 18 px native buttons, 22 px list pitch, 26 px arrows and rendered-width clipping/tooltips.

For editors, heading `y+22`, context `y+46`, list header `y+60`, rows `y+84/+106/+128/+150`, feedback `y+190`, footer `y+206`. Scenario content has no section navigation: summary occupies `y+46` through `y+150`, feedback `y+190`, Export/Close at `y+206`; long count/exclusion detail opens a read-only **More…** sheet rather than clipping. See [HTML geometry mockup](design/deferred-authoring.html).

Implementation tests at 320×240 and 640×360 must cover group create/edit/cancel/reject/stale state; picker search/pages; bounded member data; map target selection; Event action and no-spawn preview; both role sources; source switching/history status; current-effective-content counts/map exclusion/logical bindings; unbound role destinations; disabled imported Events; export rejection/late replies; forbidden-field absence in exports; and Altar close/pending/revocation with no public numerical terms. Server tests separately prove authority, immutable installed defaults, no map mutation, entity safety, spawn/receipt behavior, source history and Altar transactions.

## Open decisions for implementation

1. Confirm portable schema/build-pipeline owner and secure server export destination.
2. Define logical binding-name syntax, collisions and target-map review that moves imported templates from Disabled to enabled.
3. Define allowed entities, counts, configuration/NBT grammar, target compatibility, spawn/cleanup receipts and encounter retention.
4. Confirm idempotence/history/rearming policy for `Role assigned` and `RoleFirstEntry COMPLETE`.
5. Select and review final multi-offer Altar geometry and approve each offer's transaction, GM terms and player flavor.
