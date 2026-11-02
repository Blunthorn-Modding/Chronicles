# Scenario content capture and export

`Scenario content...` appears in the Core editor header for operators. Its landing page lists installed content providers and **Create custom scenario...**. Viewing a preview does not require Editing; creating or exporting requires non-active Editing and refuses while a Chronicle operation hold exists.

The popup captures the current effective role, book, loot-table, encounter-group and Event definitions from their ordinary Core editors. It does not create a separate content editor. Role starting and respawn bindings are removed; Events retain only portable symbolic references and require target-map review before use. Regions, locations, chests, saved states, map settings, player identities, histories, receipts, clocks and terrain are excluded. Managed block-instance UUID targets become unbound references; encounter compositions export without their world bindings.

Export rechecks the exact server preview, current permissions and definition digest. It first loads the controlled draft and then writes the content-only draft and a deterministic immutable build input under the server-controlled `config/chronicle/scenario-content/` root. The client never supplies a path. The exported path is returned only to the requesting operator. Export never edits the installed JAR, this map, or live definitions.

The popup shows immediate pending feedback and an acknowledged result containing the complete server file path, **Copy path** and the next step. **Last result** reopens the last successful result during that menu connection. The path is also sent privately in chat. An unconfirmed timeout does not claim that a file was saved.

If an export is refused or fails, the popup stays open with the server reason. A changed/revoked menu frame makes the popup read-only; reopen it to capture a new review.

Validate and import the resulting file with the [scenario build tools](SCENARIO_CONTENT_BUILD.md), then build the next mod version normally. A copied map remains independent of that export: its map setup stays in the world directory.

## Empty custom scenarios with installed mechanics

**Create custom scenario...** asks for a name and ID, then writes a create-only profile kit under `config/chronicle/scenario-setups/`. It does not change the current world or start another process. The kit starts with no roles, books, loot tables, Events or encounter groups. Quiet Below's blocks, ecology, Forge and Altar mechanics remain available.

Use a separate dedicated server with its own world and configuration. Install the required mechanics mods and place the kit at `config/chronicle/scenario-profile.json` before starting. The workspace helper prepares this environment:

```powershell
python scripts/scenario_authoring.py prepare "FULL_PATH_TO_PROFILE.json"
python scripts/scenario_authoring.py start "PREPARED_SERVER_DIRECTORY"
```

Preparation copies the installed runtime/mods and trusted whitelist/operators, using the current built Chronicle artifacts. It does not copy the source world or configuration. The default authoring endpoint is `127.0.0.1:25590`, voice `24470`; optional port arguments are available. It generates an ordinary vanilla world. Enter `stop` in the console before changing server files.

On that server, use the normal editors. Books and loot tables live in `config/chronicle/profile-content/`; roles, Events, encounters and map bindings remain world-owned during authoring. Export captures their effective reusable definitions in a typed profile under `config/chronicle/scenario-exports/`. That profile can seed another fresh dedicated server without rebuilding the mechanics JAR. Raw mod-build inputs and runtime profile envelopes are distinct formats.

An initialized profile server preserves edits and deletions on restart, even when the startup profile contains older defaults. Installing a newer export with the same identity does not overwrite existing catalogs. Profile identity, scenario ID and required mechanics must match; a different scenario needs its own environment. A missing initialized catalog, missing profile, unsupported mechanics provider or unrelated existing Chronicle world refuses startup rather than re-seeding or replacing data. Map coordinates, assignments, discoveries and live progress never enter the portable profile. Third-party scenario mods must explicitly support suppressing their content defaults before they can participate.
