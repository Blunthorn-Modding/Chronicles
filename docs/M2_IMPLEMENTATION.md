# M1 follow-up and M2 character dossiers

## Requested behavior

The operator changed the between-session policy on 19 October 2026: world simulation must freeze whenever the session is not active, while players remain mobile and can edit character cards. This supersedes the earlier ordinary-play policy. Inactive, paused, ended and unavailable campaign state freeze vanilla simulation through the server tick manager. Active sessions unfreeze it. This uses vanilla tick-freeze semantics; players can move, chat, use commands and interact. It is not a build-protection system. Session state owns this tick-freeze setting; tick-rate configuration is not changed.

Every online player sees the session number/phase above the hotbar, refreshed on transitions, join and periodically. The message identifies when the world is frozen. No private campaign flags/history are sent. Missing/corrupt campaign state freezes simulation and displays unavailable status without resetting data. Existing schema-1 campaign files remain compatible.

M2 adds one public character card per account, stored by UUID separately from session data. Fields: display name (required, 48 Unicode codepoints), pronouns (optional, 32), description (optional, 512). Normalize surrounding whitespace and reject controls/formatting codes. Only an account can edit its own card, including outside active sessions; operators do not gain a client packet that edits another account. Public cards can be viewed for online players. There are no private fields, selectable characters, journals or character mechanics in this slice.

## Data and interfaces

`CharacterProfile`, `ProfileStore` and `ProfileService` are independent of Minecraft APIs. Schema-1 JSON files are `<world>/chronicle/profiles/<uuid>.json`, bounded to 16 KiB with owner verification, atomic writes and a previous-file `.bak`. An absent card is a read-only default using the account name. Save failure/corrupt data cannot replace a valid card or report success. Revision checks reject stale edits. Session data and player/world inventories are not migrated.

Fabric play payloads are typed and bounded: request a public card by UUID; save own card using expected revision and three fields; receive a public view with server-derived editability; receive a localized error notice. Save payloads contain no target UUID. Requests run on the server thread, with per-player request throttling and bounded payloads. Only the requesting player receives the response, and disconnect clears transient rate state.

K opens the own-card editor. `/chronicle profile open [online-player]` provides another route. `/chronicle profile show [online-player]` and `/chronicle profile set <name|pronouns|description> <text>` provide a text fallback, with a clear command for optional fields. Read-only views cannot send saves from the UI. Saving waits for server acknowledgement; errors preserve edits and timeout permits retry. The screen supports keyboard navigation and resizing and does not pause the server. English localization covers the new UI/status text.

## Verification and deployment

1. Run pure Java profile validation/persistence/revision/failure tests alongside existing campaign tests and compile both mods/client source sets.
2. In an isolated dedicated world, verify frozen time/mobs, mobile players and actionbar status in inactive/paused/ended phases; active time advances and restart reapplies the saved phase.
3. Connect two clients; edit/view cards, reject invalid/stale inputs and unauthorized target edits, and verify persistence after restart. Check the actual custom payload path as well as command fallback.
4. Exercise the real Fabric GUI and inspect generated screenshots where the environment permits; record untested visual/audio behavior explicitly.
5. Save/stop/back up the live server, archive previous jars, install the tested build and matching M2 pack, restart and verify preserved campaign state/operator access. Never use the live world for destructive tests.

M3 assets/content and M4 world transformations remain later milestones. Execution evidence belongs in TEST_PLAN.md.
