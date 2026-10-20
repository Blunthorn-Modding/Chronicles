# CF-10 implementation

20 October 2026. The operator approves the revised mockup and authorizes implementation, verification and a matching review build. Branch `feat/cf10-core-menu` starts at approved design commit `e10f9d3`. This supersedes the design-only hold in earlier notes.

Deliver the eight-page native menu, left navigation/middle selection/right context, compact drilldown, proper server editing status, player-owned movement settings, unified event presentation with separate typed execution services, independent loot-table editor and registry picker, region draft handoff/single-corner editing, separate point/block lists and typed game-site assignments. Core owns generic infrastructure; Quiet Below supplies definitions. Future format tools are absent unless supplied by a game. Actual Forge/Altar/Heart blocks, ecology and their campaign lifecycle remain M9–M12, not fake menu actions.

The new bounded private menu protocol is separate from legacy author packets. Keep existing commands, model IDs, geometry, inventories and once-only receipts. Default O/management entry points open the new screen; old explicit author chat routes and legacy packets remain compatible. Ordinary players receive only their own participation/card and public phase, never the management snapshot. Every management request checks op2 and server authority. Clock-only refreshes retain controls, focus and drafts; relevant server changes push acknowledgements without reopening closed screens. Menu close is presentation-only; region selection is server-owned.

Loot saves use strict whole-type candidates and expected versions/file bytes, registry/component validation, durable atomic save/backup before publication, and preserve schedules/receipts. Game setup stores requirement-to-site revision pins in a separate strict file, adopting existing same-ID sites once without copying geometry. Region changes retain the stable ID and invalidate stale consumer pins. Player settings use private bindings rather than rewriting a shared purpose. Content/private audio preview has no world or completion effects; real runs retain live phase/hold/audience checks and once-claim services.

Verification: focused domain/store/protocol tests, full build and scripting checks, then sequential disposable Fabric clients exercising real native widgets, registry items, permissions/stale requests, independent table saves, region close/reopen commands and bounds, player settings, event previews/live checks and unchanged legacy regressions. Inspect native 320×240, 480×270 and 640×360 logical layout captures. Never use the production world as a fixture. Stop/backup before deploying matching jars/pack, verify protected files and authenticated loopback readiness, and hand off actual screen/audio flow for operator review. Record exact outcomes and remaining limits in TEST_PLAN; do not label unbuilt campaign mechanics complete.

## Review build and compatibility

Build `0.1.0-dev.12`, Minecraft 1.21.10. Both mods and the matching client pack must be updated together. Main entry points are O, `/chronicles`, `/chronicle menu` and the existing management commands. The plural root redirects all existing command children and also opens the menu when used bare. New fallbacks include players, events (explicit cue/action kind), chests, loot-tables, locations and setup. Region commands support create/edit, pos1/pos2, selection show/clear and save. No old command or schema is removed.

A loot type with an unusually large component payload (over 48,000 encoded characters) remains JSON-edited rather than being truncated into a network form. The editor preserves existing components; constructing arbitrary components and creating custom table IDs remain JSON workflows. Setup requirements reference saved site revisions; editing a region makes consumers deliberately stale until rebound. Event reset preserves the previous receipt in the existing bounded administrative audit before clearing the delivery claim.

Human review covers navigation/discoverability, normal GUI scale, real account import and audible quality. Automated offline clients cannot establish those preferences. See TEST_PLAN for exact tests, artifact hashes, backup and deployment evidence. Future station placement prompts, ecology and format engines are not represented by nonfunctional controls.

## Operator review

The review pack is `../client/Chronicle-CF10-1.21.10.mrpack`; import it through your launcher, then join `localhost`. Both mods must be dev.12. The server remains PAUSED. O or `/chronicles` opens the menu.

1. Check navigation, item selection, scrolling, enabled/disabled reasons and Confirm/Cancel at your normal GUI scale. Enable editing and verify its server acknowledgement.
2. Create a region, enter its ID, Continue in world, stand at each corner and run `/chronicles author region pos1` / `pos2`. Reopen Regions and save. Edit one position of an existing region while keeping the other; inspect the persistent private box.
3. Look at a supported chest and bind its table in one workflow. Check that the confirmation names the looked-at container. In Loot Tables, Add item/search, edit independent chance/counts, remove/cancel or save the whole table. Saving changes future fills, not current contents; configured fills replace contents.
4. Preview an event before binding any region, including private audio while paused. Content preview and Check live audience/Run are separate; previews must not mark completion.
5. Inspect Players: own card/participation, independent starting/after-death points and explicit live Move review. Assigning a point moves nobody immediately. In Locations, switch Points/Block sites and return through navigation. Game setup assigns an existing site without making duplicate geometry.

Please review that workflow before further ecology implementation. Actual Forge/Altar/Heart blocks, their placement registration prompts, ecology and future-format engines remain the later milestones described in DEVELOPMENT_MILESTONES; this menu does not invent working controls for them.
