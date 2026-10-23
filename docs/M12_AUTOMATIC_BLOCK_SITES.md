# Automatic block sites — lead approved


## Next implementation: separate limits from locations

Operator feedback, 23 October, for implementation on 24 October. This supersedes the dev.46 placement of limit controls; it is a recorded design correction, not an installed update.

- **Game setup** owns one **Block limits** entry for configuring the registered block types. Keep this global policy separate from location registration.
- Remove **Block limits** from both the top of Locations/Block sites and the selected-block detail. Remove links, redirects and return-to-limits shortcuts from placed-block entries; do not replace the duplicates with another Locations submenu.
- **Locations / Block sites** showcases physical registered locations and their normal location actions. For limits, display information only: current count/cap and whether capacity is available, the limit is reached, or the type is already over limit.
- Keep existing server-owned limits, permission/revision checks, persisted configuration, default caps, fixed Heart rule and conservative unloaded accounting. This is a presentation/routing correction, not a new quota system.
- Verify that both former Locations entry points are absent, policy edits work through Game setup, and read-only location status updates after placement/removal or a policy change.

No code, server, world or pack change is requested tonight. The remaining document records the original design except for the revised count-configuration destination below.

## Decision

**Locations → Block sites** becomes one flat list of actual instances: automatically managed supplied campaign blocks and optional manual vanilla anchors. Remove the Block-sites **Game bindings** selector, **Manage game bindings**, manual BLOCK requirement picker, naming prompt, and assign/clear route.

Placing a supplied managed block registers its physical position automatically. Managed blocks are listed by their ordinary block label and location, never as a requirement or a named saved site. Placement is refused before creating an extra instance when its configured cap is reached. Default caps are one **Ancient Altar** and one **Ancient Forge**. The **Relic Heart** is structurally one campaign root (max one); its historical root pin remains authoritative for terminal cleanup and must not be replaced by a later placement.

Intrinsic Altar and Forge player interactions stay on the blocks. Opening a station does not depend on this administration list or a binding. Manual anchors remain for vanilla/third-party references only.

## Flat Block sites list

The accepted Points / Block sites selector stays. Block sites has no nested selector.

| Row kind | Label and facts | Detail actions |
| --- | --- | --- |
| Managed instance | `Ancient Altar` / `Ancient Forge` / `Relic Heart`, dimension, exact position, actual block type, `1 / limit` count | **Show block site** only; facts include managed state, loaded/missing state, and cap |
| Manual anchor | saved ID, dimension/position, expected block type, `Manual` | existing **Show block site**, **Replace block site…**, **Delete location…** |

Rows deduplicate by dimension plus physical block position. If a manual anchor references the same position as a managed instance, show one row with both facts; managed identity wins for cap/accounting, while manual anchor actions remain explicitly scoped to its manual reference.

The empty state says `No managed blocks or manual block sites.` and shows **Bind looked-at block…** only for an eligible manual reference. It never asks the operator to name or bind a supplied block after placement.

## Managed states and safeguards

* **Present:** ordinary managed row; Show uses the existing private outline.
* **Unloaded:** row remains from its durable managed registration, says `Unloaded`, and Show gives the existing non-loading/private result. It does not invent a replacement.
* **Missing or wrong block:** row says `Missing`/`Changed`; it still occupies the cap until the server's explicit managed-instance policy resolves it. The UI offers no independent Delete/Unbind escape hatch.
* **Over limit:** placement is rejected server-side before registration with concise text such as `Ancient Altar limit reached (1).` Existing instance rows stay unchanged.
* **Legacy migration:** existing registered anchors/assignments are read as managed when their exact block and position match; unmatched manual anchors remain manual. Migration never names, moves, duplicates, or silently deletes a reference. The Heart's historical root pin remains separate from mutable placement registration.

## Count configuration

Limits are server policy, not geometry. **Game setup > Block limits** is the sole configuration entry. Operators choose a registered managed type and edit its limit through the normal configuration controls. Core provides this generically; no Altar/Forge-specific configuration branches are needed. Locations has no policy editor, policy submenu or link back to this entry. A placed block reports current capacity/limit status only.

This destination is the operator's next requested correction. The installed dev.46 build still has the former Locations controls until implementation on 24 October.

The structural Relic Heart entry says `Fixed: 1` and is read-only. JSON/server configuration remains the authoritative persistent policy and can configure the same limits. Lowering a cap below the current active count is allowed: existing blocks stay, the type/detail says `Over limit`, and new placements are denied until reconciliation brings the active count within the cap. No limit operation deletes or unbinds a block.

Unknown/unloaded managed instances stay in the active count. A missing instance leaves active count only after authoritative actual-removal/reconciliation, never because the UI is viewed. The historical Heart anchor is retained separately; after ecology has bound or ended it, a new Heart cannot replace that root through limit editing or reconciliation.

## Points and Game setup

Move only the currently exposed game `AuthoringCatalog.Kind.LOCATION` purposes into **Locations → Points** using the approved selected-point **Use for…** / **Clear use…** model. Exclude built-in `chronicle_core:arrival` and `chronicle_core:respawn` catalog definitions: actual starting/respawn policy remains under Players through separate `SpawnAuthoring`, with no fake `MapSiteAssignments`. Points retain manual save/update/delete and actual location geometry. If no eligible game LOCATION purpose is registered, hide Use for/Clear use rather than show an empty picker. This makes Game setup global tools only, without duplicating geometry or purposes.

Game setup contains global policy/tools such as **Heart weakness**. It has no REGION, LOCATION, or BLOCK placement/binding rows.

## Existing API constraints and acceptance checks

Current menu controls have a finite input kind (32 characters) and one selected detail, so managed actions use finite kinds such as `BLOCK_LIMITS`, `BLOCK_LIMIT_TYPE`, `BLOCK_LIMIT_SET`, and `MANUAL_BLOCK_CREATE`; no per-control identifier schema is required. Managed identity and cap decisions are server-owned. Existing explicit authoring commands may remain backward compatible for manual anchors; they cannot bypass managed placement caps.

* A placed supplied block auto-registers exactly once; duplicate events/reloads do not duplicate its row.
* Default/configured caps reject extra supplied placements before a second managed instance exists.
* Altar/Forge remain usable intrinsically whether or not any manual anchor exists; Heart historical cleanup pin is preserved.
* Flat list, manual-anchor lifecycle, position dedupe, unloaded/missing/legacy states, lowered-overlimit caps, and non-editor denial are clear and safe. If a deduplicated row has a manual reference, **Delete manual reference** removes only that reference and explicitly says it does not remove the block or free capacity.
* Points owns only eligible game LOCATION purposes with the same generic usage model; arrival/respawn remain Players policy, and Game setup shows global tools only.

This is a placement/presentation design only. It does not specify the managed-instance store, migration implementation, block event code, cap configuration schema, station rules, or Heart cleanup behavior.

Lead approved subject to the exact generic picker/detail, reconciliation, and Heart-root constraints above.
