# M6 — Core key-location authoring

> M7 update: the operator tested this slice and accepted the geometry approach. Persistent previews and registered clickable choices supersede the five-second preview below. Use the matching M7 pack and [current authoring workflow](MAP_AUTHORING.md); the remainder records the original M6 delivery.

19 October 2026. Scope: the first slice of [M6–M13](DEVELOPMENT_MILESTONES.md), implementing CF-06. Core owns the reusable geometry/selection tools. Quiet Below meanings, chest/spawn execution, observation triggers and autonomous ecology follow in later milestones.

## Builder workflow

Use a matching M6 client/server (Minecraft 1.21.10, Chronicle `0.1.0-dev.6`). All authoring commands require operator level 2. Pause/end the session before entering author mode. Players can move and edit dossiers while simulation is frozen.

```text
/chronicle author mode on
/chronicle author select pos1
/chronicle author select pos2
/chronicle author region save ancient_city
/chronicle author location save arrivals here
/chronicle author block bind ancient_station target
/chronicle author list
/chronicle author show ancient_city
/chronicle author validate
/chronicle author mode off
```

`pos1`/`pos2` record the block at your feet; optional coordinates support normal absolute/relative Minecraft syntax. `select target1`/`target2` instead select the block you look at within six blocks. Neither selection method changes that block. `select clear` discards both corners. Corners cannot cross dimensions. Cuboids include both corners and normalize reversed selections; large regions are metadata, not a request to scan/fill their volume.

`location save <id> here` records precise player position plus yaw/pitch. `block bind <id> target` records the targeted block and its expected registry type, without reading/copying its inventory. IDs use lowercase letters, digits and underscores, start with a letter and have at most 64 characters; they are shared across all three kinds.

Saving an existing ID fails. To move/change an existing definition, use `region replace <id>`, `location replace <id> here` or `block replace <id> target`. Replace requires an existing ID. `/chronicle author remove <id>` deletes only metadata. Each committed change advances the registry revision and a saved definition receives that revision. Later bindings must pin/check it; no gameplay binding exists in M6.

`show <id>` prints geometry/type/dimension/revision and sends a five-second geometry preview only to the requesting operator in that dimension. Regions show their edges, locations show position/facing, anchors show the target. One bounded server-to-client geometry message drives the preview, independently of world ticks and region volume. A matching M6 client is required; older clients still receive the coordinates and an upgrade notice. `validate` reports valid, invalid and unresolved definitions; unloaded anchors remain unresolved, never force-loaded. Changed/missing block types are invalid. Read-only inspection works during ACTIVE; entering/editing requires a non-active, available session. Author mode/selection clear on disconnect, restart or play becoming ACTIVE; mode off is always available. Repeating mode on preserves a current selection.

Saving geometry does not fill/reset a chest, spawn/teleport anything, consume a discovery, change campaign flags, place sculk or enable a station. No world import/export is required. Core has no Quiet Below import or custom campaign asset dependency.

## Storage and failure behavior

`<world>/chronicle/authoring.json` schema 1 contains the registry revision and a discriminated list of `region`, `location` and `anchor` definitions. Limit: 1,024 sites and 1 MiB. Definitions are immutable; membership checks are constant time. Coordinates/facing are finite; current dimension build limits/world border are checked on save. Regions may span unloaded chunks.

Storage uses forced temporary writes and atomic replacement, preserving the previous valid file as `.bak`. Strict reads reject unknown/missing fields, duplicate JSON members, excessive nesting, invalid numbers/types, trailing data and unsupported schemas. Invalid load disables this service and preserves evidence; existing sessions/profiles remain separate. External file replacement/creation/deletion after load blocks further writes rather than losing an operator edit. Save failures leave the published registry unchanged. Exhausted revisions return a controlled error. Restore only while stopped; no automatic empty fallback or silent backup promotion.

Runtime adapters live in Core `server/AuthoringRuntime` and `server/AuthoringCommands`; campaign-neutral records/store/service live in `core/authoring`. Successful mutations log actor UUID/name, action, ID and revision. Runtime operations require the server thread. Commands recheck permission and phase at execution, target only loaded chunks and reject dimension-overridden editing contexts.

## Verification and review

Domain tests cover geometry/normalization/bounds, finite values, immutable revisions, create/replace/remove, failing saves, backups, malformed/future/duplicate JSON, external edits and revision exhaustion. The real Fabric regression adds command/persistence checks for all three kinds, permissions, ACTIVE rejection, stale target types, cross-dimension contexts, reconnect selection isolation and server restart. It retains the existing session/freeze/dossier/content checks in a disposable world; evidence is recorded in [TEST_PLAN](TEST_PLAN.md).

Human acceptance remains separate from those checks. With the M6 pack installed:

1. Select a small region using feet or targeted corners, save it and inspect its outline with `show`.
2. Save a location while facing a distinctive direction; confirm `show` reports it correctly.
3. Look at an existing chest/block, bind it and validate; its contents/block should be untouched.
4. Explicitly replace a region after changing its selection, then remove a disposable definition.

Report whether the selection and preview are usable for map building. This is the M6 review gate before extending authoring into chest/spawn operations; a click wand can be added based on that feedback. This review does not require building the production map or testing sculk mechanics that are not implemented yet.
