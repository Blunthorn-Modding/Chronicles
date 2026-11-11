# M7 — reusable content and spawn tools

> Superseded chest UX: the operator accepted persistent outlines and registered chat choices, but rejected capture/template-first authoring. [M7_LOOT_TYPES](M7_LOOT_TYPES.md) is the current chest contract: one-step typed target binding, independent loot percentages, campaign defaults/server overrides, session-start and optional periodic full replacement. This document records the original slice and legacy recovery behavior.

Implementation contract, 19 October 2026. Operator feedback accepts M6's geometry approach and requests persistent previews plus campaign-registered choices. These fixes precede M7 on the same scoped branch. Existing worlds/IDs stay readable; no growth, lore or scheduled revelations are enabled.

## Authoring corrections

- A private selection/inspection outline remains visible while moving. Clear it explicitly with `select clear`/`preview hide`, leave author mode or disconnect. Changing dimension clears the displayed preview; entering ACTIVE clears author selections/previews. Read-only `show` remains usable during ACTIVE until hidden. No world/particle ticking is needed.
- Core exposes a startup catalog of namespaced typed authoring roles. Each submod registers its own IDs, labels and region/location/block kind before the catalog freezes. Quiet Below registers source city, Relic Heart, altar and forge; Core does not name those roles. The clickable author menu and completion lists derive from the catalog and saved definitions. Saving metadata does not activate a station/ecology.
- Legacy unqualified site IDs remain supported without automatic renaming. Known registered roles reject the wrong geometry kind. Saving/replacing remains explicit; the menu must not overwrite an existing binding just because it was clicked.

## M7 ownership and delivery

Core owns chest inventory templates, location references, operation policies, persistence and safe execution. Campaigns choose contents, site/group meanings and encounter composition. Commands are operator-only, server-authoritative and independent of Quiet Below classes. Configuration edits use non-ACTIVE author mode; deliberate runtime actions use explicit preview/execute controls and preserve existing sessions/dossiers.

Chest slice: target a supported single inventory, capture item stacks/components without consuming them, list/inspect templates, bind a template to a saved block anchor with its revision, preview a fill and explicitly confirm it. First policies are manual, once per campaign and once per session. Initially fill **only an entirely empty target**; never replace player contents. Reject lazy loot tables, unsupported/double inventories, unloaded/replaced/stale anchors and stale previews. Journal intent/before/after before mutation, durably flush world changes before recording success, and recover uncertain operations by comparing inventories. Pending/conflicting/corrupt recovery holds world simulation and blocks relevant interactions until resolved; do not blindly repeat a fill or silently erase evidence. A save failure after mutation is an uncertain pending action, not “nothing changed.”

Spawn slice: bind oriented Core locations to reusable arrival/respawn purposes, with explicit safe destination validation and a named target player. Saving a binding never teleports anyone or overrides vanilla respawns. Manual execution validates loaded standing room/support, world border, dimension and reference revision; preserve yaw/pitch. Entity-spawn configuration/execution, if delivered in this slice, must have bounded counts and explicit types with no automatic encounter start. No departure trap, forced return or exposure of private scenario groups.

Implement the coherent slices with domain/persistence tests, full build and isolated real Fabric checks. Test permissions, inactive editing, stale references, components/inventories, repeated execution/policy receipts, restart/recovery and safe/unsafe spawn cases. Inspect persistent previews after more than five seconds while walking and test menu/completions with registered Quiet Below roles. Human review concerns the actual content/spawn workflow before enabling it on the handcrafted production map. Mark any undelivered sub-slice explicitly rather than claiming all of M7 complete.

## Builder review of this slice

Import `../client/Chronicle-M7-1.21.10.mrpack` and join `localhost` with the server running. The client update is required for persistent outlines.

1. In a non-active session, enable author mode, select two corners, then `/chronicle author`. Click **Set** for the source city and submit the suggested command. Walk around for more than five seconds; the outline should remain visible. Check **Show**, **Replace** and `/chronicle author preview hide`.
2. Look at a nonempty single chest/barrel/shulker, capture `review_stock`, then bind that template to a saved empty container anchor using the commands in [MAP_AUTHORING](MAP_AUTHORING.md). Capture should leave the source items untouched.
3. Start a session, preview/apply the fill, inspect items/components and confirm a repeated once-only fill is rejected. Pause before making authoring edits.
4. Save a safe point/facing, bind a purpose, then during ACTIVE preview/confirm a named player. Check that an obstructed destination is refused and vanilla respawn settings remain unchanged.

Review is required for the practical chat-choice, fill and manual arrival workflow before extending M7 to automatic policies/entity groups. These commands do not add story encounters, timed reveals or ecology.

Automated build, domain, persistence, two-client/restart recovery and visual checks passed; deployed to localhost as `0.1.0-dev.7`. See [TEST_PLAN](TEST_PLAN.md) for commands/evidence and the save-disabled trial limitation. This is the first M7 slice for human review, not completion of automatic respawn/entity groups.
