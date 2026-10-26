# CF19 — Player Roles

26 October 2026. Implemented and deployed on `feat/core-roles-areas-departures` for the combined dev55 review with CF20/CF21. Human acceptance remains pending.

## Organizer workflow

Open **Advanced → Player Roles**. **Create new role** is above the middle list. Create/edit name, public description, whether players may choose it, its account limit, starting/respawn Points, starting item stacks and references to named Books & Lore. Nested selectors retain the parent draft. A changed server frame makes an old draft read-only until reopened; stale/missing Point pins are retained rather than silently cleared.

Use **Players → selected account → Assign player role** for a reviewed GM assignment. **No role** opens the same reviewed confirmation and clears only the assignment. Offline assigned accounts reserve seats. Deleting an assigned definition or lowering capacity below assignments is refused. Reassignment/clearing does not teleport, replay starting supplies, repeat books or clear participation/start history.

An ordinary player sees only their own account and public selectable-role descriptions/capacities. Starting supplies, private books, destination pins, GM-only roles and other players' assignments are not in that envelope. Choose a role before first active participation; existing participants are marked started once during upgrade without grants or moves. New active participation without a role also records a start, so later assignment cannot farm a kit.

## Configuration and defaults

`config/chronicle/player-roles.yaml` is the authoritative complete catalog. Quiet Below supplies `data/quiet_below/chronicle/player-roles.yaml` defaults only on first initialization. The two starting backgrounds are editable setup defaults, not final named characters or frozen story prose. Their Points/books initially remain unbound; final cast/content is still deferred.

The adjacent `.initialized` marker prevents a missing previously-created file from silently resurrecting deleted defaults. Successful edits retain exact prior bytes in `.bak` and use atomic replacement with external-edit detection. Failed initial creation leaves no initialization marker. YAML is conventional block YAML, parsed with pinned SnakeYAML Engine 3.1.1: explicit tags, anchors/aliases, merge keys, duplicate keys, multiple documents, unexpected fields/types and excessive size/nesting/nodes are rejected before construction. The full Apache-2.0 license is in Core's artifact, and the library is nested only in Core; the scenario development runtime uses the same pin.

Role definitions are generic Core data. `AuthoringCatalog.Role` continues to mean a map requirement; no new player-role system is placed under Locations or Game setup. Region/world replacement remains CF21.

## Start and respawn behavior

Assigned new players enter during occupied active play when eligible. All supplies/books must fit in empty normal inventory slots; existing items/components are preserved and nothing drops. Full inventory, unsafe/unloaded/stale Points or missing books defer the entire start before any claim. Configured stacks obey the shared enchantment caps. Starting movement resolves a saved manual override first, explicit Off second, then the role's pinned Point. An unresolved prior `CLAIMED` automatic arrival blocks role entry instead of silently granting supplies at an uncertain location.

The coordinator journals exact 36-slot before/after inventories, position evidence, pinned role JSON/SHA and book IDs/tokens before effects. It claims before inventory/movement, saves player data before completing secondary ledgers and the receipt, and releases only its own hold. Receiving a personal role book records discovery under the existing campaign ledger and preserves the first finder. Role books deliberately bypass automatic chest-spawn limits as personal entitlements.

Respawn uses the existing spawn scheduler, with manual policy over role default and explicit Off preserving vanilla behavior. Players management exposes a role-default choice to remove a manual/Off override without moving the player. The legacy placement-retry control is shown only for a configured manual starting policy; the kit receipt is independent. Death, reconnect, reassignment and phase boundaries never repeat the kit or books. A completed entry journal remains authoritative if a secondary start marker is lost. No normal reset/reversal of the kit receipt exists.

## Recovery and limits

World state lives in `world/chronicle/player-roles.json` and `role-first-entry.json`. The former holds seats/start markers/explicit policy-Off sets; the latter holds immutable planned effects and conservative receipt stages. Existing files are not rewritten on malformed input. Started-history/capacity and journal byte/account bounds reject before publishing; no truncation or guessed account lookup.

On restart, unresolved entry operations install holds. PREPARED plus exact before-state may abort safely; APPLYING requires exact saved post-state, another player-data save and recheck before ledger completion. Other outcomes remain held. `/chronicle roles entry recover <online-player>` only rechecks evidence; it cannot force success or clear history. `/chronicle roles reload` reloads corrected YAML without grants and can recover the role-config startup hold in non-active operator Editing. Unknown/conflicting operation evidence is retained.

## Verification

- `python scripts/workspace.py build`: integrated build passed (46 seconds before the final adapter/trial refinements); subsequent native builds compiled the later code. A final bundle-wide build remains required.
- Focused roles/entry/menu tests cover strict parser shapes, failed saves, stale bytes, no default resurrection, capacity/assignment/Off behavior, immutable claims and conservative recovery. Entry suite passes seven tests. Later area-filtered test runs do not certify the final full test count.
- `python scripts/roles_smoke.py`: latest successful disposable run `../reports/player-roles-20261026T144844705465Z`, nine reported proof groups across real initial/restart clients. Actual normal-menu create → GM review → assign → clear → delete passes. A full inventory defers entry; a prior unresolved movement claim with free space still gives no kit/discovery; after normal reset, role Point movement and one named-book grant/discovery complete. Three actual deaths prove role respawn, manual override, explicit Off and no kit/book reissue. Real server restart retains Off, vanilla position, first discovery and completed start history.
- Three clean desktop/480px captures were visually inspected; Create is above existing rows and appears once, default Clean omits diagnostics. Native opt-in trial commands require a disposable marker, exact test-runs path, dedicated offline loopback port25575 and operator permission. They are absent without the trial environment flag.
- Independent read-only source review is clear after corrections to generated IDs, clear-role routing, malformed nested JSON, initialization recovery and unresolved prior arrival handling.

Native injected disk/kill interruption, maximum-account native load and an ordinary non-GM/multiple-player role run are not claimed by the finite fixture. The combined area/departure/editor fixtures also pass; see TEST_PLAN for the combined build and deployment evidence. A later regression test verifies that shared empty role lists emit no YAML aliases and can reload after restart; the writer now validates its own strict round trip.
