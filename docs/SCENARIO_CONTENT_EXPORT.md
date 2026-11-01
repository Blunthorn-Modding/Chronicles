# Scenario content capture and export

`Scenario content...` appears in the Core editor header only when a campaign has registered a portable-content provider. It is an operator-only, non-active Editing action and refuses while a Chronicle operation hold exists.

The popup captures the current effective role, book, loot-table, encounter-group and Event definitions from their ordinary Core editors. It does not create a separate content editor. Role starting and respawn bindings are removed; Events retain only portable symbolic references and require target-map review before use. Regions, locations, chests, saved states, map settings, player identities, histories, receipts, clocks and terrain are excluded. Managed block-instance UUID targets become unbound references; encounter compositions export without their world bindings.

Export rechecks the exact server preview, current permissions and definition digest. It first loads the controlled draft and then writes the content-only draft and a deterministic immutable build input under the server-controlled `config/chronicle/scenario-content/` root. The client never supplies a path. The exported path is returned only to the requesting operator. Export never edits the installed JAR, this map, or live definitions.

The complete path is sent privately to that operator's chat. The popup shows a short completion message so a long server path is not lost to compact-screen clipping.

If an export is refused or fails, the popup stays open with the server reason. A changed/revoked menu frame makes the popup read-only; reopen it to capture a new review.

Validate and import the resulting file with the [scenario build tools](SCENARIO_CONTENT_BUILD.md), then build the next mod version normally. A copied map remains independent of that export: its map setup stays in the world directory.
